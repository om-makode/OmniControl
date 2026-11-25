package com.omnicontrol.feature.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction
import com.omnicontrol.core.common.OmniLog
import com.omnicontrol.core.common.TextMatch
import com.omnicontrol.core.model.ActionResult
import com.omnicontrol.core.model.AgentAction
import com.omnicontrol.core.model.UiElement
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Turns an [AgentAction] into real touches. Every action:
 *  1. resolves the exact node from the snapshot (falls back to a fresh read if the screen moved),
 *  2. shows the touching ball on the target,
 *  3. tries the accessibility action, then a real gesture if the screen did not react,
 *  4. waits for the screen to settle and reports whether it changed.
 */
class ActionPerformer(
    private val service: AccessibilityService,
    private val reader: ScreenReader,
    private val settle: ScreenSettleDetector,
    private val launcher: AppLauncher,
    private val feedback: () -> TouchFeedback?
) {

    suspend fun perform(action: AgentAction, snap: ScreenSnapshot): ActionResult {
        val start = System.currentTimeMillis()
        val before = snap.state.fingerprint
        val outcome: Pair<Boolean, String> = try {
            when (action.type.lowercase()) {
                "open_app" -> openApp(action.app ?: action.text.orEmpty())
                "open_url" -> openUrl(action.url ?: action.text.orEmpty(), action.app)
                "click", "tap" -> {
                    val x = action.x; val y = action.y
                    if (action.target == null && x != null && y != null) tapAt(x, y, false)
                    else click(action.target, snap, long = false)
                }
                "long_press" -> {
                    val x = action.x; val y = action.y
                    if (action.target == null && x != null && y != null) tapAt(x, y, true)
                    else click(action.target, snap, long = true)
                }
                "type" -> type(action.target, action.text.orEmpty(), action.submit == true, snap)
                "clear" -> type(action.target, "", false, snap)
                "enter" -> pressEnter(snap)
                "scroll" -> scroll(action.target, action.direction ?: "down", snap)
                "swipe" -> swipe(action.direction ?: "up")
                "back" -> global(AccessibilityService.GLOBAL_ACTION_BACK, "Went back")
                "home" -> global(AccessibilityService.GLOBAL_ACTION_HOME, "Went home")
                "recents" -> global(AccessibilityService.GLOBAL_ACTION_RECENTS, "Opened recent apps")
                "notifications" -> global(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS, "Opened notifications")
                "quick_settings" -> global(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS, "Opened quick settings")
                "wait" -> { delay((action.ms ?: 1500).coerceIn(200, 8000)); true to "Waited" }
                else -> false to "Unknown action '${action.type}'"
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            OmniLog.e(TAG, "perform ${action.type} failed", e)
            false to (e.message ?: "error")
        } finally {
            feedback()?.setPassthrough(false)
        }

        if (action.type != "wait") settle.waitForSettle()
        val after = try { reader.read().state.fingerprint } catch (e: Exception) { "" }
        return ActionResult(
            success = outcome.first,
            reason = outcome.second,
            durationMs = System.currentTimeMillis() - start,
            screenChanged = after != before
        )
    }

    // ---------------------------------------------------------------- apps / links

    private suspend fun openApp(name: String): Pair<Boolean, String> {
        if (name.isBlank()) return false to "No app name"
        return when (val r = launcher.launch(name)) {
            is LaunchResult.Launched -> {
                r.packageName?.let { settle.waitForPackage(it) }
                settle.waitForSettle(quietMs = 500, maxMs = 3500)
                true to "Opened ${r.label}"
            }
            is LaunchResult.NotFound -> false to buildString {
                append("I couldn't find an app called \"${r.query}\"")
                if (r.suggestions.isNotEmpty()) append(". Did you mean ${r.suggestions.joinToString(" or ")}?")
            }
        }
    }

    private suspend fun openUrl(url: String, pkg: String?): Pair<Boolean, String> {
        var u = url.trim()
        if (u.isBlank()) return false to "No link"
        if (!u.startsWith("http://") && !u.startsWith("https://")) u = "https://$u"
        val ok = launcher.openUrl(u, pkg)
        if (ok) settle.waitForSettle(quietMs = 500, maxMs = 4000)
        return ok to if (ok) "Opened link" else "Could not open $u"
    }

    // ---------------------------------------------------------------- touch

    private suspend fun click(id: Int?, snap: ScreenSnapshot, long: Boolean): Pair<Boolean, String> {
        val el = snap.state.element(id) ?: return false to "Element #$id is not on screen"
        val node = liveNode(id!!, el, snap)
        val label = el.primaryLabel.take(40).ifBlank { el.role.name.lowercase() }
        val point = tapPoint(node, el)

        feedback()?.showTouch(point.first, point.second, long)
        feedback()?.setPassthrough(true)

        // 1) accessibility action on the node or its nearest clickable ancestor
        val actionId = if (long) AccessibilityNodeInfo.ACTION_LONG_CLICK else AccessibilityNodeInfo.ACTION_CLICK
        val target = node?.let { clickableSelfOrAncestor(it, long) }
        var acted = target?.performAction(actionId) == true

        if (acted) {
            settle.waitForSettle(quietMs = 300, maxMs = 1800)
            val changed = reader.read().state.fingerprint != snap.state.fingerprint
            if (changed) return true to "${if (long) "Long-pressed" else "Tapped"} \"$label\""
            OmniLog.d(TAG, "node click on '$label' changed nothing, trying a real touch")
        }
        // 2) a real finger-like gesture at the visible centre
        acted = gestureTap(point.first, point.second, if (long) 700 else 60)
        return acted to if (acted) "${if (long) "Long-pressed" else "Tapped"} \"$label\"" else "Could not tap \"$label\""
    }

    private suspend fun tapAt(x: Int, y: Int, long: Boolean): Pair<Boolean, String> {
        feedback()?.showTouch(x, y, long)
        feedback()?.setPassthrough(true)
        val ok = gestureTap(x, y, if (long) 700 else 60)
        return ok to if (ok) "Tapped at ($x, $y)" else "Tap failed"
    }

    private fun clickableSelfOrAncestor(node: AccessibilityNodeInfo, long: Boolean): AccessibilityNodeInfo? {
        var n: AccessibilityNodeInfo? = node
        var depth = 0
        while (n != null && depth < 8) {
            if ((if (long) n.isLongClickable else n.isClickable) && n.isEnabled) return n
            n = n.parent
            depth++
        }
        return null
    }

    /** Centre of the part of the element that is actually on screen. */
    private fun tapPoint(node: AccessibilityNodeInfo?, el: UiElement): Pair<Int, Int> {
        val r = Rect()
        if (node != null) node.getBoundsInScreen(r) else r.set(el.bounds.left, el.bounds.top, el.bounds.right, el.bounds.bottom)
        val m = service.resources.displayMetrics
        r.intersect(0, 0, m.widthPixels, m.heightPixels)
        return r.centerX() to r.centerY()
    }

    /** The snapshot node if it is still valid, otherwise the best match on a fresh read. */
    private fun liveNode(id: Int, el: UiElement, snap: ScreenSnapshot): AccessibilityNodeInfo? {
        val node = snap.node(id)
        if (node != null && runCatching { node.refresh() }.getOrDefault(false)) return node
        val fresh = reader.read()
        val match = fresh.state.elements.maxByOrNull { other ->
            var s = 0.0
            if (other.role == el.role) s += 0.2
            if (other.viewId != null && other.viewId == el.viewId) s += 0.3
            s += TextMatch.labelScore(el.primaryLabel, other.primaryLabel) * 0.4
            val dx = (other.bounds.centerX - el.bounds.centerX).toDouble()
            val dy = (other.bounds.centerY - el.bounds.centerY).toDouble()
            if (dx * dx + dy * dy < 120.0 * 120.0) s += 0.2
            s
        }
        return match?.let { fresh.node(it.id) }
    }

    // ---------------------------------------------------------------- typing

    private suspend fun type(id: Int?, text: String, submit: Boolean, snap: ScreenSnapshot): Pair<Boolean, String> {
        var field: AccessibilityNodeInfo? = id?.let { snap.state.element(it) }?.let { liveNode(id, it, snap) }
        if (field != null && !field.isEditable) {
            // The AI pointed at a search icon / container: tap it, then look for the real input.
            val el = snap.state.element(id)!!
            val p = tapPoint(field, el)
            feedback()?.showTouch(p.first, p.second)
            feedback()?.setPassthrough(true)
            clickableSelfOrAncestor(field, false)?.performAction(AccessibilityNodeInfo.ACTION_CLICK) ?: gestureTap(p.first, p.second, 60)
            settle.waitForSettle(quietMs = 300, maxMs = 1500)
            field = null
        }
        if (field == null) field = focusedInput()
        if (field == null) {
            val fresh = reader.read()
            val el = fresh.state.elements.firstOrNull { it.editable && it.enabled }
                ?: return false to "No text box on screen"
            field = fresh.node(el.id)
        }
        val input = field ?: return false to "No text box on screen"
        if (input.isPassword) return false to "I don't type into password fields"

        if (!input.isFocused) {
            val r = Rect(); input.getBoundsInScreen(r)
            feedback()?.showTouch(r.centerX(), r.centerY())
            input.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            input.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            delay(250)
        }
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        var ok = input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        if (!ok && text.isNotEmpty()) {
            // Some apps ignore SET_TEXT: paste instead.
            val cm = service.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("omni", text))
            ok = input.performAction(AccessibilityNodeInfo.ACTION_PASTE)
        }
        if (!ok) return false to "Could not type into the field"
        if (submit) {
            delay(200)
            input.refresh()
            submitField(input)
        }
        return true to if (text.isEmpty()) "Cleared the field" else "Typed \"${text.take(50)}\"" + if (submit) " and pressed enter" else ""
    }

    private suspend fun pressEnter(snap: ScreenSnapshot): Pair<Boolean, String> {
        val input = focusedInput() ?: snap.state.elements.firstOrNull { it.editable }?.let { snap.node(it.id) }
            ?: return false to "No text box focused"
        return submitField(input) to "Pressed enter"
    }

    private suspend fun submitField(input: AccessibilityNodeInfo): Boolean {
        if (input.performAction(AccessibilityAction.ACTION_IME_ENTER.id)) return true
        // Fallback: tap a nearby search/send/go button.
        val fresh = reader.read()
        val btn = fresh.state.elements.firstOrNull { e ->
            e.clickable && !e.editable && listOf("search", "go", "submit", "done", "enter")
                .any { k -> e.searchText.split(' ').contains(k) }
        } ?: return false
        val node = fresh.node(btn.id) ?: return false
        val p = tapPoint(node, btn)
        feedback()?.showTouch(p.first, p.second)
        return clickableSelfOrAncestor(node, false)?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
    }

    private fun focusedInput(): AccessibilityNodeInfo? =
        runCatching { service.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) }.getOrNull()
            ?.takeIf { it.isEditable && it.packageName?.toString() != service.packageName }

    // ---------------------------------------------------------------- scrolling

    private suspend fun scroll(id: Int?, direction: String, snap: ScreenSnapshot): Pair<Boolean, String> {
        val dir = direction.lowercase()
        val container: Pair<UiElement, AccessibilityNodeInfo?>? = run {
            val explicit = snap.state.element(id)
            if (explicit != null && explicit.scrollable) return@run explicit to snap.node(explicit.id)
            val vertical = dir == "up" || dir == "down"
            snap.state.elements.filter { it.scrollable && it.enabled }
                .filter { if (vertical) it.bounds.height >= it.bounds.width / 3 else true }
                .maxByOrNull { it.bounds.area }
                ?.let { it to snap.node(it.id) }
        }
        val node = container?.second
        if (node != null) {
            val directional = when (dir) {
                "down" -> AccessibilityAction.ACTION_SCROLL_DOWN
                "up" -> AccessibilityAction.ACTION_SCROLL_UP
                "left" -> AccessibilityAction.ACTION_SCROLL_LEFT
                else -> AccessibilityAction.ACTION_SCROLL_RIGHT
            }
            val generic = if (dir == "down" || dir == "right") AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            if (node.performAction(directional.id) || node.performAction(generic)) {
                return true to "Scrolled $dir"
            }
        }
        // Gesture fallback inside the container (or the middle of the screen).
        val m = service.resources.displayMetrics
        val b = container?.first?.bounds
        val left = b?.left ?: 0; val right = b?.right ?: m.widthPixels
        val top = b?.top ?: (m.heightPixels * 0.2).toInt(); val bottom = b?.bottom ?: (m.heightPixels * 0.85).toInt()
        val cx = (left + right) / 2; val cy = (top + bottom) / 2
        val dy = ((bottom - top) * 0.32).toInt(); val dx = ((right - left) * 0.35).toInt()
        val (x1, y1, x2, y2) = when (dir) {
            "down" -> listOf(cx, cy + dy, cx, cy - dy)
            "up" -> listOf(cx, cy - dy, cx, cy + dy)
            "left" -> listOf(cx - dx, cy, cx + dx, cy)
            else -> listOf(cx + dx, cy, cx - dx, cy)
        }
        feedback()?.showSwipe(x1, y1, x2, y2)
        feedback()?.setPassthrough(true)
        val ok = gestureSwipe(x1, y1, x2, y2, 350)
        return ok to if (ok) "Scrolled $dir" else "Could not scroll"
    }

    /** Whole-screen swipe: "swipe up" = next reel/short, "swipe left" = next page. */
    private suspend fun swipe(direction: String): Pair<Boolean, String> {
        val m = service.resources.displayMetrics
        val w = m.widthPixels; val h = m.heightPixels
        val (x1, y1, x2, y2) = when (direction.lowercase()) {
            "up" -> listOf(w / 2, (h * 0.75).toInt(), w / 2, (h * 0.25).toInt())
            "down" -> listOf(w / 2, (h * 0.25).toInt(), w / 2, (h * 0.75).toInt())
            "left" -> listOf((w * 0.85).toInt(), h / 2, (w * 0.15).toInt(), h / 2)
            else -> listOf((w * 0.15).toInt(), h / 2, (w * 0.85).toInt(), h / 2)
        }
        feedback()?.showSwipe(x1, y1, x2, y2)
        feedback()?.setPassthrough(true)
        val ok = gestureSwipe(x1, y1, x2, y2, 300)
        return ok to if (ok) "Swiped $direction" else "Could not swipe"
    }

    private fun global(action: Int, msg: String): Pair<Boolean, String> {
        val ok = service.performGlobalAction(action)
        return ok to if (ok) msg else "System refused: $msg"
    }

    // ---------------------------------------------------------------- gestures

    private suspend fun gestureTap(x: Int, y: Int, durationMs: Long): Boolean {
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        return dispatch(path, durationMs)
    }

    private suspend fun gestureSwipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long): Boolean {
        val path = Path().apply { moveTo(x1.toFloat(), y1.toFloat()); lineTo(x2.toFloat(), y2.toFloat()) }
        return dispatch(path, durationMs)
    }

    private suspend fun dispatch(path: Path, durationMs: Long): Boolean = suspendCancellableCoroutine { cont ->
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs.coerceAtLeast(1)))
            .build()
        val sent = service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(g: GestureDescription?) { if (cont.isActive) cont.resume(true) }
            override fun onCancelled(g: GestureDescription?) { if (cont.isActive) cont.resume(false) }
        }, null)
        if (!sent && cont.isActive) cont.resume(false)
    }

    companion object {
        private const val TAG = "OmniActions"
    }
}

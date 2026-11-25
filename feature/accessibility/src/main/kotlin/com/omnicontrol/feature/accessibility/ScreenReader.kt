package com.omnicontrol.feature.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.pm.PackageManager
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.omnicontrol.core.common.OmniLog
import com.omnicontrol.core.model.RectBounds
import com.omnicontrol.core.model.Role
import com.omnicontrol.core.model.ScreenState
import com.omnicontrol.core.model.UiElement
import java.security.MessageDigest

/** A screen read plus the live node behind every element id, so actions hit the exact node. */
class ScreenSnapshot(
    val state: ScreenState,
    internal val nodes: Map<Int, AccessibilityNodeInfo>
) {
    fun node(id: Int?): AccessibilityNodeInfo? = if (id == null) null else nodes[id]
}

class ScreenReader(private val service: AccessibilityService) {

    private val pm: PackageManager = service.packageManager
    private val labelCache = HashMap<String, String>()

    fun read(): ScreenSnapshot {
        val start = System.currentTimeMillis()
        val metrics = service.resources.displayMetrics
        val screen = Rect(0, 0, metrics.widthPixels, metrics.heightPixels)
        val density = metrics.density

        val windows = try { service.windows } catch (e: Exception) { emptyList<AccessibilityWindowInfo>() }

        var keyboard = false
        var appPackage = ""
        var appWindowSeen = false
        val roots = ArrayList<AccessibilityNodeInfo>()

        // service.windows is ordered top-most first; read top windows first so dialogs get low ids.
        for (w in windows) {
            when (w.type) {
                AccessibilityWindowInfo.TYPE_INPUT_METHOD -> { keyboard = true; continue }
                AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY,
                AccessibilityWindowInfo.TYPE_SPLIT_SCREEN_DIVIDER -> continue
            }
            val root = w.root ?: continue
            val pkg = root.packageName?.toString().orEmpty()
            if (pkg == service.packageName) continue
            if (pkg == "com.android.systemui") {
                // Status bar / nav bar are noise; only keep the shade when it is pulled down.
                val b = Rect(); w.getBoundsInScreen(b)
                if (b.height() < screen.height() * 0.3f) continue
            }
            if (w.type == AccessibilityWindowInfo.TYPE_APPLICATION) {
                appWindowSeen = true
                if (appPackage.isEmpty()) appPackage = pkg
            }
            roots.add(root)
        }
        if (roots.isEmpty()) {
            service.rootInActiveWindow?.let { r ->
                if (r.packageName?.toString() != service.packageName) {
                    roots.add(r); appWindowSeen = true
                    appPackage = r.packageName?.toString().orEmpty()
                }
            }
        }
        if (appPackage.isEmpty()) appPackage = roots.firstOrNull()?.packageName?.toString().orEmpty()

        val ctx = WalkContext(screen, density)
        for (root in roots) walk(root, ctx, depth = 0, inList = false, absorbed = false)

        val elements = ctx.elements
        val state = ScreenState(
            packageName = appPackage,
            appLabel = appLabel(appPackage),
            elements = elements,
            fingerprint = fingerprint(appPackage, elements, keyboard),
            keyboardVisible = keyboard,
            screenWidth = screen.width(),
            screenHeight = screen.height(),
            isSecure = appWindowSeen && elements.isEmpty()
        )
        OmniLog.d(TAG, "read ${elements.size} elements of $appPackage in ${System.currentTimeMillis() - start}ms")
        return ScreenSnapshot(state, ctx.nodes)
    }

    private class WalkContext(val screen: Rect, val density: Float) {
        val elements = ArrayList<UiElement>()
        val nodes = HashMap<Int, AccessibilityNodeInfo>()
        var nextId = 1
    }

    /**
     * @param inList we are inside a scrollable container
     * @param absorbed an actionable ancestor already shows this subtree's text, so plain text children are skipped
     */
    private fun walk(node: AccessibilityNodeInfo, ctx: WalkContext, depth: Int, inList: Boolean, absorbed: Boolean) {
        if (depth > 60 || ctx.elements.size >= MAX_ELEMENTS) return
        if (!node.isVisibleToUser) return
        val r = Rect()
        node.getBoundsInScreen(r)
        if (!r.intersect(ctx.screen) || r.width() <= 1 || r.height() <= 1) return

        val className = node.className?.toString().orEmpty()
        val text = node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        val desc = node.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        val hint = node.hintText?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        val clickable = node.isClickable
        val longClickable = node.isLongClickable
        val editable = node.isEditable
        val scrollable = node.isScrollable
        val checkable = node.isCheckable
        val actionable = clickable || longClickable || editable || scrollable || checkable

        var childrenAbsorbed = absorbed
        val keep: Boolean
        var label: String? = text

        if (actionable) {
            keep = true
            if (clickable && text == null && desc == null) {
                label = collectText(node, 3)
            }
            if (clickable || checkable) childrenAbsorbed = true
        } else {
            val hasInfo = text != null || desc != null
            val bigImage = className.contains("Image") && desc != null &&
                r.width() > 40 * ctx.density && r.height() > 40 * ctx.density
            keep = hasInfo && !absorbed || (bigImage && !absorbed)
        }

        if (keep) {
            val id = ctx.nextId++
            val role = role(node, className, inList, r, ctx)
            ctx.elements.add(
                UiElement(
                    id = id,
                    role = role,
                    text = label?.take(200),
                    description = desc?.take(200),
                    hint = hint,
                    viewId = node.viewIdResourceName,
                    className = className.substringAfterLast('.'),
                    bounds = RectBounds(r.left, r.top, r.right, r.bottom),
                    clickable = clickable,
                    longClickable = longClickable,
                    scrollable = scrollable,
                    editable = editable,
                    checked = if (checkable) node.isChecked else null,
                    selected = node.isSelected,
                    enabled = node.isEnabled,
                    focused = node.isFocused,
                    isPassword = node.isPassword,
                    inList = inList && !scrollable
                )
            )
            ctx.nodes[id] = node
        }

        val childInList = inList || scrollable
        for (i in 0 until node.childCount) {
            val child = try { node.getChild(i) } catch (e: Exception) { null } ?: continue
            walk(child, ctx, depth + 1, childInList, childrenAbsorbed)
        }
    }

    private fun collectText(node: AccessibilityNodeInfo, maxParts: Int): String? {
        val parts = ArrayList<String>()
        fun rec(n: AccessibilityNodeInfo, d: Int) {
            if (parts.size >= maxParts || d > 8) return
            for (i in 0 until n.childCount) {
                val c = try { n.getChild(i) } catch (e: Exception) { null } ?: continue
                if (!c.isVisibleToUser) continue
                val t = c.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                    ?: c.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                if (t != null && parts.none { it == t }) parts.add(t)
                if (parts.size >= maxParts) return
                rec(c, d + 1)
            }
        }
        rec(node, 0)
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")?.take(200)
    }

    private fun role(node: AccessibilityNodeInfo, cls: String, inList: Boolean, r: Rect, ctx: WalkContext): Role = when {
        node.isEditable || cls.contains("EditText") -> Role.TEXT_FIELD
        cls.contains("Switch") || cls.contains("Toggle") -> Role.SWITCH
        cls.contains("CheckBox") || cls.contains("RadioButton") || node.isCheckable -> Role.CHECKBOX
        node.isScrollable || cls.contains("RecyclerView") || cls.contains("ListView") ||
            cls.contains("ScrollView") || cls.contains("ViewPager") -> Role.CONTAINER
        cls.contains("Tab") -> Role.TAB
        inList && node.isClickable && r.width() > ctx.screen.width() * 0.4f && r.height() > 48 * ctx.density -> Role.LIST_ITEM
        cls.contains("Button") -> Role.BUTTON
        cls.contains("Image") -> if (node.isClickable) Role.BUTTON else Role.IMAGE
        node.isClickable -> Role.BUTTON
        cls.contains("TextView") || node.text != null -> Role.TEXT
        else -> Role.OTHER
    }

    private fun appLabel(pkg: String): String {
        if (pkg.isEmpty()) return ""
        return labelCache.getOrPut(pkg) {
            try { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() } catch (e: Exception) { pkg.substringAfterLast('.') }
        }
    }

    companion object {
        private const val TAG = "OmniScreen"
        private const val MAX_ELEMENTS = 350

        /** Changes when the visible content changes (navigation, scroll, toggles, typed text). */
        fun fingerprint(pkg: String, elements: List<UiElement>, keyboard: Boolean): String {
            val sb = StringBuilder(pkg).append('|').append(keyboard)
            for (e in elements.take(60)) {
                sb.append('|').append(e.role.ordinal).append(':').append(e.primaryLabel.take(24))
                    .append(':').append(e.checked).append(':').append(e.selected)
                    .append(':').append(e.bounds.top / 40)
            }
            val md = MessageDigest.getInstance("SHA-1").digest(sb.toString().toByteArray())
            return md.joinToString("") { "%02x".format(it) }.take(16)
        }
    }
}

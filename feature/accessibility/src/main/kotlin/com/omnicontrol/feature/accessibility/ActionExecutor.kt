package com.omnicontrol.feature.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Path
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import com.omnicontrol.core.common.OmniLog
import com.omnicontrol.core.model.ActionResult
import com.omnicontrol.core.model.AgentAction
import com.omnicontrol.core.model.Observation
import com.omnicontrol.core.model.RectBounds
import com.omnicontrol.core.model.UiElement
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.math.abs

class ActionExecutor(
    private val service: AccessibilityService,
    private val screenReader: ScreenReader,
    private val settleDetector: ScreenSettleDetector,
    private val onHighlightTarget: ((RectBounds?) -> Unit)? = null
) {
    private val packageManager: PackageManager = service.packageManager

    suspend fun execute(action: AgentAction, observation: Observation): ActionResult {
        val startTime = System.currentTimeMillis()
        OmniLog.i(TAG, "Executing action: ${action.type} (target: ${action.target})")

        return try {
            val executionSuccess = when (action.type.lowercase().trim()) {
                "open_app" -> executeOpenApp(action.app ?: "")
                "open_url" -> executeOpenUrl(action.url ?: "")
                "click" -> executeClick(action.target, observation)
                "long_press" -> executeLongPress(action.target, observation)
                "type_text" -> executeTypeText(action.target, action.text ?: "", action.submit ?: false, observation)
                "clear_text" -> executeClearText(action.target, observation)
                "scroll" -> executeScroll(action.target, action.direction ?: "down", observation)
                "swipe" -> executeSwipe(action.fromX ?: 0.5f, action.fromY ?: 0.8f, action.toX ?: 0.5f, action.toY ?: 0.2f, action.durationMs ?: 300)
                "back" -> executeGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                "home" -> executeGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                "recents" -> executeGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
                "notifications" -> executeGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
                "quick_settings" -> executeGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
                "select" -> executeSelect(action.target, observation)
                "wait" -> {
                    delay((action.ms ?: 1000).coerceIn(100, 5000))
                    true
                }
                "read_screen" -> true
                "done" -> true
                else -> {
                    OmniLog.w(TAG, "Unknown action type: ${action.type}")
                    false
                }
            }

            // Wait for screen to settle
            settleDetector.waitForSettle()

            // Capture new observation
            val postObservation = screenReader.captureObservation()
            val elapsed = System.currentTimeMillis() - startTime

            // Clear target highlight
            onHighlightTarget?.invoke(null)

            // Verify action effect
            val verification = ActionVerifier.verify(action, observation, postObservation)

            ActionResult(
                success = executionSuccess && verification.isSuccess,
                reason = verification.reason,
                durationMs = elapsed,
                newObservation = postObservation
            )
        } catch (e: Exception) {
            OmniLog.e(TAG, "Error executing action: ${action.type}", e)
            onHighlightTarget?.invoke(null)
            ActionResult(
                success = false,
                reason = "Exception: ${e.message}",
                durationMs = System.currentTimeMillis() - startTime,
                newObservation = screenReader.captureObservation()
            )
        }
    }

    private fun executeOpenApp(appName: String): Boolean {
        if (appName.isBlank()) return false
        val pkg = findPackageForAppName(appName)
        if (pkg == null) {
            OmniLog.w(TAG, "Could not find package for app: $appName")
            return false
        }
        val intent = packageManager.getLaunchIntentForPackage(pkg)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } ?: return false

        service.startActivity(intent)
        return true
    }

    private fun findPackageForAppName(appName: String): String? {
        val installed = packageManager.getInstalledApplications(0)
        // Exact match
        for (info in installed) {
            val label = packageManager.getApplicationLabel(info).toString()
            if (label.equals(appName, ignoreCase = true)) {
                return info.packageName
            }
        }
        // Substring / prefix match
        for (info in installed) {
            val label = packageManager.getApplicationLabel(info).toString()
            if (label.contains(appName, ignoreCase = true) || info.packageName.contains(appName, ignoreCase = true)) {
                return info.packageName
            }
        }
        return null
    }

    private fun executeOpenUrl(url: String): Boolean {
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            OmniLog.w(TAG, "Rejected disallowed URL scheme: $url")
            return false
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        service.startActivity(intent)
        return true
    }

    private suspend fun executeClick(targetId: Int?, observation: Observation): Boolean {
        if (targetId == null) return false
        val element = observation.elements.find { it.id == targetId } ?: return false

        // Highlight target element
        onHighlightTarget?.invoke(element.bounds)
        delay(150)

        // Try node click first
        val root = service.rootInActiveWindow
        var nodeClicked = false
        if (root != null) {
            val targetNode = findNodeForElement(root, element)
            if (targetNode != null) {
                nodeClicked = clickNodeOrParent(targetNode)
                targetNode.recycle()
            }
            root.recycle()
        }

        if (nodeClicked) return true

        // Fallback: gesture tap at center
        OmniLog.d(TAG, "Falling back to gesture tap at (${element.bounds.centerX}, ${element.bounds.centerY})")
        return dispatchTapGesture(element.bounds.centerX.toFloat(), element.bounds.centerY.toFloat(), 50)
    }

    private suspend fun executeLongPress(targetId: Int?, observation: Observation): Boolean {
        if (targetId == null) return false
        val element = observation.elements.find { it.id == targetId } ?: return false

        onHighlightTarget?.invoke(element.bounds)
        delay(150)

        val root = service.rootInActiveWindow
        var nodeClicked = false
        if (root != null) {
            val targetNode = findNodeForElement(root, element)
            if (targetNode != null) {
                nodeClicked = targetNode.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
                targetNode.recycle()
            }
            root.recycle()
        }

        if (nodeClicked) return true

        // Fallback: 600ms gesture tap
        return dispatchTapGesture(element.bounds.centerX.toFloat(), element.bounds.centerY.toFloat(), 600)
    }

    private suspend fun executeTypeText(targetId: Int?, text: String, submit: Boolean, observation: Observation): Boolean {
        if (targetId == null) return false
        val element = observation.elements.find { it.id == targetId } ?: return false

        onHighlightTarget?.invoke(element.bounds)

        val root = service.rootInActiveWindow ?: return false
        val targetNode = findNodeForElement(root, element)
        if (targetNode == null) {
            root.recycle()
            return false
        }

        targetNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        val textSet = targetNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)

        if (submit && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            delay(100)
            targetNode.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
        }

        targetNode.recycle()
        root.recycle()
        return textSet
    }

    private fun executeClearText(targetId: Int?, observation: Observation): Boolean {
        if (targetId == null) return false
        val element = observation.elements.find { it.id == targetId } ?: return false

        val root = service.rootInActiveWindow ?: return false
        val targetNode = findNodeForElement(root, element) ?: run {
            root.recycle()
            return false
        }

        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "")
        }
        val cleared = targetNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        targetNode.recycle()
        root.recycle()
        return cleared
    }

    private suspend fun executeScroll(targetId: Int?, direction: String, observation: Observation): Boolean {
        val isForward = direction.equals("down", ignoreCase = true) || direction.equals("right", ignoreCase = true)
        val action = if (isForward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD

        // Try node scroll if target given or scrollable container found
        val root = service.rootInActiveWindow
        if (root != null) {
            val targetElement = if (targetId != null) {
                observation.elements.find { it.id == targetId }
            } else {
                observation.elements.find { it.scrollable }
            }

            if (targetElement != null) {
                val node = findNodeForElement(root, targetElement)
                if (node != null) {
                    val scrolled = node.performAction(action)
                    node.recycle()
                    root.recycle()
                    if (scrolled) return true
                }
            }
            root.recycle()
        }

        // Fallback: swipe gesture
        val metrics = service.resources.displayMetrics
        val centerX = metrics.widthPixels / 2f
        val centerY = metrics.heightPixels / 2f
        val deltaY = metrics.heightPixels * 0.35f

        val (startY, endY) = if (isForward) {
            centerY + deltaY to centerY - deltaY
        } else {
            centerY - deltaY to centerY + deltaY
        }

        return dispatchSwipeGesture(centerX, startY, centerX, endY, 300)
    }

    private suspend fun executeSwipe(fromX: Float, fromY: Float, toX: Float, toY: Float, durationMs: Long): Boolean {
        val metrics = service.resources.displayMetrics
        val startX = fromX * metrics.widthPixels
        val startY = fromY * metrics.heightPixels
        val endX = toX * metrics.widthPixels
        val endY = toY * metrics.heightPixels
        return dispatchSwipeGesture(startX, startY, endX, endY, durationMs)
    }

    private fun executeGlobalAction(action: Int): Boolean {
        return service.performGlobalAction(action)
    }

    private suspend fun executeSelect(targetId: Int?, observation: Observation): Boolean {
        return executeClick(targetId, observation)
    }

    private fun clickNodeOrParent(node: AccessibilityNodeInfo): Boolean {
        if (node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            return true
        }
        var parent = node.parent
        while (parent != null) {
            if (parent.isClickable && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                parent.recycle()
                return true
            }
            val nextParent = parent.parent
            parent.recycle()
            parent = nextParent
        }
        return false
    }

    private fun findNodeForElement(root: AccessibilityNodeInfo, element: UiElement): AccessibilityNodeInfo? {
        val bounds = Rect()
        root.getBoundsInScreen(bounds)
        if (isCloseBounds(bounds, element.bounds)) {
            return root
        }
        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            val found = findNodeForElement(child, element)
            if (found != null) {
                if (found !== child) {
                    child.recycle()
                }
                return found
            }
            child.recycle()
        }
        return null
    }

    private fun isCloseBounds(a: Rect, b: RectBounds, tolerancePx: Int = 8): Boolean {
        return abs(a.left - b.left) <= tolerancePx &&
            abs(a.top - b.top) <= tolerancePx &&
            abs(a.right - b.right) <= tolerancePx &&
            abs(a.bottom - b.bottom) <= tolerancePx
    }

    private suspend fun dispatchTapGesture(x: Float, y: Float, durationMs: Long): Boolean {
        val path = Path().apply {
            moveTo(x, y)
        }
        return dispatchGestureAsync(path, durationMs)
    }

    private suspend fun dispatchSwipeGesture(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long): Boolean {
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        return dispatchGestureAsync(path, durationMs)
    }

    private suspend fun dispatchGestureAsync(path: Path, durationMs: Long): Boolean = suspendCancellableCoroutine { continuation ->
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs.coerceAtLeast(10))
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        val dispatched = service.dispatchGesture(
            gesture,
            object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    if (continuation.isActive) continuation.resume(true)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    if (continuation.isActive) continuation.resume(false)
                }
            },
            null
        )
        if (!dispatched && continuation.isActive) {
            continuation.resume(false)
        }
    }

    companion object {
        private const val TAG = "ActionExecutor"
    }
}

package com.omnicontrol.feature.accessibility

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.omnicontrol.core.common.OmniLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class OmniAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        OmniLog.i(TAG, "Accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Can be monitored for window state changes
    }

    override fun onInterrupt() {
        OmniLog.w(TAG, "Accessibility service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance === this) {
            instance = null
        }
        serviceScope.cancel()
        OmniLog.i(TAG, "Accessibility service destroyed")
    }

    fun dumpTree(): Int {
        val startTime = System.currentTimeMillis()
        OmniLog.i(TAG, "--- Dumping Accessibility Tree ---")
        var totalNodes = 0

        val root = rootInActiveWindow
        if (root != null) {
            totalNodes = traverseNode(root, depth = 0)
            root.recycle()
        } else {
            OmniLog.w(TAG, "rootInActiveWindow is null, checking windows list")
            val windowList = windows
            for (window in windowList) {
                val windowRoot = window.root
                if (windowRoot != null) {
                    totalNodes += traverseNode(windowRoot, depth = 0)
                    windowRoot.recycle()
                }
            }
        }

        val elapsed = System.currentTimeMillis() - startTime
        OmniLog.i(TAG, "--- Dump finished: $totalNodes nodes in ${elapsed}ms ---")
        return totalNodes
    }

    private fun traverseNode(node: AccessibilityNodeInfo, depth: Int): Int {
        var count = 1
        val bounds = Rect()
        node.getBoundsInScreen(bounds)

        val indent = "  ".repeat(depth)
        val text = node.text?.toString() ?: ""
        val desc = node.contentDescription?.toString() ?: ""
        val viewId = node.viewIdResourceName ?: ""
        val className = node.className?.toString() ?: ""

        OmniLog.d(
            TAG,
            "$indent[$depth] class=$className, id=$viewId, text='$text', desc='$desc', " +
                "bounds=$bounds, clickable=${node.isClickable}, scrollable=${node.isScrollable}, " +
                "editable=${node.isEditable}"
        )

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            count += traverseNode(child, depth + 1)
            child.recycle()
        }
        return count
    }

    fun takeTestScreenshot(onComplete: ((Boolean, String) -> Unit)? = null) {
        val startTime = System.currentTimeMillis()
        OmniLog.i(TAG, "Starting screenshot capture...")

        takeScreenshot(
            Display.DEFAULT_DISPLAY,
            mainExecutor,
            object : TakeScreenshotCallback {
                override fun onSuccess(result: ScreenshotResult) {
                    val captureTime = System.currentTimeMillis() - startTime
                    OmniLog.i(TAG, "takeScreenshot API success in ${captureTime}ms")

                    val hardwareBuffer = result.hardwareBuffer
                    val colorSpace = result.colorSpace
                    val bitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, colorSpace)
                        ?.copy(Bitmap.Config.ARGB_8888, false)
                    hardwareBuffer.close()

                    if (bitmap == null) {
                        OmniLog.e(TAG, "Failed to wrap hardware buffer into bitmap")
                        onComplete?.invoke(false, "Bitmap wrap failed")
                        return
                    }

                    serviceScope.launch(Dispatchers.IO) {
                        try {
                            val file = File(cacheDir, "spike_screenshot.png")
                            FileOutputStream(file).use { out ->
                                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                            }
                            val totalTime = System.currentTimeMillis() - startTime
                            OmniLog.i(
                                TAG,
                                "Screenshot saved to ${file.absolutePath} (${file.length()} bytes) in ${totalTime}ms"
                            )
                            withContext(Dispatchers.Main) {
                                onComplete?.invoke(true, file.absolutePath)
                            }
                        } catch (e: Exception) {
                            OmniLog.e(TAG, "Error saving screenshot", e)
                            withContext(Dispatchers.Main) {
                                onComplete?.invoke(false, e.message ?: "Save error")
                            }
                        }
                    }
                }

                override fun onFailure(errorCode: Int) {
                    val elapsed = System.currentTimeMillis() - startTime
                    OmniLog.e(TAG, "takeScreenshot failed with error code: $errorCode in ${elapsed}ms")
                    onComplete?.invoke(false, "Failed code: $errorCode")
                }
            }
        )
    }

    companion object {
        const val TAG = "OmniA11y"
        var instance: OmniAccessibilityService? = null
    }
}

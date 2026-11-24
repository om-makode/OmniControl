package com.omnicontrol.feature.accessibility

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import com.omnicontrol.core.common.OmniLog
import com.omnicontrol.core.model.ActionResult
import com.omnicontrol.core.model.AgentAction
import com.omnicontrol.core.model.Observation
import com.omnicontrol.core.model.RectBounds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import java.io.File
import java.io.FileOutputStream

class OmniAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    lateinit var screenReader: ScreenReader
        private set
    lateinit var settleDetector: ScreenSettleDetector
        private set
    lateinit var actionExecutor: ActionExecutor
        private set

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        screenReader = ScreenReader(this)
        settleDetector = ScreenSettleDetector()
        actionExecutor = ActionExecutor(
            service = this,
            screenReader = screenReader,
            settleDetector = settleDetector,
            onHighlightTarget = { bounds ->
                highlightTargetListener?.invoke(bounds)
            }
        )
        OmniLog.i(TAG, "Accessibility service connected with ScreenReader & ActionExecutor")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (::settleDetector.isInitialized) {
            settleDetector.onAccessibilityEvent(event)
        }
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
        val obs = if (::screenReader.isInitialized) screenReader.captureObservation() else null
        return obs?.elements?.size ?: 0
    }

    fun takeTestScreenshot(onComplete: ((Boolean, String) -> Unit)? = null) {
        takeScreenshot(onComplete)
    }

    fun takeScreenshot(onComplete: ((Boolean, String) -> Unit)? = null) {
        val startTime = System.currentTimeMillis()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            onComplete?.invoke(false, "API < 30 not supported")
            return
        }

        takeScreenshot(
            Display.DEFAULT_DISPLAY,
            mainExecutor,
            object : TakeScreenshotCallback {
                override fun onSuccess(screenshotResult: ScreenshotResult) {
                    val hardwareBuffer = screenshotResult.hardwareBuffer
                    val colorSpace = screenshotResult.colorSpace
                    val bitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, colorSpace)?.copy(
                        Bitmap.Config.ARGB_8888,
                        false
                    )
                    hardwareBuffer.close()

                    if (bitmap == null) {
                        onComplete?.invoke(false, "Bitmap wrap returned null")
                        return
                    }

                    serviceScope.launch(Dispatchers.IO) {
                        try {
                            val outputDir = File(filesDir, "screenshots").apply { mkdirs() }
                            val file = File(outputDir, "screen_${System.currentTimeMillis()}.png")
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
        var highlightTargetListener: ((RectBounds?) -> Unit)? = null

        fun currentObservation(): Observation? {
            val s = instance ?: return null
            return if (s::screenReader.isInitialized) s.screenReader.captureObservation() else null
        }

        suspend fun executeAction(action: AgentAction): ActionResult? {
            val s = instance ?: return null
            if (!s::screenReader.isInitialized || !s::actionExecutor.isInitialized) return null
            val obs = s.screenReader.captureObservation()
            return s.actionExecutor.execute(action, obs)
        }

        suspend fun captureScreenshotBitmap(): Bitmap? {
            val s = instance ?: return null
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
            return suspendCancellableCoroutine { continuation ->
                try {
                    s.takeScreenshot(
                        Display.DEFAULT_DISPLAY,
                        s.mainExecutor,
                        object : TakeScreenshotCallback {
                            override fun onSuccess(screenshotResult: ScreenshotResult) {
                                val hardwareBuffer = screenshotResult.hardwareBuffer
                                val colorSpace = screenshotResult.colorSpace
                                val bitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, colorSpace)?.copy(
                                    Bitmap.Config.ARGB_8888,
                                    false
                                )
                                hardwareBuffer.close()
                                if (continuation.isActive) {
                                    continuation.resume(bitmap)
                                }
                            }

                            override fun onFailure(errorCode: Int) {
                                OmniLog.w(TAG, "captureScreenshotBitmap failed code: $errorCode")
                                if (continuation.isActive) {
                                    continuation.resume(null)
                                }
                            }
                        }
                    )
                } catch (e: Exception) {
                    OmniLog.e(TAG, "captureScreenshotBitmap exception", e)
                    if (continuation.isActive) {
                        continuation.resume(null)
                    }
                }
            }
        }
    }
}

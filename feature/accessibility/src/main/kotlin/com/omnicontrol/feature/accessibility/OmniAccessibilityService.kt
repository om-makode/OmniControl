package com.omnicontrol.feature.accessibility

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.view.Display
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.omnicontrol.core.common.OmniLog
import com.omnicontrol.core.model.ActionResult
import com.omnicontrol.core.model.AgentAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * The "hands and eyes" of OmniControl. Everything that reads or touches other apps goes through
 * the static helpers in the companion object.
 */
class OmniAccessibilityService : AccessibilityService() {

    private lateinit var reader: ScreenReader
    private lateinit var settle: ScreenSettleDetector
    private lateinit var performer: ActionPerformer
    lateinit var launcher: AppLauncher
        private set

    private var volUpDown = 0L
    private var volDownDown = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        reader = ScreenReader(this)
        settle = ScreenSettleDetector(packageName)
        launcher = AppLauncher(this)
        performer = ActionPerformer(this, reader, settle, launcher) { touchFeedback }
        instance = this
        OmniLog.i(TAG, "Accessibility service connected")
        connectionListener?.invoke(true)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event != null && ::settle.isInitialized) settle.onEvent(event)
    }

    /** Pressing Volume Up + Volume Down together stops whatever the assistant is doing. */
    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            val now = System.currentTimeMillis()
            when (event.keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> volUpDown = now
                KeyEvent.KEYCODE_VOLUME_DOWN -> volDownDown = now
            }
            if (volUpDown > 0 && volDownDown > 0 && kotlin.math.abs(volUpDown - volDownDown) < 400) {
                volUpDown = 0; volDownDown = 0
                OmniLog.i(TAG, "Panic keys pressed")
                panicListener?.invoke()
            }
        }
        return false
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        if (instance === this) instance = null
        connectionListener?.invoke(false)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        connectionListener?.invoke(false)
        super.onDestroy()
    }

    companion object {
        private const val TAG = "OmniA11y"

        @Volatile
        var instance: OmniAccessibilityService? = null
            private set

        /** Set by the overlay service. */
        @Volatile
        var touchFeedback: TouchFeedback? = null
        @Volatile
        var panicListener: (() -> Unit)? = null
        @Volatile
        var connectionListener: ((Boolean) -> Unit)? = null

        private val actionLock = Mutex()
        private var lastScreenshotAt = 0L

        val isConnected: Boolean get() = instance != null

        fun foregroundPackage(): String = instance?.settle?.foregroundPackage.orEmpty()

        /** Read the current screen. Runs off the main thread. */
        suspend fun snapshot(): ScreenSnapshot? {
            val s = instance ?: return null
            return withContext(Dispatchers.Default) { runCatching { s.reader.read() }.getOrNull() }
        }

        suspend fun waitForSettle(quietMs: Long = 400, maxMs: Long = 3000) {
            instance?.settle?.waitForSettle(quietMs, maxMs)
        }

        /** Perform one action (serialised: never two touches at once). */
        suspend fun perform(action: AgentAction, snap: ScreenSnapshot? = null): ActionResult {
            val s = instance ?: return ActionResult(false, "Screen control is off. Turn on OmniControl in Accessibility settings.")
            return actionLock.withLock {
                val current = snap ?: snapshot() ?: return@withLock ActionResult(false, "Could not read the screen")
                s.performer.perform(action, current)
            }
        }

        fun launcher(): AppLauncher? = instance?.launcher

        /**
         * Screenshot of the other app (our overlay hidden). Android allows about one per second,
         * so this waits if called too quickly.
         */
        suspend fun screenshot(): Bitmap? {
            val s = instance ?: return null
            val wait = 1100 - (System.currentTimeMillis() - lastScreenshotAt)
            if (wait > 0) delay(wait)
            val fb = touchFeedback
            val capture: suspend () -> Bitmap? = { s.capture() }
            val bmp = if (fb != null) fb.withOverlaysHidden(capture) else capture()
            lastScreenshotAt = System.currentTimeMillis()
            return bmp
        }
    }

    private suspend fun capture(): Bitmap? = suspendCancellableCoroutine { cont ->
        try {
            takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
                override fun onSuccess(result: ScreenshotResult) {
                    val hb = result.hardwareBuffer
                    val bmp = try {
                        Bitmap.wrapHardwareBuffer(hb, result.colorSpace)?.copy(Bitmap.Config.ARGB_8888, false)
                    } finally { hb.close() }
                    if (cont.isActive) cont.resume(bmp)
                }

                override fun onFailure(errorCode: Int) {
                    OmniLog.w(TAG, "screenshot failed: $errorCode")
                    if (cont.isActive) cont.resume(null)
                }
            })
        } catch (e: Exception) {
            OmniLog.w(TAG, "screenshot exception", e)
            if (cont.isActive) cont.resume(null)
        }
    }
}

package com.omnicontrol.feature.overlay.window

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import com.omnicontrol.core.common.OmniLog

/**
 * Adds Compose content as floating windows. Each add creates a fresh ComposeView with its own
 * lifecycle owner (re-attaching an owner crashes), so windows can be shown/hidden freely.
 */
class OverlayWindowManager(private val context: Context) {

    private class Entry(val view: View, val params: WindowManager.LayoutParams, val owner: OverlayLifecycleOwner)

    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val windows = LinkedHashMap<String, Entry>()

    fun params(
        width: Int = WRAP_CONTENT,
        height: Int = WRAP_CONTENT,
        gravity: Int = Gravity.TOP or Gravity.START,
        x: Int = 0,
        y: Int = 0,
        touchable: Boolean = true,
        focusable: Boolean = false,
        layoutInScreen: Boolean = false
    ): WindowManager.LayoutParams {
        var flags = WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        if (!focusable) flags = flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        if (!touchable) flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        if (layoutInScreen) flags = flags or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        return WindowManager.LayoutParams(width, height, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, flags, PixelFormat.TRANSLUCENT)
            .apply {
                this.gravity = gravity
                this.x = x
                this.y = y
                if (layoutInScreen) {
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                    fitInsetsTypes = 0
                }
            }
    }

    fun show(key: String, params: WindowManager.LayoutParams, content: @Composable () -> Unit): View? {
        remove(key)
        return try {
            val view = ComposeView(context).apply { setContent(content) }
            val owner = OverlayLifecycleOwner().also { it.attach(view) }
            wm.addView(view, params)
            windows[key] = Entry(view, params, owner)
            view
        } catch (e: Exception) {
            OmniLog.e(TAG, "Could not show window '$key'", e)
            null
        }
    }

    fun view(key: String): View? = windows[key]?.view
    fun paramsOf(key: String): WindowManager.LayoutParams? = windows[key]?.params
    fun has(key: String): Boolean = windows.containsKey(key)

    fun update(key: String) {
        val e = windows[key] ?: return
        try { wm.updateViewLayout(e.view, e.params) } catch (ex: Exception) { OmniLog.w(TAG, "update '$key' failed", ex) }
    }

    fun remove(key: String) {
        val e = windows.remove(key) ?: return
        try { wm.removeViewImmediate(e.view) } catch (_: Exception) {}
        e.owner.destroy()
    }

    fun removeAll() = windows.keys.toList().forEach { remove(it) }

    fun setTouchable(key: String, touchable: Boolean) {
        val e = windows[key] ?: return
        val old = e.params.flags
        e.params.flags = if (touchable) old and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        else old or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        if (old != e.params.flags) update(key)
    }

    fun setFocusable(key: String, focusable: Boolean) {
        val e = windows[key] ?: return
        val old = e.params.flags
        e.params.flags = if (focusable) old and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        else old or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        if (old != e.params.flags) update(key)
    }

    fun setAlpha(key: String, alpha: Float) {
        val e = windows[key] ?: return
        if (e.params.alpha == alpha) return
        e.params.alpha = alpha
        update(key)
    }

    fun keys(): Set<String> = windows.keys.toSet()

    companion object {
        private const val TAG = "OmniWindows"
    }
}

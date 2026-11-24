package com.omnicontrol.feature.overlay.window

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import com.omnicontrol.core.common.OmniLog

class OverlayWindowManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val windows = mutableMapOf<String, Pair<View, WindowManager.LayoutParams>>()
    private val lifecycleOwners = mutableMapOf<String, OverlayLifecycleOwner>()

    fun createDefaultParams(
        width: Int = WRAP_CONTENT,
        height: Int = WRAP_CONTENT,
        gravity: Int = Gravity.TOP or Gravity.START,
        x: Int = 0,
        y: Int = 0,
        flags: Int = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
    ): WindowManager.LayoutParams {
        val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        return WindowManager.LayoutParams(
            width,
            height,
            windowType,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            this.gravity = gravity
            this.x = x
            this.y = y
        }
    }

    fun addWindow(key: String, view: View, params: WindowManager.LayoutParams) {
        try {
            if (windows.containsKey(key)) {
                removeWindow(key)
            }
            val owner = OverlayLifecycleOwner()
            owner.attach(view)
            lifecycleOwners[key] = owner

            windowManager.addView(view, params)
            windows[key] = view to params
            OmniLog.i(TAG, "Window '$key' added successfully")
        } catch (e: Exception) {
            OmniLog.e(TAG, "Failed to add window '$key'", e)
        }
    }

    fun updateWindow(key: String, params: WindowManager.LayoutParams) {
        val entry = windows[key] ?: return
        try {
            windowManager.updateViewLayout(entry.first, params)
            windows[key] = entry.first to params
        } catch (e: Exception) {
            OmniLog.e(TAG, "Failed to update window '$key'", e)
        }
    }

    fun removeWindow(key: String) {
        val entry = windows.remove(key) ?: return
        try {
            lifecycleOwners.remove(key)?.destroy()
            windowManager.removeViewImmediate(entry.first)
            OmniLog.i(TAG, "Window '$key' removed successfully")
        } catch (e: Exception) {
            OmniLog.w(TAG, "Error removing window '$key'", e)
        }
    }

    fun setTouchable(key: String, touchable: Boolean) {
        val entry = windows[key] ?: return
        val params = entry.second
        if (touchable) {
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        } else {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        updateWindow(key, params)
    }

    fun setFocusable(key: String, focusable: Boolean) {
        val entry = windows[key] ?: return
        val params = entry.second
        if (focusable) {
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        } else {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
        updateWindow(key, params)
    }

    fun getParams(key: String): WindowManager.LayoutParams? {
        return windows[key]?.second
    }

    fun hasWindow(key: String): Boolean = windows.containsKey(key)

    fun removeAll() {
        val keys = windows.keys.toList()
        for (k in keys) {
            removeWindow(k)
        }
    }

    companion object {
        private const val TAG = "OmniOverlay"
    }
}

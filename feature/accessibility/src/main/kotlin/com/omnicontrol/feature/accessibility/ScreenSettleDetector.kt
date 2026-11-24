package com.omnicontrol.feature.accessibility

import android.view.accessibility.AccessibilityEvent
import com.omnicontrol.core.common.OmniLog
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicLong

class ScreenSettleDetector {

    private val lastEventTime = AtomicLong(System.currentTimeMillis())

    fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val type = event?.eventType ?: return
        when (type) {
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                lastEventTime.set(System.currentTimeMillis())
            }
        }
    }

    suspend fun waitForSettle(debounceMs: Long = 300, maxWaitMs: Long = 3000): Boolean {
        val startTime = System.currentTimeMillis()
        OmniLog.d(TAG, "Waiting for screen settle (debounce: ${debounceMs}ms, max: ${maxWaitMs}ms)")

        // Allow initial events to fire after an action
        delay(50)

        while (true) {
            val now = System.currentTimeMillis()
            val quietDuration = now - lastEventTime.get()

            if (quietDuration >= debounceMs) {
                val totalWait = now - startTime
                OmniLog.d(TAG, "Screen settled after ${totalWait}ms (${quietDuration}ms quiet)")
                return true
            }

            if (now - startTime >= maxWaitMs) {
                OmniLog.w(TAG, "Screen settle timed out after ${maxWaitMs}ms")
                return false
            }

            delay(40)
        }
    }

    companion object {
        private const val TAG = "ScreenSettleDetector"
    }
}

package com.omnicontrol.feature.accessibility

import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicLong

/** Waits until the foreground app stops changing (no UI events for a short quiet period). */
class ScreenSettleDetector(private val ownPackage: String) {

    private val lastEventTime = AtomicLong(0L)

    @Volatile
    var foregroundPackage: String = ""
        private set

    fun onEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        if (pkg == ownPackage) return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                lastEventTime.set(System.currentTimeMillis())
                if (pkg != "com.android.systemui") foregroundPackage = pkg
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED,
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> lastEventTime.set(System.currentTimeMillis())
        }
    }

    /**
     * @param quietMs how long the screen must be still
     * @param maxMs give up after this long (video players never stop changing)
     */
    suspend fun waitForSettle(quietMs: Long = 350, maxMs: Long = 2500, minMs: Long = 150) {
        val start = System.currentTimeMillis()
        delay(minMs)
        while (true) {
            val now = System.currentTimeMillis()
            if (now - lastEventTime.get() >= quietMs) return
            if (now - start >= maxMs) return
            delay(40)
        }
    }

    /** Wait until [pkg] is in front (used after launching an app). */
    suspend fun waitForPackage(pkg: String, maxMs: Long = 4000): Boolean {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < maxMs) {
            if (foregroundPackage == pkg) return true
            delay(80)
        }
        return foregroundPackage == pkg
    }
}

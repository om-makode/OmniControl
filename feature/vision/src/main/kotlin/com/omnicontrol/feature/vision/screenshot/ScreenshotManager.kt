package com.omnicontrol.feature.vision.screenshot

import android.graphics.Bitmap
import android.graphics.Color
import com.omnicontrol.core.common.OmniLog
import com.omnicontrol.feature.accessibility.OmniAccessibilityService
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object ScreenshotManager {
    private const val TAG = "ScreenshotManager"
    private const val MIN_CAPTURE_INTERVAL_MS = 1100L
    private const val MAX_WIDTH_PX = 1080

    private val mutex = Mutex()
    private var lastCaptureTimeMs: Long = 0L
    private var cachedBitmap: Bitmap? = null
    private var cachedFp: String? = null
    var isSecureScreen: Boolean = false
        private set

    suspend fun capture(currentFp: String? = null): Bitmap? = mutex.withLock {
        val now = System.currentTimeMillis()

        // Reuse cached bitmap if fingerprint unchanged and recent
        if (currentFp != null && currentFp == cachedFp && cachedBitmap != null && !cachedBitmap!!.isRecycled) {
            OmniLog.d(TAG, "Reusing cached screenshot for fp: $currentFp")
            return cachedBitmap
        }

        // Rate limit check
        val elapsed = now - lastCaptureTimeMs
        if (elapsed < MIN_CAPTURE_INTERVAL_MS && cachedBitmap != null && !cachedBitmap!!.isRecycled) {
            OmniLog.d(TAG, "Rate limit active ($elapsed ms < $MIN_CAPTURE_INTERVAL_MS ms), returning cached bitmap")
            return cachedBitmap
        }

        OmniLog.d(TAG, "Requesting screenshot from accessibility service...")
        val rawBitmap = OmniAccessibilityService.captureScreenshotBitmap()
        lastCaptureTimeMs = System.currentTimeMillis()

        if (rawBitmap == null) {
            OmniLog.w(TAG, "Failed to capture screenshot")
            return cachedBitmap
        }

        // Check for secure screen (all black window)
        isSecureScreen = checkIsSecure(rawBitmap)
        if (isSecureScreen) {
            OmniLog.w(TAG, "Detected secure screen (mostly black pixels / protected window)")
        }

        // Downscale if width > MAX_WIDTH_PX
        val finalBitmap = if (rawBitmap.width > MAX_WIDTH_PX) {
            val aspect = rawBitmap.height.toFloat() / rawBitmap.width.toFloat()
            val newHeight = (MAX_WIDTH_PX * aspect).toInt()
            val scaled = Bitmap.createScaledBitmap(rawBitmap, MAX_WIDTH_PX, newHeight, true)
            rawBitmap.recycle()
            scaled
        } else {
            rawBitmap
        }

        cachedBitmap?.recycle()
        cachedBitmap = finalBitmap
        cachedFp = currentFp
        return finalBitmap
    }

    private fun checkIsSecure(bitmap: Bitmap): Boolean {
        var blackPixels = 0
        val sampleStep = 20
        var totalSamples = 0

        val w = bitmap.width
        val h = bitmap.height

        for (x in 0 until w step sampleStep) {
            for (y in 0 until h step sampleStep) {
                totalSamples++
                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                if (r < 10 && g < 10 && b < 10) {
                    blackPixels++
                }
            }
        }

        return totalSamples > 0 && (blackPixels.toFloat() / totalSamples.toFloat()) > 0.98f
    }

    fun clearCache() {
        cachedBitmap?.recycle()
        cachedBitmap = null
        cachedFp = null
    }
}

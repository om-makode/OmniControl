package com.omnicontrol.core.common

import android.util.Log

object OmniLog {
    private const val PREFIX = "Omni"

    fun d(tag: String, message: String) {
        Log.d(normalizeTag(tag), message)
    }

    fun i(tag: String, message: String) {
        Log.i(normalizeTag(tag), message)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.w(normalizeTag(tag), message, throwable)
        } else {
            Log.w(normalizeTag(tag), message)
        }
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e(normalizeTag(tag), message, throwable)
        } else {
            Log.e(normalizeTag(tag), message)
        }
    }

    private fun normalizeTag(tag: String): String {
        return if (tag.startsWith(PREFIX)) tag else "$PREFIX$tag"
    }
}

package com.omnicontrol.core.common

import android.util.Log

object OmniLog {
    private const val PREFIX = "Omni"

    fun d(tag: String, message: String) {
        try {
            Log.d(normalizeTag(tag), message)
        } catch (_: RuntimeException) {
            println("D/${normalizeTag(tag)}: $message")
        }
    }

    fun i(tag: String, message: String) {
        try {
            Log.i(normalizeTag(tag), message)
        } catch (_: RuntimeException) {
            println("I/${normalizeTag(tag)}: $message")
        }
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        try {
            if (throwable != null) {
                Log.w(normalizeTag(tag), message, throwable)
            } else {
                Log.w(normalizeTag(tag), message)
            }
        } catch (_: RuntimeException) {
            println("W/${normalizeTag(tag)}: $message")
            throwable?.printStackTrace()
        }
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        try {
            if (throwable != null) {
                Log.e(normalizeTag(tag), message, throwable)
            } else {
                Log.e(normalizeTag(tag), message)
            }
        } catch (_: RuntimeException) {
            System.err.println("E/${normalizeTag(tag)}: $message")
            throwable?.printStackTrace()
        }
    }

    private fun normalizeTag(tag: String): String {
        return if (tag.startsWith(PREFIX)) tag else "$PREFIX$tag"
    }
}

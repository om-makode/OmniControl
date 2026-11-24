package com.omnicontrol.core.common

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

object CrashHandler {
    private const val TAG = "CrashHandler"

    fun init(context: Context) {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                OmniLog.e(TAG, "Uncaught exception on thread ${thread.name}", throwable)
                val crashDir = File(context.filesDir, "crashes").apply { mkdirs() }
                val crashFile = File(crashDir, "crash_${System.currentTimeMillis()}.txt")

                val sw = StringWriter()
                val pw = PrintWriter(sw)
                throwable.printStackTrace(pw)
                pw.flush()

                crashFile.writeText("Thread: ${thread.name}\n\nStacktrace:\n$sw")
                OmniLog.i(TAG, "Wrote crash report to ${crashFile.absolutePath}")
            } catch (e: Exception) {
                // Ignore secondary errors in crash handler
            } finally {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    fun listCrashes(context: Context): List<File> {
        val crashDir = File(context.filesDir, "crashes")
        if (!crashDir.exists()) return emptyList()
        return crashDir.listFiles()?.filter { it.extension == "txt" }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }
}

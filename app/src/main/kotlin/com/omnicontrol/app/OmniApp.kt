package com.omnicontrol.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.omnicontrol.core.common.OmniLog
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class OmniApp : Application() {

    override fun onCreate() {
        super.onCreate()
        OmniLog.i("App", "OmniApp initialized with Hilt")
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID_OVERLAY,
                "OmniControl Assistant",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "OmniControl overlay and voice session notifications"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID_OVERLAY = "omnicontrol_overlay_channel"
    }
}

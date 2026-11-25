package com.omnicontrol.feature.permissions

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.speech.SpeechRecognizer
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.omnicontrol.feature.accessibility.OmniAccessibilityService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PermissionsState(
    val overlay: Boolean = false,
    val accessibilityEnabled: Boolean = false,
    val accessibilityConnected: Boolean = false,
    val microphone: Boolean = false,
    val notifications: Boolean = false,
    val batteryUnrestricted: Boolean = false,
    val speechAvailable: Boolean = false,
    val offlineSpeech: Boolean = false
) {
    val ready: Boolean get() = overlay && accessibilityEnabled && microphone
}

class PermissionChecker(private val context: Context) {

    private val _state = MutableStateFlow(compute())
    val state: StateFlow<PermissionsState> = _state.asStateFlow()

    fun refresh() { _state.value = compute() }

    private fun compute(): PermissionsState {
        val expected = ComponentName(context, OmniAccessibilityService::class.java).flattenToString()
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        val notif = (Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return PermissionsState(
            overlay = Settings.canDrawOverlays(context),
            accessibilityEnabled = enabled.split(':').any { it.equals(expected, true) },
            accessibilityConnected = OmniAccessibilityService.isConnected,
            microphone = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED,
            notifications = notif,
            batteryUnrestricted = pm.isIgnoringBatteryOptimizations(context.packageName),
            speechAvailable = SpeechRecognizer.isRecognitionAvailable(context),
            offlineSpeech = Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        )
    }

    companion object {
        fun overlaySettings(context: Context) =
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))

        fun accessibilitySettings() = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

        fun appDetails(context: Context) =
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))

        fun batterySettings(context: Context) =
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
    }
}

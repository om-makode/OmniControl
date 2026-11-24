package com.omnicontrol.feature.permissions

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.omnicontrol.feature.accessibility.OmniAccessibilityService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

data class ModelsState(
    val asr: Boolean = false,
    val vad: Boolean = false,
    val kws: Boolean = false,
    val llm: Boolean = false
) {
    val allEssentialReady: Boolean get() = asr && vad && llm
}

data class PermissionsState(
    val overlay: Boolean = false,
    val accessibility: Boolean = false,
    val accessibilityConnected: Boolean = false,
    val microphone: Boolean = false,
    val notifications: Boolean = false,
    val batteryUnrestricted: Boolean = false,
    val modelsInstalled: ModelsState = ModelsState()
) {
    val allRequiredGranted: Boolean
        get() = overlay && accessibility && microphone && notifications
}

class PermissionChecker(private val context: Context) {

    private val _state = MutableStateFlow(computeState())
    val state: StateFlow<PermissionsState> = _state.asStateFlow()

    fun refresh(): PermissionsState {
        val newState = computeState()
        _state.value = newState
        return newState
    }

    private fun computeState(): PermissionsState {
        val overlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }

        val expectedComponent = ComponentName(context, OmniAccessibilityService::class.java).flattenToString()
        val enabledA11yServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: ""
        val a11yEnabled = enabledA11yServices.split(':').any {
            it.equals(expectedComponent, ignoreCase = true)
        }
        val a11yConnected = OmniAccessibilityService.instance != null

        val micGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        val notifGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED && NotificationManagerCompat.from(context).areNotificationsEnabled()
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val batteryUnrestricted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false
        } else {
            true
        }

        val modelsDir = File(context.filesDir, "models")
        val asrReady = File(modelsDir, "asr/encoder.onnx").exists()
        val vadReady = File(modelsDir, "vad/silero_vad.onnx").exists()
        val kwsReady = File(modelsDir, "kws").exists()
        val llmReady = (File(modelsDir, "llm").listFiles { f -> f.extension == "gguf" }?.isNotEmpty() == true)

        return PermissionsState(
            overlay = overlay,
            accessibility = a11yEnabled,
            accessibilityConnected = a11yConnected,
            microphone = micGranted,
            notifications = notifGranted,
            batteryUnrestricted = batteryUnrestricted,
            modelsInstalled = ModelsState(
                asr = asrReady,
                vad = vadReady,
                kws = kwsReady,
                llm = llmReady
            )
        )
    }
}

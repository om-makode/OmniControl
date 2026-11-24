package com.omnicontrol.app.ui.screens

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.omnicontrol.app.ui.theme.AccentError
import com.omnicontrol.app.ui.theme.AccentPrimary
import com.omnicontrol.app.ui.theme.AccentSuccess
import com.omnicontrol.app.ui.theme.AccentWarning
import com.omnicontrol.app.ui.theme.DarkSurfaceVariant
import com.omnicontrol.app.ui.theme.TextMuted
import com.omnicontrol.app.ui.theme.TextSecondary
import com.omnicontrol.feature.permissions.PermissionChecker

@Composable
fun CapabilitiesScreen(
    permissionChecker: PermissionChecker
) {
    val context = LocalContext.current
    val state by permissionChecker.state.collectAsState()

    val totalRamGb = getTotalRamGb(context)
    val ramTier = when {
        totalRamGb <= 6.0 -> "Low (0.6B–1.5B LLM)"
        totalRamGb <= 8.5 -> "Medium (1.5B–3B LLM, on-demand vision)"
        else -> "High (3B–4B LLM, comfortable)"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "Device Capabilities",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Hardware diagnostics and supported features on your device.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Hardware Summary Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Hardware Profile",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = AccentPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))
                ProfileRow("Device", "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}")
                ProfileRow("Android OS", "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                ProfileRow("Architecture", Build.SUPPORTED_ABIS.firstOrNull() ?: "arm64-v8a")
                ProfileRow("Total RAM", "${"%.1f".format(totalRamGb)} GB ($ramTier)")
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "Feature Availability",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(12.dp))

        CapabilityItem(
            name = "Floating Overlay Bubble",
            status = if (state.overlay) CapabilityStatus.READY else CapabilityStatus.BLOCKED,
            detail = if (state.overlay) "Overlay permission granted" else "Requires 'Display over other apps'"
        )

        CapabilityItem(
            name = "Screen Reading",
            status = if (state.accessibilityConnected) CapabilityStatus.READY else CapabilityStatus.BLOCKED,
            detail = if (state.accessibilityConnected) "Active via AccessibilityService" else "Accessibility service not connected"
        )

        CapabilityItem(
            name = "Action Execution (Taps & Swipes)",
            status = if (state.accessibilityConnected) CapabilityStatus.READY else CapabilityStatus.BLOCKED,
            detail = if (state.accessibilityConnected) "Gesture dispatch available" else "Requires accessibility service"
        )

        CapabilityItem(
            name = "Screenshot Capture",
            status = if (state.accessibilityConnected && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) CapabilityStatus.READY else CapabilityStatus.BLOCKED,
            detail = "Native hardware buffer capture (API 30+)"
        )

        CapabilityItem(
            name = "Continuous Offline Speech (ASR)",
            status = if (state.microphone && state.modelsInstalled.asr && state.modelsInstalled.vad) CapabilityStatus.READY
            else if (state.microphone) CapabilityStatus.WARNING
            else CapabilityStatus.BLOCKED,
            detail = if (state.microphone && state.modelsInstalled.asr) "Zipformer + Silero VAD installed"
            else if (state.microphone) "Microphone granted, ASR models missing"
            else "Requires microphone permission"
        )

        CapabilityItem(
            name = "Local AI Reasoning (LLM)",
            status = if (state.modelsInstalled.llm) CapabilityStatus.READY else CapabilityStatus.WARNING,
            detail = if (state.modelsInstalled.llm) "llama.cpp runtime & GGUF model ready" else "GGUF model not found in storage"
        )

        CapabilityItem(
            name = "Protected Screens (Banking / DRM)",
            status = CapabilityStatus.UNSUPPORTED,
            detail = "FLAG_SECURE screens blocked by Android design. Safe handoff active."
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}

enum class CapabilityStatus { READY, WARNING, BLOCKED, UNSUPPORTED }

@Composable
fun CapabilityItem(
    name: String,
    status: CapabilityStatus,
    detail: String
) {
    val (statusColor, statusText) = when (status) {
        CapabilityStatus.READY -> AccentSuccess to "Ready"
        CapabilityStatus.WARNING -> AccentWarning to "Needs Model"
        CapabilityStatus.BLOCKED -> AccentError to "Blocked"
        CapabilityStatus.UNSUPPORTED -> TextMuted to "Restricted by OS"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = statusColor
                )
            }
        }
    }
}

@Composable
fun ProfileRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        Text(text = value, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = MaterialTheme.colorScheme.onSurface)
    }
}

private fun getTotalRamGb(context: Context): Double {
    return try {
        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actManager.getMemoryInfo(memInfo)
        memInfo.totalMem.toDouble() / (1024 * 1024 * 1024)
    } catch (_: Exception) {
        8.0
    }
}

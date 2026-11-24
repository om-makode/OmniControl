package com.omnicontrol.app.ui.screens

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.omnicontrol.app.ui.theme.AccentError
import com.omnicontrol.app.ui.theme.AccentPrimary
import com.omnicontrol.app.ui.theme.AccentSuccess
import com.omnicontrol.app.ui.theme.DarkSurfaceVariant
import com.omnicontrol.app.ui.theme.TextMuted
import com.omnicontrol.app.ui.theme.TextSecondary
import com.omnicontrol.core.common.settings.OmniSettings
import com.omnicontrol.feature.permissions.PermissionChecker
import kotlinx.coroutines.launch

@Composable
fun PermissionsScreen(
    permissionChecker: PermissionChecker,
    omniSettings: OmniSettings,
    onNavigateToDashboard: () -> Unit,
    onNavigateToModels: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by permissionChecker.state.collectAsState()

    val micLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        permissionChecker.refresh()
    }

    val notifLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        permissionChecker.refresh()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "Setup & Permissions",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Grant required permissions so OmniControl can see, listen, and assist you.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(16.dp))

        // All set banner
        AnimatedVisibility(visible = state.allRequiredGranted) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = AccentSuccess.copy(alpha = 0.15f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "✓ All Required Permissions Granted!",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = AccentSuccess
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            scope.launch {
                                omniSettings.setOnboardingDone(true)
                                onNavigateToDashboard()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentSuccess)
                    ) {
                        Text("Continue to Dashboard", color = Color.Black)
                    }
                }
            }
        }

        // 1. Overlay
        PermissionCard(
            step = "Step 1",
            title = "Display Over Other Apps",
            description = "Draws the floating assistant bubble and expandable response panel above other apps.",
            isGranted = state.overlay,
            onAction = {
                safeStartActivity(
                    context,
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                )
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 2. Allow Restricted Settings (Android 13+ info)
        PermissionCard(
            step = "Step 2",
            title = "Allow Restricted Settings (Sideloaded APK)",
            description = "Android 13+ requires unlocking restricted settings in App Info before Accessibility can be toggled.",
            helpText = "1. Tap 'Open App Info'.\n2. Tap the three dots (⋮) in the top-right corner.\n3. Tap 'Allow restricted settings' and confirm with your PIN.\n(If ⋮ is missing, attempt Step 3 once first).",
            isGranted = null,
            actionLabel = "Open App Info",
            onAction = {
                safeStartActivity(
                    context,
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                )
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 3. Accessibility Service
        PermissionCard(
            step = "Step 3",
            title = "Accessibility Service",
            description = "Allows OmniControl to read UI elements and perform taps/gestures on your behalf.",
            helpText = "In Accessibility settings, find 'Downloaded apps' or 'Installed services', tap 'OmniControl Service', and switch it ON.",
            isGranted = state.accessibility && state.accessibilityConnected,
            onAction = {
                safeStartActivity(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 4. Microphone
        PermissionCard(
            step = "Step 4",
            title = "Microphone Access",
            description = "Enables voice recognition and continuous speech interaction.",
            isGranted = state.microphone,
            actionLabel = "Grant Microphone",
            onAction = {
                micLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 5. Notifications
        PermissionCard(
            step = "Step 5",
            title = "Notifications",
            description = "Required to keep the assistant foreground service alive and show quick status.",
            isGranted = state.notifications,
            actionLabel = "Grant Notifications",
            onAction = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    safeStartActivity(context, Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    })
                }
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 6. Battery Unrestricted (Optional)
        PermissionCard(
            step = "Step 6",
            title = "Battery Unrestricted (Optional)",
            description = "Prevents OEM aggressive task killers from killing the assistant service.",
            helpText = "Set Battery usage to 'Unrestricted' or ignore battery optimizations.",
            isGranted = state.batteryUnrestricted,
            actionLabel = "Ignore Optimizations",
            onAction = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    safeStartActivity(
                        context,
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
                    )
                }
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 7. Models
        PermissionCard(
            step = "Step 7",
            title = "AI & Speech Models",
            description = "ASR, VAD, and GGUF LLM models needed for complete offline operation.",
            isGranted = state.modelsInstalled.allEssentialReady,
            actionLabel = "Manage Models",
            onAction = onNavigateToModels
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun PermissionCard(
    step: String,
    title: String,
    description: String,
    helpText: String? = null,
    isGranted: Boolean?,
    actionLabel: String = "Open Settings",
    onAction: () -> Unit
) {
    var expandedHelp by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = step,
                    style = MaterialTheme.typography.labelMedium,
                    color = AccentPrimary
                )
                if (isGranted != null) {
                    Text(
                        text = if (isGranted) "✓ Granted" else "✗ Needed",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (isGranted) AccentSuccess else AccentError,
                        modifier = Modifier
                            .background(
                                color = (if (isGranted) AccentSuccess else AccentError).copy(alpha = 0.15f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted
            )

            if (helpText != null) {
                Spacer(modifier = Modifier.height(6.dp))
                TextButton(onClick = { expandedHelp = !expandedHelp }) {
                    Text(if (expandedHelp) "Hide Instructions" else "How to Enable ▼", color = AccentPrimary)
                }
                AnimatedVisibility(visible = expandedHelp) {
                    Text(
                        text = helpText,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = onAction,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(actionLabel)
            }
        }
    }
}

fun safeStartActivity(context: Context, intent: Intent) {
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        try {
            context.startActivity(Intent(Settings.ACTION_SETTINGS))
        } catch (_: Exception) {}
    }
}

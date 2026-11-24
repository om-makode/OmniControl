package com.omnicontrol.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.omnicontrol.app.spike.SpikeOverlayService
import com.omnicontrol.app.ui.theme.OmniControlTheme
import com.omnicontrol.feature.accessibility.OmniAccessibilityService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OmniControlTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainSpikeScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun MainSpikeScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var statusText by remember { mutableStateOf("Ready") }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.entries.all { it.value }
        statusText = if (granted) "All permissions granted" else "Some permissions denied"
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "OmniControl Spikes",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Status: $statusText",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Request Permissions
        Button(
            onClick = {
                val perms = mutableListOf(Manifest.permission.RECORD_AUDIO)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    perms.add(Manifest.permission.POST_NOTIFICATIONS)
                }
                permissionLauncher.launch(perms.toTypedArray())
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Request Permissions (Mic & Notif)")
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Open Overlay Permission
        Button(
            onClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    )
                    context.startActivity(intent)
                } else {
                    Toast.makeText(context, "Overlay permission already granted", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Grant Overlay Permission")
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Open Accessibility Settings
        Button(
            onClick = {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Open Accessibility Settings")
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("--- Spike A (Accessibility) ---", style = MaterialTheme.typography.labelLarge)
        Spacer(modifier = Modifier.height(8.dp))

        // Spike A: Dump tree after 5s
        Button(
            onClick = {
                val service = OmniAccessibilityService.instance
                if (service == null) {
                    Toast.makeText(context, "Accessibility service not connected!", Toast.LENGTH_SHORT).show()
                    statusText = "A11y Service not connected"
                    return@Button
                }
                statusText = "Switch to target app! Dumping in 5 seconds..."
                scope.launch {
                    delay(5000)
                    val count = service.dumpTree()
                    statusText = "Dumped $count nodes (check logcat)"
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Spike A: Dump Tree in 5s")
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Spike A: Take screenshot after 5s
        Button(
            onClick = {
                val service = OmniAccessibilityService.instance
                if (service == null) {
                    Toast.makeText(context, "Accessibility service not connected!", Toast.LENGTH_SHORT).show()
                    statusText = "A11y Service not connected"
                    return@Button
                }
                statusText = "Switch to target app! Capturing screenshot in 5 seconds..."
                scope.launch {
                    delay(5000)
                    service.takeTestScreenshot { success, pathOrError ->
                        statusText = if (success) "Screenshot saved: $pathOrError" else "Screenshot failed: $pathOrError"
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Spike A: Take Screenshot in 5s")
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("--- Spike D (Overlay Mic FGS) ---", style = MaterialTheme.typography.labelLarge)
        Spacer(modifier = Modifier.height(8.dp))

        // Spike D: Start Overlay Service
        Button(
            onClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
                    Toast.makeText(context, "Grant overlay permission first!", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                val intent = Intent(context, SpikeOverlayService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                statusText = "Overlay Bubble Started. Tap the red circle!"
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Spike D: Start Overlay Bubble")
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Spike D: Stop Overlay Service
        Button(
            onClick = {
                val intent = Intent(context, SpikeOverlayService::class.java).apply {
                    action = SpikeOverlayService.ACTION_STOP_SERVICE
                }
                context.startService(intent)
                statusText = "Overlay Bubble Stopped"
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Spike D: Stop Overlay Bubble")
        }
    }
}

package com.omnicontrol.app.ui.screens

import android.content.Intent
import android.os.Build
import android.widget.Toast
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.omnicontrol.app.BuildConfig
import com.omnicontrol.app.spike.SpikeOverlayService
import com.omnicontrol.app.ui.theme.DarkSurfaceVariant
import com.omnicontrol.app.ui.theme.TextMuted
import com.omnicontrol.app.ui.theme.TextSecondary
import com.omnicontrol.feature.accessibility.OmniAccessibilityService
import com.omnicontrol.feature.voice.AsrSpike
import com.omnicontrol.native_llama.LlamaNative
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun DeveloperScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var statusText by remember { mutableStateOf("Ready") }
    var asrPartial by remember { mutableStateOf("") }
    var asrFinal by remember { mutableStateOf("") }
    var vadState by remember { mutableStateOf(false) }
    var llamaOutput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "Developer & Spikes",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Diagnostics, benchmarks, and isolated feasibility spikes.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(16.dp))

        // System Info Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Build Information",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(6.dp))
                ProfileRow("Application ID", BuildConfig.APPLICATION_ID)
                ProfileRow("Build Type", BuildConfig.BUILD_TYPE)
                ProfileRow("Version Name", BuildConfig.VERSION_NAME)
                ProfileRow("Version Code", BuildConfig.VERSION_CODE.toString())
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("Status: $statusText", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
        Spacer(modifier = Modifier.height(12.dp))

        // Spike A
        Text("Spike A — Accessibility & Screenshot", style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(6.dp))
        Button(
            onClick = {
                val service = OmniAccessibilityService.instance
                if (service == null) {
                    Toast.makeText(context, "Accessibility service not connected!", Toast.LENGTH_SHORT).show()
                    statusText = "A11y Service not connected"
                    return@Button
                }
                statusText = "Switch to target app! Dumping in 5s..."
                scope.launch {
                    delay(5000)
                    val count = service.dumpTree()
                    statusText = "Dumped $count nodes (check logcat)"
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Dump A11y Tree in 5s")
        }
        Spacer(modifier = Modifier.height(6.dp))
        Button(
            onClick = {
                val service = OmniAccessibilityService.instance
                if (service == null) {
                    Toast.makeText(context, "Accessibility service not connected!", Toast.LENGTH_SHORT).show()
                    statusText = "A11y Service not connected"
                    return@Button
                }
                statusText = "Capturing screenshot in 5s..."
                scope.launch {
                    delay(5000)
                    service.takeTestScreenshot { success, pathOrError ->
                        statusText = if (success) "Saved: $pathOrError" else "Failed: $pathOrError"
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Capture Screenshot in 5s")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Spike B
        Text("Spike B — Sherpa-ONNX Speech & VAD", style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(4.dp))
        Text("VAD: ${if (vadState) "Speaking" else "Silent"} | Partial: $asrPartial", style = MaterialTheme.typography.bodySmall, color = TextMuted)
        Spacer(modifier = Modifier.height(6.dp))
        Button(
            onClick = {
                if (AsrSpike.isRunning) {
                    AsrSpike.stopSpike()
                    statusText = "ASR Spike Stopped"
                } else {
                    statusText = "ASR Spike Running. Speak into mic!"
                    AsrSpike.startSpike(
                        context = context,
                        onPartial = { asrPartial = it },
                        onFinal = { asrFinal = it },
                        onVadStatus = { vadState = it }
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (AsrSpike.isRunning) "Stop ASR Spike" else "Start ASR Spike")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Spike C
        Text("Spike C — Llama.cpp with Grammar", style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(4.dp))
        Text("Output: $llamaOutput", style = MaterialTheme.typography.bodySmall, color = TextMuted)
        Spacer(modifier = Modifier.height(6.dp))
        Button(
            onClick = {
                statusText = "Running Llama.cpp..."
                scope.launch {
                    val llmDir = File(context.filesDir, "models/llm")
                    val modelFiles = llmDir.listFiles { f -> f.extension == "gguf" }
                    if (modelFiles.isNullOrEmpty()) {
                        statusText = "No GGUF models in files/models/llm/"
                        llamaOutput = "Missing GGUF model"
                        return@launch
                    }
                    val model = modelFiles.first()
                    val result = LlamaNative.testGenerate(model)
                    result.onSuccess { output ->
                        llamaOutput = output
                        statusText = "Inference completed!"
                    }.onFailure { err ->
                        llamaOutput = "Error: ${err.message}"
                        statusText = "Inference failed"
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Run Llama.cpp Test")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Spike D
        Text("Spike D — Overlay Bubble Mic FGS", style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(6.dp))
        Button(
            onClick = {
                val intent = Intent(context, SpikeOverlayService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                statusText = "Overlay Bubble active. Tap red circle!"
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Launch Spike Overlay Bubble")
        }
        Spacer(modifier = Modifier.height(6.dp))
        Button(
            onClick = {
                val intent = Intent(context, SpikeOverlayService::class.java).apply {
                    action = SpikeOverlayService.ACTION_STOP_SERVICE
                }
                context.startService(intent)
                statusText = "Overlay Bubble stopped"
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Stop Spike Overlay Bubble")
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

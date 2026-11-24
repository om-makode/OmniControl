package com.omnicontrol.app.ui.screens

import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import com.omnicontrol.feature.overlay.AssistantService
import com.omnicontrol.feature.overlay.state.AssistantEvent
import com.omnicontrol.core.model.ChatItem
import com.omnicontrol.core.model.ChatRole
import com.omnicontrol.core.model.RectBounds
import com.omnicontrol.core.model.ScreenElement
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

        Spacer(modifier = Modifier.height(20.dp))

        // Phase 2 Overlay Simulator
        Text("Phase 2 — Assistant Overlay Simulator", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    AssistantService.start(context)
                    statusText = "AssistantService launched"
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Launch Overlay")
            }
            Button(
                onClick = {
                    AssistantService.quit(context)
                    statusText = "AssistantService quit"
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Quit Overlay")
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    AssistantService.toggle(context)
                    statusText = "Toggled overlay panel"
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Toggle Panel")
            }
            Button(
                onClick = {
                    AssistantService.stop(context)
                    statusText = "Stopped assistant session"
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Stop Session")
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Button(
            onClick = {
                scope.launch {
                    val store = AssistantService.stateStore
                    statusText = "Cycle: Listening"
                    store.dispatch(AssistantEvent.StartSession)
                    store.dispatch(AssistantEvent.UpdateTranscript("search for cat videos"))
                    delay(1500)

                    statusText = "Cycle: Processing"
                    store.dispatch(
                        AssistantEvent.AppendMessage(
                            ChatItem(id = "sim-1", role = ChatRole.USER, content = "search for cat videos")
                        )
                    )
                    store.dispatch(AssistantEvent.SetProcessing)
                    delay(1500)

                    statusText = "Cycle: Observing"
                    store.dispatch(AssistantEvent.SetObserving)
                    delay(1500)

                    statusText = "Cycle: Executing"
                    store.dispatch(AssistantEvent.SetExecuting("Tapping search bar", 1, 3))
                    delay(2000)

                    statusText = "Cycle: Confirming"
                    store.dispatch(AssistantEvent.SetConfirming("Play first video with sound?"))
                    delay(2500)

                    statusText = "Cycle: Done / Waiting"
                    store.dispatch(
                        AssistantEvent.AppendMessage(
                            ChatItem(id = "sim-2", role = ChatRole.ASSISTANT, content = "Playing cat video.")
                        )
                    )
                    store.dispatch(AssistantEvent.SetWaiting)
                    statusText = "Simulation cycle finished"
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Simulate Complete State Cycle")
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    val store = AssistantService.stateStore
                    store.dispatch(AssistantEvent.SetTargetHighlight(RectBounds(200, 400, 600, 520)))
                    store.dispatch(
                        AssistantEvent.SetDebugElements(
                            listOf(
                                ScreenElement(id = 1, bounds = RectBounds(50, 100, 300, 180), text = "Search", isClickable = true),
                                ScreenElement(id = 2, bounds = RectBounds(200, 400, 600, 520), text = "Result 1", isClickable = true),
                                ScreenElement(id = 3, bounds = RectBounds(50, 600, 400, 700), text = "Channel", isClickable = true)
                            )
                        )
                    )
                    statusText = "Highlighted target and 3 debug boxes"
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Show Highlights")
            }
            Button(
                onClick = {
                    val store = AssistantService.stateStore
                    store.dispatch(AssistantEvent.SetTargetHighlight(null))
                    store.dispatch(AssistantEvent.SetDebugElements(emptyList()))
                    statusText = "Cleared highlights"
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Clear Highlights")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

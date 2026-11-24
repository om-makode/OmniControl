package com.omnicontrol.app.ui.screens

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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.omnicontrol.app.ui.theme.DarkSurfaceVariant
import com.omnicontrol.app.ui.theme.TextMuted
import com.omnicontrol.app.ui.theme.TextSecondary
import com.omnicontrol.core.common.settings.OmniSettings
import com.omnicontrol.core.common.settings.SettingsState
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    omniSettings: OmniSettings
) {
    val scope = rememberCoroutineScope()
    val state by omniSettings.settingsFlow.collectAsState(initial = SettingsState())

    var homeBrainInput by remember(state.homeBrainUrl) { mutableStateOf(state.homeBrainUrl) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Configure behavior, timeouts, privacy, and optional network connections.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Privacy & Offline
        SettingsCategory("Privacy & Offline") {
            SettingToggle(
                title = "Offline Mode",
                description = "Strictly disables external network calls. Models and inference remain 100% on-device.",
                checked = state.offlineMode,
                onCheckedChange = { scope.launch { omniSettings.setOfflineMode(it) } }
            )
            SettingToggle(
                title = "Record Debug Traces",
                description = "Saves screen dumps and LLM prompts locally for debugging.",
                checked = state.recordTraces,
                onCheckedChange = { scope.launch { omniSettings.setRecordTraces(it) } }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Speech & Voice
        SettingsCategory("Speech & Voice Engine") {
            SettingToggle(
                title = "Speak Assistant Replies",
                description = "Read out status and summaries via Text-to-Speech.",
                checked = state.speakReplies,
                onCheckedChange = { scope.launch { omniSettings.setSpeakReplies(it) } }
            )
            SettingToggle(
                title = "Always-on Wake Word",
                description = "Listens for 'Hey Omni' to activate without tapping.",
                checked = state.wakeWordEnabled,
                onCheckedChange = { scope.launch { omniSettings.setWakeWordEnabled(it) } }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Safety & Guardrails
        SettingsCategory("Safety & Guardrails") {
            SettingToggle(
                title = "Confirm Sensitive Actions",
                description = "Requires explicit user confirmation before pressing Subscribe, Like, Buy, or Delete.",
                checked = state.confirmSubscribeAndLike,
                onCheckedChange = { scope.launch { omniSettings.setConfirmSubscribeAndLike(it) } }
            )
            SettingToggle(
                title = "Physical Panic Key Stop",
                description = "Pressing Volume Up/Down immediately halts agent execution.",
                checked = state.panicKeysEnabled,
                onCheckedChange = { scope.launch { omniSettings.setPanicKeysEnabled(it) } }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Optional Home Brain (LAN PC)
        SettingsCategory("Optional Home Brain (Local LAN)") {
            SettingToggle(
                title = "Enable Home Brain",
                description = "Use a local server (Ollama / llama.cpp) on your home Wi-Fi for complex tasks.",
                checked = state.homeBrainEnabled,
                onCheckedChange = { scope.launch { omniSettings.setHomeBrainEnabled(it) } }
            )
            if (state.homeBrainEnabled) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = homeBrainInput,
                    onValueChange = {
                        homeBrainInput = it
                        scope.launch { omniSettings.setHomeBrainUrl(it) }
                    },
                    label = { Text("Server URL (e.g. http://192.168.1.50:11434)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun SettingsCategory(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
fun SettingToggle(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

package com.omnicontrol.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnicontrol.app.ui.theme.Palette
import com.omnicontrol.core.common.settings.OmniSettings
import com.omnicontrol.core.common.settings.SettingsState
import com.omnicontrol.feature.permissions.PermissionsState
import kotlinx.coroutines.launch

private val LANGUAGES = listOf("" to "Phone default", "en-US" to "English (US)", "en-IN" to "English (India)", "hi-IN" to "Hindi", "te-IN" to "Telugu", "en-GB" to "English (UK)")

@Composable
fun SettingsScreen(settings: SettingsState, perms: PermissionsState, store: OmniSettings, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
        TopBar("Settings", onBack)

        SectionTitle("VOICE")
        Card {
            ToggleRow("Speak replies", "Say results out loud. The mic pauses while it talks.", settings.speakReplies) {
                scope.launch { store.setSpeakReplies(it) }
            }
            ToggleRow(
                "Prefer offline speech",
                if (perms.offlineSpeech) "On-device recognition is available: faster, private, no beeps."
                else "Not available yet: Settings > Google > Speech > download your language.",
                settings.preferOfflineSpeech
            ) { scope.launch { store.setPreferOfflineSpeech(it) } }
            Spacer(Modifier.height(6.dp))
            Text("Speech language", color = Palette.Text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            Row {
                Column {
                    LANGUAGES.chunked(3).forEach { row ->
                        Row(Modifier.padding(bottom = 8.dp)) {
                            row.forEach { (tag, name) ->
                                GhostButton(
                                    (if (settings.speechLanguage == tag) "● " else "") + name,
                                    if (settings.speechLanguage == tag) Palette.Cyan else Palette.TextDim
                                ) { scope.launch { store.setSpeechLanguage(tag) } }
                                Spacer(Modifier.padding(start = 6.dp))
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        SectionTitle("ACTIONS")
        Card {
            ToggleRow("Show touching ball", "A glowing ball flies to everything the assistant taps.", settings.showTouchBall) {
                scope.launch { store.setShowTouchBall(it) }
            }
            ToggleRow("Ask before risky taps", "Send, buy, delete, post, subscribe, call…", settings.confirmRiskyActions) {
                scope.launch { store.setConfirmRiskyActions(it) }
            }
            Spacer(Modifier.height(6.dp))
            Text("Max AI steps per command: ${settings.maxSteps}", color = Palette.Text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Slider(
                value = settings.maxSteps.toFloat(), onValueChange = { v -> scope.launch { store.setMaxSteps(v.toInt()) } },
                valueRange = 5f..60f, steps = 10,
                colors = SliderDefaults.colors(thumbColor = Palette.Violet, activeTrackColor = Palette.Violet)
            )
        }

        Spacer(Modifier.height(12.dp))
        SectionTitle("ABOUT")
        Card {
            Bullet("Passwords, PINs and payment screens are never touched.")
            Bullet("Protected screens (banking apps) can't be seen by any assistant app.")
            Bullet("Quick commands and offline speech work without internet.")
        }
        Spacer(Modifier.height(32.dp))
    }
}

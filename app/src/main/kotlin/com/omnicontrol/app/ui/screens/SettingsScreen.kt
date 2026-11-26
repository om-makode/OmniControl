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
            Spacer(Modifier.height(6.dp))
            Text("Max AI steps per command: ${settings.maxSteps}", color = Palette.Text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Slider(
                value = settings.maxSteps.toFloat(), onValueChange = { v -> scope.launch { store.setMaxSteps(v.toInt()) } },
                valueRange = 5f..60f, steps = 10,
                colors = SliderDefaults.colors(thumbColor = Palette.Violet, activeTrackColor = Palette.Violet)
            )
        }

        Spacer(Modifier.height(12.dp))
        SectionTitle("PRIVACY")
        Card {
            ToggleRow(
                "Let cloud AI read personal apps",
                "Off (recommended): messages, email, contacts, photos, files and notifications are never sent to Gemini, Groq or other online AI.",
                settings.allowPersonalCloud
            ) { scope.launch { store.setAllowPersonalCloud(it) } }
            Bullet("Before anything goes to an online AI, emails, phone numbers, card/ID numbers and codes are hidden.")
            Bullet("Banking, payment and password apps are never read or sent anywhere.")
            Bullet("Logs never contain screen text or what you said.")
        }

        Spacer(Modifier.height(12.dp))
        SectionTitle("SAFETY RULES (ALWAYS ON)")
        Card {
            Text("Never done, even if you ask", color = Palette.Red, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Bullet("Payments, UPI, bank transfers, approving payment requests")
            Bullet("Typing, reading or sharing passwords, PINs, OTPs, CVVs")
            Bullet("CAPTCHA, fingerprint or other identity checks")
            Bullet("Turning off screen lock, Play Protect or other security")
            Bullet("Factory reset or erasing the phone")
            Spacer(Modifier.height(10.dp))
            Text("Asks you first, every single time", color = Palette.Amber, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Bullet("Calls, SMS, WhatsApp and email sends, forwards and shares")
            Bullet("Posts, comments, likes, subscribes, follows")
            Bullet("Orders and bookings (it stops before payment)")
            Bullet("Deleting, moving or sharing files, contacts or data")
            Bullet("Setting changes, permissions, installing or removing apps")
            Bullet("Sharing location, using the camera, opening unfamiliar links")
            Spacer(Modifier.height(10.dp))
            Text("Voice", color = Palette.Cyan, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Bullet("Unclear or misheard speech (including adult words) is never run. It asks you to repeat.")
            Bullet("Only listens after you tap the orb, and stops after a few quiet minutes.")
            Bullet("\"Stop\" or \"cancel\" cancels at once; nothing runs after that.")
        }
        Spacer(Modifier.height(32.dp))
    }
}

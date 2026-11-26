package com.omnicontrol.app.ui.screens

import android.content.Intent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnicontrol.app.ui.theme.Palette
import com.omnicontrol.core.common.settings.SettingsState
import com.omnicontrol.core.model.UiState
import com.omnicontrol.feature.overlay.AssistantHub
import com.omnicontrol.feature.overlay.AssistantService
import com.omnicontrol.feature.permissions.PermissionChecker
import com.omnicontrol.feature.permissions.PermissionsState

private val EXAMPLES = listOf(
    "Open YouTube and play the first video",
    "Search Swift tutorials on YouTube",
    "Play the second video",
    "Open WhatsApp",
    "Scroll down",
    "Tap Subscribe",
    "Open ChatGPT, ask how to turn on dark mode in Android, then do it",
    "Open the video with a car in the thumbnail"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    perms: PermissionsState,
    settings: SettingsState,
    onRequestMic: () -> Unit,
    onRequestNotifications: () -> Unit,
    onOpenBrain: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val ui by AssistantHub.state.collectAsState()
    val running = ui.serviceRunning
    var command by remember { mutableStateOf("") }

    fun open(intent: Intent) = runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    fun run(text: String) {
        if (text.isBlank()) return
        val cmd = text
        command = ""
        // Get out of the way so the command acts on the app behind OmniControl.
        (context as? android.app.Activity)?.moveTaskToBack(true)
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            AssistantService.start(context, AssistantService.ACTION_COMMAND, cmd)
        }, 650)
    }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("OmniControl", color = Palette.Text, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text("Your phone, on voice command", color = Palette.TextDim, fontSize = 14.sp)
            }
            GhostButton("Settings", Palette.TextDim, onOpenSettings)
        }
        Spacer(Modifier.height(20.dp))

        HeroCard(ui, running, perms.ready,
            onStart = { AssistantService.start(context) },
            onListen = { AssistantService.start(context, AssistantService.ACTION_START_LISTENING) },
            onQuit = { AssistantService.send(context, AssistantService.ACTION_QUIT) }
        )

        if (!perms.ready || !perms.accessibilityConnected || !perms.notifications) {
            Spacer(Modifier.height(16.dp))
            SectionTitle("SETUP")
            Card(accent = Palette.Amber) {
                CheckRow("Display over other apps", "Shows the floating orb", perms.overlay) { open(PermissionChecker.overlaySettings(context)) }
                CheckRow(
                    "Screen control (Accessibility)",
                    if (perms.accessibilityEnabled && !perms.accessibilityConnected) "Enabled but not running: turn it off and on again"
                    else "Lets the assistant see and touch the screen",
                    perms.accessibilityEnabled && perms.accessibilityConnected, action = "Turn on"
                ) { open(PermissionChecker.accessibilitySettings()) }
                if (!perms.accessibilityEnabled) {
                    Text(
                        "Greyed out? Open App info → ⋮ (top right) → \"Allow restricted settings\", then try again.",
                        color = Palette.TextDim, fontSize = 12.sp, modifier = Modifier.padding(start = 40.dp, bottom = 4.dp)
                    )
                    Row(Modifier.padding(start = 40.dp, bottom = 6.dp)) { GhostButton("Open App info", Palette.TextDim) { open(PermissionChecker.appDetails(context)) } }
                }
                CheckRow("Microphone", "To hear your commands", perms.microphone, onClick = onRequestMic)
                CheckRow("Notifications", "Shows when the mic is on", perms.notifications, onClick = onRequestNotifications)
                CheckRow("Run without battery limits", "Stops Android from killing the assistant", perms.batteryUnrestricted, optional = true) {
                    open(PermissionChecker.batterySettings(context))
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        SectionTitle("AI BRAIN")
        Card(accent = if (settings.brainProvider.name == "NONE") null else Palette.Violet) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(settings.brainProvider.title, color = Palette.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (settings.brainProvider.name == "NONE")
                            "Handles open / search / play / tap / scroll. Add a brain for multi-step tasks and \"the video with a car\"."
                        else "${settings.effectiveModel.ifBlank { "on-device model" }}${if (settings.brainVision && settings.brainProvider.name != "LOCAL") " · sees the screen" else ""}",
                        color = Palette.TextDim, fontSize = 12.sp
                    )
                }
                Spacer(Modifier.width(10.dp))
                GhostButton(if (settings.brainProvider.name == "NONE") "Set up" else "Change", Palette.Violet, onOpenBrain)
            }
        }

        Spacer(Modifier.height(16.dp))
        SectionTitle("TRY A COMMAND")
        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.weight(1f).background(Palette.CardHigh, RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 13.dp)
                ) {
                    if (command.isEmpty()) Text("e.g. open YouTube and play the first video", color = Palette.TextDim, fontSize = 14.sp)
                    BasicTextField(
                        value = command, onValueChange = { command = it }, singleLine = true,
                        textStyle = TextStyle(color = Palette.Text, fontSize = 14.sp), cursorBrush = SolidColor(Palette.Cyan),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { run(command) }),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.width(8.dp))
                PrimaryButton("Run", enabled = perms.accessibilityConnected) { run(command) }
            }
            Spacer(Modifier.height(10.dp))
            Text("OmniControl hides itself and runs the command on the app behind it.", color = Palette.TextDim, fontSize = 12.sp)
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                EXAMPLES.forEach { ex ->
                    Text(
                        ex, color = Palette.Text, fontSize = 12.sp,
                        modifier = Modifier.background(Palette.CardHigh, RoundedCornerShape(12.dp))
                            .clickable { command = ex }.padding(horizontal = 10.dp, vertical = 7.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        SectionTitle("HOW TO USE")
        Card {
            Bullet("Tap the orb to start listening. Speak commands one after another; no need to tap again.")
            Bullet("Tap the orb while it works to stop it. Long-press the orb to open the chat and type.")
            Bullet("Say \"stop\" to cancel a task, \"done\" to stop listening.")
            Bullet("Press Volume Up + Volume Down together to stop instantly.")
            Bullet("It shows a \"Proposed action\" card and waits for you before sending, calling, buying, deleting or posting.")
            Bullet("It never does payments, passwords/OTPs or security settings. It hands those back to you.")
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun HeroCard(ui: UiState, running: Boolean, ready: Boolean, onStart: () -> Unit, onListen: () -> Unit, onQuit: () -> Unit) {
    val t = rememberInfiniteTransition(label = "hero")
    val spin by t.animateFloat(0f, 360f, infiniteRepeatable(tween(6000, easing = LinearEasing)), label = "spin")
    Card(accent = if (running) Palette.Cyan else Palette.Violet) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(84.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(84.dp)) {
                    val r = size.minDimension / 2
                    drawCircle(Brush.sweepGradient(listOf(Palette.Violet, Palette.Cyan, Palette.Violet), center), radius = r - 3f, style = Stroke(5f))
                    rotateDot(spin, r)
                    drawCircle(Brush.radialGradient(listOf(Color(0xFF2A2142), Color(0xFF0E0C16)), center, r), radius = r * 0.78f)
                    drawCircle(if (running) Palette.Cyan else Palette.Violet, radius = r * 0.2f)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    when { !ready -> "Finish setup below"; running && ui.sessionActive -> "Listening…"; running -> "Assistant is on"; else -> "Assistant is off" },
                    color = Palette.Text, fontSize = 19.sp, fontWeight = FontWeight.Bold
                )
                Text(
                    when { running && ui.status.isNotBlank() -> ui.status; running -> "The orb is floating over your apps"; else -> "Start it to get the floating orb" },
                    color = Palette.TextDim, fontSize = 13.sp, maxLines = 2
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!running) {
                PrimaryButton("Start assistant", Palette.Violet, Modifier.weight(1f), enabled = ready, onClick = onStart)
            } else {
                PrimaryButton(if (ui.sessionActive) "Listening" else "Listen now", Palette.Cyan, Modifier.weight(1f), enabled = !ui.sessionActive, onClick = onListen)
                PrimaryButton("Turn off", Palette.CardHigh, Modifier.weight(0.7f), onClick = onQuit)
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.rotateDot(angle: Float, r: Float) {
    val rad = Math.toRadians(angle.toDouble())
    val p = Offset(center.x + (r - 3f) * kotlin.math.cos(rad).toFloat(), center.y + (r - 3f) * kotlin.math.sin(rad).toFloat())
    drawCircle(Color.White, radius = 5f, center = p)
}

@Suppress("unused")
private fun Modifier.dot(color: Color) = this.background(color, CircleShape)

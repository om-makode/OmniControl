package com.omnicontrol.app.ui.screens

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnicontrol.app.ui.theme.AccentError
import com.omnicontrol.app.ui.theme.AccentPrimary
import com.omnicontrol.app.ui.theme.AccentSecondary
import com.omnicontrol.app.ui.theme.AccentSuccess
import com.omnicontrol.app.ui.theme.AccentWarning
import com.omnicontrol.app.ui.theme.DarkSurface
import com.omnicontrol.app.ui.theme.DarkSurfaceVariant
import com.omnicontrol.app.ui.theme.TextMuted
import com.omnicontrol.app.ui.theme.TextSecondary
import com.omnicontrol.core.model.ActionResult
import com.omnicontrol.core.model.AgentAction
import com.omnicontrol.core.model.Observation
import com.omnicontrol.core.model.UiElement
import com.omnicontrol.feature.accessibility.CommandParser
import com.omnicontrol.feature.accessibility.OmniAccessibilityService
import com.omnicontrol.feature.overlay.AssistantService
import com.omnicontrol.feature.overlay.state.AssistantEvent
import kotlinx.coroutines.launch

@Composable
fun ManualConsoleScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var observation by remember { mutableStateOf(OmniAccessibilityService.currentObservation()) }
    var commandText by remember { mutableStateOf("") }
    var lastResult by remember { mutableStateOf<ActionResult?>(null) }
    var isExecuting by remember { mutableStateOf(false) }
    var showDebugOverlay by remember { mutableStateOf(false) }

    val a11yActive = OmniAccessibilityService.instance != null

    fun refreshObservation() {
        val obs = OmniAccessibilityService.currentObservation()
        observation = obs
        if (showDebugOverlay && obs != null) {
            AssistantService.stateStore.dispatch(
                AssistantEvent.SetDebugElements(obs.elements.map { it.toScreenElement() })
            )
        }
    }

    fun executeAction(action: AgentAction) {
        if (!a11yActive) {
            Toast.makeText(context, "Accessibility Service is not active", Toast.LENGTH_SHORT).show()
            return
        }
        isExecuting = true
        scope.launch {
            val result = OmniAccessibilityService.executeAction(action)
            lastResult = result
            isExecuting = false
            refreshObservation()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Top Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(onClick = onNavigateBack) {
                Text("← Back")
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "Manual Action Console",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Inspect UI hierarchy and test action execution",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Service Warning if inactive
        if (!a11yActive) {
            Card(
                colors = CardDefaults.cardColors(containerColor = AccentWarning.copy(alpha = 0.2f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    }
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("⚠️", fontSize = 20.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "OmniControl Accessibility Service is disabled. Tap to enable in Settings.",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = AccentWarning
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Screen Info Bar
        val obs = observation
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (obs != null) "${obs.appLabel} (${obs.packageName})" else "No screen captured",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = AccentSecondary
                    )
                    Button(
                        onClick = { refreshObservation() },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentPrimary),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text("Capture", fontSize = 12.sp)
                    }
                }

                if (obs != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Elements: ${obs.elements.size}", fontSize = 12.sp, color = TextMuted)
                        Text("Keyboard: ${if (obs.keyboardVisible) "Visible" else "Hidden"}", fontSize = 12.sp, color = TextMuted)
                        Text("FP: ${obs.screenFingerprint.take(8)}", fontSize = 12.sp, color = TextMuted)
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Show bounding boxes on overlay", fontSize = 12.sp, color = Color.White)
                    Switch(
                        checked = showDebugOverlay,
                        onCheckedChange = { checked ->
                            showDebugOverlay = checked
                            val elements = if (checked && obs != null) {
                                obs.elements.map { it.toScreenElement() }
                            } else {
                                emptyList()
                            }
                            AssistantService.stateStore.dispatch(AssistantEvent.SetDebugElements(elements))
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = AccentPrimary)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Quick Global Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ActionButton(label = "Back", modifier = Modifier.weight(1f)) {
                executeAction(AgentAction(type = "back"))
            }
            ActionButton(label = "Home", modifier = Modifier.weight(1f)) {
                executeAction(AgentAction(type = "home"))
            }
            ActionButton(label = "Recents", modifier = Modifier.weight(1f)) {
                executeAction(AgentAction(type = "recents"))
            }
            ActionButton(label = "Scroll ↓", modifier = Modifier.weight(1f)) {
                executeAction(AgentAction(type = "scroll", direction = "down"))
            }
            ActionButton(label = "Scroll ↑", modifier = Modifier.weight(1f)) {
                executeAction(AgentAction(type = "scroll", direction = "up"))
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Command Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = commandText,
                onValueChange = { commandText = it },
                placeholder = { Text("e.g. click 2, type 2 query, scroll down", fontSize = 13.sp) },
                singleLine = true,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    val action = CommandParser.parse(commandText)
                    if (action != null) {
                        executeAction(action)
                        commandText = ""
                    } else {
                        Toast.makeText(context, "Unrecognized command", Toast.LENGTH_SHORT).show()
                    }
                },
                enabled = !isExecuting && commandText.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = AccentSuccess),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(if (isExecuting) "..." else "Run", fontWeight = FontWeight.Bold)
            }
        }

        // Action Result feedback
        val res = lastResult
        if (res != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (res.success) AccentSuccess.copy(alpha = 0.15f) else AccentError.copy(alpha = 0.15f)
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (res.success) "✓" else "✗",
                        color = if (res.success) AccentSuccess else AccentError,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${res.reason} (${res.durationMs}ms)",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Elements List
        if (obs != null && obs.elements.isNotEmpty()) {
            Text(
                text = "Detected Interactive Elements (${obs.elements.size})",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(6.dp))
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(obs.elements, key = { it.id }) { element ->
                    ElementRow(
                        element = element,
                        onTap = {
                            executeAction(AgentAction(type = "click", target = element.id))
                        }
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Tap 'Capture' to read current screen hierarchy",
                    color = TextMuted,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
fun ActionButton(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier.height(36.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp)
    ) {
        Text(text = label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
    }
}

@Composable
fun ElementRow(
    element: UiElement,
    onTap: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant.copy(alpha = 0.7f)),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // ID Badge
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(AccentPrimary.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "#${element.id}",
                    color = AccentPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = element.role.name.lowercase(),
                        color = AccentSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (element.clickable) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("clickable", color = AccentSuccess, fontSize = 10.sp)
                    }
                    if (element.editable) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("editable", color = AccentWarning, fontSize = 10.sp)
                    }
                    if (element.scrollable) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("scrollable", color = Color(0xFF38BDF8), fontSize = 10.sp)
                    }
                }
                Text(
                    text = element.primaryLabel,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = Color.White,
                    maxLines = 2
                )
                Text(
                    text = "[${element.bounds.left}, ${element.bounds.top}, ${element.bounds.right}, ${element.bounds.bottom}]",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    color = TextMuted
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            OutlinedButton(
                onClick = onTap,
                modifier = Modifier.height(30.dp),
                shape = RoundedCornerShape(6.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 2.dp)
            ) {
                Text("Tap", fontSize = 11.sp)
            }
        }
    }
}

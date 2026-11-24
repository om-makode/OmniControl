package com.omnicontrol.feature.overlay.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnicontrol.core.model.AssistantState
import com.omnicontrol.core.model.ChatItem
import com.omnicontrol.core.model.ChatRole
import com.omnicontrol.core.model.UiState

@Composable
fun PanelContent(
    uiState: UiState,
    onMinimize: () -> Unit,
    onStopSession: () -> Unit,
    onConfirm: (Boolean) -> Unit,
    onSendText: (String) -> Unit,
    onFocusChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    var inputText by remember { mutableStateOf("") }

    LaunchedEffect(uiState.messages.size, uiState.partialTranscript) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .border(1.dp, Color(0xFF334155), RoundedCornerShape(24.dp)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A).copy(alpha = 0.95f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "OmniControl",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                    if (uiState.currentApp != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "• ${uiState.currentApp}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF94A3B8)
                        )
                    }
                    if (uiState.homeBrainInUse) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Home Brain",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF22D3EE),
                            modifier = Modifier
                                .background(Color(0xFF22D3EE).copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                IconButton(onClick = onMinimize, modifier = Modifier.size(32.dp)) {
                    Text("▼", color = Color(0xFF94A3B8), fontSize = 16.sp)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Conversation history list
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(Color(0xFF020617).copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                    .padding(8.dp)
            ) {
                if (uiState.messages.isEmpty() && uiState.partialTranscript.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Listening... Tap mic or speak your command",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF64748B)
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(uiState.messages) { message ->
                            ChatBubble(message)
                        }
                        if (uiState.partialTranscript.isNotEmpty()) {
                            item {
                                Text(
                                    text = "“${uiState.partialTranscript}…”",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFF22D3EE),
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Waveform & State line
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when (val s = uiState.assistant) {
                        is AssistantState.Listening -> "Listening..."
                        is AssistantState.Processing -> "Thinking..."
                        is AssistantState.Observing -> "Reading screen..."
                        is AssistantState.Executing -> "Step ${s.index}${s.total?.let { "/$it" } ?: ""}: ${s.step}"
                        is AssistantState.Waiting -> "Waiting for command"
                        is AssistantState.Confirming -> "Confirmation required"
                        is AssistantState.Paused -> "Paused: ${s.reason}"
                        is AssistantState.Error -> "Error: ${s.message}"
                        else -> "Idle"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF94A3B8)
                )

                // Mini audio waveform
                Canvas(modifier = Modifier.size(width = 60.dp, height = 16.dp)) {
                    val count = 5
                    val step = size.width / count
                    val maxH = size.height
                    for (i in 0 until count) {
                        val barH = (maxH * (0.2f + uiState.audioLevel * 0.8f * ((i % 3 + 1) / 3f))).coerceAtLeast(3f)
                        val x = i * step + step / 2
                        drawLine(
                            color = Color(0xFF22D3EE),
                            start = Offset(x, (maxH - barH) / 2),
                            end = Offset(x, (maxH + barH) / 2),
                            strokeWidth = 3f
                        )
                    }
                }
            }

            // Confirmation Card if needed
            val assistantState = uiState.assistant
            if (assistantState is AssistantState.Confirming) {
                Spacer(modifier = Modifier.height(10.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = assistantState.question,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFFFBBF24)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { onConfirm(true) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34D399)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Yes, Proceed", color = Color.Black)
                            }
                            OutlinedButton(
                                onClick = { onConfirm(false) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Cancel", color = Color(0xFFFF5C7A))
                            }
                        }
                    }
                }
            }

            // Bottom bar: Text input & STOP
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { Text("Type instead...", fontSize = 14.sp) },
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged { focusState ->
                            onFocusChange(focusState.isFocused)
                        },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )

                if (inputText.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onSendText(inputText)
                            inputText = ""
                            onFocusChange(false)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C5CFF))
                    ) {
                        Text("Send")
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onStopSession,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5C7A)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Stop", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun ChatBubble(item: ChatItem) {
    val isUser = item.role == ChatRole.USER
    val isAction = item.role == ChatRole.ACTION

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        val detail = item.actionDetail
        if (isAction && detail != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (detail.isSuccess) "✓" else "✗",
                        color = if (detail.isSuccess) Color(0xFF34D399) else Color(0xFFFF5C7A),
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = item.content,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFE2E8F0)
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .clip(
                        RoundedCornerShape(
                            topStart = 14.dp,
                            topEnd = 14.dp,
                            bottomStart = if (isUser) 14.dp else 4.dp,
                            bottomEnd = if (isUser) 4.dp else 14.dp
                        )
                    )
                    .background(if (isUser) Color(0xFF7C5CFF) else Color(0xFF1E293B))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = item.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White
                )
            }
        }
    }
}

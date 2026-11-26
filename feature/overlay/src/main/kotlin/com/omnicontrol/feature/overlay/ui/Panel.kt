package com.omnicontrol.feature.overlay.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnicontrol.core.model.ChatItem
import com.omnicontrol.core.model.ChatRole
import com.omnicontrol.core.model.ConfirmRequest
import com.omnicontrol.core.model.UiState

private val SUGGESTIONS = listOf(
    "Open YouTube and play the first video",
    "Search Swift tutorials on YouTube",
    "Scroll down",
    "Go back",
    "Open ChatGPT and ask how to turn on dark mode, then do it",
    "Turn on Wi-Fi"
)

/** Expanded assistant: conversation, confirm buttons, suggestions and a text box. */
@Composable
fun Panel(
    state: UiState,
    onSend: (String) -> Unit,
    onMic: () -> Unit,
    onStop: () -> Unit,
    onClose: () -> Unit,
    onConfirm: (Boolean) -> Unit,
    onInputFocus: (Boolean) -> Unit
) {
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.size - 1)
    }
    val shape = RoundedCornerShape(28.dp)
    Column(
        Modifier
            .fillMaxSize()
            .padding(10.dp)
            .background(OverlayColors.Surface, shape)
            .border(1.dp, OverlayColors.Stroke, shape)
            .padding(14.dp)
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).background(OverlayColors.phase(state.phase, state.sessionActive), CircleShape))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("OmniControl", color = OverlayColors.Text, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text(
                    "${OverlayColors.phaseLabel(state.phase)} · ${state.brainName}",
                    color = OverlayColors.TextDim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            if (state.busy) Pill("Stop", OverlayColors.Red, onStop)
            Spacer(Modifier.width(6.dp))
            Pill("✕", OverlayColors.TextDim, onClose)
        }
        Spacer(Modifier.height(10.dp))

        // Conversation
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (state.messages.isEmpty()) {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Tell me what to do", color = OverlayColors.Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text("Tap the mic or type below", color = OverlayColors.TextDim, fontSize = 13.sp)
                }
            } else {
                LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(state.messages, key = { it.id }) { MessageRow(it) }
                }
            }
        }

        state.confirm?.let { req ->
            Spacer(Modifier.height(8.dp))
            ConfirmCard(req, onConfirm)
        }

        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(vertical = 2.dp)) {
            items(SUGGESTIONS) { s -> Pill(s, OverlayColors.Violet) { onSend(s) } }
        }
        Spacer(Modifier.height(10.dp))

        // Input row
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.weight(1f).background(OverlayColors.SurfaceHigh, RoundedCornerShape(22.dp))
                    .padding(horizontal = 16.dp, vertical = 13.dp)
            ) {
                if (input.isEmpty()) Text("Type a command…", color = OverlayColors.TextDim, fontSize = 15.sp)
                BasicTextField(
                    value = input,
                    onValueChange = { input = it },
                    singleLine = true,
                    textStyle = TextStyle(color = OverlayColors.Text, fontSize = 15.sp),
                    cursorBrush = SolidColor(OverlayColors.Cyan),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (input.isNotBlank()) { onSend(input); input = "" }
                    }),
                    modifier = Modifier.fillMaxWidth().onFocusChanged { onInputFocus(it.isFocused) }
                )
            }
            Spacer(Modifier.width(8.dp))
            RoundButton(if (input.isBlank()) (if (state.sessionActive) "■" else "🎤") else "➤",
                if (input.isBlank()) (if (state.sessionActive) OverlayColors.Red else OverlayColors.Cyan) else OverlayColors.Violet
            ) {
                if (input.isNotBlank()) { onSend(input); input = "" } else onMic()
            }
        }
    }
}

/** "Proposed action" card: exactly what will happen, waiting for the user's decision. */
@Composable
private fun ConfirmCard(req: ConfirmRequest, onConfirm: (Boolean) -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier.fillMaxWidth()
            .background(OverlayColors.SurfaceSoft, shape)
            .border(1.5.dp, OverlayColors.Orange.copy(alpha = 0.7f), shape)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(OverlayColors.Orange, CircleShape))
            Spacer(Modifier.width(8.dp))
            Text("PROPOSED ACTION · WAITING FOR YOU", color = OverlayColors.Orange, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        Text(req.title, color = OverlayColors.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        if (req.detail.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                req.detail, color = OverlayColors.Text, fontSize = 14.sp,
                modifier = Modifier.fillMaxWidth().background(OverlayColors.SurfaceHigh, RoundedCornerShape(12.dp)).padding(10.dp)
            )
        }
        req.warning?.let {
            Spacer(Modifier.height(6.dp))
            Text("⚠ $it", color = OverlayColors.Amber, fontSize = 13.sp)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.weight(1f).background(OverlayColors.Green, RoundedCornerShape(14.dp)).clickable { onConfirm(true) }.padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) { Text(req.confirmLabel, color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 15.sp) }
            Box(
                Modifier.weight(1f).background(OverlayColors.SurfaceHigh, RoundedCornerShape(14.dp)).clickable { onConfirm(false) }.padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) { Text("Cancel", color = OverlayColors.Text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp) }
        }
        Spacer(Modifier.height(6.dp))
        Text("Or say \"confirm\" / \"cancel\". Cancels by itself after 30 seconds.", color = OverlayColors.TextDim, fontSize = 11.sp)
    }
}

@Composable
private fun MessageRow(item: ChatItem) {
    when (item.role) {
        ChatRole.USER -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Text(
                item.content, color = Color.White, fontSize = 15.sp,
                modifier = Modifier.widthIn(max = 280.dp)
                    .background(OverlayColors.Violet, RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp))
                    .padding(horizontal = 14.dp, vertical = 9.dp)
            )
        }
        ChatRole.ASSISTANT -> Row(Modifier.fillMaxWidth()) {
            Text(
                item.content, color = OverlayColors.Text, fontSize = 15.sp,
                modifier = Modifier.widthIn(max = 300.dp)
                    .background(OverlayColors.SurfaceHigh, RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp))
                    .padding(horizontal = 14.dp, vertical = 9.dp)
            )
        }
        ChatRole.ACTION -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp)) {
            val ok = item.success != false
            Text(if (ok) "✓" else "✗", color = if (ok) OverlayColors.Green else OverlayColors.Red, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(6.dp))
            Text(item.content, color = OverlayColors.TextDim, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        ChatRole.SYSTEM -> Text(item.content, color = OverlayColors.TextDim, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp))
    }
}

@Composable
fun Pill(text: String, color: Color, onClick: () -> Unit) {
    Text(
        text, color = color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
        modifier = Modifier
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(16.dp))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    )
}

@Composable
private fun RoundButton(label: String, color: Color, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).background(color, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(label, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
}

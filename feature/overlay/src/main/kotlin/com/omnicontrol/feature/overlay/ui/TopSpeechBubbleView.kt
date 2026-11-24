package com.omnicontrol.feature.overlay.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnicontrol.core.model.AssistantState
import com.omnicontrol.core.model.ChatRole
import com.omnicontrol.core.model.UiState

/**
 * Custom WhatsApp-style speech bubble shape with a pointy tail on the left edge.
 */
class WhatsAppLeftTailShape(
    private val cornerRadiusDp: Float = 14f,
    private val tailWidthDp: Float = 12f,
    private val tailHeightDp: Float = 14f
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val cr = cornerRadiusDp * density.density
        val tw = tailWidthDp * density.density
        val th = tailHeightDp * density.density

        val path = Path().apply {
            // Pointy tail at the top left edge
            moveTo(0f, 4f * density.density)
            lineTo(tw, 0f)
            // Top edge to top-right corner
            lineTo(size.width - cr, 0f)
            arcTo(
                Rect(size.width - 2 * cr, 0f, size.width, 2 * cr),
                -90f, 90f, false
            )
            // Right edge to bottom-right corner
            lineTo(size.width, size.height - cr)
            arcTo(
                Rect(size.width - 2 * cr, size.height - 2 * cr, size.width, size.height),
                0f, 90f, false
            )
            // Bottom edge to bottom-left corner
            lineTo(tw + cr, size.height)
            arcTo(
                Rect(tw, size.height - 2 * cr, tw + 2 * cr, size.height),
                90f, 90f, false
            )
            // Left edge up to below tail
            lineTo(tw, th)
            close()
        }
        return Outline.Generic(path)
    }
}

@Composable
fun TopSpeechBubbleView(
    state: UiState,
    onStopSession: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bubbleShape = WhatsAppLeftTailShape()

    // Determine current display text
    val currentCommand = when {
        state.partialTranscript.isNotBlank() -> state.partialTranscript
        state.assistant is AssistantState.Executing -> (state.assistant as AssistantState.Executing).step
        state.assistant is AssistantState.Observing -> "Observing screen..."
        state.assistant is AssistantState.Processing -> "Thinking..."
        state.assistant is AssistantState.Error -> (state.assistant as AssistantState.Error).message
        state.messages.isNotEmpty() -> {
            val lastMsg = state.messages.last()
            if (lastMsg.role == ChatRole.USER) lastMsg.content else lastMsg.content
        }
        state.assistant is AssistantState.Listening -> "Listening for command..."
        else -> "Ready"
    }

    val statusIcon = when (state.assistant) {
        is AssistantState.Listening -> "🎙"
        is AssistantState.Executing, is AssistantState.Observing, is AssistantState.Processing -> "⚡"
        is AssistantState.Error -> "✗"
        else -> "✓"
    }

    val statusIconColor = when (state.assistant) {
        is AssistantState.Listening -> Color(0xFF22D3EE)
        is AssistantState.Executing -> Color(0xFFFBBF24)
        is AssistantState.Error -> Color(0xFFFF5C7A)
        else -> Color(0xFF25D366)
    }

    AnimatedVisibility(
        visible = state.sessionActive,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
    ) {
        Box(
            modifier = modifier
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .shadow(elevation = 10.dp, shape = bubbleShape, spotColor = Color(0x66000000))
                .clip(bubbleShape)
                .background(Color(0xE61F2C34)) // WhatsApp dark charcoal with subtle translucency
                .border(1.dp, Color(0xFF25D366).copy(alpha = 0.55f), bubbleShape)
                .padding(start = 18.dp, end = 12.dp, top = 8.dp, bottom = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left: Status Icon + Command Info
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(statusIconColor.copy(alpha = 0.15f))
                            .border(1.dp, statusIconColor.copy(alpha = 0.6f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = statusIcon,
                            fontSize = 13.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "OmniControl",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF25D366) // WhatsApp green
                        )
                        Text(
                            text = currentCommand,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFE9EDEF),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Right: Red Stop Button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFFF3B5C))
                        .clickable { onStopSession() }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Stop",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

package com.omnicontrol.feature.overlay.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnicontrol.core.model.AssistantState

@Composable
fun BubbleContent(
    state: AssistantState,
    audioLevel: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ringColor = when (state) {
        is AssistantState.Listening -> Color(0xFF22D3EE)
        is AssistantState.Processing, is AssistantState.Observing -> Color(0xFF7C5CFF)
        is AssistantState.Executing -> Color(0xFFFBBF24)
        is AssistantState.Waiting -> Color(0xFF34D399)
        is AssistantState.Error -> Color(0xFFFF5C7A)
        is AssistantState.Confirming -> Color(0xFFFBBF24)
        is AssistantState.Paused -> Color(0xFF64748B)
        else -> Color(0xFF64748B)
    }

    val animatedColor by animateColorAsState(
        targetValue = ringColor,
        animationSpec = tween(durationMillis = 300),
        label = "RingColor"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "Pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (state is AssistantState.Listening) 1.12f else 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseScale"
    )

    val iconText = when (state) {
        is AssistantState.Listening -> "🎙"
        is AssistantState.Processing, is AssistantState.Observing -> "⚡"
        is AssistantState.Executing -> "▶"
        is AssistantState.Waiting -> "✓"
        is AssistantState.Confirming -> "?"
        is AssistantState.Error -> "!"
        else -> "O"
    }

    Box(
        modifier = modifier
            .size(56.dp)
            .scale(if (state !is AssistantState.Idle) pulseScale else 1.0f)
            .clip(CircleShape)
            .background(Color(0xFF0F172A).copy(alpha = 0.92f))
            .border(
                width = 3.dp,
                brush = Brush.sweepGradient(
                    listOf(
                        animatedColor,
                        animatedColor.copy(alpha = 0.4f),
                        animatedColor
                    )
                ),
                shape = CircleShape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = iconText,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp
        )
    }
}

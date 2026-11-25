package com.omnicontrol.feature.overlay.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class BallState(val tick: Int = 0, val pressing: Boolean = false, val longPress: Boolean = false)

/** The "finger": a glowing ball that flies to the target and ripples when it touches. */
@Composable
fun TouchBall(state: BallState, size: Dp) {
    val ripple = remember { Animatable(0f) }
    val press = remember { Animatable(0f) }
    LaunchedEffect(state.tick) {
        if (state.tick == 0) return@LaunchedEffect
        ripple.snapTo(0f)
        press.animateTo(1f, tween(90))
        ripple.animateTo(1f, tween(if (state.longPress) 750 else 420))
        press.animateTo(0f, tween(140))
    }
    Canvas(Modifier.size(size)) {
        val c = center
        val r = this.size.minDimension / 2
        val core = r * (0.36f - press.value * 0.08f)
        if (ripple.value in 0.01f..0.99f) {
            drawCircle(Color.White.copy(alpha = 0.55f * (1f - ripple.value)), radius = core + (r - core) * ripple.value, center = c, style = Stroke(width = 3.dp.toPx()))
            drawCircle(OverlayColors.Cyan.copy(alpha = 0.25f * (1f - ripple.value)), radius = core + (r - core) * ripple.value, center = c)
        }
        drawCircle(OverlayColors.Cyan.copy(alpha = 0.28f), radius = core * 1.7f, center = c)
        drawCircle(
            Brush.radialGradient(listOf(Color.White, OverlayColors.Cyan, OverlayColors.Violet), center = c, radius = core * 1.1f),
            radius = core, center = c
        )
        drawCircle(Color.White.copy(alpha = 0.9f), radius = core, center = c, style = Stroke(width = 1.5.dp.toPx()))
    }
}

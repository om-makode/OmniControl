package com.omnicontrol.feature.overlay.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.omnicontrol.core.model.Phase
import kotlin.math.PI
import kotlin.math.sin

/** The floating assistant orb. Colour, ring and icon show what the assistant is doing. */
@Composable
fun Orb(phase: Phase, sessionActive: Boolean, level: Float, busy: Boolean) {
    val color by animateColorAsState(OverlayColors.phase(phase, sessionActive), tween(300), label = "orbColor")
    val t = rememberInfiniteTransition(label = "orb")
    val pulse by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart), label = "pulse")
    val spin by t.animateFloat(0f, 360f, infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "spin")
    val wave by t.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(900, easing = LinearEasing)), label = "wave")
    val lvl by animateFloatAsState(level, tween(120), label = "lvl")

    Box(Modifier.size(68.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(68.dp)) {
            val c = center
            val body = size.minDimension * 0.36f

            // Outer glow / pulse
            if (sessionActive || busy) {
                val grow = if (phase == Phase.HEARING) lvl * 0.35f else pulse * 0.3f
                val alpha = if (phase == Phase.HEARING) 0.35f + lvl * 0.3f else 0.35f * (1f - pulse)
                drawCircle(color.copy(alpha = alpha), radius = body * (1.12f + grow), center = c)
            }
            drawCircle(Color.Black.copy(alpha = 0.35f), radius = body * 1.06f, center = c + Offset(0f, 3f))

            // Body
            drawCircle(
                Brush.radialGradient(
                    listOf(Color(0xFF2A2142), Color(0xFF0E0C16)),
                    center = c - Offset(body * 0.3f, body * 0.4f), radius = body * 1.6f
                ),
                radius = body, center = c
            )
            // Rim
            drawCircle(
                Brush.sweepGradient(listOf(color, OverlayColors.Violet, color), center = c),
                radius = body, center = c, style = Stroke(width = 3.dp.toPx())
            )

            when (phase) {
                Phase.THINKING -> rotate(spin, c) {
                    drawArc(
                        color, startAngle = 0f, sweepAngle = 110f, useCenter = false,
                        topLeft = c - Offset(body * 0.55f, body * 0.55f), size = Size(body * 1.1f, body * 1.1f),
                        style = Stroke(width = 3.5.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                    )
                }
                Phase.LISTENING, Phase.HEARING, Phase.SPEAKING -> bars(c, body, color, wave, if (phase == Phase.HEARING) lvl else 0.25f)
                Phase.ACTING -> {
                    drawCircle(color.copy(alpha = 0.35f), radius = body * (0.35f + pulse * 0.25f), center = c)
                    drawCircle(color, radius = body * 0.22f, center = c)
                }
                Phase.CONFIRM, Phase.ERROR -> {
                    drawRoundRect(color, topLeft = c - Offset(2.dp.toPx(), body * 0.45f), size = Size(4.dp.toPx(), body * 0.55f), cornerRadius = CornerRadius(4f))
                    drawCircle(color, radius = 2.6.dp.toPx(), center = c + Offset(0f, body * 0.33f))
                }
                Phase.IDLE -> mic(c, body, if (sessionActive) color else Color.White.copy(alpha = 0.9f))
            }
        }
    }
}

private fun DrawScope.bars(c: Offset, body: Float, color: Color, wave: Float, level: Float) {
    val n = 5
    val bw = body * 0.13f
    val gap = body * 0.09f
    val total = n * bw + (n - 1) * gap
    for (i in 0 until n) {
        val phase = wave + i * 0.9f
        val h = body * (0.22f + (0.25f + level * 0.55f) * (0.5f + 0.5f * sin(phase)))
        val x = c.x - total / 2 + i * (bw + gap)
        drawRoundRect(color, topLeft = Offset(x, c.y - h / 2), size = Size(bw, h), cornerRadius = CornerRadius(bw / 2))
    }
}

private fun DrawScope.mic(c: Offset, body: Float, color: Color) {
    val w = body * 0.34f
    val h = body * 0.56f
    drawRoundRect(color, topLeft = Offset(c.x - w / 2, c.y - h * 0.72f), size = Size(w, h), cornerRadius = CornerRadius(w / 2))
    drawArc(
        color, 20f, 140f, false,
        topLeft = Offset(c.x - w * 0.95f, c.y - h * 0.62f), size = Size(w * 1.9f, h * 1.05f),
        style = Stroke(width = 2.2.dp.toPx())
    )
    drawLine(color, Offset(c.x, c.y + h * 0.43f), Offset(c.x, c.y + h * 0.62f), strokeWidth = 2.2.dp.toPx())
}

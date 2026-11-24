package com.omnicontrol.feature.overlay.ui

import android.graphics.Paint
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.omnicontrol.core.model.RectBounds
import com.omnicontrol.core.model.ScreenElement

@Composable
fun HighlightOverlayContent(
    targetBounds: RectBounds?,
    debugElements: List<ScreenElement> = emptyList(),
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current

    val infiniteTransition = rememberInfiniteTransition(label = "TargetPulse")
    val pulseProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "PulseProgress"
    )

    val textPaint = remember(density) {
        Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = with(density) { 10.dp.toPx() }
            isAntiAlias = true
            isFakeBoldText = true
        }
    }

    val badgePaint = remember {
        Paint().apply {
            color = android.graphics.Color.argb(200, 15, 23, 42)
            style = Paint.Style.FILL
            isAntiAlias = true
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        // 1. Draw debug bounding boxes and IDs if any
        if (debugElements.isNotEmpty()) {
            for (element in debugElements) {
                val b = element.bounds
                if (b.width <= 0 || b.height <= 0) continue

                val left = b.left.toFloat()
                val top = b.top.toFloat()
                val width = b.width.toFloat()
                val height = b.height.toFloat()

                // Outline bounding box
                val boxColor = if (element.isClickable) Color(0xFF22D3EE) else Color(0x667C5CFF)
                drawRoundRect(
                    color = boxColor,
                    topLeft = Offset(left, top),
                    size = Size(width, height),
                    cornerRadius = CornerRadius(4.dp.toPx()),
                    style = Stroke(width = 1.5.dp.toPx())
                )

                // ID Badge in top-left corner
                val badgeText = "#${element.id}"
                val textWidth = textPaint.measureText(badgeText)
                val badgeHeight = with(density) { 14.dp.toPx() }
                val badgeWidth = textWidth + with(density) { 6.dp.toPx() }

                drawContext.canvas.nativeCanvas.drawRoundRect(
                    left,
                    top,
                    left + badgeWidth,
                    top + badgeHeight,
                    4f,
                    4f,
                    badgePaint
                )
                drawContext.canvas.nativeCanvas.drawText(
                    badgeText,
                    left + with(density) { 3.dp.toPx() },
                    top + badgeHeight - with(density) { 3.dp.toPx() },
                    textPaint
                )
            }
        }

        // 2. Draw target highlight and Touching Ball when target bounds is set
        if (targetBounds != null && targetBounds.width > 0 && targetBounds.height > 0) {
            val cx = targetBounds.centerX.toFloat()
            val cy = targetBounds.centerY.toFloat()

            // Subtle target boundary
            drawRoundRect(
                color = Color(0xFF38BDF8).copy(alpha = 0.65f),
                topLeft = Offset(targetBounds.left.toFloat(), targetBounds.top.toFloat()),
                size = Size(targetBounds.width.toFloat(), targetBounds.height.toFloat()),
                cornerRadius = CornerRadius(8.dp.toPx()),
                style = Stroke(width = 2.dp.toPx())
            )

            // --- ANIMATED TOUCHING BALL ---
            val ballRadiusPx = with(density) { 20.dp.toPx() }
            val touchScale = 1.25f - (pulseProgress * 0.40f) // Presses down from 1.25 -> 0.85

            // Outer expanding touch ripple wave
            val rippleRadius = ballRadiusPx + (pulseProgress * with(density) { 28.dp.toPx() })
            val rippleAlpha = (1f - pulseProgress).coerceIn(0f, 1f) * 0.7f
            drawCircle(
                color = Color(0xFF38BDF8).copy(alpha = rippleAlpha),
                radius = rippleRadius,
                center = Offset(cx, cy),
                style = Stroke(width = with(density) { 2.5.dp.toPx() })
            )

            // Soft glowing halo
            drawCircle(
                color = Color(0xFF0284C7).copy(alpha = 0.35f),
                radius = ballRadiusPx * 1.6f,
                center = Offset(cx, cy)
            )

            // Touching Ball (Sphere with 3D specular highlight gradient)
            val sphereBrush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFFFFFFF),       // Specular white center
                    Color(0xFF7DD3FC),       // Cyan glow
                    Color(0xFF0284C7),       // Deep electric cyan
                    Color(0xFF0369A1)        // Outer sphere edge
                ),
                center = Offset(cx - with(density) { 4.dp.toPx() }, cy - with(density) { 4.dp.toPx() }),
                radius = ballRadiusPx * touchScale
            )

            drawCircle(
                brush = sphereBrush,
                radius = ballRadiusPx * touchScale,
                center = Offset(cx, cy)
            )

            // Center focal touch ring
            drawCircle(
                color = Color.White.copy(alpha = 0.85f),
                radius = with(density) { 6.dp.toPx() } * touchScale,
                center = Offset(cx, cy),
                style = Stroke(width = with(density) { 1.5.dp.toPx() })
            )
        }
    }
}

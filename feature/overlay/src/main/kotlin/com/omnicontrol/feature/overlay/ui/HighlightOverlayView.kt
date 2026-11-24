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

        // 2. Draw target pulse highlight ring if target bounds is set
        if (targetBounds != null && targetBounds.width > 0 && targetBounds.height > 0) {
            val expand = pulseProgress * 12.dp.toPx()
            val alpha = (1f - pulseProgress).coerceIn(0f, 1f)
            val left = targetBounds.left - expand
            val top = targetBounds.top - expand
            val width = targetBounds.width + (expand * 2)
            val height = targetBounds.height + (expand * 2)

            // Outer expanding ring
            drawRoundRect(
                color = Color(0xFFFBBF24).copy(alpha = alpha * 0.8f),
                topLeft = Offset(left, top),
                size = Size(width, height),
                cornerRadius = CornerRadius(12.dp.toPx()),
                style = Stroke(width = 3.dp.toPx())
            )

            // Inner solid boundary
            drawRoundRect(
                color = Color(0xFFFBBF24),
                topLeft = Offset(targetBounds.left.toFloat(), targetBounds.top.toFloat()),
                size = Size(targetBounds.width.toFloat(), targetBounds.height.toFloat()),
                cornerRadius = CornerRadius(8.dp.toPx()),
                style = Stroke(width = 2.dp.toPx())
            )
        }
    }
}

package com.omnicontrol.feature.vision.color

import android.graphics.Bitmap
import android.graphics.Color
import com.omnicontrol.core.model.RectBounds
import com.omnicontrol.core.model.UiElement

enum class NamedColor {
    RED, ORANGE, YELLOW, GREEN, BLUE, PURPLE, PINK, BROWN, BLACK, WHITE, GRAY, UNKNOWN
}

object ColorAnalyzer {

    fun dominant(bitmap: Bitmap, bounds: RectBounds): NamedColor {
        val w = bitmap.width
        val h = bitmap.height

        val left = bounds.left.coerceIn(0, w - 1)
        val right = bounds.right.coerceIn(0, w)
        val top = bounds.top.coerceIn(0, h - 1)
        val bottom = bounds.bottom.coerceIn(0, h)

        val boxWidth = right - left
        val boxHeight = bottom - top
        if (boxWidth <= 4 || boxHeight <= 4) return NamedColor.UNKNOWN

        // Skip 15% border to avoid boundary antialiasing / strokes
        val insetX = (boxWidth * 0.15f).toInt()
        val insetY = (boxHeight * 0.15f).toInt()
        val startX = left + insetX
        val endX = right - insetX
        val startY = top + insetY
        val endY = bottom - insetY

        if (startX >= endX || startY >= endY) return NamedColor.UNKNOWN

        val colorCounts = mutableMapOf<NamedColor, Int>()
        val hsv = FloatArray(3)
        val step = 4

        for (x in startX until endX step step) {
            for (y in startY until endY step step) {
                val pixel = bitmap.getPixel(x, y)
                Color.colorToHSV(pixel, hsv)
                val named = classifyHsv(hsv[0], hsv[1], hsv[2])
                colorCounts[named] = (colorCounts[named] ?: 0) + 1
            }
        }

        // Return highest non-unknown color
        return colorCounts.maxByOrNull { it.value }?.key ?: NamedColor.UNKNOWN
    }

    fun findByColor(
        bitmap: Bitmap,
        elements: List<UiElement>,
        targetColor: NamedColor
    ): List<UiElement> {
        return elements.filter { element ->
            val color = dominant(bitmap, element.bounds)
            color == targetColor
        }
    }

    fun parseColorName(name: String): NamedColor? {
        val lower = name.trim().lowercase()
        return when {
            lower.contains("red") -> NamedColor.RED
            lower.contains("orange") -> NamedColor.ORANGE
            lower.contains("yellow") -> NamedColor.YELLOW
            lower.contains("green") -> NamedColor.GREEN
            lower.contains("blue") -> NamedColor.BLUE
            lower.contains("purple") || lower.contains("violet") -> NamedColor.PURPLE
            lower.contains("pink") -> NamedColor.PINK
            lower.contains("brown") -> NamedColor.BROWN
            lower.contains("black") -> NamedColor.BLACK
            lower.contains("white") -> NamedColor.WHITE
            lower.contains("gray") || lower.contains("grey") -> NamedColor.GRAY
            else -> null
        }
    }

    private fun classifyHsv(hue: Float, sat: Float, value: Float): NamedColor {
        // Black / White / Gray check based on saturation and value
        if (value < 0.18f) return NamedColor.BLACK
        if (sat < 0.15f) {
            return if (value > 0.82f) NamedColor.WHITE else NamedColor.GRAY
        }

        // Brown check (low saturation orange/red at medium-low value)
        if (hue in 15f..45f && sat in 0.2f..0.6f && value in 0.2f..0.6f) {
            return NamedColor.BROWN
        }

        // Pink check (light magenta/red with low-medium sat and high value)
        if ((hue in 320f..355f) && sat in 0.2f..0.6f && value > 0.7f) {
            return NamedColor.PINK
        }

        // Hue wheel (0..360)
        return when (hue) {
            in 0f..15f, in 345f..360f -> NamedColor.RED
            in 16f..42f -> NamedColor.ORANGE
            in 43f..68f -> NamedColor.YELLOW
            in 69f..165f -> NamedColor.GREEN
            in 166f..260f -> NamedColor.BLUE
            in 261f..315f -> NamedColor.PURPLE
            in 316f..344f -> NamedColor.PINK
            else -> NamedColor.UNKNOWN
        }
    }
}

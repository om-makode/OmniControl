package com.omnicontrol.feature.vision.som

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.omnicontrol.core.model.UiElement

object SetOfMarksRenderer {

    private val BADGE_COLORS = intArrayOf(
        Color.rgb(239, 68, 68),   // Red
        Color.rgb(59, 130, 246),  // Blue
        Color.rgb(16, 185, 129),  // Green
        Color.rgb(245, 158, 11),  // Amber
        Color.rgb(139, 92, 246),  // Purple
        Color.rgb(236, 72, 153)   // Pink
    )

    fun render(
        bitmap: Bitmap,
        candidates: List<UiElement>
    ): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)

        val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }

        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 28f
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }

        for ((index, element) in candidates.withIndex()) {
            val markNumber = index + 1
            val color = BADGE_COLORS[index % BADGE_COLORS.size]
            boxPaint.color = color
            badgePaint.color = color

            val rect = RectF(
                element.bounds.left.toFloat(),
                element.bounds.top.toFloat(),
                element.bounds.right.toFloat(),
                element.bounds.bottom.toFloat()
            )

            // Draw bounding border
            canvas.drawRoundRect(rect, 8f, 8f, boxPaint)

            // Draw numbered badge in top-left of element
            val badgeWidth = 44f
            val badgeHeight = 36f
            val badgeRect = RectF(
                rect.left,
                rect.top,
                rect.left + badgeWidth,
                rect.top + badgeHeight
            )
            canvas.drawRoundRect(badgeRect, 6f, 6f, badgePaint)

            val textY = badgeRect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2
            canvas.drawText(markNumber.toString(), badgeRect.centerX(), textY, textPaint)
        }

        return result
    }
}

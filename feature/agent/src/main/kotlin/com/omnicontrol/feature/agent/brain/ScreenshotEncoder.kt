package com.omnicontrol.feature.agent.brain

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.Base64
import com.omnicontrol.core.model.ScreenState
import java.io.ByteArrayOutputStream

/**
 * Shrinks the screenshot and draws every actionable element's number on it ("set of marks"),
 * so a vision model can say "the video with the car is 14" instead of guessing coordinates.
 */
object ScreenshotEncoder {

    data class Encoded(val base64: String, val scale: Float)

    fun encode(src: Bitmap, screen: ScreenState, maxWidth: Int = 768): Encoded {
        val scale = if (src.width > maxWidth) src.width.toFloat() / maxWidth else 1f
        val w = (src.width / scale).toInt()
        val h = (src.height / scale).toInt()
        val bmp = Bitmap.createScaledBitmap(src, w, h, true).copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(bmp)

        val box = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f }
        val tagBg = Paint(Paint.ANTI_ALIAS_FLAG)
        val tagText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; textSize = 15f; isFakeBoldText = true
        }
        val colors = intArrayOf(0xFFE53935.toInt(), 0xFF1E88E5.toInt(), 0xFF43A047.toInt(), 0xFFFB8C00.toInt(), 0xFF8E24AA.toInt())

        // Screenshot pixels can differ from accessibility pixels (e.g. cut-outs); map by ratio.
        val sx = src.width.toFloat() / screen.screenWidth.coerceAtLeast(1) / scale
        val sy = src.height.toFloat() / screen.screenHeight.coerceAtLeast(1) / scale

        screen.elements.filter { it.actionable && !it.scrollable }.take(90).forEachIndexed { i, el ->
            val c = colors[i % colors.size]
            box.color = c
            tagBg.color = c
            val r = RectF(el.bounds.left * sx, el.bounds.top * sy, el.bounds.right * sx, el.bounds.bottom * sy)
            canvas.drawRect(r, box)
            val label = el.id.toString()
            val tw = tagText.measureText(label) + 8f
            val tx = r.left.coerceAtLeast(0f)
            val ty = r.top.coerceAtLeast(0f)
            canvas.drawRect(tx, ty, tx + tw, ty + 18f, tagBg)
            canvas.drawText(label, tx + 4f, ty + 14f, tagText)
        }

        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 70, out)
        bmp.recycle()
        return Encoded(Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP), scale)
    }
}

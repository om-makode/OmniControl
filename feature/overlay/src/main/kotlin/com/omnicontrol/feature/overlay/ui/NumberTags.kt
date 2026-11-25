package com.omnicontrol.feature.overlay.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnicontrol.core.model.NumberTag

/**
 * Renders numbered badges ([1], [2], [3], ...) over each visible video/item card.
 * When the user scrolls, tags re-number to the newly visible items so commands like
 * "open 3rd video" or "open video 3" can tap item #3 directly.
 */
@Composable
fun NumberTagsOverlay(tags: List<NumberTag>) {
    Box(Modifier.fillMaxSize()) {
        tags.forEach { tag ->
            Box(
                modifier = Modifier
                    .offset { IntOffset(tag.x, tag.y) }
                    .shadow(elevation = 6.dp, shape = CircleShape)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFF6C4AB6), Color(0xFF1E1735))
                        ),
                        shape = CircleShape
                    )
                    .border(width = 2.dp, color = Color(0xFF00E5FF), shape = CircleShape)
                    .size(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = tag.number.toString(),
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

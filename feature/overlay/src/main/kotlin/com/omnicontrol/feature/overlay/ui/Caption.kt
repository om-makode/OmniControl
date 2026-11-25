package com.omnicontrol.feature.overlay.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omnicontrol.core.model.Phase
import com.omnicontrol.core.model.UiState

/** Slim live caption at the top: what you said + what the assistant is doing. Never blocks touches. */
@Composable
fun Caption(state: UiState) {
    val color = OverlayColors.phase(state.phase, state.sessionActive)
    val appear by animateFloatAsState(1f, tween(220), label = "cap")
    val shape = RoundedCornerShape(22.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .alpha(appear)
            .background(OverlayColors.Surface, shape)
            .border(1.dp, color.copy(alpha = 0.45f), shape)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).background(color, CircleShape))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val heard = state.transcript
                if (heard.isNotBlank()) {
                    Text(
                        "“$heard”",
                        color = OverlayColors.Text, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                        fontStyle = if (state.phase == Phase.HEARING) FontStyle.Italic else FontStyle.Normal,
                        maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                }
                val status = state.status.ifBlank { OverlayColors.phaseLabel(state.phase) }
                Text(
                    status, color = if (heard.isBlank()) OverlayColors.Text else OverlayColors.TextDim,
                    fontSize = if (heard.isBlank()) 14.sp else 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis
                )
            }
            if (state.busy && state.stepIndex > 0) {
                Spacer(Modifier.width(8.dp))
                Text(
                    "Step ${state.stepIndex}", color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.background(color.copy(alpha = 0.15f), RoundedCornerShape(10.dp)).padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}

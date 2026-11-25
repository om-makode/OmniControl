package com.omnicontrol.feature.overlay.ui

import androidx.compose.ui.graphics.Color
import com.omnicontrol.core.model.Phase

object OverlayColors {
    val Surface = Color(0xF0101017)
    val SurfaceSoft = Color(0xFF1A1A24)
    val SurfaceHigh = Color(0xFF242432)
    val Stroke = Color(0x33FFFFFF)
    val Text = Color(0xFFF4F4F8)
    val TextDim = Color(0xFFA3A3B5)
    val Violet = Color(0xFF7C5CFF)
    val Cyan = Color(0xFF22D3EE)
    val Amber = Color(0xFFFBBF24)
    val Green = Color(0xFF34D399)
    val Orange = Color(0xFFFB923C)
    val Red = Color(0xFFF43F5E)

    fun phase(p: Phase, active: Boolean = true): Color = when (p) {
        Phase.IDLE -> if (active) Cyan else Violet
        Phase.LISTENING -> Cyan
        Phase.HEARING -> Cyan
        Phase.THINKING -> Violet
        Phase.ACTING -> Amber
        Phase.SPEAKING -> Green
        Phase.CONFIRM -> Orange
        Phase.ERROR -> Red
    }

    fun phaseLabel(p: Phase): String = when (p) {
        Phase.IDLE -> "Ready"
        Phase.LISTENING -> "Listening"
        Phase.HEARING -> "Hearing you"
        Phase.THINKING -> "Thinking"
        Phase.ACTING -> "Working"
        Phase.SPEAKING -> "Replying"
        Phase.CONFIRM -> "Needs your OK"
        Phase.ERROR -> "Problem"
    }
}

package com.omnicontrol.app.ui.theme

import androidx.compose.ui.graphics.Color

val DarkBackground = Color(0xFF0B0B0F)
val DarkSurface = Color(0xFF15151C)
val DarkSurfaceVariant = Color(0xFF1E1E27)
val AccentPrimary = Color(0xFF7C5CFF)
val AccentSecondary = Color(0xFF22D3EE)
val AccentError = Color(0xFFFF5C7A)
val AccentSuccess = Color(0xFF34D399)
val AccentWarning = Color(0xFFFBBF24)

val TextPrimary = Color(0xFFF8FAFC)
val TextSecondary = Color(0xFF94A3B8)
val TextMuted = Color(0xFF64748B)

// Bubble Ring State Colors
object AssistantColors {
    val Idle = Color(0xFF64748B)
    val Listening = AccentSecondary // #22D3EE
    val Thinking = AccentPrimary   // #7C5CFF
    val Executing = AccentWarning  // #FBBF24
    val Done = AccentSuccess       // #34D399
    val Error = AccentError        // #FF5C7A
}

package com.omnicontrol.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object Palette {
    val Bg = Color(0xFF07070B)
    val Card = Color(0xFF12121A)
    val CardHigh = Color(0xFF1B1B26)
    val Stroke = Color(0x22FFFFFF)
    val Text = Color(0xFFF4F4F8)
    val TextDim = Color(0xFF9C9CB0)
    val Violet = Color(0xFF7C5CFF)
    val Cyan = Color(0xFF22D3EE)
    val Green = Color(0xFF34D399)
    val Amber = Color(0xFFFBBF24)
    val Red = Color(0xFFF43F5E)
}

private val scheme = darkColorScheme(
    primary = Palette.Violet,
    onPrimary = Color.White,
    secondary = Palette.Cyan,
    background = Palette.Bg,
    onBackground = Palette.Text,
    surface = Palette.Card,
    onSurface = Palette.Text,
    surfaceVariant = Palette.CardHigh,
    onSurfaceVariant = Palette.TextDim,
    outline = Palette.Stroke,
    error = Palette.Red
)

@Composable
fun OmniControlTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}

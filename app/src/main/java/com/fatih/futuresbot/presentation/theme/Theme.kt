package com.fatih.futuresbot.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object TradeColors {
    val Background = Color(0xFF0B0E11)
    val Surface = Color(0xFF161A1E)
    val SurfaceHigh = Color(0xFF1E2329)
    val Long = Color(0xFF0ECB81)
    val Short = Color(0xFFF6465D)
    val Accent = Color(0xFFFFB020)
    val TextPrimary = Color(0xFFEAECEF)
    val TextSecondary = Color(0xFF848E9C)
}

private val DarkScheme = darkColorScheme(
    primary = TradeColors.Accent,
    onPrimary = Color.Black,
    background = TradeColors.Background,
    onBackground = TradeColors.TextPrimary,
    surface = TradeColors.Surface,
    onSurface = TradeColors.TextPrimary,
    surfaceVariant = TradeColors.SurfaceHigh,
    onSurfaceVariant = TradeColors.TextSecondary,
    surfaceContainer = TradeColors.Surface,
    error = TradeColors.Short,
)

@Composable
fun FuturesBotTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkScheme, content = content)
}

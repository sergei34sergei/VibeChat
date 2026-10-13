package com.vibe.chat.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val VibeDarkBackground = Color(0xFF0A1110)
val VibeSurfaceColor = Color(0xFF111D1A)
val VibeSurfaceVariant = Color(0xFF182824)
val VibePrimaryGreen = Color(0xFF00E676)
val VibePrimaryGreenVariant = Color(0xFF00C853)
val VibeOnPrimary = Color(0xFF003311)
val VibeTextWhite = Color(0xFFE0E0E0)
val VibeTextGray = Color(0xFF8A9A95)
val VibeBubbleOutgoing = Color(0xFF004D30)
val VibeBubbleIncoming = Color(0xFF162420)
val VibeDivider = Color(0xFF1E332E)

private val VibeDarkColorScheme = darkColorScheme(
    primary = VibePrimaryGreen,
    onPrimary = VibeOnPrimary,
    primaryContainer = VibePrimaryGreenVariant,
    background = VibeDarkBackground,
    surface = VibeSurfaceColor,
    surfaceVariant = VibeSurfaceVariant,
    onBackground = VibeTextWhite,
    onSurface = VibeTextWhite,
    outline = VibeDivider
)

@Composable
fun VibeWhatsAppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = VibeDarkColorScheme,
        content = content
    )
}

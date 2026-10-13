package com.vibe.chat

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object VibeWhatsAppColors {
    val Background = Color(0xFF0B141A)
    val Surface = Color(0xFF111B21)
    val SurfaceElevated = Color(0xFF202C33)
    val Divider = Color(0xFF26343D)
    val TextPrimary = Color(0xFFE9edef)
    val TextSecondary = Color(0xFF8696A0)
    val Accent = Color(0xFF25D366)
    val AccentDark = Color(0xFF075E54)
    val BubbleOutgoing = Color(0xFF005C4B)
    val BubbleIncoming = Color(0xFF202C33)
    val Unread = Color(0xFF25D366)
    val Danger = Color(0xFFFF6B6B)
}

private val VibeDarkScheme = darkColorScheme(
    primary = VibeWhatsAppColors.Accent,
    onPrimary = Color(0xFF062A1B),
    secondary = Color(0xFF53BDEB),
    background = VibeWhatsAppColors.Background,
    onBackground = VibeWhatsAppColors.TextPrimary,
    surface = VibeWhatsAppColors.Surface,
    onSurface = VibeWhatsAppColors.TextPrimary,
    surfaceVariant = VibeWhatsAppColors.SurfaceElevated,
    onSurfaceVariant = VibeWhatsAppColors.TextSecondary,
    outline = VibeWhatsAppColors.Divider
)

@Composable
fun VibeWhatsAppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = VibeDarkScheme,
        content = content
    )
}

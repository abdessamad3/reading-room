package com.readingroom.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF2E6A56),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD7E8E0),
    onPrimaryContainer = Color(0xFF0E2B22),
    background = Color(0xFFF8FAF7),
    onBackground = Color(0xFF18211D),
    surface = Color(0xFFF8FAF7),
    onSurface = Color(0xFF18211D),
    surfaceVariant = Color(0xFFEEF2EE),
    onSurfaceVariant = Color(0xFF5B6862),
    outline = Color(0xFFD0D8D2),
    error = Color(0xFF9A4B1E),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF74BA9D),
    onPrimary = Color(0xFF0D1511),
    primaryContainer = Color(0xFF23372E),
    onPrimaryContainer = Color(0xFFD7E8E0),
    background = Color(0xFF101412),
    onBackground = Color(0xFFE2E8E4),
    surface = Color(0xFF101412),
    onSurface = Color(0xFFE2E8E4),
    surfaceVariant = Color(0xFF1F2823),
    onSurfaceVariant = Color(0xFF96A49C),
    outline = Color(0xFF2B3530),
    error = Color(0xFFE39A6D),
)

@Composable
fun ReadingRoomTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}

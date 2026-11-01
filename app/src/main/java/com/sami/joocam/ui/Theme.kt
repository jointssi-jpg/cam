package com.sami.joocam.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Ink = Color(0xFF0F1720)
val Panel = Color(0xFF18232F)
val Steel = Color(0xFF8FA3B5)
val Signal = Color(0xFF4FD1C5)
val LiveRed = Color(0xFFFF5A5F)
val Amber = Color(0xFFF2B84B)

private val Scheme = darkColorScheme(
    primary = Signal,
    onPrimary = Ink,
    background = Ink,
    onBackground = Color(0xFFE6EDF3),
    surface = Panel,
    onSurface = Color(0xFFE6EDF3),
    surfaceVariant = Color(0xFF223140),
    onSurfaceVariant = Steel,
    error = LiveRed,
)

@Composable
fun JooCamTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}

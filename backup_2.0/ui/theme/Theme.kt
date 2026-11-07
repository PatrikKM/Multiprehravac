package com.example.multiprehrava.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkGreenColorScheme = darkColorScheme(
    primary = GreenPrimary,
    onPrimary = GreenOnPrimary,
    primaryContainer = GreenPrimaryContainer,
    onPrimaryContainer = GreenOnContainer,
    secondary = GreenPrimary,
    onSecondary = GreenOnPrimary,
    background = DarkBackground,
    onBackground = TextWhite,
    surface = DarkSurface,
    onSurface = TextWhite,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextMuted,
    outline = GreenPrimaryContainer
)

@Composable
fun MultiprehravačTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkGreenColorScheme,
        typography = Typography,
        content = content
    )
}

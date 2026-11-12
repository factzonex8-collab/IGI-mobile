package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val TacticalColorScheme = darkColorScheme(
    primary = TacticalGreen,
    onPrimary = Color.Black,
    primaryContainer = TacticalSurfaceVariant,
    onPrimaryContainer = TacticalGreen,
    secondary = RadarGreen,
    onSecondary = Color.Black,
    background = Color.Black,
    onBackground = HudText,
    surface = DarkGunmetal,
    onSurface = HudText,
    surfaceVariant = TacticalSurface,
    onSurfaceVariant = TextMuted,
    error = AlertRed,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = TacticalColorScheme,
        typography = Typography,
        content = content
    )
}

package com.example.taskmanager.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = AccentViolet,
    onPrimary = TextPrimary,
    primaryContainer = SurfaceElevated,
    secondary = AccentCyan,
    onSecondary = TextPrimary,
    tertiary = AccentCyanLight,
    background = Background,
    onBackground = TextPrimary,
    surface = SurfaceContainer,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceElevated,
    onSurfaceVariant = TextSecondary,
    outline = SurfaceBorder,
    error = DangerRed,
    onError = TextPrimary,
)

@Composable
fun TaskManagerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = TaskManagerTypography,
        content = content
    )
}

package com.parkease.core.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val ParkEaseLightColors = lightColorScheme(
    primary = ParkEaseColor.LightPrimary,
    onPrimary = ParkEaseColor.LightBackground,
    primaryContainer = ParkEaseColor.LightPrimaryContainer,
    onPrimaryContainer = ParkEaseColor.LightBackground,
    secondary = ParkEaseColor.LightAccent,
    onSecondary = ParkEaseColor.LightBackground,
    secondaryContainer = ParkEaseColor.LightSecondaryContainer,
    onSecondaryContainer = ParkEaseColor.LightOnSecondaryContainer,
    background = ParkEaseColor.LightBackground,
    onBackground = ParkEaseColor.LightOnSurface,
    surface = ParkEaseColor.LightSurface,
    onSurface = ParkEaseColor.LightOnSurface,
    surfaceVariant = ParkEaseColor.LightSecondaryContainer,
    onSurfaceVariant = ParkEaseColor.LightMutedForeground,
    error = ParkEaseColor.LightDestructive,
    onError = ParkEaseColor.LightBackground,
    outline = ParkEaseColor.LightBorder,
    outlineVariant = ParkEaseColor.LightBorder,
)

private val ParkEaseDarkColors = darkColorScheme(
    primary = ParkEaseColor.DarkPrimary,
    onPrimary = ParkEaseColor.DarkBackground,
    primaryContainer = ParkEaseColor.DarkPrimary,
    onPrimaryContainer = ParkEaseColor.DarkBackground,
    secondary = ParkEaseColor.DarkAccent,
    onSecondary = ParkEaseColor.DarkBackground,
    secondaryContainer = ParkEaseColor.DarkSecondaryContainer,
    onSecondaryContainer = ParkEaseColor.DarkOnSecondaryContainer,
    background = ParkEaseColor.DarkBackground,
    onBackground = ParkEaseColor.DarkOnSurface,
    surface = ParkEaseColor.DarkSurface,
    onSurface = ParkEaseColor.DarkOnSurface,
    surfaceVariant = ParkEaseColor.DarkSecondaryContainer,
    onSurfaceVariant = ParkEaseColor.DarkMutedForeground,
    error = ParkEaseColor.DarkDestructive,
    onError = ParkEaseColor.DarkOnSurface,
    outline = ParkEaseColor.DarkBorder,
    outlineVariant = ParkEaseColor.DarkBorder,
)

/** 0.75rem in the reference build's design tokens — rounded cards throughout. */
private val ParkEaseShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

/**
 * Real design-system tokens — colors, type, shape — ported from the Base44
 * reference build's `src/index.css` (see ParkEaseColor.kt's doc comment).
 * Previously a placeholder passthrough to bare MaterialTheme.
 */
@Composable
fun ParkEaseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) ParkEaseDarkColors else ParkEaseLightColors,
        typography = ParkEaseTypography,
        shapes = ParkEaseShapes,
        content = content,
    )
}

package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ZenithDarkColorScheme = darkColorScheme(
    primary = VioletLight,
    onPrimary = Indigo950,
    primaryContainer = Indigo800,
    onPrimaryContainer = Color(0xFFEADBFF),

    secondary = AmberAccent,
    onSecondary = Color(0xFF3E2800),
    secondaryContainer = Color(0xFF593D00),
    onSecondaryContainer = AmberLight,

    tertiary = CyanFocus,
    onTertiary = Color(0xFF00363D),
    tertiaryContainer = Color(0xFF004E59),
    onTertiaryContainer = Color(0xFFA6EEFF),

    background = Indigo950,
    onBackground = Slate50,
    surface = Indigo900,
    onSurface = Slate50,
    surfaceVariant = Indigo800,
    onSurfaceVariant = Slate200,

    outline = Slate700,
    outlineVariant = Color(0xFF2E2954),
    error = RoseWarning,
    onError = Color.White
)

private val ZenithLightColorScheme = lightColorScheme(
    primary = VioletDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEDE7F6),
    onPrimaryContainer = Indigo900,

    secondary = AmberGlow,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFECB3),
    onSecondaryContainer = Color(0xFF4A3400),

    tertiary = CyanFocus,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE0F7FA),
    onTertiaryContainer = Color(0xFF004D40),

    background = Slate50,
    onBackground = Slate900,
    surface = Color.White,
    onSurface = Slate900,
    surfaceVariant = Slate100,
    onSurfaceVariant = Slate700,

    outline = Slate200,
    outlineVariant = Color(0xFFE0E0E0),
    error = RoseWarning,
    onError = Color.White
)

@Composable
fun ZenithTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) ZenithDarkColorScheme else ZenithLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ZenithTypography,
        content = content
    )
}

package com.despertador.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Scheme = darkColorScheme(
    background = Color(0xFF0F0E0F),
    onBackground = Color(0xFFE8E1E3),
    surface = Color(0xFF0F0E0F),
    onSurface = Color(0xFFE8E1E3),
    surfaceVariant = Color(0xFF1F1B1D),
    onSurfaceVariant = Color(0xFFB5ADB0),
    surfaceContainer = Color(0xFF1F1B1D),
    surfaceContainerLow = Color(0xFF1A1719),
    surfaceContainerHigh = Color(0xFF262224),
    primary = Color(0xFFE6B8B0),
    onPrimary = Color(0xFF2B1613),
    primaryContainer = Color(0xFF6B3A36),
    onPrimaryContainer = Color(0xFFFFDAD5),
    secondaryContainer = Color(0xFF4A3B47),
    onSecondaryContainer = Color(0xFFF2DCEC),
    outline = Color(0xFF8A8286),
    outlineVariant = Color(0xFF3A3437),
)

@Composable
fun DespertadorTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}

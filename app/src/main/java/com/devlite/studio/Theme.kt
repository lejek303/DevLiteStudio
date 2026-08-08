package com.devlite.studio.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DevLiteColorScheme = darkColorScheme(
    primary = Color(0xFF89DDFF),
    secondary = Color(0xFFC792EA),
    background = Color(0xFF0F111A),
    surface = Color(0xFF1A1C25),
    surfaceVariant = Color(0xFF14161F),
    onBackground = Color(0xFFEEFFFF),
    onSurface = Color(0xFFEEFFFF)
)

@Composable
fun DevLiteStudioTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DevLiteColorScheme,
        typography = MaterialTheme.typography,
        content = content
    )
}

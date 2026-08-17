package com.caloly.app.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CalolyColorScheme = darkColorScheme(
    primary = CalolyGreen,
    onPrimary = CalolyLavenderWhite,
    secondary = CalolyLavender,
    onSecondary = CalolyLavenderWhite,
    background = CalolyBackground,
    onBackground = CalolyText,
    surface = CalolySurface,
    onSurface = CalolyText,
    surfaceVariant = CalolyLavenderLight,
    onSurfaceVariant = CalolyText,
    outline = Color(0xFF34384A),
)

@Composable
fun CalolyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CalolyColorScheme,
        typography = CalolyTypography,
        content = content,
    )
}

package com.caloly.app.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val CalolyColorScheme = lightColorScheme(
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
)

@Composable
fun CalolyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CalolyColorScheme,
        typography = CalolyTypography,
        content = content,
    )
}

package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val SalimColorScheme = lightColorScheme(
    primary = SalimBlue,
    onPrimary = SalimWhite,
    primaryContainer = SalimBlueLight,
    onPrimaryContainer = SalimBlueDark,
    secondary = SalimSecondaryText,
    onSecondary = SalimWhite,
    secondaryContainer = SalimSurfaceCard,
    onSecondaryContainer = SalimNearBlack,
    tertiary = SalimBlueDark,
    onTertiary = SalimWhite,
    background = SalimWhite,
    onBackground = SalimNearBlack,
    surface = SalimWhite,
    onSurface = SalimNearBlack,
    surfaceVariant = SalimSurfaceCard,
    onSurfaceVariant = SalimSecondaryText,
    outline = SalimSurfaceBorder,
    outlineVariant = SalimSurfaceBorderStrong,
    error = SalimStateError,
    onError = SalimWhite,
    errorContainer = SalimStateErrorBg,
    onErrorContainer = SalimStateError
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    // Salim uses strict high-contrast white & near-black palette across all modes
    MaterialTheme(
        colorScheme = SalimColorScheme,
        typography = Typography,
        content = content
    )
}

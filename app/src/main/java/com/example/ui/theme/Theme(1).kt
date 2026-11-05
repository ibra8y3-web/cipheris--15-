package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

// ☀️ Modern Clean Light Color Scheme
private val SecureLightColorScheme = lightColorScheme(
    primary = Color(0xFF00897B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F2F1),
    onPrimaryContainer = Color(0xFF00695C),
    secondary = Color(0xFF1D4ED8),
    onSecondary = Color.White,
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFEEF2F6),
    onSurfaceVariant = Color(0xFF0F172A),
    error = Color(0xFFDC2626),
    onError = Color.White
)

// 🌙 Midnight Sapphire & Cosmic Teal Color Scheme (NO BLACK / NO DULL GRAY)
private val SecureDarkColorScheme = darkColorScheme(
    primary = Color(0xFF00E5A3),
    onPrimary = Color(0xFF0B132B),
    primaryContainer = Color(0xFF064E3B),
    onPrimaryContainer = Color(0xFF00E5A3),
    secondary = Color(0xFF38BDF8),
    onSecondary = Color(0xFF0B132B),
    background = Color(0xFF0B132B),
    onBackground = Color(0xFFF0F6FC),
    surface = Color(0xFF1C2541),
    onSurface = Color(0xFFF0F6FC),
    surfaceVariant = Color(0xFF243254),
    onSurfaceVariant = Color(0xFFF0F6FC),
    error = Color(0xFFF87171),
    onError = Color(0xFF0B132B)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = false,
    whiteToneLevel: Float = 0f,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val appColors = createAppThemeColors(isDark = darkTheme, whiteToneLevel = whiteToneLevel)
    
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = appColors.primary,
            onPrimary = appColors.background,
            primaryContainer = appColors.primaryDark,
            onPrimaryContainer = appColors.primary,
            secondary = appColors.secondary,
            onSecondary = appColors.background,
            background = appColors.background,
            onBackground = appColors.textPrimary,
            surface = appColors.surface,
            onSurface = appColors.textPrimary,
            surfaceVariant = appColors.surfaceVariant,
            onSurfaceVariant = appColors.textPrimary,
            error = appColors.dangerRed,
            onError = appColors.background
        )
    } else {
        lightColorScheme(
            primary = appColors.primary,
            onPrimary = Color.White,
            primaryContainer = appColors.primaryDark,
            onPrimaryContainer = appColors.primaryDim,
            secondary = appColors.secondary,
            onSecondary = Color.White,
            background = appColors.background,
            onBackground = appColors.textPrimary,
            surface = appColors.surface,
            onSurface = appColors.textPrimary,
            surfaceVariant = appColors.surfaceVariant,
            onSurfaceVariant = appColors.textPrimary,
            error = appColors.dangerRed,
            onError = Color.White
        )
    }

    AppThemeState.current = appColors

    CompositionLocalProvider(LocalAppColors provides appColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}



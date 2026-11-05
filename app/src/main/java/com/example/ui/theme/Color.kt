package com.example.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// =========================================================================
// 🎨 BEAUTIFUL THEME PALETTES (LIGHT & MIDNIGHT SAPPHIRE ROYAL BLUE NIGHT)
// =========================================================================

data class AppThemeColors(
    val isDark: Boolean,
    val background: Color,       // Main Canvas
    val surface: Color,          // Cards, Topbars, Bottombars, Dialogs
    val surfaceVariant: Color,   // Subtle containers, inner boxes
    val textPrimary: Color,      // Main headings & body
    val textSecondary: Color,    // Supporting text
    val mutedText: Color,        // Subtitles, metadata, hints
    val borderSubtle: Color,     // Elegant soft borders
    val primary: Color,          // Emerald / Mint accent
    val primaryDim: Color,
    val primaryDark: Color,
    val secondary: Color,        // Sapphire / Azure accent
    val dangerRed: Color
)

// ☀️ Modern Clean Light Palette
val LightThemeColors = AppThemeColors(
    isDark = false,
    background = Color(0xFFF8FAFC),      // Soft clean slate-white canvas
    surface = Color(0xFFFFFFFF),         // Crisp pure white cards & sheets
    surfaceVariant = Color(0xFFEEF2F6),  // Soft mist container
    textPrimary = Color(0xFF0F172A),     // Deep slate navy charcoal
    textSecondary = Color(0xFF475569),   // Medium slate
    mutedText = Color(0xFF64748B),       // Muted slate gray
    borderSubtle = Color(0xFFE2E8F0),    // Very soft crisp border
    primary = Color(0xFF00897B),         // Modern Emerald Teal
    primaryDim = Color(0xFF00695C),
    primaryDark = Color(0xFFE0F2F1),
    secondary = Color(0xFF1D4ED8),       // Vibrant Royal Blue
    dangerRed = Color(0xFFDC2626)
)

// 🌙 Gorgeous Midnight Sapphire & Royal Ocean Night Palette (Completely far from black & dull gray!)
val MidnightSapphireNightColors = AppThemeColors(
    isDark = true,
    background = Color(0xFF0B132B),      // Deep cosmic midnight sapphire canvas (rich, glowing blue-indigo)
    surface = Color(0xFF1C2541),         // Luxurious royal sapphire surface / cards
    surfaceVariant = Color(0xFF243254),  // Luminous indigo-sapphire container
    textPrimary = Color(0xFFF0F6FC),     // Diamond ice-white
    textSecondary = Color(0xFFCBD5E1),   // Radiant frost slate
    mutedText = Color(0xFF94A3B8),       // Clear soft azure-slate
    borderSubtle = Color(0xFF334E68),    // Translucent sapphire border
    primary = Color(0xFF00E5A3),         // Luminous vibrant Mint Emerald
    primaryDim = Color(0xFF00B37E),
    primaryDark = Color(0xFF064E3B),
    secondary = Color(0xFF38BDF8),       // Radiant Sky Azure / Electric Cyan
    dangerRed = Color(0xFFF87171)        // Coral Red
)

fun lerpColor(start: Color, stop: Color, fraction: Float): Color {
    val f = fraction.coerceIn(0f, 1f)
    return Color(
        red = start.red + (stop.red - start.red) * f,
        green = start.green + (stop.green - start.green) * f,
        blue = start.blue + (stop.blue - start.blue) * f,
        alpha = start.alpha + (stop.alpha - start.alpha) * f
    )
}

fun createAppThemeColors(isDark: Boolean, whiteToneLevel: Float = 0f): AppThemeColors {
    val level = whiteToneLevel.coerceIn(0f, 1f)
    return if (!isDark) {
        val bg = lerpColor(Color(0xFFF8FAFC), Color(0xFFE5E0D5), level)
        val surf = lerpColor(Color(0xFFFFFFFF), Color(0xFFECE7DC), level)
        val surfVar = lerpColor(Color(0xFFEEF2F6), Color(0xFFE0DDD2), level)
        val border = lerpColor(Color(0xFFE2E8F0), Color(0xFFCFC9BC), level)
        val textSec = lerpColor(Color(0xFF475569), Color(0xFF334155), level * 0.4f)
        val textMuted = lerpColor(Color(0xFF64748B), Color(0xFF475569), level * 0.4f)
        
        LightThemeColors.copy(
            background = bg,
            surface = surf,
            surfaceVariant = surfVar,
            borderSubtle = border,
            textSecondary = textSec,
            mutedText = textMuted
        )
    } else {
        val textPri = lerpColor(Color(0xFFF0F6FC), Color(0xFFC2CEDC), level)
        val textSec = lerpColor(Color(0xFFCBD5E1), Color(0xFF98A6B8), level)
        val textMuted = lerpColor(Color(0xFF94A3B8), Color(0xFF68778A), level)
        val bg = lerpColor(Color(0xFF0B132B), Color(0xFF060B18), level)
        val surf = lerpColor(Color(0xFF1C2541), Color(0xFF141B2D), level)
        val surfVar = lerpColor(Color(0xFF243254), Color(0xFF1A233A), level)

        MidnightSapphireNightColors.copy(
            textPrimary = textPri,
            textSecondary = textSec,
            mutedText = textMuted,
            background = bg,
            surface = surf,
            surfaceVariant = surfVar
        )
    }
}

object AppThemeState {
    var current: AppThemeColors = LightThemeColors
}

val LocalAppColors = staticCompositionLocalOf { LightThemeColors }

// Global dynamic accessors for seamless theme switching in both composable and non-composable contexts
val ObsidianBlack: Color get() = AppThemeState.current.background
val TitaniumDark: Color get() = AppThemeState.current.surface
val TitaniumLight: Color get() = AppThemeState.current.surfaceVariant
val TextPrimary: Color get() = AppThemeState.current.textPrimary
val TextSecondary: Color get() = AppThemeState.current.textSecondary
val MutedText: Color get() = AppThemeState.current.mutedText
val BorderSubtle: Color get() = AppThemeState.current.borderSubtle
val SecureGreen: Color get() = AppThemeState.current.primary
val SecureGreenDim: Color get() = AppThemeState.current.primaryDim
val SecureGreenDark: Color get() = AppThemeState.current.primaryDark
val SecureCyan: Color get() = AppThemeState.current.secondary
val DangerRed: Color get() = AppThemeState.current.dangerRed
val LightCardBackground: Color get() = AppThemeState.current.surface




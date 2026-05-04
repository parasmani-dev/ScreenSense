package com.triggerchain.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ─── Palette ──────────────────────────────────────────────────────────────────
val NightInk       = Color(0xFF0A0D14)
val DeepVoid       = Color(0xFF111622)
val SurfaceSlate   = Color(0xFF1A2033)
val CardSurface    = Color(0xFF1F2840)
val BorderGlass    = Color(0xFF2A3550)

val NeonCyan       = Color(0xFF00E5D4)
val NeonCyanDim    = Color(0xFF00B8AA)
val AlertAmber     = Color(0xFFFFB347)
val DangerRed      = Color(0xFFFF5252)
val CalmGreen      = Color(0xFF4CAF89)
val PulsePurple    = Color(0xFF9C6FFF)

val TextPrimary    = Color(0xFFF0F4FF)
val TextSecondary  = Color(0xFF8A9BC0)
val TextMuted      = Color(0xFF4A5A80)

// ─── Dark color scheme ────────────────────────────────────────────────────────
private val DarkColors = darkColorScheme(
    primary          = NeonCyan,
    onPrimary        = NightInk,
    secondary        = PulsePurple,
    onSecondary      = TextPrimary,
    tertiary         = AlertAmber,
    background       = NightInk,
    onBackground     = TextPrimary,
    surface          = SurfaceSlate,
    onSurface        = TextPrimary,
    surfaceVariant   = CardSurface,
    onSurfaceVariant = TextSecondary,
    outline          = BorderGlass,
    error            = DangerRed,
)

// ─── Light color scheme ───────────────────────────────────────────────────────
private val LightColors = lightColorScheme(
    primary          = Color(0xFF006B63),
    onPrimary        = Color.White,
    secondary        = Color(0xFF5B3FA0),
    onSecondary      = Color.White,
    tertiary         = Color(0xFFB86E00),
    background       = Color(0xFFF5F8FF),
    onBackground     = Color(0xFF0D1929),
    surface          = Color(0xFFFFFFFF),
    onSurface        = Color(0xFF0D1929),
    surfaceVariant   = Color(0xFFEBF0FF),
    onSurfaceVariant = Color(0xFF4A5A80),
    outline          = Color(0xFFD0D8F0),
    error            = Color(0xFFB00020),
)

// ─── Typography ───────────────────────────────────────────────────────────────
val TriggerTypography = Typography(
    displayLarge = TextStyle(
        fontWeight = FontWeight.Black,
        fontSize   = 52.sp,
        letterSpacing = (-2).sp,
        lineHeight = 56.sp,
        color = TextPrimary
    ),
    displayMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize   = 36.sp,
        letterSpacing = (-1).sp,
        lineHeight = 40.sp,
    ),
    headlineLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize   = 28.sp,
        letterSpacing = (-0.5f).sp,
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize   = 22.sp,
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize   = 18.sp,
        letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize   = 15.sp,
        letterSpacing = 0.1f.sp,
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize   = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize   = 14.sp,
        lineHeight = 20.sp,
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize   = 11.sp,
        letterSpacing = 0.5f.sp,
    ),
)

// ─── Theme composable ─────────────────────────────────────────────────────────
@Composable
fun TriggerChainTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        typography  = TriggerTypography,
        content     = content
    )
}

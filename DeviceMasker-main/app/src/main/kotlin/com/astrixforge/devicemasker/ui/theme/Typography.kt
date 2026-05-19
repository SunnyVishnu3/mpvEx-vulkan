package com.astrixforge.devicemasker.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * DeviceMasker Typography.
 *
 * Uses Material 3 typography scale with default system font. Optimized for readability on mobile
 * devices.
 */
val AppTypography =
    Typography(
        // Display styles
        displayLarge =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Bold,
                fontSize = 57.sp,
                lineHeight = 64.sp,
                letterSpacing = (-0.25).sp,
            ),
        displayMedium =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Bold,
                fontSize = 45.sp,
                lineHeight = 52.sp,
                letterSpacing = 0.sp,
            ),
        displaySmall =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Bold,
                fontSize = 36.sp,
                lineHeight = 44.sp,
                letterSpacing = 0.sp,
            ),

        // Headline styles
        headlineLarge =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.SemiBold,
                fontSize = 32.sp,
                lineHeight = 40.sp,
                letterSpacing = 0.sp,
            ),
        headlineMedium =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.SemiBold,
                fontSize = 28.sp,
                lineHeight = 36.sp,
                letterSpacing = 0.sp,
            ),
        headlineSmall =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.SemiBold,
                fontSize = 24.sp,
                lineHeight = 32.sp,
                letterSpacing = 0.sp,
            ),

        // Title styles
        titleLarge =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Medium,
                fontSize = 22.sp,
                lineHeight = 28.sp,
                letterSpacing = 0.sp,
            ),
        titleMedium =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                letterSpacing = 0.15.sp,
            ),
        titleSmall =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                letterSpacing = 0.1.sp,
            ),

        // Body styles
        bodyLarge =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                letterSpacing = 0.5.sp,
            ),
        bodyMedium =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                letterSpacing = 0.25.sp,
            ),
        bodySmall =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Normal,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                letterSpacing = 0.4.sp,
            ),

        // Label styles
        labelLarge =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                letterSpacing = 0.1.sp,
            ),
        labelMedium =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                letterSpacing = 0.5.sp,
            ),
        labelSmall =
            TextStyle(
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                letterSpacing = 0.5.sp,
            ),
    )

// ═══════════════════════════════════════════════════════════
// Material 3 Expressive Typography Extensions
// ═══════════════════════════════════════════════════════════

data class EmphasizedTypography(
    val displayLarge: TextStyle,
    val displayMedium: TextStyle,
    val displaySmall: TextStyle,
    val headlineLarge: TextStyle,
    val headlineMedium: TextStyle,
    val headlineSmall: TextStyle,
    val titleLarge: TextStyle,
    val titleMedium: TextStyle,
    val titleSmall: TextStyle,
    val bodyLarge: TextStyle,
    val bodyMedium: TextStyle,
    val bodySmall: TextStyle,
    val labelLarge: TextStyle,
    val labelMedium: TextStyle,
    val labelSmall: TextStyle,
)

val AppEmphasizedTypography =
    EmphasizedTypography(
        displayLarge =
            AppTypography.displayLarge.copy(fontWeight = FontWeight.Black, letterSpacing = 0.sp),
        displayMedium = AppTypography.displayMedium.copy(fontWeight = FontWeight.Black),
        displaySmall = AppTypography.displaySmall.copy(fontWeight = FontWeight.ExtraBold),
        headlineLarge = AppTypography.headlineLarge.copy(fontWeight = FontWeight.ExtraBold),
        headlineMedium = AppTypography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
        headlineSmall = AppTypography.headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = AppTypography.titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = AppTypography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = AppTypography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = AppTypography.bodyLarge.copy(fontWeight = FontWeight.Medium),
        bodyMedium = AppTypography.bodyMedium.copy(fontWeight = FontWeight.Medium),
        bodySmall = AppTypography.bodySmall.copy(fontWeight = FontWeight.Medium),
        labelLarge = AppTypography.labelLarge.copy(fontWeight = FontWeight.Bold),
        labelMedium = AppTypography.labelMedium.copy(fontWeight = FontWeight.Bold),
        labelSmall = AppTypography.labelSmall.copy(fontWeight = FontWeight.Bold),
    )

val LocalEmphasizedTypography = staticCompositionLocalOf { AppEmphasizedTypography }

package app.gyrolet.mpvrx.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import app.gyrolet.mpvrx.R

// Roboto Flex font family (variable font supporting weights 100-900)
val RobotoFlex = FontFamily(
  Font(
    resId = R.font.roboto_flex,
    weight = FontWeight.Thin,
    style = FontStyle.Normal,
  ),
  Font(
    resId = R.font.roboto_flex,
    weight = FontWeight.ExtraLight,
    style = FontStyle.Normal,
  ),
  Font(
    resId = R.font.roboto_flex,
    weight = FontWeight.Light,
    style = FontStyle.Normal,
  ),
  Font(
    resId = R.font.roboto_flex,
    weight = FontWeight.Normal,
    style = FontStyle.Normal,
  ),
  Font(
    resId = R.font.roboto_flex,
    weight = FontWeight.Medium,
    style = FontStyle.Normal,
  ),
  Font(
    resId = R.font.roboto_flex,
    weight = FontWeight.SemiBold,
    style = FontStyle.Normal,
  ),
  Font(
    resId = R.font.roboto_flex,
    weight = FontWeight.Bold,
    style = FontStyle.Normal,
  ),
  Font(
    resId = R.font.roboto_flex,
    weight = FontWeight.ExtraBold,
    style = FontStyle.Normal,
  ),
  Font(
    resId = R.font.roboto_flex,
    weight = FontWeight.Black,
    style = FontStyle.Normal,
  ),
)

val SystemTypography = Typography()

// Use Roboto Flex typography app-wide
val AppTypography = SystemTypography.run {
  copy(
    displayLarge = displayLarge.copy(fontFamily = RobotoFlex),
    displayMedium = displayMedium.copy(fontFamily = RobotoFlex),
    displaySmall = displaySmall.copy(fontFamily = RobotoFlex),
    headlineLarge = headlineLarge.copy(fontFamily = RobotoFlex),
    headlineMedium = headlineMedium.copy(fontFamily = RobotoFlex),
    headlineSmall = headlineSmall.copy(fontFamily = RobotoFlex),
    titleLarge = titleLarge.copy(fontFamily = RobotoFlex),
    titleMedium = titleMedium.copy(fontFamily = RobotoFlex),
    titleSmall = titleSmall.copy(fontFamily = RobotoFlex),
    bodyLarge = bodyLarge.copy(fontFamily = RobotoFlex),
    bodyMedium = bodyMedium.copy(fontFamily = RobotoFlex),
    bodySmall = bodySmall.copy(fontFamily = RobotoFlex),
    labelLarge = labelLarge.copy(fontFamily = RobotoFlex),
    labelMedium = labelMedium.copy(fontFamily = RobotoFlex),
    labelSmall = labelSmall.copy(fontFamily = RobotoFlex),
  )
}

// ═══════════════════════════════════════════════════════════
// Material 3 Expressive Typography Extensions
// Bumps font weights one step heavier for expressive emphasis
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

val AppEmphasizedTypography = EmphasizedTypography(
  displayLarge = AppTypography.displayLarge.copy(fontWeight = FontWeight.Black),
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


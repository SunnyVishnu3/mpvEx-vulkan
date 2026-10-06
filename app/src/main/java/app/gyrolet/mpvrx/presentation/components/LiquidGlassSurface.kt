/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package app.gyrolet.mpvrx.presentation.components

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.gyrolet.mpvrx.BuildConfig
import app.gyrolet.mpvrx.preferences.AppearancePreferences
import app.gyrolet.mpvrx.preferences.preference.collectAsState
import app.gyrolet.mpvrx.ui.theme.AppMotion
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.hazeGlass
import org.koin.compose.koinInject

typealias LiquidGlassBackdrop = HazeState

private val LocalLiquidGlassBackdrop = staticCompositionLocalOf<LiquidGlassBackdrop?> { null }

@Composable
fun rememberLiquidGlassBackdrop(): LiquidGlassBackdrop = rememberHazeState()

fun Modifier.captureLiquidGlassBackdrop(
  backdrop: LiquidGlassBackdrop?,
  enabled: Boolean = true,
): Modifier =
  if (enabled && backdrop != null) hazeSource(backdrop) else this

@Composable
fun ProvideLiquidGlassBackdrop(
  backdrop: LiquidGlassBackdrop,
  enabled: Boolean = true,
  content: @Composable () -> Unit,
) {
  CompositionLocalProvider(
    LocalLiquidGlassBackdrop provides backdrop.takeIf { enabled },
    content = content,
  )
}

enum class LiquidGlassStyle {
  MiniPlayer,
  Navigation,
}

/**
 * Dynamic liquid glass surface supporting real-time preference tuning and optical refraction.
 *
 * Automatically connects to user-defined optical parameters from AppearancePreferences
 * (blur, lens depth, lens radius, opacity) with graceful fallback and debug logging.
 */
@OptIn(ExperimentalHazeApi::class)
@Composable
fun LiquidGlassSurface(
  shape: Shape,
  modifier: Modifier = Modifier,
  style: LiquidGlassStyle = LiquidGlassStyle.Navigation,
  glassColor: Color,
  fallbackColor: Color,
  contentColor: Color = MaterialTheme.colorScheme.onSurface,
  backdrop: LiquidGlassBackdrop? = LocalLiquidGlassBackdrop.current,
  glowStrength: Float = 1f,
  content: @Composable BoxScope.() -> Unit,
) {
  val preferences = koinInject<AppearancePreferences>()
  val liquidBlur by preferences.liquidButtonBlur.collectAsState()
  val liquidLensRadius by preferences.liquidButtonLensRadius.collectAsState()
  val liquidLensDepth by preferences.liquidButtonLensDepth.collectAsState()
  val liquidOpacity by preferences.liquidButtonOpacity.collectAsState()

  val reducedMotion = AppMotion.shouldReduceMotion()
  val baseBlur = if (style == LiquidGlassStyle.MiniPlayer) 12.dp else 8.dp
  val blurRadius = (baseBlur * (liquidBlur / 26f)).coerceIn(4.dp, 32.dp)
  val baseRefractionFraction = if (style == LiquidGlassStyle.MiniPlayer) 0.30f else 0.28f
  val refractionHeightFraction = (baseRefractionFraction * (liquidLensRadius / 42f)).coerceIn(0.12f, 0.50f)
  val baseDisplacement = if (style == LiquidGlassStyle.MiniPlayer) 26.dp else 22.dp
  val refractionAmount = (baseDisplacement * (liquidLensDepth / 72f)).coerceIn(8.dp, 64.dp)
  val shadowElevation: Dp = if (style == LiquidGlassStyle.MiniPlayer) 10.dp else 8.dp
  val roundedShape = shape as? RoundedCornerShape

  if (BuildConfig.DEBUG) {
    Log.d(
      "LiquidGlass",
      "Dynamic LiquidGlassSurface: style=$style, blur=$blurRadius, refraction=$refractionAmount, hasBackdrop=${backdrop != null}"
    )
  }

  val effectiveGlassColor = remember(glassColor, liquidOpacity) {
    glassColor.copy(alpha = (glassColor.alpha * (liquidOpacity / 0.15f)).coerceIn(0.10f, 0.65f))
  }

  val glassStyle =
    remember(roundedShape, style, effectiveGlassColor, fallbackColor, reducedMotion, glowStrength, blurRadius, refractionAmount, refractionHeightFraction) {
      roundedShape?.let { resolvedShape ->
        GlassStyle {
          shape(resolvedShape)
          tint(effectiveGlassColor)
          backgroundColor(fallbackColor.copy(alpha = 0.08f))
          optics(
            refractionStrength = if (reducedMotion) 0f else 0.72f,
            refractionHeightFraction = if (reducedMotion) 0f else refractionHeightFraction,
            refractionDisplacement = if (reducedMotion) 0.dp else refractionAmount,
            depth = if (reducedMotion) 0f else 0.6f,
            blurRadius = blurRadius,
            refractionDetailIntensity = if (reducedMotion) 0f else 0.5f,
          )
          chromaMultiplier(1.08f)
          contrast(0.04f)
          specularIntensity((if (reducedMotion) 0.28f else 0.52f) * glowStrength)
          ambientResponse(0.36f * glowStrength)
          edgeSoftness(1.dp)
          edgeShadow(Color.Black.copy(alpha = 0.16f * glowStrength))
          chromaticAberrationStrength(if (reducedMotion) 0f else 0.12f * glowStrength)
        }
      }
    }

  val surfaceModifier =
    if (backdrop != null && roundedShape != null && glassStyle != null) {
      modifier
        .shadow(shadowElevation, shape)
        .clip(shape)
        .hazeGlass(
          input = HazeInput.Sources(backdrop),
          style = glassStyle,
        )
    } else {
      modifier
        .shadow(shadowElevation, shape)
        .clip(shape)
        .background(fallbackColor)
    }

  CompositionLocalProvider(LocalContentColor provides contentColor) {
    Box(modifier = surfaceModifier, content = content)
  }
}

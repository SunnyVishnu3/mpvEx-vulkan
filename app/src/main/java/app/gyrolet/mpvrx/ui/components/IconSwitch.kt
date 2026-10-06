/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package app.gyrolet.mpvrx.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.PathEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.gyrolet.mpvrx.ui.icons.Icon
import app.gyrolet.mpvrx.ui.icons.Icons
import app.gyrolet.mpvrx.ui.theme.AppMotion
import app.gyrolet.mpvrx.ui.utils.rememberAppHaptics

import app.gyrolet.mpvrx.preferences.AppearancePreferences
import app.gyrolet.mpvrx.preferences.preference.collectAsState
import app.gyrolet.mpvrx.ui.liquidglass.LiquidToggle
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import org.koin.compose.koinInject

private const val SWITCH_MOTION_DURATION_MS = 250
private const val SWITCH_PRESSED_DURATION_MS = 100
private val SwitchWidth = 52.dp
private val SwitchHeight = 32.dp
private val UncheckedThumbSize = 16.dp
private val CheckedThumbSize = 24.dp
private val PressedThumbSize = 28.dp
private val UncheckedThumbCenter = 16.dp
private val CheckedThumbCenter = 36.dp

private val LogFoxSwitchEasing =
  PathEasing(
    Path().apply {
      moveTo(0f, 0f)
      cubicTo(0.05f, 0f, 0.133333f, 0.06f, 0.166666f, 0.4f)
      cubicTo(0.208333f, 0.82f, 0.25f, 1f, 1f, 1f)
    },
  )

private fun contrastingIconColor(background: Color): Color =
  if (background.luminance() > 0.179f) Color.Black else Color.White

@Composable
fun IconSwitch(
  checked: Boolean,
  onCheckedChange: ((Boolean) -> Unit)?,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
) {
  val haptics = rememberAppHaptics()
  val appearancePreferences = koinInject<AppearancePreferences>()
  val enableLiquidGlass by appearancePreferences.enableLiquidGlass.collectAsState()
  val liquidToggleColor by appearancePreferences.liquidToggleColor.collectAsState()

  if (enableLiquidGlass) {
    val backdrop = rememberLayerBackdrop()
    LiquidToggle(
      selected = { checked },
      onSelect = { newValue ->
        if (enabled && newValue != checked) {
          onCheckedChange?.invoke(newValue)
          haptics.selection(newValue)
        }
      },
      backdrop = backdrop,
      modifier = modifier,
      accentColor = Color(liquidToggleColor),
      enabled = enabled,
      isInteractive = onCheckedChange != null,
    )
    return
  }

  val interactionSource = remember { MutableInteractionSource() }
  val isPressed by interactionSource.collectIsPressedAsState()
  val reducedMotion = AppMotion.shouldReduceMotion()
  val positionSpec: FiniteAnimationSpec<Dp> =
    if (reducedMotion) snap() else tween(SWITCH_MOTION_DURATION_MS, easing = LogFoxSwitchEasing)
  val thumbSpec: FiniteAnimationSpec<Dp> =
    if (reducedMotion) {
      snap()
    } else if (isPressed) {
      tween(SWITCH_PRESSED_DURATION_MS, easing = LogFoxSwitchEasing)
    } else {
      positionSpec
    }
  val colorSpec: FiniteAnimationSpec<Color> =
    if (reducedMotion) snap() else tween(SWITCH_MOTION_DURATION_MS, easing = LogFoxSwitchEasing)

  val trackColor by animateColorAsState(
    targetValue =
      if (checked) {
        MaterialTheme.colorScheme.primary
      } else {
        MaterialTheme.colorScheme.surfaceContainerHighest
      },
    animationSpec = colorSpec,
    label = "IconSwitchTrackColor",
  )
  val borderColor by animateColorAsState(
    targetValue = if (checked) Color.Transparent else MaterialTheme.colorScheme.outline,
    animationSpec = colorSpec,
    label = "IconSwitchBorderColor",
  )
  val thumbColor by animateColorAsState(
    targetValue =
      when {
        checked && isPressed -> MaterialTheme.colorScheme.primaryContainer
        checked -> MaterialTheme.colorScheme.onPrimary
        isPressed -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.outline
      },
    animationSpec = colorSpec,
    label = "IconSwitchThumbColor",
  )
  val thumbSize by animateDpAsState(
    targetValue = if (isPressed) PressedThumbSize else if (checked) CheckedThumbSize else UncheckedThumbSize,
    animationSpec = thumbSpec,
    label = "IconSwitchThumbSize",
  )
  val thumbCenter by animateDpAsState(
    targetValue = if (checked) CheckedThumbCenter else UncheckedThumbCenter,
    animationSpec = positionSpec,
    label = "IconSwitchThumbPosition",
  )
  val iconColor = contrastingIconColor(thumbColor)
  val interactionModifier =
    if (onCheckedChange == null) {
      Modifier
    } else {
      Modifier.toggleable(
        value = checked,
        enabled = enabled,
        role = Role.Switch,
        interactionSource = interactionSource,
        indication = null,
      ) { value ->
        if (value != checked) {
          onCheckedChange(value)
          haptics.selection(value)
        }
      }
    }

  Box(
    modifier =
      modifier
        .minimumInteractiveComponentSize()
        .then(interactionModifier),
    contentAlignment = Alignment.Center,
  ) {
    Box(
      modifier =
        Modifier
          .size(SwitchWidth, SwitchHeight)
          .alpha(if (enabled) 1f else 0.38f)
          .clip(CircleShape)
          .background(trackColor)
          .indication(interactionSource, LocalIndication.current)
          .border(2.dp, borderColor, CircleShape),
    ) {
      Box(
        modifier =
          Modifier
            .offset(
              x = thumbCenter - thumbSize / 2,
              y = (SwitchHeight - thumbSize) / 2,
            ).size(thumbSize)
            .shadow(1.dp, CircleShape)
            .clip(CircleShape)
            .background(thumbColor),
        contentAlignment = Alignment.Center,
      ) {
        AnimatedContent(
          targetState = checked,
          transitionSpec = {
            if (reducedMotion) {
              fadeIn(snap()) togetherWith fadeOut(snap())
            } else {
              (fadeIn(tween(150, delayMillis = 100, easing = LogFoxSwitchEasing)) +
                scaleIn(
                  animationSpec = tween(150, delayMillis = 100, easing = LogFoxSwitchEasing),
                  initialScale = 0.6f,
                )) togetherWith
                (fadeOut(tween(100, easing = LogFoxSwitchEasing)) +
                  scaleOut(tween(100, easing = LogFoxSwitchEasing), targetScale = 0.6f))
            }
          },
          label = "IconSwitchSymbol",
        ) { isChecked ->
          Icon(
            imageVector = if (isChecked) Icons.RoundedFilled.Check else Icons.RoundedFilled.Close,
            contentDescription = null,
            modifier = Modifier.size(if (isChecked) 14.dp else 12.dp),
            tint = iconColor,
          )
        }
      }
    }
  }
}

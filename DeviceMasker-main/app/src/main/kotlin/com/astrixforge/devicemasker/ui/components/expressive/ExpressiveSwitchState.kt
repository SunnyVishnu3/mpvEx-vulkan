package com.astrixforge.devicemasker.ui.components.expressive

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.astrixforge.devicemasker.ui.theme.AppMotion

internal data class ExpressiveSwitchState(
    val thumbOffset: Dp,
    val thumbSize: Dp,
    val trackColor: Color,
    val thumbColor: Color,
    val borderColor: Color,
    val iconColor: Color,
    val iconScale: Float,
    val switchScale: Float,
)

@Composable
internal fun rememberExpressiveSwitchState(
    checked: Boolean,
    enabled: Boolean,
    isPressed: Boolean,
): ExpressiveSwitchState =
    ExpressiveSwitchState(
        thumbOffset = animatedThumbOffset(checked),
        thumbSize = animatedThumbSize(checked = checked, isPressed = isPressed),
        trackColor = animatedTrackColor(checked = checked, enabled = enabled),
        thumbColor = animatedThumbColor(checked = checked, enabled = enabled),
        borderColor = animatedBorderColor(checked = checked, enabled = enabled),
        iconColor = animatedIconColor(checked = checked, enabled = enabled),
        iconScale = animatedIconScale(checked),
        switchScale = animatedSwitchScale(isPressed),
    )

@Composable
private fun animatedThumbOffset(checked: Boolean): Dp {
    val target =
        if (checked) {
            SwitchDimensions.TrackWidth -
                SwitchDimensions.ThumbSizeChecked -
                SwitchDimensions.ThumbPadding
        } else {
            SwitchDimensions.ThumbPadding
        }
    val value by
        animateDpAsState(
            targetValue = target,
            animationSpec = AppMotion.Spatial.SnappyDp,
            label = "thumbOffset",
        )
    return value
}

@Composable
private fun animatedThumbSize(checked: Boolean, isPressed: Boolean): Dp {
    val value by
        animateDpAsState(
            targetValue = thumbSizeTarget(checked = checked, isPressed = isPressed),
            animationSpec = AppMotion.Spatial.SnappyDp,
            label = "thumbSize",
        )
    return value
}

private fun thumbSizeTarget(checked: Boolean, isPressed: Boolean): Dp =
    when {
        isPressed -> SwitchDimensions.ThumbSizePressed
        checked -> SwitchDimensions.ThumbSizeChecked
        else -> SwitchDimensions.ThumbSizeUnchecked
    }

@Composable
private fun animatedTrackColor(checked: Boolean, enabled: Boolean): Color {
    val activeColor = MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val value by
        animateColorAsState(
            targetValue =
                when {
                    enabled && checked -> activeColor
                    enabled -> inactiveColor
                    checked -> activeColor.copy(alpha = DISABLED_CHECKED_ALPHA)
                    else -> inactiveColor.copy(alpha = DISABLED_UNCHECKED_ALPHA)
                },
            animationSpec = AppMotion.Effect.Color,
            label = "trackColor",
        )
    return value
}

@Composable
private fun animatedThumbColor(checked: Boolean, enabled: Boolean): Color {
    val checkedColor = MaterialTheme.colorScheme.onPrimary
    val uncheckedColor = MaterialTheme.colorScheme.outline
    val value by
        animateColorAsState(
            targetValue =
                when {
                    enabled && checked -> checkedColor
                    enabled -> uncheckedColor
                    checked -> checkedColor.copy(alpha = DISABLED_THUMB_ALPHA)
                    else -> uncheckedColor.copy(alpha = DISABLED_CHECKED_ALPHA)
                },
            animationSpec = AppMotion.Effect.Color,
            label = "thumbColor",
        )
    return value
}

@Composable
private fun animatedBorderColor(checked: Boolean, enabled: Boolean): Color {
    val outlineColor = MaterialTheme.colorScheme.outline
    val value by
        animateColorAsState(
            targetValue =
                when {
                    checked -> Color.Transparent
                    enabled -> outlineColor
                    else -> outlineColor.copy(alpha = DISABLED_UNCHECKED_ALPHA)
                },
            animationSpec = AppMotion.Effect.Color,
            label = "borderColor",
        )
    return value
}

@Composable
private fun animatedIconColor(checked: Boolean, enabled: Boolean): Color {
    val checkedColor = MaterialTheme.colorScheme.onPrimaryContainer
    val uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant
    val value by
        animateColorAsState(
            targetValue =
                when {
                    enabled && checked -> checkedColor
                    enabled -> uncheckedColor
                    checked -> checkedColor.copy(alpha = DISABLED_CHECKED_ALPHA)
                    else -> uncheckedColor.copy(alpha = DISABLED_CHECKED_ALPHA)
                },
            animationSpec = AppMotion.Effect.Color,
            label = "iconColor",
        )
    return value
}

@Composable
private fun animatedIconScale(checked: Boolean): Float {
    val value by
        animateFloatAsState(
            targetValue = if (checked) CHECKED_ICON_SCALE else UNCHECKED_ICON_SCALE,
            animationSpec = AppMotion.Spatial.Snappy,
            label = "iconScale",
        )
    return value
}

@Composable
private fun animatedSwitchScale(isPressed: Boolean): Float {
    val value by
        animateFloatAsState(
            targetValue = if (isPressed) PRESSED_SWITCH_SCALE else DEFAULT_SWITCH_SCALE,
            animationSpec = AppMotion.Spatial.Snappy,
            label = "switchScale",
        )
    return value
}

private const val DISABLED_CHECKED_ALPHA = 0.38f
private const val DISABLED_UNCHECKED_ALPHA = 0.12f
private const val DISABLED_THUMB_ALPHA = 0.7f
private const val CHECKED_ICON_SCALE = 1f
private const val UNCHECKED_ICON_SCALE = 0.85f
private const val PRESSED_SWITCH_SCALE = 0.95f
private const val DEFAULT_SWITCH_SCALE = 1f

package com.astrixforge.devicemasker.ui.components.expressive

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.astrixforge.devicemasker.ui.theme.DeviceMaskerTheme

/**
 * Material 3 Expressive Switch with spring-animated thumb transitions.
 *
 * A fully custom switch implementation that follows M3 visual specs while providing expressive
 * spring-based animations for all state changes:
 * - **Thumb position**: Bouncy spring animation when toggling
 * - **Thumb size**: Grows when checked, shrinks when unchecked, expands when pressed
 * - **Colors**: Smooth spring transitions for track and thumb colors
 * - **Icon**: Animated check/close icon with scaling effect
 *
 * ## M3 Expressive Features:
 * - Spring physics for natural feel (using AppMotion.Spatial.Snappy)
 * - Thumb size morphing on state change (M3 spec)
 * - Press feedback with thumb expansion
 * - Subtle scale animation on toggle
 *
 * ## Theme Integration:
 * - Uses MaterialTheme.colorScheme.primary for checked track color
 * - Supports dynamic colors on Android 12+
 * - Adapts to light/dark theme automatically
 *
 * @param checked Current checked state
 * @param onCheckedChange Callback when state changes. If null, switch is read-only
 * @param modifier Optional modifier
 * @param enabled Whether the switch is interactive
 * @param showThumbIcon Whether to show check/close icon in thumb
 * @sample ExpressiveSwitchPreview
 */
@Composable
fun ExpressiveSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    showThumbIcon: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val switchState =
        rememberExpressiveSwitchState(checked = checked, enabled = enabled, isPressed = isPressed)
    val density = LocalDensity.current

    SwitchTrack(
        state = switchState,
        interactionSource = interactionSource,
        enabled = enabled && onCheckedChange != null,
        onClick = { onCheckedChange?.invoke(!checked) },
        modifier = modifier,
    ) {
        SwitchThumb(
            state = switchState,
            checked = checked,
            showThumbIcon = showThumbIcon,
            thumbOffset = with(density) { switchState.thumbOffset.roundToPx() },
        )
    }
}

@Composable
private fun SwitchTrack(
    state: ExpressiveSwitchState,
    interactionSource: MutableInteractionSource,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier =
            modifier
                .graphicsLayer {
                    scaleX = state.switchScale
                    scaleY = state.switchScale
                }
                .size(width = SwitchDimensions.TrackWidth, height = SwitchDimensions.TrackHeight)
                .clip(RoundedCornerShape(SwitchDimensions.TrackCornerRadius))
                .background(state.trackColor)
                .border(
                    width = SwitchDimensions.BorderWidth,
                    color = state.borderColor,
                    shape = RoundedCornerShape(SwitchDimensions.TrackCornerRadius),
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled,
                    role = Role.Switch,
                    onClick = onClick,
                ),
        contentAlignment = Alignment.CenterStart,
    ) {
        content()
    }
}

@Composable
private fun SwitchThumb(
    state: ExpressiveSwitchState,
    checked: Boolean,
    showThumbIcon: Boolean,
    thumbOffset: Int,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .offset { IntOffset(x = thumbOffset, y = 0) }
                .size(state.thumbSize)
                .background(color = state.thumbColor, shape = CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (showThumbIcon && state.thumbSize >= SwitchDimensions.ThumbSizeChecked) {
            Icon(
                imageVector = if (checked) Icons.Filled.Check else Icons.Filled.Close,
                contentDescription = null,
                modifier =
                    Modifier.size(SwitchDimensions.IconSize).graphicsLayer {
                        scaleX = state.iconScale
                        scaleY = state.iconScale
                    },
                tint = state.iconColor,
            )
        }
    }
}

/**
 * Switch with an inline label that's fully clickable.
 *
 * Use this for settings items or list items where the entire row should toggle the switch state.
 *
 * @param checked Current checked state
 * @param onCheckedChange Callback when state changes
 * @param label Text label displayed next to the switch
 * @param modifier Optional modifier
 * @param enabled Whether the switch is interactive
 * @param showThumbIcon Whether to show check/close icon in thumb
 * @param labelFirst If true, label appears before switch (default: true)
 */
@Composable
fun ExpressiveSwitchWithLabel(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    showThumbIcon: Boolean = true,
    labelFirst: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        modifier =
            modifier.clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Switch,
                onClick = { onCheckedChange(!checked) },
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (labelFirst) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color =
                    if (enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    },
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(16.dp))
        }

        ExpressiveSwitch(
            checked = checked,
            onCheckedChange = null, // Handled by Row click
            enabled = enabled,
            showThumbIcon = showThumbIcon,
        )

        if (!labelFirst) {
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color =
                    if (enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// Previews
// ═══════════════════════════════════════════════════════════════════════════════

@Preview(showBackground = true, backgroundColor = 0xFF121212, name = "Unchecked & Checked")
@Composable
private fun ExpressiveSwitchPreview() {
    DeviceMaskerTheme {
        Row(modifier = Modifier.padding(16.dp)) {
            ExpressiveSwitch(checked = false, onCheckedChange = {})
            Spacer(modifier = Modifier.width(24.dp))
            ExpressiveSwitch(checked = true, onCheckedChange = {})
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212, name = "Without Icon")
@Composable
private fun ExpressiveSwitchNoIconPreview() {
    DeviceMaskerTheme {
        Row(modifier = Modifier.padding(16.dp)) {
            ExpressiveSwitch(checked = false, onCheckedChange = {}, showThumbIcon = false)
            Spacer(modifier = Modifier.width(24.dp))
            ExpressiveSwitch(checked = true, onCheckedChange = {}, showThumbIcon = false)
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212, name = "Disabled States")
@Composable
private fun ExpressiveSwitchDisabledPreview() {
    DeviceMaskerTheme {
        Row(modifier = Modifier.padding(16.dp)) {
            ExpressiveSwitch(checked = false, onCheckedChange = {}, enabled = false)
            Spacer(modifier = Modifier.width(24.dp))
            ExpressiveSwitch(checked = true, onCheckedChange = {}, enabled = false)
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212, name = "With Label")
@Composable
private fun ExpressiveSwitchWithLabelPreview() {
    DeviceMaskerTheme {
        ExpressiveSwitchWithLabel(
            checked = true,
            onCheckedChange = {},
            label = "Enable Feature",
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF, name = "Light Theme")
@Composable
private fun ExpressiveSwitchLightPreview() {
    DeviceMaskerTheme(themeMode = com.astrixforge.devicemasker.data.models.ThemeMode.LIGHT) {
        Row(modifier = Modifier.padding(16.dp)) {
            ExpressiveSwitch(checked = false, onCheckedChange = {})
            Spacer(modifier = Modifier.width(24.dp))
            ExpressiveSwitch(checked = true, onCheckedChange = {})
        }
    }
}

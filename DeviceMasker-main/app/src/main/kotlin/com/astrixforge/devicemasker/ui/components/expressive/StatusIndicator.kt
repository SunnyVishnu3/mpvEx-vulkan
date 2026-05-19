package com.astrixforge.devicemasker.ui.components.expressive

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.astrixforge.devicemasker.ui.theme.AppMotion
import com.astrixforge.devicemasker.ui.theme.DeviceMaskerTheme
import com.astrixforge.devicemasker.ui.theme.statusActive
import com.astrixforge.devicemasker.ui.theme.statusInactive
import com.astrixforge.devicemasker.ui.theme.statusOnActive
import com.astrixforge.devicemasker.ui.theme.statusOnInactive
import com.astrixforge.devicemasker.ui.theme.statusWarning

/**
 * Status indicator with Material 3 Expressive animated color transitions.
 *
 * Features:
 * - Smooth spring-animated color transitions (no overshoot)
 * - Optional pulse scale animation for active state
 * - Optional icon inside the indicator
 * - Consistent styling with app status colors
 *
 * Use this component for status displays:
 * - Module active/inactive status
 * - Anti-detection test results
 * - Spoof status indicators
 *
 * @param color The status color
 * @param modifier Modifier for the indicator
 * @param size Indicator size (default 24.dp)
 * @param icon Optional icon to display inside
 * @param iconTint Icon tint color (default white)
 * @param showPulse Whether to show a subtle pulse animation for active states
 */
@Composable
fun StatusIndicator(
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    icon: ImageVector? = null,
    iconTint: Color = Color.Unspecified,
    showPulse: Boolean = false,
) {
    val resolvedIconTint =
        if (iconTint == Color.Unspecified) MaterialTheme.colorScheme.statusOnActive else iconTint

    // Effect spring for color (NO overshoot)
    val animatedColor by
        animateColorAsState(
            targetValue = color,
            animationSpec = AppMotion.Effect.Color,
            label = "statusColor",
        )

    // Optional pulse animation
    val scale by
        animateFloatAsState(
            targetValue = if (showPulse) 1.05f else 1f,
            animationSpec = AppMotion.Spatial.Standard,
            label = "statusPulse",
        )

    Box(
        modifier =
            modifier
                .size(size)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .background(color = animatedColor, shape = CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = resolvedIconTint,
                modifier = Modifier.size(size * ICON_SIZE_FRACTION),
            )
        }
    }
}

private const val ICON_SIZE_FRACTION = 0.6f

/** Status indicator that automatically shows check/close icons based on state. */
@Composable
fun StatusIndicatorWithIcon(
    isSuccess: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    successColor: Color = MaterialTheme.colorScheme.statusActive,
    failureColor: Color = MaterialTheme.colorScheme.statusInactive,
) {
    StatusIndicator(
        color = if (isSuccess) successColor else failureColor,
        icon = if (isSuccess) Icons.Default.Check else Icons.Default.Close,
        iconTint =
            if (isSuccess) {
                MaterialTheme.colorScheme.statusOnActive
            } else {
                MaterialTheme.colorScheme.statusOnInactive
            },
        modifier = modifier,
        size = size,
    )
}

/** Larger status indicator for hero displays (e.g., module status card). */
@Composable
fun HeroStatusIndicator(
    isActive: Boolean,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val statusColor =
        if (isActive) MaterialTheme.colorScheme.statusActive
        else MaterialTheme.colorScheme.statusInactive
    val iconTint =
        if (isActive) MaterialTheme.colorScheme.statusOnActive
        else MaterialTheme.colorScheme.statusOnInactive

    StatusIndicator(
        color = statusColor,
        icon = icon,
        iconTint = iconTint,
        modifier = modifier,
        size = 48.dp,
        showPulse = isActive,
    )
}

// ═══════════════════════════════════════════════════════════
// Previews
// ═══════════════════════════════════════════════════════════

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun StatusIndicatorPreview() {
    DeviceMaskerTheme {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.background(MaterialTheme.colorScheme.surface),
        ) {
            StatusIndicator(color = MaterialTheme.colorScheme.statusActive)
            StatusIndicator(color = MaterialTheme.colorScheme.statusWarning)
            StatusIndicator(color = MaterialTheme.colorScheme.statusInactive)
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun StatusIndicatorWithIconPreview() {
    DeviceMaskerTheme {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            StatusIndicatorWithIcon(isSuccess = true)
            StatusIndicatorWithIcon(isSuccess = false)
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun HeroStatusIndicatorPreview() {
    DeviceMaskerTheme {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            HeroStatusIndicator(isActive = true)
            HeroStatusIndicator(isActive = false)
        }
    }
}

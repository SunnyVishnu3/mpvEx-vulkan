package com.astrixforge.devicemasker.ui.components.expressive

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.astrixforge.devicemasker.ui.theme.AppMotion
import com.astrixforge.devicemasker.ui.theme.DeviceMaskerTheme

/**
 * IconButton with Material 3 Expressive spring-animated scale feedback.
 *
 * Features:
 * - Bouncy scale animation on press (0.85x) with expressive spring return
 * - Consistent sizing and styling
 * - Automatic disabled state handling
 *
 * Use this component for action buttons that need tactile feedback:
 * - Refresh buttons
 * - Edit buttons
 * - Copy buttons
 * - Card action rows
 *
 * @param onClick Click handler
 * @param icon Vector icon to display
 * @param contentDescription Accessibility description (null for decorative icons)
 * @param modifier Modifier for the button
 * @param enabled Whether the button is clickable
 * @param tint Icon tint color (defaults to LocalContentColor)
 * @param iconSize Icon size (default 24.dp)
 * @param buttonSize Overall button touch target size (default 48.dp)
 */
@Composable
fun ExpressiveIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = LocalContentColor.current,
    iconSize: Dp = 24.dp,
    buttonSize: Dp = 48.dp,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Spatial spring for scale (CAN overshoot for bouncy feel)
    val scale by
        animateFloatAsState(
            targetValue = if (isPressed && enabled) 0.85f else 1f,
            animationSpec = AppMotion.Spatial.Expressive,
            label = "iconButtonScale",
        )

    IconButton(
        onClick = onClick,
        modifier =
            modifier.minimumInteractiveComponentSize().size(buttonSize).graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        enabled = enabled,
        interactionSource = interactionSource,
        colors =
            IconButtonDefaults.iconButtonColors(
                contentColor = tint,
                disabledContentColor = tint.copy(alpha = 0.38f),
            ),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(iconSize),
        )
    }
}

/**
 * Compact visual version of ExpressiveIconButton for action rows. The visual button is compact,
 * while minimumInteractiveComponentSize keeps the touch target accessible.
 */
@Composable
fun CompactExpressiveIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    ExpressiveIconButton(
        onClick = onClick,
        icon = icon,
        contentDescription = contentDescription,
        modifier = modifier,
        enabled = enabled,
        tint = tint,
        iconSize = 20.dp,
        buttonSize = 36.dp,
    )
}

// ═══════════════════════════════════════════════════════════
// Previews
// ═══════════════════════════════════════════════════════════

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun ExpressiveIconButtonPreview() {
    DeviceMaskerTheme {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ExpressiveIconButton(
                onClick = {},
                icon = Icons.Default.Refresh,
                contentDescription = "Refresh",
                tint = MaterialTheme.colorScheme.primary,
            )
            ExpressiveIconButton(
                onClick = {},
                icon = Icons.Default.Edit,
                contentDescription = "Edit",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ExpressiveIconButton(
                onClick = {},
                icon = Icons.Default.ContentCopy,
                contentDescription = "Copy",
                enabled = false,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun CompactExpressiveIconButtonPreview() {
    DeviceMaskerTheme {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            CompactExpressiveIconButton(
                onClick = {},
                icon = Icons.Default.Refresh,
                contentDescription = "Refresh",
            )
            CompactExpressiveIconButton(
                onClick = {},
                icon = Icons.Default.Edit,
                contentDescription = "Edit",
            )
            CompactExpressiveIconButton(
                onClick = {},
                icon = Icons.Default.ContentCopy,
                contentDescription = "Copy",
            )
        }
    }
}

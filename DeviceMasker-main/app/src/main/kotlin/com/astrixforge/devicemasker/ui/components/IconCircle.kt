package com.astrixforge.devicemasker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.astrixforge.devicemasker.ui.theme.DeviceMaskerTheme

/**
 * Circular container for icons with customizable colors and sizes.
 *
 * Common usage pattern across the app for settings items, status indicators, and feature icons.
 *
 * @param icon The icon to display
 * @param modifier Optional modifier
 * @param size Size of the circular container
 * @param containerColor Background color of the circle
 * @param iconColor Tint color for the icon
 * @param iconSize Size of the icon within the container
 * @param contentDescription Accessibility description for the icon
 */
@Composable
fun IconCircle(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    iconColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    iconSize: Dp = 20.dp,
    contentDescription: String? = null,
) {
    Box(
        modifier = modifier.size(size).clip(CircleShape).background(containerColor),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = iconColor,
            modifier = Modifier.size(iconSize),
        )
    }
}

// ═══════════════════════════════════════════════════════════
// Previews
// ═══════════════════════════════════════════════════════════

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun IconCirclePreview() {
    DeviceMaskerTheme {
        IconCircle(
            icon = Icons.Filled.Shield,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            iconColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun IconCircleLargePreview() {
    DeviceMaskerTheme {
        IconCircle(
            icon = Icons.Filled.Shield,
            size = 64.dp,
            iconSize = 32.dp,
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            iconColor = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

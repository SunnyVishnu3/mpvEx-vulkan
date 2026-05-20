package com.astrixforge.devicemasker.ui.components.expressive

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.astrixforge.devicemasker.ui.theme.AppMotion
import com.astrixforge.devicemasker.ui.theme.DeviceMaskerTheme

/**
 * Material 3 Expressive Loading Indicator.
 *
 * Wraps the native Material 3 Expressive LoadingIndicator from material3 1.5.0-alpha18.
 *
 * Best for: 200ms - 5s wait times For longer waits: Use progress indicators with determinate
 * progress
 *
 * @param modifier Modifier for the indicator
 * @param size Size of the loading indicator
 * @param color Color of the indicator (defaults to primary)
 */
@Composable
fun ExpressiveLoadingIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    LoadingIndicator(modifier = modifier.size(size), color = color)
}

/**
 * Loading indicator with optional label text.
 *
 * @param modifier Modifier for the container
 * @param label Optional text to display below the indicator
 * @param size Size of the loading indicator
 * @param color Color of the indicator
 */
@Composable
fun ExpressiveLoadingIndicatorWithLabel(
    modifier: Modifier = Modifier,
    label: String? = null,
    size: Dp = 48.dp,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        ExpressiveLoadingIndicator(size = size, color = color)

        if (label != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Animated loading overlay that fades in/out.
 *
 * Use this to overlay content during loading states.
 *
 * @param isLoading Whether the loading state is active
 * @param modifier Modifier for the overlay
 * @param content Content to display when loading
 */
@Composable
fun AnimatedLoadingOverlay(
    isLoading: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit = { ExpressiveLoadingIndicator() },
) {
    val alpha by
        animateFloatAsState(
            targetValue = if (isLoading) 1f else 0f,
            animationSpec = AppMotion.Effect.Alpha,
            label = "loadingOverlayAlpha",
        )

    if (isLoading || alpha > 0f) {
        Box(
            modifier =
                modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
                    .alpha(alpha)
                    .clickable(
                        enabled = isLoading,
                        onClick = {},
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}

/**
 * Compact loading indicator for inline use.
 *
 * @param modifier Modifier for the indicator
 * @param size Size of the indicator (default 24dp)
 * @param strokeWidth Width of the progress stroke
 * @param color Color of the indicator
 */
@Composable
fun CompactLoadingIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    @Suppress("UNUSED_PARAMETER") strokeWidth: Dp = 2.dp,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    ContainedLoadingIndicator(
        modifier = modifier.size(size),
        indicatorColor = color,
        containerColor = color.copy(alpha = 0.12f),
    )
}

// ═══════════════════════════════════════════════════════════
// Previews
// ═══════════════════════════════════════════════════════════

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun ExpressiveLoadingIndicatorPreview() {
    DeviceMaskerTheme { Box(modifier = Modifier.padding(32.dp)) { ExpressiveLoadingIndicator() } }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun ExpressiveLoadingWithLabelPreview() {
    DeviceMaskerTheme {
        Box(modifier = Modifier.padding(32.dp)) {
            ExpressiveLoadingIndicatorWithLabel(label = "Loading groups...")
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun CompactLoadingIndicatorPreview() {
    DeviceMaskerTheme { Box(modifier = Modifier.padding(16.dp)) { CompactLoadingIndicator() } }
}

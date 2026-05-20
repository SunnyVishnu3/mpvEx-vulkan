package com.astrixforge.devicemasker.ui.components.expressive

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.astrixforge.devicemasker.ui.theme.AppMotion
import com.astrixforge.devicemasker.ui.theme.DeviceMaskerTheme

/** ElevatedCard with Material 3 Expressive enhancements. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExpressiveCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    isSelected: Boolean = false,
    enabled: Boolean = true,
    shape: Shape = MaterialTheme.shapes.large,
    containerColor: Color? = null,
    selectionColor: Color? = null,
    colors: CardColors? = null,
    elevation: androidx.compose.material3.CardElevation = CardDefaults.elevatedCardElevation(),
    content: @Composable ColumnScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Spatial spring for scale (CAN overshoot)
    val scale by
        animateFloatAsState(
            targetValue =
                when {
                    !enabled -> 1f
                    isPressed -> 0.98f
                    isSelected -> 1.02f
                    else -> 1f
                },
            animationSpec = AppMotion.Spatial.Expressive,
            label = "expressiveCardScale",
        )

    // Effect spring for color (NO overshoot)
    val baseColor = containerColor ?: MaterialTheme.colorScheme.surfaceContainerHigh
    val targetColor =
        if (isSelected) {
            selectionColor ?: MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        } else {
            baseColor
        }

    val animatedContainerColor by
        animateColorAsState(
            targetValue = targetColor,
            animationSpec = AppMotion.Effect.Color,
            label = "expressiveCardColor",
        )

    val finalColors =
        colors ?: CardDefaults.elevatedCardColors(containerColor = animatedContainerColor)

    ElevatedCard(
        modifier =
            modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled,
                    onClick = onClick,
                    onLongClick = onLongClick,
                ),
        colors = finalColors,
        shape = shape,
        elevation = elevation,
    ) {
        Column(content = content)
    }
}

/** OutlinedCard with Material 3 Expressive enhancements. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExpressiveOutlinedCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    isSelected: Boolean = false,
    enabled: Boolean = true,
    shape: Shape = MaterialTheme.shapes.large,
    selectionColor: Color? = null,
    colors: CardColors? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by
        animateFloatAsState(
            targetValue =
                when {
                    !enabled -> 1f
                    isPressed -> 0.98f
                    isSelected -> 1.02f
                    else -> 1f
                },
            animationSpec = AppMotion.Spatial.Expressive,
            label = "expressiveCardScale",
        )

    val targetColor =
        if (isSelected) {
            selectionColor ?: MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f)
        } else {
            Color.Transparent
        }

    val animatedContainerColor by
        animateColorAsState(
            targetValue = targetColor,
            animationSpec = AppMotion.Effect.Color,
            label = "expressiveCardColor",
        )

    OutlinedCard(
        modifier =
            modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled,
                    onClick = onClick,
                    onLongClick = onLongClick,
                ),
        shape = shape,
        colors = colors ?: CardDefaults.outlinedCardColors(containerColor = animatedContainerColor),
    ) {
        Column(content = content)
    }
}

// ═══════════════════════════════════════════════════════════
// Previews
// ═══════════════════════════════════════════════════════════

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun ExpressiveCardPreview() {
    DeviceMaskerTheme {
        ExpressiveCard(onClick = {}, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                text = "Expressive Card",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun ExpressiveCardSelectedPreview() {
    DeviceMaskerTheme {
        ExpressiveCard(
            onClick = {},
            isSelected = true,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) {
            Text(
                text = "Selected Card",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

package com.astrixforge.devicemasker.ui.components.expressive

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.astrixforge.devicemasker.ui.theme.AppMotion

/**
 * Material 3 Expressive Shape Morphing Utilities.
 *
 * These utilities enable smooth shape transitions with physics-based spring animations, following
 * Material 3 Expressive design principles.
 *
 * Use cases:
 * - Card corner radius changes on selection
 * - Button shape morphing on press
 * - Icon container shape transitions
 */

// ═══════════════════════════════════════════════════════════
// Animated Corner Radius
// ═══════════════════════════════════════════════════════════

/**
 * Creates an animated RoundedCornerShape that morphs between corner radii.
 *
 * Uses expressive spring physics for bouncy, natural-feeling transitions.
 *
 * @param targetRadius The target corner radius to animate to
 * @param label Animation label for debugging
 * @return An animated Shape that transitions smoothly
 *
 * Usage:
 * ```kotlin
 * val shape = animatedRoundedCornerShape(
 *     targetRadius = if (isSelected) 24.dp else 12.dp
 * )
 * Card(shape = shape) { ... }
 * ```
 */
@Composable
fun animatedRoundedCornerShape(targetRadius: Dp, label: String = "cornerRadiusMorph"): Shape {
    val animatedRadius by
        animateFloatAsState(
            targetValue = targetRadius.value,
            animationSpec = AppMotion.Spatial.Expressive,
            label = label,
        )
    return RoundedCornerShape(animatedRadius.dp)
}

// ═══════════════════════════════════════════════════════════
// Asymmetric Corner Shapes
// ═══════════════════════════════════════════════════════════

// ═══════════════════════════════════════════════════════════
// Predefined Shape Presets
// ═══════════════════════════════════════════════════════════

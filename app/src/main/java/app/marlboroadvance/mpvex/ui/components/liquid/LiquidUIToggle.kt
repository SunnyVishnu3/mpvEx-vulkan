package app.marlboroadvance.mpvex.ui.components.liquid

import android.os.Build
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import app.marlboroadvance.mpvex.ui.theme.LiquidUIEffects
import app.marlboroadvance.mpvex.preferences.LiquidUIPreferences

/**
 * Liquid Glass Toggle Component
 * 
 * A beautiful animated toggle with liquid glass effect.
 * When liquid UI is disabled in preferences, falls back to standard Material Switch.
 * 
 * Features:
 * - Smooth animation between states
 * - Liquid glass effect with customizable blur and lens
 * - iOS-like appearance
 * - Full accessibility support
 * 
 * @param checked Whether the toggle is in the ON position
 * @param onCheckedChange Callback when toggle state changes
 * @param modifier Modifier for the toggle
 * @param enabled Whether the toggle can be interacted with
 * @param checkedColor Color when toggle is ON
 * @param uncheckedColor Color when toggle is OFF
 * @param thumbColor Color of the toggle thumb (circle)
 * @param applyLiquidEffect Whether to apply liquid glass effect (set false for fallback)
 */
@Composable
fun LiquidToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    checkedColor: Color = Color(0xFF4CAF50),  // Green when ON
    uncheckedColor: Color = Color(0xFFBDBDBD),  // Gray when OFF
    thumbColor: Color = Color.White,
    applyLiquidEffect: Boolean = true
) {
    // Fallback to standard Material Switch if liquid effect not enabled
    if (!applyLiquidEffect) {
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = modifier,
            enabled = enabled
        )
        return
    }

    val backgroundColor = if (checked) checkedColor else uncheckedColor
    val thumbPadding = if (checked) 24.dp else 2.dp

    // Create backdrop for liquid glass effect
    val backdrop = rememberLayerBackdrop {
        drawRect(backgroundColor)
        drawContent()
    }

    // Animate thumb position
    val animatedThumbPadding = animateDpAsState(
        targetValue = thumbPadding,
        label = "toggle_thumb_position"
    )

    Box(
        modifier = modifier
            .size(width = 52.dp, height = 32.dp)
            .background(
                color = backgroundColor,
                shape = RoundedCornerShape(16.dp)
            )
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedCornerShape(16.dp) },
                effects = LiquidUIEffects.glassCardEffects(enableBlur = true),
                onDrawSurface = { drawRect(LiquidUIEffects.glassSurfaceColor) }
            )
            .clickable(
                enabled = enabled,
                onClick = { onCheckedChange(!checked) },
                indication = null,  // Remove ripple effect
                interactionSource = remember { MutableInteractionSource() }
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        // Toggle thumb (the circle that moves)
        Box(
            modifier = Modifier
                .size(28.dp)
                .padding(start = animatedThumbPadding.value)
                .background(
                    color = thumbColor,
                    shape = RoundedCornerShape(14.dp)
                )
        )
    }
}

/**
 * Liquid Glass Switch Component (with label)
 * 
 * A toggle switch with optional label text.
 * 
 * @param checked Whether the switch is ON
 * @param onCheckedChange Callback when switch state changes
 * @param modifier Modifier for the entire row
 * @param enabled Whether the switch can be interacted with
 * @param label Optional label text to display
 * @param applyLiquidEffect Whether to apply liquid glass effect
 */
@Composable
fun LiquidSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: String? = null,
    applyLiquidEffect: Boolean = true
) {
    if (label == null) {
        // Just the toggle without label
        LiquidToggle(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = modifier,
            enabled = enabled,
            applyLiquidEffect = applyLiquidEffect
        )
        return
    }

    // Toggle with label
    Row(
        modifier = modifier.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f)
        )

        Spacer(modifier = Modifier.size(8.dp))

        LiquidToggle(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            applyLiquidEffect = applyLiquidEffect
        )
    }
}

/**
 * Adaptive Toggle - Automatically uses liquid or standard based on settings
 * 
 * This is the recommended component to use throughout the app.
 * It automatically adapts to the liquid UI preference setting.
 * 
 * @param checked Whether the toggle is ON
 * @param onCheckedChange Callback when state changes
 * @param modifier Modifier
 * @param preferences LiquidUIPreferences to check liquid UI setting
 * @param enabled Whether the toggle is interactive
 * @param label Optional label text
 */
@Composable
fun AdaptiveToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    preferences: LiquidUIPreferences,
    enabled: Boolean = true,
    label: String? = null
) {
    // Collect liquid UI enabled state
    val isLiquidUIEnabled = preferences.liquidUIEnabledFlow
        .collectAsState(false).value

    if (label != null) {
        LiquidSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = modifier,
            enabled = enabled,
            label = label,
            applyLiquidEffect = isLiquidUIEnabled
        )
    } else {
        LiquidToggle(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = modifier,
            enabled = enabled,
            applyLiquidEffect = isLiquidUIEnabled
        )
    }
}

/**
 * Simple Liquid Toggle (no adaptation)
 * 
 * Use this when you're already checking liquid UI settings elsewhere
 * and want to directly control whether liquid effect is shown.
 * 
 * @param checked Toggle state
 * @param onCheckedChange State change callback
 * @param modifier Modifier
 * @param enabled Interactive state
 * @param isLiquidUI Whether to show liquid glass version (not adaptive)
 * @param label Optional label
 */
@Composable
fun SimpleToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLiquidUI: Boolean = true,
    label: String? = null
) {
    if (label != null) {
        LiquidSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = modifier,
            enabled = enabled,
            label = label,
            applyLiquidEffect = isLiquidUI
        )
    } else {
        LiquidToggle(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = modifier,
            enabled = enabled,
            applyLiquidEffect = isLiquidUI
        )
    }
}

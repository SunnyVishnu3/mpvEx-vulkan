package app.marlboroadvance.mpvex.ui.components.liquid

import android.os.Build
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import app.marlboroadvance.mpvex.ui.theme.LiquidUIEffects
import app.marlboroadvance.mpvex.preferences.LiquidUIPreferences
import kotlin.math.roundToInt

@Composable
fun LiquidToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    checkedColor: Color = Color(0xFF4CAF50),  
    uncheckedColor: Color = Color(0xFFBDBDBD),  
    thumbColor: Color = Color.White,
    applyLiquidEffect: Boolean = true
) {
    if (!applyLiquidEffect) {
        Switch(checked = checked, onCheckedChange = onCheckedChange, modifier = modifier, enabled = enabled)
        return
    }

    // Physics Dimensions
    val trackWidthDp = 52.dp
    val baseThumbSizeDp = 28.dp
    val paddingDp = 2.dp
    
    val trackWidthPx = with(LocalDensity.current) { trackWidthDp.toPx() }
    val thumbSizePx = with(LocalDensity.current) { baseThumbSizeDp.toPx() }
    val paddingPx = with(LocalDensity.current) { paddingDp.toPx() }
    val maxDragPx = trackWidthPx - thumbSizePx - (paddingPx * 2)

    var dragOffset by remember { mutableFloatStateOf(if (checked) maxDragPx else 0f) }
    var isDragging by remember { mutableStateOf(false) }

    // Sync external state changes gracefully
    LaunchedEffect(checked) {
        if (!isDragging) {
            dragOffset = if (checked) maxDragPx else 0f
        }
    }

    // Drag physics logic
    val draggableState = rememberDraggableState { delta ->
        dragOffset = (dragOffset + delta).coerceIn(0f, maxDragPx)
    }

    // Smooth springing animation for the drag position
    val animatedOffset by animateFloatAsState(
        targetValue = if (isDragging) dragOffset else (if (checked) maxDragPx else 0f),
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 400f),
        label = "thumb_offset"
    )

    // Interaction states for stretch effect
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // iOS style thumb stretching: wider when pressed or dragged
    val thumbWidth by animateDpAsState(
        targetValue = if (isPressed || isDragging) 34.dp else baseThumbSizeDp,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 800f),
        label = "thumb_width"
    )

    // Smoothly blend the glass tint color based on exact drag position
    val progress = (animatedOffset / maxDragPx).coerceIn(0f, 1f)
    val dynamicTrackColor = lerp(
        start = uncheckedColor.copy(alpha = 0.2f),
        stop = checkedColor.copy(alpha = 0.6f),
        fraction = progress
    )

    val backdrop = rememberLayerBackdrop { drawContent() }

    Box(
        modifier = modifier
            .size(width = trackWidthDp, height = 32.dp)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedCornerShape(16.dp) },
                effects = LiquidUIEffects.glassCardEffects(enableBlur = true),
                onDrawSurface = { drawRect(dynamicTrackColor) }
            )
            .clickable(
                enabled = enabled,
                onClick = { onCheckedChange(!checked) },
                indication = null,  
                interactionSource = interactionSource
            )
            .draggable(
                state = draggableState,
                orientation = Orientation.Horizontal,
                enabled = enabled,
                onDragStarted = { isDragging = true },
                onDragStopped = { 
                    isDragging = false
                    val targetChecked = dragOffset > (maxDragPx / 2)
                    onCheckedChange(targetChecked)
                }
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .padding(start = paddingDp)
                .offset { IntOffset(animatedOffset.roundToInt(), 0) }
                .size(width = thumbWidth, height = baseThumbSizeDp)
                .background(color = thumbColor, shape = RoundedCornerShape(14.dp))
        )
    }
}

@Composable
fun AdaptiveToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    preferences: LiquidUIPreferences,
    enabled: Boolean = true
) {
    val isLiquidUIEnabled = preferences.liquidUIEnabledFlow.collectAsState(false).value
    val toggleColorLong = preferences.liquidToggleColorFlow.collectAsState(0xFF4CAF50).value
    val customColor = Color(toggleColorLong)

    LiquidToggle(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        checkedColor = customColor,
        applyLiquidEffect = isLiquidUIEnabled
    )
}

// THE DROP-IN REPLACEMENT WRAPPER
// This exactly matches the library's signature so it can replace standard toggles everywhere!
@Composable
fun LiquidSwitchPreference(
    value: Boolean,
    onValueChange: (Boolean) -> Unit,
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    summary: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    preferences: LiquidUIPreferences? = null
) {
    val context = LocalContext.current
    val activePrefs = preferences ?: remember { LiquidUIPreferences(context) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onValueChange(!value) }
            .padding(horizontal = 16.dp, vertical = 16.dp)
            .alpha(if (enabled) 1f else 0.5f),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Box(modifier = Modifier.padding(end = 16.dp)) { icon() }
        }
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            title()
            if (summary != null) summary()
        }
        AdaptiveToggle(
            checked = value,
            onCheckedChange = onValueChange,
            preferences = activePrefs,
            enabled = enabled
        )
    }
}

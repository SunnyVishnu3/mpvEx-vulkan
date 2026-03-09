package app.marlboroadvance.mpvex.ui.components.liquid

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
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

    // 1.0.6 Exact Dimensions
    val trackWidthDp = 64.dp
    val trackHeightDp = 28.dp
    val thumbBaseWidthDp = 40.dp
    val thumbBaseHeightDp = 24.dp
    val paddingDp = 2.dp
    
    val density = LocalDensity.current
    val dragWidthPx = with(density) { (trackWidthDp - thumbBaseWidthDp - (paddingDp * 2)).toPx() }

    var dragFraction by remember { mutableFloatStateOf(if (checked) 1f else 0f) }
    var isDragging by remember { mutableStateOf(false) }

    LaunchedEffect(checked) {
        if (!isDragging) dragFraction = if (checked) 1f else 0f
    }

    val draggableState = rememberDraggableState { delta ->
        dragFraction = (dragFraction + (delta / dragWidthPx)).coerceIn(0f, 1f)
    }

    // The Sliding Physics
    val animatedFraction by animateFloatAsState(
        targetValue = if (isDragging) dragFraction else (if (checked) 1f else 0f),
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
        label = "thumb_fraction"
    )

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // The Squishy Press Physics
    val pressProgress by animateFloatAsState(
        targetValue = if (isPressed || isDragging) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 800f),
        label = "press_progress"
    )

    // Thumb physically SWELLS/EXPANDS when you press it (like a magnifying glass)
    val thumbWidth by animateDpAsState(
        targetValue = androidx.compose.ui.unit.lerp(thumbBaseWidthDp, 52.dp, pressProgress),
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 800f),
        label = "thumb_width"
    )
    
    val thumbHeight by animateDpAsState(
        targetValue = androidx.compose.ui.unit.lerp(thumbBaseHeightDp, 32.dp, pressProgress),
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 800f),
        label = "thumb_height"
    )

    val dynamicTint = lerp(uncheckedColor, checkedColor, animatedFraction)
    val trackBackdrop = rememberLayerBackdrop { drawContent() }

    Box(
        modifier = modifier
            .size(width = trackWidthDp, height = trackHeightDp)
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
                onDragStarted = { 
                    isDragging = true 
                    dragFraction = if (checked) 1f else 0f
                },
                onDragStopped = { 
                    isDragging = false
                    val targetChecked = dragFraction > 0.5f
                    onCheckedChange(targetChecked)
                }
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        // THE TRACK (Feeds colors to the glass)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(dynamicTint)
                .layerBackdrop(trackBackdrop)
        )

        // THE LIQUID GLASS THUMB
        val paddingPx = with(density) { paddingDp.toPx() }
        val thumbOffsetPx = paddingPx + (dragWidthPx * animatedFraction)

        Box(
            modifier = Modifier
                .offset { IntOffset(thumbOffsetPx.roundToInt(), 0) }
                .size(width = thumbWidth, height = thumbHeight)
                .drawBackdrop(
                    backdrop = trackBackdrop,
                    shape = { CircleShape },
                    effects = {
                        val currentBlur = 8f.dp.toPx() * (1f - pressProgress)
                        val currentHeight = 5f.dp.toPx() + (5f.dp.toPx() * pressProgress)
                        val currentAmount = 10f.dp.toPx() + (4f.dp.toPx() * pressProgress)
                        
                        if (currentBlur > 0f) {
                            blur(currentBlur)
                        }
                        
                        lens(
                            refractionHeight = currentHeight,
                            refractionAmount = currentAmount,
                            chromaticAberration = true
                        )
                    },
                    onDrawSurface = { 
                        // Melts into pure transparent glass when pressed!
                        drawRect(thumbColor.copy(alpha = 1f - pressProgress))
                    }
                )
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
    
    LiquidToggle(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        checkedColor = Color(toggleColorLong),
        applyLiquidEffect = isLiquidUIEnabled
    )
}

package app.marlboroadvance.mpvex.ui.components.liquid

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceAtMost
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.utils.InteractiveHighlight
import app.marlboroadvance.mpvex.preferences.LiquidTarget
import app.marlboroadvance.mpvex.preferences.LiquidUIPreferences
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh

// Broadcasts the glass camera to any button that wants it!
val LocalLiquidBackdrop = androidx.compose.runtime.staticCompositionLocalOf<com.kyant.backdrop.Backdrop?> { null }

// Shared LiquidUIPreferences CompositionLocal.
val LocalLiquidPreferences = androidx.compose.runtime.staticCompositionLocalOf<LiquidUIPreferences?> { null }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TransparentLiquidButton(
    modifier: Modifier = Modifier,
    backdrop: Backdrop?,
    shape: Shape = CircleShape,
    target: LiquidTarget = LiquidTarget.BUTTON,
    isInteractive: Boolean = true, 
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val animationScope = rememberCoroutineScope()
    val interactionSource = remember { MutableInteractionSource() }
    
    val interactiveHighlight = remember(animationScope) {
        InteractiveHighlight(animationScope = animationScope)
    }

    val physicsGraphicsModifier = if (isInteractive) {
        Modifier.graphicsLayer {
            val width = size.width
            val height = size.height
            val progress = interactiveHighlight.pressProgress
            val scale = lerp(1f, 1f + 4f.dp.toPx() / size.height, progress)
            val maxOffset = size.minDimension
            val initialDerivative = 0.05f
            val offset = interactiveHighlight.offset
            
            translationX = maxOffset * tanh(initialDerivative * offset.x / maxOffset)
            translationY = maxOffset * tanh(initialDerivative * offset.y / maxOffset)
            val maxDragScale = 4f.dp.toPx() / size.height
            val offsetAngle = atan2(offset.y, offset.x)
            scaleX = scale + maxDragScale * abs(cos(offsetAngle) * offset.x / size.maxDimension) * (width / height).fastCoerceAtMost(1f)
            scaleY = scale + maxDragScale * abs(sin(offsetAngle) * offset.y / size.maxDimension) * (height / width).fastCoerceAtMost(1f)
        }
    } else Modifier

    val physicsGestureModifier = if (isInteractive) {
        Modifier
            .then(interactiveHighlight.modifier)
            .then(interactiveHighlight.gestureModifier)
    } else Modifier

    if (backdrop == null) {
        androidx.compose.foundation.layout.Box(
            modifier = modifier
                .then(physicsGraphicsModifier)
                .clip(shape)
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = if (isInteractive) null else ripple(),
                    onClick = onClick, 
                    onLongClick = onLongClick
                )
                .then(physicsGestureModifier),
            contentAlignment = Alignment.Center
        ) { content() }
        return
    }

    LiquidGlassSurface(
        backdrop = backdrop,
        shape = shape,
        target = target,
        modifier = modifier
            .then(physicsGraphicsModifier)
            .clip(shape)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = if (isInteractive) null else ripple(),
                onClick = onClick,
                onLongClick = onLongClick
            )
            .then(physicsGestureModifier)
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.fillMaxSize(), 
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}

@Composable
fun LiquidGlassCard(
    modifier: Modifier = Modifier,
    backdrop: Backdrop?,
    shape: Shape = RoundedCornerShape(12.dp),
    content: @Composable () -> Unit
) {
    if (backdrop == null) {
        androidx.compose.foundation.layout.Box(modifier = modifier) { content() }
        return
    }

    LiquidGlassSurface(
        backdrop = backdrop,
        shape = shape,
        target = LiquidTarget.DIALOG,
        modifier = modifier
    ) {
        content()
    }
}

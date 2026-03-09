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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TransparentLiquidButton(
    modifier: Modifier = Modifier,
    backdrop: Backdrop?,
    shape: Shape = CircleShape,
    onClick: () -> Unit,
    target: LiquidTarget = LiquidTarget.BUTTON,
    onLongClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    if (backdrop == null) {
        androidx.compose.foundation.layout.Box(
            modifier = modifier
                .clip(shape)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
            contentAlignment = Alignment.Center
        ) { content() }
        return
    }

    val interactionSource = remember { MutableInteractionSource() }

    // Calls the engine directly—it automatically reads your live DataStore flows!
    LiquidGlassSurface(
        backdrop = backdrop,
        shape = shape,
        modifier = modifier
            .clip(shape)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = ripple(),
                onClick = onClick,
                onLongClick = onLongClick
            )
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
        modifier = modifier
    ) {
        content()
    }
}

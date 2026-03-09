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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import app.marlboroadvance.mpvex.preferences.LiquidTarget

/**
 * UPGRADED: Reusable Transparent Liquid Button
 * Now wired directly to the 'Buttons' Tab in your Settings!
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TransparentLiquidButton(
    modifier: Modifier = Modifier,
    backdrop: Backdrop?,
    shape: Shape = CircleShape,
    target: LiquidTarget = LiquidTarget.BUTTON, // Listens to the Buttons Settings!
    onClick: () -> Unit,
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

    LiquidGlassSurface(
        backdrop = backdrop,
        target = target, // Connects to the lens engine!
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

/**
 * UPGRADED: Reusable Liquid Glass Card 
 * Now wired directly to the 'Dialogs' Tab in your Settings!
 */
@Composable
fun LiquidGlassCard(
    modifier: Modifier = Modifier,
    backdrop: Backdrop?,
    onClick: () -> Unit = {},
    content: @Composable () -> Unit
) {
    if (backdrop == null) {
        androidx.compose.foundation.layout.Box(modifier = modifier) { 
            content() 
        }
        return
    }

    LiquidGlassSurface(
        backdrop = backdrop,
        target = LiquidTarget.DIALOG, // Connects to the Dialogs Settings!
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        content()
    }
}

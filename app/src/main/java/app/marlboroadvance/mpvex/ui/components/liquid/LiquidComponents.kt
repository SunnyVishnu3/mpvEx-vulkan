package app.marlboroadvance.mpvex.ui.components.liquid

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import app.marlboroadvance.mpvex.ui.theme.LiquidUIEffects

/**
 * Reusable Transparent Liquid Button
 * Perfect for Play/Pause/Seek controls over the video player
 */
@Composable
fun TransparentLiquidButton(
    modifier: Modifier = Modifier,
    backdrop: Backdrop?,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    // If liquid UI is disabled or backdrop is missing, show standard button
    if (backdrop == null) {
        IconButton(onClick = onClick, modifier = modifier) { content() }
        return
    }

    Surface(
        modifier = modifier
            .drawBackdrop(
                backdrop = backdrop,
                shape = { CircleShape },
                // Calls the lambda from your fixed LiquidUIEffects.kt
                effects = LiquidUIEffects.glassButtonEffects(), 
                // Using an ultra-light overlay to ensure it remains a truly transparent liquid button
                onDrawSurface = { drawRect(Color.White.copy(alpha = 0.1f)) }
            ),
        shape = CircleShape,
        // Critical: The Surface itself must be transparent so the backdrop shows through
        color = Color.Transparent 
    ) {
        IconButton(onClick = onClick) { 
            content() 
        }
    }
}

/**
 * Reusable Liquid Glass Card 
 * Perfect for the Browser Screen and video list items
 */
@Composable
fun LiquidGlassCard(
    modifier: Modifier = Modifier,
    backdrop: Backdrop?,
    onClick: () -> Unit = {},
    content: @Composable () -> Unit
) {
    if (backdrop == null) {
        Card(modifier = modifier.clickable { onClick() }) { content() }
        return
    }

    Card(
        modifier = modifier
            .clickable { onClick() }
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedCornerShape(12.dp) },
                effects = LiquidUIEffects.glassCardEffects(),
                onDrawSurface = { drawRect(LiquidUIEffects.glassSurfaceColor) }
            ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            content()
        }
    }
}

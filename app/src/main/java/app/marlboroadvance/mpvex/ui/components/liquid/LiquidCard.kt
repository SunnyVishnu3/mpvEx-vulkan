package app.marlboroadvance.mpvex.ui.components.liquid

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import app.marlboroadvance.mpvex.preferences.LiquidTarget

// --- VERSION 1: STANDARD CARD ---
@Composable
fun LiquidCard(
    modifier: Modifier = Modifier,
    shape: Shape = CardDefaults.shape,
    colors: CardColors = CardDefaults.cardColors(),
    elevation: CardElevation = CardDefaults.cardElevation(),
    border: BorderStroke? = null,
    target: LiquidTarget = LiquidTarget.CARD,
    content: @Composable ColumnScope.() -> Unit
) {
    val backdrop = LocalLiquidBackdrop.current

    if (backdrop != null) {
        LiquidGlassSurface(
            backdrop = backdrop,
            target = target,
            shape = shape,
            modifier = modifier
        ) {
            Column(content = content)
        }
    } else {
        Card(
            modifier = modifier,
            shape = shape,
            colors = colors,
            elevation = elevation,
            border = border,
            content = content
        )
    }
}

// --- VERSION 2: CLICKABLE CARD (Fixes the onClick error!) ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiquidCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = CardDefaults.shape,
    colors: CardColors = CardDefaults.cardColors(),
    elevation: CardElevation = CardDefaults.cardElevation(),
    border: BorderStroke? = null,
    target: LiquidTarget = LiquidTarget.CARD,
    content: @Composable ColumnScope.() -> Unit
) {
    val backdrop = LocalLiquidBackdrop.current

    if (backdrop != null) {
        // LIQUID UI MODE WITH CLICK ACTION
        LiquidGlassSurface(
            backdrop = backdrop,
            target = target,
            shape = shape,
            modifier = modifier.then(
                if (enabled) Modifier.clickable(onClick = onClick) else Modifier
            )
        ) {
            Column(content = content)
        }
    } else {
        // STANDARD CLICKABLE MATERIAL 3 CARD
        Card(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            shape = shape,
            colors = colors,
            elevation = elevation,
            border = border,
            content = content
        )
    }
}

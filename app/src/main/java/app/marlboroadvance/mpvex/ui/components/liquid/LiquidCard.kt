package app.marlboroadvance.mpvex.ui.components.liquid

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import app.marlboroadvance.mpvex.preferences.LiquidTarget

@Composable
fun LiquidCard(
    modifier: Modifier = Modifier,
    shape: Shape = CardDefaults.shape,
    colors: CardColors = CardDefaults.cardColors(),
    elevation: CardElevation = CardDefaults.cardElevation(),
    border: BorderStroke? = null,
    target: LiquidTarget = LiquidTarget.DIALOG,
    content: @Composable ColumnScope.() -> Unit
) {
    val backdrop = LocalLiquidBackdrop.current

    if (backdrop != null) {
        // LIQUID UI MODE: Swaps the standard Card for a beautiful glass surface!
        LiquidGlassSurface(
            backdrop = backdrop,
            target = target,
            shape = shape,
            modifier = modifier
        ) {
            // A standard Material Card naturally acts like a Column, so we wrap the content here 
            // to ensure your app's layouts don't break when switching to Glass.
            Column(content = content)
        }
    } else {
        // STANDARD MODE: Uses the exact original Material 3 Card
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

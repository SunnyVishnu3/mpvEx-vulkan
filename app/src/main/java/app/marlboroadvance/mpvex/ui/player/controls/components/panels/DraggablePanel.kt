@file:Suppress("ktlint:standard:no-wildcard-imports")

package app.marlboroadvance.mpvex.ui.player.controls.components.panels

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import app.marlboroadvance.mpvex.ui.player.controls.panelCardsColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

// --- NEW LIQUID IMPORTS ---
import app.marlboroadvance.mpvex.ui.components.liquid.LocalLiquidBackdrop
import app.marlboroadvance.mpvex.ui.components.liquid.LiquidGlassSurface
import app.marlboroadvance.mpvex.preferences.LiquidTarget

@Composable
fun DraggablePanel(
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    var offsetX by remember { mutableFloatStateOf(0f) }
    var panelWidth by remember { mutableIntStateOf(0) }

    val configuration = LocalConfiguration.current
    val isPortrait = configuration.orientation == Configuration.ORIENTATION_PORTRAIT
    
    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = if (isPortrait) Alignment.Center else Alignment.CenterEnd
    ) {
        val density = LocalDensity.current
        val parentWidthPx = with(density) { maxWidth.toPx() }
        
        val freeSpace = (parentWidthPx - panelWidth).coerceAtLeast(0f)
        val maxOffset = 0f
        val minOffset = -freeSpace
        val panelMaxHeight = if (isPortrait) maxHeight * 0.5f else maxHeight

        val colors = panelCardsColors()
        val backdrop = LocalLiquidBackdrop.current
        
        val panelModifier = Modifier
            .offset { IntOffset(offsetX.roundToInt(), 0) }
            .onSizeChanged { panelWidth = it.width }
            .widthIn(max = 380.dp)
            .heightIn(max = panelMaxHeight)

        val panelContent: @Composable () -> Unit = {
            Column {
                 Box(
                     modifier = Modifier
                         .fillMaxWidth()
                         .height(18.dp) 
                         .pointerInput(maxOffset, minOffset) {
                             detectDragGestures { change, dragAmount ->
                                 change.consume()
                                 val newOffset = offsetX + dragAmount.x
                                 offsetX = newOffset.coerceIn(minOffset, maxOffset)
                             }
                         },
                     contentAlignment = Alignment.Center
                 ) {
                     Box(
                         modifier = Modifier
                             .width(32.dp)
                             .height(4.dp)
                             .background(
                                 color = if (backdrop != null) Color.White.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                 shape = RoundedCornerShape(2.dp)
                             )
                     )
                 }
                
                if (header != null) {
                    header()
                }
                
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    content()
                }
            }
        }

        if (backdrop != null) {
            LiquidGlassSurface(
                backdrop = backdrop,
                target = LiquidTarget.DIALOG,
                shape = MaterialTheme.shapes.extraLarge,
                modifier = panelModifier
            ) {
                panelContent()
            }
        } else {
            Surface(
                modifier = panelModifier,
                shape = MaterialTheme.shapes.extraLarge,
                color = colors.containerColor,
                contentColor = colors.contentColor,
                tonalElevation = 0.dp,
            ) {
                panelContent()
            }
        }
    }
}

package app.marlboroadvance.mpvex.ui.components.liquid

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
fun LiquidTogglePreview() {
    var checked by remember { mutableStateOf(false) }
    val backdrop = rememberLayerBackdrop()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF64B5F6), Color(0xFF1976D2))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // Mock background content to be blurred/refracted
        Column(
            modifier = Modifier
                .layerBackdrop(backdrop)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Liquid Glass Toggle", color = Color.White, fontSize = 24.sp)
            Spacer(Modifier.height(8.dp))
            Text("Try interacting with it!", color = Color.White.copy(alpha = 0.7f))
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            var sliderValue by remember { mutableStateOf(0.5f) }

            LiquidToggle(
                selected = { checked },
                onSelect = { checked = it },
                backdrop = backdrop
            )
            
            Spacer(Modifier.height(24.dp))
            
            LiquidSlider(
                value = { sliderValue },
                onValueChange = { sliderValue = it },
                valueRange = 0f..1f,
                visibilityThreshold = 0.01f,
                backdrop = backdrop,
                modifier = Modifier.padding(horizontal = 32.dp)
            )

            Spacer(Modifier.height(24.dp))

            TransparentLiquidButton(
                modifier = Modifier.size(120.dp, 48.dp),
                backdrop = backdrop,
                onClick = { /* Handle click */ }
            ) {
                Text("Squishy Button", color = Color.White)
            }
        }
    }
}

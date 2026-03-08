package app.marlboroadvance.mpvex.ui.components.liquid

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

// --- KYANT BACKDROP 2.0.0-ALPHA03 IMPORTS ---
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
// --------------------------------------------

@Composable
fun LiquidGlassSurface(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp), 
    tintColor: Color = Color.White.copy(alpha = 0.15f), 
    content: @Composable () -> Unit
) {
    if (Build.VERSION.SDK_INT >= 33) { 
        // ANDROID 13+: Your Exact Custom Lens Parameters!
        Box(
            modifier = modifier
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { shape },
                    effects = {
                        vibrancy()
                        // Blur is 0, so we can either omit it or explicitly set to 0. 
                        // Omitting it is better for GPU performance!
                        
                        lens(
                            refractionHeight = 40f.dp.toPx(),
                            refractionAmount = 23f.dp.toPx(),
                            depthEffect = true,
                            chromaticAberration = false
                        )
                    },
                    onDrawSurface = {
                        drawRect(tintColor)
                    }
                )
        ) {
            content()
        }
    } else { 
        // ANDROID 12 AND BELOW: The "Flat Liquid Sheet" Fallback
        Box(
            modifier = modifier
                .background(tintColor.copy(alpha = 0.4f), shape)
                .border(1.dp, Color.White.copy(alpha = 0.2f), shape) 
                .clip(shape)
        ) {
            content()
        }
    }
}

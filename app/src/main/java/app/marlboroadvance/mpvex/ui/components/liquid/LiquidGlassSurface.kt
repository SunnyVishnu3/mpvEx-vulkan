package app.marlboroadvance.mpvex.ui.components.liquid

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

// --- KYANT BACKDROP 2.0.0-ALPHA03 IMPORTS ---
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
// --------------------------------------------

// --- PREFERENCES IMPORT ---
import app.marlboroadvance.mpvex.preferences.LiquidUIPreferences

@Composable
fun LiquidGlassSurface(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp), 
    defaultTintColor: Color = Color.White, 
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val liquidPrefs = remember { LiquidUIPreferences(context) }
    
    // --- LIVE SETTINGS STREAM ---
    // The glass will automatically morph whenever these values change!
    val blurRadius by liquidPrefs.liquidBlurRadiusFlow.collectAsState(initial = 0f)
    val refractionHeight by liquidPrefs.liquidRefractionHeightFlow.collectAsState(initial = 40f)
    val refractionAmount by liquidPrefs.liquidRefractionAmountFlow.collectAsState(initial = 23f)
    val chromaticAberration by liquidPrefs.liquidChromaticAberrationFlow.collectAsState(initial = false)
    val depthEffect by liquidPrefs.liquidDepthEffectFlow.collectAsState(initial = true)
    val vibrancyEnabled by liquidPrefs.liquidVibrancyEnabledFlow.collectAsState(initial = true)
    val tintAlpha by liquidPrefs.liquidTintAlphaFlow.collectAsState(initial = 0.15f)

    if (Build.VERSION.SDK_INT >= 33) { 
        // ANDROID 13+: Reactive Lens Engine
        Box(
            modifier = modifier
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { shape },
                    effects = {
                        if (vibrancyEnabled) {
                            vibrancy()
                        }
                        
                        // Only apply blur if it's greater than 0 to save GPU power
                        if (blurRadius > 0f) {
                            blur(blurRadius.dp.toPx())
                        }
                        
                        lens(
                            refractionHeight = refractionHeight.dp.toPx(),
                            refractionAmount = refractionAmount.dp.toPx(),
                            depthEffect = depthEffect,
                            chromaticAberration = chromaticAberration
                        )
                    },
                    onDrawSurface = {
                        // Apply the exact opacity you picked in the settings slider
                        drawRect(defaultTintColor.copy(alpha = tintAlpha))
                    }
                )
        ) {
            content()
        }
    } else { 
        // ANDROID 12 AND BELOW: The "Flat Liquid Sheet" Fallback
        Box(
            modifier = modifier
                .background(defaultTintColor.copy(alpha = tintAlpha), shape)
                .border(1.dp, Color.White.copy(alpha = 0.2f), shape) 
                .clip(shape)
        ) {
            content()
        }
    }
}

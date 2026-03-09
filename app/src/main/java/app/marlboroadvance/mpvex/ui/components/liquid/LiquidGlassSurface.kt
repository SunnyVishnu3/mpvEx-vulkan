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

import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy

import app.marlboroadvance.mpvex.preferences.LiquidUIPreferences
import app.marlboroadvance.mpvex.preferences.LiquidTarget

@Composable
fun LiquidGlassSurface(
    backdrop: Backdrop,
    target: LiquidTarget = LiquidTarget.NAV, // Targets the Nav bar by default
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp), 
    defaultTintColor: Color = Color.White, 
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val liquidPrefs = remember { LiquidUIPreferences(context) }
    
    // SAFE STATE COLLECTIONS: Only updates when the target actually changes
    val blurRadius by remember(target) { liquidPrefs.blurRadiusFlow(target) }.collectAsState(initial = 0f)
    val refractionHeight by remember(target) { liquidPrefs.refractionHeightFlow(target) }.collectAsState(initial = 40f)
    val refractionAmount by remember(target) { liquidPrefs.refractionAmountFlow(target) }.collectAsState(initial = 23f)
    val chromaticAberration by remember(target) { liquidPrefs.chromaticAberrationFlow(target) }.collectAsState(initial = false)
    val depthEffect by remember(target) { liquidPrefs.depthEffectFlow(target) }.collectAsState(initial = true)
    val vibrancyEnabled by remember(target) { liquidPrefs.vibrancyEnabledFlow(target) }.collectAsState(initial = true)
    val tintAlpha by remember(target) { liquidPrefs.tintAlphaFlow(target) }.collectAsState(initial = 0.15f)

    if (Build.VERSION.SDK_INT >= 33) { 
        Box(
            modifier = modifier
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { shape },
                    effects = {
                        if (vibrancyEnabled) vibrancy()
                        if (blurRadius > 0f) blur(blurRadius.dp.toPx())
                        
                        lens(
                            refractionHeight = refractionHeight.dp.toPx(),
                            refractionAmount = refractionAmount.dp.toPx(),
                            depthEffect = depthEffect,
                            chromaticAberration = chromaticAberration
                        )
                    },
                    onDrawSurface = { drawRect(defaultTintColor.copy(alpha = tintAlpha)) }
                )
        ) { content() }
    } else { 
        Box(
            modifier = modifier
                .background(defaultTintColor.copy(alpha = tintAlpha), shape)
                .border(1.dp, Color.White.copy(alpha = 0.2f), shape) 
                .clip(shape)
        ) { content() }
    }
}

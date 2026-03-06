package app.marlboroadvance.mpvex.ui.theme

import android.os.Build
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy

// 1. Define an enum for all effect presets
enum class LiquidEffectType {
    BOTTOM_BAR, BUTTON, DIALOG, CARD, PLAYER_OVERLAY
}

// 2. Configuration class to handle user preferences
data class LiquidEffectConfig(
    val type: LiquidEffectType,
    val enableBlur: Boolean = true,
    val enableLens: Boolean = true,
    val enableVibrancy: Boolean = true
)

object LiquidUIEffects {
    // Surface colors for readability over video
    val glassSurfaceColor = Color.White.copy(alpha = 0.5f)
    val glassDarkSurfaceColor = Color.Black.copy(alpha = 0.3f)
}

/**
 * 3. The Magic Wrapper! 
 * This uses type inference to apply the DSL safely, completely 
 * avoiding the unresolved 'BackdropEffectScope' error.
 */
fun Modifier.applyLiquidEffects(
    backdrop: Backdrop,
    shape: Shape,
    config: LiquidEffectConfig,
    surfaceColor: Color = LiquidUIEffects.glassSurfaceColor
): Modifier = composed {
    val density = LocalDensity.current
    this.drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = { // Kotlin automatically infers the hidden scope here!
            when (config.type) {
                LiquidEffectType.BOTTOM_BAR -> {
                    if (config.enableVibrancy) vibrancy()
                    if (config.enableBlur) blur(with(density) { 4f.dp.toPx() })
                    if (config.enableLens && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        lens(with(density) { 16f.dp.toPx() }, with(density) { 32f.dp.toPx() })
                    }
                }
                LiquidEffectType.BUTTON -> {
                    if (config.enableBlur) blur(with(density) { 3f.dp.toPx() })
                    if (config.enableLens && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        lens(with(density) { 12f.dp.toPx() }, with(density) { 24f.dp.toPx() })
                    }
                }
                LiquidEffectType.DIALOG -> {
                    vibrancy()
                    if (config.enableBlur) blur(with(density) { 8f.dp.toPx() })
                    if (config.enableLens && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        lens(with(density) { 20f.dp.toPx() }, with(density) { 40f.dp.toPx() })
                    }
                }
                LiquidEffectType.CARD -> {
                    if (config.enableBlur) blur(with(density) { 2.5f.dp.toPx() })
                }
                LiquidEffectType.PLAYER_OVERLAY -> {
                    if (config.enableVibrancy) vibrancy()
                    if (config.enableBlur) blur(with(density) { 5f.dp.toPx() })
                    if (config.enableLens && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        lens(with(density) { 18f.dp.toPx() }, with(density) { 36f.dp.toPx() })
                    }
                }
            }
        },
        onDrawSurface = { drawRect(surfaceColor) }
    )
}

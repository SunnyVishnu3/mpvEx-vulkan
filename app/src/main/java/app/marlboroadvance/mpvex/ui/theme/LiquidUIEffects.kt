package app.marlboroadvance.mpvex.ui.theme

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.effects.BackdropEffectScope
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy

object LiquidUIEffects {

    /**
     * Glass Bottom Bar Effect - Used for media player controls
     * Combines blur, lens, and vibrancy for a classic liquid glass look
     */
    @Composable
    fun glassBottomBarEffects(
        enableBlur: Boolean = true,
        enableLens: Boolean = true,
        enableVibrancy: Boolean = true
    ): BackdropEffectScope.() -> Unit {
        val density = LocalDensity.current
        return {
            if (enableVibrancy) vibrancy()
            if (enableBlur) blur(with(density) { 4f.dp.toPx() })
            if (enableLens && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                lens(with(density) { 16f.dp.toPx() }, with(density) { 32f.dp.toPx() })
            }
        }
    }

    /**
     * Interactive Glass Button Effect - For playback control buttons
     * Lighter blur with enhanced color
     */
    @Composable
    fun glassButtonEffects(
        enableBlur: Boolean = true,
        enableLens: Boolean = true
    ): BackdropEffectScope.() -> Unit {
        val density = LocalDensity.current
        return {
            if (enableBlur) blur(with(density) { 3f.dp.toPx() })
            if (enableLens && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                lens(with(density) { 12f.dp.toPx() }, with(density) { 24f.dp.toPx() })
            }
        }
    }

    /**
     * Glass Dialog Effect - For modal dialogs and bottom sheets
     * Stronger blur for better readability
     */
    @Composable
    fun glassDialogEffects(
        enableBlur: Boolean = true,
        enableLens: Boolean = true
    ): BackdropEffectScope.() -> Unit {
        val density = LocalDensity.current
        return {
            vibrancy()
            if (enableBlur) blur(with(density) { 8f.dp.toPx() })
            if (enableLens && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                lens(with(density) { 20f.dp.toPx() }, with(density) { 40f.dp.toPx() })
            }
        }
    }

    /**
     * Glass Card Effect - For video cards, playlist items
     * Subtle effect to avoid overwhelming content
     */
    @Composable
    fun glassCardEffects(
        enableBlur: Boolean = true
    ): BackdropEffectScope.() -> Unit {
        val density = LocalDensity.current
        return {
            if (enableBlur) blur(with(density) { 2.5f.dp.toPx() })
        }
    }

    /**
     * Video Player Overlay Effect - For player controls overlay
     * Balanced blur and lens for interactive controls
     */
    @Composable
    fun playerOverlayEffects(
        enableBlur: Boolean = true,
        enableLens: Boolean = true,
        enableVibrancy: Boolean = true
    ): BackdropEffectScope.() -> Unit {
        val density = LocalDensity.current
        return {
            if (enableVibrancy) vibrancy()
            if (enableBlur) blur(with(density) { 5f.dp.toPx() })
            if (enableLens && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                lens(with(density) { 18f.dp.toPx() }, with(density) { 36f.dp.toPx() })
            }
        }
    }

    // Surface color for readability (semi-transparent white)
    val glassSurfaceColor = Color.White.copy(alpha = 0.5f)
    val glassDarkSurfaceColor = Color.Black.copy(alpha = 0.3f)
}

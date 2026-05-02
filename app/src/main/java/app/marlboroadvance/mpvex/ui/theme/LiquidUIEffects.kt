package app.marlboroadvance.mpvex.ui.theme

import android.os.Build
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy

/**
 * CORRECTED: Effects configuration for liquid glass UI
 * 
 * IMPORTANT: These are lambdas to be used in the effects = { } block
 * within drawBackdrop modifier, NOT to be used with buildList()
 * 
 * Usage example:
 * 
 * .drawBackdrop(
 *     backdrop = backdrop,
 *     shape = { RoundedCornerShape(16.dp) },
 *     effects = LiquidUIEffects.glassBottomBarEffects(
 *         enableBlur = true,
 *         enableLens = true,
 *         enableVibrancy = true
 *     )
 * )
 */
object LiquidUIEffects {

    /**
     * Glass Bottom Bar Effect - Used for media player controls
     * Combines blur, lens, and vibrancy for a classic liquid glass look
     * 
     * Returns a lambda to be used in effects = { } block
     */
    fun glassBottomBarEffects(
        enableBlur: Boolean = true,
        enableLens: Boolean = true,
        enableVibrancy: Boolean = true
    ): BackdropEffectScope.() -> Unit = {
        if (enableVibrancy) vibrancy()
        if (enableBlur) blur(4f.dp.toPx())
        if (enableLens && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            lens(16f.dp.toPx(), 32f.dp.toPx())
        }
    }

    /**
     * Interactive Glass Button Effect - For playback control buttons
     * Lighter blur with enhanced color
     */
    fun glassButtonEffects(
        enableBlur: Boolean = true,
        enableLens: Boolean = true
    ): BackdropEffectScope.() -> Unit = {
        if (enableBlur) blur(3f.dp.toPx())
        if (enableLens && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            lens(12f.dp.toPx(), 24f.dp.toPx())
        }
    }

    /**
     * Glass Dialog Effect - For modal dialogs and bottom sheets
     * Stronger blur for better readability
     */
    fun glassDialogEffects(
        enableBlur: Boolean = true,
        enableLens: Boolean = true
    ): BackdropEffectScope.() -> Unit = {
        vibrancy()
        if (enableBlur) blur(8f.dp.toPx())
        if (enableLens && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            lens(20f.dp.toPx(), 40f.dp.toPx())
        }
    }

    /**
     * Glass Card Effect - For video cards, playlist items
     * Subtle effect to avoid overwhelming content
     */
    fun glassCardEffects(
        enableBlur: Boolean = true
    ): BackdropEffectScope.() -> Unit = {
        if (enableBlur) blur(2.5f.dp.toPx())
    }

    /**
     * Video Player Overlay Effect - For player controls overlay
     * Balanced blur and lens for interactive controls
     */
    fun playerOverlayEffects(
        enableBlur: Boolean = true,
        enableLens: Boolean = true,
        enableVibrancy: Boolean = true
    ): BackdropEffectScope.() -> Unit = {
        if (enableVibrancy) vibrancy()
        if (enableBlur) blur(5f.dp.toPx())
        if (enableLens && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            lens(18f.dp.toPx(), 36f.dp.toPx())
        }
    }

    // Surface color for readability (semi-transparent white)
    val glassSurfaceColor = Color.White.copy(alpha = 0.5f)
    val glassDarkSurfaceColor = Color.Black.copy(alpha = 0.3f)
}

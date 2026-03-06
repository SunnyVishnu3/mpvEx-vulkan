package app.marlboroadvance.mpvex.ui.theme

import android.os.Build
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy

object LiquidUIEffects {

    /**
     * Glass Bottom Bar Effect - Used for media player controls
     * Combines blur, lens, and vibrancy for a classic liquid glass look
     */
    fun glassBottomBarEffects(
        enableBlur: Boolean = true,
        enableLens: Boolean = true,
        enableVibrancy: Boolean = true
    ) = buildList {
        if (enableVibrancy) add(vibrancy())
        if (enableBlur) add(blur(4f.dp.toPx()))
        if (enableLens && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(lens(16f.dp.toPx(), 32f.dp.toPx()))
        }
    }

    /**
     * Interactive Glass Button Effect - For playback control buttons
     * Lighter blur with enhanced color
     */
    fun glassButtonEffects(
        enableBlur: Boolean = true,
        enableLens: Boolean = true
    ) = buildList {
        if (enableBlur) add(blur(3f.dp.toPx()))
        if (enableLens && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(lens(12f.dp.toPx(), 24f.dp.toPx()))
        }
    }

    /**
     * Glass Dialog Effect - For modal dialogs and bottom sheets
     * Stronger blur for better readability
     */
    fun glassDialogEffects(
        enableBlur: Boolean = true,
        enableLens: Boolean = true
    ) = buildList {
        add(vibrancy())
        if (enableBlur) add(blur(8f.dp.toPx()))
        if (enableLens && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(lens(20f.dp.toPx(), 40f.dp.toPx()))
        }
    }

    /**
     * Glass Card Effect - For video cards, playlist items
     * Subtle effect to avoid overwhelming content
     */
    fun glassCardEffects(
        enableBlur: Boolean = true
    ) = buildList {
        if (enableBlur) add(blur(2.5f.dp.toPx()))
    }

    /**
     * Video Player Overlay Effect - For player controls overlay
     * Balanced blur and lens for interactive controls
     */
    fun playerOverlayEffects(
        enableBlur: Boolean = true,
        enableLens: Boolean = true,
        enableVibrancy: Boolean = true
    ) = buildList {
        if (enableVibrancy) add(vibrancy())
        if (enableBlur) add(blur(5f.dp.toPx()))
        if (enableLens && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(lens(18f.dp.toPx(), 36f.dp.toPx()))
        }
    }

    // Surface color for readability (semi-transparent white)
    val glassSurfaceColor = Color.White.copy(alpha = 0.5f)
    val glassDarkSurfaceColor = Color.Black.copy(alpha = 0.3f)
}


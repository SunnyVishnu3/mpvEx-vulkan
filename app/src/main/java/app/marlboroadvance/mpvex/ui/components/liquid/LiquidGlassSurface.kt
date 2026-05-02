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
import androidx.compose.ui.platform.LocalDensity
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
    target: LiquidTarget = LiquidTarget.NAV,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    defaultTintColor: Color = Color.White,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    // CHANGED: prefer shared LocalLiquidPreferences if MainActivity provided one (perf: one DataStore wrapper for whole tree).
    // Fallback to a remembered instance keyed on applicationContext so it survives configuration changes
    // and is not rebuilt on every recomposition.
    val sharedPrefs = LocalLiquidPreferences.current
    val liquidPrefs = sharedPrefs ?: remember(context.applicationContext) {
        LiquidUIPreferences(context.applicationContext)
    }

    val blurRadius by remember(liquidPrefs, target) { liquidPrefs.blurRadiusFlow(target) }.collectAsState(initial = 0f)
    val refractionHeight by remember(liquidPrefs, target) { liquidPrefs.refractionHeightFlow(target) }.collectAsState(initial = 40f)
    val refractionAmount by remember(liquidPrefs, target) { liquidPrefs.refractionAmountFlow(target) }.collectAsState(initial = 23f)
    val chromaticAberration by remember(liquidPrefs, target) { liquidPrefs.chromaticAberrationFlow(target) }.collectAsState(initial = false)
    val depthEffect by remember(liquidPrefs, target) { liquidPrefs.depthEffectFlow(target) }.collectAsState(initial = true)
    val vibrancyEnabled by remember(liquidPrefs, target) { liquidPrefs.vibrancyEnabledFlow(target) }.collectAsState(initial = true)
    // CHANGED: initial value 0.15f → 0.5f to match the new DataStore default; prevents a flash of see-through glass
    // (where backdrop text would bleed through) on first frame before the flow emits.
    val tintAlpha by remember(liquidPrefs, target) { liquidPrefs.tintAlphaFlow(target) }.collectAsState(initial = 0.5f)

    val density = LocalDensity.current
    // ADDED (perf): hoist dp→px conversions out of the per-frame `effects` draw lambda.
    // Previously `refractionHeight.dp.toPx()` etc. ran on every frame inside drawBackdrop's effects block.
    // Now they only recompute when density or the underlying pref value actually changes.
    val blurPx = remember(density, blurRadius) { with(density) { blurRadius.dp.toPx() } }
    val refractionHeightPx = remember(density, refractionHeight) { with(density) { refractionHeight.dp.toPx() } }
    val refractionAmountPx = remember(density, refractionAmount) { with(density) { refractionAmount.dp.toPx() } }
    // ADDED: clamp tint alpha to [0,1] defensively; a stray out-of-range pref value would otherwise crash drawRect.
    val safeTintAlpha = tintAlpha.coerceIn(0f, 1f)

    if (Build.VERSION.SDK_INT >= 33) {
        Box(
            modifier = modifier
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { shape },
                    effects = {
                        // CHANGED: enforce Backdrop docs' required effect order — color filter (vibrancy) → blur → lens.
                        // ADDED (perf): skip lens() entirely when refraction params are 0 — the lens shader is
                        // the most expensive effect in this pipeline, and running it with zero amount is wasted GPU work.
                        if (vibrancyEnabled) vibrancy()
                        if (blurPx > 0f) blur(blurPx)
                        if (refractionHeightPx > 0f && refractionAmountPx > 0f) {
                            lens(
                                refractionHeight = refractionHeightPx,
                                refractionAmount = refractionAmountPx,
                                depthEffect = depthEffect,
                                chromaticAberration = chromaticAberration
                            )
                        }
                    },
                    onDrawSurface = { drawRect(defaultTintColor.copy(alpha = safeTintAlpha)) }
                )
        ) { content() }
    } else {
        // CHANGED: pre-API-33 fallback alpha is now coerced to ≥ 0.7f.
        // Reason: this branch can't run blur/lens shaders (RuntimeShader is API 33+), so the tint is the ONLY
        // thing separating foreground text from backdrop content. 0.5f looked transparent here even though it
        // works fine on API 33+ where blur/lens further obscure the backdrop.
        val fallbackAlpha = safeTintAlpha.coerceAtLeast(0.7f)
        Box(
            modifier = modifier
                .background(defaultTintColor.copy(alpha = fallbackAlpha), shape)
                .border(1.dp, Color.White.copy(alpha = 0.2f), shape)
                .clip(shape)
        ) { content() }
    }
}

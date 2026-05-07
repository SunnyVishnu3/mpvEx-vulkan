package app.marlboroadvance.mpvex.ui.components.liquid

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ripple
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import app.marlboroadvance.mpvex.preferences.LiquidTarget
import app.marlboroadvance.mpvex.preferences.LiquidUIPreferences
import app.marlboroadvance.mpvex.ui.components.liquid.utils.DampedDragAnimation

// Broadcasts the glass camera to any button that wants it!
val LocalLiquidBackdrop = androidx.compose.runtime.staticCompositionLocalOf<com.kyant.backdrop.Backdrop?> { null }

// ADDED: shared LiquidUIPreferences CompositionLocal.
// Why: previously every LiquidGlassSurface built its own DataStore wrapper via remember{ LiquidUIPreferences(context) }.
// With many glass surfaces on screen (nav + buttons + cards) that meant N wrappers all observing the same DataStore.
// MainActivity can now provide one instance for the whole tree; null fallback keeps old behavior working.
val LocalLiquidPreferences = androidx.compose.runtime.staticCompositionLocalOf<LiquidUIPreferences?> { null }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TransparentLiquidButton(
    modifier: Modifier = Modifier,
    backdrop: Backdrop?,
    shape: Shape = CircleShape,
    @Suppress("UNUSED_PARAMETER") target: LiquidTarget = LiquidTarget.BUTTON,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    if (backdrop == null) {
        Box(
            modifier = modifier
                .clip(shape)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
            contentAlignment = Alignment.Center
        ) { content() }
        return
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val animationScope = rememberCoroutineScope()

    val context = LocalContext.current
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
    val tintAlpha by remember(liquidPrefs, target) { liquidPrefs.tintAlphaFlow(target) }.collectAsState(initial = 0.5f)

    val density = LocalDensity.current

    val dampedDragAnimation = remember(animationScope) {
        DampedDragAnimation(
            animationScope = animationScope,
            initialValue = 0f,
            valueRange = 0f..0f, // No positional movement for button
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            pressedScale = 1.1f, // Subtle bulge on press
            onDragStarted = {},
            onDragStopped = {},
            onDrag = { _, _ -> }
        )
    }

    LaunchedEffect(isPressed) {
        if (isPressed) dampedDragAnimation.press()
        else dampedDragAnimation.release()
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = dampedDragAnimation.scaleX
                scaleY = dampedDragAnimation.scaleY
                val velocity = dampedDragAnimation.velocity / 50f
                scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
            }
            .drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    val progress = dampedDragAnimation.pressProgress
                    if (vibrancyEnabled) vibrancy()
                    val currentBlur = with(density) { blurRadius.dp.toPx() }
                    if (currentBlur > 0f) blur(currentBlur * (1f - progress))
                    
                    val currentHeight = with(density) { refractionHeight.dp.toPx() }
                    val currentAmount = with(density) { refractionAmount.dp.toPx() }
                    if (currentHeight > 0f && currentAmount > 0f) {
                        lens(
                            currentHeight * progress,
                            currentAmount * progress,
                            depthEffect = depthEffect,
                            chromaticAberration = chromaticAberration
                        )
                    }
                },
                highlight = {
                    val progress = dampedDragAnimation.pressProgress
                    Highlight.Ambient.copy(
                        width = Highlight.Ambient.width / 1.5f,
                        blurRadius = Highlight.Ambient.blurRadius / 1.5f,
                        alpha = progress
                    )
                },
                shadow = {
                    Shadow(
                        radius = 4f.dp,
                        color = Color.Black.copy(alpha = 0.05f)
                    )
                },
                innerShadow = {
                    val progress = dampedDragAnimation.pressProgress
                    InnerShadow(
                        radius = 4f.dp * progress,
                        alpha = progress
                    )
                },
                onDrawSurface = {
                    val progress = dampedDragAnimation.pressProgress
                    drawRect(Color.White.copy(alpha = tintAlpha * (1f - progress)))
                }
            )
            .combinedClickable(
                interactionSource = interactionSource,
                indication = ripple(),
                onClick = onClick,
                onLongClick = onLongClick
            ),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
fun LiquidGlassCard(
    modifier: Modifier = Modifier,
    backdrop: Backdrop?,
    shape: Shape = RoundedCornerShape(12.dp),
    content: @Composable () -> Unit
) {
    if (backdrop == null) {
        androidx.compose.foundation.layout.Box(modifier = modifier) { content() }
        return
    }

    LiquidGlassSurface(
        backdrop = backdrop,
        shape = shape,
        target = LiquidTarget.DIALOG,
        modifier = modifier
    ) {
        content()
    }
}

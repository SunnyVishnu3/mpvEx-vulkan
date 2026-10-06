package app.gyrolet.mpvrx.ui.liquidglass

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceAtMost
import androidx.compose.ui.util.lerp
import app.gyrolet.mpvrx.preferences.AppearancePreferences
import app.gyrolet.mpvrx.preferences.preference.collectAsState
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import org.koin.compose.koinInject
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh

val LocalScreenBackdrop = compositionLocalOf<Backdrop?> { null }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LiquidButton(
    onClick: () -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    onLongClick: () -> Unit = {},
    isInteractive: Boolean = true,
    useGlass: Boolean = true,
    tint: Color = Color.Unspecified,
    surfaceColor: Color = Color.Unspecified,
    height: Dp = 48.dp,
    horizontalPadding: Dp = 16.dp,
    spacing: Dp = 8.dp,
    content: @Composable RowScope.() -> Unit,
) {
    val animationScope = rememberCoroutineScope()
    val contentColor = LocalContentColor.current
    val effectiveTint = if (tint.isSpecified) tint else contentColor

    val preferences = koinInject<AppearancePreferences>()
    val blurRadius by preferences.liquidButtonBlur.collectAsState()
    val lensRadius by preferences.liquidButtonLensRadius.collectAsState()
    val lensDepth by preferences.liquidButtonLensDepth.collectAsState()
    val liquidOpacity by preferences.liquidButtonOpacity.collectAsState()
    val liquidTint by preferences.liquidButtonTint.collectAsState()
    val density = LocalDensity.current
    val isDark = isSystemInDarkTheme()

    val interactiveHighlight = remember(animationScope) {
        InteractiveHighlight(
            animationScope = animationScope
        )
    }

    Row(
        modifier
            .then(
                if (useGlass) {
                    Modifier.drawBackdrop(
                        backdrop = backdrop,
                        shape = { Capsule() },
                        effects = {
                            vibrancy()
                            blur(with(density) { blurRadius.coerceAtLeast(18f).dp.toPx() })
                            lens(
                                with(density) { lensRadius.coerceAtLeast(38f).dp.toPx() },
                                with(density) { lensDepth.coerceAtLeast(56f).dp.toPx() },
                                chromaticAberration = true
                            )
                        },
                        highlight = {
                            Highlight.Ambient.copy(
                                width = Highlight.Ambient.width / 1.35f,
                                blurRadius = Highlight.Ambient.blurRadius / 1.25f,
                                alpha = if (isInteractive) 0.72f else 0.46f
                            )
                        },
                        shadow = {
                            Shadow(
                                radius = 12.dp,
                                color = Color.Black.copy(alpha = if (isDark) 0.28f else 0.12f)
                            )
                        },
                        innerShadow = {
                            InnerShadow(
                                radius = 5.dp,
                                alpha = if (isDark) 0.42f else 0.26f
                            )
                        },
                        layerBlock = if (isInteractive) {
                            {
                                val width = size.width
                                val heightPx = size.height

                                val progress = interactiveHighlight.pressProgress
                                val scale = lerp(1f, 1f + (4f.dp.toPx() / size.height), progress)

                                val maxOffset = size.minDimension
                                val initialDerivative = 0.05f
                                val offset = interactiveHighlight.offset
                                translationX = maxOffset * tanh((initialDerivative * offset.x) / maxOffset)
                                translationY = maxOffset * tanh((initialDerivative * offset.y) / maxOffset)

                                val maxDragScale = 4f.dp.toPx() / size.height
                                val offsetAngle = atan2(offset.y, offset.x)
                                scaleX =
                                    scale +
                                            maxDragScale * abs(cos(offsetAngle) * offset.x / size.maxDimension) *
                                            (width / heightPx).fastCoerceAtMost(1f)
                                scaleY =
                                    scale +
                                            maxDragScale * abs(sin(offsetAngle) * offset.y / size.maxDimension) *
                                            (heightPx / width).fastCoerceAtMost(1f)
                            }
                        } else {
                            null
                        },
                        onDrawSurface = {
                            val tintColor = if (tint.isSpecified) tint else Color(liquidTint)
                            val baseAlpha = liquidOpacity.coerceIn(0.05f, 0.42f)
                            drawRect(
                                Color.White,
                                blendMode = BlendMode.Screen,
                                alpha = if (isDark) 0.10f else 0.26f
                            )
                            drawRect(
                                tintColor,
                                blendMode = BlendMode.Screen,
                                alpha = baseAlpha
                            )
                            drawRect(
                                Color.Black,
                                blendMode = BlendMode.Multiply,
                                alpha = if (isDark) 0.08f else 0.025f
                            )
                            drawRect(tintColor.copy(alpha = baseAlpha * 0.16f))

                            if (surfaceColor.isSpecified) {
                                drawRect(surfaceColor)
                            }
                        }
                    )
                } else {
                    Modifier
                }
            )
            .combinedClickable(
                interactionSource = null,
                indication = if (isInteractive) null else LocalIndication.current,
                role = Role.Button,
                onClick = onClick,
                onLongClick = onLongClick
            )
            .then(
                if (isInteractive) {
                    Modifier
                        .then(interactiveHighlight.modifier)
                        .then(interactiveHighlight.gestureModifier)
                } else {
                    Modifier
                }
            )
            .height(height)
            .padding(horizontal = horizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(spacing, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

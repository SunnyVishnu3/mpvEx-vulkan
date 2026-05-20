package com.astrixforge.devicemasker.ui.theme

import android.animation.ValueAnimator
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

/**
 * DeviceMasker Motion specifications.
 *
 * Uses spring-based animations as recommended by Material 3 Expressive. Spring animations feel more
 * natural and responsive than duration-based animations.
 */
@Stable data class MotionPolicy(val reduceMotion: Boolean = false)

val LocalMotionPolicy = staticCompositionLocalOf { MotionPolicy() }

@Composable
fun rememberMotionPolicy(): MotionPolicy {
    LocalView.current
    return MotionPolicy(reduceMotion = !ValueAnimator.areAnimatorsEnabled())
}

object AppMotion {
    @Composable fun policy(): MotionPolicy = LocalMotionPolicy.current

    @Composable
    fun <T> spatial(
        spec: FiniteAnimationSpec<T>,
        reduced: FiniteAnimationSpec<T>,
    ): FiniteAnimationSpec<T> = if (policy().reduceMotion) reduced else spec

    @Composable fun shouldReduceMotion(): Boolean = policy().reduceMotion

    fun <T> noBounce(stiffness: Float): SpringSpec<T> =
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = stiffness)

    val ReducedAlpha: SpringSpec<Float> = noBounce(stiffness = Spring.StiffnessMedium)
    val ReducedOffset: SpringSpec<IntOffset> = noBounce(stiffness = Spring.StiffnessMedium)
    val ReducedIntSize: SpringSpec<IntSize> = noBounce(stiffness = Spring.StiffnessMedium)
    val ReducedDp: SpringSpec<Dp> = noBounce(stiffness = Spring.StiffnessMedium)

    object Spatial {
        val ExpressiveDefault: SpringSpec<Float> = spring(dampingRatio = 0.9f, stiffness = 700f)

        val ExpressiveFast: SpringSpec<Float> = spring(dampingRatio = 0.9f, stiffness = 1400f)

        val ExpressiveSlow: SpringSpec<Float> = spring(dampingRatio = 0.9f, stiffness = 300f)

        val StandardDefault: SpringSpec<Float> = spring(dampingRatio = 1f, stiffness = 380f)

        val Expressive: SpringSpec<Float> = ExpressiveDefault

        val Standard: SpringSpec<Float> = StandardDefault

        val StandardIntSize: SpringSpec<IntSize> =
            spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMediumLow,
            )

        val Snappy: SpringSpec<Float> = ExpressiveFast

        val SnappyDp: SpringSpec<Dp> = spring(dampingRatio = 0.9f, stiffness = 1400f)
    }

    object Effect {
        val Color: SpringSpec<Color> =
            spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow,
            )

        val Alpha: SpringSpec<Float> =
            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
    }

    @Deprecated(
        message = "Use AppMotion.spatial(Spatial.Standard, ReducedOffset) for position animations"
    )
    val DefaultSpringOffset: SpringSpec<IntOffset> =
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)

    @Deprecated(
        message = "Use AppMotion.spatial(Spatial.Snappy, ReducedAlpha) for quick scale animations"
    )
    val FastSpring: SpringSpec<Float> =
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)

    @Deprecated(message = "Use AppMotion.spatial(Spatial.SnappyDp, ReducedDp) for size animations")
    val BouncySpringDp: SpringSpec<Dp> =
        spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessLow)
}

object MotionTokens {
    object Expressive {
        object Spatial {
            val default = spring<Float>(dampingRatio = 0.9f, stiffness = 700f)
            val fast = spring<Float>(dampingRatio = 0.9f, stiffness = 1400f)
            val slow = spring<Float>(dampingRatio = 0.9f, stiffness = 300f)
        }

        object Effects {
            val default = spring<Float>(dampingRatio = 1f, stiffness = 1600f)
            val fast = spring<Float>(dampingRatio = 1f, stiffness = 3800f)
            val slow = spring<Float>(dampingRatio = 1f, stiffness = 800f)
        }
    }

    object Standard {
        object Spatial {
            val default = spring<Float>(dampingRatio = 1f, stiffness = 380f)
            val fast = spring<Float>(dampingRatio = 1f, stiffness = 800f)
            val slow = spring<Float>(dampingRatio = 1f, stiffness = 200f)
        }
    }
}

object ElevationTokens {
    val Level0 = 0.dp
    val Level1 = 1.dp
    val Level2 = 3.dp
    val Level3 = 6.dp
    val Level4 = 8.dp
    val Level5 = 12.dp
}

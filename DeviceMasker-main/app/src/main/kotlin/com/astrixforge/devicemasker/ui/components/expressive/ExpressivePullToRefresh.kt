package com.astrixforge.devicemasker.ui.components.expressive

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.astrixforge.devicemasker.ui.theme.AppMotion
import com.astrixforge.devicemasker.ui.theme.ElevationTokens

/**
 * Material 3 Expressive Pull-to-Refresh container.
 *
 * Wraps content with pull-to-refresh functionality using stable components.
 *
 * @param isRefreshing Whether content is currently refreshing
 * @param onRefresh Callback triggered when user pulls to refresh
 * @param modifier Modifier for the container
 * @param indicatorColor Color of the loading indicator shapes
 * @param containerColor Background color of the indicator container
 * @param indicatorSize Size of the loading indicator
 * @param content Content to display with pull-to-refresh
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpressivePullToRefresh(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    indicatorColor: Color = MaterialTheme.colorScheme.primary,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    indicatorSize: Dp = 32.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val state = rememberPullToRefreshState()

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        state = state,
        modifier = modifier.fillMaxSize(),
        indicator = {
            ExpressiveRefreshIndicator(
                state = state,
                isRefreshing = isRefreshing,
                indicatorColor = indicatorColor,
                containerColor = containerColor,
                indicatorSize = indicatorSize,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        },
        content = content,
    )
}

/**
 * M3 Expressive refresh indicator with stable CircularProgressIndicator.
 *
 * Shows a Surface container with the progress indicator inside. Animates visibility based on pull
 * progress and refresh state.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpressiveRefreshIndicator(
    state: PullToRefreshState,
    isRefreshing: Boolean,
    modifier: Modifier = Modifier,
    indicatorColor: Color = MaterialTheme.colorScheme.primary,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    indicatorSize: Dp = 32.dp,
) {
    // Calculate visibility based on pull progress
    val progress = state.distanceFraction.coerceIn(0f, 1f)
    val showIndicator = isRefreshing || progress > 0f

    // Animate alpha for smooth appearance
    val alpha by
        animateFloatAsState(
            targetValue = if (showIndicator) 1f else 0f,
            animationSpec = AppMotion.spatial(AppMotion.Effect.Alpha, AppMotion.ReducedAlpha),
            label = "indicatorAlpha",
        )

    // Animate vertical offset based on pull progress
    val offset by
        animateFloatAsState(
            targetValue = if (isRefreshing) 64f else (progress * 64f),
            animationSpec = AppMotion.spatial(AppMotion.Spatial.Standard, AppMotion.ReducedAlpha),
            label = "indicatorOffset",
        )
    val density = LocalDensity.current

    if (showIndicator) {
        Surface(
            modifier =
                modifier
                    .padding(top = 8.dp)
                    .offset { IntOffset(x = 0, y = with(density) { offset.dp.roundToPx() }) }
                    .size(indicatorSize + 16.dp)
                    .alpha(alpha),
            shape = CircleShape,
            color = containerColor,
            shadowElevation = ElevationTokens.Level2,
            tonalElevation = ElevationTokens.Level1,
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ExpressiveLoadingIndicator(
                    modifier = Modifier.size(indicatorSize),
                    color = indicatorColor,
                )
            }
        }
    }
}

/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package app.gyrolet.mpvrx.ui.utils

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerScope
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import app.gyrolet.mpvrx.preferences.GesturePreferences
import app.gyrolet.mpvrx.preferences.PlayerPreferences
import app.gyrolet.mpvrx.preferences.preference.collectAsState
import app.gyrolet.mpvrx.ui.player.NavigationAnimStyle
import app.gyrolet.mpvrx.ui.theme.AppMotion
import kotlin.math.abs
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/** Page interaction is separate from the host lifecycle: swiping must not resume/rescan a library. */
internal val LocalNavigationPageActive = compositionLocalOf { true }

// A full-page horizontal gesture has one owner. Nested category tabs remain tappable.
private val LocalNavigationPagerPresent = staticCompositionLocalOf { false }
private val PassThroughPageScrollConnection = object : NestedScrollConnection {}

@Composable
internal fun NavigationBackHandler(enabled: Boolean = true, onBack: () -> Unit) {
  BackHandler(enabled = enabled && LocalNavigationPageActive.current, onBack = onBack)
}

/**
 * Native finger-following page scrolling. Visual treatment is applied in a graphics layer so
 * swipes do not trigger relayout/recomposition of expensive media-library screens.
 */
@Composable
internal fun NavigationPager(
  state: PagerState,
  modifier: Modifier = Modifier,
  beyondViewportPageCount: Int = 1,
  userScrollEnabled: Boolean = true,
  key: ((Int) -> Any)? = null,
  allowNestedSwipes: Boolean = false,
  content: @Composable PagerScope.(Int) -> Unit,
) {
  val gesturePreferences = koinInject<GesturePreferences>()
  val playerPreferences = koinInject<PlayerPreferences>()
  val nestedTabSwipesEnabled by gesturePreferences.nestedTabSwipesEnabled.collectAsState()
  val style by playerPreferences.appNavStyle.collectAsState()
  val reduceMotion = AppMotion.shouldReduceMotion()
  val isNestedPager = LocalNavigationPagerPresent.current
  val ownsHorizontalSwipes = userScrollEnabled && (!isNestedPager || (allowNestedSwipes && nestedTabSwipesEnabled))
  val nestedScrollConnection = if (ownsHorizontalSwipes) {
    PagerDefaults.pageNestedScrollConnection(state, Orientation.Horizontal)
  } else {
    // Disabling drag alone does not disable a pager's nested-scroll/fling consumption.
    PassThroughPageScrollConnection
  }

  CompositionLocalProvider(LocalNavigationPagerPresent provides true) {
    HorizontalPager(
      state = state,
      modifier = modifier.clipToBounds(),
      // Do not multiply offscreen library composition across outer and inner pagers.
      beyondViewportPageCount = if (isNestedPager) 0 else beyondViewportPageCount,
      userScrollEnabled = ownsHorizontalSwipes,
      pageNestedScrollConnection = nestedScrollConnection,
      overscrollEffect = null,
      key = key,
      // Keep HorizontalPager's velocity-aware fling and settling defaults in both directions.
    ) { page ->
      BrowserTabPage(
        pagerState = state,
        page = page,
        style = style,
        reduceMotion = reduceMotion,
      ) {
        content(page)
      }
    }
  }
}

/** A new tab request cancels the previous one instead of launching competing scrolls. */
@Composable
internal fun rememberTabNavigation(state: PagerState): (Int) -> Unit {
  val preferences = koinInject<PlayerPreferences>()
  val style by preferences.appNavStyle.collectAsState()
  val speed by preferences.animationSpeed.collectAsState()
  val reduceMotion = AppMotion.shouldReduceMotion()
  val scope = rememberCoroutineScope()
  var job by remember(state) { mutableStateOf<Job?>(null) }

  return { page ->
    if (page in 0 until state.pageCount) {
      val isAlreadySettled = state.currentPage == page &&
        state.currentPageOffsetFraction == 0f && !state.isScrollInProgress
      job?.cancel()
      if (!isAlreadySettled) {
        job = scope.launch {
          if (style == NavigationAnimStyle.None || reduceMotion) {
            state.scrollToPage(page)
          } else {
            state.animateScrollToPage(
              page = page,
              animationSpec = navigationTabAnimationSpec(style, speed),
            )
          }
        }
      }
    }
  }
}

@Composable
private fun BrowserTabPage(
  pagerState: PagerState,
  page: Int,
  style: NavigationAnimStyle,
  reduceMotion: Boolean,
  content: @Composable () -> Unit,
) {
  val parentActive = LocalNavigationPageActive.current
  val isActive by remember(pagerState, page, parentActive) {
    derivedStateOf { parentActive && pagerState.settledPage == page }
  }

  // State is read from the layer block so a drag invalidates only the render layer, not the
  // media-library composition. All effects are alpha/scale transforms and remain GPU-friendly.
  val motionModifier =
    if (reduceMotion || style == NavigationAnimStyle.None) {
      Modifier
    } else {
      Modifier.graphicsLayer {
        val signedOffset =
          ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
            .coerceIn(-1f, 1f)
        val distance = abs(signedOffset)

        when (style) {
          NavigationAnimStyle.None -> Unit
          NavigationAnimStyle.Minimal -> {
            alpha = 1f - (0.10f * distance)
          }
          NavigationAnimStyle.FlipFade -> {
            alpha = 1f - (0.28f * distance)
            val scale = 1f - (0.012f * distance)
            scaleX = scale
            scaleY = scale
          }
          NavigationAnimStyle.Depth -> {
            alpha = 1f - (0.18f * distance)
            val scale = 1f - (0.035f * distance)
            scaleX = scale
            scaleY = scale
            translationY = size.height * 0.012f * distance
          }
          NavigationAnimStyle.Default -> {
            alpha = 1f - (0.06f * distance)
            val scale = 1f - (0.010f * distance)
            scaleX = scale
            scaleY = scale
          }
        }
      }
    }

  // Keep data collection and resume observers on the real screen lifecycle. A synthetic
  // CREATED -> RESUMED transition here used to restart them at the end of every swipe.
  CompositionLocalProvider(LocalNavigationPageActive provides isActive) {
    Box(
      Modifier
        .fillMaxSize()
        .then(motionModifier)
        .focusProperties { canFocus = isActive }
        .semantics { if (!isActive) hideFromAccessibility() },
    ) {
      content()
    }
  }
}

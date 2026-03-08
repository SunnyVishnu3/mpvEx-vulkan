package app.marlboroadvance.mpvex.ui.browser

import android.annotation.SuppressLint
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import app.marlboroadvance.mpvex.presentation.Screen
import app.marlboroadvance.mpvex.ui.browser.folderlist.FolderListScreen
import app.marlboroadvance.mpvex.ui.browser.networkstreaming.NetworkStreamingScreen
import app.marlboroadvance.mpvex.ui.browser.playlist.PlaylistScreen
import app.marlboroadvance.mpvex.ui.browser.recentlyplayed.RecentlyPlayedScreen
import app.marlboroadvance.mpvex.ui.browser.selection.SelectionManager
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable

// --- NEW IMPORTS FOR LIQUID TABS ---
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import app.marlboroadvance.mpvex.ui.components.liquid.LiquidBottomTabs
// -----------------------------------

@Serializable
object MainScreen : Screen {
  private var persistentSelectedTab: Int = 0
  
  @Volatile
  private var isInSelectionModeShared: Boolean = false  
  
  @Volatile
  private var shouldHideNavigationBar: Boolean = false  
  
  @Volatile
  private var isBrowserBottomBarVisible: Boolean = false  
  
  @Volatile
  private var sharedVideoSelectionManager: Any? = null
  
  @Volatile
  private var onlyVideosSelected: Boolean = false
  
  @Volatile
  private var isPermissionDenied: Boolean = false
  
  fun updateSelectionState(
    isInSelectionMode: Boolean,
    isOnlyVideosSelected: Boolean,
    selectionManager: Any?
  ) {
    this.isInSelectionModeShared = isInSelectionMode
    this.onlyVideosSelected = isOnlyVideosSelected
    this.sharedVideoSelectionManager = selectionManager
    this.shouldHideNavigationBar = isInSelectionMode && isOnlyVideosSelected
  }
  
  fun updatePermissionState(isDenied: Boolean) {
    this.isPermissionDenied = isDenied
  }

  fun getPermissionDeniedState(): Boolean = isPermissionDenied

  fun updateBottomBarVisibility(shouldShow: Boolean) {
    this.shouldHideNavigationBar = !shouldShow
  }

  @Composable
  @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
  override fun Content() {
    var selectedTab by remember { mutableIntStateOf(persistentSelectedTab) }
    val context = LocalContext.current
    val density = LocalDensity.current

    val isInSelectionMode = remember { mutableStateOf(isInSelectionModeShared) }
    val hideNavigationBar = remember { mutableStateOf(shouldHideNavigationBar) }
    val videoSelectionManager = remember { mutableStateOf<SelectionManager<*, *>?>(sharedVideoSelectionManager as? SelectionManager<*, *>) }
    
    LaunchedEffect(Unit) {
      while (true) {
        if (isInSelectionMode.value != isInSelectionModeShared) {
          isInSelectionMode.value = isInSelectionModeShared
        }
        if (hideNavigationBar.value != shouldHideNavigationBar) {
          hideNavigationBar.value = shouldHideNavigationBar
        }
        val currentManager = sharedVideoSelectionManager as? SelectionManager<*, *>
        if (videoSelectionManager.value != currentManager) {
          videoSelectionManager.value = currentManager
        }
        delay(16)
      }
    }
    
    LaunchedEffect(selectedTab) {
      persistentSelectedTab = selectedTab
    }

    // THE FIX: We create the Backdrop here to capture the screen underneath!
    val bottomBarBackdrop = rememberLayerBackdrop { drawContent() }

    Scaffold(
      modifier = Modifier.fillMaxSize(),
      bottomBar = {
        AnimatedVisibility(
          visible = !hideNavigationBar.value,
          enter = slideInVertically(
            animationSpec = tween(durationMillis = 300),
            initialOffsetY = { fullHeight -> fullHeight }
          ),
          exit = slideOutVertically(
            animationSpec = tween(durationMillis = 300),
            targetOffsetY = { fullHeight -> fullHeight }
          )
        ) {
          // Wrap the new Liquid tabs in a Box with padding so it floats beautifully at the bottom
          Box(
              modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 24.dp, vertical = 24.dp)
          ) {
              LiquidBottomTabs(
                  tabsCount = 4,
                  selectedIndex = selectedTab,
                  backdrop = bottomBarBackdrop
              ) {
                  IconButton(onClick = { selectedTab = 0 }, modifier = Modifier.weight(1f)) {
                      Icon(Icons.Filled.Home, "Home", tint = if (selectedTab == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                  }
                  IconButton(onClick = { selectedTab = 1 }, modifier = Modifier.weight(1f)) {
                      Icon(Icons.Filled.History, "Recents", tint = if (selectedTab == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                  }
                  IconButton(onClick = { selectedTab = 2 }, modifier = Modifier.weight(1f)) {
                      Icon(Icons.AutoMirrored.Filled.PlaylistPlay, "Playlists", tint = if (selectedTab == 2) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                  }
                  IconButton(onClick = { selectedTab = 3 }, modifier = Modifier.weight(1f)) {
                      Icon(Icons.Filled.Language, "Network", tint = if (selectedTab == 3) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                  }
              }
          }
        }
      }
    ) { paddingValues ->
      // THE FIX: Attach the backdrop to the Box that holds the screens, so the nav bar can refract them!
      Box(modifier = Modifier.fillMaxSize().layerBackdrop(bottomBarBackdrop)) {
        val fabBottomPadding = 80.dp

        AnimatedContent(
          targetState = selectedTab,
          transitionSpec = {
            val slideDistance = with(density) { 48.dp.roundToPx() }
            val animationDuration = 250
            if (targetState > initialState) {
              (slideInHorizontally(animationSpec = tween(durationMillis = animationDuration, easing = FastOutSlowInEasing), initialOffsetX = { slideDistance }) + fadeIn(animationSpec = tween(durationMillis = animationDuration, easing = FastOutSlowInEasing))) togetherWith (slideOutHorizontally(animationSpec = tween(durationMillis = animationDuration, easing = FastOutSlowInEasing), targetOffsetX = { -slideDistance }) + fadeOut(animationSpec = tween(durationMillis = animationDuration / 2, easing = FastOutSlowInEasing)))
            } else {
              (slideInHorizontally(animationSpec = tween(durationMillis = animationDuration, easing = FastOutSlowInEasing), initialOffsetX = { -slideDistance }) + fadeIn(animationSpec = tween(durationMillis = animationDuration, easing = FastOutSlowInEasing))) togetherWith (slideOutHorizontally(animationSpec = tween(durationMillis = animationDuration, easing = FastOutSlowInEasing), targetOffsetX = { slideDistance }) + fadeOut(animationSpec = tween(durationMillis = animationDuration / 2, easing = FastOutSlowInEasing)))
            }
          },
          label = "tab_animation"
        ) { targetTab ->
          CompositionLocalProvider(
            LocalNavigationBarHeight provides fabBottomPadding
          ) {
            when (targetTab) {
              0 -> FolderListScreen.Content()
              1 -> RecentlyPlayedScreen.Content()
              2 -> PlaylistScreen.Content()
              3 -> NetworkStreamingScreen.Content()
            }
          }
        }
      }
    }
  }
}

val LocalNavigationBarHeight = compositionLocalOf { 0.dp }

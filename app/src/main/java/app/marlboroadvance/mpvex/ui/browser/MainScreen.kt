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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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

// --- LIQUID GLASS IMPORTS ---
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import app.marlboroadvance.mpvex.ui.components.liquid.LiquidGlassSurface
import app.marlboroadvance.mpvex.preferences.LiquidUIPreferences
// ----------------------------

@Serializable
object MainScreen : Screen {
  private var persistentSelectedTab: Int = 0
  
  @Volatile private var isInSelectionModeShared: Boolean = false  
  @Volatile private var shouldHideNavigationBar: Boolean = false  
  @Volatile private var isBrowserBottomBarVisible: Boolean = false  
  @Volatile private var sharedVideoSelectionManager: Any? = null
  @Volatile private var onlyVideosSelected: Boolean = false
  @Volatile private var isPermissionDenied: Boolean = false
  
  fun updateSelectionState(isInSelectionMode: Boolean, isOnlyVideosSelected: Boolean, selectionManager: Any?) {
    this.isInSelectionModeShared = isInSelectionMode
    this.onlyVideosSelected = isOnlyVideosSelected
    this.sharedVideoSelectionManager = selectionManager
    this.shouldHideNavigationBar = isInSelectionMode && isOnlyVideosSelected
  }
  
  fun updatePermissionState(isDenied: Boolean) { this.isPermissionDenied = isDenied }
  fun getPermissionDeniedState(): Boolean = isPermissionDenied
  fun updateBottomBarVisibility(shouldShow: Boolean) { this.shouldHideNavigationBar = !shouldShow }

  @Composable
  @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
  override fun Content() {
    var selectedTab by remember { mutableIntStateOf(persistentSelectedTab) }
    val density = LocalDensity.current

    // Read the Liquid UI Settings!
    val context = LocalContext.current
    val liquidPrefs = remember { LiquidUIPreferences(context) }
    val isLiquidUI by liquidPrefs.liquidUIEnabledFlow.collectAsState(false)

    val isInSelectionMode = remember { mutableStateOf(isInSelectionModeShared) }
    val hideNavigationBar = remember { mutableStateOf(shouldHideNavigationBar) }
    val videoSelectionManager = remember { mutableStateOf<SelectionManager<*, *>?>(sharedVideoSelectionManager as? SelectionManager<*, *>) }
    
    LaunchedEffect(Unit) {
      while (true) {
        if (isInSelectionMode.value != isInSelectionModeShared) isInSelectionMode.value = isInSelectionModeShared
        if (hideNavigationBar.value != shouldHideNavigationBar) hideNavigationBar.value = shouldHideNavigationBar
        val currentManager = sharedVideoSelectionManager as? SelectionManager<*, *>
        if (videoSelectionManager.value != currentManager) videoSelectionManager.value = currentManager
        delay(16)
      }
    }
    
    LaunchedEffect(selectedTab) { persistentSelectedTab = selectedTab }

    val bottomBarBackdrop = rememberLayerBackdrop { drawContent() }

    Scaffold(
      modifier = Modifier.fillMaxSize(),
      bottomBar = {
        AnimatedVisibility(
          visible = !hideNavigationBar.value,
          enter = slideInVertically(animationSpec = tween(durationMillis = 300), initialOffsetY = { it }),
          exit = slideOutVertically(animationSpec = tween(durationMillis = 300), targetOffsetY = { it })
        ) {
          
          // THE TOGGLE LOGIC
          if (isLiquidUI) {
            // The Floating Glass Capsule (from the 1.0.4 fork, upgraded to alpha03)
            LiquidGlassSurface(
                backdrop = bottomBarBackdrop,
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 24.dp)
                    .fillMaxWidth()
                    .height(64.dp),
                shape = RoundedCornerShape(32.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val activeColor = MaterialTheme.colorScheme.primary
                    val inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant
                    
                    IconButton(onClick = { selectedTab = 0 }) {
                        Icon(Icons.Filled.Home, "Home", modifier = Modifier.size(28.dp), tint = if (selectedTab == 0) activeColor else inactiveColor)
                    }
                    IconButton(onClick = { selectedTab = 1 }) {
                        Icon(Icons.Filled.History, "Recents", modifier = Modifier.size(28.dp), tint = if (selectedTab == 1) activeColor else inactiveColor)
                    }
                    IconButton(onClick = { selectedTab = 2 }) {
                        Icon(Icons.AutoMirrored.Filled.PlaylistPlay, "Playlists", modifier = Modifier.size(28.dp), tint = if (selectedTab == 2) activeColor else inactiveColor)
                    }
                    IconButton(onClick = { selectedTab = 3 }) {
                        Icon(Icons.Filled.Language, "Network", modifier = Modifier.size(28.dp), tint = if (selectedTab == 3) activeColor else inactiveColor)
                    }
                }
            }
          } else {
            // The Classic mpvEx Navigation Bar
            NavigationBar(
              modifier = Modifier.clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 0.dp, bottomEnd = 0.dp))
            ) {
              NavigationBarItem(
                icon = { Icon(Icons.Filled.Home, contentDescription = "Home") },
                label = { Text("Home") },
                selected = selectedTab == 0, onClick = { selectedTab = 0 }
              )
              NavigationBarItem(
                icon = { Icon(Icons.Filled.History, contentDescription = "Recents") },
                label = { Text("Recents") },
                selected = selectedTab == 1, onClick = { selectedTab = 1 }
              )
              NavigationBarItem(
                icon = { Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = "Playlists") },
                label = { Text("Playlists") },
                selected = selectedTab == 2, onClick = { selectedTab = 2 }
              )
              NavigationBarItem(
                icon = { Icon(Icons.Filled.Language, contentDescription = "Network") },
                label = { Text("Network") },
                selected = selectedTab == 3, onClick = { selectedTab = 3 }
              )
            }
          }
        }
      }
    ) { paddingValues ->
      // Only apply layerBackdrop if Liquid UI is enabled (saves battery!)
      Box(modifier = Modifier.fillMaxSize().then(if (isLiquidUI) Modifier.layerBackdrop(bottomBarBackdrop) else Modifier)) {
        val fabBottomPadding = 80.dp

        AnimatedContent(
          targetState = selectedTab,
          transitionSpec = {
            val slideDistance = with(density) { 48.dp.roundToPx() }
            val animationDuration = 250
            if (targetState > initialState) {
              (slideInHorizontally(animationSpec = tween(animationDuration, easing = FastOutSlowInEasing), initialOffsetX = { slideDistance }) + fadeIn(animationSpec = tween(animationDuration, easing = FastOutSlowInEasing))) togetherWith (slideOutHorizontally(animationSpec = tween(animationDuration, easing = FastOutSlowInEasing), targetOffsetX = { -slideDistance }) + fadeOut(animationSpec = tween(animationDuration / 2, easing = FastOutSlowInEasing)))
            } else {
              (slideInHorizontally(animationSpec = tween(animationDuration, easing = FastOutSlowInEasing), initialOffsetX = { -slideDistance }) + fadeIn(animationSpec = tween(animationDuration, easing = FastOutSlowInEasing))) togetherWith (slideOutHorizontally(animationSpec = tween(animationDuration, easing = FastOutSlowInEasing), targetOffsetX = { slideDistance }) + fadeOut(animationSpec = tween(animationDuration / 2, easing = FastOutSlowInEasing)))
            }
          },
          label = "tab_animation"
        ) { targetTab ->
          CompositionLocalProvider(LocalNavigationBarHeight provides fabBottomPadding) {
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

package app.marlboroadvance.mpvex.ui.browser.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

// --- LIQUID GLASS IMPORTS ---
import app.marlboroadvance.mpvex.ui.components.liquid.LiquidGlassSurface
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import app.marlboroadvance.mpvex.preferences.LiquidUIPreferences
// ----------------------------

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FloatingBottomBar(
  visible: Boolean,
  showCopy: Boolean = false,
  showMove: Boolean = false,
  showRename: Boolean = false,
  showDelete: Boolean = false,
  showAddToPlaylist: Boolean = false,
  onCopyClick: () -> Unit = {},
  onMoveClick: () -> Unit = {},
  onRenameClick: () -> Unit = {},
  onDeleteClick: () -> Unit = {},
  onAddToPlaylistClick: () -> Unit = {},
  modifier: Modifier = Modifier
) {
  // Read the Liquid UI Settings!
  val context = LocalContext.current
  val liquidPrefs = remember { LiquidUIPreferences(context) }
  val isLiquidUI by liquidPrefs.liquidUIEnabledFlow.collectAsState(false)

  AnimatedVisibility(
    visible = visible,
    enter = fadeIn(),
    exit = fadeOut(),
    modifier = modifier
  ) {
    val backdrop = rememberLayerBackdrop { drawContent() }

    val contentRow = @Composable {
      Row(
        modifier = Modifier
          .fillMaxSize()
          .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
      ) {
        FilledTonalIconButton(
          onClick = onCopyClick, enabled = showCopy, modifier = Modifier.size(50.dp),
          colors = IconButtonDefaults.filledTonalIconButtonColors()
        ) { Icon(Icons.Filled.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(24.dp)) }
        
        FilledTonalIconButton(
          onClick = onMoveClick, enabled = showMove, modifier = Modifier.size(50.dp),
          colors = IconButtonDefaults.filledTonalIconButtonColors()
        ) { Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = "Move", modifier = Modifier.size(24.dp)) }
        
        FilledTonalIconButton(
          onClick = onRenameClick, enabled = showRename, modifier = Modifier.size(50.dp),
          colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
          )
        ) { Icon(Icons.Filled.DriveFileRenameOutline, contentDescription = "Rename", modifier = Modifier.size(24.dp)) }
        
        FilledTonalIconButton(
          onClick = onAddToPlaylistClick, enabled = showAddToPlaylist, modifier = Modifier.size(50.dp),
          colors = IconButtonDefaults.filledTonalIconButtonColors()
        ) { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = "Add to Playlist", modifier = Modifier.size(24.dp)) }
        
        FilledTonalIconButton(
          onClick = onDeleteClick, enabled = showDelete, modifier = Modifier.size(50.dp),
          colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
          )
        ) { Icon(Icons.Filled.Delete, contentDescription = "Delete", modifier = Modifier.size(24.dp)) }
      }
    }

    // THE TOGGLE LOGIC
    if (isLiquidUI) {
      LiquidGlassSurface(
        backdrop = backdrop,
        modifier = Modifier
          .padding(horizontal = 16.dp, vertical = 16.dp)
          .windowInsetsPadding(WindowInsets.systemBars)
          .height(64.dp),
        shape = RoundedCornerShape(24.dp)
      ) {
        contentRow()
      }
    } else {
      // Fallback to the classic mpvEx solid surface
      Surface(
        modifier = Modifier
          .padding(horizontal = 16.dp, vertical = 16.dp)
          .windowInsetsPadding(WindowInsets.systemBars)
          .height(64.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shadowElevation = 6.dp
      ) {
        contentRow()
      }
    }
  }
}

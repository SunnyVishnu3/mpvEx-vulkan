package app.marlboroadvance.mpvex.ui.player.controls

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.marlboroadvance.mpvex.ui.player.Panels
import app.marlboroadvance.mpvex.ui.player.controls.components.panels.AudioDelayPanel
import app.marlboroadvance.mpvex.ui.player.controls.components.panels.SubtitleDelayPanel
import app.marlboroadvance.mpvex.ui.player.controls.components.panels.SubtitleSettingsPanel
import app.marlboroadvance.mpvex.ui.player.controls.components.panels.VideoSettingsPanel

// --- NEW LIQUID IMPORT ---
import app.marlboroadvance.mpvex.ui.components.liquid.LocalLiquidBackdrop

@Composable
fun PlayerPanels(
  panelShown: Panels,
  onDismissRequest: () -> Unit,
  modifier: Modifier = Modifier,
) {
  AnimatedContent(
    targetState = panelShown,
    label = "panels",
    contentAlignment = Alignment.CenterEnd,
    contentKey = { it.name },
    transitionSpec = {
      fadeIn() + slideInHorizontally { it / 3 } togetherWith fadeOut() + slideOutHorizontally { it / 2 }
    },
    modifier = modifier,
  ) { currentPanel ->
    when (currentPanel) {
      Panels.None -> {
        Box(Modifier.fillMaxHeight())
      }
      Panels.SubtitleSettings -> {
        SubtitleSettingsPanel(onDismissRequest)
      }
      Panels.SubtitleDelay -> {
        SubtitleDelayPanel(onDismissRequest)
      }
      Panels.AudioDelay -> {
        AudioDelayPanel(onDismissRequest)
      }
      Panels.VideoFilters -> {
        VideoSettingsPanel(onDismissRequest)
      }
    }
  }
}

val CARDS_MAX_WIDTH = 420.dp

val panelCardsColors: @Composable () -> CardColors = {
  val backdrop = LocalLiquidBackdrop.current

  if (backdrop != null) {
    // LIQUID UI MODE: Nested Glass Effect!
    // We use a highly transparent white so the parent panel's blur shines right through!
    CardDefaults.cardColors(
      containerColor = Color.White.copy(alpha = 0.15f),
      contentColor = Color.White,
      disabledContainerColor = Color.White.copy(alpha = 0.05f),
      disabledContentColor = Color.White.copy(alpha = 0.38f),
    )
  } else {
    // STANDARD MODE: The original solid grey colors
    val alpha = 0.85f
    CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = alpha),
      contentColor = MaterialTheme.colorScheme.onSurface,
      disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = alpha),
      disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
    )
  }
}

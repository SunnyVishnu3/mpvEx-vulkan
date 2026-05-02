package app.marlboroadvance.mpvex.ui.player.controls.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.marlboroadvance.mpvex.ui.player.controls.LocalPlayerButtonsClickEvent
import app.marlboroadvance.mpvex.ui.theme.spacing

// --- NEW LIQUID IMPORTS ---
import app.marlboroadvance.mpvex.ui.components.liquid.LocalLiquidBackdrop
import app.marlboroadvance.mpvex.ui.components.liquid.TransparentLiquidButton

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ControlsButton(
  icon: ImageVector,
  modifier: Modifier = Modifier,
  color: Color? = null,
  title: String? = null,
  hideBackground: Boolean = false,
  onClick: () -> Unit,
  onLongClick: (() -> Unit)? = null,
) {
  val clickEvent = LocalPlayerButtonsClickEvent.current
  val interactionSource = remember { MutableInteractionSource() }
  
  // 1. TUNE INTO THE BROADCAST TOWER!
  val backdrop = LocalLiquidBackdrop.current

  // 2. IF LIQUID UI IS ON, DRAW THE GLASS BUTTON!
  if (backdrop != null && !hideBackground) {
      TransparentLiquidButton(
          backdrop = backdrop,
          modifier = modifier.size(40.dp), // Match standard sizing
          onClick = {
              clickEvent()
              onClick()
          },
          onLongClick = onLongClick
      ) {
          Icon(
              imageVector = icon,
              contentDescription = title,
              tint = color ?: Color.White, // Glass buttons look best with white icons
              modifier = Modifier
                  .padding(MaterialTheme.spacing.small)
                  .size(20.dp),
          )
      }
  } else {
      // 3. OTHERWISE, FALLBACK TO THE STANDARD BUTTON
      Surface(
        modifier =
          modifier
            .clip(CircleShape)
            .combinedClickable(
              onClick = {
                clickEvent()
                onClick()
              },
              onLongClick = onLongClick,
              interactionSource = interactionSource,
              indication = ripple(),
            ),
        shape = CircleShape,
        color = if (hideBackground) Color.Transparent else MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f),
        contentColor = color ?: MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border =
          if (hideBackground) {
            null
          } else {
            BorderStroke(
              1.dp,
              MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
            )
          },
      ) {
        Icon(
          imageVector = icon,
          contentDescription = title,
          tint = color ?: MaterialTheme.colorScheme.onSurface,
          modifier =
            Modifier
              .padding(MaterialTheme.spacing.small)
              .size(20.dp),
        )
      }
  }
}

@Composable
fun ControlsGroup(
  modifier: Modifier = Modifier,
  content: @Composable RowScope.() -> Unit,
) {
  val spacing = MaterialTheme.spacing

  Row(
    modifier = modifier,
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement =
      androidx.compose.foundation.layout.Arrangement
        .spacedBy(spacing.extraSmall),
    content = content,
  )
}

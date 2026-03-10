package app.marlboroadvance.mpvex.ui.player.controls.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.marlboroadvance.mpvex.ui.theme.spacing
import dev.vivvvek.seeker.Segment
import `is`.xyz.mpv.Utils
import app.marlboroadvance.mpvex.ui.components.liquid.LocalLiquidBackdrop
import app.marlboroadvance.mpvex.ui.components.liquid.LiquidGlassSurface
import app.marlboroadvance.mpvex.preferences.LiquidTarget

@Composable
fun CurrentChapter(
  chapter: Segment,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val backdrop = LocalLiquidBackdrop.current

  if (backdrop != null) {
      LiquidGlassSurface(
          backdrop = backdrop,
          target = LiquidTarget.BUTTON,
          shape = RoundedCornerShape(100.dp),
          modifier = modifier
            .clip(RoundedCornerShape(100.dp))
            .clickable(onClick = onClick)
      ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = MaterialTheme.spacing.medium, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
          ) {
            Icon(
              imageVector = Icons.Default.Bookmarks,
              contentDescription = "Chapter",
              tint = Color.White,
              modifier = Modifier.size(16.dp),
            )
            AnimatedContent(
              targetState = chapter,
              transitionSpec = {
                if (targetState.start > initialState.start) {
                  (slideInVertically { height -> height } + fadeIn())
                    .togetherWith(slideOutVertically { height -> -height } + fadeOut())
                } else {
                  (slideInVertically { height -> -height } + fadeIn())
                    .togetherWith(slideOutVertically { height -> height } + fadeOut())
                }.using(SizeTransform(clip = false))
              },
              label = "Chapter",
            ) { currentChapter ->
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
              ) {
                Text(
                  text = Utils.prettyTime(currentChapter.start.toInt()),
                  fontWeight = FontWeight.Bold,
                  style = MaterialTheme.typography.bodyMedium,
                  maxLines = 1,
                  overflow = TextOverflow.Clip,
                  color = MaterialTheme.colorScheme.primary,
                )
                currentChapter.name.let {
                  Text(
                    text = "•",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    color = Color.White,
                    overflow = TextOverflow.Clip,
                  )
                  Text(
                    text = it,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    modifier = Modifier.basicMarquee(),
                  )
                }
              }
            }
          }
      }
  } else {
      Surface(
        modifier = modifier
          .clip(RoundedCornerShape(100.dp))
          .clickable(onClick = onClick),
        shape = RoundedCornerShape(100.dp),
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier.padding(horizontal = MaterialTheme.spacing.medium, vertical = 6.dp),
          horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        ) {
          Icon(
            imageVector = Icons.Default.Bookmarks,
            contentDescription = "Chapter",
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(16.dp),
          )
          AnimatedContent(
            targetState = chapter,
            transitionSpec = {
              if (targetState.start > initialState.start) {
                (slideInVertically { height -> height } + fadeIn())
                  .togetherWith(slideOutVertically { height -> -height } + fadeOut())
              } else {
                (slideInVertically { height -> -height } + fadeIn())
                  .togetherWith(slideOutVertically { height -> height } + fadeOut())
              }.using(SizeTransform(clip = false))
            },
            label = "Chapter",
          ) { currentChapter ->
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
            ) {
              Text(
                text = Utils.prettyTime(currentChapter.start.toInt()),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                color = MaterialTheme.colorScheme.primary,
              )
              currentChapter.name.let {
                Text(
                  text = "•",
                  textAlign = TextAlign.Center,
                  style = MaterialTheme.typography.bodyMedium,
                  maxLines = 1,
                  color = MaterialTheme.colorScheme.onSurface,
                  overflow = TextOverflow.Clip,
                )
                Text(
                  text = it,
                  textAlign = TextAlign.Center,
                  style = MaterialTheme.typography.bodyMedium,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  fontWeight = FontWeight.ExtraBold,
                  color = MaterialTheme.colorScheme.onSurface,
                  modifier = Modifier.basicMarquee(),
                )
              }
            }
          }
        }
      }
  }
}

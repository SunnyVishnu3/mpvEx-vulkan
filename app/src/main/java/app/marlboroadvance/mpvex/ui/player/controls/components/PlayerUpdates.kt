package app.marlboroadvance.mpvex.ui.player.controls.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DoubleArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.marlboroadvance.mpvex.R
import app.marlboroadvance.mpvex.ui.theme.spacing
import app.marlboroadvance.mpvex.ui.components.liquid.LocalLiquidBackdrop
import app.marlboroadvance.mpvex.ui.components.liquid.LiquidGlassSurface
import app.marlboroadvance.mpvex.preferences.LiquidTarget

@Composable
fun PlayerUpdate(
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  val backdrop = LocalLiquidBackdrop.current
  if (backdrop != null) {
      LiquidGlassSurface(
          backdrop = backdrop,
          target = LiquidTarget.BUTTON,
          shape = CircleShape,
          modifier = modifier
      ) {
        Box(
          modifier = Modifier
            .padding(vertical = 4.dp, horizontal = 12.dp)
            .height(24.dp)
            .widthIn(min = 24.dp),
          contentAlignment = Alignment.Center,
        ) {
          content()
        }
      }
  } else {
      Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
      ) {
        Box(
          modifier = Modifier
            .padding(vertical = 4.dp, horizontal = 12.dp)
            .height(24.dp)
            .widthIn(min = 24.dp),
          contentAlignment = Alignment.Center,
        ) {
          content()
        }
      }
  }
}


@Composable
fun TextPlayerUpdate(
  text: String,
  modifier: Modifier = Modifier,
) {
  val backdrop = LocalLiquidBackdrop.current
  PlayerUpdate(modifier) {
    Text(
      text = text,
      fontFamily = FontFamily.Monospace,
      fontWeight = FontWeight.Bold,
      textAlign = TextAlign.Center,
      color = if (backdrop != null) Color.White else MaterialTheme.colorScheme.onSurface,
      style = MaterialTheme.typography.bodyMedium,
    )
  }
}

@Composable
fun MultipleSpeedPlayerUpdate(
  currentSpeed: Float,
  modifier: Modifier = Modifier,
) {
  CompactSpeedIndicator(currentSpeed = currentSpeed, modifier = modifier)
}

@Composable
fun SeekPlayerUpdate(
  currentTime: String,
  seekDelta: String,
  modifier: Modifier = Modifier,
) {
  val backdrop = LocalLiquidBackdrop.current
  PlayerUpdate(modifier) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = currentTime,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        color = if (backdrop != null) Color.White else MaterialTheme.colorScheme.onSurface,
      )
      
      Text(
        text = " $seekDelta",
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.bodyMedium,
        color = if (backdrop != null) Color.White else MaterialTheme.colorScheme.primary,
      )
    }
  }
}

@Composable
fun DoubleTapSeekPlayerUpdate(
  isRight: Boolean,
  seekAmount: Int,
  seekText: String?,
  modifier: Modifier = Modifier,
) {
  val backdrop = LocalLiquidBackdrop.current
  val directionText = if (isRight) "Forward" else "Rewind"
  PlayerUpdate(modifier.animateContentSize()) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
    ) {
      if (!isRight) {
        Icon(
          imageVector = Icons.Default.DoubleArrow,
          contentDescription = null,
          tint = if (backdrop != null) Color.White else MaterialTheme.colorScheme.onSurface,
          modifier =
            Modifier
              .clip(CircleShape)
              .background(if (backdrop != null) Color.White.copy(alpha = 0.2f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
              .padding(4.dp)
              .rotate(180f),
        )
      }

      Text(
        text = seekText ?: "$directionText $seekAmount seconds",
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        color = if (backdrop != null) Color.White else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(horizontal = 8.dp),
      )

      if (isRight) {
        Icon(
          imageVector = Icons.Default.DoubleArrow,
          contentDescription = null,
          tint = if (backdrop != null) Color.White else MaterialTheme.colorScheme.onSurface,
          modifier =
            Modifier
              .clip(CircleShape)
              .background(if (backdrop != null) Color.White.copy(alpha = 0.2f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
              .padding(4.dp),
        )
      }
    }
  }
}

package app.marlboroadvance.mpvex.ui.player.controls.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.marlboroadvance.mpvex.ui.theme.spacing
import kotlinx.coroutines.delay
import app.marlboroadvance.mpvex.ui.components.liquid.LocalLiquidBackdrop
import app.marlboroadvance.mpvex.ui.components.liquid.LiquidGlassSurface
import app.marlboroadvance.mpvex.preferences.LiquidTarget

@Composable
fun CompactSpeedIndicator(
  currentSpeed: Float,
  modifier: Modifier = Modifier,
) {
  val backdrop = LocalLiquidBackdrop.current

  if (backdrop != null) {
      LiquidGlassSurface(
          backdrop = backdrop,
          target = LiquidTarget.BUTTON,
          shape = RoundedCornerShape(100.dp),
          modifier = modifier
      ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = MaterialTheme.spacing.medium, vertical = MaterialTheme.spacing.small)
          ) {
            Icon(
              imageVector = Icons.Filled.FastForward,
              contentDescription = null,
              modifier = Modifier.size(16.dp),
              tint = Color.White 
            )
            Text(
              text = "${currentSpeed.format()}x",
              fontSize = 14.sp,
              fontWeight = FontWeight.Bold,
              style = MaterialTheme.typography.bodyLarge,
              modifier = Modifier.padding(start = 4.dp),
              color = Color.White 
            )
          }
      }
  } else {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier
          .background(
            color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f),
            shape = RoundedCornerShape(100.dp)
          )
          .border(
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
            shape = RoundedCornerShape(100.dp)
          )
          .padding(horizontal = MaterialTheme.spacing.medium, vertical = MaterialTheme.spacing.small)
      ) {
        Icon(
          imageVector = Icons.Filled.FastForward,
          contentDescription = null,
          modifier = Modifier.size(16.dp),
          tint = MaterialTheme.colorScheme.onSurface 
        )
        Text(
          text = "${currentSpeed.format()}x",
          fontSize = 14.sp,
          fontWeight = FontWeight.Bold,
          style = MaterialTheme.typography.bodyLarge,
          modifier = Modifier.padding(start = 4.dp),
          color = MaterialTheme.colorScheme.onSurface
        )
      }
  }
}

private fun Float.format(): String {
  return when {
    this % 1.0f == 0.0f -> String.format("%.0f", this)
    this % 0.5f == 0.0f -> String.format("%.1f", this)
    else -> String.format("%.2f", this)
  }
}

@Composable
fun SpeedControlSlider(
  currentSpeed: Float,
  speedPresets: List<Float> = listOf(0.25f, 0.5f, 1.0f, 1.25f, 1.5f, 2.0f, 3.0f, 4.0f),
  modifier: Modifier = Modifier,
) {
  var isExpanded by remember { mutableStateOf(false) }

  LaunchedEffect(currentSpeed) {
    if (!isExpanded) {
      isExpanded = true
    }
    delay(1500)
    isExpanded = false
  }

  Box(
    modifier = modifier.height(36.dp),
    contentAlignment = Alignment.Center
  ) {
    AnimatedVisibility(
      visible = isExpanded,
      enter = fadeIn() + expandHorizontally(
        expandFrom = Alignment.CenterHorizontally,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
      ),
      exit = fadeOut(animationSpec = tween(300)) + shrinkHorizontally(
        shrinkTowards = Alignment.CenterHorizontally,
        animationSpec = tween(300, delayMillis = 100)
      )
    ) {
      val primaryColor = MaterialTheme.colorScheme.primary
      val onSurfaceColor = MaterialTheme.colorScheme.onSurface
      
      Row(
        modifier = Modifier
          .background(
            color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.85f),
            shape = RoundedCornerShape(100.dp)
          )
          .border(
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
            shape = RoundedCornerShape(100.dp)
          )
          .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        Canvas(modifier = Modifier.width(100.dp).height(24.dp)) {
          val trackWidth = size.width
          val trackHeight = 4.dp.toPx()
          val centerY = size.height / 2
          val segmentWidth = trackWidth / (speedPresets.size - 1)

          drawLine(
            color = onSurfaceColor.copy(alpha = 0.35f),
            start = Offset(0f, centerY),
            end = Offset(trackWidth, centerY),
            strokeWidth = trackHeight,
            cap = StrokeCap.Round,
          )

          val progressX = (currentSpeed - speedPresets.first()) / (speedPresets.last() - speedPresets.first()) * trackWidth
          
          drawLine(
            color = primaryColor,
            start = Offset(0f, centerY),
            end = Offset(progressX.coerceIn(0f, trackWidth), centerY),
            strokeWidth = trackHeight,
            cap = StrokeCap.Round,
          )

          drawCircle(
            color = primaryColor,
            radius = 6.dp.toPx(),
            center = Offset(progressX.coerceIn(0f, trackWidth), centerY)
          )
        }
        
        Text(
          text = "${currentSpeed.format()}x",
          fontSize = 14.sp,
          fontWeight = FontWeight.Bold,
          style = MaterialTheme.typography.bodyLarge,
          color = MaterialTheme.colorScheme.onSurface
        )
      }
    }
  }
}

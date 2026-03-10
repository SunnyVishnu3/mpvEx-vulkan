package app.marlboroadvance.mpvex.ui.player.controls.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import app.marlboroadvance.mpvex.ui.components.liquid.LocalLiquidBackdrop
import app.marlboroadvance.mpvex.ui.components.liquid.LiquidGlassSurface
import app.marlboroadvance.mpvex.preferences.LiquidTarget

@Composable
fun SlideToUnlock(
  onUnlock: () -> Unit,
  modifier: Modifier = Modifier,
  onDraggingChanged: (Boolean) -> Unit = {},
) {
  val maxOffset = with(LocalDensity.current) { (200.dp - 64.dp).toPx() }
  val offsetX = remember { Animatable(0f) }
  val coroutineScope = rememberCoroutineScope()
  var isDragging by remember { mutableStateOf(false) }

  val showUnlockIcon = offsetX.value > maxOffset * 0.8f

  Box(
    modifier = modifier.width(200.dp).height(64.dp),
    contentAlignment = Alignment.CenterStart,
  ) {
    val backdrop = LocalLiquidBackdrop.current
    if (backdrop != null) {
      LiquidGlassSurface(
        modifier = Modifier.width(200.dp).height(64.dp),
        backdrop = backdrop,
        target = LiquidTarget.BUTTON,
        shape = RoundedCornerShape(100.dp),
      ) {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .background(
              MaterialTheme.colorScheme.primary.copy(
                alpha = (offsetX.value / maxOffset).coerceIn(0f, 0.5f)
              )
            )
        )
      }
    } else {
      Surface(
        modifier = Modifier.width(200.dp).height(64.dp).alpha(0.5f),
        shape = RoundedCornerShape(100.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
      ) {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .background(
              MaterialTheme.colorScheme.primary.copy(
                alpha = (offsetX.value / maxOffset).coerceIn(0f, 0.5f)
              )
            )
        )
      }
    }

    Row(
      modifier = Modifier.fillMaxSize(),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Spacer(modifier = Modifier.width(72.dp))
      Text(
        text = "Slide to unlock",
        style = MaterialTheme.typography.bodyMedium,
        color = if (backdrop != null) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        modifier = Modifier.alpha(1f - (offsetX.value / (maxOffset * 0.5f)).coerceIn(0f, 1f)),
      )
    }

    Box(
      modifier = Modifier
        .offset { IntOffset(offsetX.value.roundToInt(), 0) }
        .padding(4.dp)
        .size(56.dp)
        .clip(CircleShape)
        .background(MaterialTheme.colorScheme.primary)
        .pointerInput(Unit) {
          detectHorizontalDragGestures(
            onDragStart = {
              isDragging = true
              onDraggingChanged(true)
            },
            onDragEnd = {
              isDragging = false
              onDraggingChanged(false)
              if (offsetX.value >= maxOffset * 0.9f) {
                onUnlock()
              } else {
                coroutineScope.launch {
                  offsetX.animateTo(targetValue = 0f, animationSpec = tween(durationMillis = 300))
                }
              }
            },
            onDragCancel = {
              isDragging = false
              onDraggingChanged(false)
              coroutineScope.launch {
                offsetX.animateTo(targetValue = 0f, animationSpec = tween(durationMillis = 300))
              }
            },
            onHorizontalDrag = { _, dragAmount ->
              coroutineScope.launch {
                val newValue = (offsetX.value + dragAmount).coerceIn(0f, maxOffset)
                offsetX.snapTo(newValue)
              }
            },
          )
        },
      contentAlignment = Alignment.Center,
    ) {
      androidx.compose.animation.Crossfade(targetState = showUnlockIcon, animationSpec = tween(durationMillis = 200)) { showUnlock ->
        Icon(
          imageVector = if (showUnlock) Icons.Filled.LockOpen else Icons.Filled.Lock,
          contentDescription = "Slide to unlock",
          tint = Color.White,
          modifier = Modifier.size(28.dp),
        )
      }
    }
  }
}

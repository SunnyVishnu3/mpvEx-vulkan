package app.marlboroadvance.mpvex.ui.player.controls.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.material3.Slider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Size
import kotlin.math.roundToInt

// --- KYANT BACKDROP 2.0.0-ALPHA03 IMPORTS (FIXED) ---
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
// ----------------------------------------------------

import app.marlboroadvance.mpvex.ui.player.controls.LocalPlayerButtonsClickEvent
import app.marlboroadvance.mpvex.ui.theme.spacing
import app.marlboroadvance.mpvex.preferences.SeekbarStyle
import app.marlboroadvance.mpvex.preferences.LiquidUIPreferences
import dev.vivvvek.seeker.Segment
import `is`.xyz.mpv.Utils
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
fun SeekbarWithTimers(
  position: Float,
  duration: Float,
  onValueChange: (Float) -> Unit,
  onValueChangeFinished: () -> Unit,
  timersInverted: Pair<Boolean, Boolean>,
  positionTimerOnClick: () -> Unit,
  durationTimerOnCLick: () -> Unit,
  chapters: ImmutableList<Segment>,
  paused: Boolean,
  seekbarStyle: SeekbarStyle = SeekbarStyle.Wavy,
  loopStart: Float? = null,
  loopEnd: Float? = null,
  modifier: Modifier = Modifier,
) {
  val clickEvent = LocalPlayerButtonsClickEvent.current
  var isUserInteracting by remember { mutableStateOf(false) }
  var userPosition by remember { mutableFloatStateOf(position) }

  val context = LocalContext.current
  val liquidPrefs = remember { LiquidUIPreferences(context) }
  val isLiquidUI by liquidPrefs.liquidUIEnabledFlow.collectAsState(false)
  val sliderColorLong by liquidPrefs.liquidSliderColorFlow.collectAsState(0xFF2196F3)
  val liquidColor = Color(sliderColorLong)

  val animatedPosition = remember { Animatable(position) }
  val scope = rememberCoroutineScope()

  LaunchedEffect(position, isUserInteracting) {
    if (!isUserInteracting && position != animatedPosition.value) {
      scope.launch {
        animatedPosition.animateTo(
          targetValue = position,
          animationSpec = tween(durationMillis = 200, easing = LinearEasing),
        )
      }
    }
  }

  Row(
    modifier = modifier.height(48.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
  ) {
    VideoTimer(
      value = if (isUserInteracting) userPosition else position,
      timersInverted.first,
      onClick = { clickEvent(); positionTimerOnClick() },
      modifier = Modifier.width(92.dp),
    )

    Box(
      modifier = Modifier.weight(1f).height(48.dp).padding(vertical = 8.dp),
      contentAlignment = Alignment.Center,
    ) {
      Box(
        modifier = Modifier.fillMaxWidth().height(64.dp)
          .pointerInput(Unit) {
            detectTapGestures(
              onTap = { offset ->
                val newPosition = (offset.x / size.width) * duration
                if (!isUserInteracting) isUserInteracting = true
                userPosition = newPosition.coerceIn(0f, duration)
                onValueChange(userPosition)
                scope.launch { 
                  animatedPosition.snapTo(userPosition)
                  isUserInteracting = false
                  onValueChangeFinished()
                }
              }
            )
          }
          .pointerInput(Unit) {
            detectDragGestures(
              onDragStart = { isUserInteracting = true },
              onDragEnd = { 
                scope.launch { 
                  delay(50) 
                  animatedPosition.snapTo(userPosition)
                  isUserInteracting = false
                  onValueChangeFinished()
                }
              },
              onDragCancel = { 
                scope.launch { 
                  delay(50)
                  animatedPosition.snapTo(userPosition)
                  isUserInteracting = false
                  onValueChangeFinished()
                }
              },
            ) { change, _ ->
              change.consume()
              val newPosition = (change.position.x / size.width) * duration
              userPosition = newPosition.coerceIn(0f, duration)
              onValueChange(userPosition)
            }
          }
      )
      
      Box(
        modifier = Modifier.fillMaxWidth().height(32.dp),
        contentAlignment = Alignment.Center,
      ) {
        if (isLiquidUI || seekbarStyle == SeekbarStyle.Liquid) {
            LiquidSeekbar(
                position = if (isUserInteracting) userPosition else animatedPosition.value,
                duration = duration,
                chapters = chapters,
                isScrubbing = isUserInteracting,
                loopStart = loopStart,
                loopEnd = loopEnd,
                liquidColor = liquidColor
            )
        } else {
            when (seekbarStyle) {
                SeekbarStyle.Standard -> StandardSeekbar(
                    position = if (isUserInteracting) userPosition else animatedPosition.value,
                    duration = duration, chapters = chapters, isPaused = paused, isScrubbing = isUserInteracting,
                    seekbarStyle = SeekbarStyle.Standard, onSeek = { }, onSeekFinished = { }, loopStart = loopStart, loopEnd = loopEnd,
                )
                SeekbarStyle.Wavy -> SquigglySeekbar(
                    position = if (isUserInteracting) userPosition else animatedPosition.value,
                    duration = duration, chapters = chapters, isPaused = paused, isScrubbing = isUserInteracting,
                    useWavySeekbar = true, seekbarStyle = SeekbarStyle.Wavy, onSeek = { }, onSeekFinished = { }, loopStart = loopStart, loopEnd = loopEnd,
                )
                SeekbarStyle.Thick -> StandardSeekbar(
                    position = if (isUserInteracting) userPosition else animatedPosition.value,
                    duration = duration, chapters = chapters, isPaused = paused, isScrubbing = isUserInteracting,
                    seekbarStyle = SeekbarStyle.Thick, onSeek = { }, onSeekFinished = { }, loopStart = loopStart, loopEnd = loopEnd,
                )
                else -> {}
            }
        }
      }
    }

    VideoTimer(
      value = if (timersInverted.second) position - duration else duration,
      isInverted = timersInverted.second,
      onClick = { clickEvent(); durationTimerOnCLick() },
      modifier = Modifier.width(92.dp),
    )
  }
}

// =========================================================================
// NEW: LIQUID SEEKBAR (Kyant Lens & Fade Mechanics)
// =========================================================================
@Composable
fun LiquidSeekbar(
    position: Float,
    duration: Float,
    chapters: ImmutableList<Segment>,
    isScrubbing: Boolean = false,
    loopStart: Float? = null,
    loopEnd: Float? = null,
    liquidColor: Color = Color.Unspecified,
    modifier: Modifier = Modifier,
) {
    val activeColor = if (liquidColor.isSpecified) liquidColor else MaterialTheme.colorScheme.primary
    
    // Retained the pressProgress variable in case you want to animate the width/height later
    val pressProgress by animateFloatAsState(
        targetValue = if (isScrubbing) 0f else 0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 800f),
        label = "pressProgress"
    )

    // 2. Make the thumb size 56.dp, 32.dp (this naturally forms a pill with CircleShape)
    val thumbWidth = androidx.compose.ui.unit.lerp(56.dp, pressProgress)
    val thumbHeight = 32.dp
    
    val trackBackdrop = rememberLayerBackdrop { drawContent() }

    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        val trackWidthPx = constraints.maxWidth.toFloat()
        val progress = if (duration > 0f) (position / duration).coerceIn(0f, 1f) else 0f
        val playedPx = trackWidthPx * progress
        
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp) // Perfect Kyant track thickness
                .clip(CircleShape) // Perfectly round track edges
                .layerBackdrop(trackBackdrop) // Feed track colors to the glass thumb
        ) {
            val chapterGapHalf = 1.dp.toPx()
            val chapterGaps = chapters
                .map { (it.start / duration).coerceIn(0f, 1f) * size.width }
                .filter { it > 0f && it < size.width }
                .map { x -> (x - chapterGapHalf) to (x + chapterGapHalf) }
            
            fun drawRangeWithGaps(rangeStart: Float, rangeEnd: Float, gaps: List<Pair<Float, Float>>, color: Color) {
                if (rangeEnd <= rangeStart) return
                val relevantGaps = gaps.filter { (gStart, gEnd) -> gEnd > rangeStart && gStart < rangeEnd }.sortedBy { it.first }
                var currentPos = rangeStart
                for ((gStart, gEnd) in relevantGaps) {
                    val segmentEnd = gStart.coerceAtMost(rangeEnd)
                    if (segmentEnd > currentPos) {
                        drawRect(color, topLeft = Offset(currentPos, 0f), size = Size(segmentEnd - currentPos, size.height))
                    }
                    currentPos = gEnd.coerceAtLeast(currentPos)
                }
                if (currentPos < rangeEnd) {
                    drawRect(color, topLeft = Offset(currentPos, 0f), size = Size(rangeEnd - currentPos, size.height))
                }
            }
            
            // 1. Draw entire track (Unplayed) at 30% alpha of your custom color
            drawRangeWithGaps(0f, size.width, chapterGaps, activeColor.copy(alpha = 0.3f))
            
            // 2. Overwrite the played portion with 100% of your custom color
            if (playedPx > 0) {
                drawRangeWithGaps(0f, playedPx, chapterGaps, activeColor)
            }

            if (loopStart != null || loopEnd != null) {
                val loopColor = Color(0xFFFFB300)
                val markerWidth = 2.dp.toPx()
                if (loopStart != null) drawLine(color = loopColor, start = Offset((loopStart / duration).coerceIn(0f, 1f) * size.width, 0f), end = Offset((loopStart / duration).coerceIn(0f, 1f) * size.width, size.height), strokeWidth = markerWidth)
                if (loopEnd != null) drawLine(color = loopColor, start = Offset((loopEnd / duration).coerceIn(0f, 1f) * size.width, 0f), end = Offset((loopEnd / duration).coerceIn(0f, 1f) * size.width, size.height), strokeWidth = markerWidth)
                if (loopStart != null && loopEnd != null) drawRect(color = loopColor.copy(alpha = 0.3f), topLeft = Offset((minOf(loopStart, loopEnd) / duration).coerceIn(0f, 1f) * size.width, 0f), size = Size((maxOf(loopStart, loopEnd) / duration).coerceIn(0f, 1f) * size.width - (minOf(loopStart, loopEnd) / duration).coerceIn(0f, 1f) * size.width, size.height))
            }
        }
        
        Box(
            modifier = Modifier
                .offset { androidx.compose.ui.unit.IntOffset((playedPx - (thumbWidth.toPx() / 2)).roundToInt(), 0) }
                .width(thumbWidth)
                .height(thumbHeight)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { CircleShape }, // Perfectly rounded pill!
                    effects = {
                        vibrancy()
                         // Blur fades away as you press it
                        blur(8f.dp.toPx() * (0f - pressProgress))

                        // 3 & 4. Lens effects exactly as requested
                        lens(
                            30f.dp.toPx() * pressProgress,
                            20f.dp.toPx() * pressProgress
                            depthEffect = true
                        )
                    },
                    onDrawSurface = {
                        // 5. Glass opacity: 0 (Pure transparent glass)
                        drawRect(Color.White.copy(alpha = 1f - pressProgress))
                    }
                )
        )
    }
}


// =========================================================================
// ORIGINAL MPVEX SEEKBARS
// =========================================================================

@Composable
private fun SquigglySeekbar(
  position: Float, duration: Float, chapters: ImmutableList<Segment>, isPaused: Boolean, isScrubbing: Boolean,
  useWavySeekbar: Boolean, seekbarStyle: SeekbarStyle, onSeek: (Float) -> Unit, onSeekFinished: () -> Unit,
  loopStart: Float? = null, loopEnd: Float? = null, modifier: Modifier = Modifier,
) {
  val activeColor = MaterialTheme.colorScheme.primary
  val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
  var phaseOffset by remember { mutableFloatStateOf(0f) }
  var heightFraction by remember { mutableFloatStateOf(1f) }
  val scope = rememberCoroutineScope()

  val waveLength = 80f
  val lineAmplitude = if (useWavySeekbar) 6f else 0f
  val phaseSpeed = 10f
  val transitionPeriods = 1.5f
  val minWaveEndpoint = 0f
  val matchedWaveEndpoint = 1f
  val transitionEnabled = true

  LaunchedEffect(isPaused, isScrubbing, useWavySeekbar) {
    if (!useWavySeekbar) { heightFraction = 0f; return@LaunchedEffect }
    scope.launch {
      val shouldFlatten = isPaused || isScrubbing
      kotlinx.coroutines.delay(if (shouldFlatten) 0L else 60L)
      Animatable(heightFraction).animateTo(
        targetValue = if (shouldFlatten) 0f else 1f,
        animationSpec = tween(durationMillis = if (shouldFlatten) 550 else 800, easing = LinearEasing),
      ) { heightFraction = value }
    }
  }

  LaunchedEffect(isPaused, useWavySeekbar) {
    if (isPaused || !useWavySeekbar) return@LaunchedEffect
    var lastFrameTime = withFrameMillis { it }
    while (isActive) {
      withFrameMillis { frameTimeMillis ->
        phaseOffset = (phaseOffset + (frameTimeMillis - lastFrameTime) / 1000f * phaseSpeed) % waveLength
        lastFrameTime = frameTimeMillis
      }
    }
  }

  Canvas(modifier = modifier.fillMaxWidth().height(48.dp)) {
    val strokeWidth = 5.dp.toPx()
    val progress = if (duration > 0f) (position / duration).coerceIn(0f, 1f) else 0f
    val totalWidth = size.width
    val totalProgressPx = totalWidth * progress
    val centerY = size.height / 2f
    val waveProgressPx = if (!transitionEnabled || progress > matchedWaveEndpoint) totalWidth * progress else totalWidth * (minWaveEndpoint + (matchedWaveEndpoint - minWaveEndpoint) * (progress / matchedWaveEndpoint).coerceIn(0f, 1f))

    fun computeAmplitude(x: Float, sign: Float): Float = if (transitionEnabled) sign * heightFraction * lineAmplitude * (((waveProgressPx + transitionPeriods * waveLength / 2f - x) / (transitionPeriods * waveLength)).coerceIn(0f, 1f)) else sign * heightFraction * lineAmplitude

    val path = Path()
    val waveStart = -phaseOffset - waveLength / 2f
    val waveEnd = if (transitionEnabled) totalWidth else waveProgressPx
    path.moveTo(waveStart, centerY)
    var currentX = waveStart
    var waveSign = 1f
    var currentAmp = computeAmplitude(currentX, waveSign)
    val dist = waveLength / 2f

    while (currentX < waveEnd) {
      waveSign = -waveSign
      val nextX = currentX + dist
      val midX = currentX + dist / 2f
      val nextAmp = computeAmplitude(nextX, waveSign)
      path.cubicTo(midX, centerY + currentAmp, midX, centerY + nextAmp, nextX, centerY + nextAmp)
      currentAmp = nextAmp
      currentX = nextX
    }

    val clipTop = lineAmplitude + strokeWidth
    val gapHalf = 1.dp.toPx()

    fun drawPathWithGaps(startX: Float, endX: Float, color: Color) {
      if (endX <= startX) return
      if (duration <= 0f) {
        clipRect(left = startX, top = centerY - clipTop, right = endX, bottom = centerY + clipTop) { drawPath(path = path, color = color, style = Stroke(width = strokeWidth, cap = StrokeCap.Round)) }
        return
      }
      val gaps = chapters.map { (it.start / duration).coerceIn(0f, 1f) * totalWidth }.filter { it in startX..endX }.sorted().map { x -> (x - gapHalf).coerceAtLeast(startX) to (x + gapHalf).coerceAtMost(endX) }
      var segmentStart = startX
      for ((gapStart, gapEnd) in gaps) {
        if (gapStart > segmentStart) clipRect(left = segmentStart, top = centerY - clipTop, right = gapStart, bottom = centerY + clipTop) { drawPath(path = path, color = color, style = Stroke(width = strokeWidth, cap = StrokeCap.Round)) }
        segmentStart = gapEnd
      }
      if (segmentStart < endX) clipRect(left = segmentStart, top = centerY - clipTop, right = endX, bottom = centerY + clipTop) { drawPath(path = path, color = color, style = Stroke(width = strokeWidth, cap = StrokeCap.Round)) }
    }

    drawPathWithGaps(0f, totalProgressPx, activeColor)
    if (transitionEnabled) drawPathWithGaps(totalProgressPx, totalWidth, activeColor.copy(alpha = 77f / 255f)) else drawLine(color = surfaceVariant.copy(alpha = 0.4f), start = Offset(totalProgressPx, centerY), end = Offset(totalWidth, centerY), strokeWidth = strokeWidth, cap = StrokeCap.Round)
    
    val startAmp = kotlin.math.cos(kotlin.math.abs(waveStart) / waveLength * (2f * kotlin.math.PI.toFloat()))
    drawCircle(color = activeColor, radius = strokeWidth / 2f, center = Offset(0f, centerY + startAmp * lineAmplitude * heightFraction))

    val barHalfHeight = (lineAmplitude + strokeWidth)
    if (barHalfHeight > 0.5f) drawLine(color = activeColor, start = Offset(totalProgressPx, centerY - barHalfHeight), end = Offset(totalProgressPx, centerY + barHalfHeight), strokeWidth = 5.dp.toPx(), cap = StrokeCap.Round)

    if (loopStart != null || loopEnd != null) {
      val loopColor = Color(0xFFFFB300)
      val markerWidth = 2.dp.toPx()
      if (loopStart != null && duration > 0f) drawLine(color = loopColor, start = Offset((loopStart / duration).coerceIn(0f, 1f) * totalWidth, centerY - lineAmplitude - strokeWidth), end = Offset((loopStart / duration).coerceIn(0f, 1f) * totalWidth, centerY + lineAmplitude + strokeWidth), strokeWidth = markerWidth)
      if (loopEnd != null && duration > 0f) drawLine(color = loopColor, start = Offset((loopEnd / duration).coerceIn(0f, 1f) * totalWidth, centerY - lineAmplitude - strokeWidth), end = Offset((loopEnd / duration).coerceIn(0f, 1f) * totalWidth, centerY + lineAmplitude + strokeWidth), strokeWidth = markerWidth)
      if (loopStart != null && loopEnd != null && duration > 0f) drawRect(color = loopColor.copy(alpha = 0.2f), topLeft = Offset((minOf(loopStart, loopEnd) / duration).coerceIn(0f, 1f) * totalWidth, centerY - lineAmplitude - strokeWidth), size = Size((maxOf(loopStart, loopEnd) / duration).coerceIn(0f, 1f) * totalWidth - (minOf(loopStart, loopEnd) / duration).coerceIn(0f, 1f) * totalWidth, (lineAmplitude + strokeWidth) * 2))
    }
  }
}

@Composable
fun VideoTimer(value: Float, isInverted: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
  val interactionSource = remember { MutableInteractionSource() }
  Text(
    modifier = modifier.fillMaxHeight().clickable(interactionSource = interactionSource, indication = ripple(), onClick = onClick).wrapContentHeight(Alignment.CenterVertically),
    text = Utils.prettyTime(value.toInt(), isInverted), color = Color.White, textAlign = TextAlign.Center,
  )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun StandardSeekbar(
    position: Float, duration: Float, chapters: ImmutableList<Segment>, isPaused: Boolean = false, isScrubbing: Boolean = false,
    seekbarStyle: SeekbarStyle = SeekbarStyle.Standard, onSeek: (Float) -> Unit, onSeekFinished: () -> Unit,
    loopStart: Float? = null, loopEnd: Float? = null, modifier: Modifier = Modifier,
) {
    val activeColor = MaterialTheme.colorScheme.primary
    val interactionSource = remember { MutableInteractionSource() }
    var heightFraction by remember { mutableFloatStateOf(1f) }
    val scope = rememberCoroutineScope()
    
    LaunchedEffect(isPaused, isScrubbing) {
        scope.launch {
            val shouldFlatten = isPaused || isScrubbing
            kotlinx.coroutines.delay(if (shouldFlatten) 0L else 60L)
            Animatable(heightFraction).animateTo(targetValue = if (shouldFlatten) 0.7f else 1f, animationSpec = tween(durationMillis = if (shouldFlatten) 550 else 800, easing = LinearEasing)) { heightFraction = value }
        }
    }
    
    val isThick = seekbarStyle == SeekbarStyle.Thick
    val baseTrackHeight = if (isThick) 16.dp else 8.dp
    val trackHeightDp = baseTrackHeight * heightFraction
    val thumbWidth = 6.dp
    val thumbHeight = if (isThick) 16.dp else 24.dp
    val thumbShape = if (isThick) RoundedCornerShape(thumbWidth / 2) else CircleShape

    Slider(
        value = position, onValueChange = onSeek, onValueChangeFinished = onSeekFinished, valueRange = 0f..duration.coerceAtLeast(0.1f),
        modifier = Modifier.fillMaxWidth(), interactionSource = interactionSource,
        track = { sliderState ->
            Canvas(modifier = Modifier.fillMaxWidth().height(trackHeightDp)) {
                val min = sliderState.valueRange.start
                val max = sliderState.valueRange.endInclusive
                val range = (max - min).takeIf { it > 0f } ?: 1f
                val playedFraction = ((sliderState.value - min) / range).coerceIn(0f, 1f)
                val playedPx = size.width * playedFraction
                val trackHeight = size.height
                
                val outerRadius = trackHeight / 2f
                val innerRadius = if (isThick) outerRadius else 2.dp.toPx()
                val gapHalf = 14.dp.toPx() / 2f
                val chapterGapHalf = 1.dp.toPx()
                
                val thumbGapStart = (playedPx - gapHalf).coerceIn(0f, size.width)
                val thumbGapEnd = (playedPx + gapHalf).coerceIn(0f, size.width)
                val chapterGaps = chapters.map { (it.start / duration).coerceIn(0f, 1f) * size.width }.filter { it > 0f && it < size.width }.map { x -> (x - chapterGapHalf) to (x + chapterGapHalf) }
                
                fun drawSegment(startX: Float, endX: Float, color: Color) {
                    if (endX - startX < 0.5f) return
                    val path = Path()
                    val isOuterLeft = startX <= 0.5f
                    val isInnerLeft = kotlin.math.abs(startX - thumbGapEnd) < 0.5f
                    val cornerRadiusLeft = when { isOuterLeft -> androidx.compose.ui.geometry.CornerRadius(outerRadius); isInnerLeft -> androidx.compose.ui.geometry.CornerRadius(innerRadius); else -> androidx.compose.ui.geometry.CornerRadius.Zero }
                    val isOuterRight = endX >= size.width - 0.5f
                    val isInnerRight = kotlin.math.abs(endX - thumbGapStart) < 0.5f
                    val cornerRadiusRight = when { isOuterRight -> androidx.compose.ui.geometry.CornerRadius(outerRadius); isInnerRight -> androidx.compose.ui.geometry.CornerRadius(innerRadius); else -> androidx.compose.ui.geometry.CornerRadius.Zero }
                    path.addRoundRect(androidx.compose.ui.geometry.RoundRect(left = startX, top = 0f, right = endX, bottom = trackHeight, topLeftCornerRadius = cornerRadiusLeft, bottomLeftCornerRadius = cornerRadiusLeft, topRightCornerRadius = cornerRadiusRight, bottomRightCornerRadius = cornerRadiusRight))
                    drawPath(path, color)
                }
                
                fun drawRangeWithGaps(rangeStart: Float, rangeEnd: Float, gaps: List<Pair<Float, Float>>, color: Color) {
                    if (rangeEnd <= rangeStart) return
                    val relevantGaps = gaps.filter { (gStart, gEnd) -> gEnd > rangeStart && gStart < rangeEnd }.sortedBy { it.first }
                    var currentPos = rangeStart
                    for ((gStart, gEnd) in relevantGaps) {
                        val segmentEnd = gStart.coerceAtMost(rangeEnd)
                        if (segmentEnd > currentPos) drawSegment(currentPos, segmentEnd, color)
                        currentPos = gEnd.coerceAtLeast(currentPos)
                    }
                    if (currentPos < rangeEnd) drawSegment(currentPos, rangeEnd, color)
                }
                
                drawRangeWithGaps(thumbGapEnd, size.width, chapterGaps, activeColor.copy(alpha = 0.3f))
                if (thumbGapStart > 0) drawRangeWithGaps(0f, thumbGapStart, chapterGaps, activeColor)

                if (loopStart != null || loopEnd != null) {
                    val loopColor = Color(0xFFFFB300)
                    val markerWidth = 2.dp.toPx()
                    if (loopStart != null) drawLine(color = loopColor, start = Offset((loopStart / duration).coerceIn(0f, 1f) * size.width, 0f), end = Offset((loopStart / duration).coerceIn(0f, 1f) * size.width, size.height), strokeWidth = markerWidth)
                    if (loopEnd != null) drawLine(color = loopColor, start = Offset((loopEnd / duration).coerceIn(0f, 1f) * size.width, 0f), end = Offset((loopEnd / duration).coerceIn(0f, 1f) * size.width, size.height), strokeWidth = markerWidth)
                    if (loopStart != null && loopEnd != null) drawRect(color = loopColor.copy(alpha = 0.3f), topLeft = Offset((minOf(loopStart, loopEnd) / duration).coerceIn(0f, 1f) * size.width, 0f), size = Size((maxOf(loopStart, loopEnd) / duration).coerceIn(0f, 1f) * size.width - (minOf(loopStart, loopEnd) / duration).coerceIn(0f, 1f) * size.width, size.height))
                }
            }
        },
        thumb = { Box(modifier = Modifier.width(thumbWidth).height(thumbHeight).background(activeColor, thumbShape)) }
    )
}

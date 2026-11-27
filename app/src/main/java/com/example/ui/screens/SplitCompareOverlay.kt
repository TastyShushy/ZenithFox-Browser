package com.example.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Interactive physical side-by-side Before / After comparison divider overlaid directly
 * on top of the playing anime video (works in both regular and fullscreen modes).
 *
 * Non-blocking design: ONLY the 56.dp vertical divider rail & center drag knob capture
 * horizontal drag gestures, so the user can still tap play/pause, seek, or use video
 * player controls anywhere else on the screen while comparing live!
 */
@Composable
fun SplitCompareOverlay(
  splitPosition: Float,
  rightSideLabel: String,
  isFullscreen: Boolean,
  onSplitPositionChange: (Float) -> Unit,
  onExitCompare: () -> Unit,
  modifier: Modifier = Modifier
) {
  val density = LocalDensity.current
  val haptics = LocalHapticFeedback.current

  var localPos by remember { mutableFloatStateOf(splitPosition.coerceIn(0.05f, 0.95f)) }
  var isDragging by remember { mutableStateOf(false) }
  var labelsBright by remember { mutableStateOf(true) }
  var lastCenterTickSide by remember { mutableStateOf(if (splitPosition < 0.5f) -1 else 1) }

  // Keep synced if changed externally (e.g. from bottom sheet)
  LaunchedEffect(splitPosition, isDragging) {
    if (!isDragging) {
      localPos = splitPosition.coerceIn(0.05f, 0.95f)
    }
  }

  // Gently soften labels after idle so the anime remains unobstructed
  LaunchedEffect(isDragging, localPos) {
    if (isDragging) {
      labelsBright = true
    } else {
      labelsBright = true
      delay(2800L)
      labelsBright = false
    }
  }

  val labelAlpha by animateFloatAsState(
    targetValue = if (isDragging || labelsBright) 0.94f else 0.42f,
    animationSpec = tween(durationMillis = 260),
    label = "compare_label_alpha"
  )
  val knobScale by animateFloatAsState(
    targetValue = if (isDragging) 1.12f else 1.0f,
    animationSpec = spring(dampingRatio = 0.65f, stiffness = 420f),
    label = "compare_knob_scale"
  )

  BoxWithConstraints(modifier = modifier.fillMaxSize()) {
    val widthPx = constraints.maxWidth.coerceAtLeast(1).toFloat()
    val stripWidthDp = 56.dp
    val stripWidthPx = with(density) { stripWidthDp.toPx() }
    val dividerXPx = (localPos * widthPx).coerceIn(stripWidthPx * 0.35f, widthPx - stripWidthPx * 0.35f)
    val accentColor = MaterialTheme.colorScheme.primary

    // 1. Draggable vertical divider strip + tactile circular grab knob (only this 56.dp strip consumes drags!)
    Box(
      contentAlignment = Alignment.Center,
      modifier = Modifier
        .fillMaxHeight()
        .width(stripWidthDp)
        .offset { IntOffset(x = (dividerXPx - stripWidthPx * 0.5f).roundToInt(), y = 0) }
        .testTag("physical_split_slider_handle")
        .pointerInput(widthPx) {
          detectHorizontalDragGestures(
            onDragStart = {
              isDragging = true
              labelsBright = true
              haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            },
            onDragEnd = { isDragging = false },
            onDragCancel = { isDragging = false },
            onHorizontalDrag = { change, dragAmount ->
              change.consume()
              val next = (localPos + (dragAmount / widthPx)).coerceIn(0.05f, 0.95f)
              val side = when {
                abs(next - 0.5f) < 0.014f -> 0
                next < 0.5f -> -1
                else -> 1
              }
              if (side == 0 && lastCenterTickSide != 0) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
              }
              lastCenterTickSide = side
              localPos = next
              onSplitPositionChange(next)
            }
          )
        }
    ) {
      // Dark outer halo so the divider line stays razor-sharp against bright sky/snow anime frames
      Box(
        modifier = Modifier
          .fillMaxHeight()
          .width(4.dp)
          .background(Color.Black.copy(alpha = 0.45f))
      )
      // Luminous crisp center line
      Box(
        modifier = Modifier
          .fillMaxHeight()
          .width(2.dp)
          .background(
            Brush.verticalGradient(
              colors = listOf(
                Color.White.copy(alpha = 0.72f),
                accentColor,
                Color.White,
                accentColor,
                Color.White.copy(alpha = 0.72f)
              )
            )
          )
      )

      // Tactile 48.dp physical drag knob in the vertical center
      Surface(
        shape = CircleShape,
        color = Color(0xFF0E0E14).copy(alpha = if (isDragging) 0.94f else 0.78f),
        contentColor = Color.White,
        border = BorderStroke(
          width = if (isDragging) 2.5.dp else 1.8.dp,
          color = if (isDragging) accentColor else Color.White.copy(alpha = 0.92f)
        ),
        modifier = Modifier
          .size(48.dp)
          .scale(knobScale)
          .shadow(elevation = if (isDragging) 12.dp else 6.dp, shape = CircleShape)
      ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
          Icon(
            imageVector = Icons.Filled.CompareArrows,
            contentDescription = "Drag left or right to compare Original vs 4K Anime4K",
            tint = if (isDragging) accentColor else Color.White,
            modifier = Modifier.size(26.dp)
          )
        }
      }
    }

    // 2. Dynamic ORIGINAL (left) & 4K ANIME4K (right) floating labels pinned to the top safe area
    val topBadgeModifier = Modifier
      .then(if (isFullscreen) Modifier.statusBarsPadding() else Modifier)
      .padding(top = 12.dp)

    // Left badge: ORIGINAL
    if (localPos > 0.16f) {
      Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color.Black.copy(alpha = 0.62f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
        modifier = topBadgeModifier
          .align(Alignment.TopStart)
          .padding(start = 12.dp)
          .alpha(labelAlpha)
      ) {
        Text(
          text = "ORIGINAL",
          style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
          color = Color.White,
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
      }
    }

    // Right badge: 4K ANIME4K (positioned clearly away from the top-end FullscreenToolsCornerPill)
    if (localPos < 0.84f) {
      Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color.Black.copy(alpha = 0.68f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.75f)),
        modifier = Modifier
          .align(Alignment.BottomEnd)
          .padding(end = 14.dp, bottom = if (isFullscreen) 56.dp else 14.dp)
          .alpha(labelAlpha)
      ) {
        Text(
          text = rightSideLabel,
          style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
          color = accentColor,
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
      }
    }

    // 3. Compact top-center Exit Compare pill (one-tap off while playing anime, >= 48dp touch height)
    Surface(
      onClick = onExitCompare,
      shape = RoundedCornerShape(20.dp),
      color = Color.Black.copy(alpha = if (isDragging) 0.82f else 0.56f),
      contentColor = Color.White,
      border = BorderStroke(1.dp, accentColor.copy(alpha = 0.6f)),
      modifier = topBadgeModifier
        .align(Alignment.TopCenter)
        .heightIn(min = 36.dp)
        .alpha(if (isDragging) 1f else 0.82f)
        .testTag("exit_split_compare_button")
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
      ) {
        Text(
          text = "Compare ${(localPos * 100).roundToInt()}%",
          style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
          color = Color.White
        )
        Icon(
          imageVector = Icons.Filled.Close,
          contentDescription = "Exit side-by-side comparison",
          tint = Color.White.copy(alpha = 0.9f),
          modifier = Modifier.size(15.dp)
        )
      }
    }
  }
}

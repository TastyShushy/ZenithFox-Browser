package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Full-viewport Anti-Accidental-Touch Screen Lock Overlay for uninterrupted anime watching.
 *
 * - Absorbs ALL stray taps, swipes, pinches, palm touches, and hardware Back presses so the
 *   embedded web video player never pauses, seeks, exits fullscreen, or opens ads accidentally.
 * - Keeps the screen 100% clean after 2.4 seconds.
 * - Intentional two-step unlock: tap anywhere to reveal the translucent Unlock pill, then tap
 *   "Unlock" to restore full touch controls.
 */
@Composable
fun ScreenLockOverlay(
  isFullscreen: Boolean,
  onUnlock: () -> Unit,
  modifier: Modifier = Modifier
) {
  val haptics = LocalHapticFeedback.current
  var showUnlockPrompt by remember { mutableStateOf(true) }
  var promptTrigger by remember { mutableIntStateOf(0) }

  // Intercept hardware Back button while locked so accidental back gestures never exit fullscreen
  BackHandler(enabled = true) {
    showUnlockPrompt = true
    promptTrigger++
  }

  // Auto-hide the unlock pill cleanly so the anime fills 100% of the screen unobstructed
  LaunchedEffect(showUnlockPrompt, promptTrigger) {
    if (showUnlockPrompt) {
      delay(3000L)
      showUnlockPrompt = false
    }
  }

  Box(
    modifier = modifier
      .fillMaxSize()
      .testTag("screen_lock_shield")
      .pointerInput(Unit) {
        awaitEachGesture {
          val down = awaitFirstDown(requireUnconsumed = false)
          down.consume()
          var totalMove = 0f
          while (true) {
            val event = awaitPointerEvent()
            event.changes.forEach { change ->
              totalMove += change.positionChange().getDistance()
              change.consume()
            }
            if (event.changes.all { !it.pressed }) break
          }
          // Only a deliberate stationary tap reveals the unlock button (palm brushes stay ignored!)
          if (totalMove < 28f) {
            showUnlockPrompt = !showUnlockPrompt
            if (showUnlockPrompt) promptTrigger++
          }
        }
      }
  ) {
    AnimatedVisibility(
      visible = showUnlockPrompt,
      enter = fadeIn() + scaleIn(initialScale = 0.92f),
      exit = fadeOut() + scaleOut(targetScale = 0.92f),
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .then(if (isFullscreen) Modifier.statusBarsPadding() else Modifier)
        .navigationBarsPadding()
        .padding(bottom = 26.dp)
    ) {
      Surface(
        shape = RoundedCornerShape(28.dp),
        color = Color(0xFF0C0C12).copy(alpha = 0.84f),
        contentColor = Color.White,
        border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.72f)),
        shadowElevation = 10.dp
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp)
        ) {
          Icon(
            imageVector = Icons.Filled.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
          )
          Text(
            text = "Screen Locked",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = Color.White
          )
          Surface(
            onClick = {
              haptics.performHapticFeedback(HapticFeedbackType.LongPress)
              onUnlock()
            },
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
              .heightIn(min = 48.dp)
              .testTag("screen_unlock_button")
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
              Icon(
                imageVector = Icons.Filled.LockOpen,
                contentDescription = "Unlock screen touch controls",
                modifier = Modifier.size(18.dp)
              )
              Text(
                text = "Unlock",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
              )
            }
          }
        }
      }
    }
  }
}

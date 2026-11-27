package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.EnhancementConfig
import com.example.data.model.VideoScalingMode
import com.example.data.model.VideoTelemetry
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun FloatingVideoControlButton(
  telemetry: VideoTelemetry,
  enhancementConfig: EnhancementConfig,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  FloatingActionButton(
    onClick = onClick,
    shape = CircleShape,
    containerColor = MaterialTheme.colorScheme.primary,
    contentColor = MaterialTheme.colorScheme.onPrimary,
    modifier = modifier.testTag("floating_video_control_fab")
  ) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Icon(Icons.Filled.Tune, contentDescription = "Open video enhancement & scaling tools", modifier = Modifier.size(22.dp))
      if (enhancementConfig.enabled && telemetry.isShaderActive) {
        Text("4K", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
      }
    }
  }
}

/**
 * In-window Video Tools Sheet:
 * Essential player controls (Play/Pause, -10s/+10s, +85s Anime OP Skip, Speed, Mute, Subtitles),
 * 4K Upscaling & Frame Gen, Video Downloader, Screen Scaling, Brightness, Volume, PiP & Force Fullscreen.
 * Does NOT replace or block the website's own native HTML5 video player!
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FloatingVideoControlPanel(
  telemetry: VideoTelemetry,
  scalingMode: VideoScalingMode,
  customZoom: Float,
  enhancementConfig: EnhancementConfig,
  isFullscreen: Boolean,
  detectedStreamCount: Int,
  onDismiss: () -> Unit,
  onToggleForceFullscreen: (Boolean) -> Unit,
  onSelectScalingMode: (VideoScalingMode, Float) -> Unit,
  onOpenEnhancer: () -> Unit,
  onOpenDownloader: () -> Unit,
  onEnterPip: () -> Unit,
  onLockScreen: () -> Unit,
  onTogglePlayPause: () -> Unit,
  onSeekBy: (Double) -> Unit,
  onSetSpeed: (Float) -> Unit,
  onToggleMute: () -> Unit,
  onToggleSubtitles: () -> Unit
) {
  BackHandler(onBack = onDismiss)

  val context = LocalContext.current
  val activity = context as? Activity
  val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
  val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
  var volumeLevel by remember {
    mutableFloatStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume)
  }
  var brightnessLevel by remember {
    val cur = activity?.window?.attributes?.screenBrightness ?: 0.5f
    mutableFloatStateOf(if (cur < 0f) 0.5f else cur)
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(Color.Black.copy(alpha = 0.45f))
      .clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onDismiss
      )
  ) {
    Surface(
      shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
      color = MaterialTheme.colorScheme.surfaceContainer,
      contentColor = MaterialTheme.colorScheme.onSurface,
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
      tonalElevation = 10.dp,
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .fillMaxWidth()
        .clickable(
          interactionSource = remember { MutableInteractionSource() },
          indication = null,
          onClick = {}
        )
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(max = 600.dp)
          .verticalScroll(rememberScrollState())
          .navigationBarsPadding()
          .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
      ) {
        Box(
          modifier = Modifier
            .align(Alignment.CenterHorizontally)
            .width(40.dp)
            .height(4.dp)
            .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(2.dp))
        )

        // Telemetry header
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text("Video Player & 4K Tools", style = MaterialTheme.typography.titleMedium)
            val panelFpsText = when {
              !telemetry.isPlaying -> "Paused"
              telemetry.frameGenActive && telemetry.sourceFps > 0 && telemetry.outputFps > telemetry.sourceFps ->
                "${telemetry.sourceFps}→${telemetry.outputFps} FPS"
              telemetry.outputFps > 0 -> "${telemetry.outputFps} FPS"
              telemetry.sourceFps > 0 -> "${telemetry.sourceFps} FPS"
              else -> "Live"
            }
            Text(
              text = "${telemetry.resolutionLabel} • $panelFpsText • ${telemetry.statusHumanLabel}",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.primary
            )
          }
          Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(
              onClick = { onDismiss(); onLockScreen() },
              modifier = Modifier.testTag("video_screen_lock_button")
            ) {
              Icon(Icons.Filled.Lock, contentDescription = "Lock screen against accidental touches")
            }
            IconButton(onClick = { onDismiss(); onEnterPip() }, modifier = Modifier.testTag("video_pip_button")) {
              Icon(Icons.Filled.PictureInPictureAlt, contentDescription = "Picture in Picture")
            }
            IconButton(
              onClick = { onToggleForceFullscreen(!isFullscreen); onDismiss() },
              modifier = Modifier.testTag("video_force_fullscreen_button")
            ) {
              Icon(
                imageVector = if (isFullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                contentDescription = "Force Fullscreen",
                tint = MaterialTheme.colorScheme.primary
              )
            }
            IconButton(onClick = onDismiss) {
              Icon(Icons.Filled.Close, contentDescription = "Close tools")
            }
          }
        }

        // Essential Player Quick Controls: -10s, Play/Pause, +10s, +85s Anime OP Skip, Mute, Subtitles
        Surface(
          shape = RoundedCornerShape(16.dp),
          color = MaterialTheme.colorScheme.surfaceContainerHigh,
          contentColor = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
          ) {
            IconButton(onClick = { onSeekBy(-10.0) }, modifier = Modifier.testTag("video_seek_back_10")) {
              Icon(Icons.Filled.Replay10, contentDescription = "Rewind 10 seconds")
            }
            IconButton(onClick = onTogglePlayPause, modifier = Modifier.testTag("video_play_pause")) {
              Icon(
                imageVector = if (telemetry.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (telemetry.isPlaying) "Pause" else "Play",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
              )
            }
            IconButton(onClick = { onSeekBy(10.0) }, modifier = Modifier.testTag("video_seek_fwd_10")) {
              Icon(Icons.Filled.Forward10, contentDescription = "Forward 10 seconds")
            }
            Surface(
              onClick = { onSeekBy(85.0) },
              shape = RoundedCornerShape(10.dp),
              color = MaterialTheme.colorScheme.primaryContainer,
              contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
              modifier = Modifier.testTag("video_skip_intro_85")
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
              ) {
                Icon(Icons.Filled.FastForward, contentDescription = "Skip 85s Intro", modifier = Modifier.size(16.dp))
                Text("+85s OP", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
              }
            }
            IconButton(onClick = onToggleMute, modifier = Modifier.testTag("video_mute_toggle")) {
              Icon(
                imageVector = if (telemetry.muted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = if (telemetry.muted) "Unmute" else "Mute"
              )
            }
            if (telemetry.hasSubtitles) {
              IconButton(onClick = onToggleSubtitles, modifier = Modifier.testTag("video_subtitles_toggle")) {
                Icon(
                  imageVector = Icons.Filled.ClosedCaption,
                  contentDescription = "Toggle subtitles",
                  tint = if (telemetry.subtitlesVisible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
          }
        }

        // Quick Action Chips: 4K Enhancer & Video Downloader
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          Surface(
            onClick = { onDismiss(); onOpenEnhancer() },
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.weight(1f)
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.Center
            ) {
              Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(6.dp))
              Text("4K & FPS: ${enhancementConfig.preset.label}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
          }
          Surface(
            onClick = { onDismiss(); onOpenDownloader() },
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier
              .weight(1f)
              .testTag("video_download_button")
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.Center
            ) {
              Icon(Icons.Filled.Download, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(18.dp))
              Spacer(Modifier.width(6.dp))
              Text(
                text = if (detectedStreamCount > 0) "Download ($detectedStreamCount)" else "Download Video",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer
              )
            }
          }
        }

        // Playback Speed Selector
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Filled.Speed, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Text("Playback Speed", style = MaterialTheme.typography.titleMedium)
          }
          FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { spd ->
              val selected = abs(telemetry.playbackRate - spd) < 0.05f
              FilterChip(
                selected = selected,
                onClick = { onSetSpeed(spd) },
                label = { Text(if (spd == 1.0f) "1×" else "${spd}×") }
              )
            }
          }
        }

        // Scaling Modes
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Filled.AspectRatio, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Text("Screen Scaling Mode", style = MaterialTheme.typography.titleMedium)
          }
          FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            VideoScalingMode.entries.forEach { mode ->
              FilterChip(
                selected = scalingMode == mode,
                onClick = { onSelectScalingMode(mode, customZoom) },
                label = { Text(mode.label) },
                modifier = Modifier.testTag("scaling_${mode.name.lowercase()}")
              )
            }
          }
          if (scalingMode == VideoScalingMode.CUSTOM) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
              Text("Custom Zoom", style = MaterialTheme.typography.bodySmall)
              Text(String.format("%.2fx", customZoom), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            Slider(
              value = customZoom,
              onValueChange = { onSelectScalingMode(VideoScalingMode.CUSTOM, it) },
              valueRange = 0.5f..3.0f
            )
          }
        }

        // Brightness & Volume Sliders
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.Brightness6, contentDescription = "Brightness", modifier = Modifier.size(18.dp))
            Slider(
              value = brightnessLevel,
              onValueChange = { v ->
                brightnessLevel = v
                activity?.window?.let { win ->
                  val lp = win.attributes
                  lp.screenBrightness = v.coerceIn(0.05f, 1f)
                  win.attributes = lp
                }
              },
              modifier = Modifier.weight(1f)
            )
            Text("${(brightnessLevel * 100).roundToInt()}%", style = MaterialTheme.typography.labelSmall)
          }

          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Volume", modifier = Modifier.size(18.dp))
            Slider(
              value = volumeLevel,
              onValueChange = { v ->
                volumeLevel = v
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (v * maxVolume).roundToInt(), 0)
              },
              modifier = Modifier.weight(1f)
            )
            Text("${(volumeLevel * 100).roundToInt()}%", style = MaterialTheme.typography.labelSmall)
          }
        }

        Spacer(modifier = Modifier.height(8.dp))
      }
    }
  }
}

/**
 * Non-blocking Fullscreen Floating Corner Pill!
 * 99% of the fullscreen display passes ALL taps, play/pause, timeline scrubbing, subtitle,
 * episode, and quality clicks directly to the website's own native HTML5 video player.
 */
@Composable
fun FullscreenToolsCornerPill(
  telemetry: VideoTelemetry,
  scalingMode: VideoScalingMode,
  customZoom: Float,
  detectedStreamCount: Int,
  isForceFullscreen: Boolean,
  isSplitCompareActive: Boolean,
  onSetScalingMode: (VideoScalingMode, Float) -> Unit,
  onToggleSplitCompare: () -> Unit,
  onOpenEnhancer: () -> Unit,
  onOpenDownloader: () -> Unit,
  onOpenControlPanel: () -> Unit,
  onSkipIntro85: () -> Unit,
  onToggleOrientation: () -> Unit,
  onEnterPip: () -> Unit,
  onLockScreen: () -> Unit,
  onExitFullscreen: () -> Unit,
  modifier: Modifier = Modifier
) {
  var expanded by remember { mutableStateOf(false) }

  LaunchedEffect(expanded) {
    if (expanded) {
      delay(6000L)
      expanded = false
    }
  }

  Column(
    horizontalAlignment = Alignment.End,
    verticalArrangement = Arrangement.spacedBy(8.dp),
    modifier = modifier
      .statusBarsPadding()
      .padding(top = 10.dp, end = 12.dp)
  ) {
    Surface(
      onClick = { expanded = !expanded },
      shape = RoundedCornerShape(20.dp),
      color = Color.Black.copy(alpha = if (expanded) 0.85f else 0.42f),
      contentColor = Color.White,
      border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = if (expanded) 0.8f else 0.45f)),
      modifier = Modifier
        .alpha(if (expanded) 1f else 0.72f)
        .testTag("fullscreen_tools_pill")
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        Icon(
          Icons.Filled.AutoAwesome,
          contentDescription = "Open video enhancement tools",
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(16.dp)
        )
        val pillFpsLabel = when {
          !telemetry.isPlaying -> "Tools"
          telemetry.frameGenActive && telemetry.sourceFps > 0 && telemetry.outputFps > telemetry.sourceFps ->
            "${telemetry.sourceFps}→${telemetry.outputFps} FPS"
          telemetry.outputFps > 0 -> "${telemetry.outputFps} FPS"
          telemetry.sourceFps > 0 -> "${telemetry.sourceFps} FPS"
          else -> "Tools"
        }
        Text(
          text = buildString {
            append(pillFpsLabel)
            if (scalingMode != VideoScalingMode.FIT) append(" • ${scalingMode.label}")
          },
          style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
          color = Color.White
        )
      }
    }

    AnimatedVisibility(visible = expanded) {
      Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color.Black.copy(alpha = 0.88f),
        contentColor = Color.White,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
          IconButton(onClick = { expanded = false; onOpenEnhancer() }) {
            Icon(Icons.Filled.AutoAwesome, contentDescription = "4K Upscaling & Frame Gen", tint = MaterialTheme.colorScheme.primary)
          }
          IconButton(
            onClick = { expanded = false; onToggleSplitCompare() },
            modifier = Modifier.testTag("fullscreen_split_compare_button")
          ) {
            Icon(
              Icons.Filled.Compare,
              contentDescription = "Toggle side-by-side 4K compare slider",
              tint = if (isSplitCompareActive) MaterialTheme.colorScheme.primary else Color.White
            )
          }
          Surface(
            onClick = {
              val values = VideoScalingMode.entries
              val next = values[(values.indexOf(scalingMode) + 1) % values.size]
              onSetScalingMode(next, customZoom)
            },
            shape = RoundedCornerShape(10.dp),
            color = Color.White.copy(alpha = 0.14f),
            contentColor = Color.White
          ) {
            Text(
              text = scalingMode.label,
              style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
              color = Color.White,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
            )
          }
          Surface(
            onClick = { onSkipIntro85() },
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f),
            contentColor = Color.White
          ) {
            Text(
              text = "+85s",
              style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
              color = Color.White,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
            )
          }
          IconButton(onClick = { expanded = false; onOpenDownloader() }) {
            Icon(
              Icons.Filled.Download,
              contentDescription = "Download video",
              tint = if (detectedStreamCount > 0) MaterialTheme.colorScheme.primary else Color.White
            )
          }
          IconButton(onClick = { expanded = false; onOpenControlPanel() }) {
            Icon(Icons.Filled.Tune, contentDescription = "Player & Audio Tools", tint = Color.White)
          }
          IconButton(onClick = { onToggleOrientation() }) {
            Icon(Icons.Filled.ScreenRotation, contentDescription = "Rotate screen", tint = Color.White)
          }
          IconButton(onClick = { expanded = false; onEnterPip() }) {
            Icon(Icons.Filled.PictureInPictureAlt, contentDescription = "Picture in Picture", tint = Color.White)
          }
          IconButton(
            onClick = { expanded = false; onLockScreen() },
            modifier = Modifier.testTag("fullscreen_screen_lock_button")
          ) {
            Icon(Icons.Filled.Lock, contentDescription = "Lock screen against accidental touches", tint = Color.White)
          }
          if (isForceFullscreen) {
            IconButton(onClick = { expanded = false; onExitFullscreen() }) {
              Icon(Icons.Filled.FullscreenExit, contentDescription = "Exit Force Fullscreen", tint = Color.White)
            }
          }
        }
      }
    }
  }
}

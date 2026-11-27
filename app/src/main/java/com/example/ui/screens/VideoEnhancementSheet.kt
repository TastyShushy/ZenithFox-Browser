package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.EnhancementConfig
import com.example.data.model.EnhancementPreset
import com.example.data.model.VideoTelemetry
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning
import kotlin.math.roundToInt

/**
 * In-window Compose bottom sheet overlay (NO separate Dialog window!) so GeckoView
 * NEVER loses window focus, NEVER exits fullscreen, and NEVER pauses or flickers
 * while adjusting Anime4K 4K upscaling and Optical-Flow Frame Generation live!
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VideoEnhancementSheet(
  config: EnhancementConfig,
  telemetry: VideoTelemetry,
  onDismiss: () -> Unit,
  onToggleMaster: (Boolean) -> Unit,
  onSelectPreset: (EnhancementPreset) -> Unit,
  onUpdateCustom: ((EnhancementConfig) -> EnhancementConfig) -> Unit,
  onToggleFrameGen: (Boolean) -> Unit,
  onSelectTargetFps: (Int) -> Unit
) {
  BackHandler(onBack = onDismiss)

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
      tonalElevation = 8.dp,
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
          .heightIn(max = 580.dp)
          .verticalScroll(rememberScrollState())
          .navigationBarsPadding()
          .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
      ) {
        // Drag handle
        Box(
          modifier = Modifier
            .align(Alignment.CenterHorizontally)
            .width(40.dp)
            .height(4.dp)
            .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(2.dp))
        )

        // Header + Master Toggle + Close
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
          ) {
            Icon(
              Icons.Filled.AutoAwesome,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(24.dp)
            )
            Column {
              Text("4K Anime4K + FidelityFX CAS", style = MaterialTheme.typography.titleMedium)
              Text(
                text = "High-precision WebGL2 super-resolution, ink darkening & crisp sharpening",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Switch(
              checked = config.enabled,
              onCheckedChange = onToggleMaster,
              modifier = Modifier.testTag("enhancer_master_switch")
            )
            IconButton(onClick = onDismiss) {
              Icon(Icons.Filled.Close, contentDescription = "Close enhancer settings")
            }
          }
        }

        // Live GPU & Honest Shader Status Banner
        val statusColor = when {
          telemetry.isShaderActive -> StatusSuccess
          telemetry.enhancementStatus == "CORS_OR_DRM_PROTECTED" -> StatusWarning
          else -> MaterialTheme.colorScheme.primary
        }
        Surface(
          shape = RoundedCornerShape(16.dp),
          color = statusColor.copy(alpha = 0.12f),
          contentColor = MaterialTheme.colorScheme.onSurface,
          border = BorderStroke(1.dp, statusColor.copy(alpha = 0.45f)),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Icon(
                imageVector = when {
                  telemetry.isShaderActive -> Icons.Filled.CheckCircle
                  telemetry.enhancementStatus == "CORS_OR_DRM_PROTECTED" -> Icons.Filled.Warning
                  else -> Icons.Filled.Memory
                },
                contentDescription = null,
                tint = statusColor,
                modifier = Modifier.size(18.dp)
              )
              Text(
                text = if (telemetry.hasVideo) telemetry.statusHumanLabel else "Waiting for HTML5 <video> stream",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = statusColor
              )
            }
            Text(
              text = "GPU: ${telemetry.gpuRenderer} • WebGPU: ${if (telemetry.enhancementStatus == "ACTIVE_WEBGPU") "Zero-Copy Active" else if (telemetry.webGpuSupported) "Detected" else "Unavailable (Using WebGL2)"}",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (telemetry.hasVideo) {
              Text(
                text = "Resolution: ${telemetry.resolutionLabel} • Source: ${telemetry.sourceFps} FPS • Output: ${telemetry.outputFps} FPS",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
              )
            }
            if (telemetry.enhancementStatus == "ACTIVE_CSS_GPU_FILTER" || telemetry.enhancementStatus == "ACTIVE_SVG_GPU_FILTER") {
              Text(
                text = "Zero-Copy Native GPU Crisp mode is active directly on the hardware video decoder for locked full FPS and low battery usage (100% original colors preserved).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            } else if (telemetry.enhancementStatus == "CORS_OR_DRM_PROTECTED") {
              Text(
                text = "Raw pixel sampling is blocked by this stream host; native hardware playback and crisp geometric scaling remain active.",
                style = MaterialTheme.typography.bodySmall,
                color = StatusWarning
              )
            }
          }
        }

        // Quality Presets
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("Quality Presets", style = MaterialTheme.typography.titleMedium)
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            EnhancementPreset.entries.forEach { preset ->
              FilterChip(
                selected = config.preset == preset,
                onClick = { onSelectPreset(preset) },
                label = { Text(preset.label) },
                modifier = Modifier.testTag("preset_${preset.name.lowercase()}")
              )
            }
          }
          Text(
            text = config.preset.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }

        // Live Before / After Physical Split-Screen Compare Control
        Surface(
          shape = RoundedCornerShape(16.dp),
          color = if (config.splitCompare) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
          else MaterialTheme.colorScheme.surfaceContainerHigh,
          contentColor = MaterialTheme.colorScheme.onSurface,
          border = BorderStroke(
            1.dp,
            if (config.splitCompare) MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
            else MaterialTheme.colorScheme.outlineVariant
          ),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "Live Side-by-Side Compare Slider",
                  style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                )
                Text(
                  text = "Drag the physical divider directly over playing anime (Left: Original • Right: 4K)",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
              Switch(
                checked = config.splitCompare,
                onCheckedChange = { on ->
                  onUpdateCustom { it.copy(splitCompare = on) }
                  if (on) onDismiss()
                },
                modifier = Modifier.testTag("enhancer_split_compare_switch")
              )
            }
            if (config.splitCompare) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Surface(
                  onClick = onDismiss,
                  shape = RoundedCornerShape(12.dp),
                  color = MaterialTheme.colorScheme.primary,
                  contentColor = MaterialTheme.colorScheme.onPrimary,
                  modifier = Modifier
                    .weight(1f)
                    .testTag("enhancer_compare_on_video_button")
                ) {
                  Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                  ) {
                    Text(
                      text = "Drag Slider on Video (${(config.splitPosition * 100).roundToInt()}%)",
                      style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                  }
                }
                Surface(
                  onClick = { onUpdateCustom { it.copy(splitPosition = 0.5f) } },
                  shape = RoundedCornerShape(12.dp),
                  color = MaterialTheme.colorScheme.surfaceContainerHighest,
                  contentColor = MaterialTheme.colorScheme.onSurface,
                  modifier = Modifier.testTag("enhancer_compare_center_button")
                ) {
                  Text(
                    text = "Center",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                  )
                }
              }
            }
          }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // Custom GLSL Uniform Sliders
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text("Real-Time GLSL Shader Controls", style = MaterialTheme.typography.titleMedium)

          ShaderSliderRow(
            label = "Upscaling & Line-Art Reconstruction",
            value = config.upscaleStrength,
            valueLabel = "${(config.upscaleStrength * 100).roundToInt()}%",
            onValueChange = { v -> onUpdateCustom { it.copy(upscaleStrength = v) } }
          )
          ShaderSliderRow(
            label = "Anime Sharpening (Halo-Clamped)",
            value = config.sharpen,
            valueLabel = "${(config.sharpen * 100).roundToInt()}%",
            onValueChange = { v -> onUpdateCustom { it.copy(sharpen = v) } }
          )
          ShaderSliderRow(
            label = "Gradient Reconstruction & Debanding",
            value = config.deband,
            valueLabel = "${(config.deband * 100).roundToInt()}%",
            onValueChange = { v -> onUpdateCustom { it.copy(deband = v) } }
          )
          ShaderSliderRow(
            label = "Compression Artifact Denoising",
            value = config.denoise,
            valueLabel = "${(config.denoise * 100).roundToInt()}%",
            onValueChange = { v -> onUpdateCustom { it.copy(denoise = v) } }
          )
          ShaderSliderRow(
            label = "Edge Enhancement",
            value = config.edgeEnhance,
            valueLabel = "${(config.edgeEnhance * 100).roundToInt()}%",
            onValueChange = { v -> onUpdateCustom { it.copy(edgeEnhance = v) } }
          )
          ShaderSliderRow(
            label = "Shader Processing Resolution",
            value = config.resolutionScale,
            valueRange = 0.5f..2.0f,
            valueLabel = String.format("%.2fx", config.resolutionScale),
            onValueChange = { v -> onUpdateCustom { it.copy(resolutionScale = v) } }
          )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // Interactive GPU Optical-Flow Frame Generation (WebGL2 MCFI)
        val frameGenStatusText = when {
          !config.frameGenEnabled -> "Off (Native ${if (telemetry.sourceFps > 0) "${telemetry.sourceFps} FPS" else "source cadence"})"
          telemetry.frameGenAutoDisabled -> "Auto-Paused (GPU frame time >24 ms thermal guard)"
          telemetry.frameGenActive -> "Active: Optical-Flow MCFI → Target ${config.targetFps} FPS"
          telemetry.enhancementStatus == "ACTIVE_CSS_GPU_FILTER" || telemetry.enhancementStatus == "ACTIVE_SVG_GPU_FILTER" -> "Standby (Stream uses Safe Mode GPU Clarity)"
          telemetry.enhancementStatus == "CORS_OR_DRM_PROTECTED" -> "Standby (Cross-origin pixel sampling blocked by host)"
          else -> "Armed for ${config.targetFps} FPS (activates when WebGL2 upscaling runs)"
        }
        val frameGenStatusColor = when {
          telemetry.frameGenActive -> StatusSuccess
          telemetry.frameGenAutoDisabled || telemetry.enhancementStatus == "CORS_OR_DRM_PROTECTED" -> StatusWarning
          config.frameGenEnabled -> MaterialTheme.colorScheme.primary
          else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
        Surface(
          shape = RoundedCornerShape(16.dp),
          color = if (config.frameGenEnabled) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
          } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
          },
          contentColor = MaterialTheme.colorScheme.onSurface,
          border = BorderStroke(
            1.dp,
            if (config.frameGenEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
            else MaterialTheme.colorScheme.outlineVariant
          ),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
              ) {
                Icon(
                  Icons.Filled.Speed,
                  contentDescription = null,
                  tint = if (config.frameGenEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                )
                Column {
                  Text("GPU Optical-Flow Frame Generation", style = MaterialTheme.typography.titleSmall)
                  Text(
                    text = frameGenStatusText,
                    style = MaterialTheme.typography.labelSmall,
                    color = frameGenStatusColor
                  )
                }
              }
              Switch(
                checked = config.frameGenEnabled,
                onCheckedChange = onToggleFrameGen,
                modifier = Modifier.testTag("frame_gen_switch")
              )
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
              Text(
                text = "Target Frame Rate",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
              FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
              ) {
                listOf(30, 45, 60, 90, 120).forEach { fps ->
                  FilterChip(
                    selected = config.targetFps == fps,
                    onClick = { onSelectTargetFps(fps) },
                    label = { Text("$fps FPS") },
                    modifier = Modifier.testTag("frame_gen_fps_$fps")
                  )
                }
              }
            }

            Row(
              verticalAlignment = Alignment.Top,
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Icon(
                Icons.Filled.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
              )
              Text(
                text = "Uses real-time WebGL2 Lucas-Kanade bidirectional optical-flow warping synced to true decoded video frames (runs across Adreno, Mali, Xclipse & PowerVR GPUs without fake frame duplication). Automatically pauses if GPU frame times exceed 24 ms. Source: ${telemetry.sourceFps} FPS • Output: ${telemetry.outputFps} FPS.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(10.dp))
      }
    }
  }
}

@Composable
private fun ShaderSliderRow(
  label: String,
  value: Float,
  valueLabel: String,
  onValueChange: (Float) -> Unit,
  valueRange: ClosedFloatingPointRange<Float> = 0f..1f
) {
  Column(modifier = Modifier.fillMaxWidth()) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(label, style = MaterialTheme.typography.bodyMedium)
      Text(
        text = valueLabel,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary
      )
    }
    Slider(
      value = value.coerceIn(valueRange.start, valueRange.endInclusive),
      onValueChange = onValueChange,
      valueRange = valueRange
    )
  }
}

package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Hd
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.DetectedVideoStream
import com.example.data.model.HlsVariant
import com.example.data.model.StreamType
import com.example.data.model.VideoTelemetry
import com.example.engine.UrlUtils
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning

/**
 * Clean, High-Contrast Video-Only Downloader Sheet for ZenithFox Browser!
 * Explicitly provides contentColor = onSurface on every Surface so resolution,
 * quality, and estimated file size text are ALWAYS razor-sharp and readable on dark themes.
 */
@Composable
fun VideoDownloaderSheet(
  pageTitle: String,
  videoTelemetry: VideoTelemetry,
  detectedStreams: List<DetectedVideoStream>,
  hlsVariantsByUrl: Map<String, List<HlsVariant>>,
  inspectingUrl: String?,
  onInspectHls: (DetectedVideoStream) -> Unit,
  onDownloadStream: (stream: DetectedVideoStream, customFileName: String, variantUrl: String?) -> Unit,
  onOpenDownloadsScreen: () -> Unit,
  onDismiss: () -> Unit
) {
  BackHandler(onBack = onDismiss)

  var fileNameInput by remember(pageTitle) {
    mutableStateOf(
      pageTitle
        .replace(Regex("[^a-zA-Z0-9._\\- ]"), "")
        .trim()
        .take(65)
        .ifBlank { "Anime_Episode" }
    )
  }

  // Automatically inspect any un-inspected HLS stream the moment the sheet opens
  LaunchedEffect(detectedStreams) {
    detectedStreams
      .filter { it.streamType == StreamType.HLS && !it.isDrmProtected && !hlsVariantsByUrl.containsKey(it.url) }
      .forEach { onInspectHls(it) }
  }

  val onSurface = MaterialTheme.colorScheme.onSurface
  val onSurfaceVar = MaterialTheme.colorScheme.onSurfaceVariant

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(Color.Black.copy(alpha = 0.56f))
      .clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onDismiss
      )
  ) {
    Surface(
      shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
      color = MaterialTheme.colorScheme.surfaceContainer,
      contentColor = onSurface,
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
            .background(MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp))
        )

        // Header
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
          ) {
            Icon(
              Icons.Filled.Download,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(26.dp)
            )
            Column {
              Text(
                text = "Download Playing Video",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = onSurface
              )
              val durationText = if (videoTelemetry.durationSec > 0) " • ${UrlUtils.formatTimeSec(videoTelemetry.durationSec)}" else ""
              val resText = if (videoTelemetry.videoWidth > 0) " • ${videoTelemetry.resolutionLabel}" else ""
              Text(
                text = "Video-only detector$resText$durationText",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary
              )
            }
          }
          Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { onDismiss(); onOpenDownloadsScreen() }) {
              Icon(
                Icons.Filled.FolderOpen,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
              )
              Spacer(Modifier.width(4.dp))
              Text("Downloads", color = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onDismiss) {
              Icon(Icons.Filled.Close, contentDescription = "Close downloader", tint = onSurface)
            }
          }
        }

        // Custom Episode Filename Field (explicit high-contrast colors)
        OutlinedTextField(
          value = fileNameInput,
          onValueChange = { fileNameInput = it },
          label = { Text("Episode filename", color = onSurfaceVar) },
          singleLine = true,
          colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = onSurface,
            unfocusedTextColor = onSurface,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            focusedLabelColor = MaterialTheme.colorScheme.primary,
            unfocusedLabelColor = onSurfaceVar,
            cursorColor = MaterialTheme.colorScheme.primary
          ),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("video_downloader_filename_input")
        )

        if (detectedStreams.isEmpty()) {
          Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = onSurface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(
              modifier = Modifier.padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                  text = "Play the anime video first",
                  style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                  color = onSurface
                )
              }
              Text(
                text = "Press Play on the episode for 1–2 seconds so ZenithFox Browser detects the actual video stream, its available qualities (1080p / 720p / 480p), and estimated file size. Webpages, subtitles, ads, YouTube, and DRM streams are filtered out.",
                style = MaterialTheme.typography.bodySmall,
                color = onSurfaceVar
              )
            }
          }
        } else {
          detectedStreams.forEachIndexed { idx, stream ->
            val variants = hlsVariantsByUrl[stream.url].orEmpty()
            val isInspecting = inspectingUrl == stream.url

            Surface(
              shape = RoundedCornerShape(16.dp),
              color = MaterialTheme.colorScheme.surfaceContainerHigh,
              contentColor = onSurface,
              border = BorderStroke(
                1.dp,
                if (stream.isDrmProtected) StatusWarning.copy(alpha = 0.55f)
                else MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
              ),
              modifier = Modifier.fillMaxWidth()
            ) {
              Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                  Icon(
                    imageVector = if (stream.streamType == StreamType.HLS) Icons.Filled.Hd else Icons.Filled.Movie,
                    contentDescription = null,
                    tint = if (stream.isDrmProtected) StatusWarning else MaterialTheme.colorScheme.primary
                  )
                  Column(modifier = Modifier.weight(1f)) {
                    val labelPrefix = if (detectedStreams.size > 1) "Video #${idx + 1} • " else "Playing Video • "
                    Text(
                      text = "$labelPrefix${stream.streamType.label}",
                      style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                      color = onSurface
                    )
                    Text(
                      text = UrlUtils.displayHostOrUrl(stream.url),
                      style = MaterialTheme.typography.labelSmall,
                      color = onSurfaceVar,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis
                    )
                  }
                  if (stream.streamType == StreamType.HLS && !stream.isDrmProtected) {
                    IconButton(onClick = { onInspectHls(stream) }, enabled = !isInspecting) {
                      if (isInspecting) {
                        CircularProgressIndicator(
                          modifier = Modifier.size(18.dp),
                          strokeWidth = 2.dp,
                          color = MaterialTheme.colorScheme.primary
                        )
                      } else {
                        Icon(
                          Icons.Filled.Refresh,
                          contentDescription = "Refresh qualities",
                          tint = MaterialTheme.colorScheme.primary
                        )
                      }
                    }
                  }
                }

                if (stream.isDrmProtected) {
                  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = StatusWarning, modifier = Modifier.size(16.dp))
                    Text(
                      text = "DRM-encrypted video cannot be downloaded.",
                      style = MaterialTheme.typography.bodySmall,
                      color = StatusWarning
                    )
                  }
                } else if (stream.streamType == StreamType.HLS) {
                  if (isInspecting && variants.isEmpty()) {
                    Row(
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.spacedBy(10.dp),
                      modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                      CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                      )
                      Text(
                        text = "Reading video qualities & estimated sizes…",
                        style = MaterialTheme.typography.bodySmall,
                        color = onSurface
                      )
                    }
                  } else if (variants.isNotEmpty()) {
                    variants.forEachIndexed { vIdx, variant ->
                      if (vIdx > 0) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                      }
                      Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                      ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                          val resLabel = if (variant.resolution != "Auto" && variant.resolution.isNotBlank()) {
                            " (${variant.resolution})"
                          } else ""
                          Text(
                            text = "${variant.label}$resLabel",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = onSurface
                          )
                          val details = buildList {
                            if (variant.estimatedBytes > 0L) add("Est. ~${UrlUtils.formatBytes(variant.estimatedBytes)}")
                            if (variant.bandwidthKbps > 0) add("${String.format("%.1f", variant.bandwidthKbps / 1000f)} Mbps")
                            if (variant.durationSec > 0) add(UrlUtils.formatTimeSec(variant.durationSec))
                          }.joinToString(" • ").ifBlank { "Ready to download" }
                          Text(
                            text = details,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.secondary
                          )
                        }
                        FilledTonalButton(
                          onClick = {
                            val suffix = if (variant.label != "Source Quality") "_${variant.label}" else ""
                            onDownloadStream(
                              stream,
                              "${fileNameInput.trim().ifBlank { "Anime_Episode" }}$suffix",
                              variant.url
                            )
                          },
                          colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                          ),
                          modifier = Modifier.testTag("download_variant_${variant.label}")
                        ) {
                          Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                          Spacer(Modifier.width(4.dp))
                          Text("Download")
                        }
                      }
                    }
                  } else {
                    Button(
                      onClick = {
                        onDownloadStream(stream, fileNameInput.trim().ifBlank { "Anime_Episode" }, null)
                      },
                      modifier = Modifier.fillMaxWidth()
                    ) {
                      Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                      Spacer(Modifier.width(6.dp))
                      Text("Download Video")
                    }
                  }
                } else {
                  // Direct MP4 / WebM video with exact size & resolution
                  val qualityText = stream.qualityLabel.ifBlank {
                    if (videoTelemetry.videoHeight > 0) "${videoTelemetry.videoHeight}p" else "Original Quality"
                  }
                  val sizeText = if (stream.sizeBytes > 0L) UrlUtils.formatBytes(stream.sizeBytes) else "Size calculating…"
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                  ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                      Text(
                        text = qualityText,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = onSurface
                      )
                      Text(
                        text = "$sizeText • ${stream.mimeType}",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.secondary
                      )
                    }
                    Button(
                      onClick = {
                        val ext = when {
                          stream.mimeType.contains("webm", true) || stream.url.contains(".webm", true) -> ".webm"
                          stream.url.contains(".mkv", true) -> ".mkv"
                          else -> ".mp4"
                        }
                        onDownloadStream(stream, "${fileNameInput.trim().ifBlank { "Anime_Episode" }}$ext", null)
                      },
                      modifier = Modifier.testTag("download_stream_button")
                    ) {
                      Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                      Spacer(Modifier.width(6.dp))
                      Text("Download")
                    }
                  }
                }

                if (!stream.isDrmProtected && stream.referer.isNotBlank()) {
                  Text(
                    text = "Streaming CDN headers attached automatically ✓",
                    style = MaterialTheme.typography.labelSmall,
                    color = StatusSuccess
                  )
                }
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(8.dp))
      }
    }
  }
}

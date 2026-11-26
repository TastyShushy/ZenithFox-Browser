package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.engine.UrlUtils
import com.example.updater.AppReleaseInfo
import com.example.updater.UpdateDownloadState

@Composable
fun AppUpdateDialog(
  release: AppReleaseInfo,
  downloadState: UpdateDownloadState,
  onInstallUpdate: () -> Unit,
  onOpenReleasePage: (String) -> Unit,
  onSkipVersion: () -> Unit,
  onIgnoreForNow: () -> Unit
) {
  val isDownloading = downloadState is UpdateDownloadState.Downloading

  AlertDialog(
    onDismissRequest = { if (!isDownloading) onIgnoreForNow() },
    containerColor = MaterialTheme.colorScheme.surfaceContainer,
    titleContentColor = MaterialTheme.colorScheme.onSurface,
    textContentColor = MaterialTheme.colorScheme.onSurface,
    icon = {
      Icon(
        imageVector = Icons.Filled.SystemUpdate,
        contentDescription = "Update available",
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(30.dp)
      )
    },
    title = {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
          text = "Update Available",
          style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
        )
        Text(
          text = "v${BuildConfig.VERSION_NAME} → ${release.versionTag}",
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.primary
        )
      }
    },
    text = {
      Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        if (release.apkSizeBytes > 0L) {
          Text(
            text = "APK Size: ${UrlUtils.formatBytes(release.apkSizeBytes)}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }

        Surface(
          shape = RoundedCornerShape(12.dp),
          color = MaterialTheme.colorScheme.surfaceContainerHigh,
          contentColor = MaterialTheme.colorScheme.onSurface,
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 200.dp)
        ) {
          Text(
            text = release.changelog.take(1200),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
              .verticalScroll(rememberScrollState())
              .padding(12.dp)
          )
        }

        when (downloadState) {
          is UpdateDownloadState.Downloading -> {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Text("Downloading update…", style = MaterialTheme.typography.labelMedium)
                Text(
                  text = "${downloadState.percent}%",
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.primary
                )
              }
              LinearProgressIndicator(
                progress = { (downloadState.percent / 100f).coerceIn(0f, 1f) },
                modifier = Modifier
                  .fillMaxWidth()
                  .height(6.dp)
              )
            }
          }
          is UpdateDownloadState.Error -> {
            Text(
              text = downloadState.message,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.error
            )
          }
          is UpdateDownloadState.ReadyToInstall -> {
            Text(
              text = "APK downloaded! Tap Install Update if the Android installer didn't open automatically.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.primary
            )
          }
          UpdateDownloadState.Idle -> Unit
        }
      }
    },
    confirmButton = {
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Button(
          onClick = {
            if (release.apkDownloadUrl != null) onInstallUpdate()
            else onOpenReleasePage(release.releasePageUrl)
          },
          enabled = !isDownloading,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("update_install_button")
        ) {
          Text(
            text = when {
              isDownloading -> "Downloading…"
              release.apkDownloadUrl != null -> "Install Update"
              else -> "Open GitHub Release"
            }
          )
        }
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          OutlinedButton(
            onClick = onSkipVersion,
            enabled = !isDownloading,
            modifier = Modifier
              .weight(1f)
              .testTag("update_skip_button")
          ) {
            Text("Skip Version")
          }
          TextButton(
            onClick = onIgnoreForNow,
            enabled = !isDownloading,
            modifier = Modifier
              .weight(1f)
              .testTag("update_ignore_button")
          ) {
            Text("Ignore")
          }
        }
      }
    }
  )
}

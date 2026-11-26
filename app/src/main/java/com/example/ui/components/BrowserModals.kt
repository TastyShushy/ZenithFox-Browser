package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.BrowserTab
import com.example.data.model.ReaderArticle
import com.example.engine.UrlUtils
import com.example.viewmodel.PendingSitePermission

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserMenuBottomSheet(
  activeTab: BrowserTab?,
  onDismiss: () -> Unit,
  onNewTab: () -> Unit,
  onNewPrivateTab: () -> Unit,
  onOpenBookmarks: () -> Unit,
  onOpenHistory: () -> Unit,
  onOpenDownloads: () -> Unit,
  onOpenEnhancer: () -> Unit,
  onOpenExtensions: () -> Unit,
  onOpenPrivacy: () -> Unit,
  onOpenSitePermissions: () -> Unit,
  onOpenSettings: () -> Unit,
  onToggleDesktopMode: () -> Unit,
  onOpenFindInPage: () -> Unit,
  onSharePage: () -> Unit,
  onCopyUrl: () -> Unit,
  onShowQrCode: () -> Unit,
  onTranslatePage: () -> Unit,
  onReaderMode: () -> Unit,
  onSaveWebpage: () -> Unit
) {
  val hasWebPage = activeTab != null && !activeTab.isHomePage

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    containerColor = MaterialTheme.colorScheme.surfaceContainer,
    tonalElevation = 8.dp
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .navigationBarsPadding()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
      // Top quick icon strip
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        MenuQuickTile(
          icon = Icons.Filled.Add,
          label = "New Tab",
          onClick = { onDismiss(); onNewTab() },
          modifier = Modifier.weight(1f).testTag("menu_new_tab")
        )
        MenuQuickTile(
          icon = Icons.Filled.PrivacyTip,
          label = "Private",
          onClick = { onDismiss(); onNewPrivateTab() },
          modifier = Modifier.weight(1f).testTag("menu_private_tab")
        )
        MenuQuickTile(
          icon = Icons.Filled.AutoAwesome,
          label = "Anime4K",
          accent = true,
          onClick = { onDismiss(); onOpenEnhancer() },
          modifier = Modifier.weight(1f).testTag("menu_enhancer")
        )
        MenuQuickTile(
          icon = Icons.Filled.Shield,
          label = "Ad Shield",
          onClick = { onDismiss(); onOpenPrivacy() },
          modifier = Modifier.weight(1f).testTag("menu_privacy")
        )
      }

      Spacer(modifier = Modifier.height(14.dp))

      // Core Browser Screens Grid
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        MenuQuickTile(Icons.Filled.Bookmarks, "Bookmarks", onClick = { onDismiss(); onOpenBookmarks() }, modifier = Modifier.weight(1f))
        MenuQuickTile(Icons.Filled.History, "History", onClick = { onDismiss(); onOpenHistory() }, modifier = Modifier.weight(1f))
        MenuQuickTile(Icons.Filled.Download, "Downloads", onClick = { onDismiss(); onOpenDownloads() }, modifier = Modifier.weight(1f))
        MenuQuickTile(Icons.Filled.Extension, "Extensions", onClick = { onDismiss(); onOpenExtensions() }, modifier = Modifier.weight(1f))
      }

      Spacer(modifier = Modifier.height(14.dp))
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
      Spacer(modifier = Modifier.height(8.dp))

      // Desktop Mode Switch Row
      if (hasWebPage) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onToggleDesktopMode(); onDismiss() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Filled.DesktopWindows, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text("Desktop website", style = MaterialTheme.typography.titleMedium)
          }
          Switch(checked = activeTab.isDesktopMode, onCheckedChange = { onToggleDesktopMode(); onDismiss() })
        }

        // Page tools row
        Row(
          modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          MenuQuickTile(Icons.Filled.FindInPage, "Find", onClick = { onDismiss(); onOpenFindInPage() }, modifier = Modifier.weight(1f))
          MenuQuickTile(Icons.Filled.Share, "Share", onClick = { onDismiss(); onSharePage() }, modifier = Modifier.weight(1f))
          MenuQuickTile(Icons.Filled.ContentCopy, "Copy URL", onClick = { onDismiss(); onCopyUrl() }, modifier = Modifier.weight(1f))
          MenuQuickTile(Icons.Filled.QrCode2, "QR Code", onClick = { onDismiss(); onShowQrCode() }, modifier = Modifier.weight(1f))
        }

        Row(
          modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          MenuQuickTile(Icons.Filled.Translate, "Translate", onClick = { onDismiss(); onTranslatePage() }, modifier = Modifier.weight(1f))
          MenuQuickTile(Icons.AutoMirrored.Filled.MenuBook, "Reader", onClick = { onDismiss(); onReaderMode() }, modifier = Modifier.weight(1f))
          MenuQuickTile(Icons.Filled.SaveAlt, "Save Page", onClick = { onDismiss(); onSaveWebpage() }, modifier = Modifier.weight(1f))
          MenuQuickTile(Icons.Filled.Security, "Site Perms", onClick = { onDismiss(); onOpenSitePermissions() }, modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(6.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
      }

      // Settings & Site Permissions Footer
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Surface(
          onClick = { onDismiss(); onOpenSitePermissions() },
          shape = RoundedCornerShape(14.dp),
          color = MaterialTheme.colorScheme.surfaceVariant,
          modifier = Modifier.weight(1f)
        ) {
          Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Icon(Icons.Filled.Security, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
            Text("Permissions", style = MaterialTheme.typography.labelLarge)
          }
        }
        Surface(
          onClick = { onDismiss(); onOpenSettings() },
          shape = RoundedCornerShape(14.dp),
          color = MaterialTheme.colorScheme.primaryContainer,
          modifier = Modifier.weight(1f).testTag("menu_settings_button")
        ) {
          Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Icon(Icons.Filled.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text("Settings", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
          }
        }
      }
      Spacer(modifier = Modifier.height(12.dp))
    }
  }
}

@Composable
private fun MenuQuickTile(
  icon: ImageVector,
  label: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  accent: Boolean = false
) {
  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(14.dp),
    color = if (accent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor = MaterialTheme.colorScheme.onSurface,
    modifier = modifier
  ) {
    Column(
      modifier = Modifier.padding(vertical = 12.dp, horizontal = 6.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      Icon(
        imageVector = icon,
        contentDescription = label,
        tint = if (accent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.size(22.dp)
      )
      Text(
        text = label,
        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
        color = if (accent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center
      )
    }
  }
}

@Composable
fun QrCodeDialog(url: String, title: String, onDismiss: () -> Unit) {
  val bitmap = remember(url) { UrlUtils.generateQrBitmap(url, 560) }
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Share via QR Code", style = MaterialTheme.typography.titleLarge) },
    text = {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
      ) {
        if (bitmap != null) {
          Box(
            modifier = Modifier
              .size(220.dp)
              .clip(RoundedCornerShape(16.dp))
              .background(Color.White)
              .padding(12.dp),
            contentAlignment = Alignment.Center
          ) {
            Image(
              bitmap = bitmap.asImageBitmap(),
              contentDescription = "QR code for $url",
              modifier = Modifier.size(196.dp)
            )
          }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
          text = title.ifBlank { UrlUtils.displayHostOrUrl(url) },
          style = MaterialTheme.typography.titleSmall,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        Text(
          text = url,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
          textAlign = TextAlign.Center
        )
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) { Text("Close") }
    }
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderModeSheet(article: ReaderArticle, onDismiss: () -> Unit) {
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    containerColor = MaterialTheme.colorScheme.surface
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .heightIn(max = 580.dp)
        .verticalScroll(rememberScrollState())
        .navigationBarsPadding()
        .padding(horizontal = 20.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      Text("READER MODE", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
      Text(article.title, style = MaterialTheme.typography.headlineSmall)
      Text(
        UrlUtils.displayHostOrUrl(article.url),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
      article.paragraphs.forEach { para ->
        Text(para, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
      }
      Spacer(modifier = Modifier.height(20.dp))
    }
  }
}

@Composable
fun SitePermissionPromptDialog(
  request: PendingSitePermission,
  onDecision: (allow: Boolean, remember: Boolean) -> Unit
) {
  AlertDialog(
    onDismissRequest = { onDecision(false, false) },
    title = { Text("Permission Request", style = MaterialTheme.typography.titleLarge) },
    text = {
      Text(
        text = "${request.host.ifBlank { "This website" }} wants to access ${request.permissionLabel}.",
        style = MaterialTheme.typography.bodyMedium
      )
    },
    confirmButton = {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = { onDecision(true, false) }) { Text("Allow once") }
        TextButton(onClick = { onDecision(true, true) }) { Text("Always allow") }
      }
    },
    dismissButton = {
      TextButton(onClick = { onDecision(false, true) }) { Text("Deny") }
    }
  )
}

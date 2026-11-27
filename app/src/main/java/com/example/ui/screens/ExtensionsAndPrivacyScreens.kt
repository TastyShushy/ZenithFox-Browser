package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.db.CustomBlockRuleEntity
import com.example.data.db.SiteSettingEntity
import com.example.data.model.BlockedEvent
import com.example.data.model.BrowserSettings
import com.example.data.model.ExtensionInfo
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExtensionsScreen(
  extensions: List<ExtensionInfo>,
  onToggleExtension: (String, Boolean) -> Unit,
  onOpenEnhancer: () -> Unit,
  onOpenDownloader: () -> Unit,
  onOpenPrivacy: () -> Unit,
  onBack: () -> Unit
) {
  BackHandler(onBack = onBack)

  Scaffold(
    containerColor = MaterialTheme.colorScheme.background,
    topBar = {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .statusBarsPadding()
          .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        IconButton(onClick = onBack, modifier = Modifier.testTag("extensions_back_button")) {
          Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        Column {
          Text("Extension Manager", style = MaterialTheme.typography.headlineSmall)
          Text(
            text = "Chromium Blink + V8 + ANGLE Engine (Built-In Modules)",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
          )
        }
      }
    }
  ) { innerPadding ->
    LazyColumn(
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .navigationBarsPadding()
    ) {
      items(extensions, key = { it.id }) { ext ->
        val isBuiltIn = ext.isBuiltIn
        Surface(
          shape = RoundedCornerShape(18.dp),
          color = MaterialTheme.colorScheme.surfaceContainer,
          border = BorderStroke(
            1.dp,
            if (isBuiltIn && ext.isEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
            else MaterialTheme.colorScheme.outlineVariant
          ),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                  imageVector = if (isBuiltIn) Icons.Filled.Extension else Icons.Filled.Warning,
                  contentDescription = null,
                  tint = if (isBuiltIn) MaterialTheme.colorScheme.primary else StatusWarning
                )
                Column {
                  Text(ext.name, style = MaterialTheme.typography.titleMedium)
                  Text("v${ext.version} • ${ext.compatibilityStatus}", style = MaterialTheme.typography.labelSmall, color = if (isBuiltIn) StatusSuccess else StatusWarning)
                }
              }
              if (isBuiltIn) {
                Switch(
                  checked = ext.isEnabled,
                  onCheckedChange = { onToggleExtension(ext.id, it) },
                  modifier = Modifier.testTag("ext_switch_${ext.id}")
                )
              }
            }

            Text(ext.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Text("Permissions:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
              ext.permissions.forEach { perm ->
                AssistChip(onClick = {}, label = { Text(perm, style = MaterialTheme.typography.labelSmall) })
              }
            }

            Text(ext.technicalNotes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            if (ext.id.contains("enhancer")) {
              TextButton(onClick = onOpenEnhancer) { Text("Open Anime4K WebGL2 Settings →") }
            } else if (ext.id.contains("downloader") && ext.isBuiltIn) {
              TextButton(onClick = onOpenDownloader) { Text("Open Anime Video Downloader →") }
            } else if (ext.id.contains("adblock")) {
              TextButton(onClick = onOpenPrivacy) { Text("Configure Ad & Tracker Shield →") }
            }
          }
        }
      }

      // Dedicated AniLens Technical Compatibility Report Card
      item {
        Surface(
          shape = RoundedCornerShape(18.dp),
          color = MaterialTheme.colorScheme.surfaceContainerHigh,
          contentColor = MaterialTheme.colorScheme.onSurface,
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
              Text("Chromium GPU Acceleration & Built-In Modules", style = MaterialTheme.typography.titleMedium)
            }
            Text(
              text = "• ZenithFox v2.0.0 runs directly on Android Chromium (Blink + V8 + ANGLE GPU) with document-start multi-frame injection.\n" +
                "• Native zero-copy WebGL2 4K upscaling, GPU Optical-Flow Frame Generation, network-level ad shielding, and HLS/MP4 video downloading all run natively across Adreno, Mali, Xclipse, and PowerVR GPUs.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      }
    }
  }
}

@Composable
fun PrivacyDashboardScreen(
  settings: BrowserSettings,
  totalBlockedCount: Int,
  recentBlocked: List<BlockedEvent>,
  customRules: List<CustomBlockRuleEntity>,
  whitelistedHosts: Set<String>,
  currentHost: String,
  onUpdateSettings: ((BrowserSettings) -> BrowserSettings) -> Unit,
  onAddCustomRule: (String) -> Unit,
  onRemoveCustomRule: (String) -> Unit,
  onToggleWhitelist: (String) -> Unit,
  onClearBrowsingData: (history: Boolean, cookies: Boolean, cache: Boolean, siteData: Boolean) -> Unit,
  onBack: () -> Unit
) {
  BackHandler(onBack = onBack)
  var newDomain by remember { mutableStateOf("") }
  var showClearDialog by remember { mutableStateOf(false) }

  Scaffold(
    containerColor = MaterialTheme.colorScheme.background,
    topBar = {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .statusBarsPadding()
          .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        IconButton(onClick = onBack, modifier = Modifier.testTag("privacy_back_button")) {
          Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        Text("Privacy & Ad Shield", style = MaterialTheme.typography.headlineSmall)
      }
    }
  ) { innerPadding ->
    LazyColumn(
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .navigationBarsPadding()
    ) {
      // Hero Blocked Stats Card
      item {
        Surface(
          shape = RoundedCornerShape(20.dp),
          color = MaterialTheme.colorScheme.primaryContainer,
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
          ) {
            Icon(Icons.Filled.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
            Column {
              Text("$totalBlockedCount", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
              Text("Ads, trackers & redirects blocked this session", style = MaterialTheme.typography.bodySmall)
            }
          }
        }
      }

      // Master Protection Switches
      item {
        Surface(
          shape = RoundedCornerShape(16.dp),
          color = MaterialTheme.colorScheme.surfaceContainer,
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ToggleRow("Ad & Tracker Blocker", "Blocks ad networks in Chromium network layer + cosmetic DOM filter", settings.adBlockEnabled) { v ->
              onUpdateSettings { it.copy(adBlockEnabled = v) }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            ToggleRow("Pop-up & Redirect Guard", "Blocks unsolicited ad pop-unders and redirects", settings.popupBlockEnabled) { v ->
              onUpdateSettings { it.copy(popupBlockEnabled = v) }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            ToggleRow("Enhanced Tracking Protection", "First-party cookie isolation & SafeBrowsing", settings.trackerBlockEnabled) { v ->
              onUpdateSettings { it.copy(trackerBlockEnabled = v) }
            }
            if (currentHost.isNotBlank()) {
              HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
              val isWhitelisted = whitelistedHosts.contains(currentHost.lowercase())
              ToggleRow("Whitelist $currentHost", "Pause ad blocking on the current website", isWhitelisted) {
                onToggleWhitelist(currentHost)
              }
            }
          }
        }
      }

      // Custom Blocklist Editor
      item {
        Surface(
          shape = RoundedCornerShape(16.dp),
          color = MaterialTheme.colorScheme.surfaceContainer,
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Custom Blocklist Domains", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              OutlinedTextField(
                value = newDomain,
                onValueChange = { newDomain = it },
                placeholder = { Text("ads.example.com") },
                singleLine = true,
                modifier = Modifier.weight(1f)
              )
              IconButton(
                onClick = {
                  if (newDomain.isNotBlank()) {
                    onAddCustomRule(newDomain)
                    newDomain = ""
                  }
                }
              ) {
                Icon(Icons.Filled.Add, contentDescription = "Add custom domain", tint = MaterialTheme.colorScheme.primary)
              }
            }
            customRules.forEach { rule ->
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text(rule.domain, style = MaterialTheme.typography.labelMedium)
                IconButton(onClick = { onRemoveCustomRule(rule.domain) }) {
                  Icon(Icons.Filled.Delete, contentDescription = "Remove rule", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
              }
            }
          }
        }
      }

      // Whitelisted Websites
      if (whitelistedHosts.isNotEmpty()) {
        item {
          Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
              Text("Whitelisted Websites", style = MaterialTheme.typography.titleMedium)
              whitelistedHosts.forEach { host ->
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text(host, style = MaterialTheme.typography.bodyMedium)
                  TextButton(onClick = { onToggleWhitelist(host) }) { Text("Remove") }
                }
              }
            }
          }
        }
      }

      // Recent Blocked Requests Log
      if (recentBlocked.isNotEmpty()) {
        item {
          Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
              Text("Recent Blocked Requests", style = MaterialTheme.typography.titleMedium)
              recentBlocked.take(10).forEach { ev ->
                Text(
                  text = "• ${ev.domain} (${ev.resourceType})",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
          }
        }
      }

      // Clear Browsing Data + Local-Only Privacy Policy
      item {
        Button(
          onClick = { showClearDialog = true },
          colors = ButtonDefaults.buttonColors(containerColor = StatusError),
          modifier = Modifier.fillMaxWidth().testTag("privacy_clear_data_button")
        ) {
          Icon(Icons.Filled.DeleteForever, contentDescription = null)
          Spacer(Modifier.size(8.dp))
          Text("Clear Browsing Data")
        }
      }

      item {
        Surface(
          shape = RoundedCornerShape(16.dp),
          color = MaterialTheme.colorScheme.surfaceContainerHigh,
          contentColor = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              Icon(Icons.Filled.Lock, contentDescription = null, tint = StatusSuccess, modifier = Modifier.size(18.dp))
              Text("Transparent Local-Only Privacy Policy", style = MaterialTheme.typography.titleSmall)
            }
            Text(
              text = "ZenithFox Browser collects zero analytics, zero telemetry, and never transmits your browsing history, bookmarks, or downloads to any server. All user data stays strictly in local Room & DataStore storage on your device with Safe Browsing and isolated WebView file protections enabled.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      }
    }
  }

  if (showClearDialog) {
    ClearBrowsingDataDialog(
      onDismiss = { showClearDialog = false },
      onConfirm = { h, c, ca, s ->
        onClearBrowsingData(h, c, ca, s)
        showClearDialog = false
      }
    )
  }
}

@Composable
fun SitePermissionsScreen(
  siteSettings: List<SiteSettingEntity>,
  onUpdateSite: (host: String, (SiteSettingEntity) -> SiteSettingEntity) -> Unit,
  onDeleteSite: (host: String) -> Unit,
  onBack: () -> Unit
) {
  BackHandler(onBack = onBack)

  Scaffold(
    containerColor = MaterialTheme.colorScheme.background,
    topBar = {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .statusBarsPadding()
          .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        IconButton(onClick = onBack, modifier = Modifier.testTag("site_perms_back_button")) {
          Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        Text("Website Settings & Permissions", style = MaterialTheme.typography.headlineSmall)
      }
    }
  ) { innerPadding ->
    if (siteSettings.isEmpty()) {
      EmptyListState(
        icon = Icons.Filled.Security,
        title = "No per-website overrides yet",
        subtitle = "When you adjust scaling, enhancement presets, desktop mode, or permissions on a site, ZenithFox Browser remembers them here.",
        modifier = Modifier.padding(innerPadding)
      )
    } else {
      LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize().padding(innerPadding).navigationBarsPadding()
      ) {
        items(siteSettings, key = { it.host }) { site ->
          Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text(site.host, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                IconButton(onClick = { onDeleteSite(site.host) }) {
                  Icon(Icons.Filled.Delete, contentDescription = "Reset site settings")
                }
              }
              Text(
                text = "Scaling: ${site.scalingMode} (${String.format("%.2fx", site.customZoom)}) • Anime4K: ${site.enhancementPreset}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
              )
              ToggleRow("Ad Shield Whitelist", "Allow ads on this website", site.adBlockWhitelisted) { v ->
                onUpdateSite(site.host) { it.copy(adBlockWhitelisted = v) }
              }
              ToggleRow("Desktop Website Mode", "Request desktop user agent", site.desktopMode) { v ->
                onUpdateSite(site.host) { it.copy(desktopMode = v) }
              }
              Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                  onClick = {
                    val next = if (site.locationPermission == "ALLOW") "DENY" else "ALLOW"
                    onUpdateSite(site.host) { it.copy(locationPermission = next) }
                  },
                  label = { Text("Location: ${site.locationPermission}", style = MaterialTheme.typography.labelSmall) }
                )
                AssistChip(
                  onClick = {
                    val next = if (site.cameraPermission == "ALLOW") "DENY" else "ALLOW"
                    onUpdateSite(site.host) { it.copy(cameraPermission = next) }
                  },
                  label = { Text("Camera: ${site.cameraPermission}", style = MaterialTheme.typography.labelSmall) }
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
fun ClearBrowsingDataDialog(
  onDismiss: () -> Unit,
  onConfirm: (history: Boolean, cookies: Boolean, cache: Boolean, siteData: Boolean) -> Unit
) {
  var clearHistory by remember { mutableStateOf(true) }
  var clearCookies by remember { mutableStateOf(true) }
  var clearCache by remember { mutableStateOf(true) }
  var clearSiteData by remember { mutableStateOf(false) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Clear Browsing Data") },
    text = {
      Column {
        CheckRow("Browsing History", clearHistory) { clearHistory = it }
        CheckRow("Cookies & Sessions", clearCookies) { clearCookies = it }
        CheckRow("Cached Images & Network Files", clearCache) { clearCache = it }
        CheckRow("DOM / Local Storage", clearSiteData) { clearSiteData = it }
      }
    },
    confirmButton = {
      TextButton(onClick = { onConfirm(clearHistory, clearCookies, clearCache, clearSiteData) }) {
        Text("Clear Selected", color = StatusError)
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
  )
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
  Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
    Checkbox(checked = checked, onCheckedChange = onChange)
    Text(label, style = MaterialTheme.typography.bodyMedium)
  }
}

@Composable
fun ToggleRow(
  title: String,
  subtitle: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit
) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween
  ) {
    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
      Text(title, style = MaterialTheme.typography.titleSmall)
      Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Switch(checked = checked, onCheckedChange = onCheckedChange)
  }
}

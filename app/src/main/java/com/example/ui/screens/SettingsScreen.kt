package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.AccentPalette
import com.example.data.model.BrowserSettings
import com.example.data.model.EnhancementPreset
import com.example.data.model.PerformanceProfile
import com.example.data.model.PerformanceStats
import com.example.data.model.SearchEngine
import com.example.data.model.ThemeMode
import com.example.data.model.ToolbarPosition
import com.example.data.model.VideoScalingMode
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
  settings: BrowserSettings,
  performanceStats: PerformanceStats,
  onUpdateSettings: ((BrowserSettings) -> BrowserSettings) -> Unit,
  onOpenPrivacy: () -> Unit,
  onOpenSitePermissions: () -> Unit,
  onOpenExtensions: () -> Unit,
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
        IconButton(onClick = onBack, modifier = Modifier.testTag("settings_back_button")) {
          Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        Text("Settings", style = MaterialTheme.typography.headlineSmall)
      }
    }
  ) { innerPadding ->
    LazyColumn(
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .navigationBarsPadding()
    ) {
      // 1. Appearance
      item {
        SettingsSectionCard("Appearance", Icons.Filled.Palette) {
          Text("Theme Mode", style = MaterialTheme.typography.labelLarge)
          FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeMode.entries.forEach { mode ->
              FilterChip(
                selected = settings.themeMode == mode,
                onClick = { onUpdateSettings { it.copy(themeMode = mode) } },
                label = { Text(mode.label) }
              )
            }
          }

          Text("Accent Color", style = MaterialTheme.typography.labelLarge)
          Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AccentPalette.entries.forEach { palette ->
              val selected = settings.accentPalette == palette
              Box(
                modifier = Modifier
                  .size(34.dp)
                  .clip(CircleShape)
                  .background(Color(palette.primaryHex))
                  .border(
                    width = if (selected) 3.dp else 1.dp,
                    color = if (selected) Color.White else Color.Transparent,
                    shape = CircleShape
                  )
                  .clickable { onUpdateSettings { it.copy(accentPalette = palette, dynamicColor = false) } }
              )
            }
          }

          ToggleRow("Material You Dynamic Color", "Use wallpaper accent on Android 12+", settings.dynamicColor) { v ->
            onUpdateSettings { it.copy(dynamicColor = v) }
          }

          Text("Address Toolbar Position", style = MaterialTheme.typography.labelLarge)
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ToolbarPosition.entries.forEach { pos ->
              FilterChip(
                selected = settings.toolbarPosition == pos,
                onClick = { onUpdateSettings { it.copy(toolbarPosition = pos) } },
                label = { Text(pos.label) }
              )
            }
          }

          ToggleRow("Smooth High-Refresh Animations", "Disable for ultra-low power consumption", settings.animationsEnabled) { v ->
            onUpdateSettings { it.copy(animationsEnabled = v) }
          }
        }
      }

      // 2. Performance & Hardware Optimization (Universal Android GPUs)
      item {
        SettingsSectionCard("Performance & Hardware", Icons.Filled.Speed) {
          PerformanceProfile.entries.forEach { profile ->
            val selected = settings.performanceProfile == profile
            Surface(
              onClick = { onUpdateSettings { it.copy(performanceProfile = profile) } },
              shape = RoundedCornerShape(14.dp),
              color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
              contentColor = MaterialTheme.colorScheme.onSurface,
              border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Transparent),
              modifier = Modifier.fillMaxWidth()
            ) {
              Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
              ) {
                Icon(
                  imageVector = when (profile) {
                    PerformanceProfile.BATTERY_SAVER -> Icons.Filled.BatterySaver
                    PerformanceProfile.BALANCED -> Icons.Filled.Memory
                    PerformanceProfile.PERFORMANCE -> Icons.Filled.Speed
                  },
                  contentDescription = null,
                  tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Column {
                  Text(profile.label, style = MaterialTheme.typography.titleSmall)
                  Text(profile.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
              }
            }
          }

          ToggleRow(
            "Live Performance HUD Overlay",
            "Show real refresh rate, RAM, thermal state, resolution & FPS",
            settings.showPerformanceOverlay
          ) { v -> onUpdateSettings { it.copy(showPerformanceOverlay = v) } }

          Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
              Text(
                "Hardware Telemetry: ${performanceStats.displayRefreshRateHz} Hz • ${performanceStats.thermalStatusLabel}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
              )
              Text(
                "RAM: App ${performanceStats.appRamMb} MB • Free ${performanceStats.availableDeviceRamMb} / ${performanceStats.totalDeviceRamMb} MB • Sessions ${performanceStats.activeSessions} active / ${performanceStats.suspendedSessions} suspended",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
        }
      }

      // 3. Video Playback Defaults
      item {
        SettingsSectionCard("Video & Enhancement Defaults", Icons.Filled.PlayCircle) {
          Text("Default Scaling Mode", style = MaterialTheme.typography.labelLarge)
          FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            VideoScalingMode.entries.forEach { mode ->
              FilterChip(
                selected = settings.defaultScalingMode == mode,
                onClick = { onUpdateSettings { it.copy(defaultScalingMode = mode) } },
                label = { Text(mode.label) }
              )
            }
          }

          Text("Default Anime4K WebGL2 Preset", style = MaterialTheme.typography.labelLarge)
          FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(
              EnhancementPreset.OFF,
              EnhancementPreset.PERFORMANCE,
              EnhancementPreset.BALANCED,
              EnhancementPreset.HIGH_QUALITY,
              EnhancementPreset.ULTRA_QUALITY
            ).forEach { preset ->
              FilterChip(
                selected = settings.defaultEnhancementPreset == preset,
                onClick = { onUpdateSettings { it.copy(defaultEnhancementPreset = preset) } },
                label = { Text(preset.label) }
              )
            }
          }

          ToggleRow("Auto-Landscape in Fullscreen", "Switch to landscape automatically when entering video fullscreen", settings.autoLandscapeFullscreen) { v ->
            onUpdateSettings { it.copy(autoLandscapeFullscreen = v) }
          }
          ToggleRow("Keep Screen Awake During Video", "Prevent display timeout while anime is playing", settings.keepScreenAwakeVideo) { v ->
            onUpdateSettings { it.copy(keepScreenAwakeVideo = v) }
          }
          ToggleRow("Floating Control Button on Right", "Turn off to place video FAB on left side", settings.floatingButtonOnRight) { v ->
            onUpdateSettings { it.copy(floatingButtonOnRight = v) }
          }

          Text("Auto-Hide Fullscreen Controls: ${settings.autoHideControlsSec}s", style = MaterialTheme.typography.bodySmall)
          Slider(
            value = settings.autoHideControlsSec.toFloat(),
            onValueChange = { v -> onUpdateSettings { it.copy(autoHideControlsSec = v.roundToInt()) } },
            valueRange = 2f..10f
          )
        }
      }

      // 4. Browser General
      item {
        SettingsSectionCard("Browser Engine", Icons.Filled.Public) {
          Text("Default Search Engine", style = MaterialTheme.typography.labelLarge)
          FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SearchEngine.entries.forEach { engine ->
              FilterChip(
                selected = settings.searchEngine == engine,
                onClick = { onUpdateSettings { it.copy(searchEngine = engine) } },
                label = { Text(engine.label) }
              )
            }
          }
          ToggleRow("Restore Open Tabs on Startup", "Resume non-private tabs lazily when reopening app", settings.restoreTabsOnStartup) { v ->
            onUpdateSettings { it.copy(restoreTabsOnStartup = v) }
          }
          ToggleRow("Default Desktop Mode", "Request desktop layouts for new tabs", settings.defaultDesktopMode) { v ->
            onUpdateSettings { it.copy(defaultDesktopMode = v) }
          }
          ToggleRow("Enable JavaScript", "Required for modern HTML5 streaming players", settings.javaScriptEnabled) { v ->
            onUpdateSettings { it.copy(javaScriptEnabled = v) }
          }
          ToggleRow("Enable First-Party Cookies", "Allows staying logged in to anime tracking sites", settings.cookiesEnabled) { v ->
            onUpdateSettings { it.copy(cookiesEnabled = v) }
          }
        }
      }

      // 5. Privacy & Extensions Shortcuts
      item {
        SettingsSectionCard("Privacy & Extensions", Icons.Filled.Shield) {
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onOpenPrivacy) { Text("Privacy & Ad Shield") }
            TextButton(onClick = onOpenSitePermissions) { Text("Site Permissions") }
            TextButton(onClick = onOpenExtensions) { Text("Extensions") }
          }
        }
      }
    }
  }
}

@Composable
private fun SettingsSectionCard(
  title: String,
  icon: ImageVector,
  content: @Composable () -> Unit
) {
  Surface(
    shape = RoundedCornerShape(18.dp),
    color = MaterialTheme.colorScheme.surfaceContainer,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
      }
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
      content()
    }
  }
}

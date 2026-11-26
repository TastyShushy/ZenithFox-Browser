package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Tab
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.BrowserTab
import com.example.data.model.PerformanceStats
import com.example.engine.UrlUtils

@Composable
fun TabSwitcherScreen(
  tabs: List<BrowserTab>,
  activeTabId: String,
  recentlyClosedTabs: List<BrowserTab>,
  performanceStats: PerformanceStats,
  onSelectTab: (String) -> Unit,
  onCloseTab: (String) -> Unit,
  onNewTab: (isPrivate: Boolean, groupName: String) -> Unit,
  onRestoreClosed: () -> Unit,
  onCloseAll: (privateOnly: Boolean) -> Unit,
  onAssignGroup: (tabId: String, groupName: String) -> Unit,
  onBack: () -> Unit
) {
  BackHandler(onBack = onBack)

  var showPrivate by remember { mutableStateOf(false) }
  var selectedGroup by remember { mutableStateOf("All") }
  val availableGroups = listOf("All", "Default", "Anime", "Watchlist", "Research")

  val filteredTabs = remember(tabs, showPrivate, selectedGroup) {
    tabs.filter { it.isPrivate == showPrivate }
      .filter { selectedGroup == "All" || it.groupName == selectedGroup }
  }

  Scaffold(
    containerColor = MaterialTheme.colorScheme.background,
    topBar = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .statusBarsPadding()
          .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("tabs_back_button")) {
              Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to browser")
            }
            Text("Tabs (${filteredTabs.size})", style = MaterialTheme.typography.headlineSmall)
          }
          IconButton(onClick = { onCloseAll(showPrivate) }) {
            Icon(Icons.Filled.DeleteSweep, contentDescription = "Close all tabs")
          }
        }

        // Normal vs Private tabs segmented control
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
          SegmentedButton(
            selected = !showPrivate,
            onClick = { showPrivate = false },
            shape = SegmentedButtonDefaults.itemShape(0, 2),
            icon = { Icon(Icons.Filled.Tab, contentDescription = null, modifier = Modifier.size(16.dp)) }
          ) {
            Text("Normal (${tabs.count { !it.isPrivate }})")
          }
          SegmentedButton(
            selected = showPrivate,
            onClick = { showPrivate = true },
            shape = SegmentedButtonDefaults.itemShape(1, 2),
            icon = { Icon(Icons.Filled.PrivacyTip, contentDescription = null, modifier = Modifier.size(16.dp)) }
          ) {
            Text("Private (${tabs.count { it.isPrivate }})")
          }
        }

        // Tab Groups filter chips
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          items(availableGroups.size) { index ->
            val grp = availableGroups[index]
            FilterChip(
              selected = selectedGroup == grp,
              onClick = { selectedGroup = grp },
              label = { Text(grp) }
            )
          }
        }

        // Memory & Suspension Status Bar
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = MaterialTheme.colorScheme.surfaceContainer,
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              Icon(Icons.Filled.Memory, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
              Text(
                text = "Active: ${performanceStats.activeSessions} • Suspended: ${performanceStats.suspendedSessions} • Heap ${performanceStats.appRamMb} MB",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
        }

        // Restore recently closed tab banner
        if (recentlyClosedTabs.isNotEmpty() && !showPrivate) {
          Surface(
            onClick = onRestoreClosed,
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Restore, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Text(
                  text = "Restore: ${recentlyClosedTabs.first().title}",
                  style = MaterialTheme.typography.labelLarge,
                  color = MaterialTheme.colorScheme.primary,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
              }
              Text("UNDO", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
          }
        }
      }
    },
    floatingActionButton = {
      FloatingActionButton(
        onClick = {
          onNewTab(showPrivate, if (selectedGroup == "All") "Default" else selectedGroup)
          onBack()
        },
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier
          .navigationBarsPadding()
          .testTag("tabs_new_tab_fab")
      ) {
        Icon(Icons.Filled.Add, contentDescription = "Open new tab")
      }
    }
  ) { innerPadding ->
    if (filteredTabs.isEmpty()) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding),
        contentAlignment = Alignment.Center
      ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Icon(
            imageVector = if (showPrivate) Icons.Filled.PrivacyTip else Icons.Filled.Tab,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp)
          )
          Text(
            text = if (showPrivate) "No private tabs open" else "No tabs in this group",
            style = MaterialTheme.typography.titleMedium
          )
          TextButton(onClick = {
            onNewTab(showPrivate, if (selectedGroup == "All") "Default" else selectedGroup)
            onBack()
          }) {
            Text(if (showPrivate) "Start Private Tab" else "Create New Tab")
          }
        }
      }
    } else {
      LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 160.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
          .fillMaxSize()
          .padding(innerPadding)
      ) {
        items(filteredTabs, key = { it.id }) { tab ->
          val isSelected = tab.id == activeTabId
          Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(
              width = if (isSelected) 2.dp else 1.dp,
              color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
            ),
            modifier = Modifier
              .fillMaxWidth()
              .height(175.dp)
              .clip(RoundedCornerShape(18.dp))
              .clickable {
                onSelectTab(tab.id)
                onBack()
              }
          ) {
            Column(
              modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
              verticalArrangement = Arrangement.SpaceBetween
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Text(
                  text = tab.title.ifBlank { "Home" },
                  style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                  modifier = Modifier.weight(1f)
                )
                IconButton(
                  onClick = { onCloseTab(tab.id) },
                  modifier = Modifier.size(28.dp)
                ) {
                  Icon(Icons.Filled.Close, contentDescription = "Close tab", modifier = Modifier.size(16.dp))
                }
              }

              // Preview card interior
              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .weight(1f)
                  .padding(vertical = 6.dp)
                  .clip(RoundedCornerShape(12.dp))
                  .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                  .padding(10.dp),
                contentAlignment = Alignment.CenterStart
              ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                  Text(
                    text = if (tab.isHomePage) "ZenithFox Homepage" else UrlUtils.displayHostOrUrl(tab.url),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                  )
                  if (tab.isSuspended) {
                    Text(
                      text = "💤 Suspended (RAM saved)",
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  }
                }
              }

              // Group quick cycle chip
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Surface(
                  onClick = {
                    val groups = listOf("Default", "Anime", "Watchlist", "Research")
                    val next = groups[(groups.indexOf(tab.groupName) + 1) % groups.size]
                    onAssignGroup(tab.id, next)
                  },
                  shape = RoundedCornerShape(8.dp),
                  color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                ) {
                  Text(
                    text = tab.groupName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                  )
                }
                if (tab.isDesktopMode) {
                  Text("DESKTOP", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                }
              }
            }
          }
        }
      }
    }
  }
}

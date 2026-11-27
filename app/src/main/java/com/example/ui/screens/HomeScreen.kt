package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tab
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.db.BookmarkEntity
import com.example.data.db.CustomShortcutEntity
import com.example.data.db.HistoryEntity
import com.example.data.model.BrowserTab
import com.example.data.model.EnhancementConfig
import com.example.data.model.SearchEngine
import com.example.engine.UrlUtils

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
  isPrivateMode: Boolean,
  searchEngine: SearchEngine,
  enhancementConfig: EnhancementConfig,
  blockedCount: Int,
  shortcuts: List<CustomShortcutEntity>,
  openTabs: List<BrowserTab>,
  recentlyWatched: List<HistoryEntity>,
  recentHistory: List<HistoryEntity>,
  bookmarks: List<BookmarkEntity>,
  onNavigate: (String) -> Unit,
  onSelectTab: (String) -> Unit,
  onAddShortcut: (title: String, url: String, accentHex: Long) -> Unit,
  onDeleteShortcut: (Long) -> Unit,
  onOpenEnhancer: () -> Unit,
  onOpenDownloads: () -> Unit,
  onOpenPrivacy: () -> Unit,
  onOpenExtensions: () -> Unit,
  modifier: Modifier = Modifier
) {
  var searchQuery by remember { mutableStateOf("") }
  var showAddShortcutDialog by remember { mutableStateOf(false) }

  LazyColumn(
    modifier = modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background),
    contentPadding = PaddingValues(bottom = 28.dp),
    verticalArrangement = Arrangement.spacedBy(20.dp)
  ) {
    // 1. ZenithFox Cosmic Hero Header with user emblem + violet/sakura atmospheric scrim
    item {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(232.dp)
      ) {
        Image(
          painter = painterResource(id = R.drawable.img_homepage_hero),
          contentDescription = "ZenithFox Browser cosmic backdrop",
          contentScale = ContentScale.Crop,
          modifier = Modifier.fillMaxSize()
        )
        Box(
          modifier = Modifier
            .fillMaxSize()
            .background(
              Brush.verticalGradient(
                colors = listOf(
                  Color(0x881A0938),
                  Color(0xC20D0620),
                  MaterialTheme.colorScheme.background
                )
              )
            )
        )
        Column(
          modifier = Modifier
            .align(Alignment.BottomStart)
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            Image(
              painter = painterResource(id = R.drawable.ic_zenithfox_logo),
              contentDescription = "ZenithFox Browser Emblem",
              contentScale = ContentScale.Crop,
              modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(
                  width = 1.5.dp,
                  brush = Brush.linearGradient(
                    colors = listOf(
                      MaterialTheme.colorScheme.primary,
                      MaterialTheme.colorScheme.secondary
                    )
                  ),
                  shape = RoundedCornerShape(16.dp)
                )
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
              Text(
                text = if (isPrivateMode) "Private Session" else "ZenithFox Browser",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = Color(0xFFF8F5FF)
              )
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                if (isPrivateMode) {
                  StatusBadge(
                    icon = Icons.Filled.PrivacyTip,
                    text = "PRIVATE MODE",
                    color = MaterialTheme.colorScheme.primary
                  )
                } else {
                  StatusBadge(
                    icon = Icons.Filled.AutoAwesome,
                    text = if (enhancementConfig.enabled) "4K ${enhancementConfig.preset.label.uppercase()}" else "CHROMIUM 2.0",
                    color = MaterialTheme.colorScheme.primary
                  )
                }
                StatusBadge(
                  icon = Icons.Filled.Shield,
                  text = "$blockedCount BLOCKED",
                  color = MaterialTheme.colorScheme.secondary
                )
              }
            }
          }

          // Prominent Search Bar
          Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)),
            modifier = Modifier
              .fillMaxWidth()
              .height(52.dp)
          ) {
            Row(
              modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = "Search",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
              )
              Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (searchQuery.isEmpty()) {
                  Text(
                    text = "Search ${searchEngine.label} or enter anime site URL…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                }
                BasicTextField(
                  value = searchQuery,
                  onValueChange = { searchQuery = it },
                  singleLine = true,
                  textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                  cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                  keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                  keyboardActions = KeyboardActions(
                    onSearch = {
                      if (searchQuery.isNotBlank()) {
                        onNavigate(searchQuery)
                        searchQuery = ""
                      }
                    }
                  ),
                  modifier = Modifier
                    .fillMaxWidth()
                    .testTag("home_search_input")
                )
              }
            }
          }
        }
      }
    }

    // 2. Quick Feature Shortcuts Row (Enhancer, Downloads, Ad Shield, Extensions)
    item {
      LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        item {
          FeatureActionChip(
            icon = Icons.Filled.AutoAwesome,
            label = "Video Enhancer",
            subtitle = enhancementConfig.preset.label,
            accent = MaterialTheme.colorScheme.primary,
            onClick = onOpenEnhancer,
            testTag = "home_enhancer_shortcut"
          )
        }
        item {
          FeatureActionChip(
            icon = Icons.Filled.Download,
            label = "Downloads",
            subtitle = "Manager",
            accent = MaterialTheme.colorScheme.secondary,
            onClick = onOpenDownloads,
            testTag = "home_downloads_shortcut"
          )
        }
        item {
          FeatureActionChip(
            icon = Icons.Filled.Shield,
            label = "Ad Shield",
            subtitle = "$blockedCount blocked",
            accent = Color(0xFF34D399),
            onClick = onOpenPrivacy,
            testTag = "home_privacy_shortcut"
          )
        }
        item {
          FeatureActionChip(
            icon = Icons.Filled.Extension,
            label = "Extensions",
            subtitle = "WebGL2 & ETP",
            accent = Color(0xFF60A5FA),
            onClick = onOpenExtensions,
            testTag = "home_extensions_shortcut"
          )
        }
      }
    }

    // 3. Quick Website Shortcuts (User-editable!)
    item {
      Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text("Quick Shortcuts", style = MaterialTheme.typography.titleLarge)
          TextButton(
            onClick = { showAddShortcutDialog = true },
            modifier = Modifier.testTag("add_shortcut_button")
          ) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Add Site")
          }
        }
        Spacer(modifier = Modifier.height(8.dp))

        val chunked = shortcuts.chunked(4)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          chunked.forEach { rowItems ->
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              rowItems.forEach { shortcut ->
                val accent = Color(shortcut.accentColorHex)
                Surface(
                  shape = RoundedCornerShape(16.dp),
                  color = MaterialTheme.colorScheme.surfaceContainer,
                  border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                  modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .combinedClickable(
                      onClick = { onNavigate(shortcut.url) },
                      onLongClick = { onDeleteShortcut(shortcut.id) }
                    )
                ) {
                  Column(
                    modifier = Modifier.padding(vertical = 12.dp, horizontal = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                  ) {
                    Box(
                      modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.18f))
                        .border(1.dp, accent.copy(alpha = 0.6f), CircleShape),
                      contentAlignment = Alignment.Center
                    ) {
                      Text(
                        text = shortcut.title.take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = accent
                      )
                    }
                    Text(
                      text = shortcut.title,
                      style = MaterialTheme.typography.bodySmall,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis
                    )
                  }
                }
              }
              repeat(4 - rowItems.size) {
                Spacer(modifier = Modifier.weight(1f))
              }
            }
          }
        }
        Text(
          text = "Tip: Long-press any shortcut to remove it.",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(top = 6.dp)
        )
      }
    }

    // 4. Continue Browsing / Recently Open Tabs
    val browsableTabs = openTabs.filter { !it.isHomePage && !it.isPrivate }
    if (browsableTabs.isNotEmpty()) {
      item {
        SectionHeader("Continue Browsing", Icons.Filled.Tab)
        Spacer(modifier = Modifier.height(8.dp))
        LazyRow(
          contentPadding = PaddingValues(horizontal = 20.dp),
          horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          items(browsableTabs, key = { it.id }) { tab ->
            Surface(
              onClick = { onSelectTab(tab.id) },
              shape = RoundedCornerShape(16.dp),
              color = MaterialTheme.colorScheme.surfaceContainer,
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
              modifier = Modifier.width(196.dp)
            ) {
              Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(tab.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                  UrlUtils.displayHostOrUrl(tab.url),
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.primary,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
              }
            }
          }
        }
      }
    }

    // 5. Recently Watched Video Pages
    if (recentlyWatched.isNotEmpty()) {
      item {
        SectionHeader("Recently Watched", Icons.Filled.PlayCircle)
        Spacer(modifier = Modifier.height(8.dp))
        LazyRow(
          contentPadding = PaddingValues(horizontal = 20.dp),
          horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          items(recentlyWatched, key = { it.id }) { entry ->
            Surface(
              onClick = { onNavigate(entry.url) },
              shape = RoundedCornerShape(16.dp),
              color = MaterialTheme.colorScheme.surfaceContainerHigh,
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f)),
              modifier = Modifier.width(210.dp)
            ) {
              Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
              ) {
                Icon(Icons.Filled.PlayCircle, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(28.dp))
                Column(modifier = Modifier.weight(1f)) {
                  Text(entry.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                  Text(entry.host, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
              }
            }
          }
        }
      }
    }

    // 6. Bookmarked Anime Sites
    if (bookmarks.isNotEmpty()) {
      item {
        SectionHeader("Bookmarked Sites", Icons.Filled.Bookmark)
        Spacer(modifier = Modifier.height(8.dp))
        LazyRow(
          contentPadding = PaddingValues(horizontal = 20.dp),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          items(bookmarks.take(10), key = { it.id }) { bm ->
            Surface(
              onClick = { onNavigate(bm.url) },
              shape = RoundedCornerShape(14.dp),
              color = MaterialTheme.colorScheme.surfaceContainer,
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
              modifier = Modifier.width(175.dp)
            ) {
              Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(bm.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(UrlUtils.displayHostOrUrl(bm.url), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
              }
            }
          }
        }
      }
    }

    // 7. Recently Visited Sites
    if (recentHistory.isNotEmpty()) {
      item {
        SectionHeader("Recently Visited", Icons.Filled.History)
        Spacer(modifier = Modifier.height(8.dp))
        Column(
          modifier = Modifier.padding(horizontal = 20.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          recentHistory.distinctBy { it.url }.take(5).forEach { item ->
            Surface(
              onClick = { onNavigate(item.url) },
              shape = RoundedCornerShape(14.dp),
              color = MaterialTheme.colorScheme.surfaceContainer,
              modifier = Modifier.fillMaxWidth()
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
              ) {
                Icon(Icons.Filled.History, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                Column(modifier = Modifier.weight(1f)) {
                  Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                  Text(item.host, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
              }
            }
          }
        }
      }
    }
  }

  if (showAddShortcutDialog) {
    AddShortcutDialog(
      onDismiss = { showAddShortcutDialog = false },
      onAdd = { title, url, hex ->
        onAddShortcut(title, url, hex)
        showAddShortcutDialog = false
      }
    )
  }
}

@Composable
private fun StatusBadge(icon: ImageVector, text: String, color: Color) {
  Surface(
    shape = RoundedCornerShape(20.dp),
    color = Color.Black.copy(alpha = 0.65f),
    border = BorderStroke(1.dp, color.copy(alpha = 0.55f))
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
      Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(13.dp))
      Text(text, style = MaterialTheme.typography.labelSmall, color = color)
    }
  }
}

@Composable
private fun FeatureActionChip(
  icon: ImageVector,
  label: String,
  subtitle: String,
  accent: Color,
  onClick: () -> Unit,
  testTag: String
) {
  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.surfaceContainer,
    border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
    modifier = Modifier.testTag(testTag)
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      Box(
        modifier = Modifier
          .size(34.dp)
          .clip(RoundedCornerShape(10.dp))
          .background(accent.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center
      ) {
        Icon(icon, contentDescription = label, tint = accent, modifier = Modifier.size(18.dp))
      }
      Column {
        Text(label, style = MaterialTheme.typography.titleSmall)
        Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }
  }
}

@Composable
private fun SectionHeader(title: String, icon: ImageVector) {
  Row(
    modifier = Modifier.padding(horizontal = 20.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
    Text(title, style = MaterialTheme.typography.titleLarge)
  }
}

@Composable
private fun AddShortcutDialog(
  onDismiss: () -> Unit,
  onAdd: (title: String, url: String, accentHex: Long) -> Unit
) {
  var title by remember { mutableStateOf("") }
  var url by remember { mutableStateOf("") }
  val colors = listOf(0xFFC084FC, 0xFFFF6BD6, 0xFF60A5FA, 0xFF34D399, 0xFFFBBF24)
  var selectedColor by remember { mutableLongStateOf(colors.first()) }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Add Website Shortcut") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
          value = title,
          onValueChange = { title = it },
          label = { Text("Name (e.g. Crunchyroll)") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
          value = url,
          onValueChange = { url = it },
          label = { Text("Website URL") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          colors.forEach { hex ->
            Box(
              modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(Color(hex))
                .border(
                  width = if (selectedColor == hex) 3.dp else 0.dp,
                  color = Color.White,
                  shape = CircleShape
                )
                .clickable { selectedColor = hex }
            )
          }
        }
      }
    },
    confirmButton = {
      TextButton(
        onClick = { if (url.isNotBlank()) onAdd(title, url, selectedColor) }
      ) { Text("Add") }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    }
  )
}

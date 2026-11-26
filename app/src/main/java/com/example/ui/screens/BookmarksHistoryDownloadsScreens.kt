package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.db.BookmarkEntity
import com.example.data.db.DownloadEntity
import com.example.data.db.HistoryEntity
import com.example.engine.UrlUtils
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BookmarksScreen(
  bookmarks: List<BookmarkEntity>,
  onOpenUrl: (String) -> Unit,
  onAddBookmark: (title: String, url: String, folder: String) -> Unit,
  onDeleteBookmark: (Long) -> Unit,
  onBack: () -> Unit
) {
  BackHandler(onBack = onBack)
  var query by remember { mutableStateOf("") }
  var showAddDialog by remember { mutableStateOf(false) }

  val filtered = remember(bookmarks, query) {
    if (query.isBlank()) bookmarks
    else bookmarks.filter { it.title.contains(query, true) || it.url.contains(query, true) }
  }

  Scaffold(
    containerColor = MaterialTheme.colorScheme.background,
    topBar = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .statusBarsPadding()
          .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          IconButton(onClick = onBack, modifier = Modifier.testTag("bookmarks_back_button")) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
          Text("Bookmarks (${bookmarks.size})", style = MaterialTheme.typography.headlineSmall)
        }
        OutlinedTextField(
          value = query,
          onValueChange = { query = it },
          placeholder = { Text("Search bookmarks…") },
          leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
          singleLine = true,
          shape = RoundedCornerShape(16.dp),
          modifier = Modifier.fillMaxWidth()
        )
      }
    },
    floatingActionButton = {
      FloatingActionButton(
        onClick = { showAddDialog = true },
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier.navigationBarsPadding().testTag("bookmarks_add_fab")
      ) {
        Icon(Icons.Filled.Add, contentDescription = "Add bookmark")
      }
    }
  ) { innerPadding ->
    if (filtered.isEmpty()) {
      EmptyListState(
        icon = Icons.Filled.Bookmark,
        title = "No bookmarks yet",
        subtitle = "Tap the star in the address bar or + to save your favorite anime sites.",
        modifier = Modifier.padding(innerPadding)
      )
    } else {
      LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize().padding(innerPadding)
      ) {
        items(filtered, key = { it.id }) { bm ->
          Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
              .fillMaxWidth()
              .clickable { onOpenUrl(bm.url); onBack() }
          ) {
            Row(
              modifier = Modifier.padding(14.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
              Icon(Icons.Filled.Bookmark, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
              Column(modifier = Modifier.weight(1f)) {
                Text(bm.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(bm.url, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
              }
              IconButton(onClick = { onDeleteBookmark(bm.id) }) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete bookmark", tint = MaterialTheme.colorScheme.onSurfaceVariant)
              }
            }
          }
        }
      }
    }
  }

  if (showAddDialog) {
    var title by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var folder by remember { mutableStateOf("Anime") }
    AlertDialog(
      onDismissRequest = { showAddDialog = false },
      title = { Text("Add Bookmark") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, singleLine = true)
          OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("URL") }, singleLine = true)
          OutlinedTextField(value = folder, onValueChange = { folder = it }, label = { Text("Folder") }, singleLine = true)
        }
      },
      confirmButton = {
        TextButton(onClick = {
          if (url.isNotBlank()) {
            onAddBookmark(title, url, folder)
            showAddDialog = false
          }
        }) { Text("Save") }
      },
      dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text("Cancel") } }
    )
  }
}

@Composable
fun HistoryScreen(
  history: List<HistoryEntity>,
  onOpenUrl: (String) -> Unit,
  onDeleteItem: (Long) -> Unit,
  onClearAll: () -> Unit,
  onBack: () -> Unit
) {
  BackHandler(onBack = onBack)
  var query by remember { mutableStateOf("") }
  val dateFormat = remember { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }

  val filtered = remember(history, query) {
    if (query.isBlank()) history
    else history.filter { it.title.contains(query, true) || it.url.contains(query, true) }
  }

  Scaffold(
    containerColor = MaterialTheme.colorScheme.background,
    topBar = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .statusBarsPadding()
          .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("history_back_button")) {
              Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text("History", style = MaterialTheme.typography.headlineSmall)
          }
          if (history.isNotEmpty()) {
            TextButton(onClick = onClearAll, modifier = Modifier.testTag("history_clear_all")) {
              Icon(Icons.Filled.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(Modifier.size(4.dp))
              Text("Clear")
            }
          }
        }
        OutlinedTextField(
          value = query,
          onValueChange = { query = it },
          placeholder = { Text("Search browsing history…") },
          leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
          singleLine = true,
          shape = RoundedCornerShape(16.dp),
          modifier = Modifier.fillMaxWidth()
        )
      }
    }
  ) { innerPadding ->
    if (filtered.isEmpty()) {
      EmptyListState(
        icon = Icons.Filled.History,
        title = "No browsing history",
        subtitle = "Visited pages are stored strictly locally on this device.",
        modifier = Modifier.padding(innerPadding)
      )
    } else {
      LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize().padding(innerPadding)
      ) {
        items(filtered, key = { it.id }) { item ->
          Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier
              .fillMaxWidth()
              .clickable { onOpenUrl(item.url); onBack() }
          ) {
            Row(
              modifier = Modifier.padding(14.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
              Icon(
                imageVector = if (item.hadVideo) Icons.Filled.PlayCircle else Icons.Filled.History,
                contentDescription = null,
                tint = if (item.hadVideo) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
              )
              Column(modifier = Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                  text = "${item.host} • ${dateFormat.format(Date(item.visitedAt))}",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 1
                )
              }
              IconButton(onClick = { onDeleteItem(item.id) }) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete history entry", tint = MaterialTheme.colorScheme.onSurfaceVariant)
              }
            }
          }
        }
      }
    }
  }
}

@Composable
fun DownloadsScreen(
  downloads: List<DownloadEntity>,
  downloadSubfolder: String,
  onStartManualDownload: (String) -> Unit,
  onCancelDownload: (DownloadEntity) -> Unit,
  onRetryDownload: (DownloadEntity) -> Unit,
  onOpenDownload: (DownloadEntity) -> Unit,
  onDeleteDownload: (DownloadEntity) -> Unit,
  onBack: () -> Unit
) {
  BackHandler(onBack = onBack)
  var showManualDialog by remember { mutableStateOf(false) }

  Scaffold(
    containerColor = MaterialTheme.colorScheme.background,
    topBar = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .statusBarsPadding()
          .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          IconButton(onClick = onBack, modifier = Modifier.testTag("downloads_back_button")) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
          Column {
            Text("Download Manager", style = MaterialTheme.typography.headlineSmall)
            Text(
              text = "Saving to Downloads/$downloadSubfolder",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.primary
            )
          }
        }
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = MaterialTheme.colorScheme.surfaceContainer,
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            Text(
              text = "Uses Android DownloadManager for direct files. DRM-protected or segmented HLS/DASH streams are not downloadable.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      }
    },
    floatingActionButton = {
      FloatingActionButton(
        onClick = { showManualDialog = true },
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier.navigationBarsPadding().testTag("downloads_add_fab")
      ) {
        Icon(Icons.Filled.Add, contentDescription = "Download URL")
      }
    }
  ) { innerPadding ->
    if (downloads.isEmpty()) {
      EmptyListState(
        icon = Icons.Filled.Download,
        title = "No downloads yet",
        subtitle = "Files downloaded from websites appear here with live progress and status.",
        modifier = Modifier.padding(innerPadding)
      )
    } else {
      LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize().padding(innerPadding)
      ) {
        items(downloads, key = { it.id }) { dl ->
          val statusColor = when (dl.status) {
            "COMPLETED" -> StatusSuccess
            "FAILED", "CANCELLED" -> StatusError
            "PAUSED" -> StatusWarning
            else -> MaterialTheme.colorScheme.primary
          }
          val progress = if (dl.totalBytes > 0) (dl.downloadedBytes.toFloat() / dl.totalBytes.toFloat()).coerceIn(0f, 1f) else 0f

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
                Column(modifier = Modifier.weight(1f)) {
                  Text(dl.fileName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                  Text(
                    text = "${dl.status} • ${UrlUtils.formatBytes(dl.downloadedBytes)} / ${if (dl.totalBytes > 0) UrlUtils.formatBytes(dl.totalBytes) else "—"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor
                  )
                }
                Row {
                  if (dl.status == "RUNNING" || dl.status == "PENDING") {
                    IconButton(onClick = { onCancelDownload(dl) }) {
                      Icon(Icons.Filled.Cancel, contentDescription = "Cancel download", tint = StatusError)
                    }
                  }
                  if (dl.status == "FAILED" || dl.status == "CANCELLED") {
                    IconButton(onClick = { onRetryDownload(dl) }) {
                      Icon(Icons.Filled.Refresh, contentDescription = "Retry download", tint = MaterialTheme.colorScheme.primary)
                    }
                  }
                  if (dl.status == "COMPLETED") {
                    IconButton(onClick = { onOpenDownload(dl) }) {
                      Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open file", tint = StatusSuccess)
                    }
                  }
                  IconButton(onClick = { onDeleteDownload(dl) }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete download", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                  }
                }
              }
              if (dl.status == "RUNNING" || dl.status == "PENDING") {
                LinearProgressIndicator(
                  progress = { progress },
                  modifier = Modifier.fillMaxWidth().height(4.dp)
                )
              }
            }
          }
        }
      }
    }
  }

  if (showManualDialog) {
    var url by remember { mutableStateOf("") }
    AlertDialog(
      onDismissRequest = { showManualDialog = false },
      title = { Text("Direct File Download") },
      text = {
        OutlinedTextField(
          value = url,
          onValueChange = { url = it },
          label = { Text("Direct file URL (https://…)") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )
      },
      confirmButton = {
        TextButton(onClick = {
          if (url.startsWith("http")) {
            onStartManualDownload(url.trim())
            showManualDialog = false
          }
        }) { Text("Download") }
      },
      dismissButton = { TextButton(onClick = { showManualDialog = false }) { Text("Cancel") } }
    )
  }
}

@Composable
fun EmptyListState(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  title: String,
  subtitle: String,
  modifier: Modifier = Modifier
) {
  Box(modifier = modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
      Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f), modifier = Modifier.size(52.dp))
      Text(title, style = MaterialTheme.typography.titleLarge)
      Text(
        subtitle,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center
      )
    }
  }
}

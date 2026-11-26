package com.example.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val title: String,
  val url: String,
  val folder: String = "Anime",
  val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "history")
data class HistoryEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val title: String,
  val url: String,
  val host: String,
  val hadVideo: Boolean = false,
  val visitedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "downloads")
data class DownloadEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val systemDownloadId: Long = -1L,
  val fileName: String,
  val url: String,
  val mimeType: String = "*/*",
  val totalBytes: Long = 0L,
  val downloadedBytes: Long = 0L,
  val status: String = "PENDING", // PENDING, RUNNING, PAUSED, COMPLETED, FAILED, CANCELLED
  val localUri: String = "",
  val destinationFolder: String = "Downloads/AnimeBrowser",
  val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "saved_tabs")
data class SavedTabEntity(
  @PrimaryKey val id: String,
  val title: String,
  val url: String,
  val groupName: String = "Default",
  val isDesktopMode: Boolean = false,
  val lastAccessedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "site_settings")
data class SiteSettingEntity(
  @PrimaryKey val host: String,
  val scalingMode: String = "FIT",
  val customZoom: Float = 1.0f,
  val enhancementPreset: String = "BALANCED",
  val desktopMode: Boolean = false,
  val adBlockWhitelisted: Boolean = false,
  val javaScriptAllowed: Boolean = true,
  val cameraPermission: String = "ASK",   // ALLOW, DENY, ASK
  val micPermission: String = "ASK",
  val locationPermission: String = "ASK",
  val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "custom_shortcuts")
data class CustomShortcutEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val title: String,
  val url: String,
  val accentColorHex: Long = 0xFF00E5FF,
  val sortOrder: Int = 0
)

@Entity(tableName = "custom_block_rules")
data class CustomBlockRuleEntity(
  @PrimaryKey val domain: String,
  val addedAt: Long = System.currentTimeMillis()
)

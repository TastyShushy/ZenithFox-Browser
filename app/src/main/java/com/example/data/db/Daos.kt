package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {
  @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC")
  fun observeAll(): Flow<List<BookmarkEntity>>

  @Query("SELECT EXISTS(SELECT 1 FROM bookmarks WHERE url = :url)")
  fun observeIsBookmarked(url: String): Flow<Boolean>

  @Query("SELECT * FROM bookmarks WHERE url = :url LIMIT 1")
  suspend fun findByUrl(url: String): BookmarkEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(bookmark: BookmarkEntity)

  @Query("DELETE FROM bookmarks WHERE id = :id")
  suspend fun deleteById(id: Long)

  @Query("DELETE FROM bookmarks WHERE url = :url")
  suspend fun deleteByUrl(url: String)
}

@Dao
interface HistoryDao {
  @Query("SELECT * FROM history ORDER BY visitedAt DESC LIMIT 300")
  fun observeRecent(): Flow<List<HistoryEntity>>

  @Query("SELECT * FROM history WHERE hadVideo = 1 ORDER BY visitedAt DESC LIMIT 20")
  fun observeRecentlyWatched(): Flow<List<HistoryEntity>>

  @Query("SELECT * FROM history WHERE title LIKE '%' || :query || '%' OR url LIKE '%' || :query || '%' ORDER BY visitedAt DESC LIMIT 50")
  fun search(query: String): Flow<List<HistoryEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(entry: HistoryEntity)

  @Query("UPDATE history SET hadVideo = 1 WHERE url = :url")
  suspend fun markAsVideoPage(url: String)

  @Query("DELETE FROM history WHERE id = :id")
  suspend fun deleteById(id: Long)

  @Query("DELETE FROM history")
  suspend fun clearAll()
}

@Dao
interface DownloadDao {
  @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
  fun observeAll(): Flow<List<DownloadEntity>>

  @Query("SELECT * FROM downloads WHERE status IN ('PENDING', 'RUNNING', 'PAUSED')")
  suspend fun getActiveDownloads(): List<DownloadEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(download: DownloadEntity): Long

  @Query("UPDATE downloads SET status = :status, downloadedBytes = :downloadedBytes, totalBytes = :totalBytes, localUri = :localUri WHERE id = :id")
  suspend fun updateProgress(id: Long, status: String, downloadedBytes: Long, totalBytes: Long, localUri: String)

  @Query("DELETE FROM downloads WHERE id = :id")
  suspend fun deleteById(id: Long)

  @Query("DELETE FROM downloads")
  suspend fun clearAll()
}

@Dao
interface SavedTabDao {
  @Query("SELECT * FROM saved_tabs ORDER BY lastAccessedAt DESC")
  suspend fun getAll(): List<SavedTabEntity>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertAll(tabs: List<SavedTabEntity>)

  @Query("DELETE FROM saved_tabs")
  suspend fun clearAll()
}

@Dao
interface SiteSettingDao {
  @Query("SELECT * FROM site_settings ORDER BY updatedAt DESC")
  fun observeAll(): Flow<List<SiteSettingEntity>>

  @Query("SELECT * FROM site_settings WHERE host = :host LIMIT 1")
  suspend fun getForHost(host: String): SiteSettingEntity?

  @Query("SELECT host FROM site_settings WHERE adBlockWhitelisted = 1")
  fun observeWhitelistedHosts(): Flow<List<String>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(setting: SiteSettingEntity)

  @Query("DELETE FROM site_settings WHERE host = :host")
  suspend fun deleteForHost(host: String)
}

@Dao
interface CustomShortcutDao {
  @Query("SELECT * FROM custom_shortcuts ORDER BY sortOrder ASC, id ASC")
  fun observeAll(): Flow<List<CustomShortcutEntity>>

  @Query("SELECT COUNT(*) FROM custom_shortcuts")
  suspend fun count(): Int

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(shortcut: CustomShortcutEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertAll(shortcuts: List<CustomShortcutEntity>)

  @Query("DELETE FROM custom_shortcuts WHERE id = :id")
  suspend fun deleteById(id: Long)
}

@Dao
interface CustomBlockRuleDao {
  @Query("SELECT * FROM custom_block_rules ORDER BY addedAt DESC")
  fun observeAll(): Flow<List<CustomBlockRuleEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(rule: CustomBlockRuleEntity)

  @Query("DELETE FROM custom_block_rules WHERE domain = :domain")
  suspend fun delete(domain: String)
}

package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
  entities = [
    BookmarkEntity::class,
    HistoryEntity::class,
    DownloadEntity::class,
    SavedTabEntity::class,
    SiteSettingEntity::class,
    CustomShortcutEntity::class,
    CustomBlockRuleEntity::class
  ],
  version = 1,
  exportSchema = false
)
abstract class AnimeBrowserDatabase : RoomDatabase() {
  abstract fun bookmarkDao(): BookmarkDao
  abstract fun historyDao(): HistoryDao
  abstract fun downloadDao(): DownloadDao
  abstract fun savedTabDao(): SavedTabDao
  abstract fun siteSettingDao(): SiteSettingDao
  abstract fun customShortcutDao(): CustomShortcutDao
  abstract fun customBlockRuleDao(): CustomBlockRuleDao

  companion object {
    @Volatile
    private var INSTANCE: AnimeBrowserDatabase? = null

    fun getInstance(context: Context): AnimeBrowserDatabase {
      return INSTANCE ?: synchronized(this) {
        INSTANCE ?: Room.databaseBuilder(
          context.applicationContext,
          AnimeBrowserDatabase::class.java,
          "anime_browser_local.db"
        ).fallbackToDestructiveMigration(dropAllTables = false)
          .build()
          .also { INSTANCE = it }
      }
    }
  }
}

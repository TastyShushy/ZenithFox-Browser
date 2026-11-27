package com.example

import android.app.Application
import com.example.adblock.AdBlockEngine
import com.example.data.db.AnimeBrowserDatabase
import com.example.data.db.CustomShortcutEntity
import com.example.data.prefs.BrowserPreferencesRepository
import com.example.downloads.BrowserDownloadManager
import com.example.engine.ChromiumEngineManager
import com.example.engine.PerformanceMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AnimeBrowserApplication : Application() {

  val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  val database: AnimeBrowserDatabase by lazy {
    AnimeBrowserDatabase.getInstance(this)
  }

  val preferencesRepository: BrowserPreferencesRepository by lazy {
    BrowserPreferencesRepository(this)
  }

  val adBlockEngine: AdBlockEngine by lazy {
    AdBlockEngine(
      customBlockRuleDao = database.customBlockRuleDao(),
      siteSettingDao = database.siteSettingDao(),
      scope = applicationScope
    )
  }

  val downloadManager: BrowserDownloadManager by lazy {
    BrowserDownloadManager(
      context = this,
      downloadDao = database.downloadDao(),
      scope = applicationScope
    )
  }

  val performanceMonitor: PerformanceMonitor by lazy {
    PerformanceMonitor(this)
  }

  val chromiumEngine: ChromiumEngineManager by lazy {
    ChromiumEngineManager(this, adBlockEngine)
  }

  override fun onCreate() {
    super.onCreate()
    seedDefaultShortcutsIfEmpty()
  }

  private fun seedDefaultShortcutsIfEmpty() {
    applicationScope.launch {
      val dao = database.customShortcutDao()
      if (dao.count() == 0) {
        dao.insertAll(
          listOf(
            CustomShortcutEntity(title = "Google", url = "https://www.google.com", accentColorHex = 0xFF4285F4, sortOrder = 0),
            CustomShortcutEntity(title = "AniList", url = "https://anilist.co", accentColorHex = 0xFF00E5FF, sortOrder = 1),
            CustomShortcutEntity(title = "MyAnimeList", url = "https://myanimelist.net", accentColorHex = 0xFF2E51A2, sortOrder = 2),
            CustomShortcutEntity(title = "Crunchyroll", url = "https://www.crunchyroll.com", accentColorHex = 0xFFF47521, sortOrder = 3),
            CustomShortcutEntity(title = "HIDIVE", url = "https://www.hidive.com", accentColorHex = 0xFF00AEF0, sortOrder = 4),
            CustomShortcutEntity(title = "YouTube", url = "https://m.youtube.com", accentColorHex = 0xFFFF2E63, sortOrder = 5),
            CustomShortcutEntity(title = "Anime News", url = "https://www.animenewsnetwork.com", accentColorHex = 0xFFA855F7, sortOrder = 6),
            CustomShortcutEntity(title = "Wikipedia", url = "https://en.m.wikipedia.org", accentColorHex = 0xFF00E676, sortOrder = 7)
          )
        )
      }
    }
  }
}

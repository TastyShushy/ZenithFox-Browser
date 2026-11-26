package com.example.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.AccentPalette
import com.example.data.model.BrowserSettings
import com.example.data.model.EnhancementPreset
import com.example.data.model.PerformanceProfile
import com.example.data.model.SearchEngine
import com.example.data.model.ThemeMode
import com.example.data.model.ToolbarPosition
import com.example.data.model.VideoScalingMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.browserPrefsDataStore: DataStore<Preferences> by preferencesDataStore(
  name = "anime_browser_preferences"
)

class BrowserPreferencesRepository(private val context: Context) {

  private object Keys {
    val THEME_MODE = stringPreferencesKey("theme_mode")
    val ACCENT_PALETTE = stringPreferencesKey("accent_palette")
    val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
    val TOOLBAR_POSITION = stringPreferencesKey("toolbar_position")
    val ANIMATIONS_ENABLED = booleanPreferencesKey("animations_enabled")
    val SEARCH_ENGINE = stringPreferencesKey("search_engine")
    val HOMEPAGE_URL = stringPreferencesKey("homepage_url")
    val RESTORE_TABS = booleanPreferencesKey("restore_tabs")
    val DEFAULT_DESKTOP_MODE = booleanPreferencesKey("default_desktop_mode")
    val JAVASCRIPT_ENABLED = booleanPreferencesKey("javascript_enabled")
    val COOKIES_ENABLED = booleanPreferencesKey("cookies_enabled")
    val DEFAULT_SCALING_MODE = stringPreferencesKey("default_scaling_mode")
    val DEFAULT_ENHANCEMENT_PRESET = stringPreferencesKey("default_enhancement_preset")
    val AUTO_FULLSCREEN_VIDEO = booleanPreferencesKey("auto_fullscreen_video")
    val AUTO_LANDSCAPE_FULLSCREEN = booleanPreferencesKey("auto_landscape_fullscreen")
    val DEFAULT_PLAYBACK_SPEED = floatPreferencesKey("default_playback_speed")
    val AUTO_HIDE_CONTROLS_SEC = intPreferencesKey("auto_hide_controls_sec")
    val KEEP_SCREEN_AWAKE = booleanPreferencesKey("keep_screen_awake")
    val FLOATING_BUTTON_RIGHT = booleanPreferencesKey("floating_button_right")
    val PERFORMANCE_PROFILE = stringPreferencesKey("performance_profile")
    val SHOW_PERF_OVERLAY = booleanPreferencesKey("show_perf_overlay")
    val ADBLOCK_ENABLED = booleanPreferencesKey("adblock_enabled")
    val POPUP_BLOCK_ENABLED = booleanPreferencesKey("popup_block_enabled")
    val TRACKER_BLOCK_ENABLED = booleanPreferencesKey("tracker_block_enabled")
    val DOWNLOAD_SUBFOLDER = stringPreferencesKey("download_subfolder")
    val AUTO_PIP_ON_MINIMIZE = booleanPreferencesKey("auto_pip_on_minimize")
    val AUTO_CHECK_UPDATES = booleanPreferencesKey("auto_check_updates")
    val UPDATE_REPO_SLUG = stringPreferencesKey("update_repo_slug")
    val SKIPPED_UPDATE_VERSION = stringPreferencesKey("skipped_update_version")
  }

  val settingsFlow: Flow<BrowserSettings> = context.browserPrefsDataStore.data.map { prefs ->
    BrowserSettings(
      themeMode = prefs[Keys.THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.AMOLED,
      accentPalette = prefs[Keys.ACCENT_PALETTE]?.let { runCatching { AccentPalette.valueOf(it) }.getOrNull() } ?: AccentPalette.ZENITH_VIOLET,
      dynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: false,
      toolbarPosition = prefs[Keys.TOOLBAR_POSITION]?.let { runCatching { ToolbarPosition.valueOf(it) }.getOrNull() } ?: ToolbarPosition.TOP,
      animationsEnabled = prefs[Keys.ANIMATIONS_ENABLED] ?: true,
      searchEngine = prefs[Keys.SEARCH_ENGINE]?.let { runCatching { SearchEngine.valueOf(it) }.getOrNull() } ?: SearchEngine.GOOGLE,
      homepageUrl = prefs[Keys.HOMEPAGE_URL] ?: "about:home",
      restoreTabsOnStartup = prefs[Keys.RESTORE_TABS] ?: true,
      defaultDesktopMode = prefs[Keys.DEFAULT_DESKTOP_MODE] ?: false,
      javaScriptEnabled = prefs[Keys.JAVASCRIPT_ENABLED] ?: true,
      cookiesEnabled = prefs[Keys.COOKIES_ENABLED] ?: true,
      defaultScalingMode = prefs[Keys.DEFAULT_SCALING_MODE]?.let { runCatching { VideoScalingMode.valueOf(it) }.getOrNull() } ?: VideoScalingMode.FIT,
      defaultEnhancementPreset = prefs[Keys.DEFAULT_ENHANCEMENT_PRESET]?.let { runCatching { EnhancementPreset.valueOf(it) }.getOrNull() } ?: EnhancementPreset.OFF,
      autoFullscreenVideo = prefs[Keys.AUTO_FULLSCREEN_VIDEO] ?: false,
      autoLandscapeFullscreen = prefs[Keys.AUTO_LANDSCAPE_FULLSCREEN] ?: true,
      defaultPlaybackSpeed = prefs[Keys.DEFAULT_PLAYBACK_SPEED] ?: 1.0f,
      autoHideControlsSec = prefs[Keys.AUTO_HIDE_CONTROLS_SEC] ?: 4,
      keepScreenAwakeVideo = prefs[Keys.KEEP_SCREEN_AWAKE] ?: true,
      floatingButtonOnRight = prefs[Keys.FLOATING_BUTTON_RIGHT] ?: true,
      performanceProfile = prefs[Keys.PERFORMANCE_PROFILE]?.let { runCatching { PerformanceProfile.valueOf(it) }.getOrNull() } ?: PerformanceProfile.BALANCED,
      showPerformanceOverlay = prefs[Keys.SHOW_PERF_OVERLAY] ?: false,
      adBlockEnabled = prefs[Keys.ADBLOCK_ENABLED] ?: true,
      popupBlockEnabled = prefs[Keys.POPUP_BLOCK_ENABLED] ?: true,
      trackerBlockEnabled = prefs[Keys.TRACKER_BLOCK_ENABLED] ?: true,
      downloadSubfolder = prefs[Keys.DOWNLOAD_SUBFOLDER] ?: "ZenithFox",
      autoPipOnMinimize = prefs[Keys.AUTO_PIP_ON_MINIMIZE] ?: true,
      autoCheckUpdates = prefs[Keys.AUTO_CHECK_UPDATES] ?: true,
      updateRepoSlug = prefs[Keys.UPDATE_REPO_SLUG] ?: "",
      skippedUpdateVersion = prefs[Keys.SKIPPED_UPDATE_VERSION] ?: ""
    )
  }

  suspend fun updateSettings(transform: (BrowserSettings) -> BrowserSettings) {
    context.browserPrefsDataStore.edit { prefs ->
      val current = BrowserSettings(
        themeMode = prefs[Keys.THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.AMOLED,
        accentPalette = prefs[Keys.ACCENT_PALETTE]?.let { runCatching { AccentPalette.valueOf(it) }.getOrNull() } ?: AccentPalette.ZENITH_VIOLET,
        dynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: false,
        toolbarPosition = prefs[Keys.TOOLBAR_POSITION]?.let { runCatching { ToolbarPosition.valueOf(it) }.getOrNull() } ?: ToolbarPosition.TOP,
        animationsEnabled = prefs[Keys.ANIMATIONS_ENABLED] ?: true,
        searchEngine = prefs[Keys.SEARCH_ENGINE]?.let { runCatching { SearchEngine.valueOf(it) }.getOrNull() } ?: SearchEngine.GOOGLE,
        homepageUrl = prefs[Keys.HOMEPAGE_URL] ?: "about:home",
        restoreTabsOnStartup = prefs[Keys.RESTORE_TABS] ?: true,
        defaultDesktopMode = prefs[Keys.DEFAULT_DESKTOP_MODE] ?: false,
        javaScriptEnabled = prefs[Keys.JAVASCRIPT_ENABLED] ?: true,
        cookiesEnabled = prefs[Keys.COOKIES_ENABLED] ?: true,
        defaultScalingMode = prefs[Keys.DEFAULT_SCALING_MODE]?.let { runCatching { VideoScalingMode.valueOf(it) }.getOrNull() } ?: VideoScalingMode.FIT,
        defaultEnhancementPreset = prefs[Keys.DEFAULT_ENHANCEMENT_PRESET]?.let { runCatching { EnhancementPreset.valueOf(it) }.getOrNull() } ?: EnhancementPreset.OFF,
        autoFullscreenVideo = prefs[Keys.AUTO_FULLSCREEN_VIDEO] ?: false,
        autoLandscapeFullscreen = prefs[Keys.AUTO_LANDSCAPE_FULLSCREEN] ?: true,
        defaultPlaybackSpeed = prefs[Keys.DEFAULT_PLAYBACK_SPEED] ?: 1.0f,
        autoHideControlsSec = prefs[Keys.AUTO_HIDE_CONTROLS_SEC] ?: 4,
        keepScreenAwakeVideo = prefs[Keys.KEEP_SCREEN_AWAKE] ?: true,
        floatingButtonOnRight = prefs[Keys.FLOATING_BUTTON_RIGHT] ?: true,
        performanceProfile = prefs[Keys.PERFORMANCE_PROFILE]?.let { runCatching { PerformanceProfile.valueOf(it) }.getOrNull() } ?: PerformanceProfile.BALANCED,
        showPerformanceOverlay = prefs[Keys.SHOW_PERF_OVERLAY] ?: false,
        adBlockEnabled = prefs[Keys.ADBLOCK_ENABLED] ?: true,
        popupBlockEnabled = prefs[Keys.POPUP_BLOCK_ENABLED] ?: true,
        trackerBlockEnabled = prefs[Keys.TRACKER_BLOCK_ENABLED] ?: true,
        downloadSubfolder = prefs[Keys.DOWNLOAD_SUBFOLDER] ?: "ZenithFox",
        autoPipOnMinimize = prefs[Keys.AUTO_PIP_ON_MINIMIZE] ?: true,
        autoCheckUpdates = prefs[Keys.AUTO_CHECK_UPDATES] ?: true,
        updateRepoSlug = prefs[Keys.UPDATE_REPO_SLUG] ?: "",
        skippedUpdateVersion = prefs[Keys.SKIPPED_UPDATE_VERSION] ?: ""
      )
      val next = transform(current)
      prefs[Keys.THEME_MODE] = next.themeMode.name
      prefs[Keys.ACCENT_PALETTE] = next.accentPalette.name
      prefs[Keys.DYNAMIC_COLOR] = next.dynamicColor
      prefs[Keys.TOOLBAR_POSITION] = next.toolbarPosition.name
      prefs[Keys.ANIMATIONS_ENABLED] = next.animationsEnabled
      prefs[Keys.SEARCH_ENGINE] = next.searchEngine.name
      prefs[Keys.HOMEPAGE_URL] = next.homepageUrl
      prefs[Keys.RESTORE_TABS] = next.restoreTabsOnStartup
      prefs[Keys.DEFAULT_DESKTOP_MODE] = next.defaultDesktopMode
      prefs[Keys.JAVASCRIPT_ENABLED] = next.javaScriptEnabled
      prefs[Keys.COOKIES_ENABLED] = next.cookiesEnabled
      prefs[Keys.DEFAULT_SCALING_MODE] = next.defaultScalingMode.name
      prefs[Keys.DEFAULT_ENHANCEMENT_PRESET] = next.defaultEnhancementPreset.name
      prefs[Keys.AUTO_FULLSCREEN_VIDEO] = next.autoFullscreenVideo
      prefs[Keys.AUTO_LANDSCAPE_FULLSCREEN] = next.autoLandscapeFullscreen
      prefs[Keys.DEFAULT_PLAYBACK_SPEED] = next.defaultPlaybackSpeed
      prefs[Keys.AUTO_HIDE_CONTROLS_SEC] = next.autoHideControlsSec
      prefs[Keys.KEEP_SCREEN_AWAKE] = next.keepScreenAwakeVideo
      prefs[Keys.FLOATING_BUTTON_RIGHT] = next.floatingButtonOnRight
      prefs[Keys.PERFORMANCE_PROFILE] = next.performanceProfile.name
      prefs[Keys.SHOW_PERF_OVERLAY] = next.showPerformanceOverlay
      prefs[Keys.ADBLOCK_ENABLED] = next.adBlockEnabled
      prefs[Keys.POPUP_BLOCK_ENABLED] = next.popupBlockEnabled
      prefs[Keys.TRACKER_BLOCK_ENABLED] = next.trackerBlockEnabled
      prefs[Keys.DOWNLOAD_SUBFOLDER] = next.downloadSubfolder
      prefs[Keys.AUTO_PIP_ON_MINIMIZE] = next.autoPipOnMinimize
      prefs[Keys.AUTO_CHECK_UPDATES] = next.autoCheckUpdates
      prefs[Keys.UPDATE_REPO_SLUG] = next.updateRepoSlug
      prefs[Keys.SKIPPED_UPDATE_VERSION] = next.skippedUpdateVersion
    }
  }
}

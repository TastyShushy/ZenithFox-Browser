package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.AnimeBrowserApplication
import com.example.data.db.BookmarkEntity
import com.example.data.db.CustomShortcutEntity
import com.example.data.db.DownloadEntity
import com.example.data.db.HistoryEntity
import com.example.data.db.SavedTabEntity
import com.example.data.db.SiteSettingEntity
import com.example.data.model.BrowserSettings
import com.example.data.model.BrowserTab
import com.example.data.model.DetectedVideoStream
import com.example.data.model.EnhancementConfig
import com.example.data.model.EnhancementPreset
import com.example.data.model.HlsVariant
import com.example.data.model.PerformanceProfile
import com.example.data.model.PerformanceStats
import com.example.data.model.VideoScalingMode
import com.example.engine.GeckoEngineManager
import com.example.engine.UrlUtils
import com.example.updater.AppReleaseInfo
import com.example.updater.AppUpdateManager
import com.example.updater.UpdateDownloadState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import java.util.UUID

data class PendingSitePermission(
  val host: String,
  val permissionType: Int,
  val permissionLabel: String,
  val geckoResult: GeckoResult<Int>
)

data class PendingAndroidPermission(
  val permissions: Array<String>,
  val callback: GeckoSession.PermissionDelegate.Callback
)

class BrowserViewModel(application: Application) : AndroidViewModel(application) {

  private val app = application as AnimeBrowserApplication
  private val db = app.database
  private val prefs = app.preferencesRepository
  val geckoEngine = app.geckoEngine
  val adBlockEngine = app.adBlockEngine
  val downloadManager = app.downloadManager
  private val perfMonitor = app.performanceMonitor

  val settings: StateFlow<BrowserSettings> = prefs.settingsFlow.stateIn(
    viewModelScope, SharingStarted.Eagerly, BrowserSettings()
  )

  val bookmarks = db.bookmarkDao().observeAll()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val history = db.historyDao().observeRecent()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val recentlyWatched = db.historyDao().observeRecentlyWatched()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val downloads = downloadManager.downloadsFlow
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val shortcuts = db.customShortcutDao().observeAll()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val siteSettings = db.siteSettingDao().observeAll()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

  val customBlockRules = adBlockEngine.customRules
  val whitelistedHosts = adBlockEngine.whitelistedHosts
  val totalBlockedCount = adBlockEngine.totalBlockedCount
  val recentBlockedEvents = adBlockEngine.recentBlocked

  val videoTelemetry = geckoEngine.videoTelemetry
  val detectedStreams = geckoEngine.detectedStreams
  val extensions = geckoEngine.extensions
  val readerArticle = geckoEngine.readerArticle

  private val _hlsVariantsByUrl = MutableStateFlow<Map<String, List<HlsVariant>>>(emptyMap())
  val hlsVariantsByUrl: StateFlow<Map<String, List<HlsVariant>>> = _hlsVariantsByUrl.asStateFlow()

  private val _inspectingHlsUrl = MutableStateFlow<String?>(null)
  val inspectingHlsUrl: StateFlow<String?> = _inspectingHlsUrl.asStateFlow()

  private val _tabs = MutableStateFlow<List<BrowserTab>>(emptyList())
  val tabs: StateFlow<List<BrowserTab>> = _tabs.asStateFlow()

  private val _activeTabId = MutableStateFlow<String>("")
  val activeTabId: StateFlow<String> = _activeTabId.asStateFlow()

  private val _recentlyClosedTabs = MutableStateFlow<List<BrowserTab>>(emptyList())
  val recentlyClosedTabs: StateFlow<List<BrowserTab>> = _recentlyClosedTabs.asStateFlow()

  val activeTab: StateFlow<BrowserTab?> = combine(_tabs, _activeTabId) { list, id ->
    list.find { it.id == id } ?: list.firstOrNull()
  }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

  private val _enhancementConfig = MutableStateFlow(EnhancementConfig.forPreset(EnhancementPreset.OFF))
  val enhancementConfig: StateFlow<EnhancementConfig> = _enhancementConfig.asStateFlow()

  private val _scalingMode = MutableStateFlow(VideoScalingMode.FIT)
  val scalingMode: StateFlow<VideoScalingMode> = _scalingMode.asStateFlow()

  private val _customZoom = MutableStateFlow(1.0f)
  val customZoom: StateFlow<Float> = _customZoom.asStateFlow()

  private val _isWebFullScreen = MutableStateFlow(false)
  val isWebFullScreen: StateFlow<Boolean> = _isWebFullScreen.asStateFlow()

  private val _isForceFullScreen = MutableStateFlow(false)
  val isForceFullScreen: StateFlow<Boolean> = _isForceFullScreen.asStateFlow()

  private val _searchSuggestions = MutableStateFlow<List<String>>(emptyList())
  val searchSuggestions: StateFlow<List<String>> = _searchSuggestions.asStateFlow()

  private val _performanceStats = MutableStateFlow(PerformanceStats())
  val performanceStats: StateFlow<PerformanceStats> = _performanceStats.asStateFlow()

  private val _statusMessage = MutableStateFlow<String?>(null)
  val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

  private val _blockedRedirectUrl = MutableStateFlow<String?>(null)
  val blockedRedirectUrl: StateFlow<String?> = _blockedRedirectUrl.asStateFlow()

  private val _pendingSitePermission = MutableStateFlow<PendingSitePermission?>(null)
  val pendingSitePermission: StateFlow<PendingSitePermission?> = _pendingSitePermission.asStateFlow()

  private val _pendingAndroidPermission = MutableStateFlow<PendingAndroidPermission?>(null)
  val pendingAndroidPermission: StateFlow<PendingAndroidPermission?> = _pendingAndroidPermission.asStateFlow()

  private val _availableUpdate = MutableStateFlow<AppReleaseInfo?>(null)
  val availableUpdate: StateFlow<AppReleaseInfo?> = _availableUpdate.asStateFlow()

  private val _updateDownloadState = MutableStateFlow<UpdateDownloadState>(UpdateDownloadState.Idle)
  val updateDownloadState: StateFlow<UpdateDownloadState> = _updateDownloadState.asStateFlow()

  private val _isCheckingForUpdate = MutableStateFlow(false)
  val isCheckingForUpdate: StateFlow<Boolean> = _isCheckingForUpdate.asStateFlow()

  private var suggestionJob: Job? = null
  private var lastRecordedVideoUrl: String = ""
  private var startupUpdateCheckDone = false

  private val sessionListener = object : GeckoEngineManager.SessionEventListener {
    override fun onLocationChanged(tabId: String, url: String) {
      updateTab(tabId) { it.copy(url = url, isSuspended = false, lastAccessedAt = System.currentTimeMillis()) }
      val tab = _tabs.value.find { it.id == tabId }
      if (tab != null && !tab.isPrivate && url.startsWith("http")) {
        applySavedSitePreferences(url)
        persistOpenTabs()
      }
    }

    override fun onTitleChanged(tabId: String, title: String) {
      updateTab(tabId) { it.copy(title = title) }
      val tab = _tabs.value.find { it.id == tabId }
      if (tab != null && !tab.isPrivate && tab.url.startsWith("http")) {
        recordHistory(title, tab.url)
        persistOpenTabs()
      }
    }

    override fun onProgressChanged(tabId: String, progress: Int) {
      updateTab(tabId) { it.copy(progress = progress, isLoading = progress in 1..99) }
    }

    override fun onPageStart(tabId: String, url: String) {
      updateTab(tabId) { it.copy(url = url, isLoading = true, progress = 5) }
      if (tabId == _activeTabId.value) {
        geckoEngine.resetVideoTelemetry()
        geckoEngine.clearDetectedStreams()
        _hlsVariantsByUrl.value = emptyMap()
      }
    }

    override fun onPageStop(tabId: String, success: Boolean) {
      updateTab(tabId) { it.copy(isLoading = false, progress = 100) }
      if (tabId == _activeTabId.value) {
        geckoEngine.sendEnhancementConfig(_enhancementConfig.value)
        geckoEngine.sendScalingMode(_scalingMode.value, _customZoom.value)
      }
    }

    override fun onNavigationStateChanged(tabId: String, canGoBack: Boolean, canGoForward: Boolean) {
      updateTab(tabId) { it.copy(canGoBack = canGoBack, canGoForward = canGoForward) }
    }

    override fun onSecurityChanged(tabId: String, isSecure: Boolean) {
      updateTab(tabId) { it.copy(isSecure = isSecure) }
    }

    override fun onFullScreenChanged(tabId: String, isFullScreen: Boolean) {
      if (tabId == _activeTabId.value) {
        _isWebFullScreen.value = isFullScreen
      }
    }

    override fun onDownloadRequested(url: String, mimeType: String?) {
      startDownload(url, mimeType = mimeType)
    }

    override fun onOpenNewTab(url: String, isPrivate: Boolean) {
      createNewTab(url = url, isPrivate = isPrivate)
    }

    override fun onBlockedRedirect(blockedUrl: String) {
      _blockedRedirectUrl.value = blockedUrl
    }

    override fun onPermissionRequested(
      tabId: String,
      host: String,
      permissionType: Int,
      result: GeckoResult<Int>
    ) {
      viewModelScope.launch {
        val saved = db.siteSettingDao().getForHost(host)
        val decision = when (permissionType) {
          GeckoSession.PermissionDelegate.PERMISSION_GEOLOCATION -> saved?.locationPermission
          else -> null
        }
        when (decision) {
          "ALLOW" -> result.complete(GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW)
          "DENY" -> result.complete(GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY)
          else -> {
            val label = when (permissionType) {
              GeckoSession.PermissionDelegate.PERMISSION_GEOLOCATION -> "Location"
              GeckoSession.PermissionDelegate.PERMISSION_DESKTOP_NOTIFICATION -> "Notifications"
              GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_AUDIBLE,
              GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_INAUDIBLE -> "Media Autoplay"
              GeckoSession.PermissionDelegate.PERMISSION_MEDIA_KEY_SYSTEM_ACCESS -> "Protected Media (DRM)"
              else -> "Site Permission"
            }
            if (permissionType == GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_AUDIBLE ||
              permissionType == GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_INAUDIBLE ||
              permissionType == GeckoSession.PermissionDelegate.PERMISSION_MEDIA_KEY_SYSTEM_ACCESS
            ) {
              // Allow video playback & standard DRM playback smoothly without breaking streaming players
              result.complete(GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW)
            } else {
              _pendingSitePermission.value = PendingSitePermission(host, permissionType, label, result)
            }
          }
        }
      }
    }

    override fun onAndroidPermissionsRequested(
      permissions: Array<String>,
      callback: GeckoSession.PermissionDelegate.Callback
    ) {
      _pendingAndroidPermission.value = PendingAndroidPermission(permissions, callback)
    }
  }

  init {
    initializeTabs()
    observeSettingsSync()
    observeVideoTelemetryForHistory()
    observeDetectedStreamsForAutoInspect()
    observeSavedHtml()
    startPerformanceStatsLoop()
    scheduleStartupUpdateCheck()
  }

  private fun scheduleStartupUpdateCheck() {
    viewModelScope.launch {
      delay(2500L)
      if (startupUpdateCheckDone) return@launch
      val s = settings.value
      if (s.autoCheckUpdates && s.updateRepoSlug.isNotBlank()) {
        startupUpdateCheckDone = true
        checkForUpdates(manual = false)
      }
    }
  }

  private fun initializeTabs() {
    viewModelScope.launch {
      val currentSettings = settings.value
      val saved = if (currentSettings.restoreTabsOnStartup) db.savedTabDao().getAll() else emptyList()
      if (saved.isNotEmpty()) {
        val restored = saved.map {
          BrowserTab(
            id = it.id,
            title = it.title,
            url = it.url,
            isPrivate = false,
            groupName = it.groupName,
            isDesktopMode = it.isDesktopMode,
            isSuspended = true,
            lastAccessedAt = it.lastAccessedAt
          )
        }
        _tabs.value = restored
        _activeTabId.value = restored.first().id
      } else {
        createNewTab(url = "", isPrivate = false)
      }
    }
  }

  private fun observeSettingsSync() {
    viewModelScope.launch {
      settings.collect { s ->
        adBlockEngine.isEnabled = s.adBlockEnabled
        adBlockEngine.isPopupBlockingEnabled = s.popupBlockEnabled
        geckoEngine.syncAdBlockRulesToExtension()
        geckoEngine.updateRuntimeJavaScript(s.javaScriptEnabled)
        geckoEngine.updateRuntimeColorScheme(s.themeMode != com.example.data.model.ThemeMode.LIGHT)

        if (s.performanceProfile == PerformanceProfile.BATTERY_SAVER && _enhancementConfig.value.enabled) {
          applyEnhancementPreset(EnhancementPreset.OFF)
        }
      }
    }
    viewModelScope.launch {
      combine(customBlockRules, whitelistedHosts) { _, _ -> Unit }.collect {
        geckoEngine.syncAdBlockRulesToExtension()
      }
    }
  }

  private fun observeVideoTelemetryForHistory() {
    viewModelScope.launch {
      videoTelemetry.collect { tel ->
        if (tel.hasVideo && tel.videoWidth > 0 && tel.url.startsWith("http") && tel.url != lastRecordedVideoUrl) {
          lastRecordedVideoUrl = tel.url
          db.historyDao().markAsVideoPage(tel.url)
        }
      }
    }
  }

  private fun observeDetectedStreamsForAutoInspect() {
    viewModelScope.launch {
      detectedStreams.collect { streams ->
        for (stream in streams) {
          if (stream.isDrmProtected) continue
          if (stream.streamType == com.example.data.model.StreamType.HLS &&
            !_hlsVariantsByUrl.value.containsKey(stream.url)
          ) {
            inspectHlsQualities(stream)
          } else if (stream.streamType == com.example.data.model.StreamType.DIRECT &&
            stream.sizeBytes <= 0L
          ) {
            launch {
              val probed = downloadManager.probeDirectVideoSize(stream.url, stream.toRequestHeaders())
              if (probed > 0L) {
                geckoEngine.updateDetectedStreamSize(stream.url, probed)
              }
            }
          }
        }
      }
    }
  }

  private fun observeSavedHtml() {
    viewModelScope.launch {
      geckoEngine.savedHtmlResult.collect { pair ->
        if (pair != null) {
          val (title, html) = pair
          geckoEngine.consumeSavedHtmlResult()
          val res = UrlUtils.savePageHtmlLocally(app, title, html)
          _statusMessage.value = res.fold(
            onSuccess = { "Saved offline copy: ${it.name}" },
            onFailure = { "Could not save page: ${it.message}" }
          )
        }
      }
    }
  }

  private fun startPerformanceStatsLoop() {
    viewModelScope.launch(Dispatchers.Default) {
      while (isActive) {
        val activeSessions = geckoEngine.activeSessionCount()
        val totalTabs = _tabs.value.size
        val suspended = (totalTabs - activeSessions).coerceAtLeast(0)
        _performanceStats.value = perfMonitor.captureStats(
          activeSessions = activeSessions,
          suspendedSessions = suspended,
          gpuRendererFromWebGl = videoTelemetry.value.gpuRenderer
        )
        delay(2500L)
      }
    }
  }

  fun getSessionForTab(tab: BrowserTab): GeckoSession {
    val session = geckoEngine.getOrCreateSession(
      tabId = tab.id,
      isPrivate = tab.isPrivate,
      isDesktopMode = tab.isDesktopMode,
      listener = sessionListener
    )
    if (tab.isSuspended && tab.url.startsWith("http")) {
      updateTab(tab.id) { it.copy(isSuspended = false) }
      session.loadUri(tab.url)
    }
    val maxSessions = when (settings.value.performanceProfile) {
      PerformanceProfile.BATTERY_SAVER -> 1
      PerformanceProfile.BALANCED -> 3
      PerformanceProfile.PERFORMANCE -> 6
    }
    geckoEngine.suspendInactiveSessions(tab.id, maxSessions)
    return session
  }

  fun createNewTab(url: String = "", isPrivate: Boolean = false, groupName: String = "Default"): String {
    val id = UUID.randomUUID().toString()
    val newTab = BrowserTab(
      id = id,
      title = if (url.isBlank()) (if (isPrivate) "Private Tab" else "Home") else UrlUtils.displayHostOrUrl(url),
      url = url,
      isPrivate = isPrivate,
      groupName = groupName,
      isDesktopMode = settings.value.defaultDesktopMode,
      isSuspended = false
    )
    _tabs.update { it + newTab }
    _activeTabId.value = id
    if (url.startsWith("http")) {
      val session = getSessionForTab(newTab)
      session.loadUri(url)
    }
    persistOpenTabs()
    return id
  }

  fun selectTab(tabId: String) {
    val target = _tabs.value.find { it.id == tabId } ?: return
    val prevId = _activeTabId.value
    if (prevId != tabId && prevId.isNotBlank()) {
      // Pause media & deactivate previous session so audio doesn't bleed into the newly selected tab/home
      geckoEngine.sendVideoControl("PAUSE")
      geckoEngine.deactivateSession(prevId)
    }
    _activeTabId.value = tabId
    _isForceFullScreen.value = false
    _isWebFullScreen.value = false
    geckoEngine.resetVideoTelemetry()
    geckoEngine.clearDetectedStreams()
    _hlsVariantsByUrl.value = emptyMap()
    updateTab(tabId) { it.copy(lastAccessedAt = System.currentTimeMillis()) }
  }

  fun closeTab(tabId: String) {
    val target = _tabs.value.find { it.id == tabId } ?: return
    geckoEngine.closeSession(tabId)
    if (!target.isPrivate && target.url.startsWith("http")) {
      _recentlyClosedTabs.update { (listOf(target) + it).take(12) }
    }
    val remaining = _tabs.value.filterNot { it.id == tabId }
    if (remaining.isEmpty()) {
      _tabs.value = emptyList()
      createNewTab(url = "", isPrivate = false)
    } else {
      _tabs.value = remaining
      if (_activeTabId.value == tabId) {
        _activeTabId.value = remaining.last().id
      }
      persistOpenTabs()
    }
  }

  fun closeAllTabs(privateOnly: Boolean) {
    val toClose = _tabs.value.filter { if (privateOnly) it.isPrivate else !it.isPrivate }
    toClose.forEach { geckoEngine.closeSession(it.id) }
    val remaining = _tabs.value.filterNot { if (privateOnly) it.isPrivate else !it.isPrivate }
    if (remaining.isEmpty()) {
      _tabs.value = emptyList()
      createNewTab(url = "", isPrivate = false)
    } else {
      _tabs.value = remaining
      if (remaining.none { it.id == _activeTabId.value }) {
        _activeTabId.value = remaining.first().id
      }
      persistOpenTabs()
    }
  }

  fun restoreRecentlyClosedTab() {
    val closed = _recentlyClosedTabs.value.firstOrNull() ?: return
    _recentlyClosedTabs.update { it.drop(1) }
    createNewTab(url = closed.url, isPrivate = false, groupName = closed.groupName)
  }

  fun setTabGroup(tabId: String, groupName: String) {
    updateTab(tabId) { it.copy(groupName = groupName.ifBlank { "Default" }) }
    persistOpenTabs()
  }

  fun navigateActiveTab(rawInput: String) {
    val current = activeTab.value ?: return
    val normalized = UrlUtils.normalizeInputToUrl(rawInput, settings.value.searchEngine)
    if (normalized.isBlank()) return
    _searchSuggestions.value = emptyList()
    if (normalized == "about:home") {
      goHome()
      return
    }
    updateTab(current.id) {
      it.copy(url = normalized, title = UrlUtils.displayHostOrUrl(normalized), isLoading = true, progress = 5)
    }
    val session = getSessionForTab(current.copy(url = normalized))
    session.loadUri(normalized)
    persistOpenTabs()
  }

  fun goHome() {
    val current = activeTab.value ?: return
    // Immediately stop & close the live GeckoSession so the webpage & video audio STOP 100% when going Home!
    geckoEngine.sendVideoControl("PAUSE")
    geckoEngine.closeSession(current.id)
    _isForceFullScreen.value = false
    _isWebFullScreen.value = false
    geckoEngine.resetVideoTelemetry()
    geckoEngine.clearDetectedStreams()
    _hlsVariantsByUrl.value = emptyMap()
    updateTab(current.id) {
      it.copy(
        url = "",
        title = if (it.isPrivate) "Private Tab" else "Home",
        isLoading = false,
        progress = 0,
        canGoBack = false,
        canGoForward = false
      )
    }
    persistOpenTabs()
  }

  fun goBack(): Boolean {
    val current = activeTab.value ?: return false
    if (current.isHomePage) return false
    val session = geckoEngine.getExistingSession(current.id)
    return if (session != null && current.canGoBack) {
      session.goBack()
      true
    } else {
      goHome()
      true
    }
  }

  fun goForward() {
    val current = activeTab.value ?: return
    geckoEngine.getExistingSession(current.id)?.goForward()
  }

  fun reloadOrStop() {
    val current = activeTab.value ?: return
    val session = geckoEngine.getExistingSession(current.id) ?: return
    if (current.isLoading) session.stop() else session.reload()
  }

  fun toggleDesktopMode() {
    val current = activeTab.value ?: return
    val next = !current.isDesktopMode
    updateTab(current.id) { it.copy(isDesktopMode = next) }
    geckoEngine.setSessionDesktopMode(current.id, next)
    val host = UrlUtils.extractHost(current.url)
    if (host.isNotEmpty() && !current.isPrivate) {
      updateSiteSetting(host) { it.copy(desktopMode = next) }
    }
  }

  fun findInPage(query: String, backward: Boolean = false) {
    val current = activeTab.value ?: return
    val session = geckoEngine.getExistingSession(current.id) ?: return
    if (query.isBlank()) {
      session.finder.clear()
    } else {
      val flags = if (backward) GeckoSession.FINDER_FIND_BACKWARDS else 0
      session.finder.find(query, flags)
    }
  }

  fun clearFindInPage() {
    val current = activeTab.value ?: return
    geckoEngine.getExistingSession(current.id)?.finder?.clear()
  }

  fun translateCurrentPage() {
    val url = activeTab.value?.url.orEmpty()
    if (url.startsWith("http")) {
      navigateActiveTab(UrlUtils.buildTranslateUrl(url))
    }
  }

  fun requestReaderMode() {
    geckoEngine.requestReaderMode()
  }

  fun dismissReaderMode() {
    geckoEngine.clearReaderArticle()
  }

  fun saveCurrentWebpage() {
    geckoEngine.requestPageHtmlSave()
  }

  fun updateSearchQueryForSuggestions(query: String) {
    suggestionJob?.cancel()
    if (query.trim().length < 2) {
      _searchSuggestions.value = emptyList()
      return
    }
    suggestionJob = viewModelScope.launch {
      delay(180L)
      _searchSuggestions.value = UrlUtils.fetchSearchSuggestions(query)
    }
  }

  // --- Video Scaling, Force Fullscreen, & Anime4K WebGL2 Enhancement ---

  fun setScalingMode(mode: VideoScalingMode, customZoomValue: Float = _customZoom.value) {
    _scalingMode.value = mode
    _customZoom.value = customZoomValue.coerceIn(0.5f, 3.0f)
    geckoEngine.sendScalingMode(mode, _customZoom.value)
    val host = UrlUtils.extractHost(activeTab.value?.url.orEmpty())
    if (host.isNotEmpty()) {
      updateSiteSetting(host) { it.copy(scalingMode = mode.name, customZoom = _customZoom.value) }
    }
  }

  fun setForceFullscreen(enabled: Boolean, hideSiteControls: Boolean = true) {
    _isForceFullScreen.value = enabled
    geckoEngine.sendForceFullscreen(enabled, hideSiteControls)
  }

  fun exitAnyFullscreen() {
    if (_isForceFullScreen.value) {
      setForceFullscreen(false, false)
    }
    if (_isWebFullScreen.value) {
      activeTab.value?.let { geckoEngine.getExistingSession(it.id)?.exitFullScreen() }
      _isWebFullScreen.value = false
    }
  }

  fun applyEnhancementPreset(preset: EnhancementPreset) {
    val current = _enhancementConfig.value
    val keepFrameGen = if (preset == EnhancementPreset.OFF) false else current.frameGenEnabled
    val cfg = EnhancementConfig.forPreset(
      preset = preset,
      frameGenEnabled = keepFrameGen,
      targetFps = current.targetFps,
      splitCompare = if (preset == EnhancementPreset.OFF) false else current.splitCompare,
      splitPosition = current.splitPosition
    )
    _enhancementConfig.value = cfg
    geckoEngine.sendEnhancementConfig(cfg)
    val host = UrlUtils.extractHost(activeTab.value?.url.orEmpty())
    if (host.isNotEmpty()) {
      updateSiteSetting(host) { it.copy(enhancementPreset = preset.name) }
    }
  }

  fun updateCustomEnhancement(transform: (EnhancementConfig) -> EnhancementConfig) {
    val prev = _enhancementConfig.value
    val raw = transform(prev)
    val onlySplitChanged = raw.upscaleStrength == prev.upscaleStrength &&
      raw.sharpen == prev.sharpen &&
      raw.deband == prev.deband &&
      raw.denoise == prev.denoise &&
      raw.edgeEnhance == prev.edgeEnhance &&
      raw.vibrance == prev.vibrance &&
      raw.resolutionScale == prev.resolutionScale
    val updated = if (onlySplitChanged && prev.enabled) {
      raw.copy(enabled = true)
    } else if (onlySplitChanged && !prev.enabled) {
      EnhancementConfig.forPreset(
        preset = EnhancementPreset.HIGH_QUALITY,
        frameGenEnabled = prev.frameGenEnabled,
        targetFps = prev.targetFps,
        splitCompare = raw.splitCompare,
        splitPosition = raw.splitPosition
      )
    } else {
      raw.copy(enabled = true, preset = EnhancementPreset.CUSTOM)
    }
    _enhancementConfig.value = updated
    geckoEngine.sendEnhancementConfig(updated)
  }

  fun toggleEnhancementMaster(enabled: Boolean) {
    val current = _enhancementConfig.value
    val next = if (enabled) {
      val preset = settings.value.defaultEnhancementPreset.takeIf { it != EnhancementPreset.OFF }
        ?: EnhancementPreset.HIGH_QUALITY
      EnhancementConfig.forPreset(
        preset = preset,
        frameGenEnabled = current.frameGenEnabled,
        targetFps = current.targetFps,
        splitCompare = current.splitCompare,
        splitPosition = current.splitPosition
      )
    } else {
      EnhancementConfig.forPreset(
        preset = EnhancementPreset.OFF,
        frameGenEnabled = false,
        targetFps = current.targetFps,
        splitCompare = false,
        splitPosition = current.splitPosition
      )
    }
    _enhancementConfig.value = next
    geckoEngine.sendEnhancementConfig(next)
  }

  fun toggleFrameGeneration(enabled: Boolean) {
    val current = _enhancementConfig.value
    val next = if (enabled && !current.enabled) {
      val preset = settings.value.defaultEnhancementPreset.takeIf { it != EnhancementPreset.OFF }
        ?: EnhancementPreset.HIGH_QUALITY
      EnhancementConfig.forPreset(
        preset = preset,
        frameGenEnabled = true,
        targetFps = current.targetFps,
        splitCompare = current.splitCompare,
        splitPosition = current.splitPosition
      )
    } else {
      current.copy(frameGenEnabled = enabled)
    }
    _enhancementConfig.value = next
    geckoEngine.sendEnhancementConfig(next)
  }

  fun setTargetFps(fps: Int) {
    val clamped = fps.coerceIn(30, 120)
    val next = _enhancementConfig.value.copy(targetFps = clamped)
    _enhancementConfig.value = next
    geckoEngine.sendEnhancementConfig(next)
  }

  fun videoTogglePlay() = geckoEngine.sendVideoControl("TOGGLE_PLAY")
  fun videoSeekBy(seconds: Double) = geckoEngine.sendVideoControl("SEEK_BY", seconds = seconds)
  fun videoSeekTo(seconds: Double) = geckoEngine.sendVideoControl("SEEK_TO", seconds = seconds)
  fun videoSetSpeed(speed: Float) = geckoEngine.sendVideoControl("SET_SPEED", speed = speed)
  fun videoToggleMute() = geckoEngine.sendVideoControl("TOGGLE_MUTE")
  fun videoToggleSubtitles() = geckoEngine.sendVideoControl("TOGGLE_SUBTITLES")

  // --- Bookmarks, History, Shortcuts ---

  fun toggleBookmarkForActiveTab() {
    val tab = activeTab.value ?: return
    if (!tab.url.startsWith("http")) return
    viewModelScope.launch {
      val existing = db.bookmarkDao().findByUrl(tab.url)
      if (existing != null) {
        db.bookmarkDao().deleteByUrl(tab.url)
        _statusMessage.value = "Removed from Bookmarks"
      } else {
        db.bookmarkDao().insert(BookmarkEntity(title = tab.title.ifBlank { tab.url }, url = tab.url))
        _statusMessage.value = "Saved to Bookmarks"
      }
    }
  }

  fun addBookmark(title: String, url: String, folder: String = "Anime") {
    val normalized = UrlUtils.normalizeInputToUrl(url, settings.value.searchEngine)
    if (normalized.isBlank()) return
    viewModelScope.launch {
      db.bookmarkDao().insert(BookmarkEntity(title = title.ifBlank { UrlUtils.displayHostOrUrl(normalized) }, url = normalized, folder = folder))
    }
  }

  fun deleteBookmark(id: Long) {
    viewModelScope.launch { db.bookmarkDao().deleteById(id) }
  }

  fun deleteHistoryItem(id: Long) {
    viewModelScope.launch { db.historyDao().deleteById(id) }
  }

  fun clearHistory() {
    viewModelScope.launch { db.historyDao().clearAll() }
  }

  fun addCustomShortcut(title: String, url: String, accentHex: Long) {
    val normalized = UrlUtils.normalizeInputToUrl(url, settings.value.searchEngine)
    if (normalized.isBlank()) return
    viewModelScope.launch {
      db.customShortcutDao().insert(
        CustomShortcutEntity(
          title = title.ifBlank { UrlUtils.displayHostOrUrl(normalized) },
          url = normalized,
          accentColorHex = accentHex,
          sortOrder = shortcuts.value.size
        )
      )
    }
  }

  fun deleteShortcut(id: Long) {
    viewModelScope.launch { db.customShortcutDao().deleteById(id) }
  }

  // --- Downloads ---

  fun startDownload(
    url: String,
    fileName: String? = null,
    mimeType: String? = null,
    headers: Map<String, String> = emptyMap()
  ) {
    viewModelScope.launch {
      val result = downloadManager.enqueueDownload(
        url = url,
        suggestedFileName = fileName,
        mimeType = mimeType,
        subfolder = settings.value.downloadSubfolder,
        headers = headers
      )
      _statusMessage.value = result.fold(
        onSuccess = { "Download started — track progress in Downloads" },
        onFailure = { it.message ?: "Download could not be started" }
      )
    }
  }

  fun inspectHlsQualities(stream: DetectedVideoStream) {
    viewModelScope.launch {
      _inspectingHlsUrl.value = stream.url
      val res = downloadManager.inspectHlsQualities(
        playlistUrl = stream.url,
        headers = stream.toRequestHeaders(),
        fallbackDurationSec = videoTelemetry.value.durationSec
      )
      _inspectingHlsUrl.value = null
      res.fold(
        onSuccess = { variants ->
          _hlsVariantsByUrl.update { it + (stream.url to variants) }
        },
        onFailure = { err ->
          _statusMessage.value = err.message ?: "Could not inspect HLS qualities"
        }
      )
    }
  }

  fun downloadDetectedStream(
    stream: DetectedVideoStream,
    customFileName: String,
    variantUrl: String?
  ) {
    val targetUrl = variantUrl?.takeIf { it.startsWith("http") } ?: stream.url
    startDownload(
      url = targetUrl,
      fileName = customFileName,
      mimeType = stream.mimeType,
      headers = stream.toRequestHeaders()
    )
  }

  fun cancelDownload(item: DownloadEntity) {
    viewModelScope.launch { downloadManager.cancelDownload(item) }
  }

  fun retryDownload(item: DownloadEntity) {
    viewModelScope.launch {
      val res = downloadManager.retryDownload(item, settings.value.downloadSubfolder)
      if (res.isFailure) _statusMessage.value = res.exceptionOrNull()?.message
    }
  }

  fun deleteDownload(item: DownloadEntity) {
    viewModelScope.launch { downloadManager.deleteDownload(item) }
  }

  fun openDownload(item: DownloadEntity) {
    if (!downloadManager.openDownloadedFile(item)) {
      _statusMessage.value = "No app found to open ${item.fileName}"
    }
  }

  // --- AdBlock, Extensions, Permissions, Settings ---

  fun addCustomBlockDomain(domain: String) {
    viewModelScope.launch { adBlockEngine.addCustomRule(domain) }
  }

  fun removeCustomBlockDomain(domain: String) {
    viewModelScope.launch { adBlockEngine.removeCustomRule(domain) }
  }

  fun toggleHostWhitelist(host: String) {
    viewModelScope.launch { adBlockEngine.toggleHostWhitelist(host) }
  }

  fun toggleExtension(extensionId: String, enable: Boolean) {
    geckoEngine.toggleExtension(extensionId, enable)
  }

  fun updateSettings(transform: (BrowserSettings) -> BrowserSettings) {
    viewModelScope.launch { prefs.updateSettings(transform) }
  }

  fun updateSitePermission(host: String, transform: (SiteSettingEntity) -> SiteSettingEntity) {
    updateSiteSetting(host, transform)
  }

  fun deleteSiteSetting(host: String) {
    viewModelScope.launch { db.siteSettingDao().deleteForHost(host) }
  }

  fun clearBrowsingData(history: Boolean, cookies: Boolean, cache: Boolean, siteData: Boolean) {
    viewModelScope.launch {
      if (history) db.historyDao().clearAll()
      geckoEngine.clearBrowserData(cookies, cache, siteData)
      _statusMessage.value = "Selected browsing data cleared"
    }
  }

  fun resolvePendingSitePermission(allow: Boolean, remember: Boolean) {
    val pending = _pendingSitePermission.value ?: return
    _pendingSitePermission.value = null
    pending.geckoResult.complete(
      if (allow) GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW
      else GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY
    )
    if (remember && pending.host.isNotBlank()) {
      val value = if (allow) "ALLOW" else "DENY"
      if (pending.permissionType == GeckoSession.PermissionDelegate.PERMISSION_GEOLOCATION) {
        updateSiteSetting(pending.host) { it.copy(locationPermission = value) }
      }
    }
  }

  fun resolvePendingAndroidPermission(granted: Boolean) {
    val pending = _pendingAndroidPermission.value ?: return
    _pendingAndroidPermission.value = null
    if (granted) pending.callback.grant() else pending.callback.reject()
  }

  fun shareUrl(context: Context, title: String, url: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
      type = "text/plain"
      putExtra(Intent.EXTRA_SUBJECT, title)
      putExtra(Intent.EXTRA_TEXT, url)
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(Intent.createChooser(intent, "Share webpage").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
  }

  fun clearStatusMessage() {
    _statusMessage.value = null
  }

  fun clearBlockedRedirectBanner() {
    _blockedRedirectUrl.value = null
  }

  fun openBlockedUrlAnyway(url: String) {
    _blockedRedirectUrl.value = null
    if (url.startsWith("http://") || url.startsWith("https://")) {
      navigateActiveTab(url)
    }
  }

  // --- App Lifecycle & PiP Surface Recovery (prevents black screen on minimize/resume!) ---
  fun onAppForegrounded() {
    val current = activeTab.value ?: return
    if (!current.isHomePage) {
      geckoEngine.reactivateSession(current.id)
    }
  }

  fun onAppBackgrounded(inPip: Boolean) {
    if (inPip) return
    val current = activeTab.value ?: return
    if (!current.isHomePage) {
      // Gently pause non-PiP background audio without tearing down the active session
      geckoEngine.sendVideoControl("PAUSE")
    }
  }

  // --- Video Player Essential Quick Controls ---
  fun toggleVideoPlayPause() {
    geckoEngine.sendVideoControl("TOGGLE_PLAY")
  }

  fun seekVideoBy(seconds: Double) {
    geckoEngine.sendVideoControl("SEEK_BY", seconds = seconds)
  }

  fun setVideoPlaybackSpeed(speed: Float) {
    geckoEngine.sendVideoControl("SET_SPEED", speed = speed)
  }

  fun toggleVideoMute() {
    geckoEngine.sendVideoControl("TOGGLE_MUTE")
  }

  fun toggleVideoSubtitles() {
    geckoEngine.sendVideoControl("TOGGLE_SUBTITLES")
  }

  // --- Built-In GitHub Releases APK Auto-Updater ---
  fun checkForUpdates(manual: Boolean = false) {
    if (_isCheckingForUpdate.value) return
    val s = settings.value
    val slug = AppUpdateManager.normalizeRepoSlug(s.updateRepoSlug)
    if (slug.isBlank()) {
      if (manual) {
        _statusMessage.value = "Enter your GitHub repo (owner/repo) in Settings → App Updates first"
      }
      return
    }
    viewModelScope.launch {
      _isCheckingForUpdate.value = true
      val result = AppUpdateManager.checkLatestRelease(slug)
      _isCheckingForUpdate.value = false
      result.onSuccess { release ->
        if (release == null) {
          if (manual) _statusMessage.value = "No published GitHub releases found for $slug"
          return@onSuccess
        }
        val isNewer = AppUpdateManager.isNewerVersion(release.versionTag)
        val isSkipped = !manual && s.skippedUpdateVersion.equals(release.versionTag, ignoreCase = true)
        if (isNewer && !isSkipped) {
          _updateDownloadState.value = UpdateDownloadState.Idle
          _availableUpdate.value = release
        } else if (manual) {
          _statusMessage.value = "You are on the latest version (v${com.example.BuildConfig.VERSION_NAME})"
        }
      }.onFailure { err ->
        if (manual) {
          _statusMessage.value = err.message ?: "Could not check GitHub for updates"
        }
      }
    }
  }

  fun installAvailableUpdate() {
    val release = _availableUpdate.value ?: return
    val existing = (_updateDownloadState.value as? UpdateDownloadState.ReadyToInstall)?.apkFile
    if (existing != null && existing.exists()) {
      val launched = AppUpdateManager.launchApkInstaller(app, existing)
      if (!launched) {
        _statusMessage.value = "Allow 'Install unknown apps' for ZenithFox Browser, then tap Install Update again"
      }
      return
    }

    viewModelScope.launch {
      _updateDownloadState.value = UpdateDownloadState.Downloading(0, 0L, release.apkSizeBytes)
      val dlResult = AppUpdateManager.downloadApk(app, release) { pct, downloaded, total ->
        _updateDownloadState.value = UpdateDownloadState.Downloading(pct, downloaded, total)
      }
      dlResult.onSuccess { apkFile ->
        _updateDownloadState.value = UpdateDownloadState.ReadyToInstall(apkFile)
        val launched = AppUpdateManager.launchApkInstaller(app, apkFile)
        if (!launched) {
          _statusMessage.value = "Allow 'Install unknown apps' for ZenithFox Browser, then tap Install Update again"
        }
      }.onFailure { err ->
        _updateDownloadState.value = UpdateDownloadState.Error(err.message ?: "Update download failed")
      }
    }
  }

  fun skipAvailableUpdate() {
    val tag = _availableUpdate.value?.versionTag ?: return
    _availableUpdate.value = null
    _updateDownloadState.value = UpdateDownloadState.Idle
    updateSettings { it.copy(skippedUpdateVersion = tag) }
    _statusMessage.value = "Skipped update $tag"
  }

  fun ignoreAvailableUpdate() {
    _availableUpdate.value = null
    _updateDownloadState.value = UpdateDownloadState.Idle
  }

  private fun applySavedSitePreferences(url: String) {
    val host = UrlUtils.extractHost(url)
    if (host.isBlank()) return
    viewModelScope.launch {
      val saved = db.siteSettingDao().getForHost(host)
      val mode = saved?.scalingMode?.let { runCatching { VideoScalingMode.valueOf(it) }.getOrNull() }
        ?: settings.value.defaultScalingMode
      val zoom = saved?.customZoom ?: 1.0f
      val preset = saved?.enhancementPreset?.let { runCatching { EnhancementPreset.valueOf(it) }.getOrNull() }
        ?: settings.value.defaultEnhancementPreset

      _scalingMode.value = mode
      _customZoom.value = zoom
      val currentCfg = _enhancementConfig.value
      val isBatterySaver = settings.value.performanceProfile == PerformanceProfile.BATTERY_SAVER
      val effectivePreset = if (isBatterySaver) EnhancementPreset.OFF else preset
      _enhancementConfig.value = EnhancementConfig.forPreset(
        preset = effectivePreset,
        frameGenEnabled = if (isBatterySaver || effectivePreset == EnhancementPreset.OFF) false else currentCfg.frameGenEnabled,
        targetFps = currentCfg.targetFps
      )
      geckoEngine.sendScalingMode(mode, zoom)
      geckoEngine.sendEnhancementConfig(_enhancementConfig.value)
    }
  }

  private fun updateSiteSetting(host: String, transform: (SiteSettingEntity) -> SiteSettingEntity) {
    viewModelScope.launch {
      val current = db.siteSettingDao().getForHost(host) ?: SiteSettingEntity(host = host)
      db.siteSettingDao().upsert(transform(current).copy(updatedAt = System.currentTimeMillis()))
    }
  }

  private fun recordHistory(title: String, url: String) {
    viewModelScope.launch {
      db.historyDao().insert(
        HistoryEntity(
          title = title.ifBlank { UrlUtils.displayHostOrUrl(url) },
          url = url,
          host = UrlUtils.extractHost(url)
        )
      )
    }
  }

  private fun persistOpenTabs() {
    viewModelScope.launch {
      val nonPrivate = _tabs.value.filter { !it.isPrivate }.map {
        SavedTabEntity(
          id = it.id,
          title = it.title,
          url = it.url,
          groupName = it.groupName,
          isDesktopMode = it.isDesktopMode,
          lastAccessedAt = it.lastAccessedAt
        )
      }
      db.savedTabDao().clearAll()
      if (nonPrivate.isNotEmpty()) {
        db.savedTabDao().upsertAll(nonPrivate)
      }
    }
  }

  private fun updateTab(tabId: String, transform: (BrowserTab) -> BrowserTab) {
    _tabs.update { list -> list.map { if (it.id == tabId) transform(it) else it } }
  }
}

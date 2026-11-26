package com.example.engine

import android.content.Context
import android.util.Log
import com.example.adblock.AdBlockEngine
import com.example.data.model.DetectedVideoStream
import com.example.data.model.EnhancementConfig
import com.example.data.model.ExtensionInfo
import com.example.data.model.ReaderArticle
import com.example.data.model.StreamType
import com.example.data.model.VideoScalingMode
import com.example.data.model.VideoTelemetry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.StorageController
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController
import org.mozilla.geckoview.WebResponse
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages the Mozilla GeckoView runtime, per-tab GeckoSessions (with lazy tab suspension),
 * built-in WebExtensions (Anime4K WebGL2 Enhancer + Ad & Tracker Shield), and native messaging.
 */
class GeckoEngineManager(
  private val context: Context,
  private val adBlockEngine: AdBlockEngine
) {
  companion object {
    private const val TAG = "GeckoEngineManager"
    const val EXT_ENHANCER_ID = "anime-enhancer@animebrowser.app"
    const val EXT_ENHANCER_URI = "resource://android/assets/extensions/anime_enhancer/"
    const val EXT_ADBLOCK_ID = "anime-adblock@animebrowser.app"
    const val EXT_ADBLOCK_URI = "resource://android/assets/extensions/anime_adblock/"
    const val EXT_DOWNLOADER_ID = "anime-downloader@animebrowser.app"
    const val EXT_DOWNLOADER_URI = "resource://android/assets/extensions/anime_downloader/"
  }

  interface SessionEventListener {
    fun onLocationChanged(tabId: String, url: String)
    fun onTitleChanged(tabId: String, title: String)
    fun onProgressChanged(tabId: String, progress: Int)
    fun onPageStart(tabId: String, url: String)
    fun onPageStop(tabId: String, success: Boolean)
    fun onNavigationStateChanged(tabId: String, canGoBack: Boolean, canGoForward: Boolean)
    fun onSecurityChanged(tabId: String, isSecure: Boolean)
    fun onFullScreenChanged(tabId: String, isFullScreen: Boolean)
    fun onDownloadRequested(url: String, mimeType: String?)
    fun onOpenNewTab(url: String, isPrivate: Boolean)
    fun onBlockedRedirect(blockedUrl: String)
    fun onPermissionRequested(tabId: String, host: String, permissionType: Int, result: GeckoResult<Int>)
    fun onAndroidPermissionsRequested(permissions: Array<String>, callback: GeckoSession.PermissionDelegate.Callback)
  }

  private var runtime: GeckoRuntime? = null
  private val sessions = ConcurrentHashMap<String, GeckoSession>()
  private val installedWebExtensions = ConcurrentHashMap<String, WebExtension>()
  private var enhancerPort: WebExtension.Port? = null
  private var adBlockPort: WebExtension.Port? = null
  private var downloaderPort: WebExtension.Port? = null
  private var cachedEnhancementConfig: EnhancementConfig? = null
  private var cachedScaling: Pair<VideoScalingMode, Float>? = null

  private val _videoTelemetry = MutableStateFlow(VideoTelemetry())
  val videoTelemetry: StateFlow<VideoTelemetry> = _videoTelemetry.asStateFlow()

  private val _detectedStreams = MutableStateFlow<List<DetectedVideoStream>>(emptyList())
  val detectedStreams: StateFlow<List<DetectedVideoStream>> = _detectedStreams.asStateFlow()

  private val _readerArticle = MutableStateFlow<ReaderArticle?>(null)
  val readerArticle: StateFlow<ReaderArticle?> = _readerArticle.asStateFlow()

  private val _savedHtmlResult = MutableStateFlow<Pair<String, String>?>(null)
  val savedHtmlResult: StateFlow<Pair<String, String>?> = _savedHtmlResult.asStateFlow()

  private val _extensions = MutableStateFlow<List<ExtensionInfo>>(defaultExtensionCatalog())
  val extensions: StateFlow<List<ExtensionInfo>> = _extensions.asStateFlow()

  @Synchronized
  fun ensureRuntime(): GeckoRuntime {
    runtime?.let { return it }
    val contentBlockingSettings = ContentBlocking.Settings.Builder()
      .antiTracking(
        ContentBlocking.AntiTracking.DEFAULT or ContentBlocking.AntiTracking.STP
      )
      .safeBrowsing(ContentBlocking.SafeBrowsing.DEFAULT)
      .cookieBehavior(ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY)
      .build()

    val settings = GeckoRuntimeSettings.Builder()
      .javaScriptEnabled(true)
      .contentBlocking(contentBlockingSettings)
      .preferredColorScheme(GeckoRuntimeSettings.COLOR_SCHEME_DARK)
      .automaticFontSizeAdjustment(true)
      .consoleOutput(false)
      .build()

    val created = GeckoRuntime.create(context.applicationContext, settings)
    runtime = created
    installBuiltInExtensions(created)
    return created
  }

  private fun installBuiltInExtensions(rt: GeckoRuntime) {
    val controller = rt.webExtensionController

    // 1. Anime4K WebGL2 Enhancer & Video Controller
    controller.ensureBuiltIn(EXT_ENHANCER_URI, EXT_ENHANCER_ID).accept(
      { ext ->
        if (ext != null) {
          installedWebExtensions[EXT_ENHANCER_ID] = ext
          attachEnhancerBridge(ext)
          markExtensionLoaded(EXT_ENHANCER_ID, true)
        }
      },
      { err ->
        Log.e(TAG, "Failed to load built-in enhancer extension", err)
      }
    )

    // 2. Anime Ad & Tracker Shield
    controller.ensureBuiltIn(EXT_ADBLOCK_URI, EXT_ADBLOCK_ID).accept(
      { ext ->
        if (ext != null) {
          installedWebExtensions[EXT_ADBLOCK_ID] = ext
          attachAdBlockBridge(ext)
          markExtensionLoaded(EXT_ADBLOCK_ID, true)
        }
      },
      { err ->
        Log.e(TAG, "Failed to load built-in adblock extension", err)
      }
    )

    // 3. Anime Video Downloader (Clean-room MAX Video Downloader equivalent for Android)
    controller.ensureBuiltIn(EXT_DOWNLOADER_URI, EXT_DOWNLOADER_ID).accept(
      { ext ->
        if (ext != null) {
          installedWebExtensions[EXT_DOWNLOADER_ID] = ext
          attachDownloaderBridge(ext)
          markExtensionLoaded(EXT_DOWNLOADER_ID, true)
        }
      },
      { err ->
        Log.e(TAG, "Failed to load built-in video downloader extension", err)
      }
    )
  }

  private fun attachEnhancerBridge(extension: WebExtension) {
    extension.setMessageDelegate(object : WebExtension.MessageDelegate {
      override fun onConnect(port: WebExtension.Port) {
        enhancerPort = port
        // Push latest enhancement & scaling settings immediately on connect
        cachedEnhancementConfig?.let { sendEnhancementConfig(it) }
        cachedScaling?.let { (m, z) -> sendScalingMode(m, z) }
        port.setDelegate(object : WebExtension.PortDelegate {
          override fun onPortMessage(message: Any, port: WebExtension.Port) {
            if (message is JSONObject) {
              handleEnhancerMessage(message)
            }
          }

          override fun onDisconnect(port: WebExtension.Port) {
            if (enhancerPort === port) enhancerPort = null
          }
        })
      }
    }, "animeBrowser")
  }

  private fun attachAdBlockBridge(extension: WebExtension) {
    extension.setMessageDelegate(object : WebExtension.MessageDelegate {
      override fun onConnect(port: WebExtension.Port) {
        adBlockPort = port
        syncAdBlockRulesToExtension()
        port.setDelegate(object : WebExtension.PortDelegate {
          override fun onPortMessage(message: Any, port: WebExtension.Port) {
            if (message is JSONObject) {
              when (message.optString("type")) {
                "ADBLOCK_READY" -> syncAdBlockRulesToExtension()
                "REQUEST_BLOCKED" -> {
                  adBlockEngine.recordBlocked(
                    domain = message.optString("domain", "tracker"),
                    pageHost = message.optString("pageHost", ""),
                    resourceType = message.optString("resourceType", "script")
                  )
                }
              }
            }
          }

          override fun onDisconnect(port: WebExtension.Port) {
            if (adBlockPort === port) adBlockPort = null
          }
        })
      }
    }, "animeAdBlock")
  }

  private fun attachDownloaderBridge(extension: WebExtension) {
    extension.setMessageDelegate(object : WebExtension.MessageDelegate {
      override fun onConnect(port: WebExtension.Port) {
        downloaderPort = port
        port.setDelegate(object : WebExtension.PortDelegate {
          override fun onPortMessage(message: Any, port: WebExtension.Port) {
            if (message is JSONObject && message.optString("type") == "STREAM_DETECTED") {
              val d = message.optJSONObject("data") ?: return
              val url = d.optString("url", "")
              if (!url.startsWith("http")) return
              val rawType = d.optString("streamType", "")
              val type = when (rawType) {
                "HLS" -> StreamType.HLS
                "DIRECT" -> StreamType.DIRECT
                else -> return // Ignore webpages, subtitles, and non-video entries
              }
              val stream = DetectedVideoStream(
                url = url,
                title = d.optString("title", "Anime Video"),
                streamType = type,
                mimeType = d.optString("mimeType", "video/mp4"),
                sizeBytes = d.optLong("sizeBytes", 0L),
                qualityLabel = d.optString("qualityLabel", ""),
                referer = d.optString("referer", ""),
                origin = d.optString("origin", ""),
                userAgent = d.optString("userAgent", ""),
                cookie = d.optString("cookie", ""),
                isDrmProtected = d.optBoolean("isDrmProtected", false)
              )
              _detectedStreams.update { list ->
                if (list.any { it.url == stream.url }) list
                else {
                  // Keep at most 4 distinct active video streams, prioritizing HLS master / newest playing video
                  val isMaster = stream.streamType == StreamType.HLS &&
                    (stream.url.contains("master", true) || stream.url.contains("manifest", true))
                  val merged = if (isMaster) listOf(stream) + list else list + stream
                  merged.distinctBy { it.url }.take(4)
                }
              }
            }
          }

          override fun onDisconnect(port: WebExtension.Port) {
            if (downloaderPort === port) downloaderPort = null
          }
        })
      }
    }, "animeDownloader")
  }

  private fun handleEnhancerMessage(json: JSONObject) {
    when (json.optString("type")) {
      "EXTENSION_READY" -> {
        cachedEnhancementConfig?.let { sendEnhancementConfig(it) }
        cachedScaling?.let { (m, z) -> sendScalingMode(m, z) }
      }
      "VIDEO_TELEMETRY" -> {
        val d = json.optJSONObject("data") ?: return
        val hasVideo = d.optBoolean("hasVideo", false)
        if (!hasVideo) {
          _videoTelemetry.update { prev ->
            prev.copy(hasVideo = false, isPlaying = false)
          }
          return
        }
        val mode = runCatching {
          VideoScalingMode.valueOf(d.optString("scalingMode", "FIT"))
        }.getOrDefault(VideoScalingMode.FIT)

        _videoTelemetry.value = VideoTelemetry(
          hasVideo = true,
          url = d.optString("url", ""),
          videoWidth = d.optInt("videoWidth", 0),
          videoHeight = d.optInt("videoHeight", 0),
          isPlaying = d.optBoolean("isPlaying", false),
          currentTimeSec = d.optDouble("currentTime", 0.0),
          durationSec = d.optDouble("duration", 0.0),
          playbackRate = d.optDouble("playbackRate", 1.0).toFloat(),
          muted = d.optBoolean("muted", false),
          sourceFps = d.optInt("sourceFps", 0),
          outputFps = d.optInt("outputFps", 0),
          enhancementStatus = d.optString("enhancementStatus", "IDLE"),
          gpuRenderer = d.optString("gpuRenderer", "WebGL GPU"),
          webGpuSupported = d.optBoolean("webGpuSupported", false),
          isDrmProtected = d.optBoolean("isDrmProtected", false),
          hasSubtitles = d.optBoolean("hasSubtitles", false),
          subtitlesVisible = d.optBoolean("subtitlesVisible", false),
          bufferedPercent = d.optInt("bufferedPercent", 0),
          scalingMode = mode,
          customZoom = d.optDouble("customZoom", 1.0).toFloat(),
          forceFullscreen = d.optBoolean("forceFullscreen", false),
          frameGenActive = d.optBoolean("frameGenActive", false),
          frameGenAutoDisabled = d.optBoolean("frameGenAutoDisabled", false)
        )
      }
      "READER_MODE_CONTENT" -> {
        val d = json.optJSONObject("data") ?: return
        val arr = d.optJSONArray("paragraphs") ?: JSONArray()
        val list = buildList {
          for (i in 0 until arr.length()) {
            val p = arr.optString(i)
            if (p.isNotBlank()) add(p)
          }
        }
        _readerArticle.value = ReaderArticle(
          title = d.optString("title", "Reader View"),
          url = d.optString("url", ""),
          paragraphs = list
        )
      }
      "PAGE_HTML_CONTENT" -> {
        val d = json.optJSONObject("data") ?: return
        _savedHtmlResult.value = d.optString("title", "webpage") to d.optString("html", "")
      }
    }
  }

  fun getOrCreateSession(
    tabId: String,
    isPrivate: Boolean,
    isDesktopMode: Boolean,
    listener: SessionEventListener
  ): GeckoSession {
    sessions[tabId]?.let { existing ->
      if (!existing.isOpen) {
        existing.open(ensureRuntime())
      }
      existing.setActive(true)
      return existing
    }

    val rt = ensureRuntime()
    val sessionSettings = GeckoSessionSettings.Builder()
      .usePrivateMode(isPrivate)
      .userAgentMode(
        if (isDesktopMode) GeckoSessionSettings.USER_AGENT_MODE_DESKTOP
        else GeckoSessionSettings.USER_AGENT_MODE_MOBILE
      )
      .useTrackingProtection(true)
      .build()

    val session = GeckoSession(sessionSettings)
    var currentTabUrl = ""

    session.navigationDelegate = object : GeckoSession.NavigationDelegate {
      override fun onLocationChange(
        session: GeckoSession,
        url: String?,
        perms: MutableList<GeckoSession.PermissionDelegate.ContentPermission>,
        hasUserGesture: Boolean
      ) {
        val safeUrl = url.orEmpty()
        currentTabUrl = safeUrl
        listener.onLocationChanged(tabId, safeUrl)
      }

      override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
        listener.onNavigationStateChanged(tabId, canGoBack, lastForward[tabId] ?: false)
        lastBack[tabId] = canGoBack
      }

      override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) {
        listener.onNavigationStateChanged(tabId, lastBack[tabId] ?: false, canGoForward)
        lastForward[tabId] = canGoForward
      }

      override fun onLoadRequest(
        session: GeckoSession,
        request: GeckoSession.NavigationDelegate.LoadRequest
      ): GeckoResult<AllowOrDeny> {
        val uri = request.uri
        val sourcePage = request.triggerUri?.takeIf { it.isNotBlank() } ?: currentTabUrl
        if (adBlockEngine.shouldBlockNavigation(
            targetUrl = uri,
            currentPageUrl = sourcePage,
            isRedirectOrPopup = request.isRedirect || request.target == GeckoSession.NavigationDelegate.TARGET_WINDOW_NEW,
            isDirectNavigation = request.isDirectNavigation,
            hasUserGesture = request.hasUserGesture
          )
        ) {
          listener.onBlockedRedirect(uri)
          return GeckoResult.fromValue(AllowOrDeny.DENY)
        }
        return GeckoResult.fromValue(AllowOrDeny.ALLOW)
      }

      override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession> {
        if (adBlockEngine.shouldBlockPopupWindow(uri, currentTabUrl)) {
          listener.onBlockedRedirect(uri)
          return GeckoResult.fromValue(null)
        }
        listener.onOpenNewTab(uri, isPrivate)
        return GeckoResult.fromValue(null)
      }
    }

    session.progressDelegate = object : GeckoSession.ProgressDelegate {
      override fun onPageStart(session: GeckoSession, url: String) {
        currentTabUrl = url
        listener.onPageStart(tabId, url)
      }

      override fun onPageStop(session: GeckoSession, success: Boolean) {
        listener.onPageStop(tabId, success)
      }

      override fun onProgressChange(session: GeckoSession, progress: Int) {
        listener.onProgressChanged(tabId, progress)
      }

      override fun onSecurityChange(
        session: GeckoSession,
        securityInfo: GeckoSession.ProgressDelegate.SecurityInformation
      ) {
        listener.onSecurityChanged(tabId, securityInfo.isSecure)
      }
    }

    session.contentDelegate = object : GeckoSession.ContentDelegate {
      override fun onTitleChange(session: GeckoSession, title: String?) {
        if (!title.isNullOrBlank()) {
          listener.onTitleChanged(tabId, title)
        }
      }

      override fun onFullScreen(session: GeckoSession, fullScreen: Boolean) {
        listener.onFullScreenChanged(tabId, fullScreen)
      }

      override fun onExternalResponse(session: GeckoSession, response: WebResponse) {
        val uri = response.uri
        if (!uri.isNullOrBlank()) {
          val mime = response.headers["Content-Type"] ?: response.headers["content-type"]
          listener.onDownloadRequested(uri, mime)
        }
      }
    }

    session.contentBlockingDelegate = object : ContentBlocking.Delegate {
      override fun onContentBlocked(session: GeckoSession, event: ContentBlocking.BlockEvent) {
        adBlockEngine.recordBlocked(
          domain = UrlUtils.extractHost(event.uri).ifBlank { event.uri.take(40) },
          pageHost = UrlUtils.extractHost(currentTabUrl),
          resourceType = "gecko_etp"
        )
      }
    }

    session.permissionDelegate = object : GeckoSession.PermissionDelegate {
      override fun onContentPermissionRequest(
        session: GeckoSession,
        perm: GeckoSession.PermissionDelegate.ContentPermission
      ): GeckoResult<Int> {
        val result = GeckoResult<Int>()
        val host = UrlUtils.extractHost(perm.uri)
        listener.onPermissionRequested(tabId, host, perm.permission, result)
        return result
      }

      override fun onAndroidPermissionsRequest(
        session: GeckoSession,
        permissions: Array<String>?,
        callback: GeckoSession.PermissionDelegate.Callback
      ) {
        if (permissions != null && permissions.isNotEmpty()) {
          listener.onAndroidPermissionsRequested(permissions, callback)
        } else {
          callback.grant()
        }
      }
    }

    session.open(rt)
    sessions[tabId] = session
    return session
  }

  private val lastBack = ConcurrentHashMap<String, Boolean>()
  private val lastForward = ConcurrentHashMap<String, Boolean>()

  fun getExistingSession(tabId: String): GeckoSession? = sessions[tabId]

  fun setSessionDesktopMode(tabId: String, desktop: Boolean) {
    sessions[tabId]?.let { session ->
      session.settings.userAgentMode =
        if (desktop) GeckoSessionSettings.USER_AGENT_MODE_DESKTOP
        else GeckoSessionSettings.USER_AGENT_MODE_MOBILE
      session.reload()
    }
  }

  fun suspendInactiveSessions(activeTabId: String, maxActiveSessions: Int) {
    val inactive = sessions.keys.filter { it != activeTabId }
    for ((idx, id) in inactive.withIndex()) {
      sessions[id]?.setActive(false)
      if (idx >= maxActiveSessions) {
        sessions.remove(id)?.close()
      }
    }
  }

  fun closeSession(tabId: String) {
    sessions.remove(tabId)?.let { session ->
      runCatching {
        session.stop()
        session.setFocused(false)
        session.setActive(false)
        session.close()
      }
    }
    lastBack.remove(tabId)
    lastForward.remove(tabId)
  }

  fun deactivateSession(tabId: String) {
    sessions[tabId]?.let { session ->
      runCatching {
        session.setFocused(false)
        session.setActive(false)
      }
    }
  }

  fun reactivateSession(tabId: String) {
    sessions[tabId]?.let { session ->
      runCatching {
        if (!session.isOpen) {
          session.open(ensureRuntime())
        }
        session.setActive(true)
        session.setFocused(true)
      }
    }
  }

  fun updateDetectedStreamSize(url: String, sizeBytes: Long) {
    if (sizeBytes <= 0L) return
    _detectedStreams.update { list ->
      list.map { if (it.url == url && it.sizeBytes <= 0L) it.copy(sizeBytes = sizeBytes) else it }
    }
  }

  fun activeSessionCount(): Int = sessions.size

  fun sendEnhancementConfig(config: EnhancementConfig) {
    cachedEnhancementConfig = config
    val payload = JSONObject().apply {
      put("enabled", config.enabled)
      put("upscaleStrength", config.upscaleStrength.toDouble())
      put("sharpen", config.sharpen.toDouble())
      put("deband", config.deband.toDouble())
      put("denoise", config.denoise.toDouble())
      put("edgeEnhance", config.edgeEnhance.toDouble())
      put("vibrance", config.vibrance.toDouble())
      put("resolutionScale", config.resolutionScale.toDouble())
      put("frameGenEnabled", config.frameGenEnabled)
      put("targetFps", config.targetFps)
      put("splitCompare", config.splitCompare)
      put("splitPosition", config.splitPosition.toDouble())
    }
    enhancerPort?.postMessage(JSONObject().apply {
      put("type", "SET_ENHANCEMENT")
      put("payload", payload)
    })
  }

  fun sendScalingMode(mode: VideoScalingMode, customZoom: Float) {
    cachedScaling = mode to customZoom
    enhancerPort?.postMessage(JSONObject().apply {
      put("type", "SET_SCALING")
      put("payload", JSONObject().apply {
        put("mode", mode.name)
        put("customZoom", customZoom.toDouble())
      })
    })
  }

  fun sendForceFullscreen(enabled: Boolean, hideControls: Boolean) {
    enhancerPort?.postMessage(JSONObject().apply {
      put("type", "SET_FORCE_FULLSCREEN")
      put("payload", JSONObject().apply {
        put("enabled", enabled)
        put("hideControls", hideControls)
      })
    })
  }

  fun sendVideoControl(action: String, seconds: Double = 0.0, speed: Float = 1.0f) {
    enhancerPort?.postMessage(JSONObject().apply {
      put("type", "VIDEO_CONTROL")
      put("payload", JSONObject().apply {
        put("action", action)
        put("seconds", seconds)
        put("speed", speed.toDouble())
      })
    })
  }

  fun requestReaderMode() {
    enhancerPort?.postMessage(JSONObject().apply { put("type", "REQUEST_READER_MODE") })
  }

  fun clearReaderArticle() {
    _readerArticle.value = null
  }

  fun requestPageHtmlSave() {
    enhancerPort?.postMessage(JSONObject().apply { put("type", "REQUEST_PAGE_HTML") })
  }

  fun consumeSavedHtmlResult() {
    _savedHtmlResult.value = null
  }

  fun syncAdBlockRulesToExtension() {
    val customArr = JSONArray().apply {
      adBlockEngine.getAllCustomDomains().forEach { put(it) }
    }
    val whitelistArr = JSONArray().apply {
      adBlockEngine.getAllWhitelistedHosts().forEach { put(it) }
    }
    adBlockPort?.postMessage(JSONObject().apply {
      put("type", "SYNC_RULES")
      put("enabled", adBlockEngine.isEnabled)
      put("popupBlockEnabled", adBlockEngine.isPopupBlockingEnabled)
      put("customDomains", customArr)
      put("whitelist", whitelistArr)
    })
  }

  fun toggleExtension(extensionId: String, enable: Boolean) {
    val rt = runtime ?: return
    val ext = installedWebExtensions[extensionId]
    if (ext != null) {
      val result = if (enable) {
        rt.webExtensionController.enable(ext, WebExtensionController.EnableSource.USER)
      } else {
        rt.webExtensionController.disable(ext, WebExtensionController.EnableSource.USER)
      }
      result.accept(
        { updated ->
          if (updated != null) installedWebExtensions[extensionId] = updated
          markExtensionLoaded(extensionId, enable)
        },
        { Log.e(TAG, "Failed to toggle extension $extensionId", it) }
      )
    } else {
      markExtensionLoaded(extensionId, enable)
    }
  }

  fun updateRuntimeJavaScript(enabled: Boolean) {
    runtime?.settings?.javaScriptEnabled = enabled
  }

  fun updateRuntimeColorScheme(isDark: Boolean) {
    runtime?.settings?.preferredColorScheme =
      if (isDark) GeckoRuntimeSettings.COLOR_SCHEME_DARK
      else GeckoRuntimeSettings.COLOR_SCHEME_LIGHT
  }

  fun clearBrowserData(clearCookies: Boolean, clearCache: Boolean, clearSiteData: Boolean) {
    val rt = runtime ?: return
    var flags = 0L
    if (clearCookies) flags = flags or StorageController.ClearFlags.COOKIES
    if (clearCache) flags = flags or StorageController.ClearFlags.NETWORK_CACHE or StorageController.ClearFlags.IMAGE_CACHE
    if (clearSiteData) flags = flags or StorageController.ClearFlags.DOM_STORAGES
    if (flags != 0L) {
      rt.storageController.clearData(flags)
    }
  }

  fun resetVideoTelemetry() {
    _videoTelemetry.value = VideoTelemetry()
  }

  fun clearDetectedStreams() {
    _detectedStreams.value = emptyList()
    downloaderPort?.postMessage(JSONObject().apply { put("type", "CLEAR_STREAMS") })
  }

  private fun markExtensionLoaded(id: String, enabled: Boolean) {
    _extensions.update { list ->
      list.map { if (it.id == id) it.copy(isEnabled = enabled) else it }
    }
  }

  private fun defaultExtensionCatalog(): List<ExtensionInfo> = listOf(
    ExtensionInfo(
      id = EXT_ENHANCER_ID,
      name = "Anime4K WebGL2 Enhancer & Video Controller",
      version = "1.3.0",
      description = "Clean-room WebGL2 GLSL anime shader pipeline (line-art reconstruction, bilateral denoise, debanding, adaptive sharpen, Optical-Flow frame generation) + geometric video scaling & force fullscreen.",
      isBuiltIn = true,
      isEnabled = true,
      permissions = listOf("geckoViewAddons", "nativeMessaging", "webRequest", "webRequestBlocking", "tabs", "<all_urls>"),
      compatibilityStatus = "Native GeckoView Built-In (Verified)",
      technicalNotes = "Universal Android GPU pipeline (Adreno, Mali, Xclipse, PowerVR). Synced to true decoded frames via requestVideoFrameCallback with zero-flicker shaders and automatic WebRender GPU fallback."
    ),
    ExtensionInfo(
      id = EXT_DOWNLOADER_ID,
      name = "Anime Video Downloader (HLS & MP4 Sniffer)",
      version = "1.1.0",
      description = "Clean-room Android equivalent of MAX Video Downloader. Sniffs ONLY actual playing HLS (.m3u8) and MP4/WebM video streams with estimated file sizes & quality selector.",
      isBuiltIn = true,
      isEnabled = true,
      permissions = listOf("geckoViewAddons", "nativeMessaging", "webRequest", "<all_urls>"),
      compatibilityStatus = "Native GeckoView Built-In (Verified)",
      technicalNotes = "Replaces Chrome MV3 MAX Video Downloader + desktop CoApp with a pure-Kotlin parallel HLS segment downloader & Android DownloadManager engine. Excludes webpages, subtitles, YouTube, and DRM streams."
    ),
    ExtensionInfo(
      id = EXT_ADBLOCK_ID,
      name = "Anime Ad & Tracker Shield",
      version = "1.1.0",
      description = "Real webRequest network blocker + cross-domain redirect shield + invisible click-trap & pop-under filter synced with your custom blocklists and per-site whitelist.",
      isBuiltIn = true,
      isEnabled = true,
      permissions = listOf("geckoViewAddons", "nativeMessaging", "webRequest", "webRequestBlocking", "<all_urls>"),
      compatibilityStatus = "Native GeckoView Built-In (Verified)",
      technicalNotes = "Works alongside GeckoView Enhanced Tracking Protection (ETP) and NavigationDelegate popup/redirect guards."
    ),
    ExtensionInfo(
      id = "max-video-downloader@reference",
      name = "MAX Video Downloader (Chrome Web Store Reference)",
      version = "External Reference",
      description = "Investigated Chrome extension (kjinbaahkmjgkkedfdgpkkelehofieke). Cannot be installed directly on Android GeckoView because it is closed-source Chrome MV3 and relies on a desktop Windows/macOS/Linux CoApp binary.",
      isBuiltIn = false,
      isEnabled = false,
      permissions = listOf("Chrome MV3 webRequest", "Desktop CoApp nativeMessaging"),
      compatibilityStatus = "Incompatible Directly — Replaced by Built-In Anime Video Downloader",
      technicalNotes = "Equivalent HLS (.m3u8) multi-quality detection, MP4/WebM downloading, and subtitle extraction are built natively into the bundled Anime Video Downloader extension above."
    ),
    ExtensionInfo(
      id = "anilens-investigation@reference",
      name = "AniLens (Chrome Web Store Reference)",
      version = "External Reference",
      description = "Investigated upstream Chrome extension (awdas221/AniLens). Cannot be bundled directly due to WebGPU dependency, Chrome MV3 declarativeNetRequest APIs, and missing open-source license.",
      isBuiltIn = false,
      isEnabled = false,
      permissions = listOf("declarativeNetRequest (Chrome-only)", "WebGPU (Unavailable in Android GeckoView)"),
      compatibilityStatus = "Incompatible Directly — Replaced by Built-In WebGL2 Enhancer",
      technicalNotes = "Equivalent real-time Anime4K-style WebGL2 enhancement is delivered natively by the bundled Anime4K WebGL2 Enhancer extension."
    )
  )
}

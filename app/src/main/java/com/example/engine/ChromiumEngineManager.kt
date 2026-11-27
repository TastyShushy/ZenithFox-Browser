package com.example.engine

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import android.view.View
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
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
import java.io.ByteArrayInputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Chromium Blink + V8 + ANGLE Browser Engine Manager for ZenithFox Browser v2.0.0.
 *
 * Uses Android System WebView (Chromium) + androidx.webkit Document-Start JavaScript &
 * Multi-Frame WebMessageListener so 4K WebGL2 upscaling, GPU Optical-Flow Frame Generation,
 * video scaling, player controls, ad/redirect shielding, and HLS/MP4 stream sniffing work
 * cleanly across BOTH main frames AND cross-origin embedded video iframes with ZERO
 * GeckoView Mali pink/black screen bugs.
 */
class ChromiumEngineManager(
  private val context: Context,
  private val adBlockEngine: AdBlockEngine
) {
  companion object {
    private const val TAG = "ChromiumEngineManager"
    const val EXT_ENHANCER_ID = "anime-enhancer@zenithfox.app"
    const val EXT_ADBLOCK_ID = "anime-adblock@zenithfox.app"
    const val EXT_DOWNLOADER_ID = "anime-downloader@zenithfox.app"

    private const val MOBILE_CHROME_UA =
      "Mozilla/5.0 (Linux; Android 15; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Mobile Safari/537.36"
    private const val DESKTOP_CHROME_UA =
      "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Safari/537.36"

    private val EXCLUDED_DOWNLOAD_HOSTS = listOf(
      "youtube.com", "youtu.be", "googlevideo.com", "ytimg.com",
      "netflix.com", "nflxvideo.net", "disneyplus.com", "primevideo.com",
      "doubleclick.net", "googlesyndication.com", "google-analytics.com",
      "popads.net", "exoclick.com", "adsterra.com"
    )
    private val HLS_REGEX = Regex("""(\.m3u8|%2em3u8|/m3u8(\?|/|$))""", RegexOption.IGNORE_CASE)
    private val DIRECT_VIDEO_REGEX = Regex("""\.(mp4|m4v|webm)(\?|$|#|\&)""", RegexOption.IGNORE_CASE)
    private val IGNORED_SEGMENT_REGEX = Regex(
      """\.(ts|m4s|m4a|vtt|srt|ass|ssa|jpg|jpeg|png|gif|webp|css|js|woff|woff2|svg|ico)(\?|$)""",
      RegexOption.IGNORE_CASE
    )
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
    fun onGeolocationPermissionRequested(
      tabId: String,
      host: String,
      origin: String,
      callback: GeolocationPermissions.Callback
    )
    fun onWebPermissionRequested(
      tabId: String,
      host: String,
      request: PermissionRequest
    )
  }

  private val mainHandler = Handler(Looper.getMainLooper())
  private val webViews = ConcurrentHashMap<String, WebView>()
  private val tabUrls = ConcurrentHashMap<String, String>()
  private val tabReplyProxies = ConcurrentHashMap<String, CopyOnWriteArraySet<JavaScriptReplyProxy>>()
  private val tabPrivateFlags = ConcurrentHashMap<String, Boolean>()

  private var activeTabId: String = ""
  private var jsEnabledGlobally: Boolean = true
  private var darkThemeGlobally: Boolean = true
  private var enhancerEnabled: Boolean = true
  private var downloaderEnabled: Boolean = true
  private var cachedEnhancementConfig: EnhancementConfig? = null
  private var cachedScaling: Pair<VideoScalingMode, Float>? = null

  private var customViewCallback: WebChromeClient.CustomViewCallback? = null
  private val _fullscreenCustomView = MutableStateFlow<View?>(null)
  val fullscreenCustomView: StateFlow<View?> = _fullscreenCustomView.asStateFlow()

  private val _webViewGeneration = MutableStateFlow(0L)
  val webViewGeneration: StateFlow<Long> = _webViewGeneration.asStateFlow()

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

  private val engineScript: String by lazy {
    runCatching {
      context.assets.open("chromium/zenithfox_engine.js").bufferedReader().use { it.readText() }
    }.getOrElse { err ->
      Log.e(TAG, "Failed to read chromium/zenithfox_engine.js", err)
      ""
    }
  }

  @SuppressLint("SetJavaScriptEnabled")
  fun getOrCreateWebView(
    tabId: String,
    isPrivate: Boolean,
    isDesktopMode: Boolean,
    listener: SessionEventListener
  ): WebView {
    activeTabId = tabId
    webViews[tabId]?.let { existing ->
      existing.onResume()
      existing.resumeTimers()
      return existing
    }

    val webView = WebView(context)
    tabPrivateFlags[tabId] = isPrivate
    tabReplyProxies[tabId] = CopyOnWriteArraySet()

    configureWebViewSettings(webView, isPrivate = isPrivate, isDesktopMode = isDesktopMode)
    attachChromiumBridge(webView, tabId)

    webView.webViewClient = object : WebViewClient() {
      override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?
      ): WebResourceResponse? {
        val url = request?.url?.toString() ?: return null
        if (!url.startsWith("http://") && !url.startsWith("https://")) return null

        val currentPageUrl = tabUrls[tabId].orEmpty()

        // 1. Network-level Ad, Tracker & Malware Shield
        if (!request.isForMainFrame && adBlockEngine.shouldBlockSubresource(url, currentPageUrl)) {
          return WebResourceResponse(
            "text/plain",
            "utf-8",
            ByteArrayInputStream(ByteArray(0))
          )
        }

        // 2. Live Video-Only Stream Sniffer (HLS .m3u8 & Direct MP4/WebM)
        if (downloaderEnabled && tabId == activeTabId) {
          inspectRequestForVideoStream(url, request.requestHeaders, currentPageUrl)
        }

        return super.shouldInterceptRequest(view, request)
      }

      override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val uri = request?.url ?: return false
        val urlStr = uri.toString()
        val scheme = uri.scheme?.lowercase().orEmpty()

        // Block unsafe non-web schemes (intent://, file://, javascript:// external hijacks)
        if (scheme != "http" && scheme != "https" && scheme != "about") {
          return true
        }

        val sourcePage = tabUrls[tabId].orEmpty()
        val isRedirect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) request.isRedirect else false
        if (adBlockEngine.shouldBlockNavigation(
            targetUrl = urlStr,
            currentPageUrl = sourcePage,
            isRedirectOrPopup = isRedirect,
            isDirectNavigation = false,
            hasUserGesture = request.hasGesture()
          )
        ) {
          mainHandler.post { listener.onBlockedRedirect(urlStr) }
          return true
        }
        return false
      }

      override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        val safeUrl = url.orEmpty()
        tabUrls[tabId] = safeUrl
        tabReplyProxies[tabId]?.clear()
        listener.onPageStart(tabId, safeUrl)
        listener.onLocationChanged(tabId, safeUrl)
        listener.onSecurityChanged(tabId, safeUrl.startsWith("https://"))
        if (view != null) {
          listener.onNavigationStateChanged(tabId, view.canGoBack(), view.canGoForward())
        }
      }

      override fun onPageFinished(view: WebView?, url: String?) {
        val safeUrl = url.orEmpty()
        if (safeUrl.isNotBlank()) tabUrls[tabId] = safeUrl
        listener.onPageStop(tabId, true)
        if (view != null) {
          listener.onNavigationStateChanged(tabId, view.canGoBack(), view.canGoForward())
          // Fallback injection for older WebView runtimes without DOCUMENT_START_SCRIPT
          if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT) && engineScript.isNotBlank()) {
            view.evaluateJavascript(engineScript, null)
          }
        }
        syncStateToTab(tabId)
      }

      override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
        val safeUrl = url.orEmpty()
        if (safeUrl.isNotBlank()) {
          tabUrls[tabId] = safeUrl
          listener.onLocationChanged(tabId, safeUrl)
          listener.onSecurityChanged(tabId, safeUrl.startsWith("https://"))
        }
        if (view != null) {
          listener.onNavigationStateChanged(tabId, view.canGoBack(), view.canGoForward())
        }
      }

      override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
        // Android OS reclaimed the background Chromium renderer process — transparently recover
        // instead of crashing or leaving the user with a permanently blank/black screen!
        val lastUrl = tabUrls[tabId].orEmpty()
        tabReplyProxies.remove(tabId)
        webViews.remove(tabId)
        mainHandler.post {
          runCatching {
            (view?.parent as? android.view.ViewGroup)?.removeView(view)
            view?.destroy()
          }
          if (lastUrl.startsWith("http://") || lastUrl.startsWith("https://")) {
            val fresh = getOrCreateWebView(tabId, isPrivate, isDesktopMode, listener)
            fresh.loadUrl(lastUrl)
          }
          _webViewGeneration.update { it + 1L }
        }
        return true
      }
    }

    webView.webChromeClient = object : WebChromeClient() {
      override fun onProgressChanged(view: WebView?, newProgress: Int) {
        listener.onProgressChanged(tabId, newProgress)
      }

      override fun onReceivedTitle(view: WebView?, title: String?) {
        if (!title.isNullOrBlank()) {
          listener.onTitleChanged(tabId, title)
        }
      }

      override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
        if (view == null) {
          callback?.onCustomViewHidden()
          return
        }
        customViewCallback?.onCustomViewHidden()
        customViewCallback = callback
        _fullscreenCustomView.value = view
        listener.onFullScreenChanged(tabId, true)
      }

      override fun onHideCustomView() {
        customViewCallback?.onCustomViewHidden()
        customViewCallback = null
        _fullscreenCustomView.value = null
        listener.onFullScreenChanged(tabId, false)
      }

      override fun onCreateWindow(
        view: WebView?,
        isDialog: Boolean,
        isUserGesture: Boolean,
        resultMsg: Message?
      ): Boolean {
        val sourcePage = tabUrls[tabId].orEmpty()
        if (!isUserGesture && adBlockEngine.isEnabled && adBlockEngine.isPopupBlockingEnabled) {
          return false
        }
        val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
        // Inspect the popup target URL in a lightweight temporary WebView so ad popups are blocked cleanly
        val probe = WebView(context)
        probe.webViewClient = object : WebViewClient() {
          override fun shouldOverrideUrlLoading(v: WebView?, req: WebResourceRequest?): Boolean {
            val targetUrl = req?.url?.toString().orEmpty()
            v?.destroy()
            if (targetUrl.startsWith("http://") || targetUrl.startsWith("https://")) {
              if (adBlockEngine.shouldBlockPopupWindow(targetUrl, sourcePage)) {
                listener.onBlockedRedirect(targetUrl)
              } else {
                listener.onOpenNewTab(targetUrl, isPrivate)
              }
            }
            return true
          }
        }
        transport.webView = probe
        resultMsg.sendToTarget()
        return true
      }

      override fun onGeolocationPermissionsShowPrompt(
        origin: String?,
        callback: GeolocationPermissions.Callback?
      ) {
        if (origin == null || callback == null) return
        val host = UrlUtils.extractHost(origin)
        listener.onGeolocationPermissionRequested(tabId, host, origin, callback)
      }

      override fun onPermissionRequest(request: PermissionRequest?) {
        if (request == null) return
        val resources = request.resources ?: emptyArray()
        // Auto-grant protected media playback IDs so web streaming players initialize cleanly
        if (resources.all { it == PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID }) {
          request.grant(resources)
          return
        }
        val host = UrlUtils.extractHost(request.origin?.toString().orEmpty())
        listener.onWebPermissionRequested(tabId, host, request)
      }
    }

    webView.setDownloadListener { url, _, _, mimetype, _ ->
      if (!url.isNullOrBlank() && (url.startsWith("http://") || url.startsWith("https://"))) {
        listener.onDownloadRequested(url, mimetype)
      }
    }

    webView.setBackgroundColor(Color.TRANSPARENT)
    webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)

    webViews[tabId] = webView
    return webView
  }

  @SuppressLint("SetJavaScriptEnabled")
  private fun configureWebViewSettings(webView: WebView, isPrivate: Boolean, isDesktopMode: Boolean) {
    val s = webView.settings
    s.javaScriptEnabled = jsEnabledGlobally
    s.domStorageEnabled = true
    s.databaseEnabled = !isPrivate
    s.mediaPlaybackRequiresUserGesture = false
    s.useWideViewPort = true
    s.loadWithOverviewMode = true
    s.builtInZoomControls = true
    s.displayZoomControls = false
    s.setSupportZoom(true)
    s.setSupportMultipleWindows(true)
    s.javaScriptCanOpenWindowsAutomatically = false
    s.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
    s.cacheMode = if (isPrivate) WebSettings.LOAD_NO_CACHE else WebSettings.LOAD_DEFAULT
    s.userAgentString = if (isDesktopMode) DESKTOP_CHROME_UA else MOBILE_CHROME_UA

    // Strict WebView security hardening
    s.allowFileAccess = false
    s.allowContentAccess = false
    @Suppress("DEPRECATION")
    s.allowFileAccessFromFileURLs = false
    @Suppress("DEPRECATION")
    s.allowUniversalAccessFromFileURLs = false
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      s.safeBrowsingEnabled = true
    }

    CookieManager.getInstance().setAcceptCookie(true)
    CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

    applyDarkModeToWebView(webView, darkThemeGlobally)
  }

  private fun applyDarkModeToWebView(webView: WebView, isDark: Boolean) {
    if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
      runCatching { WebSettingsCompat.setAlgorithmicDarkeningAllowed(webView.settings, isDark) }
    }
  }

  private fun attachChromiumBridge(webView: WebView, tabId: String) {
    // 1. Inject ZenithFox 4K + Frame Gen + Scaling + AdShield script at document_start into ALL frames (including cross-origin anime iframes!)
    if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT) && engineScript.isNotBlank()) {
      runCatching {
        WebViewCompat.addDocumentStartJavaScript(webView, engineScript, setOf("*"))
      }.onFailure { err ->
        Log.e(TAG, "addDocumentStartJavaScript failed", err)
      }
    }

    // 2. Attach secure multi-frame WebMessageListener so every iframe can send telemetry & receive live commands
    if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
      runCatching {
        WebViewCompat.addWebMessageListener(
          webView,
          "ZenithFoxBridge",
          setOf("*")
        ) { _, message, _, _, replyProxy ->
          tabReplyProxies.getOrPut(tabId) { CopyOnWriteArraySet() }.add(replyProxy)
          val raw = message.data ?: return@addWebMessageListener
          handleBridgeMessage(tabId, raw, replyProxy)
        }
      }.onFailure { err ->
        Log.e(TAG, "addWebMessageListener failed", err)
      }
    }

    // 3. Minimal fallback bridge for legacy Android WebViews without WEB_MESSAGE_LISTENER
    webView.addJavascriptInterface(
      object {
        @JavascriptInterface
        fun postMessage(rawJson: String) {
          handleBridgeMessage(tabId, rawJson, null)
        }
      },
      "_ZenithFoxLegacy"
    )
  }

  private fun handleBridgeMessage(tabId: String, rawJson: String, replyProxy: JavaScriptReplyProxy?) {
    val json = runCatching { JSONObject(rawJson) }.getOrNull() ?: return
    when (json.optString("type")) {
      "EXTENSION_READY" -> {
        if (replyProxy != null) {
          pushInitialConfigToProxy(replyProxy)
        } else {
          syncStateToTab(tabId)
        }
      }
      "VIDEO_TELEMETRY" -> {
        if (tabId != activeTabId) return
        val d = json.optJSONObject("data") ?: return
        val hasVideo = d.optBoolean("hasVideo", false)
        if (!hasVideo) {
          // Only clear if top-level frame reports no video and we aren't receiving active iframe video telemetry
          if (!_videoTelemetry.value.isPlaying) {
            _videoTelemetry.update { prev -> prev.copy(hasVideo = false, isPlaying = false) }
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
          gpuRenderer = d.optString("gpuRenderer", "Chromium ANGLE GPU"),
          webGpuSupported = d.optBoolean("webGpuSupported", false),
          isDrmProtected = false,
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
      "STREAM_DETECTED" -> {
        if (!downloaderEnabled || tabId != activeTabId) return
        val d = json.optJSONObject("data") ?: return
        val url = d.optString("url", "")
        if (!url.startsWith("http://") && !url.startsWith("https://")) return
        val type = when (d.optString("streamType")) {
          "HLS" -> StreamType.HLS
          "DIRECT" -> StreamType.DIRECT
          else -> return
        }
        publishDetectedStream(
          DetectedVideoStream(
            url = url,
            title = d.optString("title", "Anime Video"),
            streamType = type,
            mimeType = d.optString("mimeType", "video/mp4"),
            sizeBytes = d.optLong("sizeBytes", 0L),
            qualityLabel = d.optString("qualityLabel", ""),
            referer = d.optString("referer", tabUrls[tabId].orEmpty()),
            origin = d.optString("origin", ""),
            userAgent = d.optString("userAgent", MOBILE_CHROME_UA),
            cookie = CookieManager.getInstance().getCookie(url).orEmpty(),
            isDrmProtected = false
          )
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

  private fun inspectRequestForVideoStream(
    url: String,
    reqHeaders: Map<String, String>?,
    pageUrl: String
  ) {
    val host = UrlUtils.extractHost(url).lowercase()
    val pageHost = UrlUtils.extractHost(pageUrl).lowercase()
    if (EXCLUDED_DOWNLOAD_HOSTS.any { host.endsWith(it) || pageHost.endsWith(it) }) return
    if (IGNORED_SEGMENT_REGEX.containsMatchIn(url)) return

    val isHls = HLS_REGEX.containsMatchIn(url)
    val isDirect = !isHls && DIRECT_VIDEO_REGEX.containsMatchIn(url)
    if (!isHls && !isDirect) return

    val referer = reqHeaders?.entries?.firstOrNull { it.key.equals("Referer", true) }?.value
      ?: pageUrl
    val origin = reqHeaders?.entries?.firstOrNull { it.key.equals("Origin", true) }?.value
      ?: runCatching {
        val u = Uri.parse(pageUrl)
        if (!u.scheme.isNullOrBlank() && !u.host.isNullOrBlank()) "${u.scheme}://${u.host}" else ""
      }.getOrDefault("")
    val ua = reqHeaders?.entries?.firstOrNull { it.key.equals("User-Agent", true) }?.value
      ?: MOBILE_CHROME_UA
    val cookie = runCatching { CookieManager.getInstance().getCookie(url).orEmpty() }.getOrDefault("")

    val qualityHint = when {
      url.contains("2160", true) || url.contains("4k", true) -> "4K"
      url.contains("1080", true) -> "1080p"
      url.contains("720", true) -> "720p"
      url.contains("480", true) -> "480p"
      isHls -> "HLS Stream"
      else -> "MP4 Video"
    }

    publishDetectedStream(
      DetectedVideoStream(
        url = url,
        title = UrlUtils.displayHostOrUrl(pageUrl).ifBlank { "Anime Video" },
        streamType = if (isHls) StreamType.HLS else StreamType.DIRECT,
        mimeType = if (isHls) "application/vnd.apple.mpegurl" else (if (url.contains(".webm", true)) "video/webm" else "video/mp4"),
        sizeBytes = 0L,
        qualityLabel = qualityHint,
        referer = referer,
        origin = origin,
        userAgent = ua,
        cookie = cookie,
        isDrmProtected = false
      )
    )
  }

  private fun publishDetectedStream(stream: DetectedVideoStream) {
    _detectedStreams.update { list ->
      if (list.any { it.url == stream.url }) list
      else {
        val isMaster = stream.streamType == StreamType.HLS &&
          (stream.url.contains("master", true) || stream.url.contains("manifest", true) || stream.url.contains("index", true))
        val merged = if (isMaster) listOf(stream) + list else list + stream
        merged.distinctBy { it.url }.take(6)
      }
    }
  }

  private fun pushInitialConfigToProxy(proxy: JavaScriptReplyProxy) {
    runCatching {
      cachedEnhancementConfig?.let { proxy.postMessage(buildEnhancementMessage(it).toString()) }
      cachedScaling?.let { (m, z) -> proxy.postMessage(buildScalingMessage(m, z).toString()) }
      proxy.postMessage(buildAdBlockSyncMessage().toString())
    }
  }

  private fun syncStateToTab(tabId: String) {
    cachedEnhancementConfig?.let { sendJsonToTab(tabId, buildEnhancementMessage(it)) }
    cachedScaling?.let { (m, z) -> sendJsonToTab(tabId, buildScalingMessage(m, z)) }
    sendJsonToTab(tabId, buildAdBlockSyncMessage())
  }

  private fun sendJsonToActiveTab(message: JSONObject) {
    if (activeTabId.isNotBlank()) {
      sendJsonToTab(activeTabId, message)
    }
  }

  private fun sendJsonToTab(tabId: String, message: JSONObject) {
    val serialized = message.toString()
    val proxies = tabReplyProxies[tabId]
    var deliveredViaProxy = false
    if (proxies != null && proxies.isNotEmpty()) {
      val dead = mutableListOf<JavaScriptReplyProxy>()
      for (proxy in proxies) {
        runCatching {
          proxy.postMessage(serialized)
          deliveredViaProxy = true
        }.onFailure {
          dead.add(proxy)
        }
      }
      if (dead.isNotEmpty()) proxies.removeAll(dead.toSet())
    }

    if (!deliveredViaProxy) {
      val webView = webViews[tabId] ?: return
      val quoted = JSONObject.quote(serialized)
      mainHandler.post {
        runCatching {
          webView.evaluateJavascript(
            "if(window.__zenithFoxReceiveCommand){window.__zenithFoxReceiveCommand($quoted);}",
            null
          )
        }
      }
    }
  }

  fun getExistingWebView(tabId: String): WebView? = webViews[tabId]

  fun loadUrl(tabId: String, url: String) {
    val wv = webViews[tabId] ?: return
    mainHandler.post { wv.loadUrl(url) }
  }

  fun setSessionDesktopMode(tabId: String, desktop: Boolean) {
    val wv = webViews[tabId] ?: return
    mainHandler.post {
      wv.settings.userAgentString = if (desktop) DESKTOP_CHROME_UA else MOBILE_CHROME_UA
      wv.reload()
    }
  }

  fun findInPage(tabId: String, query: String, backward: Boolean) {
    val wv = webViews[tabId] ?: return
    mainHandler.post {
      if (query.isBlank()) {
        wv.clearMatches()
      } else {
        wv.findAllAsync(query)
        wv.findNext(!backward)
      }
    }
  }

  fun clearFindInPage(tabId: String) {
    val wv = webViews[tabId] ?: return
    mainHandler.post { wv.clearMatches() }
  }

  fun exitFullscreen() {
    mainHandler.post {
      customViewCallback?.onCustomViewHidden()
      customViewCallback = null
      _fullscreenCustomView.value = null
      webViews[activeTabId]?.evaluateJavascript(
        "if(document.fullscreenElement){document.exitFullscreen().catch(function(){});}",
        null
      )
    }
  }

  fun suspendInactiveSessions(activeId: String, maxActiveSessions: Int) {
    activeTabId = activeId
    val inactive = webViews.keys.filter { it != activeId }
    for ((idx, id) in inactive.withIndex()) {
      webViews[id]?.let { wv ->
        mainHandler.post { wv.onPause() }
      }
      if (idx >= maxActiveSessions) {
        closeSession(id)
      }
    }
  }

  fun closeSession(tabId: String) {
    val wasPrivate = tabPrivateFlags.remove(tabId) == true
    tabUrls.remove(tabId)
    tabReplyProxies.remove(tabId)
    webViews.remove(tabId)?.let { wv ->
      mainHandler.post {
        runCatching {
          wv.stopLoading()
          wv.onPause()
          wv.loadUrl("about:blank")
          if (wasPrivate) {
            wv.clearCache(true)
            wv.clearHistory()
          }
          wv.destroy()
        }
      }
    }
  }

  fun deactivateSession(tabId: String) {
    webViews[tabId]?.let { wv ->
      mainHandler.post { wv.onPause() }
    }
  }

  /**
   * Freezes ALL open Chromium WebViews and V8 JS timers when the app is minimized
   * (outside Picture-in-Picture) so background CPU & battery usage drops to ~0%.
   */
  fun pauseAllWebViews() {
    mainHandler.post {
      var pausedTimers = false
      webViews.values.forEach { wv ->
        runCatching {
          wv.onPause()
          if (!pausedTimers) {
            wv.pauseTimers()
            pausedTimers = true
          }
        }
      }
    }
  }

  /**
   * Immediately terminates every Chromium WebView & renderer when the user swipes
   * ZenithFox Browser away from Recents — zero lingering background processes!
   */
  fun shutdownAllSessions() {
    mainHandler.post {
      runCatching { customViewCallback?.onCustomViewHidden() }
      customViewCallback = null
      _fullscreenCustomView.value = null
      val all = webViews.values.toList()
      webViews.clear()
      tabReplyProxies.clear()
      all.forEach { wv ->
        runCatching {
          wv.stopLoading()
          wv.onPause()
          wv.pauseTimers()
          (wv.parent as? android.view.ViewGroup)?.removeView(wv)
          wv.loadUrl("about:blank")
          wv.destroy()
        }
      }
    }
  }

  fun reactivateSession(tabId: String) {
    activeTabId = tabId
    webViews[tabId]?.let { wv ->
      mainHandler.post {
        wv.resumeTimers()
        wv.onResume()
        wv.requestLayout()
        wv.invalidate()
      }
      sendJsonToTab(tabId, JSONObject().apply { put("type", "APP_RESUMED") })
    }
  }

  fun updateDetectedStreamSize(url: String, sizeBytes: Long) {
    if (sizeBytes <= 0L) return
    _detectedStreams.update { list ->
      list.map { if (it.url == url && it.sizeBytes <= 0L) it.copy(sizeBytes = sizeBytes) else it }
    }
  }

  fun activeSessionCount(): Int = webViews.size

  private fun buildEnhancementMessage(config: EnhancementConfig): JSONObject {
    val effectiveEnabled = config.enabled && enhancerEnabled
    val effectiveFrameGen = config.frameGenEnabled && enhancerEnabled
    return JSONObject().apply {
      put("type", "SET_ENHANCEMENT")
      put("payload", JSONObject().apply {
        put("enabled", effectiveEnabled)
        put("upscaleStrength", config.upscaleStrength.toDouble())
        put("sharpen", config.sharpen.toDouble())
        put("deband", config.deband.toDouble())
        put("denoise", config.denoise.toDouble())
        put("edgeEnhance", config.edgeEnhance.toDouble())
        put("resolutionScale", config.resolutionScale.toDouble())
        put("frameGenEnabled", effectiveFrameGen)
        put("targetFps", config.targetFps)
        put("splitCompare", config.splitCompare)
        put("splitPosition", config.splitPosition.toDouble())
      })
    }
  }

  private fun buildScalingMessage(mode: VideoScalingMode, customZoom: Float): JSONObject =
    JSONObject().apply {
      put("type", "SET_SCALING")
      put("payload", JSONObject().apply {
        put("mode", mode.name)
        put("customZoom", customZoom.toDouble())
      })
    }

  private fun buildAdBlockSyncMessage(): JSONObject =
    JSONObject().apply {
      put("type", "SYNC_ADBLOCK")
      put("payload", JSONObject().apply {
        put("enabled", adBlockEngine.isEnabled)
        put("popupBlockEnabled", adBlockEngine.isPopupBlockingEnabled)
      })
    }

  fun sendEnhancementConfig(config: EnhancementConfig) {
    cachedEnhancementConfig = config
    sendJsonToActiveTab(buildEnhancementMessage(config))
  }

  fun sendScalingMode(mode: VideoScalingMode, customZoom: Float) {
    cachedScaling = mode to customZoom
    sendJsonToActiveTab(buildScalingMessage(mode, customZoom))
  }

  fun sendForceFullscreen(enabled: Boolean, hideControls: Boolean) {
    sendJsonToActiveTab(JSONObject().apply {
      put("type", "SET_FORCE_FULLSCREEN")
      put("payload", JSONObject().apply {
        put("enabled", enabled)
        put("hideControls", hideControls)
      })
    })
  }

  fun sendVideoControl(action: String, seconds: Double = 0.0, speed: Float = 1.0f) {
    sendJsonToActiveTab(JSONObject().apply {
      put("type", "VIDEO_CONTROL")
      put("payload", JSONObject().apply {
        put("action", action)
        put("seconds", seconds)
        put("speed", speed.toDouble())
      })
    })
  }

  fun requestReaderMode() {
    sendJsonToActiveTab(JSONObject().apply { put("type", "REQUEST_READER_MODE") })
  }

  fun clearReaderArticle() {
    _readerArticle.value = null
  }

  fun requestPageHtmlSave() {
    sendJsonToActiveTab(JSONObject().apply { put("type", "REQUEST_PAGE_HTML") })
  }

  fun consumeSavedHtmlResult() {
    _savedHtmlResult.value = null
  }

  fun syncAdBlockRulesToExtension() {
    val msg = buildAdBlockSyncMessage()
    webViews.keys.forEach { tabId -> sendJsonToTab(tabId, msg) }
  }

  fun toggleExtension(extensionId: String, enable: Boolean) {
    when (extensionId) {
      EXT_ENHANCER_ID -> {
        enhancerEnabled = enable
        cachedEnhancementConfig?.let { sendEnhancementConfig(it) }
      }
      EXT_DOWNLOADER_ID -> {
        downloaderEnabled = enable
        if (!enable) _detectedStreams.value = emptyList()
      }
      EXT_ADBLOCK_ID -> {
        adBlockEngine.isEnabled = enable
        syncAdBlockRulesToExtension()
      }
    }
    _extensions.update { list ->
      list.map { if (it.id == extensionId) it.copy(isEnabled = enable) else it }
    }
  }

  @SuppressLint("SetJavaScriptEnabled")
  fun updateRuntimeJavaScript(enabled: Boolean) {
    jsEnabledGlobally = enabled
    mainHandler.post {
      webViews.values.forEach { it.settings.javaScriptEnabled = enabled }
    }
  }

  fun updateRuntimeColorScheme(isDark: Boolean) {
    darkThemeGlobally = isDark
    mainHandler.post {
      webViews.values.forEach { applyDarkModeToWebView(it, isDark) }
    }
  }

  fun clearBrowserData(clearCookies: Boolean, clearCache: Boolean, clearSiteData: Boolean) {
    mainHandler.post {
      if (clearCookies) {
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
      }
      if (clearCache) {
        webViews.values.forEach { it.clearCache(true) }
      }
      if (clearSiteData) {
        WebStorage.getInstance().deleteAllData()
      }
    }
  }

  fun resetVideoTelemetry() {
    _videoTelemetry.value = VideoTelemetry()
  }

  fun clearDetectedStreams() {
    _detectedStreams.value = emptyList()
    sendJsonToActiveTab(JSONObject().apply { put("type", "CLEAR_STREAMS") })
  }

  private fun defaultExtensionCatalog(): List<ExtensionInfo> = listOf(
    ExtensionInfo(
      id = EXT_ENHANCER_ID,
      name = "4K WebGL2 Enhancer & Frame Generation",
      version = "2.0.0",
      description = "Zero-copy Chromium ANGLE WebGL2 4K shader pipeline (Catmull-Rom super-resolution, Anime4K line thinning, AMD FidelityFX CAS, optical-flow frame generation) with exact original colors + blur-free scaling.",
      isBuiltIn = true,
      isEnabled = true,
      permissions = listOf("Chromium Document-Start Injection", "Multi-Frame WebMessage Bridge", "WebGL2 ANGLE GPU"),
      compatibilityStatus = "Native Chromium Built-In (Verified)",
      technicalNotes = "Runs across the main page and all cross-origin video iframes on Chromium Blink/ANGLE with zero Mali pink-screen issues."
    ),
    ExtensionInfo(
      id = EXT_DOWNLOADER_ID,
      name = "Anime Video Downloader (HLS & MP4 Sniffer)",
      version = "2.0.0",
      description = "Real-time Chromium network & DOM sniffer capturing playing HLS (.m3u8) and direct MP4/WebM streams with quality & file-size estimation.",
      isBuiltIn = true,
      isEnabled = true,
      permissions = listOf("Chromium Network Interceptor", "Parallel HLS Downloader"),
      compatibilityStatus = "Native Chromium Built-In (Verified)",
      technicalNotes = "Intercepts live streaming playlists and direct video requests directly in Chromium's network layer and downloads via pure-Kotlin HLS/Android DownloadManager."
    ),
    ExtensionInfo(
      id = EXT_ADBLOCK_ID,
      name = "ZenithFox Ad, Tracker & Popup Shield",
      version = "2.0.0",
      description = "Network-level ad & tracker blocker + cross-domain redirect/pop-under shield + invisible click-hijack overlay killer.",
      isBuiltIn = true,
      isEnabled = true,
      permissions = listOf("Chromium Network Filter", "Cosmetic DOM Filter", "Popup Window Guard"),
      compatibilityStatus = "Native Chromium Built-In (Verified)",
      technicalNotes = "Blocks ad domains before bytes download in shouldInterceptRequest while neutralizing click-trap overlays and pop-unders."
    )
  )
}

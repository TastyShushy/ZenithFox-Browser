package com.example.data.model

enum class ThemeMode(val label: String) {
  AMOLED("AMOLED Black"),
  DARK("Dark Obsidian"),
  LIGHT("Light")
}

enum class AccentPalette(val label: String, val primaryHex: Long, val secondaryHex: Long) {
  ZENITH_VIOLET("ZenithFox Violet", 0xFFC084FC, 0xFFFF6BD6),
  SAKURA_CRIMSON("Sakura Pink", 0xFFFF6BD6, 0xFF60A5FA),
  ELECTRIC_CYAN("Crescent Cyan", 0xFF38BDF8, 0xFFC084FC),
  NEON_VIOLET("Amethyst Pulse", 0xFFA855F7, 0xFF38BDF8),
  SHONEN_AMBER("Shonen Amber", 0xFFFFAB00, 0xFFFF4D6D),
  EMERALD_PULSE("Emerald Pulse", 0xFF34D399, 0xFF38BDF8)
}

enum class ToolbarPosition(val label: String) {
  TOP("Top"),
  BOTTOM("Bottom")
}

enum class SearchEngine(
  val label: String,
  val searchUrlTemplate: String,
  val homeUrl: String
) {
  GOOGLE("Google", "https://www.google.com/search?q=%s", "https://www.google.com"),
  DUCKDUCKGO("DuckDuckGo", "https://duckduckgo.com/?q=%s", "https://duckduckgo.com"),
  BRAVE("Brave Search", "https://search.brave.com/search?q=%s", "https://search.brave.com"),
  BING("Bing", "https://www.bing.com/search?q=%s", "https://www.bing.com")
}

enum class PerformanceProfile(val label: String, val subtitle: String) {
  BATTERY_SAVER("Battery Saver", "Minimal animations, aggressive tab suspension, GPU shader off"),
  BALANCED("Balanced", "Smooth high-refresh UI, moderate Anime4K WebGL2, smart tab suspension"),
  PERFORMANCE("Performance", "Full WebGL2 pipeline, hardware acceleration, high-res shaders")
}

enum class VideoScalingMode(val label: String, val description: String) {
  ORIGINAL("Original", "Preserve exact source aspect ratio"),
  FIT("Fit", "Show entire video without cropping"),
  FILL("Fill", "Fill full display, cropping edges if needed"),
  STRETCH("Stretch", "Stretch to full screen dimensions"),
  ZOOM("Zoom", "1.22x magnified frame without distortion"),
  CUSTOM("Custom", "Manual zoom factor (0.5x – 3.0x)")
}

enum class EnhancementPreset(val label: String, val description: String) {
  OFF("Off", "Native hardware video decode only (zero shader overhead)"),
  PERFORMANCE("1080p Crisp", "Fast FidelityFX CAS sharpening & clean ink-line reconstruction (1.0x)"),
  BALANCED("1440p Anime4K", "Catmull-Rom super-res + Anime4K line thinning + CAS crispness (1.25x)"),
  HIGH_QUALITY("2.5K Ultra Crisp", "Deep Anime4K ink darkening + strong FidelityFX CAS with natural colors (1.5x)"),
  ULTRA_QUALITY("4K Super-Res", "Maximum FP32 4K Anime4K + Dual-Scale FidelityFX CAS up to 3840×2160 (2.0x)"),
  CUSTOM("Custom", "User-tuned 4K shader parameters")
}

data class EnhancementConfig(
  val enabled: Boolean = false,
  val preset: EnhancementPreset = EnhancementPreset.OFF,
  val upscaleStrength: Float = 0.82f,
  val sharpen: Float = 0.76f,
  val deband: Float = 0.45f,
  val denoise: Float = 0.30f,
  val edgeEnhance: Float = 0.78f,
  val vibrance: Float = 0f,
  val resolutionScale: Float = 1.5f,
  val frameGenEnabled: Boolean = false,
  val targetFps: Int = 60,
  val splitCompare: Boolean = false,
  val splitPosition: Float = 0.5f
) {
  companion object {
    fun forPreset(
      preset: EnhancementPreset,
      frameGenEnabled: Boolean = false,
      targetFps: Int = 60,
      splitCompare: Boolean = false,
      splitPosition: Float = 0.5f
    ): EnhancementConfig = when (preset) {
      EnhancementPreset.OFF -> EnhancementConfig(
        enabled = false, preset = preset,
        upscaleStrength = 0f, sharpen = 0f, deband = 0f, denoise = 0f,
        edgeEnhance = 0f, vibrance = 0f, resolutionScale = 1.0f,
        frameGenEnabled = frameGenEnabled, targetFps = targetFps,
        splitCompare = false, splitPosition = splitPosition
      )
      EnhancementPreset.PERFORMANCE -> EnhancementConfig(
        enabled = true, preset = preset,
        upscaleStrength = 0.55f, sharpen = 0.58f, deband = 0.20f, denoise = 0.15f,
        edgeEnhance = 0.55f, vibrance = 0f, resolutionScale = 1.0f,
        frameGenEnabled = frameGenEnabled, targetFps = targetFps,
        splitCompare = splitCompare, splitPosition = splitPosition
      )
      EnhancementPreset.BALANCED -> EnhancementConfig(
        enabled = true, preset = preset,
        upscaleStrength = 0.72f, sharpen = 0.70f, deband = 0.38f, denoise = 0.25f,
        edgeEnhance = 0.68f, vibrance = 0f, resolutionScale = 1.25f,
        frameGenEnabled = frameGenEnabled, targetFps = targetFps,
        splitCompare = splitCompare, splitPosition = splitPosition
      )
      EnhancementPreset.HIGH_QUALITY -> EnhancementConfig(
        enabled = true, preset = preset,
        upscaleStrength = 0.86f, sharpen = 0.84f, deband = 0.52f, denoise = 0.32f,
        edgeEnhance = 0.84f, vibrance = 0f, resolutionScale = 1.5f,
        frameGenEnabled = frameGenEnabled, targetFps = targetFps,
        splitCompare = splitCompare, splitPosition = splitPosition
      )
      EnhancementPreset.ULTRA_QUALITY -> EnhancementConfig(
        enabled = true, preset = preset,
        upscaleStrength = 1.0f, sharpen = 0.96f, deband = 0.62f, denoise = 0.38f,
        edgeEnhance = 0.95f, vibrance = 0f, resolutionScale = 2.0f,
        frameGenEnabled = frameGenEnabled, targetFps = targetFps,
        splitCompare = splitCompare, splitPosition = splitPosition
      )
      EnhancementPreset.CUSTOM -> EnhancementConfig(
        enabled = true, preset = preset,
        vibrance = 0f,
        frameGenEnabled = frameGenEnabled, targetFps = targetFps,
        splitCompare = splitCompare, splitPosition = splitPosition
      )
    }
  }
}

data class VideoTelemetry(
  val hasVideo: Boolean = false,
  val url: String = "",
  val videoWidth: Int = 0,
  val videoHeight: Int = 0,
  val isPlaying: Boolean = false,
  val currentTimeSec: Double = 0.0,
  val durationSec: Double = 0.0,
  val playbackRate: Float = 1.0f,
  val muted: Boolean = false,
  val sourceFps: Int = 0,
  val outputFps: Int = 0,
  val enhancementStatus: String = "IDLE",
  val gpuRenderer: String = "Detecting GPU…",
  val webGpuSupported: Boolean = false,
  val isDrmProtected: Boolean = false,
  val hasSubtitles: Boolean = false,
  val subtitlesVisible: Boolean = false,
  val bufferedPercent: Int = 0,
  val scalingMode: VideoScalingMode = VideoScalingMode.FIT,
  val customZoom: Float = 1.0f,
  val forceFullscreen: Boolean = false,
  val frameGenActive: Boolean = false,
  val frameGenAutoDisabled: Boolean = false
) {
  val resolutionLabel: String
    get() = if (videoWidth > 0 && videoHeight > 0) "${videoWidth}×${videoHeight}" else "N/A"

  val isShaderActive: Boolean
    get() = enhancementStatus == "ACTIVE_WEBGPU" ||
      enhancementStatus == "ACTIVE_WEBGL2" ||
      enhancementStatus == "ACTIVE_WEBGL1" ||
      enhancementStatus == "ACTIVE_CSS_GPU_FILTER" ||
      enhancementStatus == "ACTIVE_SVG_GPU_FILTER"

  val statusHumanLabel: String
    get() = when (enhancementStatus) {
      "ACTIVE_WEBGPU" -> if (frameGenActive) "4K WebGPU Zero-Copy + Optical-Flow" else "4K WebGPU Zero-Copy Active"
      "ACTIVE_WEBGL2" -> if (frameGenActive) "4K WebGL2 + Optical-Flow Active" else "4K WebGL2 Shader Active"
      "ACTIVE_WEBGL1" -> "WebGL1 4K Fallback Active"
      "ACTIVE_CSS_GPU_FILTER" -> "Zero-Copy GPU Clarity Active"
      "ACTIVE_SVG_GPU_FILTER" -> "Zero-Copy GPU Crisp Active"
      "UNSUPPORTED" -> "GPU Shader Unavailable"
      else -> "Native Hardware Decode"
    }
}

data class BrowserTab(
  val id: String,
  val title: String = "Home",
  val url: String = "",
  val isPrivate: Boolean = false,
  val groupName: String = "Default",
  val isLoading: Boolean = false,
  val progress: Int = 0,
  val canGoBack: Boolean = false,
  val canGoForward: Boolean = false,
  val isSecure: Boolean = true,
  val isDesktopMode: Boolean = false,
  val isSuspended: Boolean = false,
  val lastAccessedAt: Long = System.currentTimeMillis()
) {
  val isHomePage: Boolean get() = url.isBlank() || url == "about:home"
}

data class ExtensionInfo(
  val id: String,
  val name: String,
  val version: String,
  val description: String,
  val isBuiltIn: Boolean,
  val isEnabled: Boolean,
  val permissions: List<String>,
  val compatibilityStatus: String,
  val technicalNotes: String
)

data class PerformanceStats(
  val appRamMb: Int = 0,
  val totalDeviceRamMb: Int = 0,
  val availableDeviceRamMb: Int = 0,
  val activeSessions: Int = 0,
  val suspendedSessions: Int = 0,
  val thermalStatusLabel: String = "Nominal",
  val displayRefreshRateHz: Int = 60,
  val gpuInfo: String = "Hardware Accelerated"
)

data class ReaderArticle(
  val title: String,
  val url: String,
  val paragraphs: List<String>
)

data class BlockedEvent(
  val domain: String,
  val pageHost: String,
  val resourceType: String,
  val timestamp: Long = System.currentTimeMillis()
)

enum class StreamType(val label: String) {
  HLS("HLS Video"),
  DIRECT("MP4 / WebM Video")
}

data class HlsVariant(
  val label: String,
  val resolution: String,
  val bandwidthKbps: Int,
  val url: String,
  val estimatedBytes: Long = 0L,
  val durationSec: Double = 0.0
)

data class DetectedVideoStream(
  val url: String,
  val title: String,
  val streamType: StreamType,
  val mimeType: String,
  val sizeBytes: Long = 0L,
  val qualityLabel: String = "",
  val referer: String = "",
  val origin: String = "",
  val userAgent: String = "",
  val cookie: String = "",
  val isDrmProtected: Boolean = false,
  val detectedAt: Long = System.currentTimeMillis()
) {
  fun toRequestHeaders(): Map<String, String> = buildMap {
    if (referer.isNotBlank()) put("Referer", referer)
    if (origin.isNotBlank()) put("Origin", origin)
    if (userAgent.isNotBlank()) put("User-Agent", userAgent)
    if (cookie.isNotBlank()) put("Cookie", cookie)
  }
}

data class BrowserSettings(
  val themeMode: ThemeMode = ThemeMode.AMOLED,
  val accentPalette: AccentPalette = AccentPalette.ZENITH_VIOLET,
  val dynamicColor: Boolean = false,
  val toolbarPosition: ToolbarPosition = ToolbarPosition.TOP,
  val animationsEnabled: Boolean = true,
  val searchEngine: SearchEngine = SearchEngine.GOOGLE,
  val homepageUrl: String = "about:home",
  val restoreTabsOnStartup: Boolean = true,
  val defaultDesktopMode: Boolean = false,
  val javaScriptEnabled: Boolean = true,
  val cookiesEnabled: Boolean = true,
  val defaultScalingMode: VideoScalingMode = VideoScalingMode.FIT,
  val defaultEnhancementPreset: EnhancementPreset = EnhancementPreset.OFF,
  val autoFullscreenVideo: Boolean = false,
  val autoLandscapeFullscreen: Boolean = true,
  val defaultPlaybackSpeed: Float = 1.0f,
  val autoHideControlsSec: Int = 4,
  val keepScreenAwakeVideo: Boolean = true,
  val floatingButtonOnRight: Boolean = true,
  val performanceProfile: PerformanceProfile = PerformanceProfile.BALANCED,
  val showPerformanceOverlay: Boolean = false,
  val adBlockEnabled: Boolean = true,
  val popupBlockEnabled: Boolean = true,
  val trackerBlockEnabled: Boolean = true,
  val downloadSubfolder: String = "ZenithFox",
  val autoPipOnMinimize: Boolean = true,
  val autoCheckUpdates: Boolean = true,
  val updateRepoSlug: String = "",
  val skippedUpdateVersion: String = ""
)

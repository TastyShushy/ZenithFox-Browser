package com.example

import com.example.data.model.BrowserTab
import com.example.data.model.EnhancementConfig
import com.example.data.model.EnhancementPreset
import com.example.data.model.SearchEngine
import com.example.data.model.VideoScalingMode
import com.example.data.model.VideoTelemetry
import com.example.engine.UrlUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimeBrowserCoreLogicTest {

  @Test
  fun urlNormalizationHandlesDomainsUrlsAndSearchQueries() {
    assertEquals("https://anilist.co", UrlUtils.normalizeInputToUrl("anilist.co", SearchEngine.GOOGLE))
    assertEquals("https://example.com/episode/1", UrlUtils.normalizeInputToUrl("https://example.com/episode/1", SearchEngine.GOOGLE))
    assertEquals(
      "https://www.google.com/search?q=frieren+season+2",
      UrlUtils.normalizeInputToUrl("frieren season 2", SearchEngine.GOOGLE)
    )
    assertEquals(
      "https://duckduckgo.com/?q=anime+schedule",
      UrlUtils.normalizeInputToUrl("anime schedule", SearchEngine.DUCKDUCKGO)
    )
    assertEquals("about:home", UrlUtils.normalizeInputToUrl("about:home", SearchEngine.GOOGLE))
  }

  @Test
  fun enhancementPresetsMatchExpectedShaderScaling() {
    val off = EnhancementConfig.forPreset(EnhancementPreset.OFF)
    val perf = EnhancementConfig.forPreset(EnhancementPreset.PERFORMANCE)
    val balanced = EnhancementConfig.forPreset(EnhancementPreset.BALANCED)
    val high = EnhancementConfig.forPreset(EnhancementPreset.HIGH_QUALITY)
    val ultra = EnhancementConfig.forPreset(EnhancementPreset.ULTRA_QUALITY)

    assertFalse(off.enabled)
    assertTrue(perf.enabled && balanced.enabled && high.enabled && ultra.enabled)
    assertTrue(perf.resolutionScale < balanced.resolutionScale)
    assertTrue(balanced.resolutionScale < high.resolutionScale)
    assertTrue(high.resolutionScale < ultra.resolutionScale)
    assertTrue(ultra.upscaleStrength > perf.upscaleStrength)
  }

  @Test
  fun videoTelemetryDistinguishesWebGl2OpticalFlowSvgFallbackAndDrm() {
    val webgl2 = VideoTelemetry(
      hasVideo = true,
      videoWidth = 1920,
      videoHeight = 1080,
      sourceFps = 24,
      outputFps = 60,
      enhancementStatus = "ACTIVE_WEBGL2",
      frameGenActive = true,
      scalingMode = VideoScalingMode.STRETCH
    )
    val svgFallback = VideoTelemetry(
      hasVideo = true,
      videoWidth = 1920,
      videoHeight = 1080,
      sourceFps = 24,
      outputFps = 24,
      enhancementStatus = "ACTIVE_SVG_GPU_FILTER"
    )
    val corsProtected = VideoTelemetry(
      hasVideo = true,
      videoWidth = 1280,
      videoHeight = 720,
      enhancementStatus = "CORS_OR_DRM_PROTECTED",
      isDrmProtected = true
    )

    assertTrue(webgl2.isShaderActive)
    assertEquals("1920×1080", webgl2.resolutionLabel)
    assertEquals("WebGL2 Anime4K + Optical-Flow Active", webgl2.statusHumanLabel)

    val cssSafeFallback = VideoTelemetry(
      hasVideo = true,
      videoWidth = 1920,
      videoHeight = 1080,
      sourceFps = 24,
      outputFps = 24,
      enhancementStatus = "ACTIVE_CSS_GPU_FILTER"
    )
    assertTrue(cssSafeFallback.isShaderActive)
    assertEquals("WebRender GPU Color & Clarity (Safe Mode)", cssSafeFallback.statusHumanLabel)
    assertEquals(com.example.data.model.AccentPalette.ZENITH_VIOLET, com.example.data.model.BrowserSettings().accentPalette)

    assertTrue(svgFallback.isShaderActive)
    assertEquals("WebRender GPU Sharpen (Cross-Origin)", svgFallback.statusHumanLabel)

    assertFalse(corsProtected.isShaderActive)
    assertEquals("1280×720", corsProtected.resolutionLabel)
    assertTrue(corsProtected.statusHumanLabel.contains("CSS Scaling Only"))

    val frameGenCfg = EnhancementConfig.forPreset(
      preset = EnhancementPreset.HIGH_QUALITY,
      frameGenEnabled = true,
      targetFps = 90
    )
    assertTrue(frameGenCfg.enabled)
    assertTrue(frameGenCfg.frameGenEnabled)
    assertEquals(90, frameGenCfg.targetFps)
  }

  @Test
  fun formattersProduceAccurateReadableStrings() {
    assertEquals("0 B", UrlUtils.formatBytes(0))
    assertEquals("512 B", UrlUtils.formatBytes(512))
    assertEquals("1.0 MB", UrlUtils.formatBytes(1024L * 1024L))
    assertEquals("0:45", UrlUtils.formatTimeSec(45.0))
    assertEquals("1:05:09", UrlUtils.formatTimeSec(3909.0))
  }

  @Test
  fun browserTabHomePageDetectionWorks() {
    assertTrue(BrowserTab(id = "1", url = "").isHomePage)
    assertTrue(BrowserTab(id = "2", url = "about:home").isHomePage)
    assertFalse(BrowserTab(id = "3", url = "https://anilist.co").isHomePage)
  }

  @Test
  fun hlsPlaylistParserExtractsQualityVariantsSegmentsAndDetectsDrm() {
    val master = """
      #EXTM3U
      #EXT-X-STREAM-INF:BANDWIDTH=1200000,RESOLUTION=1280x720
      720p/index.m3u8
      #EXT-X-STREAM-INF:BANDWIDTH=3800000,RESOLUTION=1920x1080
      1080p/index.m3u8
    """.trimIndent()

    val variants = com.example.downloads.HlsVideoDownloader.parseMasterVariants(
      "https://cdn.example.com/anime/ep1/master.m3u8",
      master
    )
    assertEquals(2, variants.size)
    assertEquals("720p", variants[0].label)
    assertEquals("https://cdn.example.com/anime/ep1/720p/index.m3u8", variants[0].url)
    assertEquals("1080p", variants[1].label)
    assertEquals(3800, variants[1].bandwidthKbps)

    val media = """
      #EXTM3U
      #EXT-X-MEDIA-SEQUENCE:5
      #EXT-X-KEY:METHOD=AES-128,URI="enc.key"
      #EXTINF:6.0,
      seg0.ts
      #EXTINF:6.0,
      seg1.ts
    """.trimIndent()

    val parsed = com.example.downloads.HlsVideoDownloader.parseMediaPlaylist(
      "https://cdn.example.com/anime/ep1/1080p/index.m3u8",
      media
    )
    assertEquals(5L, parsed.mediaSequence)
    assertEquals(2, parsed.segments.size)
    assertEquals("https://cdn.example.com/anime/ep1/1080p/seg0.ts", parsed.segments[0].url)
    assertEquals("https://cdn.example.com/anime/ep1/1080p/enc.key", parsed.segments[0].keyUri)
    assertFalse(com.example.downloads.HlsVideoDownloader.isDrmManifest(media))

    assertEquals(12.0, com.example.downloads.HlsVideoDownloader.parseDurationSeconds(media), 0.01)

    val drmManifest = """
      #EXTM3U
      #EXT-X-KEY:METHOD=SAMPLE-AES,KEYFORMAT="urn:uuid:edef8ba9-79d6-4ace-a3c8-27dcd51d21ed",URI="data:text/plain;base64,AAA"
    """.trimIndent()
    assertTrue(com.example.downloads.HlsVideoDownloader.isDrmManifest(drmManifest))
    assertEquals(
      setOf(com.example.data.model.StreamType.HLS, com.example.data.model.StreamType.DIRECT),
      com.example.data.model.StreamType.entries.toSet()
    )
  }
}

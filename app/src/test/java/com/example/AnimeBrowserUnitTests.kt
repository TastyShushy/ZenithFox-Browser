package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.adblock.AdBlockEngine
import com.example.data.db.AnimeBrowserDatabase
import com.example.data.model.EnhancementConfig
import com.example.data.model.EnhancementPreset
import com.example.data.model.SearchEngine
import com.example.data.model.VideoTelemetry
import com.example.engine.UrlUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AnimeBrowserUnitTests {

  private lateinit var context: Context
  private lateinit var db: AnimeBrowserDatabase
  private lateinit var adBlockEngine: AdBlockEngine
  private val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    db = Room.inMemoryDatabaseBuilder(context, AnimeBrowserDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    adBlockEngine = AdBlockEngine(db.customBlockRuleDao(), db.siteSettingDao(), testScope)
  }

  @After
  fun tearDown() {
    db.close()
  }

  @Test
  fun appNameResourceMatchesBranding() {
    assertEquals("ZenithFox Browser", context.getString(R.string.app_name))
  }

  @Test
  fun urlNormalizationHandlesDomainsAndSearches() {
    assertEquals("https://anilist.co", UrlUtils.normalizeInputToUrl("anilist.co", SearchEngine.GOOGLE))
    assertEquals("https://example.com/watch", UrlUtils.normalizeInputToUrl("https://example.com/watch", SearchEngine.GOOGLE))
    assertEquals(
      "https://www.google.com/search?q=frieren+episode+1",
      UrlUtils.normalizeInputToUrl("frieren episode 1", SearchEngine.GOOGLE)
    )
    assertEquals(
      "https://duckduckgo.com/?q=anime+news",
      UrlUtils.normalizeInputToUrl("anime news", SearchEngine.DUCKDUCKGO)
    )
  }

  @Test
  fun qrCodeGeneratorProducesValidBitmap() {
    val bmp = UrlUtils.generateQrBitmap("https://anilist.co", 256)
    assertNotNull(bmp)
    assertEquals(256, bmp!!.width)
    assertEquals(256, bmp.height)
  }

  @Test
  fun enhancementPresetsScaleShaderParametersCorrectly() {
    val off = EnhancementConfig.forPreset(EnhancementPreset.OFF)
    val balanced = EnhancementConfig.forPreset(EnhancementPreset.BALANCED)
    val ultra = EnhancementConfig.forPreset(EnhancementPreset.ULTRA_QUALITY)

    assertFalse(off.enabled)
    assertTrue(balanced.enabled)
    assertTrue(ultra.enabled)
    assertTrue(ultra.upscaleStrength > balanced.upscaleStrength)
    assertTrue(ultra.resolutionScale > balanced.resolutionScale)
  }

  @Test
  fun videoTelemetryReportsCorsOrDrmHonestly() {
    val active = VideoTelemetry(hasVideo = true, videoWidth = 1920, videoHeight = 1080, enhancementStatus = "ACTIVE_WEBGL2")
    val corsBlocked = VideoTelemetry(hasVideo = true, videoWidth = 1280, videoHeight = 720, enhancementStatus = "CORS_OR_DRM_PROTECTED")

    assertTrue(active.isShaderActive)
    assertEquals("1920×1080", active.resolutionLabel)
    assertEquals("4K WebGL2 Shader Active", active.statusHumanLabel)
    assertFalse(corsBlocked.isShaderActive)
    assertEquals("Native Hardware Decode", corsBlocked.statusHumanLabel)
  }

  @Test
  fun adBlockEngineBlocksAdDomainsRedirectsAndPopupsWhileRespectingWhitelist() = runBlocking {
    assertTrue(adBlockEngine.shouldBlockNavigation("https://pagead2.googlesyndication.com/pagead.js", "https://anilist.co", false))
    assertTrue(adBlockEngine.shouldBlockNavigation("https://popads.net/pop", "https://anilist.co", true))
    assertFalse(adBlockEngine.shouldBlockNavigation("https://anilist.co/home", "https://anilist.co", false))

    // Cross-domain auto-redirect without user gesture is blocked
    assertTrue(
      adBlockEngine.shouldBlockNavigation(
        targetUrl = "https://random-promo-offer.example/landing",
        currentPageUrl = "https://animesite.example/watch/1",
        isRedirectOrPopup = false,
        isDirectNavigation = false,
        hasUserGesture = false
      )
    )

    // Cross-domain popup tab from anime site is blocked, while same-domain navigation is allowed
    assertTrue(adBlockEngine.shouldBlockPopupWindow("https://promo-redirect.cfd/win", "https://animesite.example/watch/1"))
    assertFalse(adBlockEngine.shouldBlockPopupWindow("https://cdn.animesite.example/ep2", "https://animesite.example/watch/1"))

    // Search engine clicks and direct address bar navigations are allowed
    assertFalse(
      adBlockEngine.shouldBlockNavigation(
        targetUrl = "https://anilist.co/home",
        currentPageUrl = "https://www.google.com/search?q=anilist",
        isRedirectOrPopup = false,
        isDirectNavigation = false,
        hasUserGesture = true
      )
    )
    assertFalse(
      adBlockEngine.shouldBlockNavigation(
        targetUrl = "https://promo-redirect.cfd/win",
        currentPageUrl = "https://animesite.example/watch/1",
        isRedirectOrPopup = false,
        isDirectNavigation = true,
        hasUserGesture = true
      )
    )

    // Custom block rule
    adBlockEngine.addCustomRule("custom-tracker.test")
    delay(50)
    assertTrue(adBlockEngine.shouldBlockNavigation("https://sub.custom-tracker.test/pixel.gif", "https://anilist.co", false))

    // Per-site whitelist bypass
    adBlockEngine.toggleHostWhitelist("anilist.co")
    delay(50)
    assertFalse(adBlockEngine.shouldBlockNavigation("https://pagead2.googlesyndication.com/pagead.js", "https://anilist.co", false))
  }
}

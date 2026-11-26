package com.example.adblock

import android.net.Uri
import com.example.data.db.CustomBlockRuleDao
import com.example.data.db.CustomBlockRuleEntity
import com.example.data.db.SiteSettingDao
import com.example.data.db.SiteSettingEntity
import com.example.data.model.BlockedEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicInteger

class AdBlockEngine(
  private val customBlockRuleDao: CustomBlockRuleDao,
  private val siteSettingDao: SiteSettingDao,
  scope: CoroutineScope
) {

  private val builtInAdAndTrackerDomains: Set<String> = setOf(
    "doubleclick.net", "googlesyndication.com", "googleadservices.com", "adservice.google.com",
    "adnxs.com", "popads.net", "popcash.net", "propellerads.com", "exoclick.com", "exosrv.com",
    "juicyads.com", "trafficjunky.net", "trafficstars.com", "adsterra.com", "highcpmgate.com",
    "profitablegate.com", "highrevenuegate.com", "effectivegatecpm.com", "topcreativeformat.com",
    "taboola.com", "outbrain.com", "criteo.com", "rubiconproject.com", "pubmatic.com", "openx.net",
    "scorecardresearch.com", "quantserve.com", "hotjar.com", "mixpanel.com", "segment.io",
    "clarity.ms", "mc.yandex.ru", "amazon-adsystem.com", "ads-twitter.com", "connect.facebook.net",
    "clickadu.com", "hilltopads.net", "monetag.com", "galaksion.com", "adcash.com", "a-ads.com",
    "realsrv.com", "magsrv.com", "onclickperformance.com", "onclickalgo.com", "onclickmax.com",
    "acscdn.com", "dtscout.com", "bidgear.com", "mgid.com", "revcontent.com", "admaven.com",
    "ad-maven.com", "pushub.net", "pushengage.com", "notifpush.com", "alwingulla.com",
    "glizauvo.net", "dolohen.com", "pemsrv.com", "tsyndicate.com", "bet365.com", "1xbet.com",
    "melbet.com", "mostbet.com", "stake.com", "bc.game", "bcvc.live", "ouo.io", "shrinkme.io",
    "linkvertise.com", "adf.ly", "shorte.st", "directrev.com", "adskeeper.com", "zedo.com",
    "bidvertiser.com", "infolinks.com", "revenuehits.com", "propellerclick.com", "go2cloud.org",
    "voluumtrk.com", "bemobtrk.com", "redtrack.io"
  )

  private val maliciousDomains: Set<String> = setOf(
    "coinhive.com", "coin-hive.com", "cryptoloot.pro", "jsecoin.com"
  )

  private val suspiciousRedirectTlds: Set<String> = setOf(
    "cfd", "sbs", "click", "buzz", "loan", "win", "icu", "cyou", "gq", "tk", "ml", "cf", "ga", "cam", "top", "xyz"
  )

  private val suspiciousRedirectUrlRegex = Regex(
    """(/afu\.php|/popunder|/adclick|/go\.php|/out\.php|/click\.php|[?&](zoneid|clickid|bannerid|pop_id|sub_id|offer_id)=)""",
    RegexOption.IGNORE_CASE
  )

  val customRules: StateFlow<List<CustomBlockRuleEntity>> =
    customBlockRuleDao.observeAll().stateIn(scope, SharingStarted.Eagerly, emptyList())

  val whitelistedHosts: StateFlow<Set<String>> =
    siteSettingDao.observeWhitelistedHosts()
      .map { list -> list.map { it.lowercase() }.toSet() }
      .stateIn(scope, SharingStarted.Eagerly, emptySet())

  @Volatile
  var isEnabled: Boolean = true

  @Volatile
  var isPopupBlockingEnabled: Boolean = true

  private val totalBlockedCounter = AtomicInteger(0)
  private val _totalBlockedCount = MutableStateFlow(0)
  val totalBlockedCount: StateFlow<Int> = _totalBlockedCount.asStateFlow()

  private val _recentBlocked = MutableStateFlow<List<BlockedEvent>>(emptyList())
  val recentBlocked: StateFlow<List<BlockedEvent>> = _recentBlocked.asStateFlow()

  /**
   * Decides whether to block a top-level navigation / redirect in GeckoView's onLoadRequest.
   * Stops BOTH known ad networks AND sneaky rotating cross-domain ad redirects on anime sites!
   */
  fun shouldBlockNavigation(
    targetUrl: String,
    currentPageUrl: String?,
    isRedirectOrPopup: Boolean,
    isDirectNavigation: Boolean = false,
    hasUserGesture: Boolean = true
  ): Boolean {
    if (!isEnabled) return false
    if (isDirectNavigation) return false // Never block URLs the user typed in the address bar!

    val pageHost = extractHost(currentPageUrl)
    if (pageHost.isNotEmpty() && isHostWhitelisted(pageHost)) return false

    val targetHost = extractHost(targetUrl)
    if (targetHost.isEmpty()) return false

    if (matchesSet(targetHost, maliciousDomains)) {
      recordBlocked(targetHost, pageHost, "malicious")
      return true
    }

    if (matchesSet(targetHost, builtInAdAndTrackerDomains) || matchesCustomRules(targetHost)) {
      recordBlocked(targetHost, pageHost, if (isRedirectOrPopup) "popup_redirect" else "ad_domain")
      return true
    }

    if (suspiciousRedirectUrlRegex.containsMatchIn(targetUrl)) {
      recordBlocked(targetHost, pageHost, "ad_redirect_pattern")
      return true
    }

    // Cross-domain hijack protection when popup/redirect blocking is enabled
    val isCrossDomain = pageHost.isNotEmpty() && !sameRootDomain(pageHost, targetHost)
    if (isPopupBlockingEnabled && isCrossDomain) {
      // Allow legitimate navigation from search engines (Google, DuckDuckGo, Bing, Brave)
      if (isSearchEngineHost(pageHost)) return false

      // 1. Automatic script redirect without user gesture
      if (!hasUserGesture) {
        recordBlocked(targetHost, pageHost, "auto_redirect")
        return true
      }

      // 2. HTTP 3xx redirect chain leaving the current anime site to a different domain
      if (isRedirectOrPopup) {
        recordBlocked(targetHost, pageHost, "cross_domain_redirect")
        return true
      }

      // 3. Click-hijack redirect to disposable ad TLDs (.cfd, .sbs, .click, .top, .xyz, etc.)
      val tld = targetHost.substringAfterLast('.', "")
      if (tld in suspiciousRedirectTlds) {
        recordBlocked(targetHost, pageHost, "suspicious_tld_redirect")
        return true
      }
    }

    return false
  }

  /**
   * Decides whether to block an unsolicited new tab/window popup (window.open / target="_blank").
   */
  fun shouldBlockPopupWindow(targetUrl: String, currentPageUrl: String?): Boolean {
    if (!isEnabled || !isPopupBlockingEnabled) return false
    val pageHost = extractHost(currentPageUrl)
    if (pageHost.isNotEmpty() && isHostWhitelisted(pageHost)) return false

    val targetHost = extractHost(targetUrl)
    if (targetHost.isEmpty()) return true // Block blank popunders

    if (matchesSet(targetHost, builtInAdAndTrackerDomains) ||
      matchesCustomRules(targetHost) ||
      suspiciousRedirectUrlRegex.containsMatchIn(targetUrl)
    ) {
      recordBlocked(targetHost, pageHost, "popup_ad")
      return true
    }

    // Block cross-domain popup tabs from non-search sites (primary cause of anime site ad redirects!)
    if (pageHost.isNotEmpty() && !isSearchEngineHost(pageHost) && !sameRootDomain(pageHost, targetHost)) {
      recordBlocked(targetHost, pageHost, "popup_redirect")
      return true
    }

    return false
  }

  fun recordBlocked(domain: String, pageHost: String, resourceType: String) {
    val count = totalBlockedCounter.incrementAndGet()
    _totalBlockedCount.value = count
    _recentBlocked.update { current ->
      (listOf(BlockedEvent(domain = domain, pageHost = pageHost, resourceType = resourceType)) + current).take(60)
    }
  }

  fun isHostWhitelisted(host: String): Boolean {
    val normalized = host.lowercase()
    return whitelistedHosts.value.any { normalized == it || normalized.endsWith(".$it") }
  }

  suspend fun addCustomRule(rawDomain: String) {
    val cleaned = rawDomain.trim()
      .removePrefix("https://")
      .removePrefix("http://")
      .substringBefore("/")
      .lowercase()
    if (cleaned.isNotEmpty() && cleaned.contains(".")) {
      customBlockRuleDao.insert(CustomBlockRuleEntity(domain = cleaned))
    }
  }

  suspend fun removeCustomRule(domain: String) {
    customBlockRuleDao.delete(domain)
  }

  suspend fun toggleHostWhitelist(host: String) {
    val cleanHost = host.lowercase().trim()
    if (cleanHost.isEmpty()) return
    val existing = siteSettingDao.getForHost(cleanHost)
    if (existing != null) {
      siteSettingDao.upsert(
        existing.copy(
          adBlockWhitelisted = !existing.adBlockWhitelisted,
          updatedAt = System.currentTimeMillis()
        )
      )
    } else {
      siteSettingDao.upsert(
        SiteSettingEntity(
          host = cleanHost,
          adBlockWhitelisted = true,
          updatedAt = System.currentTimeMillis()
        )
      )
    }
  }

  fun getAllCustomDomains(): List<String> = customRules.value.map { it.domain }

  fun getAllWhitelistedHosts(): List<String> = whitelistedHosts.value.toList()

  fun resetStats() {
    totalBlockedCounter.set(0)
    _totalBlockedCount.value = 0
    _recentBlocked.value = emptyList()
  }

  private fun matchesCustomRules(host: String): Boolean {
    val customSet = customRules.value.map { it.domain.lowercase() }.toSet()
    return matchesSet(host, customSet)
  }

  private fun matchesSet(host: String, domains: Set<String>): Boolean {
    val lower = host.lowercase()
    if (domains.contains(lower)) return true
    val parts = lower.split(".")
    for (i in 1 until parts.size - 1) {
      val suffix = parts.subList(i, parts.size).joinToString(".")
      if (domains.contains(suffix)) return true
    }
    return false
  }

  private fun sameRootDomain(hostA: String, hostB: String): Boolean {
    val rootA = rootDomain(hostA)
    val rootB = rootDomain(hostB)
    return rootA.isNotEmpty() && rootA == rootB
  }

  private fun rootDomain(host: String): String {
    val parts = host.lowercase().split(".").filter { it.isNotBlank() }
    return if (parts.size <= 2) parts.joinToString(".") else parts.takeLast(2).joinToString(".")
  }

  private fun isSearchEngineHost(host: String): Boolean {
    val root = rootDomain(host)
    return root == "google.com" || root == "duckduckgo.com" || root == "bing.com" || root == "brave.com"
  }

  private fun extractHost(url: String?): String {
    if (url.isNullOrBlank()) return ""
    return runCatching { Uri.parse(url).host?.lowercase().orEmpty() }.getOrDefault("")
  }
}

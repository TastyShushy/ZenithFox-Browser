// Built-in GeckoView webRequest Ad, Tracker, Pop-Under & Redirect Shield
(function () {
  "use strict";

  let enabled = true;
  let popupBlockEnabled = true;
  let whitelist = new Set();
  let blockedDomains = new Set([
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
    "glizauvo.net", "oeele.com", "whourq.com", "dolohen.com", "pemsrv.com", "tsyndicate.com",
    "bet365.com", "1xbet.com", "melbet.com", "mostbet.com", "stake.com", "bc.game",
    "bcvc.live", "ouo.io", "shrinkme.io", "linkvertise.com", "adf.ly", "shorte.st",
    "directrev.com", "adskeeper.com", "zedo.com", "bidvertiser.com", "infolinks.com",
    "revenuehits.com", "propellerclick.com", "mybestclick.net", "go2cloud.org", "voluumtrk.com",
    "bemobtrk.com", "keitaro.io", "binom.org", "redtrack.io", "adjust.com", "appsflyer.com"
  ]);

  const SUSPICIOUS_URL_PATTERNS = [
    /\/afu\.php/i,
    /\/popunder/i,
    /\/in-page-push/i,
    /\/adclick/i,
    /[?&](zoneid|clickid|bannerid|pop_id|sub_id|offer_id)=/i,
    /\/vast[^/]*\.xml/i,
    /\/pagead\//i
  ];

  let port = null;

  function extractHost(urlStr) {
    try { return new URL(urlStr).hostname.toLowerCase(); } catch (_) { return ""; }
  }

  function matchesDomainSet(host, domainSet) {
    if (!host) return false;
    if (domainSet.has(host)) return true;
    const parts = host.split(".");
    for (let i = 1; i < parts.length - 1; i++) {
      const suffix = parts.slice(i).join(".");
      if (domainSet.has(suffix)) return true;
    }
    return false;
  }

  function matchesUrlPatterns(urlStr) {
    for (let i = 0; i < SUSPICIOUS_URL_PATTERNS.length; i++) {
      if (SUSPICIOUS_URL_PATTERNS[i].test(urlStr)) return true;
    }
    return false;
  }

  function connect() {
    try {
      port = browser.runtime.connectNative("animeAdBlock");
      port.onMessage.addListener((msg) => {
        if (!msg || !msg.type) return;
        if (msg.type === "SYNC_RULES") {
          enabled = msg.enabled !== false;
          popupBlockEnabled = msg.popupBlockEnabled !== false;
          whitelist = new Set((msg.whitelist || []).map((h) => String(h).toLowerCase()));
          if (Array.isArray(msg.customDomains)) {
            for (const d of msg.customDomains) {
              const cleaned = String(d).trim().toLowerCase();
              if (cleaned) blockedDomains.add(cleaned);
            }
          }
        }
      });
      port.onDisconnect.addListener(() => {
        port = null;
        setTimeout(connect, 2000);
      });
      port.postMessage({ type: "ADBLOCK_READY" });
    } catch (_) {
      setTimeout(connect, 2500);
    }
  }

  browser.webRequest.onBeforeRequest.addListener(
    (details) => {
      if (!enabled) return {};
      const pageHost = extractHost(details.documentUrl || details.originUrl || details.url);
      if (matchesDomainSet(pageHost, whitelist)) return {};

      const reqHost = extractHost(details.url);
      if (matchesDomainSet(reqHost, blockedDomains) || matchesUrlPatterns(details.url)) {
        if (port) {
          try {
            port.postMessage({
              type: "REQUEST_BLOCKED",
              domain: reqHost || "ad-script",
              pageHost: pageHost,
              resourceType: details.type || "script"
            });
          } catch (_) {}
        }
        return { cancel: true };
      }
      return {};
    },
    { urls: ["<all_urls>"] },
    ["blocking"]
  );

  browser.runtime.onMessage.addListener((msg) => {
    if (msg && msg.type === "GET_COSMETIC_STATE") {
      const host = extractHost(msg.url || "");
      return Promise.resolve({
        enabled: enabled && !matchesDomainSet(host, whitelist),
        popupBlockEnabled: popupBlockEnabled && !matchesDomainSet(host, whitelist)
      });
    }
  });

  connect();
})();

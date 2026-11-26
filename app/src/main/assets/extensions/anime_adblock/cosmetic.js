// Aggressive Cosmetic Ad Filter, Pop-Under Blocker & Invisible Click-Hijack Overlay Killer
(function () {
  "use strict";

  function getRootDomain(hostname) {
    const parts = (hostname || "").toLowerCase().split(".").filter(Boolean);
    if (parts.length <= 2) return parts.join(".");
    return parts.slice(-2).join(".");
  }

  function isCrossOriginUrl(urlStr) {
    if (!urlStr) return false;
    try {
      const u = new URL(urlStr, location.href);
      if (u.protocol !== "http:" && u.protocol !== "https:") return false;
      return getRootDomain(u.hostname) !== getRootDomain(location.hostname);
    } catch (_) {
      return false;
    }
  }

  // Immediately guard against pop-unders & synthetic click-hijack redirects before page scripts load
  let blockPopups = true;
  let blockCosmetic = true;

  const origOpen = window.open;
  window.open = function (url, name, specs) {
    if (blockPopups) {
      if (!url || url === "about:blank" || isCrossOriginUrl(String(url))) {
        return null;
      }
    }
    return origOpen.call(window, url, name, specs);
  };

  // Block synthetic anchor.click() popups used by anime streaming ad scripts
  const origAnchorClick = HTMLAnchorElement.prototype.click;
  HTMLAnchorElement.prototype.click = function () {
    if (blockPopups && this.target === "_blank" && isCrossOriginUrl(this.href)) {
      return;
    }
    return origAnchorClick.apply(this, arguments);
  };

  // Intercept invisible click-hijack overlays (full-screen transparent divs/anchors over video players)
  function purgeClickHijackOverlays() {
    if (!blockCosmetic || !document.body) return;
    try {
      const vw = window.innerWidth || 360;
      const vh = window.innerHeight || 640;
      const nodes = document.querySelectorAll("div, a, iframe, section");
      for (let i = 0; i < nodes.length; i++) {
        const el = nodes[i];
        if (el.querySelector("video") || el.hasAttribute("data-ab-active")) continue;
        const style = getComputedStyle(el);
        const z = parseInt(style.zIndex, 10) || 0;
        if ((style.position === "fixed" || style.position === "absolute") && z >= 999) {
          const rect = el.getBoundingClientRect();
          const coversBigArea = rect.width >= vw * 0.6 && rect.height >= vh * 0.5;
          const isTransparentTrap =
            (parseFloat(style.opacity) <= 0.05 || style.backgroundColor === "rgba(0, 0, 0, 0)") &&
            (el.innerText || "").trim().length === 0;
          if (coversBigArea && isTransparentTrap) {
            el.style.setProperty("display", "none", "important");
            el.style.setProperty("pointer-events", "none", "important");
          }
        }
      }
    } catch (_) {}
  }

  // Stop _blank external ad links triggered during tap on video player areas
  document.addEventListener(
    "click",
    (ev) => {
      if (!blockPopups) return;
      const anchor = ev.target && ev.target.closest ? ev.target.closest("a[target='_blank']") : null;
      if (anchor && isCrossOriginUrl(anchor.href)) {
        const isEmptyOrOverlay = (anchor.innerText || "").trim().length === 0;
        if (isEmptyOrOverlay) {
          ev.preventDefault();
          ev.stopImmediatePropagation();
        }
      }
    },
    true
  );

  browser.runtime.sendMessage({ type: "GET_COSMETIC_STATE", url: location.href })
    .then((state) => {
      if (!state) return;
      blockCosmetic = !!state.enabled;
      blockPopups = !!state.popupBlockEnabled;
      if (!blockCosmetic) return;

      const style = document.createElement("style");
      style.id = "anime-browser-cosmetic-filter";
      style.textContent = `
        ins.adsbygoogle,
        iframe[src*="doubleclick.net"],
        iframe[src*="googlesyndication.com"],
        iframe[src*="exoclick.com"],
        iframe[src*="popads.net"],
        iframe[src*="adsterra"],
        iframe[src*="hilltopads"],
        iframe[src*="clickadu"],
        iframe[src*="monetag"],
        iframe[src*="mgid.com"],
        div[id*="taboola"],
        div[class*="outbrain"],
        div[class*="ad-banner"],
        div[class*="popunder"],
        a[href*="popads.net"],
        a[href*="exoclick.com"],
        a[href*="adsterra"] {
          display: none !important;
          visibility: hidden !important;
          pointer-events: none !important;
        }
      `;
      (document.head || document.documentElement).appendChild(style);
      purgeClickHijackOverlays();
      setInterval(purgeClickHijackOverlays, 1500);
    })
    .catch(() => {});
})();

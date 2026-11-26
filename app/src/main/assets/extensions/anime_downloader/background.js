// ZenithFox Browser v1.7.0 — Reliable Video-Only Stream Sniffer
// Detects real playing HLS (.m3u8, including proxy-wrapped URLs) & Direct MP4/WebM/MKV streams.
// Strictly excludes webpages (HTML), scripts, CSS, images, subtitles (.vtt/.srt), and ad bumpers.
(function () {
  "use strict";

  let nativePort = null;
  let currentPageTitle = "";
  const requestHeadersByUrl = new Map();
  const reportedUrls = new Set();
  const seenMasterPrefixes = new Set();

  // Match .m3u8 in path OR inside encoded proxy query params (e.g. ?url=https%3A%2F%2F...%2Fmaster.m3u8)
  const HLS_URL_RE = /(\.m3u8(\?|#|&|$)|%2em3u8|\/m3u8(\?|\/|$))/i;
  const DIRECT_VIDEO_RE = /\.(mp4|webm|mkv)(\?|#|&|$)/i;
  const IGNORED_EXT_RE = /\.(ts|m4s|key|vtt|srt|ass|ssa|html?|php|aspx?|jsp|json|xml|jpg|jpeg|png|webp|gif|svg|ico|css|js|mjs|woff2?|ttf|otf|mp3|aac|m4a|ogg|wav)(\?|#|$)/i;
  const IGNORED_PATH_RE = /(\/ads?\/|preroll|midroll|vast|vpaid|thumbnail|preview|sprite|storyboard|subtitles?\/|captions?\/|audio[_-]?only|audio[_-]?track)/i;
  const BLOCKED_HOSTS_RE = /(^|\.)(youtube\.com|youtu\.be|googlevideo\.com|doubleclick\.net|googlesyndication\.com|popads\.net|exoclick\.com|adsterra\.com|propellerads\.com)$/i;

  function isBlockedHost(url) {
    try {
      const u = new URL(url);
      return BLOCKED_HOSTS_RE.test(u.hostname);
    } catch (_) {
      return true;
    }
  }

  function extractHeader(headers, name) {
    if (!headers) return "";
    const lower = name.toLowerCase();
    for (let i = 0; i < headers.length; i++) {
      if (headers[i].name && headers[i].name.toLowerCase() === lower) {
        return headers[i].value || "";
      }
    }
    return "";
  }

  function inferQualityLabel(url) {
    const m = url.match(/(2160|1440|1080|720|480|360)p/i);
    if (m) return m[1] + "p";
    if (/master|manifest|playlist|index/i.test(url)) return "Multi-Quality";
    return "";
  }

  function getDirectoryPrefix(url) {
    try {
      const u = new URL(url);
      const parts = u.pathname.split("/").filter(Boolean);
      parts.pop();
      return u.origin + "/" + parts.slice(0, Math.max(1, parts.length - 1)).join("/");
    } catch (_) {
      return "";
    }
  }

  function emitVideoStream(stream) {
    if (!stream || !stream.url) return;
    if (!stream.url.startsWith("http")) return;
    if (isBlockedHost(stream.url) || IGNORED_PATH_RE.test(stream.url)) return;
    if (reportedUrls.has(stream.url)) return;

    if (stream.streamType === "HLS") {
      const prefix = getDirectoryPrefix(stream.url);
      const isMaster = /master|manifest/i.test(stream.url);
      if (!isMaster && prefix && seenMasterPrefixes.has(prefix)) {
        return;
      }
      if (isMaster && prefix) {
        seenMasterPrefixes.add(prefix);
      }
    }

    reportedUrls.add(stream.url);
    if (reportedUrls.size > 40) {
      const first = reportedUrls.values().next().value;
      if (first) reportedUrls.delete(first);
    }

    if (nativePort) {
      try {
        nativePort.postMessage({ type: "STREAM_DETECTED", data: stream });
      } catch (_) {}
    }
  }

  function broadcastResetToFrames() {
    if (browser.tabs && browser.tabs.query) {
      browser.tabs.query({}).then((tabs) => {
        for (const tab of tabs) {
          browser.tabs.sendMessage(tab.id, { type: "RESET_VIDEO_SCAN" }).catch(() => {});
        }
      }).catch(() => {});
    }
  }

  // 1. Capture exact outbound headers (Referer/Origin/UA/Cookie) for video requests
  if (browser.webRequest && browser.webRequest.onBeforeSendHeaders) {
    browser.webRequest.onBeforeSendHeaders.addListener(
      (details) => {
        const url = details.url || "";
        if (!url.startsWith("http") || IGNORED_EXT_RE.test(url) || isBlockedHost(url)) return;
        const mightBeVideo =
          details.type === "media" ||
          details.type === "xmlhttprequest" ||
          details.type === "other" ||
          HLS_URL_RE.test(url) ||
          DIRECT_VIDEO_RE.test(url);
        if (!mightBeVideo) return;

        const hdrs = details.requestHeaders || [];
        requestHeadersByUrl.set(url, {
          referer: extractHeader(hdrs, "referer") || details.documentUrl || details.originUrl || "",
          origin: extractHeader(hdrs, "origin") || "",
          userAgent: extractHeader(hdrs, "user-agent") || "",
          cookie: extractHeader(hdrs, "cookie") || ""
        });
        if (requestHeadersByUrl.size > 150) {
          const firstKey = requestHeadersByUrl.keys().next().value;
          if (firstKey) requestHeadersByUrl.delete(firstKey);
        }
      },
      { urls: ["<all_urls>"] },
      ["requestHeaders"]
    );
  }

  // 2. Verify response Content-Type & URL -> VIDEO STREAMS ONLY!
  if (browser.webRequest && browser.webRequest.onHeadersReceived) {
    browser.webRequest.onHeadersReceived.addListener(
      (details) => {
        const url = details.url || "";
        if (!url.startsWith("http") || IGNORED_EXT_RE.test(url) || IGNORED_PATH_RE.test(url) || isBlockedHost(url)) return;
        if (details.statusCode && (details.statusCode < 200 || (details.statusCode >= 300 && details.statusCode !== 206))) return;

        if (
          details.type === "main_frame" ||
          details.type === "sub_frame" ||
          details.type === "stylesheet" ||
          details.type === "script" ||
          details.type === "image"
        ) {
          return;
        }

        const respHeaders = details.responseHeaders || [];
        const contentType = extractHeader(respHeaders, "content-type").toLowerCase().split(";")[0].trim();

        // Strictly reject webpages, CSS, JS, JSON, images, and audio-only streams
        if (
          contentType.startsWith("text/html") ||
          contentType.startsWith("text/css") ||
          contentType.startsWith("application/javascript") ||
          contentType.startsWith("text/javascript") ||
          contentType.startsWith("application/json") ||
          contentType.startsWith("image/") ||
          contentType.startsWith("audio/")
        ) {
          return;
        }

        const contentRange = extractHeader(respHeaders, "content-range");
        const contentLength = parseInt(extractHeader(respHeaders, "content-length") || "0", 10) || 0;
        let totalSizeBytes = contentLength;
        if (contentRange) {
          const m = contentRange.match(/\/(\d+)$/);
          if (m) totalSizeBytes = parseInt(m[1], 10) || totalSizeBytes;
        }

        let streamType = null;
        const isHlsMime =
          contentType === "application/vnd.apple.mpegurl" ||
          contentType === "application/x-mpegurl" ||
          contentType === "application/mpegurl" ||
          contentType === "audio/mpegurl";

        if (HLS_URL_RE.test(url) || isHlsMime) {
          streamType = "HLS";
        } else if (
          contentType === "video/mp4" ||
          contentType === "video/webm" ||
          contentType === "video/x-matroska" ||
          contentType === "video/quicktime" ||
          (DIRECT_VIDEO_RE.test(url) &&
            (contentType === "" || contentType === "application/octet-stream" || contentType.startsWith("video/")))
        ) {
          // Ignore tiny non-range ad/preview clips under 400 KB, but keep HTTP 206 partial video streams!
          const isPartialRange = details.statusCode === 206 || !!contentRange;
          if (!isPartialRange && totalSizeBytes > 0 && totalSizeBytes < 400000) return;
          streamType = "DIRECT";
        }

        if (!streamType) return;

        const reqHdrs = requestHeadersByUrl.get(url) || {};
        emitVideoStream({
          url: url,
          title: currentPageTitle || "Video",
          streamType: streamType,
          mimeType: contentType || (streamType === "HLS" ? "application/vnd.apple.mpegurl" : "video/mp4"),
          sizeBytes: totalSizeBytes,
          qualityLabel: inferQualityLabel(url),
          referer: reqHdrs.referer || details.documentUrl || details.originUrl || "",
          origin: reqHdrs.origin || "",
          userAgent: reqHdrs.userAgent || "",
          cookie: reqHdrs.cookie || "",
          isDrmProtected: false
        });
      },
      { urls: ["<all_urls>"] },
      ["responseHeaders"]
    );
  }

  // 3. Receive episode title & playing <video> sources from content.js
  browser.runtime.onMessage.addListener((msg) => {
    if (!msg || !msg.type) return;
    if (msg.type === "PAGE_TITLE_UPDATE" && msg.title) {
      currentPageTitle = msg.title;
    } else if (msg.type === "DOM_VIDEO_FOUND" && msg.data) {
      const d = msg.data;
      const reqHdrs = requestHeadersByUrl.get(d.url) || {};
      emitVideoStream({
        url: d.url,
        title: currentPageTitle || d.title || "Video",
        streamType: d.streamType || "DIRECT",
        mimeType: d.mimeType || "video/mp4",
        sizeBytes: 0,
        qualityLabel: d.qualityLabel || inferQualityLabel(d.url),
        referer: reqHdrs.referer || d.referer || "",
        origin: reqHdrs.origin || d.origin || "",
        userAgent: reqHdrs.userAgent || navigator.userAgent || "",
        cookie: reqHdrs.cookie || "",
        isDrmProtected: false
      });
    }
  });

  function connectNative() {
    try {
      nativePort = browser.runtime.connectNative("animeDownloader");
      nativePort.onMessage.addListener((message) => {
        if (!message || !message.type) return;
        if (message.type === "CLEAR_STREAMS") {
          reportedUrls.clear();
          seenMasterPrefixes.clear();
          requestHeadersByUrl.clear();
          currentPageTitle = "";
          broadcastResetToFrames();
        }
      });
      nativePort.onDisconnect.addListener(() => {
        nativePort = null;
        setTimeout(connectNative, 1500);
      });
      nativePort.postMessage({ type: "DOWNLOADER_READY", id: "anime-downloader@animebrowser.app" });
    } catch (_) {
      setTimeout(connectNative, 2000);
    }
  }

  connectNative();
})();

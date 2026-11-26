// Background bridge connecting Anime Browser Kotlin native layer <-> ALL GeckoView frames (including video iframes)
// Fixes:
// 1. Single command delivery with monotonically increasing _msgId (eliminates double TOGGLE_PLAY pause-resume bug)
// 2. Multi-frame video telemetry arbitration (prevents empty top-level page from overwriting embedded iframe video state)
// 3. CORS header injection on non-DRM media streams for WebGL2 Anime4K shaders & optical-flow interpolation
(function () {
  "use strict";

  let nativePort = null;
  const framePorts = new Set();
  const frameVideoState = new Map(); // port -> { hasVideo: boolean, ts: number }
  let nextMsgId = 1;

  let lastEnhancementConfig = null;
  let lastScalingConfig = null;
  let lastForceFsConfig = null;
  let lastSentHasVideo = false;

  function broadcastToAllFrames(message) {
    const tagged = Object.assign({}, message, { _msgId: nextMsgId++ });
    let deliveredViaPort = 0;

    for (const port of framePorts) {
      try {
        port.postMessage(tagged);
        deliveredViaPort++;
      } catch (_) {
        framePorts.delete(port);
        frameVideoState.delete(port);
      }
    }

    // ONLY fall back to browser.tabs.sendMessage if NO persistent frame ports are connected
    // (Sending to BOTH caused every TOGGLE_PLAY command to execute twice!)
    if (deliveredViaPort === 0 && browser.tabs && browser.tabs.query) {
      browser.tabs.query({}).then((tabs) => {
        for (const tab of tabs) {
          browser.tabs.sendMessage(tab.id, tagged).catch(() => {});
        }
      }).catch(() => {});
    }
  }

  function anyOtherFrameHasRecentVideo(excludePort) {
    const now = Date.now();
    for (const [p, state] of frameVideoState.entries()) {
      if (p !== excludePort && state.hasVideo && (now - state.ts) < 3500) {
        return true;
      }
    }
    return false;
  }

  function forwardTelemetryFromFrame(port, msg) {
    if (!nativePort || !msg || !msg.type) return;

    if (msg.type === "VIDEO_TELEMETRY") {
      const hasVideo = !!(msg.data && msg.data.hasVideo);
      if (port) {
        frameVideoState.set(port, { hasVideo, ts: Date.now() });
      }
      // Critical anti-flicker guard: if an embedded iframe is actively playing a video,
      // ignore { hasVideo: false } heartbeats from the empty top-level wrapper page!
      if (!hasVideo && anyOtherFrameHasRecentVideo(port)) {
        return;
      }
      if (!hasVideo && !lastSentHasVideo) {
        return; // Don't spam redundant empty heartbeats
      }
      lastSentHasVideo = hasVideo;
    }

    try {
      nativePort.postMessage(msg);
    } catch (_) {}
  }

  // Receive persistent connections from EVERY frame (top page + embedded player iframes)
  browser.runtime.onConnect.addListener((port) => {
    if (port.name !== "anime-frame") return;
    framePorts.add(port);

    // Push current state immediately to newly connected video iframe
    if (lastEnhancementConfig) {
      try { port.postMessage({ type: "SET_ENHANCEMENT", payload: lastEnhancementConfig, _msgId: nextMsgId++ }); } catch (_) {}
    }
    if (lastScalingConfig) {
      try { port.postMessage({ type: "SET_SCALING", payload: lastScalingConfig, _msgId: nextMsgId++ }); } catch (_) {}
    }
    if (lastForceFsConfig) {
      try { port.postMessage({ type: "SET_FORCE_FULLSCREEN", payload: lastForceFsConfig, _msgId: nextMsgId++ }); } catch (_) {}
    }

    port.onMessage.addListener((msg) => {
      forwardTelemetryFromFrame(port, msg);
    });

    port.onDisconnect.addListener(() => {
      framePorts.delete(port);
      frameVideoState.delete(port);
      if (lastSentHasVideo && !anyOtherFrameHasRecentVideo(null) && nativePort) {
        lastSentHasVideo = false;
        try {
          nativePort.postMessage({ type: "VIDEO_TELEMETRY", data: { hasVideo: false, url: "" } });
        } catch (_) {}
      }
    });
  });

  // Fallback one-off messages from content scripts
  browser.runtime.onMessage.addListener((msg) => {
    forwardTelemetryFromFrame(null, msg);
  });

  // Unlock WebGL2 texImage2D on non-DRM media CDN streams by ensuring valid CORS headers
  const MEDIA_URL_RE = /\.(m3u8|mp4|webm|ts|m4s|mpd|mkv)(\?|$)/i;
  if (browser.webRequest && browser.webRequest.onHeadersReceived) {
    browser.webRequest.onHeadersReceived.addListener(
      (details) => {
        const isMedia = details.type === "media" ||
          MEDIA_URL_RE.test(details.url) ||
          (details.responseHeaders || []).some(
            (h) => h.name.toLowerCase() === "content-type" &&
              /^(video\/|application\/vnd\.apple\.mpegurl|application\/x-mpegurl)/i.test(h.value || "")
          );
        if (!isMedia) return {};

        let reqOrigin = "*";
        try {
          if (details.originUrl && details.originUrl.startsWith("http")) {
            reqOrigin = new URL(details.originUrl).origin;
          }
        } catch (_) {}

        const headers = (details.responseHeaders || []).filter(
          (h) => h.name.toLowerCase() !== "access-control-allow-origin" &&
            h.name.toLowerCase() !== "access-control-allow-credentials"
        );
        headers.push({ name: "Access-Control-Allow-Origin", value: reqOrigin });
        if (reqOrigin !== "*") {
          headers.push({ name: "Access-Control-Allow-Credentials", value: "true" });
          headers.push({ name: "Vary", value: "Origin" });
        }
        return { responseHeaders: headers };
      },
      { urls: ["<all_urls>"] },
      ["blocking", "responseHeaders"]
    );
  }

  function connectNative() {
    try {
      nativePort = browser.runtime.connectNative("animeBrowser");
      nativePort.onMessage.addListener((message) => {
        if (!message || !message.type) return;
        if (message.type === "SET_ENHANCEMENT") {
          lastEnhancementConfig = message.payload;
        } else if (message.type === "SET_SCALING") {
          lastScalingConfig = message.payload;
        } else if (message.type === "SET_FORCE_FULLSCREEN") {
          lastForceFsConfig = message.payload;
        }
        broadcastToAllFrames(message);
      });
      nativePort.onDisconnect.addListener(() => {
        nativePort = null;
        setTimeout(connectNative, 1200);
      });
      nativePort.postMessage({ type: "EXTENSION_READY", id: "anime-enhancer@animebrowser.app" });
    } catch (_) {
      setTimeout(connectNative, 1800);
    }
  }

  connectNative();
})();

// Content script for ZenithFox Video-Only Downloader:
// Captures clean page title and real loaded <video>/<source> streams (zero webpages/subtitles).
(function () {
  "use strict";

  const sent = new Set();

  function reportTitle() {
    if (window === window.top && document.title) {
      try {
        browser.runtime.sendMessage({
          type: "PAGE_TITLE_UPDATE",
          title: document.title.trim()
        }).catch(() => {});
      } catch (_) {}
    }
  }

  function scanPlayingVideos() {
    const videos = document.querySelectorAll("video");
    videos.forEach((v) => {
      if (v.videoWidth < 200 || v.videoHeight < 120) return;
      if (isFinite(v.duration) && v.duration > 0 && v.duration < 15) return; // Skip tiny ad clips

      const quality = v.videoHeight > 0 ? `${v.videoHeight}p` : "";
      const candidates = [];
      if (v.currentSrc && v.currentSrc.startsWith("http")) candidates.push(v.currentSrc);
      if (v.src && v.src.startsWith("http") && !candidates.includes(v.src)) candidates.push(v.src);
      const sources = v.querySelectorAll("source");
      sources.forEach((s) => {
        if (s.src && s.src.startsWith("http") && !candidates.includes(s.src)) {
          candidates.push(s.src);
        }
      });

      for (const url of candidates) {
        if (sent.has(url)) continue;
        sent.add(url);
        const isHls = /(\.m3u8(\?|#|&|$)|%2em3u8)/i.test(url);
        try {
          browser.runtime.sendMessage({
            type: "DOM_VIDEO_FOUND",
            data: {
              url: url,
              title: document.title || "",
              streamType: isHls ? "HLS" : "DIRECT",
              mimeType: isHls ? "application/vnd.apple.mpegurl" : "video/mp4",
              qualityLabel: quality,
              referer: location.href,
              origin: location.origin,
              isDrmProtected: false
            }
          }).catch(() => {});
        } catch (_) {}
      }
    });
  }

  browser.runtime.onMessage.addListener((msg) => {
    if (msg && msg.type === "RESET_VIDEO_SCAN") {
      sent.clear();
      setTimeout(scanPlayingVideos, 400);
    }
  });

  reportTitle();
  scanPlayingVideos();
  setInterval(() => {
    reportTitle();
    scanPlayingVideos();
  }, 1800);
})();

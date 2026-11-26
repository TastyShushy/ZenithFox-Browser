// Content script for Anime Video Downloader:
// Captures clean anime episode title and ONLY real loaded <video> streams (no webpages, no subtitles).
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
      // Only report actual loaded videos (ignore tiny UI animations or unloaded placeholders)
      if (v.videoWidth < 240 || v.videoHeight < 140) return;
      if (isFinite(v.duration) && v.duration > 0 && v.duration < 25) return; // Skip short ad clips < 25s

      const isDrm = !!v.mediaKeys;
      const quality = `${v.videoHeight}p`;
      const candidates = [];
      if (v.currentSrc && v.currentSrc.startsWith("http")) candidates.push(v.currentSrc);
      else if (v.src && v.src.startsWith("http")) candidates.push(v.src);

      for (const url of candidates) {
        if (sent.has(url)) continue;
        sent.add(url);
        const isHls = /\.m3u8(\?|#|$)/i.test(url);
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
              isDrmProtected: isDrm
            }
          }).catch(() => {});
        } catch (_) {}
      }
    });
  }

  reportTitle();
  scanPlayingVideos();
  setInterval(() => {
    reportTitle();
    scanPlayingVideos();
  }, 2000);
})();

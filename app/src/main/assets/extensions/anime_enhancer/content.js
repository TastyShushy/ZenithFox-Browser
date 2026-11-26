// ZenithFox Browser v1.7.0 — Enhancer, Real-Time FPS Telemetry & Blur-Free Video Scaling Content Script
// - Uses native object-fit: cover / fill with transform: none whenever <video> fills the viewport (ZERO YouTube blur!)
// - Real-Time Source FPS via MediaCodec getVideoPlaybackQuality().totalVideoFrames (500ms sliding window)
// - Clean-Sheet CPU-Verified 4K WebGL2 Anime4K + FidelityFX CAS + Optical-Flow Frame Generation
(function () {
  "use strict";

  // Inject page-level GeckoView WebGL HTMLVideoElement staging fix at document_start so
  // even websites' OWN built-in 4K WebGL video upscalers never turn pink or black!
  (function injectPageWebGlVideoFix() {
    try {
      const url = browser.runtime.getURL("webgl_video_fix.js");
      if (!url) return;
      const script = document.createElement("script");
      script.src = url;
      script.async = false;
      script.onload = () => { try { script.remove(); } catch (_) {} };
      script.onerror = () => { try { script.remove(); } catch (_) {} };
      const target = document.head || document.documentElement;
      if (target) target.appendChild(script);
    } catch (_) {}
  })();

  let activeVideo = null;
  let pipeline = null;
  let bgPort = null;
  let lastHandledMsgId = -1;

  let currentScalingMode = "FIT";
  let currentCustomZoom = 1.0;
  let isForceFullscreen = false;
  let hideSiteControls = false;
  let currentEnhancementConfig = {
    enabled: false,
    upscaleStrength: 0.55,
    sharpen: 0.45,
    deband: 0.35,
    denoise: 0.25,
    edgeEnhance: 0.5,
    vibrance: 0.22,
    resolutionScale: 1.0,
    frameGenEnabled: false,
    targetFps: 60,
    splitCompare: false,
    splitPosition: 0.5
  };

  let measuredSourceFps = 0;
  let lastSampleFrames = -1;
  let lastSampleTs = performance.now();
  let rvfcPresentedFrames = 0;
  let rvfcHandle = null;
  let lastVideoSrcKey = "";

  let scalingStyleTag = null;
  let fsStyleTag = null;
  let savedInlineStyles = null;
  let videoStyleObserver = null;
  let isApplyingScaling = false;

  function connectFramePort() {
    try {
      bgPort = browser.runtime.connect({ name: "anime-frame" });
      bgPort.onMessage.addListener((msg) => handleCommand(msg));
      bgPort.onDisconnect.addListener(() => {
        bgPort = null;
        setTimeout(connectFramePort, 1200);
      });
    } catch (_) {
      setTimeout(connectFramePort, 1500);
    }
  }

  function postToBackground(msg) {
    if (bgPort) {
      try {
        bgPort.postMessage(msg);
        return;
      } catch (_) {}
    }
    try {
      browser.runtime.sendMessage(msg).catch(() => {});
    } catch (_) {}
  }

  function findAllVideos(root) {
    const videos = [];
    if (!root) return videos;
    try {
      const direct = root.querySelectorAll("video");
      for (let i = 0; i < direct.length; i++) videos.push(direct[i]);
      const all = root.querySelectorAll("*");
      for (let i = 0; i < all.length; i++) {
        if (all[i].shadowRoot) {
          videos.push(...findAllVideos(all[i].shadowRoot));
        }
      }
    } catch (_) {}
    return videos;
  }

  function pickPrimaryVideo() {
    const videos = findAllVideos(document);
    if (videos.length === 0) return null;
    let best = null;
    let bestScore = -1;
    for (const v of videos) {
      const rect = v.getBoundingClientRect();
      const area = Math.max(0, rect.width) * Math.max(0, rect.height);
      const playingBonus = !v.paused && v.readyState >= 2 ? 2000000 : 0;
      const loadedBonus = v.videoWidth > 0 ? 500000 : 0;
      const score = area + playingBonus + loadedBonus + (v.videoWidth * v.videoHeight * 0.1);
      if (score > bestScore) {
        bestScore = score;
        best = v;
      }
    }
    return best;
  }

  function resetFpsBaseline() {
    measuredSourceFps = 0;
    lastSampleFrames = -1;
    lastSampleTs = performance.now();
  }

  function getDecodedFrameCount(video) {
    if (!video) return 0;
    if (typeof video.getVideoPlaybackQuality === "function") {
      try {
        const q = video.getVideoPlaybackQuality();
        if (q && typeof q.totalVideoFrames === "number" && q.totalVideoFrames > 0) {
          return q.totalVideoFrames;
        }
      } catch (_) {}
    }
    return rvfcPresentedFrames;
  }

  function sampleRealTimeSourceFps() {
    const v = activeVideo;
    if (!v || !v.isConnected || v.paused || v.ended || v.readyState < 2) {
      resetFpsBaseline();
      return;
    }

    const srcKey = `${v.currentSrc || ""}|${v.videoWidth}x${v.videoHeight}|${Math.round(v.duration || 0)}`;
    if (srcKey !== lastVideoSrcKey) {
      lastVideoSrcKey = srcKey;
      resetFpsBaseline();
    }

    const now = performance.now();
    const totalFrames = getDecodedFrameCount(v);
    if (lastSampleFrames < 0 || totalFrames < lastSampleFrames) {
      lastSampleFrames = totalFrames;
      lastSampleTs = now;
      return;
    }

    const dtSec = (now - lastSampleTs) / 1000;
    if (dtSec >= 0.40) {
      const deltaFrames = totalFrames - lastSampleFrames;
      const instantFps = deltaFrames / dtSec;
      if (instantFps >= 0 && instantFps <= 144) {
        measuredSourceFps = measuredSourceFps === 0
          ? Math.round(instantFps)
          : Math.round(measuredSourceFps * 0.35 + instantFps * 0.65);
      }
      lastSampleFrames = totalFrames;
      lastSampleTs = now;
    }
  }

  function startRvfcBackupCounter(video) {
    if (!video || typeof video.requestVideoFrameCallback !== "function") return;
    rvfcPresentedFrames = 0;
    const onFrame = (now, metadata) => {
      if (video !== activeVideo) return;
      if (metadata && typeof metadata.presentedFrames === "number") {
        rvfcPresentedFrames = metadata.presentedFrames;
      } else {
        rvfcPresentedFrames++;
      }
      rvfcHandle = video.requestVideoFrameCallback(onFrame);
    };
    rvfcHandle = video.requestVideoFrameCallback(onFrame);
  }

  function detachVideoStyleObserver() {
    if (videoStyleObserver) {
      try { videoStyleObserver.disconnect(); } catch (_) {}
      videoStyleObserver = null;
    }
  }

  function attachVideoStyleObserver(video) {
    detachVideoStyleObserver();
    if (!video) return;
    videoStyleObserver = new MutationObserver(() => {
      if (isApplyingScaling || video !== activeVideo) return;
      if (currentScalingMode !== "FIT") {
        applyScaling();
      } else if (pipeline && pipeline.active) {
        pipeline.syncGeometry();
      }
    });
    videoStyleObserver.observe(video, { attributes: true, attributeFilter: ["style", "class"] });
  }

  function bindVideo(video) {
    if (video === activeVideo) return;
    detachVideoStyleObserver();
    if (activeVideo) {
      if (rvfcHandle !== null && typeof activeVideo.cancelVideoFrameCallback === "function") {
        try { activeVideo.cancelVideoFrameCallback(rvfcHandle); } catch (_) {}
        rvfcHandle = null;
      }
      activeVideo.removeAttribute("data-ab-scaled");
    }
    if (pipeline) {
      pipeline.destroy();
      pipeline = null;
    }

    activeVideo = video;
    lastVideoSrcKey = "";
    resetFpsBaseline();

    if (video) {
      video.setAttribute("data-ab-scaled", "true");
      if (typeof window.AnimeWebGLPipeline === "function") {
        pipeline = new window.AnimeWebGLPipeline(video);
        pipeline.updateConfig(currentEnhancementConfig);
      }
      applyScaling();
      attachVideoStyleObserver(video);
      startRvfcBackupCounter(video);

      video.addEventListener("loadedmetadata", () => { resetFpsBaseline(); applyScaling(); sendTelemetry(); });
      video.addEventListener("loadeddata", () => { resetFpsBaseline(); applyScaling(); sendTelemetry(); });
      video.addEventListener("play", () => { resetFpsBaseline(); applyScaling(); sendTelemetry(); });
      video.addEventListener("pause", () => { resetFpsBaseline(); sendTelemetry(); });
      video.addEventListener("ended", () => { resetFpsBaseline(); sendTelemetry(); });
      video.addEventListener("waiting", () => { resetFpsBaseline(); });
      video.addEventListener("emptied", () => { resetFpsBaseline(); });
      video.addEventListener("resize", () => { applyScaling(); });
    }
  }

  function isEffectivelyFullscreen(video) {
    if (isForceFullscreen || document.fullscreenElement || document.mozFullScreenElement || document.webkitFullscreenElement) {
      return true;
    }
    if (!video) return false;
    const vRect = video.getBoundingClientRect();
    const fillsWindow = vRect.width >= window.innerWidth * 0.85 && vRect.height >= window.innerHeight * 0.70;
    const isLandscapeViewport = window.innerWidth > window.innerHeight * 1.35;
    return fillsWindow && (window !== window.top || isLandscapeViewport);
  }

  // Blur-Free Scaling Engine:
  // Uses native hardware object-fit: cover / fill with transform: none whenever <video> fills the
  // viewport (YouTube fullscreen, native HTML5 fullscreen) -> ZERO double-stretch, ZERO CSS zoom blur!
  function computeScalingGeometry(video, mode, customZoom) {
    const inFullscreen = isEffectivelyFullscreen(video);
    const vRect = video.getBoundingClientRect();
    // Does <video> already fill the full viewport width & height (e.g. YouTube fullscreen)?
    const videoFillsViewport =
      inFullscreen &&
      vRect.width >= window.innerWidth * 0.90 &&
      vRect.height >= window.innerHeight * 0.90;

    switch (mode) {
      case "ORIGINAL":
        return { objectFit: "none", transform: "none", expandToViewport: false, forceFullBox: false };
      case "FIT":
        return { objectFit: "contain", transform: "none", expandToViewport: false, forceFullBox: false };
      case "FILL":
        // Native hardware object-fit: cover with ZERO CSS transform zoom -> crystal clear!
        // If trapped inside a narrower 16:9 wrapper during fullscreen, cleanly expand the box to 100vw x 100vh.
        return {
          objectFit: "cover",
          transform: "none",
          expandToViewport: inFullscreen && !videoFillsViewport,
          forceFullBox: true
        };
      case "STRETCH":
        // Native hardware object-fit: fill with ZERO CSS transform scale -> eliminates black bars WITHOUT blurring YouTube!
        return {
          objectFit: "fill",
          transform: "none",
          expandToViewport: inFullscreen && !videoFillsViewport,
          forceFullBox: true
        };
      case "ZOOM":
        return { objectFit: "contain", transform: "scale(1.22)", expandToViewport: false, forceFullBox: true };
      case "CUSTOM":
        return {
          objectFit: "contain",
          transform: `scale(${Math.max(0.5, Math.min(3.0, customZoom)).toFixed(2)})`,
          expandToViewport: false,
          forceFullBox: true
        };
      default:
        return { objectFit: "contain", transform: "none", expandToViewport: false, forceFullBox: false };
    }
  }

  function applyScaling() {
    if (!activeVideo) return;
    isApplyingScaling = true;
    try {
      activeVideo.setAttribute("data-ab-scaled", "true");
      const geom = computeScalingGeometry(activeVideo, currentScalingMode, currentCustomZoom);

      if (!scalingStyleTag) {
        scalingStyleTag = document.createElement("style");
        scalingStyleTag.id = "anime-browser-scaling-style";
        (document.head || document.documentElement).appendChild(scalingStyleTag);
      }

      if (currentScalingMode === "FIT") {
        scalingStyleTag.textContent = `
          video[data-ab-scaled="true"] {
            object-fit: contain !important;
            transform: none !important;
          }
          canvas[data-anime-browser-canvas="true"] {
            object-fit: contain !important;
            transform: none !important;
          }
        `;
        activeVideo.style.removeProperty("object-fit");
        activeVideo.style.removeProperty("transform");
        activeVideo.style.removeProperty("transform-origin");
        if (pipeline) {
          pipeline.applyScalingStyles("contain", "none");
        }
        return;
      }

      const viewportExpandRules = geom.expandToViewport
        ? `position: fixed !important; inset: 0 !important; width: 100vw !important; height: 100vh !important; max-width: 100vw !important; max-height: 100vh !important; margin: 0 !important;`
        : (geom.forceFullBox
            ? `width: 100% !important; height: 100% !important; max-width: none !important; max-height: none !important; left: 0 !important; top: 0 !important;`
            : "");

      scalingStyleTag.textContent = `
        video[data-ab-scaled="true"] {
          object-fit: ${geom.objectFit} !important;
          transform: ${geom.transform} !important;
          transform-origin: center center !important;
          ${viewportExpandRules}
        }
        canvas[data-anime-browser-canvas="true"] {
          object-fit: ${geom.objectFit} !important;
          transform: ${geom.transform} !important;
          transform-origin: center center !important;
          ${viewportExpandRules}
        }
      `;

      activeVideo.style.setProperty("object-fit", geom.objectFit, "important");
      activeVideo.style.setProperty("transform", geom.transform, "important");
      activeVideo.style.setProperty("transform-origin", "center center", "important");

      if (pipeline) {
        pipeline.applyScalingStyles(geom.objectFit, geom.transform);
      }
    } finally {
      setTimeout(() => { isApplyingScaling = false; }, 0);
    }
  }

  function setForceFullscreen(enable, hideControls) {
    isForceFullscreen = !!enable;
    hideSiteControls = !!hideControls;
    if (!activeVideo) return;

    if (isForceFullscreen) {
      if (savedInlineStyles === null) {
        savedInlineStyles = activeVideo.getAttribute("style") || "";
      }
      if (!fsStyleTag) {
        fsStyleTag = document.createElement("style");
        fsStyleTag.id = "anime-browser-force-fs-style";
        (document.head || document.documentElement).appendChild(fsStyleTag);
      }
      fsStyleTag.textContent = `
        html, body { overflow: hidden !important; background: #000 !important; }
        video[data-ab-active="true"] {
          position: fixed !important;
          inset: 0 !important;
          width: 100vw !important;
          height: 100vh !important;
          max-width: 100vw !important;
          max-height: 100vh !important;
          z-index: 2147483644 !important;
          background: #000 !important;
          margin: 0 !important;
          padding: 0 !important;
        }
        canvas[data-anime-browser-canvas="true"] {
          position: fixed !important;
          inset: 0 !important;
          width: 100vw !important;
          height: 100vh !important;
          z-index: 2147483645 !important;
          background: transparent !important;
        }
      `;
      activeVideo.setAttribute("data-ab-active", "true");
      if (hideSiteControls) activeVideo.controls = false;
      applyScaling();
    } else {
      activeVideo.removeAttribute("data-ab-active");
      if (fsStyleTag) {
        fsStyleTag.remove();
        fsStyleTag = null;
      }
      if (savedInlineStyles !== null) {
        activeVideo.setAttribute("style", savedInlineStyles);
        savedInlineStyles = null;
      }
      applyScaling();
    }
  }

  function handleVideoControl(action, payload) {
    if (!activeVideo) return;
    switch (action) {
      case "PAUSE":
        try { activeVideo.pause(); } catch (_) {}
        break;
      case "TOGGLE_PLAY":
        if (activeVideo.paused) activeVideo.play().catch(() => {});
        else activeVideo.pause();
        break;
      case "SEEK_BY":
        activeVideo.currentTime = Math.max(0, Math.min(activeVideo.duration || 0, activeVideo.currentTime + (payload.seconds || 0)));
        break;
      case "SEEK_TO":
        activeVideo.currentTime = Math.max(0, Math.min(activeVideo.duration || 0, payload.seconds || 0));
        break;
      case "SET_SPEED":
        activeVideo.playbackRate = Math.max(0.25, Math.min(3.0, payload.speed || 1.0));
        break;
      case "TOGGLE_MUTE":
        activeVideo.muted = !activeVideo.muted;
        break;
      case "TOGGLE_SUBTITLES":
        if (activeVideo.textTracks && activeVideo.textTracks.length > 0) {
          const anyShowing = Array.from(activeVideo.textTracks).some(t => t.mode === "showing");
          for (let i = 0; i < activeVideo.textTracks.length; i++) {
            activeVideo.textTracks[i].mode = anyShowing ? "disabled" : (i === 0 ? "showing" : "disabled");
          }
        }
        break;
    }
  }

  function extractReaderModeContent() {
    const title = document.title || location.hostname;
    const paragraphs = Array.from(document.querySelectorAll("article p, main p, p"))
      .map(p => (p.innerText || "").trim())
      .filter(t => t.length > 35)
      .slice(0, 80);
    return {
      title,
      url: location.href,
      paragraphs: paragraphs.length > 0 ? paragraphs : [(document.body.innerText || "").slice(0, 4000)]
    };
  }

  function sendTelemetry() {
    const candidate = pickPrimaryVideo();
    if (candidate && candidate !== activeVideo) {
      bindVideo(candidate);
    } else if (!candidate && activeVideo && !activeVideo.isConnected) {
      bindVideo(null);
    }

    if (!activeVideo) {
      if (window === window.top) {
        postToBackground({
          type: "VIDEO_TELEMETRY",
          data: { hasVideo: false, url: location.href }
        });
      }
      return;
    }

    sampleRealTimeSourceFps();

    const v = activeVideo;
    const isPlaying = !v.paused && !v.ended && v.readyState >= 2;
    const hasSubtitles = v.textTracks && v.textTracks.length > 0;
    const subtitlesVisible = hasSubtitles && Array.from(v.textTracks).some(t => t.mode === "showing");
    const bufferedPct = (v.duration > 0 && v.buffered.length > 0)
      ? Math.round((v.buffered.end(v.buffered.length - 1) / v.duration) * 100)
      : 0;

    const frameGenActive = !!(
      pipeline &&
      pipeline.active &&
      pipeline.frameVerified &&
      pipeline.isWebGL2 &&
      currentEnhancementConfig.frameGenEnabled &&
      !pipeline.interpAutoDisabled
    );

    const liveSourceFps = isPlaying ? measuredSourceFps : 0;
    const liveOutputFps = isPlaying
      ? (pipeline && pipeline.active && pipeline.frameVerified && pipeline.outputFps > 0 ? pipeline.outputFps : liveSourceFps)
      : 0;

    postToBackground({
      type: "VIDEO_TELEMETRY",
      data: {
        hasVideo: true,
        url: location.href,
        videoWidth: v.videoWidth || 0,
        videoHeight: v.videoHeight || 0,
        isPlaying: isPlaying,
        currentTime: isFinite(v.currentTime) ? v.currentTime : 0,
        duration: isFinite(v.duration) ? v.duration : 0,
        playbackRate: v.playbackRate || 1.0,
        muted: !!v.muted,
        sourceFps: liveSourceFps,
        outputFps: liveOutputFps,
        enhancementStatus: pipeline ? pipeline.status : "IDLE",
        gpuRenderer: pipeline ? pipeline.gpuRenderer : "Unavailable",
        webGpuSupported: pipeline ? pipeline.webGpuSupported : false,
        isDrmProtected: false,
        hasSubtitles: !!hasSubtitles,
        subtitlesVisible: !!subtitlesVisible,
        bufferedPercent: bufferedPct,
        scalingMode: currentScalingMode,
        customZoom: currentCustomZoom,
        forceFullscreen: isForceFullscreen,
        frameGenActive: frameGenActive,
        frameGenAutoDisabled: !!(pipeline && pipeline.interpAutoDisabled)
      }
    });
  }

  function handleCommand(msg) {
    if (!msg || !msg.type) return;
    if (typeof msg._msgId === "number") {
      if (msg._msgId <= lastHandledMsgId) return;
      lastHandledMsgId = msg._msgId;
    }
    switch (msg.type) {
      case "SET_ENHANCEMENT":
        currentEnhancementConfig = Object.assign({}, currentEnhancementConfig, msg.payload || {});
        if (pipeline) pipeline.updateConfig(currentEnhancementConfig);
        sendTelemetry();
        break;
      case "SET_SCALING":
        currentScalingMode = (msg.payload && msg.payload.mode) || "FIT";
        currentCustomZoom = (msg.payload && msg.payload.customZoom) || 1.0;
        applyScaling();
        sendTelemetry();
        break;
      case "SET_FORCE_FULLSCREEN":
        setForceFullscreen(msg.payload && msg.payload.enabled, msg.payload && msg.payload.hideControls);
        sendTelemetry();
        break;
      case "VIDEO_CONTROL":
        handleVideoControl(msg.payload && msg.payload.action, msg.payload || {});
        sendTelemetry();
        break;
      case "REQUEST_READER_MODE":
        if (window === window.top) {
          postToBackground({ type: "READER_MODE_CONTENT", data: extractReaderModeContent() });
        }
        break;
      case "REQUEST_PAGE_HTML":
        if (window === window.top) {
          postToBackground({
            type: "PAGE_HTML_CONTENT",
            data: { title: document.title || "page", html: document.documentElement.outerHTML.slice(0, 500000) }
          });
        }
        break;
    }
  }

  browser.runtime.onMessage.addListener((msg) => handleCommand(msg));

  document.addEventListener("fullscreenchange", () => setTimeout(applyScaling, 120));
  document.addEventListener("mozfullscreenchange", () => setTimeout(applyScaling, 120));
  window.addEventListener("resize", () => applyScaling());
  window.addEventListener("orientationchange", () => setTimeout(applyScaling, 180));

  const observer = new MutationObserver(() => {
    if (activeVideo && activeVideo.isConnected && activeVideo.videoWidth > 0 && !activeVideo.paused) {
      return;
    }
    const candidate = pickPrimaryVideo();
    if (candidate && candidate !== activeVideo) {
      bindVideo(candidate);
      sendTelemetry();
    }
  });

  if (document.documentElement) {
    observer.observe(document.documentElement, { childList: true, subtree: true });
  } else {
    document.addEventListener("DOMContentLoaded", () => {
      if (document.documentElement) {
        observer.observe(document.documentElement, { childList: true, subtree: true });
      }
    });
  }

  connectFramePort();
  setInterval(sampleRealTimeSourceFps, 500);
  setInterval(sendTelemetry, 650);
})();

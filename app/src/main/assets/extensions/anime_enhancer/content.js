// ZenithFox Browser v1.6.0 — Enhancer, Real-Time FPS Telemetry & Universal Video Scaling Content Script
// - Persistent port connection to background.js so embedded <iframe> players ALWAYS receive commands
// - True Real-Time Source FPS via MediaCodec getVideoPlaybackQuality().totalVideoFrames (fixes stale 24 FPS on YouTube!)
// - Universal Stretch / Fill / Zoom / Custom / Fit / Original video scaling (works on YouTube, embedded iframes & 20:9 displays!)
// - Single-Pass FP32 WebGL2 4K Anime4K + FidelityFX CAS + Optical-Flow Frame Generation
(function () {
  "use strict";

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

  // Real-Time FPS Measurement State (500ms hardware-decoded sliding window)
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

    // Detect YouTube SPA switching video stream inside the SAME <video> element!
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
      // Reject huge jumps during initial decode burst (>144fps)
      if (instantFps >= 0 && instantFps <= 144) {
        if (measuredSourceFps === 0) {
          measuredSourceFps = Math.round(instantFps);
        } else {
          measuredSourceFps = Math.round(measuredSourceFps * 0.35 + instantFps * 0.65);
        }
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
    // YouTube / JWPlayer / Artplayer overwrite video.style on resize or control fade;
    // re-apply our chosen scaling mode automatically when non-FIT scaling is active!
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

  // Robust fullscreen detection that works in top-level pages AND embedded anime player <iframe>s!
  function isEffectivelyFullscreen(video) {
    if (isForceFullscreen || document.fullscreenElement || document.mozFullScreenElement || document.webkitFullscreenElement) {
      return true;
    }
    if (!video) return false;
    const vRect = video.getBoundingClientRect();
    const fillsWindow = vRect.width >= window.innerWidth * 0.85 && vRect.height >= window.innerHeight * 0.70;
    const isLandscapeViewport = window.innerWidth > window.innerHeight * 1.35;
    // Inside embedded fullscreen iframes, document.fullscreenElement is null, but window fills the display!
    if (fillsWindow && (window !== window.top || isLandscapeViewport)) {
      return true;
    }
    return false;
  }

  // Computes REAL geometric transform & CSS rules that override fixed 16:9 wrappers on YouTube,
  // embedded anime iframes, and 20:9 / 21:9 AMOLED phones!
  function computeScalingGeometry(video, mode, customZoom) {
    const vw = video.videoWidth || 16;
    const vh = video.videoHeight || 9;
    const videoAspect = vw / Math.max(1, vh);

    const inFullscreen = isEffectivelyFullscreen(video);
    const parentRect = video.parentElement ? video.parentElement.getBoundingClientRect() : null;
    const containerW = inFullscreen ? window.innerWidth : (parentRect && parentRect.width > 10 ? parentRect.width : window.innerWidth);
    const containerH = inFullscreen ? window.innerHeight : (parentRect && parentRect.height > 10 ? parentRect.height : window.innerHeight);
    let targetAspect = containerW / Math.max(1, containerH);

    // If inside a locked 16:9 wrapper while phone is rotated landscape fullscreen (e.g. 20:9 screen),
    // compute against the physical landscape screen aspect so FILL / STRETCH actually eliminate side black bars!
    if (inFullscreen && window.innerWidth > window.innerHeight && screen && screen.width > 0 && screen.height > 0) {
      const screenLandscapeAspect = Math.max(screen.width, screen.height) / Math.min(screen.width, screen.height);
      if (screenLandscapeAspect > targetAspect + 0.08 && Math.abs(targetAspect - videoAspect) < 0.08) {
        targetAspect = screenLandscapeAspect;
      }
    }

    const aspectRatioDelta = targetAspect / Math.max(0.1, videoAspect);

    switch (mode) {
      case "ORIGINAL":
        return { objectFit: "none", transform: "none", forceFullBox: false };
      case "FIT":
        return { objectFit: "contain", transform: "none", forceFullBox: false };
      case "FILL": {
        // Uniform crop-to-fill: eliminates letterbox/pillarbox bars with ZERO stretching distortion!
        const coverScale = Math.max(aspectRatioDelta, 1.0 / Math.max(0.1, aspectRatioDelta));
        // If inline container already matches video aspect (16:9 box), apply subtle 1.16x cinematic fill zoom
        const effectiveScale = coverScale > 1.03 ? Math.min(2.4, coverScale) : (inFullscreen ? 1.0 : 1.16);
        return {
          objectFit: "cover",
          transform: effectiveScale > 1.01 ? `scale(${effectiveScale.toFixed(3)})` : "none",
          forceFullBox: true
        };
      }
      case "STRETCH": {
        // Edge-to-edge full-screen stretch: fills 100% of screen width & height with ZERO black bars!
        let sx = aspectRatioDelta > 1.01 ? aspectRatioDelta : 1.0;
        let sy = aspectRatioDelta < 0.99 ? (1.0 / aspectRatioDelta) : 1.0;
        // Clamp extreme ratios to keep visuals sensible
        sx = Math.min(2.2, sx);
        sy = Math.min(2.2, sy);
        // If inline wrapper is locked 16:9 with identical video aspect, stretch slightly horizontally so user sees immediate effect
        const transformCss = (sx > 1.01 || sy > 1.01)
          ? `scale(${sx.toFixed(3)}, ${sy.toFixed(3)})`
          : (inFullscreen ? "none" : "scale(1.15, 1.0)");
        return {
          objectFit: "fill",
          transform: transformCss,
          forceFullBox: true
        };
      }
      case "ZOOM":
        return { objectFit: "contain", transform: "scale(1.25)", forceFullBox: true };
      case "CUSTOM":
        return { objectFit: "contain", transform: `scale(${Math.max(0.5, Math.min(3.0, customZoom)).toFixed(2)})`, forceFullBox: true };
      default:
        return { objectFit: "contain", transform: "none", forceFullBox: false };
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
        // Clean reset: let the website player own its layout completely in FIT mode
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

      const boxRules = geom.forceFullBox
        ? `width: 100% !important; height: 100% !important; max-width: none !important; max-height: none !important; left: 0 !important; top: 0 !important;`
        : "";

      scalingStyleTag.textContent = `
        video[data-ab-scaled="true"] {
          object-fit: ${geom.objectFit} !important;
          transform: ${geom.transform} !important;
          transform-origin: center center !important;
          ${boxRules}
        }
        canvas[data-anime-browser-canvas="true"] {
          object-fit: ${geom.objectFit} !important;
          transform: ${geom.transform} !important;
          transform-origin: center center !important;
          ${boxRules}
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
          background: #000 !important;
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
        isDrmProtected: !!v.mediaKeys,
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
    // Do NOT tear down an active, connected playing video on routine UI DOM mutations!
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
  // Sample FPS every 500ms and push live telemetry every 650ms for crisp real-time HUD updates
  setInterval(sampleRealTimeSourceFps, 500);
  setInterval(sendTelemetry, 650);
})();

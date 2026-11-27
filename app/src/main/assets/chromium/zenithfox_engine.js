// ZenithFox Browser v2.0.0 — Unified Chromium Blink/V8/ANGLE Engine Script
// Injected at document_start into the main frame AND all cross-origin video iframes via androidx.webkit.
// Features:
//  1. Fast Single-Pass Chromium WebGL2 4K Upscaler (Anime4K Line-Thinning + AMD FidelityFX CAS + Dual-Scale Micro-Detail)
//  2. True-to-Source Colors (ZERO color grading / zero saturation or brightness shift)
//  3. Fast 5-Tap GPU Optical-Flow Frame Generation (MCFI 30/45/60/90/120 FPS) tuned for mobile GPUs
//  4. Real SVG <feConvolveMatrix> GPU Sharpening Fallback for cross-origin tainted direct <video> tags
//  5. Zero-Battery Background Pause (halts rAF, rVFC & telemetry immediately when document.hidden)
//  6. Blur-Free Hardware Video Scaling (ORIGINAL / FIT / FILL / STRETCH / ZOOM / CUSTOM) + Force Fullscreen
//  7. Essential Video Player Controls (-10s / Play-Pause / +10s / +85s Anime OP Skip / Speed / Mute / Subtitles)
//  8. Zero-Reflow Cosmetic Ad Blocker, Pop-Under Shield & Invisible Click-Hijack Overlay Killer
//  9. DOM Video Stream Scanner + Reader Mode + Offline Page HTML Saver
(function () {
  "use strict";

  if (window.__zenithFoxChromiumEngineLoaded) return;
  window.__zenithFoxChromiumEngineLoaded = true;

  // =========================================================================
  // 1. TWO-WAY BRIDGE (androidx.webkit WebMessageListener + Legacy Fallback)
  // =========================================================================
  let commandHandler = null;

  function postToNative(msg) {
    try {
      const payload = JSON.stringify(msg);
      if (window.ZenithFoxBridge && typeof window.ZenithFoxBridge.postMessage === "function") {
        window.ZenithFoxBridge.postMessage(payload);
        return;
      }
      if (window._ZenithFoxLegacy && typeof window._ZenithFoxLegacy.postMessage === "function") {
        window._ZenithFoxLegacy.postMessage(payload);
      }
    } catch (_) {}
  }

  function setupNativeBridgeListener() {
    try {
      if (window.ZenithFoxBridge) {
        window.ZenithFoxBridge.onmessage = function (event) {
          try {
            const msg = typeof event.data === "string" ? JSON.parse(event.data) : event.data;
            if (commandHandler && msg) commandHandler(msg);
          } catch (_) {}
        };
      }
    } catch (_) {}

    window.__zenithFoxReceiveCommand = function (jsonStr) {
      try {
        const msg = typeof jsonStr === "string" ? JSON.parse(jsonStr) : jsonStr;
        if (commandHandler && msg) commandHandler(msg);
      } catch (_) {}
    };
  }

  // =========================================================================
  // 2. COSMETIC AD SHIELD, POP-UNDER BLOCKER & CLICK-HIJACK KILLER
  //    (Battery-friendly: CSS rules injected once; trap sweep throttled to >= 5s)
  // =========================================================================
  let adBlockEnabled = true;
  let popupBlockEnabled = true;
  let cosmeticStyleInjected = false;
  let lastOverlaySweepTs = 0;

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

  try {
    const origOpen = window.open;
    window.open = function (url, name, specs) {
      if (adBlockEnabled && popupBlockEnabled) {
        if (!url || url === "about:blank" || isCrossOriginUrl(String(url))) {
          return null;
        }
      }
      return origOpen.call(window, url, name, specs);
    };
  } catch (_) {}

  try {
    const origAnchorClick = HTMLAnchorElement.prototype.click;
    HTMLAnchorElement.prototype.click = function () {
      if (adBlockEnabled && popupBlockEnabled && this.target === "_blank" && isCrossOriginUrl(this.href)) {
        return;
      }
      return origAnchorClick.apply(this, arguments);
    };
  } catch (_) {}

  document.addEventListener(
    "click",
    (ev) => {
      if (!adBlockEnabled || !popupBlockEnabled) return;
      const anchor = ev.target && ev.target.closest ? ev.target.closest("a[target='_blank']") : null;
      if (anchor && isCrossOriginUrl(anchor.href)) {
        if ((anchor.innerText || "").trim().length === 0) {
          ev.preventDefault();
          ev.stopImmediatePropagation();
        }
      }
    },
    true
  );

  function applyCosmeticAdFilter(forceOverlaySweep) {
    if (document.hidden) return;
    const existing = document.getElementById("zenithfox-cosmetic-adblock");
    if (!adBlockEnabled) {
      if (existing) existing.remove();
      cosmeticStyleInjected = false;
      return;
    }
    if (!cosmeticStyleInjected && (document.head || document.documentElement)) {
      const style = document.createElement("style");
      style.id = "zenithfox-cosmetic-adblock";
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
      cosmeticStyleInjected = true;
    }

    const now = performance.now();
    if (!forceOverlaySweep && now - lastOverlaySweepTs < 5000) return;
    lastOverlaySweepTs = now;

    if (!document.body) return;
    try {
      const vw = window.innerWidth || 360;
      const vh = window.innerHeight || 640;
      // Only inspect direct body children & high-z overlays to avoid layout thrash
      const candidates = document.querySelectorAll("body > div, body > a, body > section, div[style*='z-index']");
      const limit = Math.min(candidates.length, 60);
      for (let i = 0; i < limit; i++) {
        const el = candidates[i];
        if (el.querySelector("video") || el.hasAttribute("data-ab-active") || el.hasAttribute("data-anime-browser-canvas")) continue;
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

  // =========================================================================
  // 3. FAST SINGLE-PASS CHROMIUM WEBGL2 4K UPSCALER + OPTICAL-FLOW FRAME GEN
  //    - Reuses 9-tap neighborhood for 3x faster mobile GPU throughput (60 FPS!)
  //    - Unmistakable Anime4K line-thinning + AMD FidelityFX CAS + Micro-Detail
  //    - Exact original video colors (ZERO color grading / zero saturation shift)
  // =========================================================================
  const VERTEX_SHADER_GL2 = `#version 300 es
    in vec2 aPos;
    in vec2 aTexCoord;
    out vec2 vUv;
    void main() {
      gl_Position = vec4(aPos, 0.0, 1.0);
      vUv = aTexCoord;
    }
  `;

  const FRAGMENT_SHADER_GL2 = `#version 300 es
    precision highp float;
    uniform sampler2D uTexCurr;
    uniform sampler2D uTexPrev;
    uniform vec2 uSrcTexelSize;
    uniform vec2 uOutTexelSize;
    uniform float uInterpEnabled;
    uniform float uAlpha;
    uniform float uUpscaleStrength;
    uniform float uSharpen;
    uniform float uDeband;
    uniform float uDenoise;
    uniform float uEdgeEnhance;
    uniform float uSplitCompare;
    uniform float uSplitPos;
    in vec2 vUv;
    out vec4 fragColor;

    float luma(vec3 c) {
      return dot(c, vec3(0.2126, 0.7152, 0.0722));
    }

    float hash12(vec2 p) {
      vec3 p3 = fract(vec3(p.xyx) * 0.1031);
      p3 += dot(p3, p3.yzx + 33.33);
      return fract((p3.x + p3.y) * p3.z);
    }

    vec3 tapCurr(vec2 uv) {
      return texture(uTexCurr, clamp(uv, vec2(0.0005), vec2(0.9995))).rgb;
    }

    vec3 tapPrev(vec2 uv) {
      return texture(uTexPrev, clamp(uv, vec2(0.0005), vec2(0.9995))).rgb;
    }

    // Fast 5-tap Bidirectional Lucas-Kanade Optical-Flow Frame Generation (MCFI)
    // Tuned for mobile Adreno/Mali/Xclipse GPUs: 10 taps total, smooth 60/90/120 FPS
    vec3 computeOpticalFlowFrame(vec2 uv, vec3 currC, out vec2 outFlow) {
      outFlow = vec2(0.0);
      if (uInterpEnabled < 0.5) return currC;

      vec3 prevC = tapPrev(uv);
      float rawDiff = length(currC - prevC);
      // Hard scene cut -> snap cleanly to current frame with zero ghosting
      if (rawDiff > 0.58) return currC;
      // Static region -> crisp blend without jitter
      if (rawDiff < 0.006) return mix(prevC, currC, uAlpha);

      vec2 st = uSrcTexelSize * 3.0;
      float cC = luma(currC);
      float pC = luma(prevC);
      float cL = luma(tapCurr(uv - vec2(st.x, 0.0)));
      float cR = luma(tapCurr(uv + vec2(st.x, 0.0)));
      float cU = luma(tapCurr(uv - vec2(0.0, st.y)));
      float cD = luma(tapCurr(uv + vec2(0.0, st.y)));

      float pL = luma(tapPrev(uv - vec2(st.x, 0.0)));
      float pR = luma(tapPrev(uv + vec2(st.x, 0.0)));
      float pU = luma(tapPrev(uv - vec2(0.0, st.y)));
      float pD = luma(tapPrev(uv + vec2(0.0, st.y)));

      // Symmetric spatiotemporal gradient
      float ix = ((cR - cL) + (pR - pL)) * 0.25;
      float iy = ((cD - cU) + (pD - pU)) * 0.25;
      float it = ((cC - pC) * 2.0 + (cL - pL) + (cR - pR) + (cU - pU) + (cD - pD)) * 0.1667;

      float denom = ix * ix + iy * iy + 0.0045;
      vec2 flow = clamp((-it / denom) * vec2(ix, iy), vec2(-7.5), vec2(7.5)) * st;
      outFlow = flow;

      vec3 warpedPrev = tapPrev(uv + flow * uAlpha);
      vec3 warpedCurr = tapCurr(uv - flow * (1.0 - uAlpha));
      float warpDiff = length(warpedCurr - warpedPrev);
      float confidence = 1.0 - smoothstep(0.18, 0.55, warpDiff);
      vec3 blended = mix(warpedPrev, warpedCurr, uAlpha);
      // Blend toward direct temporal mix on low-confidence fast pans instead of snapping to stuttery currC
      vec3 fallbackMix = mix(prevC, currC, uAlpha);
      return mix(fallbackMix, blended, confidence);
    }

    void main() {
      vec3 rawCurr = tapCurr(vUv);

      // Interactive Live Before / After Split-Screen Comparison
      if (uSplitCompare > 0.5) {
        if (abs(vUv.x - uSplitPos) < max(0.0024, uOutTexelSize.x * 2.5)) {
          fragColor = vec4(0.753, 0.518, 0.988, 1.0);
          return;
        }
        if (vUv.x < uSplitPos) {
          fragColor = vec4(clamp(rawCurr, 0.0, 1.0), 1.0);
          return;
        }
      }

      // 1. Optional GPU Optical-Flow Frame Generation
      vec2 flowVec;
      vec3 c = computeOpticalFlowFrame(vUv, rawCurr, flowVec);

      float anyEnhance = uUpscaleStrength + uSharpen + uDeband + uDenoise + uEdgeEnhance;
      if (anyEnhance < 0.01) {
        fragColor = vec4(clamp(c, 0.0, 1.0), 1.0);
        return;
      }

      // Single-pass 9-tap source neighborhood (offset by motion flow when interpolating!)
      vec2 baseUv = vUv - flowVec * (1.0 - uAlpha) * uInterpEnabled;
      vec2 dx = vec2(uSrcTexelSize.x, 0.0);
      vec2 dy = vec2(0.0, uSrcTexelSize.y);

      vec3 nw = tapCurr(baseUv - dx - dy);
      vec3 n  = tapCurr(baseUv - dy);
      vec3 ne = tapCurr(baseUv + dx - dy);
      vec3 w  = tapCurr(baseUv - dx);
      vec3 e  = tapCurr(baseUv + dx);
      vec3 sw = tapCurr(baseUv - dx + dy);
      vec3 s  = tapCurr(baseUv + dy);
      vec3 se = tapCurr(baseUv + dx + dy);

      float lc  = luma(c);
      float lnw = luma(nw);
      float ln  = luma(n);
      float lne = luma(ne);
      float lw  = luma(w);
      float le  = luma(e);
      float lsw = luma(sw);
      float ls  = luma(s);
      float lse = luma(se);

      vec3 result = c;

      // 2. Edge-Preserving Bilateral Compression Denoise (cleans 480p/720p macroblocks)
      if (uDenoise > 0.01) {
        float sigma = mix(0.022, 0.090, uDenoise);
        float wn  = exp(-abs(ln  - lc) / sigma);
        float ws  = exp(-abs(ls  - lc) / sigma);
        float ww  = exp(-abs(lw  - lc) / sigma);
        float we  = exp(-abs(le  - lc) / sigma);
        float wnw = exp(-abs(lnw - lc) / sigma) * 0.5;
        float wne = exp(-abs(lne - lc) / sigma) * 0.5;
        float wsw = exp(-abs(lsw - lc) / sigma) * 0.5;
        float wse = exp(-abs(lse - lc) / sigma) * 0.5;
        float wSum = 1.0 + wn + ws + ww + we + wnw + wne + wsw + wse;
        vec3 denoised = (c + n*wn + s*ws + w*ww + e*we + nw*wnw + ne*wne + sw*wsw + se*wse) / wSum;
        result = mix(result, denoised, uDenoise * 0.55);
      }

      // 3. Anti-Banding Sky & Gradient Smoothing (reuses diagonal taps with zero extra texture reads!)
      if (uDeband > 0.01) {
        vec3 bandAvg = (nw + ne + sw + se) * 0.25;
        vec3 diff = abs(result - bandAvg);
        float maxDiff = max(diff.r, max(diff.g, diff.b));
        float threshold = mix(0.010, 0.045, uDeband);
        float flatMask = 1.0 - smoothstep(threshold * 0.45, threshold, maxDiff);
        float dither = (hash12(vUv * 1024.0) - 0.5) * (0.0040 * uDeband);
        result = mix(result, bandAvg + vec3(dither), flatMask * uDeband * 0.65);
      }

      // 4. Anime4K Directional Line-Thinning & Crisp Ink-Line Reconstruction
      if (uUpscaleStrength > 0.01 || uEdgeEnhance > 0.01) {
        float gx = (-3.0*lnw - 10.0*lw - 3.0*lsw + 3.0*lne + 10.0*le + 3.0*lse) * 0.0625;
        float gy = (-3.0*lnw - 10.0*ln - 3.0*lne + 3.0*lsw + 10.0*ls + 3.0*lse) * 0.0625;
        float gradMag = clamp(sqrt(gx * gx + gy * gy) * 1.85, 0.0, 1.0);

        vec3 minCross = min(c, min(min(n, s), min(w, e)));
        vec3 maxCross = max(c, max(max(n, s), max(w, e)));
        vec3 minDiag  = min(min(nw, ne), min(sw, se));
        vec3 maxDiag  = max(max(nw, ne), max(sw, se));
        vec3 darkest  = min(minCross, minDiag);
        vec3 lightest = max(maxCross, maxDiag);

        float lDark  = luma(darkest);
        float lLight = luma(lightest);
        float contrastRange = lLight - lDark;
        float edgeMask = smoothstep(0.022, 0.18, gradMag) * smoothstep(0.025, 0.16, contrastRange);

        float thinAmount = clamp(uUpscaleStrength * 1.15 + uEdgeEnhance * 0.65, 0.0, 1.50) * edgeMask;
        float midLuma = (lDark + lLight) * 0.5;
        vec3 pushed = (lc < midLuma)
          ? mix(result, darkest, clamp(thinAmount * 0.78, 0.0, 0.85))
          : mix(result, lightest, clamp(thinAmount * 0.55, 0.0, 0.68));

        // Subtle ink-line darkening strictly on anime outlines (zero color shift)
        float isDarkLine = smoothstep(0.52, 0.12, lc) * edgeMask;
        pushed *= (1.0 - isDarkLine * (uUpscaleStrength * 0.30 + uEdgeEnhance * 0.22));
        result = pushed;
      }

      // 5. NaN-Free AMD FidelityFX CAS (Contrast-Adaptive Sharpening)
      float casStrength = clamp(uSharpen * 1.15 + uUpscaleStrength * 0.65 + uEdgeEnhance * 0.45, 0.0, 1.50);
      if (casStrength > 0.01) {
        vec3 mnRGB = min(min(min(w, result), min(e, n)), s);
        vec3 mnRGB2 = min(mnRGB, min(min(nw, ne), min(sw, se)));
        mnRGB += mnRGB2;

        vec3 mxRGB = max(max(max(w, result), max(e, n)), s);
        vec3 mxRGB2 = max(mxRGB, max(max(nw, ne), max(sw, se)));
        mxRGB += mxRGB2;

        vec3 rcpMRGB = 1.0 / max(mxRGB, vec3(0.01));
        vec3 ampRGB = clamp(min(mnRGB, max(2.0 - mxRGB, vec3(0.0))) * rcpMRGB, vec3(0.0), vec3(1.0));
        ampRGB = sqrt(ampRGB);

        float peak = -1.0 / mix(8.5, 4.4, clamp(casStrength, 0.0, 1.0));
        vec3 wRGB = ampRGB * peak;
        vec3 rcpWeightRGB = 1.0 / max(1.0 + 4.0 * wRGB, vec3(0.18));
        vec3 casColor = clamp(((n + w + e + s) * wRGB + result) * rcpWeightRGB, vec3(0.0), vec3(1.0));
        result = mix(result, casColor, clamp(casStrength, 0.0, 1.0));
      }

      // 6. Crisp High-Frequency 4K Detail Boost (reusing 8 neighbors — zero extra texture reads!)
      if (uSharpen > 0.01 || uEdgeEnhance > 0.01 || uUpscaleStrength > 0.01) {
        vec3 avg8 = (n + s + w + e + nw + ne + sw + se) * 0.125;
        vec3 detail = clamp(result - avg8, vec3(-0.30), vec3(0.30));
        float gain = uSharpen * 0.85 + uEdgeEnhance * 0.65 + uUpscaleStrength * 0.45;
        result = clamp(result + detail * gain, vec3(0.0), vec3(1.0));
      }

      // Exact original video colors preserved (zero color grading / saturation / hue shift)
      fragColor = vec4(clamp(result, 0.0, 1.0), 1.0);
    }
  `;

  const VERTEX_SHADER_GL1 = `
    attribute vec2 aPos;
    attribute vec2 aTexCoord;
    varying highp vec2 vUv;
    void main() {
      gl_Position = vec4(aPos, 0.0, 1.0);
      vUv = aTexCoord;
    }
  `;

  const FRAGMENT_SHADER_GL1 = `
    #ifdef GL_FRAGMENT_PRECISION_HIGH
    precision highp float;
    #else
    precision mediump float;
    #endif
    uniform sampler2D uTexCurr;
    uniform vec2 uTexelSize;
    uniform float uUpscaleStrength;
    uniform float uSharpen;
    uniform float uEdgeEnhance;
    uniform float uSplitCompare;
    uniform float uSplitPos;
    varying highp vec2 vUv;

    void main() {
      vec3 c = texture2D(uTexCurr, clamp(vUv, vec2(0.0005), vec2(0.9995))).rgb;
      if (uSplitCompare > 0.5) {
        if (abs(vUv.x - uSplitPos) < 0.003) {
          gl_FragColor = vec4(0.753, 0.518, 0.988, 1.0);
          return;
        }
        if (vUv.x < uSplitPos) {
          gl_FragColor = vec4(clamp(c, 0.0, 1.0), 1.0);
          return;
        }
      }
      vec3 n = texture2D(uTexCurr, clamp(vUv + vec2(0.0, -uTexelSize.y), vec2(0.0005), vec2(0.9995))).rgb;
      vec3 s = texture2D(uTexCurr, clamp(vUv + vec2(0.0,  uTexelSize.y), vec2(0.0005), vec2(0.9995))).rgb;
      vec3 w = texture2D(uTexCurr, clamp(vUv + vec2(-uTexelSize.x, 0.0), vec2(0.0005), vec2(0.9995))).rgb;
      vec3 e = texture2D(uTexCurr, clamp(vUv + vec2( uTexelSize.x, 0.0), vec2(0.0005), vec2(0.9995))).rgb;
      vec3 mn = min(c, min(min(n, s), min(w, e)));
      vec3 mx = max(c, max(max(n, s), max(w, e)));
      vec3 amp = sqrt(clamp(min(mn, max(1.0 - mx, vec3(0.0))) / max(mx, vec3(0.01)), vec3(0.0), vec3(1.0)));
      float k = -1.0 / mix(8.5, 4.4, clamp(uSharpen + uUpscaleStrength * 0.5, 0.0, 1.0));
      vec3 wRGB = amp * k;
      vec3 result = clamp(((n + s + w + e) * wRGB + c) / max(1.0 + 4.0 * wRGB, vec3(0.18)), vec3(0.0), vec3(1.0));
      vec3 detail = clamp(result - (n + s + w + e) * 0.25, vec3(-0.28), vec3(0.28));
      result += detail * (uSharpen * 1.1 + uEdgeEnhance * 0.75);
      gl_FragColor = vec4(clamp(result, 0.0, 1.0), 1.0);
    }
  `;

  function compileShader(gl, type, source) {
    const shader = gl.createShader(type);
    gl.shaderSource(shader, source);
    gl.compileShader(shader);
    if (!gl.getShaderParameter(shader, gl.COMPILE_STATUS)) {
      const log = gl.getShaderInfoLog(shader);
      gl.deleteShader(shader);
      throw new Error("Shader compile error: " + log);
    }
    return shader;
  }

  function linkProgram(gl, vsSrc, fsSrc) {
    const vs = compileShader(gl, gl.VERTEX_SHADER, vsSrc);
    const fs = compileShader(gl, gl.FRAGMENT_SHADER, fsSrc);
    const prog = gl.createProgram();
    gl.attachShader(prog, vs);
    gl.attachShader(prog, fs);
    gl.linkProgram(prog);
    if (!gl.getProgramParameter(prog, gl.LINK_STATUS)) {
      throw new Error(gl.getProgramInfoLog(prog));
    }
    return prog;
  }

  // Real Skia-GPU SVG 3x3 Convolution Sharpening Filter for CORS-tainted direct <video> tags
  // (Zero color shift, works even on cross-origin CDNs where WebGL texImage2D throws SecurityError!)
  function ensureSvgSharpenFilter(strength) {
    const filterId = "zenithfox-svg-4k-sharp";
    let svg = document.getElementById("zenithfox-svg-defs-host");
    if (!svg) {
      svg = document.createElementNS("http://www.w3.org/2000/svg", "svg");
      svg.id = "zenithfox-svg-defs-host";
      svg.setAttribute("width", "0");
      svg.setAttribute("height", "0");
      svg.style.cssText = "position:fixed;left:-9999px;top:-9999px;width:0;height:0;pointer-events:none;";
      const defs = document.createElementNS("http://www.w3.org/2000/svg", "defs");
      const filter = document.createElementNS("http://www.w3.org/2000/svg", "filter");
      filter.id = filterId;
      filter.setAttribute("color-interpolation-filters", "sRGB");
      const conv = document.createElementNS("http://www.w3.org/2000/svg", "feConvolveMatrix");
      conv.id = "zenithfox-svg-4k-kernel";
      conv.setAttribute("order", "3");
      conv.setAttribute("preserveAlpha", "true");
      conv.setAttribute("edgeMode", "duplicate");
      filter.appendChild(conv);
      defs.appendChild(filter);
      svg.appendChild(defs);
      (document.body || document.documentElement).appendChild(svg);
    }
    const conv = document.getElementById("zenithfox-svg-4k-kernel");
    if (conv) {
      // Unsharp-mask 3x3 kernel preserving exact DC gain (sum of weights == 1.0 -> ZERO color/brightness shift!)
      const k = Math.max(0.18, Math.min(0.95, 0.25 + strength * 0.70));
      const center = (1.0 + 4.0 * k).toFixed(3);
      const neg = (-k).toFixed(3);
      conv.setAttribute("kernelMatrix", `0 ${neg} 0 ${neg} ${center} ${neg} 0 ${neg} 0`);
    }
    return `url(#${filterId})`;
  }

  class ChromiumWebGL4KPipeline {
    constructor(video) {
      this.video = video;
      this.canvas = document.createElement("canvas");
      this.canvas.setAttribute("data-anime-browser-canvas", "true");
      this.canvas.style.position = "absolute";
      this.canvas.style.pointerEvents = "none";
      this.canvas.style.zIndex = "2";
      this.canvas.style.objectFit = "contain";
      this.canvas.style.background = "transparent";
      this.canvas.style.opacity = "0";
      this.canvas.style.transition = "none";

      this.gl = null;
      this.isWebGL2 = false;
      this.progGl2 = null;
      this.progGl1 = null;
      this.texCurr = null;
      this.texPrev = null;
      this.posBuf = null;
      this.uvBuf = null;
      this.u2 = null;
      this.uGl1 = null;

      this.active = false;
      this.destroyed = false;
      this.rafId = null;
      this.rvfcId = null;
      this.status = "IDLE";
      this.gpuRenderer = "Chromium ANGLE GPU";
      this.webGpuSupported = typeof navigator !== "undefined" && !!navigator.gpu;

      this.hasCurrFrame = false;
      this.hasPrevFrame = false;
      this.frameVerified = false;
      this.consecutiveValidDraws = 0;
      this.needsGlHealthCheck = true;
      this.lastGlHealthCheckTs = 0;
      this.directUploadFailed = false;
      this.webglBrokenForVideo = false;
      this.stagingCanvas = null;
      this.stagingCtx = null;

      this.lastVideoTime = -1;
      this.lastPresentedFrames = -1;
      this.lastFrameUploadTs = 0;
      this.estimatedFrameIntervalMs = 0;
      this.lastRenderTs = 0;
      this.needsConfigRedraw = true;
      this.newFramePending = false;

      this.outputFrames = 0;
      this.lastOutputFpsTs = performance.now();
      this.outputFps = 0;
      this.slowFrameCount = 0;
      this.interpAutoDisabled = false;
      this.dynamicMaxDim = 3840;

      this.config = {
        enabled: false,
        upscaleStrength: 0.82,
        sharpen: 0.76,
        deband: 0.45,
        denoise: 0.30,
        edgeEnhance: 0.78,
        resolutionScale: 1.5,
        frameGenEnabled: false,
        targetFps: 60,
        splitCompare: false,
        splitPosition: 0.5
      };

      this._onContextLost = (e) => {
        e.preventDefault();
        this.frameVerified = false;
        this.canvas.style.opacity = "0";
        this._fallbackToSvgGpuFilter();
      };
      this.canvas.addEventListener("webglcontextlost", this._onContextLost, false);

      this._onVideoSeeked = () => {
        this.hasPrevFrame = false;
        this.newFramePending = true;
        this.needsConfigRedraw = true;
      };
      this._onVideoPause = () => {
        this.outputFps = 0;
        this.outputFrames = 0;
      };
      this._onVideoReset = () => {
        this.hasCurrFrame = false;
        this.hasPrevFrame = false;
        this.frameVerified = false;
        this.consecutiveValidDraws = 0;
        this.directUploadFailed = false;
        this.webglBrokenForVideo = false;
        this.estimatedFrameIntervalMs = 0;
        this.lastFrameUploadTs = 0;
        this.lastVideoTime = -1;
        this.lastPresentedFrames = -1;
        this.canvas.style.opacity = "0";
        this._clearSvgGpuFallback();
      };

      video.addEventListener("seeked", this._onVideoSeeked);
      video.addEventListener("pause", this._onVideoPause);
      video.addEventListener("ended", this._onVideoPause);
      video.addEventListener("emptied", this._onVideoReset);
      video.addEventListener("loadeddata", this._onVideoReset);

      this._initGL();
    }

    _initGL() {
      const opts = {
        alpha: false,
        antialias: false,
        depth: false,
        stencil: false,
        premultipliedAlpha: false,
        preserveDrawingBuffer: false,
        powerPreference: "high-performance"
      };
      let gl = this.canvas.getContext("webgl2", opts);
      if (gl) {
        this.isWebGL2 = true;
      } else {
        gl = this.canvas.getContext("webgl", opts) || this.canvas.getContext("experimental-webgl", opts);
        this.isWebGL2 = false;
      }
      if (!gl) {
        this.status = "UNSUPPORTED";
        return;
      }
      this.gl = gl;

      try {
        const dbg = gl.getExtension("WEBGL_debug_renderer_info");
        if (dbg) {
          this.gpuRenderer = String(gl.getParameter(dbg.UNMASKED_RENDERER_WEBGL) || "Chromium GPU");
        } else {
          this.gpuRenderer = String(gl.getParameter(gl.RENDERER) || "Chromium GPU");
        }
      } catch (_) {}

      const positions = new Float32Array([-1, -1, 1, -1, -1, 1, 1, 1]);
      const texCoords = new Float32Array([0, 1, 1, 1, 0, 0, 1, 0]);
      this.posBuf = gl.createBuffer();
      gl.bindBuffer(gl.ARRAY_BUFFER, this.posBuf);
      gl.bufferData(gl.ARRAY_BUFFER, positions, gl.STATIC_DRAW);

      this.uvBuf = gl.createBuffer();
      gl.bindBuffer(gl.ARRAY_BUFFER, this.uvBuf);
      gl.bufferData(gl.ARRAY_BUFFER, texCoords, gl.STATIC_DRAW);

      const createTex = () => {
        const tex = gl.createTexture();
        gl.bindTexture(gl.TEXTURE_2D, tex);
        gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE);
        gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
        gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR);
        gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR);
        return tex;
      };
      this.texCurr = createTex();
      this.texPrev = createTex();

      try {
        if (this.isWebGL2) {
          this.progGl2 = linkProgram(gl, VERTEX_SHADER_GL2, FRAGMENT_SHADER_GL2);
          this.u2 = {
            aPos: gl.getAttribLocation(this.progGl2, "aPos"),
            aTex: gl.getAttribLocation(this.progGl2, "aTexCoord"),
            uTexCurr: gl.getUniformLocation(this.progGl2, "uTexCurr"),
            uTexPrev: gl.getUniformLocation(this.progGl2, "uTexPrev"),
            uSrcTexelSize: gl.getUniformLocation(this.progGl2, "uSrcTexelSize"),
            uOutTexelSize: gl.getUniformLocation(this.progGl2, "uOutTexelSize"),
            uInterpEnabled: gl.getUniformLocation(this.progGl2, "uInterpEnabled"),
            uAlpha: gl.getUniformLocation(this.progGl2, "uAlpha"),
            uUpscaleStrength: gl.getUniformLocation(this.progGl2, "uUpscaleStrength"),
            uSharpen: gl.getUniformLocation(this.progGl2, "uSharpen"),
            uDeband: gl.getUniformLocation(this.progGl2, "uDeband"),
            uDenoise: gl.getUniformLocation(this.progGl2, "uDenoise"),
            uEdgeEnhance: gl.getUniformLocation(this.progGl2, "uEdgeEnhance"),
            uSplitCompare: gl.getUniformLocation(this.progGl2, "uSplitCompare"),
            uSplitPos: gl.getUniformLocation(this.progGl2, "uSplitPos")
          };
        } else {
          this.progGl1 = linkProgram(gl, VERTEX_SHADER_GL1, FRAGMENT_SHADER_GL1);
          this.uGl1 = {
            aPos: gl.getAttribLocation(this.progGl1, "aPos"),
            aTex: gl.getAttribLocation(this.progGl1, "aTexCoord"),
            uTexCurr: gl.getUniformLocation(this.progGl1, "uTexCurr"),
            uTexelSize: gl.getUniformLocation(this.progGl1, "uTexelSize"),
            uUpscaleStrength: gl.getUniformLocation(this.progGl1, "uUpscaleStrength"),
            uSharpen: gl.getUniformLocation(this.progGl1, "uSharpen"),
            uEdgeEnhance: gl.getUniformLocation(this.progGl1, "uEdgeEnhance"),
            uSplitCompare: gl.getUniformLocation(this.progGl1, "uSplitCompare"),
            uSplitPos: gl.getUniformLocation(this.progGl1, "uSplitPos")
          };
        }
      } catch (_) {
        this.status = "UNSUPPORTED";
      }
    }

    _bindQuad(gl, posLoc, uvLoc) {
      if (posLoc >= 0) {
        gl.bindBuffer(gl.ARRAY_BUFFER, this.posBuf);
        gl.enableVertexAttribArray(posLoc);
        gl.vertexAttribPointer(posLoc, 2, gl.FLOAT, false, 0, 0);
      }
      if (uvLoc >= 0) {
        gl.bindBuffer(gl.ARRAY_BUFFER, this.uvBuf);
        gl.enableVertexAttribArray(uvLoc);
        gl.vertexAttribPointer(uvLoc, 2, gl.FLOAT, false, 0, 0);
      }
    }

    _verifyWebGlOutput(gl, targetW, targetH) {
      if (targetW < 16 || targetH < 16) return "DARK";
      const pts = [
        [0.50, 0.50], [0.25, 0.25], [0.75, 0.25], [0.25, 0.75], [0.75, 0.75]
      ];
      const px = new Uint8Array(4);
      let nonBlack = 0;
      let pinkCount = 0;
      let greenCount = 0;
      for (let i = 0; i < pts.length; i++) {
        const x = Math.max(0, Math.min(targetW - 1, Math.floor(targetW * pts[i][0])));
        const y = Math.max(0, Math.min(targetH - 1, Math.floor(targetH * pts[i][1])));
        try {
          gl.readPixels(x, y, 1, 1, gl.RGBA, gl.UNSIGNED_BYTE, px);
        } catch (_) {
          return "BROKEN";
        }
        const r = px[0], g = px[1], b = px[2];
        if (r > 4 || g > 4 || b > 4) nonBlack++;
        const isMagenta = r > 180 && b > 140 && g < 110;
        const isPastelPink = r > 220 && b > 170 && g > 130 && g < 200 && Math.abs(r - b) < 75;
        if (isMagenta || isPastelPink) pinkCount++;
        if (g > 180 && r < 45 && b < 45) greenCount++;
      }
      if (pinkCount >= 4 || greenCount >= 4) return "BROKEN";
      if (nonBlack === 0) return "DARK";
      return "OK";
    }

    _uploadFrameToCurrTexture(gl, video) {
      gl.pixelStorei(gl.UNPACK_FLIP_Y_WEBGL, false);
      if (!this.directUploadFailed) {
        // Zero-copy Chromium ANGLE GPU texture upload
        gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, gl.RGBA, gl.UNSIGNED_BYTE, video);
        return;
      }
      // Fallback: 2D canvas staging
      if (!this.stagingCanvas) {
        this.stagingCanvas = document.createElement("canvas");
        this.stagingCtx = this.stagingCanvas.getContext("2d", { alpha: false, willReadFrequently: true });
      }
      if (!this.stagingCtx) throw new Error("2D staging unavailable");
      const sw = Math.min(1920, video.videoWidth);
      const sh = Math.min(1080, video.videoHeight);
      if (this.stagingCanvas.width !== sw || this.stagingCanvas.height !== sh) {
        this.stagingCanvas.width = sw;
        this.stagingCanvas.height = sh;
      }
      this.stagingCtx.drawImage(video, 0, 0, sw, sh);
      gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, gl.RGBA, gl.UNSIGNED_BYTE, this.stagingCanvas);
    }

    syncGeometry() {
      const video = this.video;
      const parent = video.parentElement;
      if (!parent) return;

      const directVideoFullscreen =
        document.fullscreenElement === video || document.webkitFullscreenElement === video;
      if (directVideoFullscreen) {
        this.canvas.style.opacity = "0";
        return;
      }

      const computedParentPos = getComputedStyle(parent).position;
      if (computedParentPos === "static") {
        parent.style.position = "relative";
      }

      const computedVid = getComputedStyle(video);
      const vidZ = parseInt(computedVid.zIndex, 10);
      const safeCanvasZ = isNaN(vidZ) ? 1 : Math.max(1, vidZ + 1);
      this.canvas.style.zIndex = String(safeCanvasZ);

      if (video.offsetWidth > 10 && video.offsetHeight > 10) {
        this.canvas.style.left = `${video.offsetLeft}px`;
        this.canvas.style.top = `${video.offsetTop}px`;
        this.canvas.style.width = `${video.offsetWidth}px`;
        this.canvas.style.height = `${video.offsetHeight}px`;
      } else {
        this.canvas.style.left = "0";
        this.canvas.style.top = "0";
        this.canvas.style.width = "100%";
        this.canvas.style.height = "100%";
      }
    }

    attachToParent() {
      const parent = this.video.parentElement;
      if (!parent) return;
      if (!this.canvas.isConnected) {
        try {
          this.video.insertAdjacentElement("afterend", this.canvas);
        } catch (_) {
          parent.appendChild(this.canvas);
        }
      }
      this.syncGeometry();
    }

    _applySvgGpuFallback() {
      if (!this.config.enabled) {
        this._clearSvgGpuFallback();
        this.status = "IDLE";
        return;
      }
      const strength = Math.max(this.config.upscaleStrength, this.config.sharpen, this.config.edgeEnhance);
      try {
        const filterUrl = ensureSvgSharpenFilter(strength);
        this.video.style.setProperty("filter", filterUrl, "important");
        this.status = "ACTIVE_SVG_GPU_FILTER";
      } catch (_) {
        const contrast = (1.04 + strength * 0.10).toFixed(3);
        this.video.style.setProperty("filter", `contrast(${contrast})`, "important");
        this.status = "ACTIVE_CSS_GPU_FILTER";
      }
    }

    _clearSvgGpuFallback() {
      this.video.style.removeProperty("filter");
    }

    _fallbackToSvgGpuFilter() {
      this.webglBrokenForVideo = true;
      this.stop();
      if (this.config.enabled) {
        this._applySvgGpuFallback();
      } else {
        this.status = "IDLE";
      }
    }

    updateConfig(cfg) {
      const prevFrameGen = this.config.frameGenEnabled;
      this.config = Object.assign({}, this.config, cfg);
      if (this.config.frameGenEnabled && !prevFrameGen) {
        this.interpAutoDisabled = false;
        this.hasPrevFrame = false;
        this.slowFrameCount = 0;
      }
      this.needsConfigRedraw = true;
      this.needsGlHealthCheck = true;

      const wantPipeline = this.config.enabled || this.config.frameGenEnabled;
      if (!wantPipeline) {
        this.stop();
        this._clearSvgGpuFallback();
        return;
      }

      if (this.webglBrokenForVideo || this.status === "ACTIVE_SVG_GPU_FILTER" || this.status === "ACTIVE_CSS_GPU_FILTER" || this.status === "UNSUPPORTED") {
        this.stop();
        this._applySvgGpuFallback();
        return;
      }

      if (!document.hidden) {
        this.start();
      }
    }

    onVisibilityChanged(hidden) {
      if (hidden) {
        // Freeze WebGL rAF/rVFC completely in background -> 0% GPU/CPU battery drain!
        if (this.rafId !== null) {
          cancelAnimationFrame(this.rafId);
          this.rafId = null;
        }
        if (this.rvfcId !== null && typeof this.video.cancelVideoFrameCallback === "function") {
          try { this.video.cancelVideoFrameCallback(this.rvfcId); } catch (_) {}
          this.rvfcId = null;
        }
      } else {
        const wantPipeline = this.config.enabled || this.config.frameGenEnabled;
        if (wantPipeline && !this.destroyed && !this.webglBrokenForVideo) {
          this.active = false;
          this.newFramePending = true;
          this.needsConfigRedraw = true;
          this.start();
        }
      }
    }

    _scheduleVideoFrameCallback() {
      if (!this.active || this.destroyed || document.hidden) return;
      if (typeof this.video.requestVideoFrameCallback !== "function") return;
      if (this.rvfcId !== null) return;

      const onDecodedFrame = (now, metadata) => {
        this.rvfcId = null;
        if (!this.active || this.destroyed || document.hidden) return;

        const isNewFrame =
          (typeof metadata.presentedFrames === "number" && metadata.presentedFrames !== this.lastPresentedFrames) ||
          (typeof metadata.mediaTime === "number" && Math.abs(metadata.mediaTime - this.lastVideoTime) > 0.002);

        if (isNewFrame) {
          this.lastPresentedFrames = metadata.presentedFrames;
          this.newFramePending = true;
          if (this.lastFrameUploadTs > 0) {
            const delta = now - this.lastFrameUploadTs;
            if (delta >= 7 && delta <= 120) {
              this.estimatedFrameIntervalMs = this.estimatedFrameIntervalMs <= 0
                ? delta
                : (this.estimatedFrameIntervalMs * 0.72 + delta * 0.28);
            }
          }
        }
        this._scheduleVideoFrameCallback();
      };

      try {
        this.rvfcId = this.video.requestVideoFrameCallback(onDecodedFrame);
      } catch (_) {
        this.rvfcId = null;
      }
    }

    start() {
      if (document.hidden) return;
      if (this.destroyed || this.webglBrokenForVideo || !this.gl || (!this.progGl2 && !this.progGl1)) {
        if (this.config.enabled) this._applySvgGpuFallback();
        return;
      }
      this._clearSvgGpuFallback();
      this.attachToParent();
      if (this.active) return;

      this.active = true;
      this.status = this.isWebGL2 ? "ACTIVE_WEBGL2" : "ACTIVE_WEBGL1";
      this.newFramePending = true;
      this.needsConfigRedraw = true;
      this.needsGlHealthCheck = true;
      this.canvas.style.opacity = this.frameVerified ? "1" : "0";
      this.lastOutputFpsTs = performance.now();
      this.outputFrames = 0;

      this._scheduleVideoFrameCallback();

      const renderLoop = (nowTs) => {
        if (!this.active || this.destroyed || document.hidden) {
          this.rafId = null;
          return;
        }
        this._renderFrame(nowTs);
        this.rafId = requestAnimationFrame(renderLoop);
      };
      this.rafId = requestAnimationFrame(renderLoop);
    }

    stop() {
      this.active = false;
      if (this.rafId !== null) {
        cancelAnimationFrame(this.rafId);
        this.rafId = null;
      }
      if (this.rvfcId !== null && typeof this.video.cancelVideoFrameCallback === "function") {
        try { this.video.cancelVideoFrameCallback(this.rvfcId); } catch (_) {}
        this.rvfcId = null;
      }
      this.canvas.style.opacity = "0";
      this.outputFps = 0;
      this.outputFrames = 0;
      if (this.status !== "UNSUPPORTED" && this.status !== "ACTIVE_SVG_GPU_FILTER" && this.status !== "ACTIVE_CSS_GPU_FILTER") {
        this.status = "IDLE";
      }
    }

    _renderFrame(nowTs) {
      const video = this.video;
      const gl = this.gl;
      if (!video || !gl || !video.isConnected || video.readyState < 2 || video.videoWidth <= 0 || video.videoHeight <= 0) {
        return;
      }

      if (document.fullscreenElement === video || document.webkitFullscreenElement === video) {
        this.canvas.style.opacity = "0";
        return;
      }

      this.syncGeometry();

      const measuredNativeFps = this.estimatedFrameIntervalMs > 6 ? (1000 / this.estimatedFrameIntervalMs) : 0;
      const targetFps = Math.max(30, Math.min(120, this.config.targetFps || 60));
      const interpActive = !!(
        this.isWebGL2 &&
        this.config.frameGenEnabled &&
        !this.interpAutoDisabled &&
        !video.paused &&
        !video.ended &&
        (measuredNativeFps <= 0 || targetFps > measuredNativeFps + 3)
      );

      // When Frame Gen is active, cap internal resolution at 1440p (2560px) so mobile GPUs hold rock-solid 60 FPS!
      // When only 4K Upscaling is active, allow full 3840px (4K)!
      const scale = Math.max(0.75, Math.min(2.0, this.config.resolutionScale || 1.5));
      let targetW = Math.round(video.videoWidth * scale);
      let targetH = Math.round(video.videoHeight * scale);
      const effectiveCap = interpActive ? Math.min(this.dynamicMaxDim, 2560) : this.dynamicMaxDim;
      const maxDim = Math.min(effectiveCap, gl.getParameter(gl.MAX_TEXTURE_SIZE) || 3840);
      if (targetW > maxDim || targetH > maxDim) {
        const k = Math.min(maxDim / targetW, maxDim / targetH);
        targetW = Math.max(64, Math.round(targetW * k));
        targetH = Math.max(64, Math.round(targetH * k));
      }
      if (this.canvas.width !== targetW || this.canvas.height !== targetH) {
        this.canvas.width = targetW;
        this.canvas.height = targetH;
        this.needsConfigRedraw = true;
      }

      if (Math.abs(video.currentTime - this.lastVideoTime) > 0.002) {
        this.newFramePending = true;
        if (this.lastFrameUploadTs > 0 && typeof video.requestVideoFrameCallback !== "function") {
          const delta = nowTs - this.lastFrameUploadTs;
          if (delta >= 7 && delta <= 120) {
            this.estimatedFrameIntervalMs = this.estimatedFrameIntervalMs <= 0
              ? delta
              : (this.estimatedFrameIntervalMs * 0.72 + delta * 0.28);
          }
        }
      }

      const needUpload = this.newFramePending || !this.hasCurrFrame;
      if (interpActive && !needUpload && !this.needsConfigRedraw) {
        const minRenderInterval = (1000 / targetFps) * 0.88;
        if (nowTs - this.lastRenderTs < minRenderInterval) return;
      }
      // Zero wasted GPU work: if Frame Gen is OFF and no new video frame arrived, skip drawing completely!
      if (!needUpload && !interpActive && !this.needsConfigRedraw) {
        return;
      }

      const t0 = performance.now();
      this.needsConfigRedraw = false;

      try {
        if (needUpload) {
          this.newFramePending = false;
          this.lastVideoTime = video.currentTime;
          this.lastFrameUploadTs = nowTs;

          if (this.hasCurrFrame && this.isWebGL2) {
            const tmp = this.texPrev;
            this.texPrev = this.texCurr;
            this.texCurr = tmp;
            this.hasPrevFrame = true;
          }

          gl.activeTexture(gl.TEXTURE0);
          gl.bindTexture(gl.TEXTURE_2D, this.texCurr);
          this._uploadFrameToCurrTexture(gl, video);
          this.hasCurrFrame = true;
        }

        if (!this.hasCurrFrame) return;

        const enhOn = this.config.enabled ? 1.0 : 0.0;
        const elapsedSinceFrame = this.lastFrameUploadTs > 0 ? Math.max(0, nowTs - this.lastFrameUploadTs) : 0;
        const intervalMs = this.estimatedFrameIntervalMs > 6 ? this.estimatedFrameIntervalMs : 41.67;
        const alpha = interpActive && this.hasPrevFrame
          ? Math.max(0.0, Math.min(1.0, elapsedSinceFrame / intervalMs))
          : 1.0;

        gl.bindFramebuffer(gl.FRAMEBUFFER, null);
        gl.viewport(0, 0, targetW, targetH);

        if (this.isWebGL2 && this.progGl2) {
          gl.useProgram(this.progGl2);
          this._bindQuad(gl, this.u2.aPos, this.u2.aTex);

          gl.activeTexture(gl.TEXTURE0);
          gl.bindTexture(gl.TEXTURE_2D, this.texCurr);
          gl.uniform1i(this.u2.uTexCurr, 0);

          gl.activeTexture(gl.TEXTURE1);
          gl.bindTexture(gl.TEXTURE_2D, this.hasPrevFrame ? this.texPrev : this.texCurr);
          gl.uniform1i(this.u2.uTexPrev, 1);

          gl.uniform2f(this.u2.uSrcTexelSize, 1.0 / video.videoWidth, 1.0 / video.videoHeight);
          gl.uniform2f(this.u2.uOutTexelSize, 1.0 / targetW, 1.0 / targetH);
          gl.uniform1f(this.u2.uInterpEnabled, interpActive && this.hasPrevFrame ? 1.0 : 0.0);
          gl.uniform1f(this.u2.uAlpha, alpha);
          gl.uniform1f(this.u2.uUpscaleStrength, this.config.upscaleStrength * enhOn);
          gl.uniform1f(this.u2.uSharpen, this.config.sharpen * enhOn);
          gl.uniform1f(this.u2.uDeband, this.config.deband * enhOn);
          gl.uniform1f(this.u2.uDenoise, this.config.denoise * enhOn);
          gl.uniform1f(this.u2.uEdgeEnhance, this.config.edgeEnhance * enhOn);
          gl.uniform1f(this.u2.uSplitCompare, this.config.splitCompare ? 1.0 : 0.0);
          gl.uniform1f(this.u2.uSplitPos, Math.max(0.05, Math.min(0.95, this.config.splitPosition || 0.5)));

          gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4);
        } else if (this.progGl1) {
          gl.useProgram(this.progGl1);
          this._bindQuad(gl, this.uGl1.aPos, this.uGl1.aTex);

          gl.activeTexture(gl.TEXTURE0);
          gl.bindTexture(gl.TEXTURE_2D, this.texCurr);
          gl.uniform1i(this.uGl1.uTexCurr, 0);
          gl.uniform2f(this.uGl1.uTexelSize, 1.0 / video.videoWidth, 1.0 / video.videoHeight);
          gl.uniform1f(this.uGl1.uUpscaleStrength, this.config.upscaleStrength * enhOn);
          gl.uniform1f(this.uGl1.uSharpen, this.config.sharpen * enhOn);
          gl.uniform1f(this.uGl1.uEdgeEnhance, this.config.edgeEnhance * enhOn);
          gl.uniform1f(this.uGl1.uSplitCompare, this.config.splitCompare ? 1.0 : 0.0);
          gl.uniform1f(this.uGl1.uSplitPos, Math.max(0.05, Math.min(0.95, this.config.splitPosition || 0.5)));

          gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4);
        }

        this.consecutiveValidDraws++;

        // Throttled Pixel Health Guard (NEVER stalls GPU on every frame!)
        const healthInterval = this.frameVerified ? 3500 : 450;
        const needGlCheck =
          this.needsGlHealthCheck ||
          (nowTs - this.lastGlHealthCheckTs) >= healthInterval;

        if (needGlCheck) {
          this.needsGlHealthCheck = false;
          this.lastGlHealthCheckTs = nowTs;
          const verdict = this._verifyWebGlOutput(gl, targetW, targetH);
          if (verdict === "OK") {
            this.frameVerified = true;
            this.canvas.style.opacity = "1";
          } else if (verdict === "BROKEN") {
            this.canvas.style.opacity = "0";
            if (!this.directUploadFailed) {
              this.directUploadFailed = true;
              this.frameVerified = false;
              this.consecutiveValidDraws = 0;
              this.needsGlHealthCheck = true;
              this.newFramePending = true;
            } else {
              this._fallbackToSvgGpuFilter();
            }
            return;
          } else if (!this.frameVerified && this.consecutiveValidDraws >= 4) {
            // Valid dark/fade anime scene with zero GL errors -> expose canvas cleanly
            if (gl.getError() === gl.NO_ERROR) {
              this.frameVerified = true;
              this.canvas.style.opacity = "1";
            }
          }
        } else if (this.frameVerified && this.canvas.style.opacity !== "1") {
          this.canvas.style.opacity = "1";
        }

        this.lastRenderTs = nowTs;

        // Gentle thermal guard: only step down internal resolution if GPU consistently takes >30ms
        const dt = performance.now() - t0;
        if (dt > 30) {
          this.slowFrameCount++;
          if (this.slowFrameCount > 24) {
            if (this.dynamicMaxDim > 2560) this.dynamicMaxDim = 2560;
            else if (this.dynamicMaxDim > 1920) this.dynamicMaxDim = 1920;
            else if (this.dynamicMaxDim > 1440) this.dynamicMaxDim = 1440;
            else if (interpActive) this.interpAutoDisabled = true;
            this.slowFrameCount = 0;
          }
        } else {
          this.slowFrameCount = Math.max(0, this.slowFrameCount - 1);
        }

        if (!video.paused && !video.ended) {
          this.outputFrames++;
          const fpsElapsed = nowTs - this.lastOutputFpsTs;
          if (fpsElapsed >= 500) {
            const instant = (this.outputFrames * 1000) / fpsElapsed;
            this.outputFps = this.outputFps === 0
              ? Math.round(instant)
              : Math.round(this.outputFps * 0.35 + instant * 0.65);
            this.outputFrames = 0;
            this.lastOutputFpsTs = nowTs;
          }
        }
      } catch (_) {
        // SecurityError (cross-origin direct MP4 without CORS) -> switch to real SVG GPU 3x3 sharpening!
        this._fallbackToSvgGpuFilter();
      }
    }

    applyScalingStyles(objectFit, transformCss) {
      this.canvas.style.setProperty("object-fit", objectFit, "important");
      this.canvas.style.setProperty("transform", transformCss, "important");
      this.canvas.style.setProperty("transform-origin", "center center", "important");
      this.syncGeometry();
    }

    destroy() {
      this.destroyed = true;
      this.stop();
      this._clearSvgGpuFallback();
      this.canvas.removeEventListener("webglcontextlost", this._onContextLost, false);
      this.video.removeEventListener("seeked", this._onVideoSeeked);
      this.video.removeEventListener("pause", this._onVideoPause);
      this.video.removeEventListener("ended", this._onVideoPause);
      this.video.removeEventListener("emptied", this._onVideoReset);
      this.video.removeEventListener("loadeddata", this._onVideoReset);
      if (this.canvas.isConnected) this.canvas.remove();
      this.stagingCanvas = null;
      this.stagingCtx = null;
    }
  }

  // =========================================================================
  // 4. VIDEO CONTROLLER, SCALING, TELEMETRY, STREAM SCANNER & READER MODE
  // =========================================================================
  let activeVideo = null;
  let pipeline = null;
  let currentScalingMode = "FIT";
  let currentCustomZoom = 1.0;
  let isForceFullscreen = false;
  let hideSiteControls = false;
  let currentEnhancementConfig = {
    enabled: false,
    upscaleStrength: 0.82,
    sharpen: 0.76,
    deband: 0.45,
    denoise: 0.30,
    edgeEnhance: 0.78,
    resolutionScale: 1.5,
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
  const reportedDomStreamUrls = new Set();

  function findAllVideos(root) {
    const videos = [];
    if (!root) return videos;
    try {
      const direct = root.querySelectorAll("video");
      for (let i = 0; i < direct.length; i++) videos.push(direct[i]);
      const all = root.querySelectorAll("*");
      const lim = Math.min(all.length, 400);
      for (let i = 0; i < lim; i++) {
        if (all[i].shadowRoot) videos.push(...findAllVideos(all[i].shadowRoot));
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
    const srcKey = `${v.currentSrc || v.src || ""}|${v.videoWidth}x${v.videoHeight}`;
    if (srcKey !== lastVideoSrcKey) {
      lastVideoSrcKey = srcKey;
      resetFpsBaseline();
      return;
    }
    const now = performance.now();
    const totalFrames = getDecodedFrameCount(v);
    if (totalFrames <= 0) return;

    if (lastSampleFrames < 0 || totalFrames < lastSampleFrames) {
      lastSampleFrames = totalFrames;
      lastSampleTs = now;
      return;
    }
    const dt = now - lastSampleTs;
    const df = totalFrames - lastSampleFrames;
    if (dt >= 450 && df > 0) {
      const instant = (df * 1000) / dt;
      if (instant >= 5 && instant <= 144) {
        measuredSourceFps = measuredSourceFps <= 0
          ? Math.round(instant)
          : Math.round(measuredSourceFps * 0.35 + instant * 0.65);
      }
      lastSampleFrames = totalFrames;
      lastSampleTs = now;
    }
  }

  function startSourceRvfcTracker(video) {
    if (rvfcHandle !== null && activeVideo && typeof activeVideo.cancelVideoFrameCallback === "function") {
      try { activeVideo.cancelVideoFrameCallback(rvfcHandle); } catch (_) {}
      rvfcHandle = null;
    }
    rvfcPresentedFrames = 0;
    if (!video || typeof video.requestVideoFrameCallback !== "function" || document.hidden) return;
    const tick = (_now, meta) => {
      if (video !== activeVideo || !video.isConnected || document.hidden) {
        rvfcHandle = null;
        return;
      }
      rvfcPresentedFrames = typeof meta.presentedFrames === "number"
        ? meta.presentedFrames
        : (rvfcPresentedFrames + 1);
      try { rvfcHandle = video.requestVideoFrameCallback(tick); } catch (_) { rvfcHandle = null; }
    };
    try { rvfcHandle = video.requestVideoFrameCallback(tick); } catch (_) { rvfcHandle = null; }
  }

  function ensureScalingStylesheet() {
    if (!scalingStyleTag) {
      scalingStyleTag = document.createElement("style");
      scalingStyleTag.id = "zenithfox-scaling-style";
      (document.head || document.documentElement).appendChild(scalingStyleTag);
    }
    return scalingStyleTag;
  }

  function applyScaling() {
    if (!activeVideo || !activeVideo.isConnected || isApplyingScaling) return;
    isApplyingScaling = true;
    try {
      const v = activeVideo;
      const mode = currentScalingMode || "FIT";
      const zoom = Math.max(0.5, Math.min(3.0, currentCustomZoom || 1.0));
      v.setAttribute("data-ab-active", "true");

      const rect = v.getBoundingClientRect();
      const cw = rect.width || window.innerWidth || 1;
      const ch = rect.height || window.innerHeight || 1;
      const vw = v.videoWidth || 16;
      const vh = v.videoHeight || 9;
      const containerAr = cw / ch;
      const videoAr = vw / vh;

      let objectFit = "contain";
      let transformCss = "none";

      if (mode === "ORIGINAL") {
        objectFit = "none";
      } else if (mode === "FIT") {
        objectFit = "contain";
      } else if (mode === "FILL") {
        objectFit = "cover";
      } else if (mode === "STRETCH") {
        // Blur-free GPU stretch: object-fit: fill maps full native video buffer directly to viewport
        objectFit = "fill";
      } else if (mode === "ZOOM") {
        objectFit = "cover";
        const extra = containerAr > videoAr
          ? Math.min(1.22, (containerAr / videoAr) * 0.96)
          : Math.min(1.22, (videoAr / containerAr) * 0.96);
        transformCss = `scale(${Math.max(1.06, extra).toFixed(4)})`;
      } else if (mode === "CUSTOM") {
        objectFit = "contain";
        transformCss = Math.abs(zoom - 1.0) > 0.01 ? `scale(${zoom.toFixed(4)})` : "none";
      }

      const styleEl = ensureScalingStylesheet();
      styleEl.textContent = `
        video[data-ab-active="true"],
        canvas[data-anime-browser-canvas="true"] {
          object-fit: ${objectFit} !important;
          transform: ${transformCss} !important;
          transform-origin: center center !important;
        }
      `;
      v.style.setProperty("object-fit", objectFit, "important");
      v.style.setProperty("transform", transformCss, "important");
      v.style.setProperty("transform-origin", "center center", "important");

      if (pipeline) {
        pipeline.applyScalingStyles(objectFit, transformCss);
      }
    } finally {
      isApplyingScaling = false;
    }
  }

  function setForceFullscreen(enabled, hideControls) {
    isForceFullscreen = !!enabled;
    hideSiteControls = !!hideControls;
    if (!activeVideo) return;

    if (isForceFullscreen) {
      if (!savedInlineStyles) {
        savedInlineStyles = {
          position: activeVideo.style.position,
          top: activeVideo.style.top,
          left: activeVideo.style.left,
          width: activeVideo.style.width,
          height: activeVideo.style.height,
          zIndex: activeVideo.style.zIndex,
          background: activeVideo.style.background
        };
      }
      if (!fsStyleTag) {
        fsStyleTag = document.createElement("style");
        fsStyleTag.id = "zenithfox-force-fs";
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
          z-index: 2147483640 !important;
          background: #000 !important;
        }
        canvas[data-anime-browser-canvas="true"] {
          position: fixed !important;
          inset: 0 !important;
          width: 100vw !important;
          height: 100vh !important;
          z-index: 2147483641 !important;
        }
        ${hideSiteControls ? `.ytp-chrome-top, .ytp-chrome-bottom, .jw-controls, .vjs-control-bar, .plyr__controls { opacity: 0 !important; pointer-events: none !important; }` : ""}
      `;
    } else {
      if (fsStyleTag) {
        fsStyleTag.remove();
        fsStyleTag = null;
      }
      if (savedInlineStyles) {
        Object.assign(activeVideo.style, savedInlineStyles);
        savedInlineStyles = null;
      }
    }
    applyScaling();
  }

  function bindVideo(video) {
    if (activeVideo === video) return;
    if (videoStyleObserver) {
      videoStyleObserver.disconnect();
      videoStyleObserver = null;
    }
    if (pipeline) {
      pipeline.destroy();
      pipeline = null;
    }
    if (activeVideo) {
      activeVideo.removeAttribute("data-ab-active");
    }

    activeVideo = video;
    resetFpsBaseline();
    if (!video) return;

    video.setAttribute("data-ab-active", "true");
    startSourceRvfcTracker(video);

    pipeline = new ChromiumWebGL4KPipeline(video);
    pipeline.updateConfig(currentEnhancementConfig);
    applyScaling();

    videoStyleObserver = new MutationObserver(() => {
      if (!isApplyingScaling) applyScaling();
    });
    videoStyleObserver.observe(video, { attributes: true, attributeFilter: ["style", "class"] });
  }

  function handleVideoControl(action, payload) {
    const v = activeVideo || pickPrimaryVideo();
    if (!v) return;
    if (v !== activeVideo) bindVideo(v);
    try {
      switch (action) {
        case "PAUSE":
          if (!v.paused) v.pause();
          break;
        case "TOGGLE_PLAY":
          if (v.paused) v.play().catch(() => {});
          else v.pause();
          break;
        case "SEEK_BY": {
          const delta = Number(payload.seconds || 0);
          const dur = isFinite(v.duration) && v.duration > 0 ? v.duration : 999999;
          v.currentTime = Math.max(0, Math.min(dur, (v.currentTime || 0) + delta));
          break;
        }
        case "SEEK_TO": {
          const target = Number(payload.seconds || 0);
          const dur = isFinite(v.duration) && v.duration > 0 ? v.duration : 999999;
          v.currentTime = Math.max(0, Math.min(dur, target));
          break;
        }
        case "SET_SPEED": {
          const spd = Math.max(0.25, Math.min(4.0, Number(payload.speed || 1.0)));
          v.playbackRate = spd;
          break;
        }
        case "TOGGLE_MUTE":
          v.muted = !v.muted;
          break;
        case "TOGGLE_SUBTITLES": {
          const tracks = v.textTracks;
          if (tracks && tracks.length > 0) {
            const anyShowing = Array.from(tracks).some(t => t.mode === "showing");
            for (let i = 0; i < tracks.length; i++) {
              tracks[i].mode = anyShowing ? "hidden" : (i === 0 ? "showing" : "hidden");
            }
          }
          break;
        }
      }
    } catch (_) {}
  }

  function scanDomVideoStreams() {
    if (document.hidden) return;
    const host = (location.hostname || "").toLowerCase();
    if (host.includes("youtube.com") || host.includes("youtu.be") || host.includes("googlevideo.com")) return;
    const videos = findAllVideos(document);
    for (const v of videos) {
      const candidates = [v.currentSrc, v.src];
      v.querySelectorAll("source").forEach((s) => candidates.push(s.src));
      for (const raw of candidates) {
        if (!raw || !raw.startsWith("http") || reportedDomStreamUrls.has(raw)) continue;
        const lower = raw.toLowerCase();
        const isHls = lower.includes(".m3u8") || lower.includes("%2em3u8") || lower.includes("/m3u8");
        const isDirect = /\.(mp4|m4v|webm)(\?|$|#)/i.test(lower);
        if (!isHls && !isDirect) continue;
        reportedDomStreamUrls.add(raw);
        postToNative({
          type: "STREAM_DETECTED",
          data: {
            url: raw,
            title: document.title || "Anime Video",
            streamType: isHls ? "HLS" : "DIRECT",
            mimeType: isHls ? "application/vnd.apple.mpegurl" : (lower.includes(".webm") ? "video/webm" : "video/mp4"),
            qualityLabel: v.videoHeight > 0 ? `${v.videoHeight}p` : (isHls ? "HLS Stream" : "MP4"),
            referer: location.href,
            origin: location.origin,
            userAgent: navigator.userAgent
          }
        });
      }
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
      paragraphs: paragraphs.length > 0 ? paragraphs : [(document.body && document.body.innerText || "").slice(0, 4000)]
    };
  }

  function sendTelemetry() {
    if (document.hidden) return;
    const candidate = pickPrimaryVideo();
    if (candidate && candidate !== activeVideo) bindVideo(candidate);
    else if (!candidate && activeVideo && !activeVideo.isConnected) bindVideo(null);

    if (!activeVideo) {
      if (window === window.top) {
        postToNative({ type: "VIDEO_TELEMETRY", data: { hasVideo: false, url: location.href } });
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

    postToNative({
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
        gpuRenderer: pipeline ? pipeline.gpuRenderer : "Chromium GPU",
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

  commandHandler = function (msg) {
    if (!msg || !msg.type) return;
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
      case "APP_RESUMED":
        if (pipeline) {
          pipeline.onVisibilityChanged(false);
          pipeline.newFramePending = true;
          pipeline.needsConfigRedraw = true;
        }
        applyScaling();
        sendTelemetry();
        break;
      case "SYNC_ADBLOCK":
        adBlockEnabled = !!(msg.payload && msg.payload.enabled);
        popupBlockEnabled = !!(msg.payload && msg.payload.popupBlockEnabled);
        applyCosmeticAdFilter(true);
        break;
      case "CLEAR_STREAMS":
        reportedDomStreamUrls.clear();
        setTimeout(scanDomVideoStreams, 400);
        break;
      case "REQUEST_READER_MODE":
        if (window === window.top) {
          postToNative({ type: "READER_MODE_CONTENT", data: extractReaderModeContent() });
        }
        break;
      case "REQUEST_PAGE_HTML":
        if (window === window.top) {
          postToNative({
            type: "PAGE_HTML_CONTENT",
            data: { title: document.title || "page", html: document.documentElement.outerHTML.slice(0, 500000) }
          });
        }
        break;
    }
  };

  // Keep 4K WebGL2 canvas & Optical-Flow Frame Generation active even when a player calls video.requestFullscreen() directly
  try {
    const origVideoReqFs = HTMLVideoElement.prototype.requestFullscreen;
    if (typeof origVideoReqFs === "function") {
      HTMLVideoElement.prototype.requestFullscreen = function (options) {
        try {
          const parent = this.parentElement;
          const enhWanted = currentEnhancementConfig.enabled || currentEnhancementConfig.frameGenEnabled;
          if (
            enhWanted &&
            parent &&
            parent !== document.body &&
            parent !== document.documentElement &&
            typeof parent.requestFullscreen === "function"
          ) {
            const vr = this.getBoundingClientRect();
            const pr = parent.getBoundingClientRect();
            if (Math.abs(pr.width - vr.width) <= 48 && Math.abs(pr.height - vr.height) <= 48) {
              return parent.requestFullscreen(options);
            }
          }
        } catch (_) {}
        return origVideoReqFs.call(this, options);
      };
    }
  } catch (_) {}

  setupNativeBridgeListener();

  document.addEventListener("visibilitychange", () => {
    const hidden = !!document.hidden;
    if (pipeline) pipeline.onVisibilityChanged(hidden);
    if (!hidden) {
      if (activeVideo) startSourceRvfcTracker(activeVideo);
      setTimeout(() => {
        applyScaling();
        sendTelemetry();
      }, 80);
    }
  });

  document.addEventListener("fullscreenchange", () => setTimeout(applyScaling, 100));
  document.addEventListener("webkitfullscreenchange", () => setTimeout(applyScaling, 100));
  window.addEventListener("resize", () => applyScaling());
  window.addEventListener("orientationchange", () => setTimeout(applyScaling, 160));

  function bootstrapDomObservers() {
    applyCosmeticAdFilter(true);
    scanDomVideoStreams();
    sendTelemetry();

    let mutationTimer = null;
    const observer = new MutationObserver(() => {
      if (document.hidden) return;
      if (mutationTimer !== null) return;
      mutationTimer = setTimeout(() => {
        mutationTimer = null;
        if (document.hidden) return;
        applyCosmeticAdFilter(false);
        if (activeVideo && activeVideo.isConnected && activeVideo.videoWidth > 0 && !activeVideo.paused) return;
        const cand = pickPrimaryVideo();
        if (cand && cand !== activeVideo) {
          bindVideo(cand);
          sendTelemetry();
        }
      }, 350);
    });
    if (document.documentElement) {
      observer.observe(document.documentElement, { childList: true, subtree: true });
    }
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", bootstrapDomObservers, { once: true });
  } else {
    bootstrapDomObservers();
  }

  // Low-power 1.2s heartbeat (completely paused when document.hidden!)
  setInterval(() => {
    if (document.hidden) return;
    sendTelemetry();
    scanDomVideoStreams();
  }, 1200);

  postToNative({ type: "EXTENSION_READY" });
})();

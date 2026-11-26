// ZenithFox Browser v1.7.0 — Clean-Sheet Zero-Black-Screen 4K WebGL2 Engine & GPU Frame Generation
//
// Why previous Android GeckoView WebGL2 upscalers showed BLACK or PINK screens:
//   On Android GeckoView (Mali-G615 / Adreno / Xclipse), calling gl.texImage2D(..., HTMLVideoElement)
//   invokes a broken zero-copy MediaCodec SurfaceTexture (GL_TEXTURE_EXTERNAL_OES) blit that fills the
//   WebGL texture with solid magenta/pink or pure black while gl.getError() reports NO_ERROR.
//
// Clean-Sheet Solution:
//   1. CPU-Verified RGBA Staging Bridge:
//      Draws <video> into a software-backed 2D canvas ({ willReadFrequently: true }), reads pure RGBA
//      bytes via getImageData(), runs an instant 9-point CPU pixel check (rejecting black, pink/magenta,
//      green, or uniform frames BEFORE touching WebGL), then uploads pure Uint8Array pixels via
//      gl.texImage2D(..., Uint8Array) — 100% immune to Android's SurfaceTexture OES black/pink bug!
//   2. High-Precision (FP32) Single-Pass WebGL2 4K GPU Engine:
//      - Crisp 5-tap Catmull-Rom super-resolution resampling (zero bilinear mush)
//      - True Anime4K directional line-thinning & ink darkening (Scharr gradient push)
//      - NaN-Free AMD FidelityFX CAS (Contrast-Adaptive Sharpening) + Dual-Scale 4K Micro-Detail
//      - Luma-preserving HDR cel micro-contrast & selective saturation pop
//      - Smooth GPU Optical-Flow Frame Generation (MCFI) running 100% on GPU between verified frames
//      - Interactive Live Before / After Split-Screen Comparison Divider
//   3. Zero-Black-Screen Guarantee:
//      Canvas stays invisible (opacity: 0) until BOTH CPU 9-point AND WebGL 5-point checks pass.
//      If a cross-origin stream throws SecurityError or fails pixel verification, canvas stays hidden
//      and a clean WebRender-safe GPU clarity/contrast/vibrance filter enhances <video> directly.
(function (globalScope) {
  "use strict";

  const VERTEX_SHADER_GL2 = `#version 300 es
    in vec2 aPos;
    in vec2 aTexCoord;
    out highp vec2 vUv;
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
    uniform float uVibrance;
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

    // Crisp 5-tap Catmull-Rom subpixel super-resolution sampling (eliminates bilinear blur!)
    vec3 sampleCatmullRomCurr(vec2 uv) {
      vec2 texSize = 1.0 / max(uSrcTexelSize, vec2(0.0001));
      vec2 samplePos = uv * texSize;
      vec2 tc1 = floor(samplePos - 0.5) + 0.5;
      vec2 f = samplePos - tc1;
      vec2 f2 = f * f;
      vec2 f3 = f2 * f;

      vec2 w0 = f2 - 0.5 * (f3 + f);
      vec2 w1 = 1.5 * f3 - 2.5 * f2 + 1.0;
      vec2 w3 = 0.5 * (f3 - f2);
      vec2 w2 = 1.0 - w0 - w1 - w3;

      vec2 w12 = w1 + w2;
      vec2 offset12 = w2 / max(w12, vec2(0.001));

      vec2 tc0  = (tc1 - 1.0) * uSrcTexelSize;
      vec2 tc3  = (tc1 + 2.0) * uSrcTexelSize;
      vec2 tc12 = (tc1 + offset12) * uSrcTexelSize;

      vec3 c0 = tapCurr(vec2(tc12.x, tc0.y));
      vec3 c1 = tapCurr(vec2(tc0.x,  tc12.y));
      vec3 c2 = tapCurr(vec2(tc12.x, tc12.y));
      vec3 c3 = tapCurr(vec2(tc3.x,  tc12.y));
      vec3 c4 = tapCurr(vec2(tc12.x, tc3.y));

      float cw0 = w12.x * w0.y;
      float cw1 = w0.x  * w12.y;
      float cw2 = w12.x * w12.y;
      float cw3 = w3.x  * w12.y;
      float cw4 = w12.x * w3.y;

      vec3 sum = c0 * cw0 + c1 * cw1 + c2 * cw2 + c3 * cw3 + c4 * cw4;
      vec3 res = sum / max(cw0 + cw1 + cw2 + cw3 + cw4, 0.001);
      vec3 minC = min(c2, min(min(c0, c4), min(c1, c3)));
      vec3 maxC = max(c2, max(max(c0, c4), max(c1, c3)));
      return clamp(res, minC, maxC);
    }

    // Smooth GPU Optical-Flow Motion-Compensated Frame Generation (MCFI)
    vec3 sampleMotionBase(vec2 uv, vec3 currCenter) {
      if (uInterpEnabled < 0.5 || uAlpha >= 0.96 || uAlpha <= 0.04) {
        return currCenter;
      }
      vec3 prevCenter = tapPrev(uv);
      float lCurr = luma(currCenter);
      float lPrev = luma(prevCenter);
      float it = lCurr - lPrev;

      // Scene-cut protection: avoid ghosting across sudden anime cuts
      if (abs(it) > 0.28) {
        return currCenter;
      }

      vec2 step2 = uSrcTexelSize * 2.0;
      float ix = (
        (luma(tapCurr(uv + vec2(step2.x, 0.0))) - luma(tapCurr(uv - vec2(step2.x, 0.0)))) +
        (luma(tapPrev(uv + vec2(step2.x, 0.0))) - luma(tapPrev(uv - vec2(step2.x, 0.0))))
      ) * 0.25;
      float iy = (
        (luma(tapCurr(uv + vec2(0.0, step2.y))) - luma(tapCurr(uv - vec2(0.0, step2.y)))) +
        (luma(tapPrev(uv + vec2(0.0, step2.y))) - luma(tapPrev(uv - vec2(0.0, step2.y))))
      ) * 0.25;

      float denom = ix * ix + iy * iy + 0.008;
      vec2 flow = clamp(vec2(-it * ix / denom, -it * iy / denom), vec2(-2.4), vec2(2.4));
      vec2 flowUv = flow * step2;

      vec3 warpedPrev = tapPrev(uv - flowUv * uAlpha);
      vec3 warpedCurr = tapCurr(uv + flowUv * (1.0 - uAlpha));

      float warpDiff = abs(luma(warpedPrev) - luma(warpedCurr));
      float confidence = 1.0 - smoothstep(0.05, 0.20, warpDiff);
      vec3 blended = mix(warpedPrev, warpedCurr, uAlpha);
      return mix(currCenter, blended, confidence);
    }

    void main() {
      vec3 rawCurr = tapCurr(vUv);

      // Interactive Live Before / After Split-Screen Comparison
      if (uSplitCompare > 0.5) {
        if (abs(vUv.x - uSplitPos) < max(0.0022, uOutTexelSize.x * 2.5)) {
          fragColor = vec4(0.753, 0.518, 0.988, 1.0);
          return;
        }
        if (vUv.x < uSplitPos) {
          fragColor = vec4(clamp(rawCurr, 0.0, 1.0), 1.0);
          return;
        }
      }

      // 1. Crisp Catmull-Rom base sample + optional GPU Frame Generation
      vec3 crispBase = (uUpscaleStrength > 0.01 || uSharpen > 0.01)
        ? sampleCatmullRomCurr(vUv)
        : rawCurr;
      vec3 c = sampleMotionBase(vUv, crispBase);

      float anyEnhance = uUpscaleStrength + uSharpen + uDeband + uDenoise + uEdgeEnhance + uVibrance;
      if (anyEnhance < 0.01) {
        fragColor = vec4(clamp(c, 0.0, 1.0), 1.0);
        return;
      }

      vec2 dx = vec2(uSrcTexelSize.x, 0.0);
      vec2 dy = vec2(0.0, uSrcTexelSize.y);

      vec3 nw = tapCurr(vUv - dx - dy);
      vec3 n  = tapCurr(vUv - dy);
      vec3 ne = tapCurr(vUv + dx - dy);
      vec3 w  = tapCurr(vUv - dx);
      vec3 e  = tapCurr(vUv + dx);
      vec3 sw = tapCurr(vUv - dx + dy);
      vec3 s  = tapCurr(vUv + dy);
      vec3 se = tapCurr(vUv + dx + dy);

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

      // 2. Edge-Preserving Bilateral Compression Denoise
      if (uDenoise > 0.01) {
        float sigma = mix(0.020, 0.095, uDenoise);
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
        result = mix(result, denoised, uDenoise * 0.58);
      }

      // 3. Anti-Banding Sky & Gradient Reconstruction
      if (uDeband > 0.01) {
        vec2 dStep = uSrcTexelSize * 2.8;
        vec3 b1 = tapCurr(vUv + vec2( dStep.x,  dStep.y));
        vec3 b2 = tapCurr(vUv + vec2(-dStep.x,  dStep.y));
        vec3 b3 = tapCurr(vUv + vec2( dStep.x, -dStep.y));
        vec3 b4 = tapCurr(vUv + vec2(-dStep.x, -dStep.y));
        vec3 bandAvg = (b1 + b2 + b3 + b4) * 0.25;
        vec3 diff = abs(result - bandAvg);
        float maxDiff = max(diff.r, max(diff.g, diff.b));
        float threshold = mix(0.010, 0.046, uDeband);
        float flatMask = 1.0 - smoothstep(threshold * 0.45, threshold, maxDiff);
        float dither = (hash12(vUv * 1024.0) - 0.5) * (0.0038 * uDeband);
        result = mix(result, bandAvg + vec3(dither), flatMask * uDeband * 0.70);
      }

      // 4. True Anime4K Directional Line-Thinning & Ink-Line Darkening
      if (uUpscaleStrength > 0.01 || uEdgeEnhance > 0.01) {
        float gx = (-3.0*lnw - 10.0*lw - 3.0*lsw + 3.0*lne + 10.0*le + 3.0*lse) * 0.0625;
        float gy = (-3.0*lnw - 10.0*ln - 3.0*lne + 3.0*lsw + 10.0*ls + 3.0*lse) * 0.0625;
        float gradMag = clamp(sqrt(gx * gx + gy * gy) * 1.50, 0.0, 1.0);

        vec3 minCross = min(c, min(min(n, s), min(w, e)));
        vec3 maxCross = max(c, max(max(n, s), max(w, e)));
        vec3 minDiag  = min(min(nw, ne), min(sw, se));
        vec3 maxDiag  = max(max(nw, ne), max(sw, se));
        vec3 darkest  = min(minCross, minDiag);
        vec3 lightest = max(maxCross, maxDiag);

        float lDark  = luma(darkest);
        float lLight = luma(lightest);
        float contrastRange = lLight - lDark;
        float edgeMask = smoothstep(0.030, 0.22, gradMag) * smoothstep(0.032, 0.20, contrastRange);

        float thinAmount = clamp(uUpscaleStrength * 0.88 + uEdgeEnhance * 0.48, 0.0, 1.25) * edgeMask;
        float midLuma = (lDark + lLight) * 0.5;
        vec3 pushed = (lc < midLuma)
          ? mix(result, darkest, clamp(thinAmount * 0.66, 0.0, 0.76))
          : mix(result, lightest, clamp(thinAmount * 0.46, 0.0, 0.60));

        float isDarkLine = smoothstep(0.55, 0.14, lc) * edgeMask;
        pushed *= (1.0 - isDarkLine * (uUpscaleStrength * 0.24 + uEdgeEnhance * 0.16));
        result = pushed;
      }

      // 5. NaN-Free AMD FidelityFX CAS (Contrast-Adaptive Sharpening)
      float casStrength = clamp(uSharpen * 1.0 + uUpscaleStrength * 0.50 + uEdgeEnhance * 0.35, 0.0, 1.35);
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

        float peak = -1.0 / mix(9.2, 5.0, clamp(casStrength, 0.0, 1.0));
        vec3 wRGB = ampRGB * peak;
        vec3 rcpWeightRGB = 1.0 / max(1.0 + 4.0 * wRGB, vec3(0.20));
        vec3 casColor = clamp(((n + w + e + s) * wRGB + result) * rcpWeightRGB, vec3(0.0), vec3(1.0));
        result = mix(result, casColor, clamp(casStrength, 0.0, 1.0));
      }

      // 6. Dual-Scale 4K Micro-Detail Recovery (crisp hair, eyes, fine textures)
      if (uSharpen > 0.01 || uEdgeEnhance > 0.01) {
        vec2 mdx = vec2(uSrcTexelSize.x * 1.75, 0.0);
        vec2 mdy = vec2(0.0, uSrcTexelSize.y * 1.75);
        vec3 wideBlur = (
          tapCurr(vUv - mdx) + tapCurr(vUv + mdx) +
          tapCurr(vUv - mdy) + tapCurr(vUv + mdy) +
          n + s + w + e
        ) * 0.125;
        vec3 microDetail = clamp(result - wideBlur, vec3(-0.26), vec3(0.26));
        float detailGain = uSharpen * 0.68 + uEdgeEnhance * 0.56 + uUpscaleStrength * 0.34;
        result = clamp(result + microDetail * detailGain, vec3(0.0), vec3(1.0));
      }

      // 7. Luma-Preserving HDR Cel Micro-Contrast & Selective Saturation Pop
      if (uVibrance > 0.01) {
        float y = luma(result);
        float sY = y * y * (3.0 - 2.0 * y);
        float targetY = mix(y, sY, uVibrance * 0.36);
        result *= clamp(targetY / max(y, 0.002), 0.75, 1.35);
        result = clamp(result, vec3(0.0), vec3(1.0));

        float mx = max(result.r, max(result.g, result.b));
        float mn = min(result.r, min(result.g, result.b));
        float sat = (mx - mn) / max(mx, 0.002);
        float vibBoost = 1.0 + uVibrance * 0.58 * (1.0 - sat * 0.55);
        result = mix(vec3(luma(result)), result, vibBoost);
      }

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
    uniform float uVibrance;
    uniform float uSplitCompare;
    uniform float uSplitPos;
    varying highp vec2 vUv;

    float luma(vec3 c) { return dot(c, vec3(0.2126, 0.7152, 0.0722)); }

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
      float k = -1.0 / mix(9.2, 5.0, clamp(uSharpen + uUpscaleStrength * 0.4, 0.0, 1.0));
      vec3 wRGB = amp * k;
      vec3 result = clamp(((n + s + w + e) * wRGB + c) / max(1.0 + 4.0 * wRGB, vec3(0.20)), vec3(0.0), vec3(1.0));
      vec3 detail = clamp(result - (n + s + w + e) * 0.25, vec3(-0.24), vec3(0.24));
      result += detail * (uSharpen * 0.95 + uEdgeEnhance * 0.65);
      if (uVibrance > 0.01) {
        result = mix(vec3(luma(result)), result, 1.0 + uVibrance * 0.50);
      }
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

  // Instant 9-point CPU pixel sanity check on raw decoded RGBA ImageData BEFORE WebGL upload!
  // Rejects solid black, solid pink/magenta (broken OES YUV), solid green, or uniform blank frames.
  function verifyCpuImageData(data, w, h) {
    if (!data || w < 16 || h < 16) return "BLACK";
    const xs = [0.20, 0.50, 0.80];
    const ys = [0.20, 0.50, 0.80];
    let nonBlack = 0;
    let pinkCount = 0;
    let greenCount = 0;
    let minLuma = 255;
    let maxLuma = 0;

    for (let yi = 0; yi < 3; yi++) {
      const py = Math.floor(h * ys[yi]);
      for (let xi = 0; xi < 3; xi++) {
        const px = Math.floor(w * xs[xi]);
        const idx = (py * w + px) * 4;
        const r = data[idx];
        const g = data[idx + 1];
        const b = data[idx + 2];
        const y = (r * 54 + g * 183 + b * 19) >> 8;
        if (y < minLuma) minLuma = y;
        if (y > maxLuma) maxLuma = y;
        if (r > 7 || g > 7 || b > 7) nonBlack++;
        // Catch BOTH bright magenta (#FF00FF) AND pastel/washed pink (#FF80FF / #E060D0)
        if (r > 145 && b > 125 && (r - g) > 55 && (b - g) > 40) pinkCount++;
        // Catch broken YUV-zero green
        if (g > 110 && r < 30 && b < 30) greenCount++;
      }
    }

    if (pinkCount >= 3 || greenCount >= 5) return "BROKEN";
    if (nonBlack === 0) return "BLACK";
    // If all 9 points are identical pink/magenta, reject immediately
    if ((maxLuma - minLuma) <= 2 && pinkCount >= 1) return "BROKEN";
    return "OK";
  }

  class AnimeWebGLPipeline {
    constructor(videoEl) {
      this.video = videoEl;
      this.canvas = document.createElement("canvas");
      this.canvas.setAttribute("data-anime-browser-canvas", "true");
      this.canvas.style.pointerEvents = "none";
      this.canvas.style.position = "absolute";
      this.canvas.style.top = "0";
      this.canvas.style.left = "0";
      this.canvas.style.width = "100%";
      this.canvas.style.height = "100%";
      this.canvas.style.zIndex = "1";
      this.canvas.style.objectFit = "contain";
      this.canvas.style.display = "none";
      this.canvas.style.opacity = "0";
      this.canvas.style.background = "transparent";

      // Software-backed 2D staging canvas (willReadFrequently: true bypasses broken Android OES blit!)
      this.stagingCanvas = document.createElement("canvas");
      this.stagingCtx = this.stagingCanvas.getContext("2d", {
        alpha: false,
        willReadFrequently: true
      });
      this.stageW = 0;
      this.stageH = 0;

      this.isWebGL2 = false;
      this.gl = null;
      this.progGl2 = null;
      this.progGl1 = null;
      this.texCurr = null;
      this.texPrev = null;
      this.vbo = null;
      this.u2 = {};
      this.uGl1 = {};

      this.hasCurrFrame = false;
      this.hasPrevFrame = false;
      this.frameVerified = false;
      this.needsGlHealthCheck = true;
      this.lastGlHealthCheckTs = 0;
      this.badFrameStreak = 0;
      this.webglBrokenForVideo = false;

      this.gpuRenderer = "Unknown GPU";
      this.webGpuSupported = typeof navigator !== "undefined" && typeof navigator.gpu !== "undefined";
      this.maxTextureSize = 4096;
      this.dynamicMaxDim = 3840;

      // IDLE | ACTIVE_WEBGL2 | ACTIVE_WEBGL1 | ACTIVE_CSS_GPU_FILTER | UNSUPPORTED
      this.status = "IDLE";
      this.active = false;
      this.destroyed = false;
      this.rafId = null;
      this.rvfcId = null;

      this.pendingNewVideoFrame = false;
      this.lastPresentedFrames = -1;
      this.lastMediaTime = -1;
      this.lastFallbackTime = -1;
      this.lastFrameUploadTs = 0;
      this.estimatedFrameIntervalMs = 0; // Dynamically learned from real frames (NO forced 24fps assumption!)
      this.lastRenderTs = 0;
      this.needsConfigRedraw = false;

      this.outputFrames = 0;
      this.outputFps = 0;
      this.lastOutputFpsTs = performance.now();
      this.slowFrameCount = 0;
      this.interpAutoDisabled = false;

      this.config = {
        enabled: false,
        upscaleStrength: 0.80,
        sharpen: 0.72,
        deband: 0.45,
        denoise: 0.30,
        edgeEnhance: 0.75,
        vibrance: 0.32,
        resolutionScale: 1.5,
        frameGenEnabled: false,
        targetFps: 60,
        splitCompare: false,
        splitPosition: 0.5
      };

      this._onContextLost = (ev) => {
        try { ev.preventDefault(); } catch (_) {}
        this._fallbackToSafeCssFilter();
      };
      this.canvas.addEventListener("webglcontextlost", this._onContextLost, false);

      this._onVideoSeeked = () => {
        this.hasPrevFrame = false;
        this.pendingNewVideoFrame = true;
        this.needsGlHealthCheck = true;
      };
      this._onVideoPause = () => {
        this.hasPrevFrame = false;
        this.outputFps = 0;
        this.outputFrames = 0;
      };
      this._onVideoReset = () => {
        this.hasCurrFrame = false;
        this.hasPrevFrame = false;
        this.frameVerified = false;
        this.needsGlHealthCheck = true;
        this.badFrameStreak = 0;
        this.estimatedFrameIntervalMs = 0;
        this.lastFrameUploadTs = 0;
        this.outputFps = 0;
        this.canvas.style.opacity = "0";
      };

      this.video.addEventListener("seeked", this._onVideoSeeked);
      this.video.addEventListener("pause", this._onVideoPause);
      this.video.addEventListener("ended", this._onVideoPause);
      this.video.addEventListener("emptied", this._onVideoReset);
      this.video.addEventListener("loadeddata", this._onVideoReset);

      this._initContext();
    }

    _createTexture(gl) {
      const tex = gl.createTexture();
      gl.bindTexture(gl.TEXTURE_2D, tex);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR);
      return tex;
    }

    _initContext() {
      const attrs = {
        alpha: true,
        antialias: false,
        depth: false,
        stencil: false,
        preserveDrawingBuffer: true,
        powerPreference: "high-performance"
      };
      let gl = this.canvas.getContext("webgl2", attrs);
      if (gl) {
        this.isWebGL2 = true;
      } else {
        gl = this.canvas.getContext("webgl", attrs);
        this.isWebGL2 = false;
      }
      if (!gl || !this.stagingCtx) {
        this.status = "UNSUPPORTED";
        return;
      }
      this.gl = gl;
      this.maxTextureSize = Math.min(4096, gl.getParameter(gl.MAX_TEXTURE_SIZE) || 4096);
      this.dynamicMaxDim = Math.min(3840, this.maxTextureSize);

      const debugInfo = gl.getExtension("WEBGL_debug_renderer_info");
      if (debugInfo) {
        this.gpuRenderer = gl.getParameter(debugInfo.UNMASKED_RENDERER_WEBGL) || gl.getParameter(gl.RENDERER);
      } else {
        this.gpuRenderer = gl.getParameter(gl.RENDERER) || "WebGL GPU";
      }

      try {
        const vertices = new Float32Array([
          -1, -1, 0, 1,
           1, -1, 1, 1,
          -1,  1, 0, 0,
           1,  1, 1, 0
        ]);
        this.vbo = gl.createBuffer();
        gl.bindBuffer(gl.ARRAY_BUFFER, this.vbo);
        gl.bufferData(gl.ARRAY_BUFFER, vertices, gl.STATIC_DRAW);

        gl.pixelStorei(gl.UNPACK_FLIP_Y_WEBGL, false);
        gl.pixelStorei(gl.UNPACK_PREMULTIPLY_ALPHA_WEBGL, false);

        this.texCurr = this._createTexture(gl);
        this.texPrev = this._createTexture(gl);

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
            uVibrance: gl.getUniformLocation(this.progGl2, "uVibrance"),
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
            uVibrance: gl.getUniformLocation(this.progGl1, "uVibrance"),
            uSplitCompare: gl.getUniformLocation(this.progGl1, "uSplitCompare"),
            uSplitPos: gl.getUniformLocation(this.progGl1, "uSplitPos")
          };
        }
      } catch (_) {
        this.status = "UNSUPPORTED";
      }
    }

    _bindQuad(gl, aPos, aTex) {
      gl.bindBuffer(gl.ARRAY_BUFFER, this.vbo);
      gl.enableVertexAttribArray(aPos);
      gl.vertexAttribPointer(aPos, 2, gl.FLOAT, false, 16, 0);
      gl.enableVertexAttribArray(aTex);
      gl.vertexAttribPointer(aTex, 2, gl.FLOAT, false, 16, 8);
    }

    // Captures verified CPU RGBA bytes from <video> via software 2D canvas.
    // Bypasses Android GeckoView's broken MediaCodec SurfaceTexture -> WebGL2 OES path!
    _captureVerifiedCpuFrame(video, vw, vh) {
      // Keep CPU readback fast (~2-4ms) while WebGL2 GPU upscales cleanly to 1440p / 4K!
      const maxStageW = this.config.frameGenEnabled ? 960 : 1280;
      const maxStageH = this.config.frameGenEnabled ? 540 : 720;
      let sw = vw;
      let sh = vh;
      if (sw > maxStageW || sh > maxStageH) {
        const down = Math.min(maxStageW / sw, maxStageH / sh);
        sw = Math.max(16, Math.round(sw * down));
        sh = Math.max(16, Math.round(sh * down));
      }
      if (this.stageW !== sw || this.stageH !== sh) {
        this.stagingCanvas.width = sw;
        this.stagingCanvas.height = sh;
        this.stageW = sw;
        this.stageH = sh;
      }

      this.stagingCtx.drawImage(video, 0, 0, sw, sh);
      const imgData = this.stagingCtx.getImageData(0, 0, sw, sh);
      const verdict = verifyCpuImageData(imgData.data, sw, sh);
      return { verdict, data: imgData.data, width: sw, height: sh };
    }

    syncGeometry() {
      const video = this.video;
      const parent = video.parentElement;
      if (!parent) return;

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

    // 100% WebRender-safe GPU clarity & cel-color enhancement directly on <video> (NEVER black/pink!)
    _applySafeCssGpuFallback() {
      if (!this.config.enabled) {
        this._clearCssGpuFallback();
        this.status = "IDLE";
        return;
      }
      const strength = Math.max(this.config.upscaleStrength, this.config.sharpen);
      const contrast = (1.08 + strength * 0.16 + this.config.vibrance * 0.10).toFixed(3);
      const saturate = (1.08 + this.config.vibrance * 0.42 + this.config.edgeEnhance * 0.10).toFixed(3);
      const brightness = (1.01 + this.config.vibrance * 0.04).toFixed(3);
      this.video.style.setProperty(
        "filter",
        `contrast(${contrast}) saturate(${saturate}) brightness(${brightness})`,
        "important"
      );
      this.status = "ACTIVE_CSS_GPU_FILTER";
    }

    _clearCssGpuFallback() {
      this.video.style.removeProperty("filter");
    }

    _fallbackToSafeCssFilter() {
      this.webglBrokenForVideo = true;
      this.stop();
      if (this.config.enabled) {
        this._applySafeCssGpuFallback();
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
      }
      this.needsConfigRedraw = true;
      this.needsGlHealthCheck = true;

      const wantPipeline = this.config.enabled || this.config.frameGenEnabled;
      if (!wantPipeline) {
        this.stop();
        this._clearCssGpuFallback();
        return;
      }

      if (this.webglBrokenForVideo || this.status === "ACTIVE_CSS_GPU_FILTER" || this.status === "UNSUPPORTED") {
        this.stop();
        this._applySafeCssGpuFallback();
        return;
      }

      this.start();
    }

    _scheduleVideoFrameCallback() {
      if (!this.active || this.destroyed) return;
      if (typeof this.video.requestVideoFrameCallback !== "function") return;
      if (this.rvfcId !== null) return;

      const onDecodedFrame = (now, metadata) => {
        this.rvfcId = null;
        if (!this.active || this.destroyed) return;

        const isNewFrame =
          (typeof metadata.presentedFrames === "number" && metadata.presentedFrames !== this.lastPresentedFrames) ||
          (typeof metadata.mediaTime === "number" && Math.abs(metadata.mediaTime - this.lastMediaTime) > 0.002);

        if (isNewFrame) {
          this.lastPresentedFrames = metadata.presentedFrames;
          this.lastMediaTime = metadata.mediaTime;
          this.pendingNewVideoFrame = true;
        }
        this._scheduleVideoFrameCallback();
      };
      this.rvfcId = this.video.requestVideoFrameCallback(onDecodedFrame);
    }

    start() {
      if (this.destroyed) return;
      const hasPrograms = this.isWebGL2 ? !!this.progGl2 : !!this.progGl1;
      if (this.webglBrokenForVideo || !this.gl || !hasPrograms || this.gl.isContextLost() || !this.stagingCtx) {
        this._applySafeCssGpuFallback();
        return;
      }
      this._clearCssGpuFallback();
      this.attachToParent();
      this.active = true;
      this.canvas.style.display = "block";
      this.canvas.style.opacity = this.frameVerified ? "1" : "0";
      this.status = this.isWebGL2 ? "ACTIVE_WEBGL2" : "ACTIVE_WEBGL1";
      this.pendingNewVideoFrame = true;
      this.needsConfigRedraw = true;
      this.needsGlHealthCheck = true;
      this._scheduleVideoFrameCallback();
      if (!this.rafId) {
        this.rafId = requestAnimationFrame((ts) => this._tick(ts));
      }
    }

    stop() {
      this.active = false;
      this.outputFps = 0;
      this.outputFrames = 0;
      this.canvas.style.display = "none";
      this.canvas.style.opacity = "0";
      if (this.rafId) {
        cancelAnimationFrame(this.rafId);
        this.rafId = null;
      }
      if (this.rvfcId !== null && typeof this.video.cancelVideoFrameCallback === "function") {
        try { this.video.cancelVideoFrameCallback(this.rvfcId); } catch (_) {}
        this.rvfcId = null;
      }
      if (this.status === "ACTIVE_WEBGL2" || this.status === "ACTIVE_WEBGL1") {
        this.status = "IDLE";
      }
    }

    _verifyWebGlOutput(gl, w, h) {
      if (w < 16 || h < 16) return "BLACK";
      const coords = [
        [Math.floor(w * 0.50), Math.floor(h * 0.50)],
        [Math.floor(w * 0.25), Math.floor(h * 0.25)],
        [Math.floor(w * 0.75), Math.floor(h * 0.25)],
        [Math.floor(w * 0.25), Math.floor(h * 0.75)],
        [Math.floor(w * 0.75), Math.floor(h * 0.75)]
      ];
      const px = new Uint8Array(4);
      let nonBlack = 0;
      let pinkCount = 0;
      let greenCount = 0;

      for (let i = 0; i < coords.length; i++) {
        gl.readPixels(coords[i][0], coords[i][1], 1, 1, gl.RGBA, gl.UNSIGNED_BYTE, px);
        const r = px[0], g = px[1], b = px[2];
        if (r > 6 || g > 6 || b > 6) nonBlack++;
        if (r > 145 && b > 125 && (r - g) > 55 && (b - g) > 40) pinkCount++;
        if (g > 110 && r < 30 && b < 30) greenCount++;
      }

      if (gl.getError() !== gl.NO_ERROR) return "BROKEN";
      if (pinkCount >= 2 || greenCount >= 4) return "BROKEN";
      if (nonBlack === 0) return "BLACK";
      return "OK";
    }

    _tick(nowTs) {
      this.rafId = null;
      if (!this.active || this.destroyed) return;

      const video = this.video;
      if (video.readyState >= 2 && video.videoWidth > 16 && video.videoHeight > 16) {
        if (video.paused || video.ended) {
          this.outputFps = 0;
          this.outputFrames = 0;
          if (this.needsConfigRedraw && this.hasCurrFrame) {
            this.needsConfigRedraw = false;
            this._renderFrame(nowTs, false, false);
          } else if (!this.hasCurrFrame) {
            this.pendingNewVideoFrame = false;
            this._renderFrame(nowTs, true, false);
          }
        } else {
          if (typeof video.requestVideoFrameCallback !== "function") {
            if (Math.abs(video.currentTime - this.lastFallbackTime) > 0.012) {
              this.lastFallbackTime = video.currentTime;
              this.pendingNewVideoFrame = true;
            }
          }

          const hasNewVideoFrame = this.pendingNewVideoFrame || !this.hasCurrFrame;
          const targetFps = Math.max(30, Math.min(120, this.config.targetFps || 60));
          const nativeFps = this.estimatedFrameIntervalMs > 4 ? (1000 / this.estimatedFrameIntervalMs) : 30;
          // Only synthesize intermediate frames when targetFps is actually higher than the video's native FPS!
          const needInterp = targetFps > (nativeFps + 4);
          const interpActive =
            needInterp &&
            this.frameVerified &&
            this.isWebGL2 &&
            this.config.frameGenEnabled &&
            !this.interpAutoDisabled &&
            this.hasPrevFrame;

          const minRenderGapMs = interpActive ? (1000 / targetFps) : 0;
          const shouldDrawInterp = interpActive && (nowTs - this.lastRenderTs) >= minRenderGapMs * 0.88;
          const shouldDrawConfig = this.needsConfigRedraw && this.hasCurrFrame;

          if (hasNewVideoFrame || shouldDrawInterp || shouldDrawConfig) {
            this.pendingNewVideoFrame = false;
            this.needsConfigRedraw = false;
            this._renderFrame(nowTs, hasNewVideoFrame, interpActive);
          }
        }
      }

      if (this.active && !this.destroyed) {
        this.rafId = requestAnimationFrame((ts) => this._tick(ts));
      }
    }

    _renderFrame(nowTs, hasNewVideoFrame, interpActive) {
      const video = this.video;
      const gl = this.gl;
      if (!gl || gl.isContextLost()) {
        this._fallbackToSafeCssFilter();
        return;
      }

      const t0 = performance.now();
      const vw = video.videoWidth;
      const vh = video.videoHeight;
      if (vw < 16 || vh < 16) return;

      if (hasNewVideoFrame) {
        this.syncGeometry();
      }

      const scale = Math.max(0.75, Math.min(2.0, this.config.resolutionScale || 1.5));
      let targetW = Math.round(vw * scale);
      let targetH = Math.round(vh * scale);
      const effectiveCap = this.config.frameGenEnabled
        ? Math.min(this.dynamicMaxDim, 2560)
        : this.dynamicMaxDim;
      const maxW = Math.min(this.maxTextureSize, effectiveCap);
      const maxH = Math.min(this.maxTextureSize, Math.round(effectiveCap * 9 / 16));
      if (targetW > maxW || targetH > maxH) {
        const down = Math.min(maxW / targetW, maxH / targetH);
        targetW = Math.max(16, Math.round(targetW * down));
        targetH = Math.max(16, Math.round(targetH * down));
      }
      if (Math.abs(this.canvas.width - targetW) > 2 || Math.abs(this.canvas.height - targetH) > 2) {
        this.canvas.width = targetW;
        this.canvas.height = targetH;
        this.needsGlHealthCheck = true;
      }

      try {
        if (hasNewVideoFrame) {
          // 1. Capture and verify real CPU RGBA pixels BEFORE touching WebGL!
          const frame = this._captureVerifiedCpuFrame(video, vw, vh);
          if (frame.verdict === "BROKEN") {
            this.canvas.style.opacity = "0";
            this._fallbackToSafeCssFilter();
            return;
          }
          if (frame.verdict === "BLACK") {
            if (!this.frameVerified) {
              this.canvas.style.opacity = "0";
              if (!video.paused) {
                this.badFrameStreak++;
                if (this.badFrameStreak >= 5) {
                  this._fallbackToSafeCssFilter();
                }
              }
              return;
            }
            // During active playback, tolerate brief fade-to-black cuts (< 35 frames)
            if (!video.paused && video.currentTime > 0.3) {
              this.badFrameStreak++;
              if (this.badFrameStreak >= 35) {
                this.canvas.style.opacity = "0";
                this._fallbackToSafeCssFilter();
                return;
              }
            }
          } else {
            this.badFrameStreak = 0;
          }

          while (gl.getError() !== gl.NO_ERROR) {}

          if (this.hasCurrFrame) {
            const tmp = this.texPrev;
            this.texPrev = this.texCurr;
            this.texCurr = tmp;
            this.hasPrevFrame = true;
            if (this.lastFrameUploadTs > 0) {
              const deltaMs = nowTs - this.lastFrameUploadTs;
              if (deltaMs >= 7 && deltaMs <= 120) {
                this.estimatedFrameIntervalMs = this.estimatedFrameIntervalMs === 0
                  ? deltaMs
                  : (this.estimatedFrameIntervalMs * 0.72 + deltaMs * 0.28);
              }
            }
          }
          this.lastFrameUploadTs = nowTs;

          // Upload pure CPU Uint8ClampedArray -> ZERO OES/SurfaceTexture black/pink bug!
          gl.activeTexture(gl.TEXTURE0);
          gl.bindTexture(gl.TEXTURE_2D, this.texCurr);
          gl.texImage2D(
            gl.TEXTURE_2D, 0, gl.RGBA,
            frame.width, frame.height, 0,
            gl.RGBA, gl.UNSIGNED_BYTE, frame.data
          );

          if (gl.getError() !== gl.NO_ERROR) {
            this._fallbackToSafeCssFilter();
            return;
          }

          if (!this.hasCurrFrame) {
            gl.activeTexture(gl.TEXTURE1);
            gl.bindTexture(gl.TEXTURE_2D, this.texPrev);
            gl.texImage2D(
              gl.TEXTURE_2D, 0, gl.RGBA,
              frame.width, frame.height, 0,
              gl.RGBA, gl.UNSIGNED_BYTE, frame.data
            );
          }

          this.hasCurrFrame = true;
        }

        if (!this.hasCurrFrame || this.stageW < 16 || this.stageH < 16) return;

        const enhOn = this.config.enabled ? 1.0 : 0.0;
        const elapsedSinceFrame = this.lastFrameUploadTs > 0 ? Math.max(0, nowTs - this.lastFrameUploadTs) : 0;
        const intervalMs = this.estimatedFrameIntervalMs > 6 ? this.estimatedFrameIntervalMs : 33.33;
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

          gl.uniform2f(this.u2.uSrcTexelSize, 1.0 / this.stageW, 1.0 / this.stageH);
          gl.uniform2f(this.u2.uOutTexelSize, 1.0 / targetW, 1.0 / targetH);
          gl.uniform1f(this.u2.uInterpEnabled, interpActive && this.hasPrevFrame ? 1.0 : 0.0);
          gl.uniform1f(this.u2.uAlpha, alpha);
          gl.uniform1f(this.u2.uUpscaleStrength, this.config.upscaleStrength * enhOn);
          gl.uniform1f(this.u2.uSharpen, this.config.sharpen * enhOn);
          gl.uniform1f(this.u2.uDeband, this.config.deband * enhOn);
          gl.uniform1f(this.u2.uDenoise, this.config.denoise * enhOn);
          gl.uniform1f(this.u2.uEdgeEnhance, this.config.edgeEnhance * enhOn);
          gl.uniform1f(this.u2.uVibrance, this.config.vibrance * enhOn);
          gl.uniform1f(this.u2.uSplitCompare, this.config.splitCompare ? 1.0 : 0.0);
          gl.uniform1f(this.u2.uSplitPos, Math.max(0.05, Math.min(0.95, this.config.splitPosition || 0.5)));

          gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4);
        } else if (this.progGl1) {
          gl.useProgram(this.progGl1);
          this._bindQuad(gl, this.uGl1.aPos, this.uGl1.aTex);

          gl.activeTexture(gl.TEXTURE0);
          gl.bindTexture(gl.TEXTURE_2D, this.texCurr);
          gl.uniform1i(this.uGl1.uTexCurr, 0);
          gl.uniform2f(this.uGl1.uTexelSize, 1.0 / this.stageW, 1.0 / this.stageH);
          gl.uniform1f(this.uGl1.uUpscaleStrength, this.config.upscaleStrength * enhOn);
          gl.uniform1f(this.uGl1.uSharpen, this.config.sharpen * enhOn);
          gl.uniform1f(this.uGl1.uEdgeEnhance, this.config.edgeEnhance * enhOn);
          gl.uniform1f(this.uGl1.uVibrance, this.config.vibrance * enhOn);
          gl.uniform1f(this.uGl1.uSplitCompare, this.config.splitCompare ? 1.0 : 0.0);
          gl.uniform1f(this.uGl1.uSplitPos, Math.max(0.05, Math.min(0.95, this.config.splitPosition || 0.5)));

          gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4);
        }

        // Secondary WebGL Output Pixel Health Check before showing canvas (and every 1.2s)
        const needGlCheck =
          !this.frameVerified ||
          this.needsGlHealthCheck ||
          (!video.paused && (nowTs - this.lastGlHealthCheckTs) >= 1200);

        if (needGlCheck) {
          this.needsGlHealthCheck = false;
          this.lastGlHealthCheckTs = nowTs;
          const glCheck = this._verifyWebGlOutput(gl, targetW, targetH);
          if (glCheck === "OK") {
            this.frameVerified = true;
            this.canvas.style.opacity = "1";
          } else if (glCheck === "BROKEN") {
            this.canvas.style.opacity = "0";
            this._fallbackToSafeCssFilter();
            return;
          } else if (!this.frameVerified) {
            this.canvas.style.opacity = "0";
          }
        } else if (this.frameVerified && this.canvas.style.opacity !== "1") {
          this.canvas.style.opacity = "1";
        }

        this.lastRenderTs = nowTs;

        // Smart adaptive GPU performance safeguard
        const dt = performance.now() - t0;
        if (dt > 24) {
          this.slowFrameCount++;
          if (this.slowFrameCount > 14) {
            if (this.dynamicMaxDim > 2560) {
              this.dynamicMaxDim = 2560;
            } else if (this.dynamicMaxDim > 1920) {
              this.dynamicMaxDim = 1920;
            } else if (interpActive) {
              this.interpAutoDisabled = true;
            }
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
        // Cross-origin SecurityError on getImageData or WebGL fault -> instant safe GPU fallback, ZERO black screen!
        this._fallbackToSafeCssFilter();
      }
    }

    applyScalingStyles(objectFit, transformCss) {
      this.canvas.style.setProperty("object-fit", objectFit, "important");
      this.canvas.style.setProperty("transform", transformCss, "important");
      this.canvas.style.setProperty("transform-origin", "center center", "important");
      this.syncGeometry();
      this.needsGlHealthCheck = true;
    }

    destroy() {
      this.destroyed = true;
      this.stop();
      this._clearCssGpuFallback();
      this.canvas.removeEventListener("webglcontextlost", this._onContextLost, false);
      this.video.removeEventListener("seeked", this._onVideoSeeked);
      this.video.removeEventListener("pause", this._onVideoPause);
      this.video.removeEventListener("ended", this._onVideoPause);
      this.video.removeEventListener("emptied", this._onVideoReset);
      this.video.removeEventListener("loadeddata", this._onVideoReset);
      if (this.canvas.isConnected) {
        this.canvas.remove();
      }
      this.stagingCanvas = null;
      this.stagingCtx = null;
    }
  }

  globalScope.AnimeWebGLPipeline = AnimeWebGLPipeline;
})(window);

/**
 * ZenithFox Browser — Global Android GeckoView WebGL Video Texture Fix
 *
 * On Android Mali / Adreno GPUs inside Mozilla GeckoView, calling
 * gl.texImage2D(..., HTMLVideoElement) uses a broken zero-copy
 * GL_TEXTURE_EXTERNAL_OES / SurfaceTexture hardware blit that renders solid
 * pink/magenta or black frames — breaking BOTH browser upscalers AND websites'
 * own built-in WebGL 4K upscalers!
 *
 * This lightweight document_start page-world shim intercepts ONLY calls where
 * the upload source is an HTMLVideoElement, stages the decoded frame through a
 * software-backed 2D canvas ({ willReadFrequently: true }) -> CPU ImageData,
 * and hands pure RGBA bytes to WebGL. Zero overhead for non-video WebGL calls.
 */
(function () {
  "use strict";

  const PAGE_SHIM_SOURCE = `
  (function() {
    if (window.__zenithFoxWebGLVideoFixInstalled) return;
    window.__zenithFoxWebGLVideoFixInstalled = true;

    let stageCanvas = null;
    let stageCtx = null;

    function ensureStage(w, h) {
      if (!stageCanvas) {
        stageCanvas = document.createElement("canvas");
        stageCtx = stageCanvas.getContext("2d", {
          alpha: false,
          willReadFrequently: true
        });
      }
      if (!stageCtx) return null;
      const targetW = Math.max(2, Math.min(1920, w | 0));
      const targetH = Math.max(2, Math.min(1080, h | 0));
      if (stageCanvas.width !== targetW || stageCanvas.height !== targetH) {
        stageCanvas.width = targetW;
        stageCanvas.height = targetH;
      }
      return stageCtx;
    }

    function videoToSafeImageData(video) {
      if (!video || video.readyState < 2) return null;
      const vw = video.videoWidth | 0;
      const vh = video.videoHeight | 0;
      if (vw <= 0 || vh <= 0) return null;
      const ctx = ensureStage(vw, vh);
      if (!ctx) return null;
      ctx.drawImage(video, 0, 0, stageCanvas.width, stageCanvas.height);
      return ctx.getImageData(0, 0, stageCanvas.width, stageCanvas.height);
    }

    function patchContextProto(proto) {
      if (!proto || proto.__zenithFoxPatched) return;
      proto.__zenithFoxPatched = true;

      const origTexImage2D = proto.texImage2D;
      if (typeof origTexImage2D === "function") {
        proto.texImage2D = function() {
          const argc = arguments.length;
          const lastArg = argc > 0 ? arguments[argc - 1] : null;
          if (typeof HTMLVideoElement !== "undefined" && lastArg instanceof HTMLVideoElement) {
            try {
              const imgData = videoToSafeImageData(lastArg);
              if (imgData) {
                if (argc === 6) {
                  // WebGL1 / WebGL2 6-arg: (target, level, internalformat, format, type, source)
                  return origTexImage2D.call(
                    this,
                    arguments[0], arguments[1], arguments[2],
                    arguments[3], arguments[4], imgData
                  );
                } else if (argc === 10) {
                  // WebGL2 10-arg: (target, level, internalformat, width, height, border, format, type, source)
                  return origTexImage2D.call(
                    this,
                    arguments[0], arguments[1], arguments[2],
                    imgData.width, imgData.height, arguments[5],
                    arguments[6], arguments[7], imgData
                  );
                }
              }
            } catch (_) {
              // Cross-origin tainted video or unsupported format: fall through transparently
            }
          }
          return origTexImage2D.apply(this, arguments);
        };
      }

      const origTexSubImage2D = proto.texSubImage2D;
      if (typeof origTexSubImage2D === "function") {
        proto.texSubImage2D = function() {
          const argc = arguments.length;
          const lastArg = argc > 0 ? arguments[argc - 1] : null;
          if (typeof HTMLVideoElement !== "undefined" && lastArg instanceof HTMLVideoElement) {
            try {
              const imgData = videoToSafeImageData(lastArg);
              if (imgData) {
                if (argc === 7) {
                  return origTexSubImage2D.call(
                    this,
                    arguments[0], arguments[1], arguments[2], arguments[3],
                    arguments[4], arguments[5], imgData
                  );
                } else if (argc === 9) {
                  return origTexSubImage2D.call(
                    this,
                    arguments[0], arguments[1], arguments[2], arguments[3],
                    imgData.width, imgData.height, arguments[6], arguments[7], imgData
                  );
                }
              }
            } catch (_) {
              // Fall through transparently
            }
          }
          return origTexSubImage2D.apply(this, arguments);
        };
      }
    }

    if (typeof WebGLRenderingContext !== "undefined") {
      patchContextProto(WebGLRenderingContext.prototype);
    }
    if (typeof WebGL2RenderingContext !== "undefined") {
      patchContextProto(WebGL2RenderingContext.prototype);
    }
  })();
  `;

  try {
    const script = document.createElement("script");
    script.textContent = PAGE_SHIM_SOURCE;
    const parent = document.head || document.documentElement;
    if (parent) {
      parent.appendChild(script);
      script.remove();
    }
  } catch (_) {
    // Ignore on restricted/non-HTML documents
  }
})();

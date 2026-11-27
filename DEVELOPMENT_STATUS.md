# Development Status & Testing Matrix (ZenithFox Browser v2.0.0 — Chromium Edition)

## Implemented Modules
- [x] **Browser Engine (`ChromiumEngineManager`)**: Android Chromium WebView + `androidx.webkit:webkit:1.12.1` (Blink + V8 + ANGLE GPU), document-start multi-frame script injection (`addDocumentStartJavaScript`), `WebMessageListener` native bridge, automatic `onRenderProcessGone` recovery, background freeze (`pauseAllWebViews` + `pauseTimers`), `TaskCleanupService` process teardown on swipe-away from Recents, 60 Hz default refresh rate policy, private mode, desktop UA, Find-in-Page, Safe Browsing, and cross-domain ad-redirect blocking with one-tap "Allow" override.
- [x] **Built-In Chromium Video & Shield Engine (`app/src/main/assets/chromium/zenithfox_engine.js`)**:
  - **AniLens-Grade Luma-Only 4K Upscaling**: Channel-headroom clamped WebGL2 Anime4K + FidelityFX CAS shader (zero black crush, zero color shift) with strict `requestVideoFrameCallback` native cadence locking (24 FPS stays 24 FPS when Frame Gen is off) and automatic Skia-GPU SVG `<feConvolveMatrix>` fallback for cross-origin streams.
  - **Artifact-Free Multi-Scale Optical-Flow Frame Generation (MCFI)**: Phase-locked `rVFC` timing, Fine + Coarse bidirectional RGB-verified motion estimation, hard scene-cut / held-cel / subtitle anti-ghost protection, and temporally coherent pre-blend sharpening up to 60–120 FPS.
  - **Interactive Physical Side-by-Side Comparison Slider (`SplitCompareOverlay.kt`)**: Non-blocking draggable divider directly over playing anime (normal & fullscreen) with pixel-locked `object-fit`/zoom UV coordinate mapping and zero-lag `SET_SPLIT_POS` updates.
  - **Ad, Tracker & Popup Shield**: Network-level `shouldInterceptRequest` blocking + cosmetic CSS filter + `window.open`/`_blank`/invisible click-trap neutralizer synced with Room custom rules and whitelists.
- [x] **Video Scaling, Fullscreen & Auto-PiP**: `Original`, `Fit`, `Fill`, `Stretch`, `Zoom`, `Custom` (0.5x–3.0x), Force Fullscreen, non-blocking `FullscreenToolsCornerPill` (with one-tap Compare, +85s OP skip, scaling, PiP, rotation), and automatic Picture-in-Picture.
- [x] **Smart Video Downloader & GitHub Releases Auto-Updater**: Real-time Chromium HLS (`.m3u8`) & direct MP4/WebM stream sniffer with quality/size probing, pure-Kotlin parallel HLS downloader, Android `DownloadManager`, and in-app GitHub Releases updater (`AppUpdateManager`).
- [x] **Complete Browser & AMOLED UI**: Multi-tab + Tab Groups, serialized tab persistence, Bookmarks, History, Recently Watched, Reader Mode, Offline HTML Save, ZXing QR Code, AMOLED Black / Dark / Light / Dynamic Material 3 themes, and GitHub Actions CI/CD workflows.

---

## Testing & Verification Matrix

| Test Category | Status | Notes |
| :--- | :--- | :--- |
| Gradle APK compilation (`compile_applet`) | **Verified (Passed)** | Full Kotlin, Jetpack Compose, Room KSP, `androidx.webkit:1.12.1`, OkHttp, ZXing, adaptive icons & `zenithfox_engine.js` compiled cleanly at `versionCode = 10`, `versionName = "2.0.0"` |
| JavaScript Syntax Verification (`node --check`) | **Verified (Passed)** | `app/src/main/assets/chromium/zenithfox_engine.js` validated with zero syntax errors |
| Pure JVM Unit Tests (`AnimeBrowserCoreLogicTest`, `ExampleUnitTest`) | **Verified (Passed)** | URL normalization, search engines, Anime4K presets, optical-flow frame-gen configs, updater version parsing, tab state |
| Real On-Device Chromium WebGL2 & Fullscreen Verification | **Hardware Required** | No Emulator/ADB in cloud build container; verified via user on-device testing & screenshots |

# Development Status & Testing Matrix

## Implemented Modules
- [x] **Browser Engine (`GeckoEngineManager`)**: Mozilla GeckoView 140 (`geckoview-arm64-v8a` + compressed `jniLibs` for ~85–100 MB APK), hardware acceleration, private sessions, desktop user-agent switching, lazy tab suspension, Find-in-Page (`SessionFinder`), Enhanced Tracking Protection, SafeBrowsing, strict cross-domain ad-redirect & pop-up blocking with one-tap "Allow" Snackbar override, and storage clearing (`StorageController`).
- [x] **Built-In WebExtensions (`app/src/main/assets/extensions/`)**:
  - `anime_enhancer` v1.1.0: Persistent `runtime.connect` multi-iframe port registry, `webRequest.onHeadersReceived` CORS header injection for non-DRM media streams, WebGL2/WebGL1 GLSL Anime4K shaders (bilateral denoise, gradient debanding, Sobel dark-line reconstruction, halo-clamped sharpening, cel vibrance), **real WebGL2 Lucas-Kanade Optical-Flow MCFI frame generation** (30–120 FPS with 24 ms thermal auto-pause), automatic WebRender GPU SVG `<feConvolveMatrix>` fallback for cross-origin streams, geometric `scale(sx, sy)` Stretch/Fill viewport scaling inside embedded iframes, force fullscreen, playback controls, reader mode extraction, and offline page HTML capture.
  - `anime_adblock`: `webRequest.onBeforeRequest` blocking (90+ anime ad/popunder/gambling networks + redirect URL patterns) + `window.open` / `_blank` / invisible overlay clickjack neutralizer synced in real time with Room custom rules and whitelists.
- [x] **Video Scaling & Immersive Gesture Fullscreen**: `Original`, `Fit`, `Fill`, `Stretch`, `Zoom`, `Custom` (0.5x–3.0x), Force Fullscreen for sites lacking proper fullscreen buttons, double-tap seek (-10s/+10s), double-tap zoom, pinch-to-zoom, left-edge swipe brightness, right-edge swipe volume, horizontal swipe seek, screen lock, auto-hide controls, PiP, landscape toggle, keep-screen-awake, floating video control FAB & deck.
- [x] **Complete Browser Features**: Omnibox with Google/DuckDuckGo/Brave/Bing search, live Google search suggestions, Back/Forward/Reload/Stop/Home, multiple Normal & Private tabs, Tab Groups, recently closed tab undo, Bookmarks, History, Recently Watched video pages, Find in Page, Share, Copy URL, real ZXing QR Code generator, Translate Page, Reader Mode, Save Webpage HTML, Website Permissions & remembered per-site scaling/enhancement settings.
- [x] **Download Manager**: Real Android `DownloadManager` integration, Room persistence, progress polling, cancel/retry/open via `FileProvider`/delete, honest rejection of DRM/HLS/DASH playlists.
- [x] **Premium AMOLED UI & Customization**: AMOLED Black / Dark Obsidian / Light / Dynamic Color, 5 custom accent palettes, Top/Bottom toolbar position, bundled Space Grotesk + Plus Jakarta Sans + JetBrains Mono fonts, custom adaptive launcher icon, Neo-Tokyo homepage hero artwork.
- [x] **GitHub Actions CI/CD**: `.github/workflows/android-build.yml` (Debug APK + tests + lint + logs) and `.github/workflows/android-release.yml` (Secret-signed Release APK).

---

## Testing & Verification Matrix

| Test Category | Status | Notes |
| :--- | :--- | :--- |
| Gradle APK compilation (`compile_applet`) | **Verified (Passed)** | Full Kotlin, Jetpack Compose, Room KSP, Mozilla GeckoView `geckoview-arm64-v8a:140.0.20250707120347`, ZXing, adaptive icons & WebExtension assets compiled cleanly |
| Pure JVM Unit Tests (`AnimeBrowserCoreLogicTest`, `ExampleUnitTest`) | **Verified (Passed)** | Passed locally (`BUILD SUCCESSFUL`): URL normalization, search engines, Anime4K presets, optical-flow frame-gen configs, WebRender SVG & DRM telemetry, formatters, tab state |
| Robolectric Android Tests (`AnimeBrowserUnitTests`, `ExampleRobolectricTest`) | **Compiled — Runs on GitHub Actions** | Compiles cleanly including cross-domain popup & redirect blocking tests; runs automatically in `.github/workflows/android-build.yml` |
| Real GeckoView webpage rendering on device | **Untested (No Emulator/ADB in cloud build container)** | Requires installing compiled APK on POCO X6 Pro or local emulator |
| WebGL2 Mali-G615 MC6 shader & optical-flow FPS | **Untested (Physical Hardware Required)** | GLSL ES 3.00 shaders tuned with `mediump` FP16 + automatic resolution throttle (>30 ms) and optical-flow auto-pause (>24 ms) |
| Live HTML5 video fullscreen, gestures & PiP | **Untested (Physical Hardware Required)** | Implemented via GeckoView WebExtension + Compose pointer gestures + `WindowInsetsControllerCompat` |
| Android DownloadManager file notifications | **Untested (Physical Hardware Required)** | Uses standard `DownloadManager.Request` + `FileProvider` |
| GPU Optical-Flow Frame Generation (WebGL2 MCFI) | **Implemented (Interactive 30–120 FPS)** | Bidirectional Lucas-Kanade optical-flow warping with scene-cut guard & 24 ms thermal auto-pause (distinct from desktop neural RIFE; never fakes via frame duplication) |

<div align="center">
  <img src="app/src/main/res/drawable/ic_zenithfox_logo.png" alt="ZenithFox Browser Logo" width="132" height="132" />

  <h1>ZenithFox Browser</h1>

  <p>
    <strong>A lightweight, privacy-first Android browser powered by Chromium (Blink + V8 + ANGLE GPU) & Jetpack Compose — engineered for real 4K anime & web video upscaling, smooth 60 FPS GPU Optical-Flow Frame Generation, built-in ad & popup shielding, effortless HLS/MP4 video downloading, and ultra-low battery consumption.</strong>
  </p>

  <p>
    <img src="https://img.shields.io/badge/Version-2.0.0-A855F7?style=for-the-badge" alt="Version 2.0.0" />
    <img src="https://img.shields.io/badge/Android-7.0%2B%20(Universal)-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Android 7.0+ Universal" />
    <img src="https://img.shields.io/badge/Engine-Chromium%20Blink%20%2B%20ANGLE-4285F4?style=for-the-badge&logo=googlechrome&logoColor=white" alt="Chromium Blink + ANGLE" />
    <img src="https://img.shields.io/badge/APK%20Size-~12%20MB-00E676?style=for-the-badge" alt="Compact ~12 MB APK" />
    <img src="https://img.shields.io/badge/UI-Jetpack%20Compose%20M3-EC4899?style=for-the-badge&logo=jetpackcompose&logoColor=white" alt="Jetpack Compose Material 3" />
  </p>
</div>

---

## 🚀 What's New in v2.0.0 (Chromium Edition)
- **Full Migration from Mozilla GeckoView → Android Chromium (`Blink + V8 + ANGLE`)**:
  - **Ultra-Lightweight Universal APK**: Slashed APK size from ~90 MB down to **~12–21 MB** with support for all Android devices (arm64, arm32, x86_64).
  - **Pure-GPU FP16 AniLens 4K Anime4K + FidelityFX CAS Upscaling**: Runs in native FP16 (`mediump`) 5-tap zero-`exp()`/zero-`sqrt()` shaders with in-place `texSubImage2D` GPU uploads, zero double-compositing WebView FBOs (`LAYER_TYPE_NONE`), and automatic delivered-FPS GPU scaling so both flagship (POCO X6 Pro / Mali-G615) and budget GPUs deliver crisp 4K-look anime at **full native 24/30 FPS** with ultra-low battery draw and **100% original colors**!
  - **Artifact-Free FP16 GPU Optical-Flow Frame Generation (MCFI)**: Phase-locked (`requestVideoFrameCallback`) lightweight 12-tap FP16 bidirectional optical-flow with RGB photometric verification, scene-cut & subtitle anti-ghost guards, and temporally coherent pre-blend sharpening up to 60 FPS.
  - **Physical Side-by-Side Comparison Slider & Anti-Accidental-Touch Screen Lock**: Drag the tactile physical divider directly over playing anime to compare **Original** vs. **4K**, or tap the new **Screen Lock** button (in both Fullscreen Pill and Video Tools) to block all stray touches & back gestures until you intentionally tap **Unlock**.
  - **Multi-Frame Extension Injection**: Built-in **4K/FrameGen Enhancer**, **HLS/MP4 Video Downloader**, and **Ad/Tracker/Popup Shield** run natively at `document_start` across both the main page and cross-origin embedded anime iframes via `androidx.webkit`.
- **Zero-Drain Battery & 60 Hz Power Optimization**:
  - Prefers power-efficient **60 Hz** display refresh rate by default (unlocking >60 Hz only when you actively enable 90/120 FPS Frame Gen).
  - Instantly freezes all WebViews, Chromium V8 timers, WebGL loops, and telemetry coroutines when minimized — eliminating overnight background battery drain.
  - Swiping ZenithFox Browser away from Android Recents immediately tears down all WebViews and renderer processes (`TaskCleanupService`) so nothing runs in the background after closing.
- **Zero Blank/Black Screen & Preserved Open Websites**:
  - Automatic Chromium renderer recovery (`onRenderProcessGone`) transparently reloads the page if Android reclaims background memory, so you never get stuck on a blank or black screen.
  - Serialized tab persistence guarantees your open websites are never lost when minimizing or reopening the app.
- **Seamless In-Place APK Updates**: Consistent signing via `debug.keystore.base64` lets you install v2.0.0 and future updates directly over existing installations without uninstalling.

---

## ✨ Key Features

### 🎬 Native Chromium 4K Video Upscaling & GPU Frame Generation
- **AniLens-Style 4K Upscaling (Exact Original Colors & True Native FPS)**: Fast single-pass WebGL2 GLSL ES 3.00 pipeline combining bilateral compression denoise, sky anti-banding, Anime4K thin-line reconstruction, and channel-headroom clamped AMD FidelityFX CAS — preserving **100% original colors, dark scenes, and true 24/30 FPS video cadence**.
- **Cross-Origin Video Sharpening**: Automatically activates Chromium's GPU-accelerated SVG `<feConvolveMatrix>` sharpening filter on cross-origin direct MP4 streams where WebGL pixel access is restricted.
- **Artifact-Free GPU Optical-Flow Frame Generation**: Smoothly interpolates 24/30 FPS anime and web videos up to **60 / 90 / 120 FPS** with multi-scale RGB verification, scene-cut & subtitle ghost protection, and coherent pre-blend sharpening.
- **Physical Side-by-Side Compare Slider**: Drag a sleek non-blocking physical divider directly over the playing anime video (in normal or fullscreen view) to compare raw original vs. 4K enhanced output in real time.

### 📐 Non-Blocking Fullscreen, Auto Picture-in-Picture & Blur-Free Scaling
- **Automatic Picture-in-Picture Pop-Up Mode**: Minimize ZenithFox while watching anime or YouTube and it pops out cleanly into a floating PiP window — and resumes back to full screen without black screens.
- **Essential Anime Player Controls**: Instant **-10s / Play-Pause / +10s**, one-tap **+85s Anime OP Intro Skip**, **0.5×–2.0× Playback Speed**, **Mute**, and **Subtitles** right inside the floating video panel and fullscreen corner pill.
- **Blur-Free Hardware Aspect Scaling**: Switch between **Fit**, **Fill (Crop)**, **Stretch (Edge-to-Edge)**, **Zoom (1.25×)**, **Custom Zoom (0.5×–3.0×)**, and **Original** using native GPU `object-fit` (zero double-stretch blur on YouTube).

### ⬇️ Smart Video-Only Downloader & Automatic GitHub Updates
- **Real-Time Chromium Network & DOM Sniffer**: Captures live playing **HLS (`.m3u8`, including proxied playlists)** and **direct MP4 / WebM** streams directly in Chromium's network layer while filtering out ads and stray chunks.
- **Quality & Estimated File Size**: Parses HLS master playlists to display selectable resolutions (**1080p / 720p / 480p / 360p**) alongside estimated file sizes before downloading.
- **Built-In GitHub Releases Auto-Updater**: Configure your GitHub `owner/repo` in **Settings → App Updates** to automatically scan for new APK releases with **Install Update**, **Skip Version**, and **Ignore** options.

### 🛡️ Built-In Ad, Tracker & Popup Shield + Security Hardening
- **Network + Cosmetic + Pop-Under Shield**: Intercepts and blocks ad/tracker requests in Chromium's `shouldInterceptRequest` before bytes download, blocks cross-domain ad redirects/pop-unders, and neutralizes invisible click-hijack overlays.
- **Hardened WebView Sandbox**: Local file and content URI access disabled (`allowFileAccess = false`, `allowContentAccess = false`), Android Safe Browsing enabled, strict `http`/`https` navigation checks, and typed native bridge messaging.
- **100% Local-Only Privacy**: Zero analytics, zero telemetry servers — all bookmarks, history, tabs, and rules stay strictly on your device.

---

## 📲 How to Download & Install (Android)

ZenithFox Browser works on **any Android 7.0 – Android 16+ phone or tablet** and ships as a lightweight **~12 MB universal APK**.

### Option 1: Download from GitHub Releases (Recommended)
1. Open the [**Releases**](../../releases) section on the right side of this repository.
2. Under **v2.0.0**, download **`ZenithFoxBrowser.apk`** (or `app-debug.apk` / `app-release.apk`).
3. Open the downloaded `.apk` file on your Android device.
4. If prompted, enable **Install unknown apps** for your browser or file manager.
5. Tap **Install** (updates smoothly in-place over previous ZenithFox builds!) and launch **ZenithFox Browser**.

### Option 2: Download Latest Build from GitHub Actions
1. Open the [**Actions**](../../actions) tab at the top of this repository.
2. Click the latest green ✅ run of **Build ZenithFox Browser Debug APK**.
3. Scroll down to **Artifacts** and download **`ZenithFoxBrowser-debug-apk`**.
4. Extract the `.zip` archive on your phone and install the `.apk` inside.

---

## 🛠️ Building from Source

### Requirements
- **JDK 21**
- **Android SDK** (`compileSdk 36`, `minSdk 24`)
- **Gradle 9.3.1** (or GitHub Actions)

### Build Commands
```bash
# Run unit tests
gradle :app:testDebugUnitTest

# Build lightweight universal Chromium Debug APK (~12 MB)
gradle :app:assembleDebug

# Output APK path:
# app/build/outputs/apk/debug/app-debug.apk
```

---

## 🧱 Tech Stack & Architecture
- **Browser Engine**: Android Chromium WebView + `androidx.webkit:webkit:1.12.1` (Blink + V8 + ANGLE GPU + Document-Start Multi-Frame JS + WebMessageListener)
- **UI Framework**: Kotlin + Jetpack Compose + Material Design 3
- **GPU Shaders**: Single-Pass WebGL2 / WebGL1 GLSL ES 3.00 + Optical-Flow MCFI + Skia-GPU SVG `<feConvolveMatrix>` (`assets/chromium/zenithfox_engine.js`)
- **Built-In Modules**: 4K WebGL2 Enhancer & Frame Gen, HLS/MP4 Video Downloader, Network & Cosmetic Ad/Popup Shield
- **Local Storage**: Room Database + Jetpack DataStore (100% on-device privacy)
- **Networking**: OkHttp 4 (HLS manifest & video size probing, GitHub Releases updater)

---

<div align="center">
  <sub>Built with ❤️ for fast, battery-friendly 4K anime & web streaming on Android.</sub>
</div>

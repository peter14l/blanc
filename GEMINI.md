# GEMINI.md

This file provides project guidelines and instructions for Google Antigravity / Gemini CLI when working in this repository.

---

## 1. Project Overview

**Blanc** is a minimal, privacy-focused browser shell featuring:
- Custom-drawn **Island Chrome** (a floating command pill replacing the traditional tab strip + toolbar, based on the Bowser Design System handoff).
- Network-level ad & tracker blocking (Blanc Blocker & optional Manifest V2 uBlock Origin) independent of Chrome Web Store extension restrictions.
- Multi-platform targets:
  - **Desktop (Electron)**: Primary desktop shell with multi-window `BrowserWindow` + `WebContentsView` architecture.
  - **Mobile (Android Native)**: Kotlin + Jetpack Compose shell in [`blanc-android/`](file:///workspaces/blanc/blanc-android) with native `WebView`, custom ad-blocker engine, and React bridge contract parity.
  - **Desktop (Tauri Native)**: Lightweight Rust + Webview implementation in [`blanc-tauri/`](file:///workspaces/blanc/blanc-tauri).
  - **Documentation & Site**: Cloudflare Pages site in [`site/`](file:///workspaces/blanc/site).

### Critical Identity & Licensing Invariants
- **App ID Mismatch is Intentional**: The app was renamed from "Bowser" to "Blanc" in July 2026. `build.appId` in `package.json` deliberately remains `me.bnfy.bowser` to preserve macOS Gatekeeper/notarization identity and auto-update chains for existing installs. **Never "clean up" or modify this ID.**
- **Open-Source Baseline (MIT with Carve-outs)**: Blanc is MIT licensed for Bananify Creative-owned code, docs, and media. Brand marks and identity assets in `ASSET-LICENSE.md` remain reserved. Upstream third-party licenses (EasyList, EasyPrivacy, 1Password, fonts) are preserved in `THIRD-PARTY-NOTICES.md`.
- **Marketing Claims Must Be Release-Backed**: Follow `docs/marketing-claims.md`. Verify all claims against the current public release tag and release evidence. Do not claim automatic AI tab organization or semantic boundary detection.

---

## 2. Platform Architecture & Subsystems

### A. Desktop Electron (`src/main/`, `src/renderer/`, `adblock/`)
- **Multi-Window & WebContentsView**: Windows host an Island Chrome strip (`blanc-chrome://`), transparent overlay views (`overlay.html` for Quick Switcher / palette / slash commands / shield), and child `WebContentsView` instances for web content tabs.
- **Quiet Tabs**: Idle background tabs discard renderer processes after a configurable delay (default 1h) to recover memory, keeping lightweight state in `tab-sleep.js`. Tabs restore silently on focus.
- **Reopen Closed Tab**: 30-second parked live `WebContentsView` holding state, degrading to snapshot restore. No private tabs are recorded.
- **Local Profiles**: Default "Personal" profile keeps root configuration. Named profiles store isolated favorites, history, downloads, and partition cookies/storage under `profiles/<id>/`.
- **Adblock Engine**: Compiles bundled, byte-verified EasyList + EasyPrivacy sources in `adblock/sources/` without remote filter fetching at startup.

### B. Android Native (`blanc-android/`)
- **Package**: `me.bnfy.blanc`
- **UI & Chrome**: Jetpack Compose (`BrowserScreen.kt`, `NewTabPage.kt`, overlays) hosting hardware-accelerated `ContentWebView` instances.
- **Tab Lifecycle (`TabManager.kt`)**: Manages tab order, regular vs. private tabs, incognito cookies, tab grouping, and state persistence.
- **Bridge Protocol (`BlancBridge.kt`)**: Implements JavaScript interface matching `PARITY_IPC_CONTRACT.md` for web-to-native communication.
- **Architecture Invariants**:
  - **No Circular Dependency**: `TabManager` and `BlancBridge` must never lazily construct each other in mutually recursive getters. `TabManager` is instantiated first with a default bridge delegate, and `BlancBridge` is linked via `tabManager.setBridge(bridge)` upon creation.
  - **Application Lifecycle**: Android `Application` does not implement `LifecycleOwner`. Always use `ProcessLifecycleOwner.get()` for process-scoped lifecycle needs.
  - **Null Safety**: Avoid force-unwrapping active tab references (`activeTabId!!`). When `activeTabId` is null (such as cold launch or after closing all tabs), fallback to creating a default new tab (`activeTabId?.let { ... } ?: tabManager.createTab(url)`).
  - **Activity References**: Use `WeakReference<Activity>` for Activity references attached to `TabManager` or `TabWebChromeClient` to prevent memory leaks and handle background states gracefully.

### C. Tauri Native (`blanc-tauri/`)
- Rust core with Wry / Webview bindings for high performance and low-resource footprints.

---

## 3. Build & Execution Rules

> [!IMPORTANT]
> **Cloud Builds Only for Heavy Compilations**:
> Native Android builds (Gradle) and heavy platform packaging must be built via **GitHub Actions**, NOT locally inside Codespaces. This conserves disk space and CPU and ensures reproducible CI artifacts.

### Common Commands

#### Desktop (Electron)
```bash
npm install                     # Install dependencies
npm start                       # Launch browser in development
npm run dist:dir                # Quick unpacked desktop build in dist/
npm run test:unit               # Unit test suite (node --test)
npm run lint                    # ESLint correctness checks
```

#### Android
- **Workflow file**: [`.github/workflows/android.yml`](file:///workspaces/blanc/.github/workflows/android.yml)
- **Artifacts**: Produces split APKs per ABI (`arm64-v8a`, `armeabi-v7a`, `x86_64`, `x86`, and `universal`).
- **Release Trigger**: Pushing a tag matching `android-*` (e.g. `android-v1.0.1`) or `v*` triggers the release pipeline and publishes the APKs to GitHub Releases.
- **Manual Trigger**:
  ```bash
  gh workflow run android.yml -f create_release=true -f tag_name=android-v1.0.X
  ```

#### GitHub CLI (`gh`) Configuration
- Default repository is pinned to `peter14l/blanc`.
- Check status or watch runs with:
  ```bash
  gh run list --workflow=android.yml --limit 5
  gh run watch <run-id>
  ```

---

## 4. Coding & Collaboration Guidelines

1. **Immutable Releases**: Once a release tag or draft is created, never delete or overwrite existing release assets. Always increment `versionCode` and `versionName` in [`blanc-android/app/build.gradle.kts`](file:///workspaces/blanc/blanc-android/app/build.gradle.kts) and tag a new release.
2. **Preserve Documentation Integrity**: Maintain comments, docs, and licenses across all modified files.
3. **Markdown Links**: When referencing codebase files or symbols in responses or artifacts, always provide clickable Markdown links (e.g., `[TabManager.kt](file:///workspaces/blanc/blanc-android/app/src/main/java/me/bnfy/blanc/tab/TabManager.kt)`).
4. **Reactive Task Handling**: When background commands or workflow watches are running, rely on system wakeup events instead of running tight polling loops.

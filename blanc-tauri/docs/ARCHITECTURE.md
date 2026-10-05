# Architecture Specification: Blanc

## 1. Architectural Overview

Blanc uses a **dual-platform architecture** sharing a single React UI codebase:

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        SHARED REACT UI (Layer A)                        │
│        React 18 + TypeScript + Tailwind • Island Pill • Overlays        │
└──────────────────────────────────┬──────────────────────────────────────┘
                                   │
              ┌────────────────────┴────────────────────┐
              ▼                                         ▼
┌─────────────────────────────────┐         ┌─────────────────────────────────┐
│      DESKTOP (Tauri v2)         │         │      ANDROID (Native Kotlin)    │
│  ┌───────────────────────────┐  │         │  ┌───────────────────────────┐  │
│  │ Rust Core (Layer B)       │  │         │  │ Kotlin Core (Layer B)     │  │
│  │ • Multi-Webview Manager   │  │         │  │ • TabManager              │  │
│  │ • Window Bounds           │  │         │  │ • WebViewFactory          │  │
│  │ • IPC Routes              │  │         │  │ • AdblockEngine           │  │
│  └───────────────────────────┘  │         │  │ • Room DB Storage         │  │
│  ┌───────────────────────────┐  │         │  │ • BlancBridge (JS↔KT)     │  │
│  │ Child WebViews (per tab)  │  │         │  │ • Array<WebView> (per tab)│  │
│  │ WebView2 / WebKitGTK      │  │         │  │   System WebView          │  │
│  └───────────────────────────┘  │         │  └───────────────────────────┘  │
└─────────────────────────────────┘         └─────────────────────────────────┘
```

### Desktop (Tauri v2) — `blanc-tauri/`
`blanc-tauri` decouples browser chrome from web content execution:

* **Layer A (Chrome / Shell):** A transparent, frameless root Tauri window rendering the React 18 + TypeScript + Tailwind CSS UI. The viewport canvas has a transparent background (`bg-transparent`), leaving only the floating **Island** pill and its ephemeral overlays (Tab Switcher, Quick Switcher command palette) visible.
* **Layer B (Content Engine):** Child native OS webviews spawned and controlled by the Rust core via Tauri v2's native `WebviewBuilder` API. Each tab is an isolated native webview attached to the parent window, positioned directly beneath the floating Island chrome.

### Android (Native Kotlin) — `blanc-android/`
Native Android app managing multiple `WebView` instances directly:

* **Layer A (Chrome / Shell):** Identical React UI loaded in a dedicated transparent "UI WebView" overlaying the content WebViews. Communicates with Kotlin via `BlancBridge` (`@JavascriptInterface` + `evaluateJavascript`).
* **Layer B (Content Engine):** `TabManager` manages `Array<WebView>` — one native `WebView` per tab. Full isolation: cookies, storage, history, adblock per tab. No iframe limitations.

---

## 2. Decoupled Multi-Webview Lifecycle

### Desktop (Tauri)
Web pages are **never** rendered inside HTML `<iframe>` or Chromium `<webview>` elements. Instead, the native Rust backend manages native child webviews:

#### Bounds Calculation Formula
When the root window opens or resizes, the active webview bounds are computed strictly according to:
```rust
X = 16px
Y = 80px (clearing the 44px pill + 36px floating clearance margin)
Width = Window_Width - 32px
Height = Window_Height - 96px
```

#### Tab Lifecycle State Machine

1. **Tab Creation (`create_tab`):**
   - Rust assigns a unique `tab_<timestamp>_<uuid>` identifier.
   - An entry is inserted into `BrowserState`.
   - A child `Webview` is spawned via Tauri v2 `WebviewBuilder` pointing to the requested URL or `blanc://newtab`.
   - The new webview is positioned to the calculated bounds and brought into focus.

2. **Tab Switching (`switch_tab`):**
   - The previously active child webview is hidden via `.hide()`.
   - The target webview is shown via `.show()` and focused.
   - **Zero DOM re-rendering or reloading occurs**, preserving full page state, video playback positions, and scroll offsets.

3. **Tab Navigation (`navigate`, `reload`, `go_back`, `go_forward`):**
   - Rust invokes the corresponding webview navigation APIs (`eval("window.location.href = ...")`, `reload()`, `window.history.back()`).
   - URL and title updates are propagated back to the React UI via IPC events.

4. **Tab Closure (`close_tab`):**
   - The webview instance is closed and destroyed from the native window hierarchy.
   - `BrowserState` cleans up its internal metadata.
   - The adjacent tab is automatically shown and focused.

5. **Window Resize Event:**
   - The root window listens to native `WindowEvent::Resized`.
   - Bounds are recomputed and pushed to the currently active child webview via `.set_bounds()`.

### Android (Kotlin)
Native tab management via `TabManager` controlling `Array<WebView>`:

#### Tab Lifecycle

1. **Tab Creation (`TabManager.createTab()`):**
   - Kotlin generates UUID, creates `Tab` with `WebViewFactory.createWebView()`.
   - `WebView` configured with `TabWebViewClient` + `TabWebChromeClient`.
   - Tab inserted into `TabManager.tabs`, made active.

2. **Tab Switching (`TabManager.switchTab()`):**
   - Previous active `WebView` → `setVisibility(GONE)`.
   - Target `WebView` → `setVisibility(VISIBLE)`, `requestFocus()`.
   - **Zero reload** — full page state preserved in native WebView.

3. **Tab Navigation:**
   - `WebView.loadUrl()`, `goBack()`, `goForward()`, `reload()` called directly.
   - `TabWebViewClient` emits `BlancBridge.onNavigation()` → React updates URL bar.

3. **Tab Closure (`TabManager.closeTab()`):**
   - `WebView.destroy()`, removed from `TabManager.tabs`.
   - Adjacent tab selected and shown.

---

## 3. Transparency & Hit-Testing Strategy

### Desktop (Tauri)
* **Frameless & Transparent Window:** `decorations: false`, `transparent: true`, `shadow: true` in `tauri.conf.json`.
* **Drag Region:** `data-tauri-drag-region` on floating pill header only.
* **Canvas Passthrough:** React root `pointer-events-none`; only Island pill, Tab Switcher, Quick Switcher enable `pointer-events-auto`.

### Android (Kotlin)
* **UI WebView:** Transparent background (`setBackgroundColor(Color.TRANSPARENT)`), full-screen overlay.
* **Touch Routing:** UI WebView intercepts touches on Island pill (via bridge hit-test); passes through to content WebView elsewhere.
* **Content WebViews:** Normal touch handling; hidden when not active.

---

## 4. Performance & Memory Profile Comparison

| Metric | Electron Blanc (`bnfy/blanc`) | Tauri Desktop (`blanc-tauri`) | Android Native (`blanc-android`) |
| :--- | :--- | :--- | :--- |
| **Idle Memory Footprint** | ~320–480 MB | **< 60 MB** | **< 80 MB** (10 tabs) |
| **Cold Startup Time** | 1,400–2,500 ms | **< 200 ms** | **< 200 ms** |
| **Binary Bundle Size** | ~180 MB installer | **~12–25 MB** | **~15–30 MB** (APK/AAB) |
| **Underlying Web Engine** | Bundled Chromium | OS Native (WebView2/WebKitGTK) | OS Native (Android System WebView) |
| **Process Model** | 5–8 Chromium processes | 1 Rust + OS WebView host | 1 App + 1 WebView process/tab |
| **Multi-Tab Isolation** | ✅ Native child WebViews | ✅ Native child WebViews | ✅ Native WebView per tab |
| **GitHub/Gmail Auth** | ✅ | ✅ | ✅ (no iframe limits) |
| **Adblock** | Rust (Ghostery) | Rust (Ghostery) | Kotlin (WebViewClient intercept) |

---

## 5. Security Architecture

### Desktop (Tauri)
1. **Strict IPC Capabilities:** `capabilities/default.json` restricts invoke commands.
2. **Context Isolation:** Web content in isolated OS webview contexts, no Node.js/Rust access.
3. **No Mixed Runtime:** Electron's `nodeIntegration` eliminated.

### Android (Kotlin)
1. **Single WebView Process:** System WebView runs in isolated renderer process per tab.
2. **No Arbitrary JS Injection:** Only `BlancBridge` exposed via `@JavascriptInterface` (annotated methods only).
3. **Adblock at Network Layer:** `WebViewClient.shouldInterceptRequest()` blocks before request leaves app.
4. **Private Tabs:** Separate `WebView` with `setIncognitoMode()` — no persistence.
5. **Permission Model:** `TabWebChromeClient` handles geolocation/camera/mic prompts via bridge.

---

## 6. Adblock Architecture

| Layer | Desktop (Rust) | Android (Kotlin) |
|-------|----------------|------------------|
| **Engine** | `ghostery/adblocker` compiled | Ported logic in `AdblockEngine.kt` |
| **Interception** | Tauri IPC + Ghostery | `WebViewClient.shouldInterceptRequest()` |
| **Cosmetic** | Injected CSS via Ghostery | Injected CSS via `evaluateJavascript()` |
| **Exceptions** | Per-host allowlist | Per-host allowlist |
| **Lists** | EasyList/EasyPrivacy (bundled) | Same lists, bundled in assets |

---

## 7. Storage & Sync

| Feature | Desktop (Rust) | Android (Kotlin) |
|---------|----------------|------------------|
| **History** | `StorageManager` (JSON) | Room DB (`HistoryDao`) |
| **Bookmarks/Favorites** | `StorageManager` (JSON) | Room DB (`BookmarkDao`) |
| **Settings** | `StorageManager` (JSON) | DataStore Preferences |
| **Downloads** | `DownloadManager` (Rust) | System `DownloadManager` + Room |
| **Sync** | Rust `sync.rs` + Worker | Kotlin port or Rust via JNI/uniffi |
| **Patron** | Rust `patron.rs` | Kotlin port |
| **Credentials/1Password** | Rust `credentials.rs` | Platform autofill + optional Rust via JNI |

---

## 8. Bridge Protocol (Android Only)

```kotlin
// Kotlin → React (events via evaluateJavascript)
webView.evaluateJavascript("window.__BLANC__.onTabsChanged($json)")

// React → Kotlin (commands via @JavascriptInterface)
@JavascriptInterface
fun navigate(tabId: String, url: String)
fun goBack(tabId: String)
fun goForward(tabId: String)
fun reload(tabId: String)
fun createTab(url: String?)
fun closeTab(tabId: String)
fun switchTab(tabId: String)
// ... all Tauri commands mirrored
```

Message format: `{ "type": "event|command", "payload": {...} }`

---

## 9. Migration Status

| Phase | Status | Target |
|-------|--------|--------|
| Phase 1: Foundation (TabManager, WebViewFactory, Adblock) | 🟡 In Progress | Week 2 |
| Phase 2: Bridge + UI WebView | ⏳ Pending | Week 3 |
| Phase 3: Feature Parity (Storage, Sync, Patron) | ⏳ Pending | Week 5 |
| Phase 4: UI Parity & Polish | ⏳ Pending | Week 6 |
| Phase 5: Testing & Release | ⏳ Pending | Week 7 |

See `ANDROID_NATIVE_MIGRATION_PLAN.md` for detailed plan.

# Android Native Migration Plan: Kotlin Multi-WebView Architecture

## Goal
Replace Tauri's single-WebView + iframe architecture with native Kotlin multi-WebView management, while keeping the React UI (Island pill, overlays) identical.

## Current Architecture (Tauri Mobile)
```
┌─────────────────────────────────────┐
│ Single Android WebView (Tauri)      │
│ ┌─────────────────────────────────┐ │
│ │ React App (Island, Tabs, UI)    │ │
│ │ ┌─────────────────────────────┐ │ │
│ │ │ iframe (web content)        │ │ │
│ │ │ • X-Frame-Options blocks    │ │ │
│ │ │ • No multi-tab isolation    │ │ │
│ │ │ • Proxy workaround          │ │ │
│ │ └─────────────────────────────┘ │ │
│ └─────────────────────────────────┘ │
└─────────────────────────────────────┘
```

## Target Architecture (Native Kotlin)
```
┌────────────────────────────────────────────────────────────┐
│ Android App (Kotlin)                                       │
│ ┌────────────────────────────────────────────────────────┐ │
│ │ TabManager: Array<WebView> (one per tab)               │ │
│ │ • Full isolation, cookies, storage per tab             │ │
│ │ • Native back/forward/reload per tab                   │ │
│ │ • Adblock via WebViewClient / WebResourceResponse      │ │
│ └────────────────────────────────────────────────────────┘ │
│ ┌────────────────────────────────────────────────────────┐ │
│ │ React UI Overlay (WebView or TextureView)              │ │
│ │ • Island pill, QuickSwitcher, TabSwitcher, Find        │ │
│ │ • Identical look/feel to current React UI              │ │
│ │ • Communicates via Kotlin↔JS bridge                    │ │
│ └────────────────────────────────────────────────────────┘ │
└────────────────────────────────────────────────────────────┘
```

---

## Phase 1: Foundation (Week 1-2)
### 1.1 Kotlin Project Setup
- [ ] Create `blanc-android/` module separate from `blanc-tauri/`
- [ ] Gradle config: `minSdk 24`, `targetSdk 34`, `Kotlin 2.0`, `Compose` optional
- [ ] Dependencies: `WebView` (system), `Gson`/`Moshi` (bridge), `Room` (storage), `Coil` (images)

### 1.2 TabManager Core
- [ ] `TabManager` class managing `MutableList<Tab>`
- [ ] `Tab` data class: `id`, `WebView`, `url`, `title`, `canGoBack`, `canGoForward`, `isPrivate`, `isLoading`, `blockedCount`
- [ ] `WebViewFactory` creating configured WebViews (settings, WebViewClient, WebChromeClient)
- [ ] Tab lifecycle: `create()`, `switchTo()`, `close()`, `destroy()`

### 1.3 WebView Configuration
- [ ] `WebViewClient`:
  - `shouldOverrideUrlLoading()` → handle navigation, update tab state
  - `onPageStarted()` / `onPageFinished()` → loading state, title, history
  - `onReceivedError()` → error handling
- [ ] `WebChromeClient`:
  - `onProgressChanged()` → progress bar
  - `onReceivedTitle()` → tab title
  - `onCreateWindow()` → handle `target="_blank"` / `window.open()`
  - Permission requests (camera, mic, location)

### 1.4 Adblock Engine (Native)
- [ ] Port Rust `AdblockEngine` logic to Kotlin
- [ ] Use `WebViewClientCompat.shouldInterceptRequest()` (API 21+)
- [ ] Return `WebResourceResponse` with empty body for blocked URLs
- [ ] Cosmetic filtering via injected CSS (`WebView.evaluateJavascript()`)
- [ ] Exception handling (allowlist)

---

## Phase 2: React UI Bridge (Week 2-3)
### 2.1 UI Delivery Options
**Option A: React in WebView (recommended)**
- Load React build (`index.html`) in a dedicated "UI WebView" overlay
- Transparent background, covers full screen
- Island pill, overlays render here
- Bridge: `addJavascriptInterface()` + `evaluateJavascript()`

**Option B: Jetpack Compose / XML**
- Rewrite UI in Kotlin — **REJECTED** (breaks UI consistency)

### 2.2 Bridge Protocol
```kotlin
// Kotlin → React (events)
webView.evaluateJavascript("window.__BLANC__.onTabsChanged($json)")

// React → Kotlin (commands)
@JavascriptInterface
fun navigate(tabId: String, url: String)
fun goBack(tabId: String)
fun createTab(url: String?)
fun closeTab(tabId: String)
// ... all current Tauri commands
```

### 2.3 UI WebView Setup
- Transparent background: `setBackgroundColor(Color.TRANSPARENT)`
- Overlay on top of content WebViews: `FrameLayout` with `UI WebView` on top
- Touch handling: UI WebView intercepts touches on Island pill, passes through to content WebView elsewhere

---

## Phase 3: Feature Parity (Week 3-5)
### 3.1 Browser Features
| Feature | Tauri Location | Kotlin Location |
|---------|---------------|-----------------|
| Tab state (title, url, history) | `useBrowserIPC` + Rust | `TabManager` + `Tab` |
| Back/Forward/Reload | Tauri commands | `Tab.webView.goBack()` etc |
| Adblock | Rust `AdblockEngine` | `WebViewClient.shouldInterceptRequest()` |
| History | Rust `StorageManager` | Room DB |
| Bookmarks/Favorites | Rust `StorageManager` | Room DB |
| Downloads | Rust `DownloadManager` | `DownloadManager` system service |
| Settings | Rust `StorageManager` | `DataStore` / Room |
| Private tabs | Separate WebView (incognito) | Separate WebView + no persistence |
| Tab groups | Rust `BrowserState` | `TabManager` + grouping logic |
| Closed tabs recovery | Rust `ClosedTabEntry` | In-memory + Room |

### 3.2 Storage Layer
- **Room Database** for: History, Bookmarks, Favorites, Settings, ClosedTabs
- **DataStore (Preferences)** for: Simple key-value settings
- **FileProvider** for: Downloads, export/import

### 3.3 Sync / Patron / Credentials
- Port Rust logic to Kotlin or call Rust via JNI (if keeping Rust core)
- Recommendation: Port to Kotlin for pure Android, or use `uniffi` for Rust↔Kotlin

---

## Phase 4: UI Parity & Polish (Week 5-6)
### 4.1 Visual Parity Checklist
- [ ] Island pill: back/forward, tab dots, favicon, domain, shield count, private chip, actions
- [ ] TabSwitcher: grid, close tabs, new tab, grouping
- [ ] QuickSwitcher: tabs, bookmarks, history, search, commands
- [ ] Find capsule: in-page search
- [ ] Settings pages: all sections
- [ ] NewTab page: favorites, history, groups, adblock stats
- [ ] Themes: dark, light, sunrise, patron
- [ ] Animations: pill expand/collapse, overlay transitions

### 4.2 Performance
- [ ] WebView pooling (reuse destroyed WebViews)
- [ ] Lazy tab loading (don't create WebView until switched to)
- [ ] Memory management: `onTrimMemory()`, destroy background tabs after N
- [ ] Startup: <200ms cold, <50ms warm

---

## Phase 5: Testing & Release (Week 6-7)
### 5.1 Test Matrix
| Scenario | Tauri | Native |
|----------|-------|--------|
| GitHub sign-in | ❌ | ✅ |
| Google Workspace | ❌ | ✅ |
| Banking sites | ❌ | ✅ |
| YouTube embed | ✅ | ✅ |
| Multi-tab isolation | ❌ | ✅ |
| Private tabs | ✅ | ✅ |
| Adblock | ✅ | ✅ |
| Cold start | ~300ms | <200ms |
| Memory (10 tabs) | ~150MB | ~80MB |

### 5.2 CI/CD
- GitHub Actions: `gradle test`, `connectedAndroidTest`
- Release: Play Store internal → closed → production

---

## Migration Strategy: Strangler Fig
```
Phase 0: blanc-tauri (current) → Ship to Play Store as "Blanc Beta"
Phase 1: blanc-android (new)   → Develop in parallel, same package name
Phase 2: Feature parity        → Run both, compare
Phase 3: Switch                → blanc-android becomes main
Phase 4: Deprecate             → Remove Tauri mobile code
```

**Key**: Same `applicationId` (`me.bnfy.blanc.tauri`), same keystore → seamless update.

---

## File Structure (New)
```
blanc-android/
├── app/
│   ├── src/main/
│   │   ├── java/me/bnfy/blanc/
│   │   │   ├── MainActivity.kt
│   │   │   ├── tab/
│   │   │   │   ├── TabManager.kt
│   │   │   │   ├── Tab.kt
│   │   │   │   ├── WebViewFactory.kt
│   │   │   │   ├── TabWebViewClient.kt
│   │   │   │   └── TabWebChromeClient.kt
│   │   │   ├── adblock/
│   │   │   │   ├── AdblockEngine.kt
│   │   │   │   └── AdblockWebViewClient.kt
│   │   │   ├── bridge/
│   │   │   │   ├── BlancBridge.kt
│   │   │   │   └── BridgeProtocol.kt
│   │   │   ├── storage/
│   │   │   │   ├── AppDatabase.kt (Room)
│   │   │   │   ├── HistoryDao.kt
│   │   │   │   ├── BookmarkDao.kt
│   │   │   │   └── ...
│   │   │   ├── ui/
│   │   │   │   ├── UiWebViewClient.kt
│   │   │   │   └── UiWebChromeClient.kt
│   │   │   └── model/
│   │   │       └── ...
│   │   ├── assets/
│   │   │   └── ui/          # React build output (index.html, assets/)
│   │   └── res/
│   └── build.gradle.kts
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

---

## Parallel Agent Work Streams

| Agent | Stream | Deliverables |
|-------|--------|--------------|
| **Agent 1** | TabManager + WebView Factory | `TabManager`, `WebViewFactory`, `Tab`, lifecycle tests |
| **Agent 2** | Adblock Engine | `AdblockEngine`, `AdblockWebViewClient`, filter list parser, benchmarks |
| **Agent 3** | Bridge + UI WebView | `BlancBridge`, UI WebView setup, React build integration, message protocol |
| **Agent 4** | Storage + Features | Room DB, History/Bookmarks/Settings DAOs, Downloads, Private tabs |
| **Agent 5** | UI Parity | React build integration, visual regression tests, theme support |

---

## Risk Mitigation
| Risk | Mitigation |
|------|------------|
| WebView memory leaks | `WebView.destroy()`, `removeAllViews()`, leakcanary |
| Bridge message loss | Acknowledge protocol, retry queue |
| React build size | Code splitting, lazy load overlay |
| Android version fragmentation | Test API 24-34, use `WebViewClientCompat` |
| Play Store policy | No dynamic code loading, all JS bundled |

---

## Success Criteria
- [ ] GitHub sign-in works
- [ ] Google Workspace works
- [ ] 10 tabs < 100MB RAM
- [ ] Cold start < 200ms
- [ ] Visual regression: 0 pixel diff vs Tauri UI
- [ ] All 69 Rust tests have Kotlin equivalents passing
- [ ] Play Store internal test passes
# Blanc: `blanc-tauri` → `blanc-android` Parity Audit & Executable Plan

**Audit date:** 2026-10-12
**Reference implementation:** `blanc-tauri` (React 18 + Tauri v2/Rust core, v0.3.8)
**Target:** `blanc-android` (Kotlin + Jetpack Compose + native multi-`WebView`, v1.0.18)
**Scope:** user-facing features and their backing engine/IPC, feature-by-feature.

This document is both the **report** (what exists where) and the **executable plan**
(prioritized, dependency-ordered tranches) for closing the gap. Items already at
parity are listed so the audit is auditable, not just an omission list.

---

## 1. Architecture recap

| Layer | `blanc-tauri` | `blanc-android` |
| --- | --- | --- |
| Chrome/UI | React 18 + TS + Tailwind, transparent Tauri window | Jetpack Compose, single-activity, floats over `WebView`s |
| Content engine | Rust `BrowserState` + Tauri child `Webview`s | Kotlin `TabManager` + one native `WebView` per tab |
| UI↔engine bridge | Tauri `invoke`/`emit` (typed IPC) | `BlancBridge` `@JavascriptInterface` + `evaluateJavascript` |
| Storage | `JsonStore<T>` atomic JSON envelopes | Room (entities/DAOs) + DataStore prefs |

Both are two-layer designs. Parity therefore means **re-implementing the React
surface in Compose**, not moving code. The Rust engine counterparts mostly exist
in Kotlin already.

---

## 2. Feature matrix

Legend: ✅ present · ⚠️ partial · ❌ missing · ➖ N/A on mobile (desktop-only concept)

### 2.1 Island / chrome UI

| Feature | Tauri | Android | Notes |
| --- | --- | --- | --- |
| Floating island (pill) | ✅ top | ✅ bottom (`BottomFloatingIslandBar`) | mobile adaptation, correct |
| Back / Forward / Reload / Home | ✅ | ✅ | |
| Address bar + URL/search resolution | ✅ | ✅ (`resolveUrlOrSearch`) | |
| Search suggestions dropdown | ✅ | ✅ (`SearchSuggestionService`) | |
| Slash commands | ✅ | ✅ (`SlashCommands`, handled inline) | |
| Blanc Shield counter + flyout | ✅ | ✅ (`ShieldPopover`, `blockedCount`) | |
| Private tab chip / private theme | ✅ | ✅ | |
| Tab switcher (grid) | ✅ (`TabSwitcher`) | ✅ (`TabSwitcherOverlay`) | |
| Tab switcher fast-cycle HUD (thumbnails) | ✅ (`TabSwitcherHUD`, Alt/Ctrl+Tab) | ➖ | Desktop keyboard concept |
| **Quick Switcher / command palette** | ✅ (`QuickSwitcher`, ⌘K) | ✅ (`ui/QuickSwitcher.kt`) | overflow menu → `/` or "Quick Switcher" |
| Vertical tabs pane (toggle + collapse) | ✅ (`VerticalTabsPane`) | ➖ | Explicitly desktop-only (owner decision) |
| Window controls (min/max/close) | ✅ (`WindowControls`) | ➖ | |
| Zoom controls (⌘ +/-/0) | ✅ | ✅ (`WebSettings.textZoom`, Settings → Text scaling) | adapted as a persistent % setting |
| In-page find (capsule) | ✅ (`FindCapsule`) | ✅ (`FindInPageOverlay`) | |
| Rich context menu | ✅ (`ContextMenu`) | ✅ (`ui/ContextMenu.kt` long-press action sheet) | links/images only; native selection menu retained for text |
| Site permission prompt UI | ✅ (`PermissionPrompt`) | ✅ prompt + per-origin decisions UI | |
| Web notification toast | ✅ (`NotificationToast`) | ⚠️ native `Toast` notices | mobile adaptation, documented |
| Font switcher / Google Fonts | ✅ (`FontSwitcher`, `googleFonts.ts`) | ✅ Settings → Typography (generic families: sans/serif/mono) | adapted, no web-font files |
| Nature wallpapers (new tab) | ✅ (`natureWallpapers.ts`) | ✅ Settings → New tab background (gradient presets, light+dark) | adapted, no bundled images |
| Mouse gestures + rocker + HUD | ✅ (`useMouseGestures`, `GestureHUD`) | ➖ | |
| Shortcuts discovery page | ✅ (`ShortcutsPage`) | ✅ (`ui/pages/ShortcutsPage.kt`) | |

### 2.2 Internal pages

| Page | Tauri | Android | Notes |
| --- | --- | --- | --- |
| New Tab | ✅ ledger: date line, search, favorites tiles, tab groups, weekly blocked footer, wallpapers, fonts | ✅ date line + "Where to?", search, shield banner, tab groups ("Pick up where you left off"), favorites, recent, blocked footer | wallpapers applied as background gradients |
| History | ✅ search + paginate + remove + clear | ✅ | |
| Favorites / Bookmarks | ✅ CRUD, folders, edit | ✅ | |
| Downloads | ✅ open/show/cancel/clear | ✅ | |
| Settings | ✅ 11 sections incl. permissions, typography, wallpapers, import, saved passwords | ✅ sections incl. workspaces, patron, site permissions, text scaling, typography, wallpapers, data backup | see §2.4 |
| Shortcuts | ✅ | ✅ (`ui/pages/ShortcutsPage.kt`) | |
| Diagnostics | ✅ (`DiagnosticsPage`) | ✅ (`ui/pages/DiagnosticsPage.kt`) | |

### 2.3 Engine / backend

| Capability | Tauri (Rust) | Android (Kotlin) | Notes |
| --- | --- | --- | --- |
| Multi-tab / Windows | ✅ real windows | ⚠️ simulated windows | |
| Tab groups (named, collapse, group-local pins) | ✅ | ✅ | |
| Pinned tabs | ✅ | ✅ | |
| Quiet/sleep tabs + snapshots | ✅ | ✅ (`discardTab`/`wakeTab`) | |
| Closed-tab reopen (LIFO + tiers) | ✅ | ✅ (Room, no 30 s held tier) | |
| Private browsing isolation | ✅ `SessionPartition` | ✅ incognito `WebView` | |
| Adblock + exceptions + stats | ✅ | ✅ | |
| Permissions broker (auto-allow tiers) | ✅ | ⚠️ decisions stored, no tiers | |
| Downloads manager | ✅ | ✅ | |
| Favorites / History | ✅ | ✅ | |
| Local profiles | ✅ | ✅ (`ProfileDao`) | |
| Workspaces | ✅ **+ UI** | ✅ **+ UI** (Settings → Workspaces, create/switch/delete; creation gated on Patron) | |
| Sync engine (crypto payload + eligibility) | ✅ | ⚠️ toggle only | **documented divergence, needs review** (§5) |
| Patron / license verification | ✅ **+ UI** | ✅ **+ UI** (Settings → Blanc Patron toggle + entitlements) | |
| 1Password fill broker | ✅ | ⚠️ has `BlancAutofillService` instead (Android-native) | platform substitution |
| Saved passwords list/delete | ✅ | ⚠️ `/passwords` page + nav only (no vault); autofill service shell | **documented divergence, needs review** (§5) |
| Import browser data (bookmarks/history/passwords/tabs) | ✅ | ✅ Blanc archive (Data backup) + Netscape bookmarks HTML from other browsers | passwords excluded (no vault) |
| Font injection into live webviews | ✅ | ✅ `WebSettings` family remap + `applyFontToAll` | adapted |
| Thumbnail capture | ✅ | ✅ | |
| Proxy | ✅ | ❌ | not implemented (low mobile demand) |
| Telemetry | ✅ (`/ping`,`/event`) | ⚠️ `sendUsageStats` pref only | |
| Mica (Windows) | ✅ | ➖ | |
| Global keyboard hook | ✅ | ➖ | |

### 2.4 Settings sections

| Section | Tauri | Android |
| --- | --- | --- |
| Search engine | ✅ | ✅ |
| Theme & Appearance | ✅ | ✅ (Theme; Wallpaper choice; text scaling; typography) |
| Tab layout & vertical tabs | ✅ | ➖ (desktop-only, owner decision) |
| Typography & Google Fonts | ✅ | ✅ (generic families, adapted) |
| Nature wallpapers | ✅ | ✅ (gradient presets, adapted) |
| Mouse gestures | ✅ | ➖ |
| Privacy & Shields | ✅ | ✅ |
| Site permissions & media access | ✅ | ✅ (per-origin decisions) |
| On startup | ✅ | ✅ |
| Import & data migration | ✅ | ✅ (Data backup: export to clipboard + import archive) |
| Saved passwords | ✅ | ⚠️ `/passwords` page + menu entry (nav only, points to system autofill); divergence (§5) |
| About | ✅ | ✅ |
| Quiet tabs | (desktop key) | ✅ |

### 2.5 Dangling / inconsistent findings

- `blanc-android` `strings.xml` declares Mahjong strings (`mahjong_*`) with **no**
  Mahjong page or engine. Tauri has none either (Mahjong is an Electron-only page),
  so this is dead copy, not a Tauri→Android gap. Recommend removing the unused
  strings (or implementing, but that is out of scope for parity).
- Android `createWindow` is explicitly a simulated window (grouping) — documented,
  acceptable for mobile.
- **Resolved:** `Screen.NewTab` was previously never set; it now routes to the
  upgraded Composed `NewTabPage` (and a tab whose URL is blank/about:blank/newtab
  renders the same surface inline).

---

## 3. Executable plan

Ordered so each tranche is independently shippable and dependency-correct:
UI-only work that reuses existing backend comes first; new native engines last.

### Tranche 1 — Power-user navigation & discovery (UI-only, reuses existing state) ← **done**
1. **Quick Switcher / command palette** — new `ui/QuickSwitcher.kt`; matches tabs,
   favorites, history, and slash commands; wired from the overflow menu.
2. **Shortcuts page** — new `ui/pages/ShortcutsPage.kt`, `Screen.Shortcuts` route.
3. **Diagnostics page** — new `ui/pages/DiagnosticsPage.kt`, `Screen.Diagnostics` route.
   Surfaces tab counts, private-tab count, adblock state/counters, active settings.

*Backend needed:* none (uses `TabManager` flows, `FavoriteDao`, `HistoryDao`,
`AdblockEngine`, `DataStore` prefs).

### Tranche 2 — Workspaces & Patron surfaces (backend already exists) ← **done**
4. **Workspaces UI** — list/create/switch/delete in Settings; `Repository` +
   `TabManager.switchWorkspace` were already implemented.
5. **Patron activation UI** — Settings → Blanc Patron toggle consuming
   `Repository.patronActive` / `setPatronActive` / `setPatronEntitlements`;
   Named Workspace creation gated on `patronActive`.
6. **Privacy & Shield: site permissions UI** — per-origin camera/mic/location/
   notifications decisions via `PermissionDecisionEntity` + `ProfileDao`.

### Tranche 3 — New Tab & typography parity ← **done (adapted)**
7. **New Tab upgrades** — date line, tab-group "Pick up where you left off",
   blocked footer (`AdblockEngine.getTotalBlocked`).
8. **Nature wallpapers** — gradient presets (light+dark variants) instead of the
   desktop photographic set; `ui/WallpaperPresets.kt` + Settings picker.
9. **Google Fonts / typography** — adapted to generic platform families
   (sans/serif/mono) via `WebSettings` family remap + live `applyFontToAll`.

### Tranche 4 — Data & security parity ← **import done; two documented divergences**
10. **Saved passwords UI + autofill integration** — ⚠️ **not implemented**;
    documented divergence, needs product/security review (§5). Only the
    Android-native `BlancAutofillService` shell exists.
11. **Import & data migration** — `Repository.importData` now a real,
    fail-closed import (favorites/bookmarks/history/workspaces/settings);
    wired to Settings → Data backup (export to clipboard + paste-to-import).
12. **Sync engine** — ⚠️ **not implemented**; documented divergence, needs its
    own reviewed project (§5), mirroring the Electron plan.

### Tranche 5 — Long-tail ← **mostly done**
13. **Context menu** — long-press action sheet (`ui/ContextMenu.kt`) for
    links/images; native selection menu retained for text.
14. **Vertical tabs** — dropped as desktop-only (owner decision).
15. **Zoom controls** — `WebSettings.textZoom` setting (75–150%), applied live
    via `TabManager.applyTextZoomToAll`.
16. **Web notification toasts** — adapted to native `Toast` where feedback
    matters (import/export, clear data); no custom toast framework.

### Explicitly out of scope (desktop-only)
Window controls, vertical tabs, mouse gestures/rocker, Mica, global keyboard hook,
tab-cycle HUD.

---

## 4. Tranche 1 — what was implemented

| File | Change |
| --- | --- |
| `ui/QuickSwitcher.kt` | **New.** `QuickSwitcherOverlay` composable: substring match across tabs, favorites, history, and slash commands; keyboard "Go" submits the top hit. |
| `ui/pages/ShortcutsPage.kt` | **New.** Lists all slash commands from the shared `SlashCommands` source of truth. |
| `ui/pages/DiagnosticsPage.kt` | **New.** Read-only runtime report (tabs, private/pinned/muted/sleeping counts, adblock state/counters, active settings, version). |
| `ui/BrowserScreen.kt` | Added `Screen.Shortcuts` + `Screen.Diagnostics`, `showQuickSwitcher` state, wired overflow-menu entries and render branches; collects recent history for the palette. |

### Tranche 2 — what was implemented

| File | Change |
| --- | --- |
| `ui/pages/SettingsPage.kt` | **Workspaces section** (Settings → Workspaces): list/create/switch/delete; creation disabled unless `patronActive`. **Blanc Patron dialog**: toggle wired to `setPatronActive` + `setPatronEntitlements`. **Site permissions** row + dialog that groups stored `PermissionDecisionEntity` rows by origin, shows each resource's decision, clears a whole origin. |

### Tranche 3 — what was implemented

| File | Change |
| --- | --- |
| `ui/pages/NewTabPage.kt` | Date line + "Where to?" header, tab-groups "Pick up where you left off" row (`tabManager.focusGroup`), blocked-count footer (`adblockEngine.getTotalBlocked`); wallpaper applied as the page background; `Screen.NewTab` now genuinely routes here. |
| `ui/WallpaperPresets.kt` | **New.** 6 gradient presets (Plain/Sunrise/Ocean/Forest/Dusk/Rose) with light- and dark-theme color pairs; `wallpaperBrushFor` / `wallpaperLabelFor`. |
| `ui/pages/SettingsPage.kt` | **New tab background** picker (swatch rows + radio) writing the `wallpaper` pref. **Typography** dialog (system/sans/serif/mono) writing the `fontFamily` pref. |

### Tranche 4 (import) — what was implemented

| File | Change |
| --- | --- |
| `storage/Repository.kt` | `importData` is now a real, fail-closed import: parses a `exportData` archive inside `database.withTransaction`, typed-deserializes and inserts favorites/bookmarks/history (`OnConflictStrategy.REPLACE`/`IGNORE`), re-inserts workspaces, and merges `settings` via `saveSettings`. Returns `Boolean` success. |
| `ui/pages/SettingsPage.kt` | **Data backup** dialog (Advanced & Data): export → clipboard (`ClipData`), import → paste JSON into a field, `Toast` reports success/failure. |

### Tranche 5 — what was implemented

| File | Change |
| --- | --- |
| `ui/ContextMenu.kt` | **New.** `ContextMenuOverlay` long-press action sheet: Back/Forward/Reload/New tab quick row; link actions (open in new tab, copy address); image actions (open, save via `downloadUrl`); copy page URL, duplicate tab, print, close tab, settings. |
| `ui/ContentWebView.kt` | `onLongPressCallback` wired to `OnLongClickListener` + `HitTestResult` (links `SRC_ANCHOR_TYPE`/`SRC_IMAGE_ANCHOR_TYPE`, images `IMAGE_TYPE`; plain text falls through to the native selection menu); `downloadUrl(url)` public for "Save image"; `settings.textZoom`/font-families applied from the factory cache. |
| `ui/BrowserScreen.kt` | `contextMenuTarget` state, `onLongPress` passed into the container, `ContextMenuOverlay` rendered last in the content box. |
| `storage/Repository.kt` | `textZoom` (75–150) and `fontFamily` (system/sans/serif/mono) DataStore prefs + flows + setters; `wallpaper` pref + flow + setter. |
| `tab/WebViewFactory.kt` | Cached text zoom + font family (seeded at startup and on change); both applied in `configureSettings`. |
| `tab/TabManager.kt` | `applyTextZoomToAll(percent)` and `applyFontToAll(family)` — live-apply to every tab's WebView and seed the factory cache. |
| `BlancApplication.kt` | Loads persisted `textZoom`/`fontFamily` into the factory cache at startup (async, non-blocking). |
| `res/drawable/ic_copy.xml`, `ic_print.xml`, `ic_image.xml` | **New.** Material vectors for the context menu / wallpaper picker. |

### Follow-up — Saved Passwords navigation surface

| File | Change |
| --- | --- |
| `ui/pages/PasswordsPage.kt` | **New.** Read-only "Saved passwords" page (`Screen.Passwords`): honestly states the mobile app stores no passwords on-device (autofill is delegated to Android's system Autofill via `BlancAutofillService`) and offers "Open system autofill settings" (`Settings.ACTION_AUTOFILL_SETTINGS`, API 26+, guarded). No vault was invented — the credential store remains a documented divergence. |
| `res/drawable/ic_lock.xml` | **New.** Material lock vector for the page + overflow menu. |
| `ui/SlashCommands.kt` | Added `/passwords` ("View saved passwords & autofill") — picked up automatically by the Shortcuts page and Quick Switcher palette (both iterate `SlashCommands.ALL`). |
| `ui/BrowserScreen.kt` | Added `Screen.Passwords`, `/passwords` slash-command dispatch, render branch, and overflow-menu "Saved passwords" entry (`onPasswords`). |

### Follow-up — Import browser data (bookmarks from other browsers)

| File | Change |
| --- | --- |
| `util/BookmarksHtmlImporter.kt` | **New.** Pure Kotlin parser for browser bookmarks exports: structured Netscape HTML (the format Chrome/Edge/Brave/Firefox "Export bookmarks" produces; `<H3>` folders + `<DL>` nesting, HTML-entity unescaping, http/https only) with a generic `<a href>` fallback for plain HTML files. |
| `storage/Repository.kt` | `importBookmarks(entries)` — dedups by URL (case-insensitive) against existing favorites and past new ones into the user-visible Favorites store with appended positions; returns `(imported, skipped)`. |
| `ui/pages/SettingsPage.kt` | Data backup dialog gains an "Import from a browser" section: SAF file picker (`ActivityResultContracts.GetContent`), reads the picked file via `ContentResolver`, parses through `BookmarksHtmlImporter`, inserts through `repository.importBookmarks`, and reports "Imported N bookmarks (M duplicates skipped)" via `Toast`. |
| `app/src/test/.../util/BookmarksHtmlImporterTest.kt` | **New.** JUnit tests: nested folders, entity unescaping, non-http filtering, generic-HTML fallback, multi-line anchors/tags, garbage input. |

Platform note: Android **cannot** directly read another installed browser's
private profile — each app's data is sandboxed and there is no public API
(unlike desktop, where the Tauri import discovers and reads profile
directories). The browser-export HTML file is the universal route on mobile,
mirroring the desktop's own "Import Bookmarks HTML" fallback. Passwords CSV
import remains blocked by the no-vault divergence.

### Documented divergences (need review, not build-blocking)

| Divergence | Status | Rationale |
| --- | --- | --- |
| **Sync engine** | ❌ toggle-only in Android | Full crypto-payload + eligibility port is its own reviewed project (mirrors the Electron plan). Needs a security review before implementation; out of scope for a static parity sweep. |
| **Saved passwords** | ⚠️ autofill service shell only | No credential vault exists in Android; adding one is a security-sensitive product decision (keychain/Keystore design, biometric gating). Documented, not silently half-implemented. |
| **Direct browser-data import** | ➖ platform-impossible | Android sandboxes every app's profile (no public API to read Chrome/Edge/Brave/Firefox data). Desktop's profile-directory import has no mobile equivalent; bookmarks HTML export import implemented instead. |
| **Notification toasts** | ⚠️ native `Toast` | Mobile-appropriate adaptation of the desktop in-app toast; no custom framework. |
| **Wallpapers / typography** | ✅ adapted | Gradients + generic font families instead of bundled images/Google-Font files (no new asset or CSP surface). |

Verification: static review only (no build permitted in this environment). All new
code reuses patterns and imports already proven in `BrowserScreen.kt`,
`SettingsPage.kt`, `DownloadsPage.kt`, and `ContentWebView.kt`. Every referenced
symbol (`TabManager.createTab/duplicateTab/switchWorkspace/focusGroup/groupsFlow`,
`Repository.getWorkspaces/createWorkspace/deleteWorkspace/setPatronActive/setPatronEntitlements/exportData/importData`,
`Favorite`/`HistoryEntry`/`Bookmark`/`WorkspaceEntity` fields, `WebSettings.textZoom`
and font-family setters, drawables, Material3 `colors()`/`surfaceContainer*`) was
confirmed against the existing sources before use.

### Still open

- **Sync engine** and **saved-passwords vault** — documented divergences (§ above)
  awaiting product/security review; deliberately not half-implemented.
- **Proxy** support (Engine row) — not implemented; low mobile demand, left
  open.
- **Telemetry** `/ping`,`/event` — Android still only has the consent pref, no
  worker; flagged but unchanged (mirrors Electron's separate telemetry design).
- Everything else in the matrix is implemented, adapted, or explicitly
  desktop-only.

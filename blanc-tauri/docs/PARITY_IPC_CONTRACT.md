# Parity IPC contract (Rust ⇄ React ⇄ Kotlin)

**Status:** binding contract for the Electron → Tauri parity port.
**Owner of this file:** the integrator. Every agent implements against it;
nobody changes it without the integrator.

This exists because Rust, Kotlin, and React slices are developed in parallel. It fixes
the event names, command names, and payload shapes so the three sides can be built
independently and then wired together without drift.

## Platform mapping

| Platform | Native Core | Bridge Layer |
|----------|-------------|--------------|
| Desktop (Windows/macOS/Linux) | Rust (`blanc-tauri/`) | Tauri IPC (`invoke`, `emit`) |
| Android | Kotlin (`blanc-android/`) | `BlancBridge` (`@JavascriptInterface` + `evaluateJavascript`) |

React UI is **identical** across platforms. It receives the same events and sends
the same commands regardless of platform. The bridge layer translates between
platform-native transport (Tauri IPC vs. `@JavascriptInterface`) and the
platform-agnostic protocol defined here.

## Rules this contract enforces

1. **One source of truth per platform.** Rust (desktop) / Kotlin (Android) own
   browser state, webview ownership, navigation, permissions, downloads,
   blocking, profiles, persistence, and security decisions. React renders a
   projection and sends intents.
2. **React holds no browser state of its own.** No localStorage for tabs,
   history, favorites, settings, groups, closed tabs, permissions, or blocker
   stats. localStorage may hold ephemeral UI preferences only (panel open,
   last-used surface, dismissed coach marks).
3. **React never infers browser state by replaying its own actions.** Every
   native mutation emits a fresh projection; React replaces its view wholesale.
4. **Errors surface.** A failed command rejects with a message React displays.
   Native never substitutes success-shaped data for a failure.
5. **Platform transport is an implementation detail.** React sees the same
   command/event names and payloads; Tauri IPC and `BlancBridge` are
   transports, not protocols.

## Serialization conventions

- `model::TabRecord`, `model::ClosedTabEntry`, `model::WindowRuntime` serialize
  with **snake_case** field names (no `rename_all`), matching the existing React
  `Tab` type (`is_active`, `can_go_back`, …).
- `model::StateProjection`, `model::WindowProjection`, `ClosedTabProjection`, and
  every new projection type serialize with **camelCase**.
- Rust command arguments arrive from JS camelCase; Tauri v2 converts to the
  snake_case Rust parameter. Declare parameters in snake_case.
- Kotlin uses the **same camelCase JSON** as React. `Gson`/`Moshi` handles
  conversion. No platform-specific payload shapes.

## Events (Native → React)

All events are emitted via platform transport:
- **Desktop:** `Emitter::emit("event-name", payload)`
- **Android:** `webView.evaluateJavascript("window.__BLANC__.onEvent('name', $json)")`

React registers handlers via `window.__BLANC__.onEvent = (name, payload) => {...}`

### `blanc:state-updated` — the authoritative projection

Emitted **after every browser-state mutation**, and once on startup. This is the
only way React learns browser state.

```rust
// payload = model::StateProjection
{
  windows: [ { id, label, profileId, activeTabId, tabIds, groups,
               overlay, closedTabs, workspaceId, permissionPromptOpen } ],
  focusedWindowId: "win-..." | null,
  adblockEnabled: bool,
  blockingReady: bool,
  totalBlocked: u64,
  blockingError: string | null      // present when blockingReady === false
}
```

`tabs` are read from Native through `get_state_projection` and from
`blanc:state-updated`; there is no separate `tabs-updated` event. Callers that
want only the window's tabs filter the projection by `label`.

When `blockingReady === false`, the chrome shows a visible **"Blocking could not
start"** state and never claims the blocker is active. `blockingError` explains
why. See `adblock.rs`.

### `blanc:tab-event` — transient per-tab signals

Not part of the projection: loading, committed navigation, title changes,
capture changes and navigation failures arrive here because they are signals,
not state.

```rust
// payload = TabEvent (serde(tag = "kind", rename_all = "camelCase"))
{ kind: "loading",        tabId, generation: u64 }
{ kind: "loaded",         tabId, generation: u64 }
{ kind: "navigated",      tabId, url, title, generation: u64 }
{ kind: "titleChanged",   tabId, title }
{ kind: "captureChanged", tabId, capturing: bool }
{ kind: "navigationError", tabId, url, message }
```

`generation` is `TabRecord::navigation_generation`. A consumer **must** discard
an event whose `generation` is lower than the tab's current generation: stale
callbacks from a superseded redirect chain must never overwrite live state.

### `blanc:blocking-status`

```rust
{ enabled: bool, ready: bool, totalBlocked: u64, error: Option<String> }
```

### `blanc:permission-request`

```rust
{ id: String, windowId: String, tabId: String, origin: String,
  resource: PermissionResource, kind: PermissionKind }
```

Emitted only for an **admitted main-frame** request. A subframe request is
answered `Deny` without a prompt.

### `blanc:permission-resolved`

```rust
{ id: String, origin: String, resource: PermissionResource,
  decision: PermissionDecision, remembered: bool }
```

### `blanc:download-updated`

```rust
{ id, windowId, tabId, url, fileName, state, receivedBytes, totalBytes,
  targetPath, error }
```

`state` ∈ `Pending | InProgress | Completed | Cancelled | Failed`.

### `blanc:toast`

```rust
{ kind: "error" | "info" | "success", message: String }
```

Every surfaced failure goes through here; React never invents its own message
for a native error.

### `blanc:create-window-request` (Android only)

```rust
{ url: String, private: bool }
```

Emitted when a link with `target="_blank"` or `window.open()` is triggered.
Kotlin creates a new tab and returns its ID via bridge callback.

---

## Commands (React → Native)

All commands sent via platform transport:
- **Desktop:** `invoke("command_name", args)`
- **Android:** `BlancBridge.commandName(args)` via `@JavascriptInterface`

### State

| Command | Args | Returns |
|---|---|---|
| `get_state_projection` | — | `StateProjection` |
| `get_window_projection` | `window: String` | `WindowProjection` |

### Tabs

| Command | Args | Returns |
|---|---|---|
| `create_tab` | `window: String`, `url: Option<String>`, `private: bool`, `group: Option<String>` | `TabRecord` |
| `close_tab` | `tabId: String` | `TabRecord` (the newly active tab, if any) |
| `close_all_tabs` | `window: String` | `()` |
| `switch_tab` | `tabId: String` | `()` |
| `navigate` | `tabId: String`, `url: String`, `private: bool` | `()` |
| `reload_tab` | `tabId: String` | `()` |
| `go_back` | `tabId: String` | `()` |
| `go_forward` | `tabId: String` | `()` |
| `duplicate_tab` | `tabId: String` | `TabRecord` |
| `set_tab_pinned` | `tabId: String`, `pinned: bool` | `()` |
| `set_tab_muted` | `tabId: String`, `muted: bool` | `()` |

`navigate` takes **raw user text**. Native owns classification, search fallback,
protocol handoff and popup policy (see `navigation.rs`). React must not
pre-normalize: doing so twice is exactly the drift the roadmap forbids.

### Tab groups

| Command | Args |
|---|---|
| `create_group` | `window: String`, `name: String` |
| `rename_group` | `groupId: String`, `name: String` |
| `set_group_collapsed` | `groupId: String`, `collapsed: bool` |
| `move_tab_to_group` | `tabId: String`, `group: Option<String>` (`None` ungroups) |
| `close_group` | `groupId: String` |
| `focus_group` | `groupId: String` |

Group identity is **group-local pinning**: a grouped pin stays in its group and
leads its rows; only ungrouped pins use a standalone pinned shelf.

### Closed tabs

| Command | Args |
|---|---|
| `reopen_closed_tab` | `entryId: String`, `window: String` |
| `forget_closed_tab` | `entryId: String` |
| `clear_closed_tabs` | `window: String` |

### Windows

| Command | Args |
|---|---|
| `create_window` | `private: bool`, `profile: Option<String>` |
| `close_window` | `window: String` |
| `focus_window` | `window: String` |
| `list_windows` | — |
| `set_viewport` | `window: String`, `x, y, width, height: f64`, `hidden: bool` |
| `minimize_window` / `maximize_window` / `toggle_maximize_window` / `close_window_ui` | `window: String` |

`close_window` (destroy the runtime and every child webview) is distinct from
`close_window_ui` (close the native window). Both must destroy the runtime's
child webviews; see `state.rs` and `webview.rs`.

**Android:** `create_window` / `close_window` are no-ops (single activity).
`set_viewport` maps to UI WebView layout.

### History

| Command | Args | Returns |
|---|---|---|
| `history_list` | `limit, offset: u32`, `query: Option<String>` | `HistoryPage { entries, total }` |
| `history_record` | `url: String`, `title: String` | `HistoryEntry` |
| `history_remove` | `id: String` | `()` |
| `history_clear` | — | `()` |

`history_record` **rejects private tabs**. Native is the only caller that knows
privacy; the command takes no privacy argument it could be lied about.

### Favorites

| Command | Args | Returns |
|---|---|---|
| `favorites_list` | — | `Vec<Favorite>` |
| `favorites_add` | `favorite: Favorite` | `Favorite` |
| `favorites_update` | `id: String`, `favorite: Favorite` | `Favorite` |
| `favorites_remove` | `id: String` | `()` |

### Settings

| Command | Args | Returns |
|---|---|---|
| `get_settings` | — | `UserSettings` |
| `save_settings` | `settings: UserSettings` | `UserSettings` |
| `get_sync_eligibility` | — | `SyncEligibility` |

### Ad blocking

| Command | Args | Returns |
|---|---|---|
| `adblock_status` | — | `BlockingStatus` |
| `toggle_adblock` | `enabled: bool` | `BlockingStatus` |
| `adblock_add_exception` | `hostname: String` | `()` |
| `adblock_remove_exception` | `hostname: String` | `()` |
| `adblock_except_active` | `url: String` | `bool` |

### Permissions

| Command | Args | Returns |
|---|---|---|
| `permission_list_decisions` | — | `Vec<PermissionDecisionRecord>` |
| `permission_set_decision` | `origin, resource, decision` | `()` |
| `permission_respond` | `id: String`, `allow: bool`, `remember: bool` | `()` |

### Downloads

| Command | Args |
|---|---|
| `downloads_list` | — |
| `downloads_open` | `id: String` |
| `downloads_show_in_folder` | `id: String` |
| `downloads_cancel` | `id: String` |
| `downloads_clear_completed` | — |

### Surfaces

| Command | Args |
|---|---|
| `open_surface` | `surface: String`, `private: bool` |
| `surface_close` | `window: String` |

`open_surface` accepts only a `Surface` variant name. Unknown names reject.
Utility surfaces (`favorites`, `history`, `downloads`, `settings`, `shortcuts`)
become a floating sheet on the window, **not** a browser tab. `newtab` is an
ordinary managed tab.

---

## Android-specific: `BlancBridge` Interface

```kotlin
class BlancBridge @JvmOverloads constructor(
    private val context: Context,
    private val tabManager: TabManager,
    private val adblockEngine: AdblockEngine,
    // ... other services
) {

    // Commands (React → Kotlin)
    @JavascriptInterface
    fun createTab(url: String?, private: Boolean = false): String // returns tabId

    @JavascriptInterface
    fun closeTab(tabId: String)

    @JavascriptInterface
    fun switchTab(tabId: String)

    @JavascriptInterface
    fun navigate(tabId: String, url: String)

    @JavascriptInterface
    fun reloadTab(tabId: String)

    @JavascriptInterface
    fun goBack(tabId: String)

    @JavascriptInterface
    fun goForward(tabId: String)

    @JavascriptInterface
    fun getStateProjection(): String // JSON

    @JavascriptInterface
    fun getSettings(): String // JSON

    @JavascriptInterface
    fun saveSettings(settingsJson: String)

    @JavascriptInterface
    fun toggleAdblock(enabled: Boolean)

    @JavascriptInterface
    fun addAdblockException(hostname: String)

    @JavascriptInterface
    fun removeAdblockException(hostname: String)

    @JavascriptInterface
    fun historyList(limit: Int, offset: Int, query: String?): String // JSON

    @JavascriptInterface
    fun historyRecord(url: String, title: String)

    @JavascriptInterface
    fun favoritesList(): String // JSON

    @JavascriptInterface
    fun favoritesAdd(favoriteJson: String)

    // ... all other commands mirrored
}
```

React calls via:
```ts
// Platform-agnostic wrapper
const native = {
  createTab: (url?: string, private?: boolean) =>
    window.__BLANC_BRIDGE?.createTab(url, private) ?? invoke("create_tab", ...),
  // ...
}
```

---

## Internal-surface trust

`blanc://` URLs are resolved by `model::Surface::from_url`, which accepts only
the exact allowlisted host with no path suffix, no embedded credentials, and no
port. `Surface::asset_name()` returns a flat file name from a closed enum, so
traversal is unrepresentable rather than filtered.

Internal pages are served from a privileged virtual origin. Ordinary web content
must never be able to reach Blanc's IPC: a command is rejected unless the caller
is the shell's own window (`main` or another `window-*` label) **and** the
calling surface matches the command's expected surface. See the
`blanc:surface-caller` validation in `lib.rs` integration.

**Android:** UI WebView loads React from `file:///android_asset/ui/index.html`.
Internal pages (`blanc://...`) are routed via `BlancBridge.openSurface()`.

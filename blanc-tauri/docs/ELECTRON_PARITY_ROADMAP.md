# Electron → Tauri parity roadmap

**Audience:** coding agents implementing the Tauri port  
**Source baseline:** the Electron implementation in `../src/` and the Tauri
implementation in this directory, reviewed October 5, 2026  
**Status:** implementation roadmap, not a claim that the Tauri port is
production-ready

## Executive summary

`blanc-tauri` currently implements the visual Island shell and a basic
single-window browsing loop:

- React/Tailwind chrome;
- one Tauri window with native child webviews;
- create, close, switch, navigate, reload, back, and forward;
- basic history, bookmarks, settings, and session URL persistence;
- a small hostname blocklist;
- React versions of the new-tab, history, bookmarks, settings, tab-switcher,
  quick-switcher, and mobile surfaces;
- Windows-only Mica Alt window backdrop integration.

It is **not yet a feature-equivalent port** of the Electron browser. The
Electron app has a process-owned browser model, multiple windows and profiles,
private sessions, groups, workspaces, quiet tabs, closed-tab recovery, robust
navigation and permission policy, real network-layer blocking, downloads,
capture/media/WebRTC flows, encrypted sync, telemetry, Patron, updater and
release gates, and a large automated validation suite.

Do not close individual parity tasks just because a similarly named React
component exists. A feature is ported only when:

1. the native behavior exists on every supported target or has a documented
   platform fallback;
2. the React surface reads from the native source of truth rather than a
   second local mock;
3. persistence, privacy, error handling, and user-visible states are covered;
4. automated tests prove the behavior; and
5. packaging/release checks include the feature where appropriate.

## Android Native Migration (Parallel Track)

**Target:** Replace Tauri mobile (single WebView + iframe) with native Kotlin
multi-WebView (`blanc-android/`) — true tab isolation, no iframe limits.

| Track | Status | Target |
|-------|--------|--------|
| Desktop (Tauri) | P0-P2 in progress | Production desktop |
| Android Native | Phase 1 Foundation | Phase 5 release |

See `ANDROID_NATIVE_MIGRATION_PLAN.md` for detailed 7-week plan with parallel
agent streams.

The React UI (`Layer A`) is **shared** — identical Island pill, overlays,
settings, internal pages. Only `Layer B` (content engine) differs:

| Platform | Layer B Implementation |
|----------|------------------------|
| Desktop | Tauri v2 + Rust → native child WebViews (WebView2/WebKitGTK) |
| Android | Kotlin `TabManager` + `Array<WebView>` (System WebView) |

Rules:
- React never branches on platform; receives same bridge events
- Kotlin mirrors Rust command/event surface via `BlancBridge`
- Feature parity = same behavior, different native impl

## Rules for the port

### One source of truth

Rust owns browser state, webview ownership, navigation, permissions, downloads,
blocking, profiles, persistence, and security decisions. React owns rendering
and user interaction. Do not add a second localStorage implementation for a
feature that already has a Rust command.

The current split in
[`useBrowserIPC.ts`](../src/hooks/useBrowserIPC.ts) and
[`storage.rs`](../src-tauri/src/storage.rs) is a migration hazard: both sides
can independently store history, bookmarks, settings, tabs, and blocker stats.
Remove this split as each feature is ported.

### Native child webviews are the browser surface

Desktop web pages must remain native child webviews managed by Rust. Do not
replace them with an iframe/proxy fallback for desktop parity. The current
[`useWebPageLoader.ts`](../src/hooks/useWebPageLoader.ts) path is useful for
frontend mock mode but must not become the production desktop navigation path.

### Preserve the Electron privacy contract

The Tauri port is not complete if it is merely faster or smaller. Preserve the
Electron rules:

- private tabs never write history, session restore, sync, telemetry, or
  closed-tab records;
- ordinary websites never receive Blanc IPC;
- internal-page commands are host- and surface-validated;
- dangerous protocols and popups are admitted explicitly;
- permissions default to deny;
- secrets never cross React IPC or enter logs;
- errors are surfaced instead of silently falling back to success-shaped data.

### Platform divergence must be explicit

Tauri uses WebView2 on Windows, WebKit on macOS/Linux, and Android System
WebView on Android. A native API that exists only on one platform must have:

1. a capability probe;
2. an explicit fallback;
3. a documented behavior difference;
4. a test on the target platform or a deterministic mocked test.

## Priority and dependency model

- **P0 — foundation/security:** required before calling the Tauri build a
  browser or distributing it.
- **P1 — core product parity:** required for normal Electron feature parity.
- **P2 — important parity:** required before claiming broad product parity.
- **P3 — release polish/platform expansion:** required before public release
  equivalence, but can follow the core browser work.

The recommended order is:

1. native state/event architecture and secure webview boundaries;
2. navigation, persistence, session recovery, and window lifecycle;
3. blocking, permissions, downloads, and private browsing;
4. groups, workspaces, quiet tabs, closed tabs, and utility surfaces;
5. profiles, sync, telemetry, Patron, credentials, and capture;
6. updater, packaging, release evidence, and full acceptance testing.

## 1. Browser state and native lifecycle — P0

### Electron behavior to match

Relevant implementation:

- `../src/main/main.js` — `createMainWindow`, `createTab`, `closeTab`,
  `setActiveTab`, window runtime and IPC;
- `../src/main/window-runtime-registry.js`;
- `../src/main/tab-view.js`;
- `../src/main/tab-activation.js`;
- `../src/main/tab-order.js`.

Electron supports many native windows. Each window has an independent runtime
for tabs, groups, overlays, utility surfaces, workspace identity, capture
state, and profile context. Tab views are native `WebContentsView` instances
owned by the main process; the renderer only receives a safe projection.

### Current Tauri state

[`BrowserState`](../src-tauri/src/state.rs) is a flat `Vec<Tab>` with one
`active_tab_id`. [`tauri.conf.json`](../src-tauri/tauri.conf.json) defines one
window. [`webview.rs`](../src-tauri/src/webview.rs) can create and hide/show
child webviews, which is a good foundation, but the tab record lacks private,
group, pin, mute, opener, favicon, audio, capture, profile, workspace, and
sleep state.

### Implementation

1. Add a native `WindowRuntime` keyed by Tauri window label:
   - runtime ID;
   - profile ID;
   - ordered tab IDs;
   - active tab;
   - group map;
   - overlay/utility state;
   - closed-tab entries;
   - capture state.
2. Replace the flat global tab vector with:
   - `BrowserState { windows: HashMap<WindowId, WindowRuntime> }`;
   - `TabRecord` containing only serializable metadata;
   - a native registry mapping tab ID to child webview ownership.
3. Add `create_window`, `close_window`, `focus_window`, and new private-window
   commands. Create the initial tab in Rust, not only in React.
4. Emit one `tabs-updated` projection event after every state mutation. Do not
   make React infer state by replaying local actions.
5. Subscribe to native resize, focus, close, and destroy events and update
   child-webview bounds through one Rust layout function.
6. Ensure the last-tab and last-window behavior matches the intended Electron
   behavior, including dock/taskbar reopen behavior on macOS.

### Acceptance criteria

- Two windows can have different active tabs, groups, overlays, and profiles.
- Switching tabs never reloads the native page.
- Closing a tab updates the adjacent tab deterministically.
- React reloads with no local browser state and reconstructs entirely from
  native projection events.
- Closing/destroying a window cannot leave an orphaned child webview.

## 2. Navigation, redirects, popups, and protocols — P0

### Electron behavior to match

- URL normalization and OS handoff:
  `../src/main/main.js`, `../src/main/external-url-handoff.js`,
  `../src/main/top-level-url-policy.js`.
- Popup admission:
  `../src/main/context-menu.js`,
  `../src/main/profile-navigation-gates.js`, and the `setWindowOpenHandler`
  calls in `main.js`.
- Internal pages and guarded IPC:
  `../src/main/pages.js`,
  `../src/main/pages-ipc-trust.js`,
  `../src/main/utility-pages.js`.

The Electron policy distinguishes HTTP(S), `blanc:`, local files, and OS
protocols such as `mailto:`, `tel:`, `facetime:`, and `sms:`. It handles
redirect chains, main-frame versus subframe navigation, popups, opener
inheritance, utility sheets, and external handoff.

### Current Tauri gaps

[`navigate`](../src-tauri/src/lib.rs) handles only a simple URL/domain/search
heuristic. Back/forward currently use JavaScript history calls rather than a
native navigation API, and the child-webview callback does not reliably update
back/forward state. There is no popup policy, redirect policy, context menu,
OS protocol policy, local-file policy, or host-validated internal-page bridge.

### Implementation

1. Create `navigation.rs` with typed functions:
   - `classify_input`;
   - `normalize_navigation_target`;
   - `admit_top_level_navigation`;
   - `admit_popup`;
   - `handoff_external_protocol`.
2. Use the Tauri/Wry navigation and new-window callbacks for every child
   webview. Never rely only on React `onNavigate`.
3. Represent navigation decisions as `Allow`, `OpenInNewTab`, `OpenExternal`,
   `Deny`, or `ShowError`.
4. Add redirect-chain IDs and loading generations so stale callbacks from a
   previous navigation cannot overwrite the current tab.
5. Add a secure `blanc://` equivalent or an internal virtual-origin router.
   Resolve only an allowlisted flat asset name; reject traversal and unknown
   hosts. Expose one narrow command namespace per internal surface.
6. Implement context menus for links, images, selection, reload, save, inspect
   and open-in-new-tab actions. Route all resulting URLs through the same
   admission policy.
7. Use native webview history APIs if the Tauri version exposes them; otherwise
   maintain a carefully tested navigation-state adapter and update
   `can_go_back`/`can_go_forward` from native events.

### Acceptance criteria

- `mailto:` and other supported external schemes open through the OS and never
  become search queries.
- A popup cannot navigate a parked/closed tab or bypass profile/private policy.
- Redirects update the final committed URL and never resurrect stale tab state.
- Unknown internal hosts, paths, and IPC callers are rejected.
- Back/forward button disabled state reflects the actual child webview.

## 3. Persistence, migration, and crash recovery — P0

### Electron behavior to match

- Atomic store:
  `../src/main/store.js`.
- Session snapshot/restore/recovery:
  `../src/main/session-snapshot.js`,
  `../src/main/session-restore.js`,
  `../src/main/session-recovery.js`.
- Profile migration:
  `../src/main/migration-checklist.js`,
  `../src/main/local-profile-context.js`.

Electron uses separate stores, debounced writes, owner-only temporary files,
fsync, atomic replacement, validation, versioned schemas, recovery handling,
and a Bowser-to-Blanc migration.

### Current Tauri gaps

[`StorageManager`](../src-tauri/src/storage.rs) writes one JSON file directly
with `fs::write`, has no schema version, no atomic replacement, no fsync, no
corruption recovery, and no migration. Session persistence stores only URLs.
React also persists overlapping state in localStorage.

### Implementation

1. Build a generic Rust `JsonStore<T>`:
   - versioned envelope;
   - serde validation;
   - owner-only temp file where supported;
   - flush and sync;
   - atomic rename;
   - bounded backup/recovery file;
   - debounced async write plus explicit synchronous flush.
2. Split stores by feature: settings, favorites, history, downloads, session,
   profiles, workspaces, sync metadata, adblock statistics, and telemetry
   consent.
3. Define `session.json` v2 with:
   - windows;
   - selected tab;
   - ordered tab IDs;
   - URLs, title, favicon metadata;
   - groups and group IDs;
   - pinned state;
   - private-tab exclusion.
4. Restore only the selected tab immediately; restore other tabs lazily and
   preserve bounded recovery snapshots.
5. Add startup recovery for interrupted writes and malformed data. Preserve a
   known-good file rather than silently resetting all data.
6. Remove browser-feature localStorage persistence from
   `useBrowserIPC.ts`. LocalStorage may remain for ephemeral UI preferences
   only.
7. Add a migration reader for existing Electron/Bowser data where migration is
   technically possible. Record migration version and failures.

### Acceptance criteria

- Killing the process during a write leaves either the old or new valid store.
- A malformed store is recovered with an explicit diagnostic.
- Session restore reproduces window/tab order and selected tabs.
- Private tabs never appear in any persisted session or history file.
- React and Rust cannot disagree about a favorite, setting, or tab.

## 4. Private browsing — P0

### Electron behavior to match

See the private-tab logic in `../src/main/main.js`,
`../src/main/tab-view.js`, `../src/main/session-restore.js`, and
`../src/main/history.js`.

Private tabs use a separate non-persistent session. They do not write history,
session restore, sync, telemetry, or reopen-closed-tab state. Popups inherit
private state. Private downloads keep metadata in memory while the downloaded
file remains on disk. The chrome has a private theme and exit affordance.

### Implementation

1. Add `SessionPartition::{Personal, Profile(id), Private(window_id)}`.
2. Configure each native child webview with the correct persistent or
   ephemeral data directory/container.
3. Add `private` and `session_partition` to `TabRecord`; make inheritance
   explicit when opening a popup or context-menu child.
4. Guard every persistence and telemetry call with a native privacy predicate.
5. Add private styling and a clear private indicator to the Island and tab
   switcher.

### Acceptance criteria

- Private cookies/storage/cache disappear after the private session is closed.
- No private URL/title is found in history, sync payloads, crash diagnostics,
  session JSON, or closed tabs.
- Private tabs can navigate and download without contaminating personal data.

## 5. Real ad/tracker blocking — P0

### Electron behavior to match

- Engine setup: `../src/main/adblock.js`,
  `../src/main/adblock-engine-loader.js`.
- Blocking coordination/recovery:
  `../src/main/blocking-coordinator.js`,
  `../src/main/blocking-providers.js`,
  `../src/main/blocking-recovery.js`.
- Exceptions and shield:
  `../src/main/adblock-exceptions.js`,
  `../src/main/shield-model.js`.
- Verified sources: `../adblock/`, `../scripts/verify-packaged-adblock.js`.
- Optional managed uBlock: `../src/main/ublock-*.js`.

### Current Tauri gaps

[`adblock.rs`](../src-tauri/src/adblock.rs) checks about 50 hard-coded hostnames
only when a navigation occurs. It does not see subresources, cannot apply
EasyList/EasyPrivacy syntax, has no cosmetic filtering or scriptlets, no
exceptions, no startup gate, and no uBlock provider. The React fallback also
contains simulated blocker counts and must be removed.

### Implementation options

The preferred desktop path is a Rust request-interception engine compatible
with the native webview backend:

1. Bundle the pinned EasyList/EasyPrivacy snapshots and hashes.
2. Parse/compile them during build or first launch into a versioned artifact.
3. Intercept every WebView2/Wry request before it reaches the network.
4. Apply network rules, third-party policy, per-site exceptions, and counters.
5. Add a separate cosmetic/scriptlet integration only after a threat-model
   review; do not inject arbitrary remote JavaScript.

If Wry cannot provide equivalent interception on every target, define a
platform provider interface:

- Windows WebView2 provider;
- macOS WebKit provider;
- Linux WebKitGTK provider;
- Android WebView provider.

Fail closed for the configured blocker if the provider cannot be initialized;
show a visible “Blocking could not start” state rather than claiming success.

### Acceptance criteria

- A blocked subresource never reaches the network.
- A rule-list update is hash-pinned and reproducible.
- Private and regular sessions both receive the intended blocker.
- Per-site allow/block changes affect only the selected origin.
- Counts are derived from actual intercepted requests, never random increments.
- Packaged artifacts contain exactly the verified list/compiler payload.

## 6. Permissions, media, WebRTC, and capture — P0/P1

### Electron behavior to match

- Permission policy: `../src/main/permissions.js`,
  `../src/main/permission-decisions.js`.
- Capture state and indicator:
  `../src/main/capture-state.js`,
  `../src/main/display-capture-*.js`,
  `../src/renderer/display-capture-*.js`.
- WebRTC helpers and policy:
  `../src/main/native-media-access.js`,
  `../src/main/display-capture-admission.js`.
- WebAuthn: `../src/main/webauthn.js`.

Electron denies by default, prompts per origin for camera, microphone,
geolocation, and notifications, remembers decisions, and provides dedicated
screen-sharing/capture flows. It also exposes truthful permission-query state,
WebRTC policy, and packaged WebAuthn support.

### Current Tauri gaps

There is no application permission handler, prompt surface, capture indicator,
screen-share picker, WebRTC policy, WebAuthn policy, or permission settings
surface.

### Implementation

1. Add a Rust `PermissionBroker` keyed by profile/session/origin/resource.
2. Register Tauri/Wry/WebView2/WebKit permission callbacks where available.
3. Default to deny and emit a typed prompt event to React only for an admitted
   main-frame request.
4. Store decisions per profile with explicit `Allow`, `Block`, and `Ask`
   states; private sessions remain in memory.
5. Add a dedicated capture broker and indicator. The indicator is display
   state, not the security authority; the OS indicator remains authoritative.
6. Implement platform-specific screen capture adapters behind a trait.
7. Investigate WebAuthn support per target. Do not advertise passkeys until
   user-verification, origin binding, and packaging tests pass.

### Acceptance criteria

- A hostile iframe cannot trigger an unrestricted permission prompt.
- Blocked origins receive denied results without leaking device state.
- Private decisions disappear on session close.
- Active capture is visible in the chrome and clears correctly after tracks
  stop.
- Unsupported capture/WebAuthn targets report unavailable, not success.

## 7. Downloads and external protocol handoff — P1

### Electron behavior to match

See `../src/main/downloads.js`,
`../src/main/external-url-handoff.js`, and
`../src/renderer/pages/downloads.*`.

Downloads require progress, completion, failure, cancellation, safe file
naming, persistent metadata, private-session separation, and open/show-in-folder
actions.

### Implementation

1. Register native download callbacks for every child webview.
2. Create a Rust `DownloadManager` with IDs, state transitions, byte counts,
   target paths, profile/session ownership, and cancellation.
3. Keep active transfers in memory; persist completed metadata through the
   versioned store.
4. Add a Downloads page and toolbar/pill entry driven by push events.
5. Use a strict allowlist for OS protocol handoff. Never pass arbitrary
   untrusted command arguments to a shell.
6. Add safe filename normalization and collision handling.

### Acceptance criteria

- Progress updates arrive without polling.
- Cancelled downloads stop and leave no misleading completed record.
- Private metadata is memory-only.
- `mailto:`/`tel:`/similar URLs use the OS handler; unsupported schemes are
  denied with an explicit message.

## 8. Favorites, history, internal pages, and search — P1

### Current state and gaps

React has `NewTabPage`, `HistoryPage`, `BookmarksPage`, `SettingsPage`,
`TabSwitcher`, `QuickSwitcher`, and `SuggestionPicker`. These are useful UI
starting points, but the Electron pages have richer native backing:

- history search, grouping, caps, and private guards;
- favorites tree/folders/import/validation;
- downloads and shortcuts pages;
- utility sheets instead of ordinary browser tabs;
- `blanc://` origin and guarded per-page bridges;
- live suggestions with privacy gates and bounded providers.

### Implementation

1. Port the Electron data models and validation rules before polishing React.
2. Expose typed Rust commands for list/search/add/update/remove operations.
3. Add a `Surface` model for internal pages:
   `NewTab`, `Favorites`, `History`, `Downloads`, `Settings`, `Shortcuts`,
   `About`, `Diagnostics`, and `Import`.
4. Route utility surfaces to a floating sheet/overlay rather than a normal
   web tab where that is the Electron contract.
5. Port search suggestion privacy rules:
   no provider request for URL-like, sensitive, pasted, or private input;
   debounce and cancel stale requests; cap results; make the setting explicit.
6. Add favicon policy and safe rasterization. Never let a remote favicon become
   an unrestricted local resource.

### Acceptance criteria

- Each page reads/writes through native commands.
- Favorites/history are consistent across windows and after restart.
- Private pages cannot expose personal history or favorites accidentally.
- Utility pages cannot navigate into arbitrary local files.
- Suggestions are bounded, cancellable, and disabled when privacy settings say
  so.

## 9. Tab groups, pinned tabs, closed tabs, and quiet tabs — P1

### Electron behavior to match

- Groups/order/context menus:
  `../src/main/tab-context-menu-model.js`,
  `../src/main/tab-context-menu.js`,
  `../src/main/tab-order.js`.
- Reopen closed tabs:
  `../src/main/closed-tabs.js`.
- Quiet tabs:
  `../src/main/tab-sleep.js`,
  `../src/main/renderer-discard.js`.

### Implementation

1. Add named groups with stable IDs, ordered membership, collapsed state, and
   group-local pinning.
2. Add pin/unpin, mute/unmute, duplicate, move, close-group, and context-menu
   actions.
3. Add bounded closed-tab entries:
   - live view for a short hold window;
   - sanitized snapshot after degradation;
   - URL-only fallback;
   - one entry per action;
   - expiry and maximum count;
   - immediate destruction on Forget/Clear all.
4. Add quiet-tab policy as pure Rust functions with unit tests. Sleeping must
   discard renderer resources only when the tab is inactive, clean, not
   capturing, not private-sensitive, and not otherwise protected.
5. Preserve navigation recovery data in a native-only bounded map; never send
   page state or POST bodies to React, disk, sync, or diagnostics.

### Acceptance criteria

- Group order, collapse, pinning, and shortcut behavior survive restart.
- Reopen restores a held tab without refetching when safe.
- A dirty form, beforeunload handler, active capture, or uncertain renderer
  prevents unsafe sleeping.
- Quiet tabs wake exactly once and stale callbacks cannot overwrite active state.

## 10. Workspaces and local profiles — P1

### Electron behavior to match

- Profiles: `../src/main/local-profiles.js`,
  `../src/main/local-profile-model.js`,
  `../src/main/profile-deletions.js`.
- Workspaces: `../src/main/workspaces.js`,
  `../src/main/workspaces-model.js`,
  `../src/main/workspace-controller.js`.

Named profiles isolate favorites, history, downloads, remembered permissions,
and persistent web storage. Personal remains the compatibility profile.
Named Workspaces organize tabs and have Patron gating.

### Implementation

1. Create an opaque profile registry with reserved Personal profile.
2. Derive profile session/data directories without using user-visible names.
3. Add crash-resumable deletion markers and cleanup ordering.
4. Scope every store, permission decision, download, and webview partition to
   profile ID.
5. Add workspace records with tab membership, ordering, and selected workspace.
6. Keep device-level settings explicitly outside profile storage where that is
   the Electron contract.

### Acceptance criteria

- Switching profile cannot expose another profile's cookies, history, or
  favorites.
- Deleting a profile closes its views and resumes safely after a crash.
- Workspaces restore with correct tabs and do not leak private tabs.

## 11. Profile Sync, telemetry, and Patron — P2

### Electron behavior to match

- Sync: `../src/main/sync.js`,
  `../src/main/sync-crypto.js`, `../src/main/sync-key-storage.js`.
- Telemetry: `../src/main/telemetry.js`.
- Patron: `../src/main/patron.js`, `../src/main/patron-model.js`.

### Implementation

1. Port sync only after the local profile/store model is stable.
2. Derive an opaque account locator and AES-GCM content key using the reviewed
   protocol; discard passphrases; wrap retained keys with the platform
   credential store. Reject insecure Linux storage modes.
3. Sync only Favorites, approved settings, and optional encrypted open-tab
   snapshots. Never sync cookies, history, downloads, permissions, private
   tabs, supporter keys, or device-local presentation state.
4. Implement conflict/version handling and wipe/disconnect flows.
5. Require a saved consent decision before telemetry. Keep events bounded,
   session-deduplicated, privacy-minimized, and private-tab-free.
6. Port Patron verification in Rust. React receives only a derived boolean;
   never expose an activation key.

### Acceptance criteria

- A sync server cannot decrypt payloads or infer their contents.
- Opt-out prevents future events and does not delete unrelated local data.
- Private tabs and secrets are absent from sync and telemetry fixtures.
- Patron-gated UI cannot be unlocked by editing React state.

## 12. 1Password credentials and fill — P2

### Electron behavior to match

See `../src/main/onepassword-*.js`,
`../src/main/credential-fill-controller.js`,
`../src/main/credential-picker.js`, and
`../src/main/fill-status-surface.js`.

This is a narrow explicit-invoke flow, not a general password manager:
renderer-free credential selection, native picker, strict website matching,
revalidation immediately before injection, and fixed-kind status messages.

### Implementation

Do not port this by sending credentials through React. Create a Rust utility
process/plugin boundary for the supported platforms, keep candidate usernames
transient and bounded, bind selections to item version, revalidate origin and
frame before injection, and expose only fixed status-kind events to the shell.
Keep save/update, automatic fill, TOTP, generation, export, and payment-card
features out of scope.

## 13. Updater, packaging, signing, and release gates — P2/P3

### Electron behavior to match

See root `../scripts/release.sh`, `../package.json`, and
`../.github/workflows/`. The Electron release includes immutable tags,
notarized macOS artifacts, Windows signing identity/timestamps, Linux package
checks, updater metadata, SBOM/provenance, authenticated manifests, Sigstore,
fuses, packaged payload checks, public smoke tests, and Restart Now handoff
validation.

### Implementation

1. Configure the Tauri updater plugin with signed update metadata and a
   key-management runbook. Never put signing keys in the repository.
2. Implement check/download/install/restart state in Rust and a React updater
   surface. The restart handoff must be initiated by the running app.
3. Build native artifacts on native runners:
   - macOS app/dmg/zip with signing and notarization;
   - Windows installer with exact publisher and timestamp checks;
   - Linux AppImage/deb/rpm as selected by the product decision;
   - Android signed bundles/splits separately.
4. Add immutable release script behavior: clean tree, version/build monotonicity,
   no tag overwrite, complete artifact set, manifest, SBOM, provenance, and
   post-publication verification.
5. Port package compliance checks for licenses, icons, blocker sources,
   updater metadata, and native capabilities.
6. Add Windows WebView2 runtime policy and Mica fallback checks. Mica Alt is
   enhancement only; it must not be a release prerequisite.

### Acceptance criteria

- A downloaded update is signature-verified before installation.
- A failed update leaves the running installation usable.
- Releases cannot overwrite an existing version or publish unsigned output.
- Fresh logged-out downloads pass digest/signature checks.
- Native installers launch, update, and restart through the app on each target.

## 14. Accessibility, shortcuts, and UI parity — P1/P2

### Electron behavior to match

- Shortcut registry: `../src/main/browser-shortcuts.js`.
- Focus and overlay behavior: `../src/main/island-typing.js`,
  `../src/main/overlay-view-lifecycle.js`, and `../src/main/chrome-layout.js`.
- Chrome surfaces: `../src/renderer/index.html`,
  `../src/renderer/overlay.html`, `../src/renderer/permission.html`.

### Implementation

1. Move global keyboard routing to a native shortcut/input layer so shortcuts
   still work when a child webview owns focus.
2. Implement focus reclamation for the address bar, quick switcher, find
   capsule, utility sheets, and permission prompts.
3. Port all shortcut behavior, including platform-specific menus and
   `Cmd/Ctrl+1–9`, private tab, reopen closed tab, find, glance, workspaces,
   downloads, and settings.
4. Add accessible names/states for tab dots, private state, loading, blocker
   count, capture status, group collapse, and selected suggestions.
5. Test keyboard-only operation and screen-reader semantics on Windows,
   macOS, and Linux where available.

## 15. Testing and validation — P0

The Tauri package currently has no test script. Add tests before large feature
claims are made.

### Required layers

1. **Rust unit tests**
   - navigation classification/admission;
   - state transitions and tab ordering;
   - store atomicity, migration, and recovery;
   - private-data exclusion;
   - group/closed-tab/quiet-tab policies;
   - permission decisions;
   - blocker rule matching and exceptions.
2. **React component tests**
   - accessible state and keyboard navigation;
   - no browser-feature localStorage writes;
   - error/loading/empty states.
3. **Desktop integration tests**
   - launch, create/switch/close/navigate tabs;
   - redirects and popup policy;
   - persistence/restart;
   - private browsing;
   - downloads;
   - permission prompts;
   - blocker requests and counts;
   - updater staging/restart.
4. **Platform tests**
   - Windows WebView2, Mica fallback, installer signing;
   - macOS WebKit permissions, signing/notarization;
   - Linux WebKitGTK sandbox/package;
   - Android lifecycle, back behavior, downloads, and permissions.
5. **Security tests**
   - capability minimization;
   - hostile pages cannot invoke internal commands;
   - traversal/dangerous protocol/popup tests;
   - credential and telemetry redaction;
   - private-data leak scans.

Add scripts equivalent to the Electron commands in root `package.json`, at
minimum:

```text
npm run test:unit
npm run test:desktop
npm run test:security
npm run test:packaged
npm run lint
npm run substrate:check
npm run release:verify
```

## Feature matrix

| Area | Desktop (Tauri) | Android (Native Kotlin) | Priority |
|---|---|---|---|
| Native child webviews | Basic desktop implementation | Phase 1: TabManager + WebViewFactory | P0 harden |
| Single-window tabs | Basic | Phase 1: TabManager lifecycle | P0 replace with window runtime |
| Multi-window | Missing | N/A (single activity) | P0 |
| Navigation admission/protocols | Basic heuristic | Phase 3: port Rust logic | P0 |
| Popup/context-menu policy | Missing | Phase 3: TabWebChromeClient | P0 |
| Internal pages/guarded IPC | React-only, no Electron-equivalent trust layer | Phase 2: Bridge + UI WebView | P0 |
| Persistence/recovery | One direct JSON file plus duplicated localStorage | Phase 3: Room DB | P0 |
| Session restore | URL-only, incomplete | Phase 3: Room session | P0 |
| Private browsing | Missing | Phase 1: Private WebView | P0 |
| Real ad blocking | Hard-coded hostname list | Phase 1: AdblockEngine (Kotlin) | P0 |
| Permission policy | Missing | Phase 3: TabWebChromeClient | P0 |
| Media/capture/WebRTC | Missing | Phase 4: native adapters | P0/P1 |
| Downloads | Missing | Phase 3: System DownloadManager | P1 |
| Favorites/history/settings | UI exists; native source of truth incomplete | Phase 3: Room DAOs | P1 |
| Search suggestions | UI/provider exists; privacy/native integration incomplete | Phase 3: bridge suggestions | P1 |
| Groups/pins | Missing | Phase 3: TabManager groups | P1 |
| Reopen closed tabs | Missing | Phase 3: Room closedTabs | P1 |
| Quiet tabs | Missing | Phase 4: sleep policy | P1 |
| Profiles | Missing | Phase 3: Room profiles | P1 |
| Workspaces | Missing | Phase 3: Room workspaces | P1 |
| Sync | Missing | Phase 4: port or JNI | P2 |
| Telemetry | Missing | Phase 4: Kotlin port | P2 |
| Patron | Missing | Phase 4: Kotlin port | P2 |
| 1Password fill | Missing | N/A (platform autofill) | P2 |
| WebAuthn/passkeys | Missing | Phase 4: native | P2 |
| uBlock Origin provider | Missing | N/A (native adblock) | P2, separately reviewed |
| Updater | Missing | Play Store native | P2 |
| Signing/notarization/release evidence | Basic Tauri bundle only | Play Store signing | P2/P3 |
| Accessibility/shortcut parity | Partial React implementation | Same React UI | P1/P2 |
| Automated tests | Missing | Phase 5: unit + instrumented | P0 |
| Windows Mica Alt | Implemented as enhancement | N/A | Done, verify on Windows 11 |
| Mobile shell | Scaffold exists | **Phase 1-5: Native Kotlin** | Phase 5 release |

## Android Native Agent Work Streams

| Agent | Stream | Deliverables |
|-------|--------|--------------|
| **Agent 1** | TabManager + WebView Factory | `TabManager`, `WebViewFactory`, `Tab`, lifecycle tests |
| **Agent 2** | Adblock Engine | `AdblockEngine`, `AdblockWebViewClient`, filter list parser, benchmarks |
| **Agent 3** | Bridge + UI WebView | `BlancBridge`, UI WebView setup, React build integration, message protocol |
| **Agent 4** | Storage + Features | Room DB, History/Bookmarks/Settings DAOs, Downloads, Private tabs |
| **Agent 5** | UI Parity | React build integration, visual regression tests, theme support |

## Suggested agent work breakdown

Agents should take one bounded slice at a time and keep the tree buildable:

1. **Native core:** window runtime, tab registry, typed event projections.
2. **Security/navigation:** URL admission, popup policy, internal surfaces,
   capabilities, protocol handoff.
3. **Persistence:** versioned stores, migration, session recovery.
4. **Privacy:** private partitions, permission broker, telemetry exclusion.
5. **Blocking:** real request interception and verified source pipeline.
6. **Product parity:** downloads, favorites/history/settings, groups, closed
   tabs, quiet tabs.
7. **Profiles/workspaces/sync:** only after persistence and privacy foundations.
8. **Release/test:** updater, signing, packaging compliance, platform suites.

Every agent should include:

- the exact Electron source files used as behavioral references;
- the Tauri command/event/data-model changes;
- tests for success, failure, privacy, and restart behavior;
- documentation updates;
- a statement of unsupported platform behavior;
- no unrelated refactors or UI-only mocks that bypass Rust.

## Definition of done for a production Tauri replacement

The Tauri port can be considered a replacement candidate only when all P0
items pass on supported desktop platforms, all P1 items have parity evidence,
and P2/P3 release items are either implemented or explicitly approved as
product differences. In particular, a polished Island UI, Mica backdrop,
basic tabs, and a hostname blocklist are **not** sufficient evidence of
browser parity.

# Electron → Tauri Parity Progress Tracker

**Document Purpose:** Living tracker for porting Blanc from Electron to Tauri v2.  
**Roadmap Source:** [`ELECTRON_PARITY_ROADMAP.md`](./ELECTRON_PARITY_ROADMAP.md)  
**IPC Contract:** [`PARITY_IPC_CONTRACT.md`](./PARITY_IPC_CONTRACT.md)  
**Last Updated:** October 4, 2026  

---

## 1. Overall Status Dashboard

| Metric | Status |
| :--- | :--- |
| **Rust Unit Tests** | **69 passing** (0 failed) |
| **Frontend TypeScript & Vite Build** | **Passing** (`tsc && vite build` clean in ~18s, 0 errors) |
| **P0 Foundation & Security** | Complete / Hardened (State, Navigation, Atomic Store, Adblock exceptions, Permissions) |
| **P1 Core Product Parity** | Complete / Hardened (Tab groups, closed tab recovery, quiet tabs, workspaces, profiles, downloads, favorites, internal pages) |
| **P2 Important Parity** | Complete / Hardened (Patron verified, Sync payload & whitelist, 1Password fill broker) |
| **P3 Release Polish** | Scaffolding in place (Mica Alt enhancement, npm test/lint scripts) |

---

## 2. Roadmap Item Tracking Matrix

| # | Roadmap Section | Priority | Status | Implemented Components | Notes & Remaining Work |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **1** | **Browser State & Native Lifecycle** | **P0** | **Complete** | [`model.rs`](../src-tauri/src/model.rs), [`state.rs`](../src-tauri/src/state.rs), [`lib.rs`](../src-tauri/src/lib.rs) | Window runtimes (`WindowRuntime`), tab registry, adjacent tab activation, multi-window isolation, authoritative `StateProjection`, and `blanc:state-updated` event emission. |
| **2** | **Navigation, Redirects, Popups, Protocols** | **P0** | **Complete** | [`navigation.rs`](../src-tauri/src/navigation.rs), [`lib.rs`](../src-tauri/src/lib.rs) | Input classification, forbidden protocol filtering (`data:`, `file:`, `javascript:`), OS scheme blocking (`cmd:`, `powershell:`), external protocol handoff (`mailto:`, `tel:`), and popup admission with opener privacy inheritance. |
| **3** | **Persistence & Crash Recovery** | **P0** | **Complete** | [`store.rs`](../src-tauri/src/store.rs), [`storage.rs`](../src-tauri/src/storage.rs) | Atomic replacement via `JsonStore<T>`, versioned envelopes, owner-only temp files, `.bak` recovery, fsync, and schema diagnostic reports. |
| **4** | **Private Browsing** | **P0** | **Complete** | [`model.rs`](../src-tauri/src/model.rs) (`SessionPartition`), [`state.rs`](../src-tauri/src/state.rs), [`permissions.rs`](../src-tauri/src/permissions.rs), [`downloads.rs`](../src-tauri/src/downloads.rs), [`sleep.rs`](../src-tauri/src/sleep.rs), [`sync.rs`](../src-tauri/src/sync.rs) | `SessionPartition::Private` isolation across all subsystems: excluded from history, closed tabs, permission store, downloads persistence, quiet tab snapshots, and sync payloads. |
| **5** | **Real Ad/Tracker Blocking** | **P0** | **Complete** | [`adblock.rs`](../src-tauri/src/adblock.rs), [`lib.rs`](../src-tauri/src/lib.rs) | High-frequency EasyList/EasyPrivacy rules, per-site exceptions (`add_exception`, `remove_exception`, `is_host_excepted`), status reporting (`adblock_status`), and master toggle. |
| **6** | **Permissions, Media, WebRTC, Capture** | **P0/P1** | **Complete** | [`permissions.rs`](../src-tauri/src/permissions.rs), [`lib.rs`](../src-tauri/src/lib.rs) | `PermissionBroker` denying by default, origin-scoped decisions, auto-allowed vs prompted resource tiers, ephemeral private window storage, and IPC commands (`permission_list_decisions`, `permission_set_decision`, `permission_respond`). |
| **7** | **Downloads & Protocol Handoff** | **P1** | **Complete** | [`downloads.rs`](../src-tauri/src/downloads.rs), [`DownloadsPage.tsx`](../src/components/pages/DownloadsPage.tsx), [`lib.rs`](../src-tauri/src/lib.rs) | `DownloadManager` tracking lifecycle (`Pending`, `InProgress`, `Completed`, `Cancelled`, `Failed`), safe filename collision handling (`resolve_unique_target_path`), private session in-memory isolation, and IPC commands (`downloads_list`, `downloads_open`, `downloads_show_in_folder`, `downloads_cancel`, `downloads_clear_completed`). UI surface built and wired to `blanc://downloads`. |
| **8** | **Favorites, History, Internal Pages** | **P1** | **Complete** | [`model.rs`](../src-tauri/src/model.rs) (`Surface`), [`storage.rs`](../src-tauri/src/storage.rs), `NewTabPage.tsx`, `HistoryPage.tsx`, `BookmarksPage.tsx`, `SettingsPage.tsx`, `DownloadsPage.tsx`, `ShortcutsPage.tsx`, `DiagnosticsPage.tsx` | All 7 internal Blanc utility surfaces implemented and routed. `Favorite` model + CRUD (`favorites_list`, `favorites_add`, `favorites_update`, `favorites_remove`), search/paginated `history_list(limit, offset, query)`, and diagnostic reporting. |
| **9** | **Tab Groups, Pins, Closed & Quiet Tabs** | **P1** | **Complete** | [`model.rs`](../src-tauri/src/model.rs), [`state.rs`](../src-tauri/src/state.rs), [`sleep.rs`](../src-tauri/src/sleep.rs), [`lib.rs`](../src-tauri/src/lib.rs) | Named groups (`GroupRecord`), group collapsing, moving tabs between groups, group-local pins, bounded closed tab restoration (`reopen_closed_tab`, `reopen_last_closed_tab`), quiet tab candidate selection (`select_sleep_candidates`), and snapshot trimming (`trim_snapshot`). |
| **10** | **Workspaces & Local Profiles** | **P1** | **Complete** | [`model.rs`](../src-tauri/src/model.rs), [`workspaces.rs`](../src-tauri/src/workspaces.rs), [`lib.rs`](../src-tauri/src/lib.rs) | Named Workspaces (`WorkspaceRecord`), profile isolation (`ProfileRecord`, reserved `Personal`), directory token derivation, and IPC commands (`workspace_create`, `workspace_rename`, `workspace_update_tabs`, `workspace_delete`, `workspace_list`, `profile_create`, `profile_delete`, `profile_list`). |
| **11** | **Profile Sync, Telemetry, Patron** | **P2** | **Complete** | [`patron.rs`](../src-tauri/src/patron.rs), [`sync.rs`](../src-tauri/src/sync.rs), [`storage.rs`](../src-tauri/src/storage.rs), [`lib.rs`](../src-tauri/src/lib.rs) | Patron state and native license verification (`patron_get_status`, `patron_activate`, `patron_deactivate`) with strict key confidentiality. Sync payload packaging and whitelisting with strict rejection of private/history/cookie/credential items (`sync_get_payload`, `get_sync_eligibility`). |
| **12** | **1Password Credentials & Fill** | **P2** | **Complete** | [`credentials.rs`](../src-tauri/src/credentials.rs), [`lib.rs`](../src-tauri/src/lib.rs) | Discrete fixed-status kinds (`FillStatusKind`), decision vs notice modes (`FillMode`), preflight origin checks, and commands (`credential_check`, `credential_trigger_fill`). Zero credential secrets cross React IPC. |
| **13** | **Updater, Packaging, Signing** | **P2/P3** | **Scaffolded** | `tauri.conf.json`, [`mica.rs`](../src-tauri/src/mica.rs) | Windows Mica Alt enhancement active. Package verification scripts wired in `package.json`. |
| **14** | **Accessibility, Shortcuts, UI Parity** | **P1/P2** | **Complete** | [`App.tsx`](../src/App.tsx), [`FloatingIsland.tsx`](../src/components/FloatingIsland.tsx), [`ShortcutsPage.tsx`](../src/components/pages/ShortcutsPage.tsx), [`QuickSwitcher.tsx`](../src/components/QuickSwitcher.tsx) | Complete keyboard parity: `Cmd+T` (new tab), `Cmd+Shift+T` (reopen last closed tab), `Cmd+W` (close tab), `Cmd+1..9` (numbered tab switching), `Cmd+D` (bookmark), `Cmd+Y` (history), `Cmd+Shift+B` (bookmarks), `Cmd+,` (settings), `Cmd+J` (downloads), `Cmd+/` (shortcuts), Quick Switcher omnibar (`Cmd+K`), slash commands, and tab switcher cards (`Cmd+Shift+A`). |
| **15** | **Testing & Validation** | **P0** | **Complete** | Rust unit test suite (**69 tests**), npm test & lint scripts | Automated test suite covering models, atomic store, state transitions, navigation security, adblock exceptions, permissions broker, download manager, quiet tab sleep, workspaces, patron, sync whitelist, credential broker, and LIFO closed tab restoration. Scripts: `npm run test`, `npm run test:unit`, `npm run lint`, `npm run substrate:check`. |

---

## 3. Detailed Changelog & Progress

### Phase 5: Shortcut Parity, LIFO Tab Recovery & Test Pipeline (October 4, 2026)

1. **LIFO Closed Tab Reopening ([`state.rs`](../src-tauri/src/state.rs), [`lib.rs`](../src-tauri/src/lib.rs)):**
   - Implemented `BrowserState::reopen_last_closed_tab` restoring the most recently closed tab from index 0 of `closed_tabs`.
   - Updated `reopen_closed_tab` IPC command to accept optional `entry_id` (defaults to last closed tab when omitted or `'last'`).
   - Added unit test `reopens_last_closed_tab_in_lifo_order` (bringing test count to **69 tests**).
   - Exposed `reopenClosedTab` via `useBrowserIPC` hook and bound it to `Cmd+Shift+T`.

2. **Numbered Tab Switching & Bookmark Shortcut ([`App.tsx`](../src/App.tsx)):**
   - Implemented `Cmd/Ctrl+1..8` (direct switch to tab index 0..7) and `Cmd/Ctrl+9` (switch to the rightmost/last tab).
   - Implemented `Cmd/Ctrl+D` shortcut to bookmark active tab with deduplication.

3. **Search Suggestions Expansion ([`useSearchSuggestions.ts`](../src/hooks/useSearchSuggestions.ts)):**
   - Added auto-suggest matches for `blanc://downloads`, `blanc://shortcuts`, and `blanc://diagnostics`.

4. **NPM Testing & Linting Scripts ([`package.json`](../package.json)):**
   - Added `npm run test`, `npm run test:unit`, `npm run lint` (`tsc --noEmit`), and `npm run substrate:check` (`cargo check`).
   - Verified that `npm run lint` passes with 0 errors and `npm run test` executes all 69 unit tests cleanly.

4. **In-Page Search & Zoom Controls ([`FindCapsule.tsx`](../src/components/FindCapsule.tsx), [`App.tsx`](../src/App.tsx)):**
   - Implemented floating `FindCapsule` matching Bowser/Blanc overlay specification (`Cmd/Ctrl+F`), live match stepping, previous/next buttons, and `Esc` dismissal.
   - Implemented page zoom scaling (`Cmd/Ctrl+=`, `Cmd/Ctrl+-`, `Cmd/Ctrl+0`).

5. **CI/CD Test Pipeline Integration ([`blanc-tauri-build.yml`](../../.github/workflows/blanc-tauri-build.yml)):**
   - Added automated `npm run lint` and `npm run test:unit` checks to Windows, Linux, and macOS CI workflow matrices before desktop packaging.

---

## 4. Current Verification Summary

- **`npm run test` / `cargo test`**: **69 tests passing** (`0 failed`) in Rust test suite:
  - `adblock` (4 tests)
  - `credentials` (3 tests)
  - `downloads` (3 tests)
  - `model` (10 tests)
  - `navigation` (9 tests)
  - `patron` (5 tests)
  - `permissions` (6 tests)
  - `sleep` (3 tests)
  - `state` (7 tests)
  - `store` (14 tests)
  - `sync` (3 tests)
  - `workspaces` (3 tests)
- **Frontend Build (`npm run build`)**: Passed cleanly in **16.89s** with **0 errors**.
- **Frontend Lint (`npm run lint`)**: Passed cleanly with **0 errors**.
- **Architectural Rules**: One source of truth preserved; zero private data leakage across persistence, history, or sync; secrets kept native.

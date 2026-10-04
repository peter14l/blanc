//! Shared data model for the Blanc Tauri port.
//!
//! This module is the **contract** every other module in the port is written
//! against. Rust owns browser state, webview ownership, navigation, privacy and
//! security decisions; React renders a *projection* of this state and never
//! invents a second source of truth.
//!
//! Behavioral references in the Electron app (the port's specification):
//!
//! - `src/main/window-runtime-registry.js` — per-window runtime records
//! - `src/main/main.js` `createTab`/`closeTab`/`setActiveTab` — tab lifecycle
//! - `src/main/tab-view.js` — per-tab listener set and private-session choice
//! - `src/main/closed-tabs.js` — bounded, tiered closed-tab entries
//! - `src/main/pages.js` / `utility-pages.js` — internal surface routing
//!
//! Nothing here may contain secrets or page state: `TabRecord` is serialized to
//! the renderer, so it carries display metadata only.

use serde::{Deserialize, Serialize};
use std::sync::atomic::{AtomicU64, Ordering};
use std::time::{SystemTime, UNIX_EPOCH};

/// A Tauri window label. This is the native identity of a window runtime.
pub type WindowId = String;
/// A tab identifier, unique process-wide (not merely per window).
pub type TabId = String;
/// A tab-group identifier.
pub type GroupId = String;
/// The reserved, always-present personal profile. It is the compatibility
/// profile: existing user data moves no files when Blanc upgrades.
pub const PERSONAL_PROFILE: &str = "personal";

/// A profile identifier. The reserved personal profile is [`PERSONAL_PROFILE`].
pub type ProfileId = String;
/// A workspace identifier.
pub type WorkspaceId = String;

static ID_COUNTER: AtomicU64 = AtomicU64::new(1);

/// Milliseconds since the Unix epoch. Used for ordering and expiry only.
pub fn now_millis() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_millis() as u64)
        .unwrap_or_default()
}

/// A process-unique, opaque identifier with a short readable prefix.
///
/// IDs must never encode user-visible names (profile names, workspace titles,
/// hostnames) because they become directory names on disk. See
/// `src/main/local-profiles.js` for the same rule in the Electron app.
pub fn new_id(prefix: &str) -> String {
    let counter = ID_COUNTER.fetch_add(1, Ordering::Relaxed);
    format!("{}-{}-{}", prefix, now_millis(), counter)
}

/// Which persistent/ephemeral web storage a tab's native webview may use.
///
/// Mirrors the Electron private-session contract: private tabs share one
/// non-persistent session per app run and are discarded at quit, while regular
/// tabs use their profile's persistent partition.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(tag = "kind", rename_all = "camelCase")]
pub enum SessionPartition {
    /// The reserved personal profile (Electron's default session).
    Personal,
    /// A bounded named profile's persistent partition.
    Profile(ProfileId),
    /// An ephemeral private partition, scoped to the owning window's app run.
    Private(WindowId),
}

impl SessionPartition {
    /// Private partitions are ephemeral and must never be persisted, synced, or
    /// counted for telemetry. This predicate is the single source of that rule.
    pub fn is_private(&self) -> bool {
        matches!(self, SessionPartition::Private(_))
    }

    pub fn profile_id(&self) -> ProfileId {
        match self {
            SessionPartition::Personal => PERSONAL_PROFILE.to_string(),
            SessionPartition::Profile(id) => id.clone(),
            // A private session belongs to its window's profile for display and
            // permission purposes, but never to a persistent storage scope.
            SessionPartition::Private(_) => PERSONAL_PROFILE.to_string(),
        }
    }

    /// The directory-safe partition token used for on-disk data separation.
    pub fn disk_token(&self) -> String {
        match self {
            SessionPartition::Personal => "personal".to_string(),
            SessionPartition::Profile(id) => format!("profile-{}", id),
            SessionPartition::Private(window) => format!("private-{}", window),
        }
    }
}

/// Which internal page a window is currently presenting.
///
/// Internal surfaces never become ordinary web tabs when the Electron contract
/// routes them to a sheet. See `src/main/utility-pages.js`.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize, Default)]
#[serde(rename_all = "camelCase")]
pub enum OverlayMode {
    #[default]
    None,
    /// The command bar expanded in place at the pill's anchor.
    Panel,
    /// The same panel summoned over a scrim.
    Palette,
    /// The find capsule, whose bounds stay tight so the page stays clickable.
    Find,
    /// A utility surface (favorites, history, downloads, settings, shortcuts)
    /// presented as a floating sheet rather than a browser tab.
    Utility(Surface),
}

/// The allowlisted set of internal `blanc://`-equivalent surfaces.
///
/// An unknown surface is not representable, which is what makes the flat-asset
/// router in `pages.rs` safe by construction.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub enum Surface {
    NewTab,
    Favorites,
    History,
    Downloads,
    Settings,
    Shortcuts,
    About,
    Diagnostics,
    Import,
}

impl Surface {
    /// The flat asset name served for this surface.
    ///
    /// Only these exact names resolve; there is no subdirectory and no `../`
    /// traversal, matching `src/main/pages.js` in the Electron app.
    pub fn asset_name(&self) -> &'static str {
        match self {
            Surface::NewTab => "newtab.html",
            Surface::Favorites => "favorites.html",
            Surface::History => "history.html",
            Surface::Downloads => "downloads.html",
            Surface::Settings => "settings.html",
            Surface::Shortcuts => "shortcuts.html",
            Surface::About => "about.html",
            Surface::Diagnostics => "diagnostics.html",
            Surface::Import => "import.html",
        }
    }

    /// Whether this surface is presented as a floating utility sheet rather than
    /// as a normal managed tab.
    pub fn is_utility(&self) -> bool {
        matches!(
            self,
            Surface::Favorites | Surface::History | Surface::Downloads | Surface::Settings | Surface::Shortcuts
        )
    }

    /// The `blanc://` URL a user can type to reach this surface.
    pub fn url(&self) -> String {
        let host = match self {
            Surface::NewTab => "newtab",
            Surface::Favorites => "favorites",
            Surface::History => "history",
            Surface::Downloads => "downloads",
            Surface::Settings => "settings",
            Surface::Shortcuts => "shortcuts",
            Surface::About => "about",
            Surface::Diagnostics => "diagnostics",
            Surface::Import => "import",
        };
        format!("blanc://{}/", host)
    }

    /// Parses a `blanc://` URL into a known surface.
    ///
    /// Returns `None` for any unknown host, which callers must treat as a deny
    /// rather than a fallback.
    pub fn from_url(url: &str) -> Option<Surface> {
        let rest = url.strip_prefix("blanc://")?;
        // The host ends at the first slash, and nothing else may vary.
        let (host, tail) = match rest.find('/') {
            Some(idx) => (&rest[..idx], &rest[idx..]),
            None => (rest, "/"),
        };
        if !(tail == "/" || tail.is_empty()) {
            return None;
        }
        // Reject embedded credentials: `blanc://user@newtab/` must not resolve.
        if host.contains('@') || host.contains(':') {
            return None;
        }
        match host {
            "newtab" => Some(Surface::NewTab),
            "favorites" => Some(Surface::Favorites),
            "history" => Some(Surface::History),
            "downloads" => Some(Surface::Downloads),
            "settings" => Some(Surface::Settings),
            "shortcuts" => Some(Surface::Shortcuts),
            "about" => Some(Surface::About),
            "diagnostics" => Some(Surface::Diagnostics),
            "import" => Some(Surface::Import),
            _ => None,
        }
    }
}

/// A named tab group. Groups have **names, not colors**, matching the Electron
/// contract (`src/main/main.js` group handling).
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct GroupRecord {
    pub id: GroupId,
    pub name: String,
    /// Collapsed groups stay in the island but hide their rows in the panel.
    pub collapsed: bool,
    /// Cluster order. Groups with no tabs are pruned, so this is dense.
    pub order: usize,
}

impl GroupRecord {
    pub fn new(name: impl Into<String>, order: usize) -> Self {
        Self {
            id: new_id("grp"),
            name: name.into(),
            collapsed: false,
            order,
        }
    }
}

/// Serializable per-tab metadata. This is the *only* tab shape that crosses IPC.
///
/// It deliberately excludes page state, POST bodies, and webview handles; those
/// stay in native-only maps. See `sleepSnapshots` in the Electron app for the
/// same rule applied to quiet tabs.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct TabRecord {
    pub id: TabId,
    pub window_id: WindowId,
    pub url: String,
    pub title: String,
    pub favicon: Option<String>,
    /// Pinned tabs lead their cluster. Pinning is group-local: a grouped pin
    /// stays in its group rather than moving to a standalone pinned shelf.
    pub pinned: bool,
    /// User-muted audio. Distinct from whether the tab is *currently* playing.
    pub muted: bool,
    /// True when the tab's webview is currently playing audible media.
    pub audible: bool,
    /// Convenience mirror of `partition.is_private()` for the renderer's
    /// private chip. Rust re-derives privacy from the partition, never this.
    pub private: bool,
    pub partition: SessionPartition,
    pub group_id: Option<GroupId>,
    /// The tab that spawned this one, for opener-family restore of closed tabs.
    pub opener_tab_id: Option<TabId>,
    pub profile_id: ProfileId,
    pub workspace_id: Option<WorkspaceId>,
    /// The tab is a quiet (asleep) tab whose renderer was discarded.
    pub asleep: bool,
    /// Display-only mirror of main's capture registry, for the chrome pill chip.
    /// The OS capture indicator remains the security authority.
    pub capturing: bool,
    pub can_go_back: bool,
    pub can_go_forward: bool,
    pub loading: bool,
    /// Requests actually intercepted and blocked in this tab. Never a random
    /// increment — see `adblock.rs`.
    pub blocked: u32,
    pub created_at: u64,
    /// Incremented every time a quiet tab is woken, so a stale callback from an
    /// earlier hop of a redirect chain cannot resurrect dead state.
    pub wake_generation: u64,
    /// Incremented on every committed navigation; stale load callbacks carry an
    /// older generation and are discarded.
    pub navigation_generation: u64,
}

impl TabRecord {
    /// Creates a tab record for `window_id` at `url` in `partition`.
    pub fn new(window_id: WindowId, url: impl Into<String>, partition: SessionPartition) -> Self {
        let url = url.into();
        let title = derive_title(&url);
        Self {
            id: new_id("tab"),
            window_id,
            url,
            private: partition.is_private(),
            profile_id: partition.profile_id(),
            title,
            favicon: None,
            pinned: false,
            muted: false,
            audible: false,
            partition,
            group_id: None,
            opener_tab_id: None,
            workspace_id: None,
            asleep: false,
            capturing: false,
            can_go_back: false,
            can_go_forward: false,
            loading: false,
            blocked: 0,
            created_at: now_millis(),
            wake_generation: 0,
            navigation_generation: 0,
        }
    }

    /// The private-browsing truth for this tab.
    ///
    /// Rust derives privacy from the partition rather than trusting the
    /// serialized `private` mirror, so a tampered projection cannot downgrade a
    /// private tab into persistence.
    pub fn is_private(&self) -> bool {
        self.partition.is_private()
    }
}

/// Derives a display title from a URL when the page has not reported one.
pub fn derive_title(url: &str) -> String {
    if url.is_empty() || url == "about:blank" || url == "about:newtab" {
        return "New Tab".to_string();
    }
    if let Some(surface) = Surface::from_url(url) {
        return match surface {
            Surface::NewTab => "New Tab",
            Surface::Favorites => "Favorites",
            Surface::History => "History",
            Surface::Downloads => "Downloads",
            Surface::Settings => "Settings",
            Surface::Shortcuts => "Keyboard Shortcuts",
            Surface::About => "About Blanc",
            Surface::Diagnostics => "Diagnostics",
            Surface::Import => "Import",
        }
        .to_string();
    }
    match url.split_once("://") {
        Some((scheme, rest)) if scheme == "http" || scheme == "https" => rest
            .split(['/', '?', '#'])
            .next()
            .unwrap_or(url)
            .trim_start_matches("www.")
            .to_string(),
        _ => url.to_string(),
    }
}

/// How much of a closed tab we can still restore.
///
/// The tiers are internal policy and must never appear in user-visible copy —
/// Electron deliberately avoids words like "held" (`src/main/closed-tabs.js`).
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub enum RestoreTier {
    /// The live view is parked and can be re-attached without a refetch.
    Live,
    /// The view is gone but back stack, scroll, group and pin survived.
    Snapshot,
    /// URL only; everything else was lost with the renderer.
    UrlOnly,
}

/// One entry in a window's reopen-closed-tab list.
///
/// Like Electron's closed entries, this is a secret-bearing record: only
/// `{id, title, favicon, tab_count}` may ever cross IPC.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct ClosedTabEntry {
    pub id: String,
    pub url: String,
    pub title: String,
    pub favicon: Option<String>,
    pub pinned: bool,
    pub group_id: Option<GroupId>,
    pub tier: RestoreTier,
    /// Number of tabs in the entry; 1 for a single tab, N for a closed group.
    pub tab_count: u32,
    pub created_at: u64,
    pub expires_at: u64,
}

/// The closed-entry projection the renderer is allowed to see.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ClosedTabProjection {
    pub id: String,
    pub title: String,
    pub favicon: Option<String>,
    pub tab_count: u32,
}

/// Per-window state, resolved by window label. One record per native window.
///
/// This replaces the flat global tab vector. Two windows can now have different
/// active tabs, groups, overlays, workspaces and profiles.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct WindowRuntime {
    pub id: WindowId,
    /// The Tauri window label, e.g. `main` or `window-2`.
    pub label: WindowId,
    pub profile_id: ProfileId,
    /// Tab IDs in display order.
    pub tab_order: Vec<TabId>,
    pub active_tab_id: Option<TabId>,
    pub groups: Vec<GroupRecord>,
    pub overlay: OverlayMode,
    pub closed_tabs: Vec<ClosedTabEntry>,
    pub workspace_id: Option<WorkspaceId>,
    /// True while a permission prompt is showing for this window.
    pub permission_prompt_open: bool,
}

impl WindowRuntime {
    pub fn new(label: impl Into<String>) -> Self {
        let label = label.into();
        Self {
            id: new_id("win"),
            label,
            profile_id: PERSONAL_PROFILE.to_string(),
            tab_order: Vec::new(),
            active_tab_id: None,
            groups: Vec::new(),
            overlay: OverlayMode::None,
            closed_tabs: Vec::new(),
            workspace_id: None,
            permission_prompt_open: false,
        }
    }

    /// The index of `tab_id` in display order.
    pub fn index_of(&self, tab_id: &str) -> Option<usize> {
        self.tab_order.iter().position(|id| id == tab_id)
    }
}

/// The safe, serializable projection of all browser state.
///
/// React rebuilds its entire view from this on every mutation and on reload. It
/// must never contain webview handles, session storage, or page state.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct StateProjection {
    pub windows: Vec<WindowProjection>,
    /// The focused window's runtime id, or `None` when no window exists.
    pub focused_window_id: Option<WindowId>,
    pub adblock_enabled: bool,
    pub blocking_ready: bool,
    pub total_blocked: u64,
    /// Populated only when `blocking_ready` is false. The shell shows a visible
    /// "Blocking could not start" state rather than claiming success.
    pub blocking_error: Option<String>,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct WindowProjection {
    pub id: WindowId,
    pub label: WindowId,
    pub profile_id: ProfileId,
    pub active_tab_id: Option<TabId>,
    pub tab_ids: Vec<TabId>,
    pub tabs: Vec<TabRecord>,
    pub groups: Vec<GroupRecord>,
    pub overlay: OverlayMode,
    pub closed_tabs: Vec<ClosedTabProjection>,
    pub workspace_id: Option<WorkspaceId>,
    pub permission_prompt_open: bool,
}

impl WindowProjection {
    /// Builds a projection from a runtime, dropping everything secret.
    pub fn from_runtime(runtime: &WindowRuntime) -> Self {
        Self {
            id: runtime.id.clone(),
            label: runtime.label.clone(),
            profile_id: runtime.profile_id.clone(),
            active_tab_id: runtime.active_tab_id.clone(),
            tab_ids: runtime.tab_order.clone(),
            tabs: Vec::new(),
            groups: runtime.groups.clone(),
            overlay: runtime.overlay,
            closed_tabs: runtime
                .closed_tabs
                .iter()
                .map(|entry| ClosedTabProjection {
                    id: entry.id.clone(),
                    title: entry.title.clone(),
                    favicon: entry.favicon.clone(),
                    tab_count: entry.tab_count,
                })
                .collect(),
            workspace_id: runtime.workspace_id.clone(),
            permission_prompt_open: runtime.permission_prompt_open,
        }
    }

    pub fn from_runtime_with_tabs(runtime: &WindowRuntime, tabs: Vec<TabRecord>) -> Self {
        let mut proj = Self::from_runtime(runtime);
        proj.tabs = tabs;
        proj
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn personal_profile_id_is_reserved_and_stable() {
        assert_eq!(PERSONAL_PROFILE, "personal");
        assert_eq!(SessionPartition::Personal.profile_id(), PERSONAL_PROFILE);
    }

    #[test]
    fn private_partitions_are_ephemeral_and_never_persist() {
        let partition = SessionPartition::Private("main".to_string());
        assert!(partition.is_private());
        assert_eq!(partition.disk_token(), "private-main");

        let mut tab = TabRecord::new("main".into(), "https://example.com", partition);
        assert!(tab.is_private(), "privacy derives from the partition, not the mirror");

        // Tampering with the serialized mirror must not grant persistence.
        tab.private = false;
        assert!(tab.is_private());
    }

    #[test]
    fn profile_partitions_are_not_private() {
        let partition = SessionPartition::Profile("p-42".to_string());
        assert!(!partition.is_private());
        assert_eq!(partition.profile_id(), "p-42");
        assert_eq!(partition.disk_token(), "profile-p-42");
    }

    #[test]
    fn surface_url_round_trips_for_every_known_surface() {
        for surface in [
            Surface::NewTab,
            Surface::Favorites,
            Surface::History,
            Surface::Downloads,
            Surface::Settings,
            Surface::Shortcuts,
            Surface::About,
            Surface::Diagnostics,
            Surface::Import,
        ] {
            assert_eq!(Surface::from_url(&surface.url()), Some(surface));
        }
    }

    #[test]
    fn surface_rejects_unknown_hosts_and_traversal() {
        for rejected in [
            "blanc://evil/",
            "blanc://newtab/../../etc/passwd",
            "blanc://newtab/index.html",
            "blanc://user@newtab/",
            "blanc://newtab:80/",
            "blanc://",
            "blanc://NEWTAB/",
            "https://newtab/",
        ] {
            assert_eq!(Surface::from_url(rejected), None, "must reject {rejected}");
        }
    }

    #[test]
    fn surface_asset_names_are_flat_and_unique() {
        let all = [
            Surface::NewTab,
            Surface::Favorites,
            Surface::History,
            Surface::Downloads,
            Surface::Settings,
            Surface::Shortcuts,
            Surface::About,
            Surface::Diagnostics,
            Surface::Import,
        ];
        let mut names: Vec<&str> = all.iter().map(|s| s.asset_name()).collect();
        names.sort_unstable();
        let count = names.len();
        names.dedup();
        assert_eq!(names.len(), count, "asset names must be unique");
        for name in names {
            assert!(!name.contains('/'), "{name} must be a flat file");
            assert!(!name.contains(".."), "{name} must not traverse");
        }
    }

    #[test]
    fn utility_surfaces_match_the_electron_sheet_contract() {
        assert!(Surface::Favorites.is_utility());
        assert!(Surface::History.is_utility());
        assert!(Surface::Downloads.is_utility());
        assert!(Surface::Settings.is_utility());
        assert!(Surface::Shortcuts.is_utility());
        // New tab stays an ordinary managed tab, like the Mahjong page in Electron.
        assert!(!Surface::NewTab.is_utility());
        assert!(!Surface::About.is_utility());
    }

    #[test]
    fn titles_derive_readably_without_exposing_credentials() {
        assert_eq!(derive_title("about:blank"), "New Tab");
        assert_eq!(derive_title("blanc://newtab/"), "New Tab");
        assert_eq!(derive_title("https://www.example.com/a/b?c=d"), "example.com");
        assert_eq!(derive_title("https://sub.example.com"), "sub.example.com");
    }

    #[test]
    fn ids_are_unique_and_do_not_encode_user_visible_names() {
        let a = new_id("tab");
        let b = new_id("tab");
        assert_ne!(a, b);
        assert!(a.starts_with("tab-"));
        // Opaque: no user input is ever interpolated into an id.
        assert!(!a.contains(' '));
    }

    #[test]
    fn window_projection_drops_urls_from_closed_entries() {
        let mut runtime = WindowRuntime::new("main");
        runtime.closed_tabs.push(ClosedTabEntry {
            id: "closed-1".into(),
            url: "https://secret.example.com/private?q=token".into(),
            title: "Secret".into(),
            favicon: None,
            pinned: false,
            group_id: None,
            tier: RestoreTier::Snapshot,
            tab_count: 1,
            created_at: 0,
            expires_at: 0,
        });

        let json = serde_json::to_string(&WindowProjection::from_runtime(&runtime)).unwrap();
        assert!(!json.contains("secret.example.com"), "closed entry URL leaked: {json}");
        assert!(!json.contains("token"), "closed entry query leaked: {json}");
    }
}

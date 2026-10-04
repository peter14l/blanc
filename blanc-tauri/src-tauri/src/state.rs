//! Authoritative native browser state.
//!
//! Behavioral references in the Electron app (the port's specification):
//!
//! - `src/main/window-runtime-registry.js` — per-window runtime lifecycle and lookup
//! - `src/main/main.js` `createTab`/`closeTab`/`setActiveTab` — tab state machine
//! - `src/main/closed-tabs.js` — bounded closed-tab hold window and private exclusion
//! - `src/main/tab-order.js` — deterministic tab ordering and adjacent selection
//!
//! Rust is the single source of truth for browser state. React renders a projection
//! of this state and never invents or stores browser records in localStorage.

use std::collections::HashMap;
use crate::model::{
    new_id, now_millis, ClosedTabEntry, GroupId, GroupRecord, ProfileId, RestoreTier,
    SessionPartition, StateProjection, TabId, TabRecord, WindowId,
    WindowProjection, WindowRuntime, PERSONAL_PROFILE,
};

/// Maximum closed tab entries remembered per window.
const MAX_CLOSED_TABS_PER_WINDOW: usize = 25;
/// Closed tab hold time before degradation (10 minutes in millis).
const CLOSED_TAB_TTL_MILLIS: u64 = 10 * 60 * 1000;

#[derive(Debug)]
pub struct BrowserState {
    pub windows: HashMap<WindowId, WindowRuntime>,
    pub tabs: HashMap<TabId, TabRecord>,
    pub focused_window_id: Option<WindowId>,
    pub adblock_enabled: bool,
    pub blocking_ready: bool,
    pub total_blocked: u64,
    pub blocking_error: Option<String>,
}

impl Default for BrowserState {
    fn default() -> Self {
        Self::new()
    }
}

impl BrowserState {
    pub fn new() -> Self {
        Self {
            windows: HashMap::new(),
            tabs: HashMap::new(),
            focused_window_id: None,
            adblock_enabled: true,
            blocking_ready: true,
            total_blocked: 0,
            blocking_error: None,
        }
    }

    /// Initializes a browser state with the default main window and an initial tab.
    pub fn with_default_window() -> Self {
        let mut state = Self::new();
        let _ = state.ensure_window("main", Some(PERSONAL_PROFILE.to_string()), false);
        let _ = state.create_tab("main", Some("blanc://newtab/".to_string()), false, None);
        state
    }

    /// Ensures a window runtime exists for `label`, creating it if missing.
    pub fn ensure_window(
        &mut self,
        label: &str,
        profile_id: Option<ProfileId>,
        _private: bool,
    ) -> &mut WindowRuntime {
        if !self.windows.contains_key(label) {
            let mut runtime = WindowRuntime::new(label);
            if let Some(p) = profile_id {
                runtime.profile_id = p;
            }
            if self.focused_window_id.is_none() {
                self.focused_window_id = Some(runtime.id.clone());
            }
            self.windows.insert(label.to_string(), runtime);
        }
        self.windows.get_mut(label).unwrap()
    }

    pub fn get_window(&self, label: &str) -> Option<&WindowRuntime> {
        self.windows.get(label)
    }

    pub fn get_window_mut(&mut self, label: &str) -> Option<&mut WindowRuntime> {
        self.windows.get_mut(label)
    }

    /// Creates a new native window runtime record.
    pub fn create_window(
        &mut self,
        label: Option<String>,
        profile_id: Option<ProfileId>,
        private: bool,
    ) -> &WindowRuntime {
        let label = label.unwrap_or_else(|| new_id("win"));
        let runtime = self.ensure_window(&label, profile_id, private);
        runtime
    }

    /// Closes a window runtime and all tabs owned by it.
    pub fn close_window(&mut self, label: &str) -> Option<WindowRuntime> {
        let runtime = self.windows.remove(label)?;
        for tab_id in &runtime.tab_order {
            self.tabs.remove(tab_id);
        }
        if self.focused_window_id.as_deref() == Some(&runtime.id) {
            self.focused_window_id = self.windows.values().next().map(|w| w.id.clone());
        }
        Some(runtime)
    }

    pub fn focus_window(&mut self, label: &str) {
        if let Some(runtime) = self.windows.get(label) {
            self.focused_window_id = Some(runtime.id.clone());
        }
    }

    // -------------------------------------------------------------------------
    // TAB OPERATIONS
    // -------------------------------------------------------------------------

    /// Creates a tab in the specified window runtime.
    pub fn create_tab(
        &mut self,
        window_label: &str,
        url: Option<String>,
        private: bool,
        group_id: Option<GroupId>,
    ) -> Result<TabRecord, String> {
        let partition = if private {
            SessionPartition::Private(window_label.to_string())
        } else {
            let profile = self
                .windows
                .get(window_label)
                .map(|w| w.profile_id.clone())
                .unwrap_or_else(|| PERSONAL_PROFILE.to_string());
            SessionPartition::Profile(profile)
        };

        let target_url = url.unwrap_or_else(|| "blanc://newtab/".to_string());
        let mut tab = TabRecord::new(window_label.to_string(), target_url, partition);
        tab.group_id = group_id;

        let tab_id = tab.id.clone();
        self.tabs.insert(tab_id.clone(), tab.clone());

        let runtime = self.ensure_window(window_label, None, private);
        runtime.tab_order.push(tab_id.clone());
        runtime.active_tab_id = Some(tab_id);

        Ok(tab)
    }

    /// Closes a tab by ID. If active, activates the adjacent tab.
    /// Non-private tabs are recorded in the window's closed-tabs list.
    pub fn close_tab(&mut self, tab_id: &str) -> Result<Option<TabRecord>, String> {
        let tab = self
            .tabs
            .remove(tab_id)
            .ok_or_else(|| format!("Tab '{}' not found", tab_id))?;

        let window_label = tab.window_id.clone();
        let runtime = self
            .windows
            .get_mut(&window_label)
            .ok_or_else(|| format!("Window '{}' not found for tab", window_label))?;

        // Record in closed tabs if NOT private and NOT a blank new tab
        if !tab.is_private() && tab.url != "about:blank" && tab.url != "blanc://newtab/" {
            let entry = ClosedTabEntry {
                id: new_id("closed"),
                url: tab.url.clone(),
                title: tab.title.clone(),
                favicon: tab.favicon.clone(),
                pinned: tab.pinned,
                group_id: tab.group_id.clone(),
                tier: RestoreTier::Snapshot,
                tab_count: 1,
                created_at: now_millis(),
                expires_at: now_millis() + CLOSED_TAB_TTL_MILLIS,
            };
            runtime.closed_tabs.insert(0, entry);
            if runtime.closed_tabs.len() > MAX_CLOSED_TABS_PER_WINDOW {
                runtime.closed_tabs.pop();
            }
        }

        // Remove from window tab order
        let index = runtime.tab_order.iter().position(|id| id == tab_id);
        if let Some(idx) = index {
            runtime.tab_order.remove(idx);
        }

        // Determine newly active tab if this tab was active
        let next_active = if runtime.active_tab_id.as_deref() == Some(tab_id) {
            if runtime.tab_order.is_empty() {
                runtime.active_tab_id = None;
                None
            } else {
                let new_idx = if let Some(idx) = index {
                    if idx > 0 { idx - 1 } else { 0 }
                } else {
                    0
                };
                let new_id = runtime.tab_order[new_idx].clone();
                runtime.active_tab_id = Some(new_id.clone());
                self.tabs.get(&new_id).cloned()
            }
        } else {
            runtime
                .active_tab_id
                .as_ref()
                .and_then(|id| self.tabs.get(id).cloned())
        };

        Ok(next_active)
    }

    /// Closes all tabs in a window.
    pub fn close_all_tabs(&mut self, window_label: &str) -> Result<(), String> {
        let runtime = self
            .windows
            .get_mut(window_label)
            .ok_or_else(|| format!("Window '{}' not found", window_label))?;

        let old_tabs: Vec<TabId> = runtime.tab_order.drain(..).collect();
        runtime.active_tab_id = None;

        for id in old_tabs {
            self.tabs.remove(&id);
        }

        Ok(())
    }

    /// Switches the active tab in a window.
    pub fn switch_tab(&mut self, tab_id: &str) -> Result<(), String> {
        let tab = self
            .tabs
            .get(tab_id)
            .ok_or_else(|| format!("Tab '{}' not found", tab_id))?;
        let window_label = tab.window_id.clone();

        let runtime = self
            .windows
            .get_mut(&window_label)
            .ok_or_else(|| format!("Window '{}' not found", window_label))?;

        if !runtime.tab_order.iter().any(|id| id == tab_id) {
            return Err(format!("Tab '{}' not part of window '{}'", tab_id, window_label));
        }

        runtime.active_tab_id = Some(tab_id.to_string());
        Ok(())
    }

    /// Duplicates an existing tab, inheriting its partition, group, and pinned state.
    pub fn duplicate_tab(&mut self, tab_id: &str) -> Result<TabRecord, String> {
        let tab = self
            .tabs
            .get(tab_id)
            .ok_or_else(|| format!("Tab '{}' not found", tab_id))?
            .clone();

        let mut dup = TabRecord::new(tab.window_id.clone(), tab.url.clone(), tab.partition.clone());
        dup.title = tab.title.clone();
        dup.favicon = tab.favicon.clone();
        dup.pinned = tab.pinned;
        dup.group_id = tab.group_id.clone();
        dup.opener_tab_id = Some(tab_id.to_string());

        let dup_id = dup.id.clone();
        self.tabs.insert(dup_id.clone(), dup.clone());

        if let Some(runtime) = self.windows.get_mut(&tab.window_id) {
            // Insert adjacent to original tab
            if let Some(idx) = runtime.tab_order.iter().position(|id| id == tab_id) {
                runtime.tab_order.insert(idx + 1, dup_id.clone());
            } else {
                runtime.tab_order.push(dup_id.clone());
            }
            runtime.active_tab_id = Some(dup_id);
        }

        Ok(dup)
    }

    pub fn set_tab_pinned(&mut self, tab_id: &str, pinned: bool) -> Result<(), String> {
        let tab = self
            .tabs
            .get_mut(tab_id)
            .ok_or_else(|| format!("Tab '{}' not found", tab_id))?;
        tab.pinned = pinned;
        Ok(())
    }

    pub fn set_tab_muted(&mut self, tab_id: &str, muted: bool) -> Result<(), String> {
        let tab = self
            .tabs
            .get_mut(tab_id)
            .ok_or_else(|| format!("Tab '{}' not found", tab_id))?;
        tab.muted = muted;
        Ok(())
    }

    pub fn update_tab_navigation(
        &mut self,
        tab_id: &str,
        url: Option<String>,
        title: Option<String>,
    ) -> Result<(), String> {
        let tab = self
            .tabs
            .get_mut(tab_id)
            .ok_or_else(|| format!("Tab '{}' not found", tab_id))?;

        if let Some(u) = url {
            tab.url = u;
            tab.navigation_generation += 1;
        }
        if let Some(t) = title {
            tab.title = t;
        }
        tab.loading = false;
        Ok(())
    }

    pub fn set_tab_loading(&mut self, tab_id: &str, loading: bool) -> Result<(), String> {
        let tab = self
            .tabs
            .get_mut(tab_id)
            .ok_or_else(|| format!("Tab '{}' not found", tab_id))?;
        tab.loading = loading;
        Ok(())
    }

    pub fn increment_tab_blocked(&mut self, tab_id: &str, count: u32) -> Result<(), String> {
        let tab = self
            .tabs
            .get_mut(tab_id)
            .ok_or_else(|| format!("Tab '{}' not found", tab_id))?;
        tab.blocked += count;
        self.total_blocked += count as u64;
        Ok(())
    }

    pub fn set_tab_can_go_back_forward(
        &mut self,
        tab_id: &str,
        can_go_back: bool,
        can_go_forward: bool,
    ) -> Result<(), String> {
        let tab = self
            .tabs
            .get_mut(tab_id)
            .ok_or_else(|| format!("Tab '{}' not found", tab_id))?;
        tab.can_go_back = can_go_back;
        tab.can_go_forward = can_go_forward;
        Ok(())
    }

    // -------------------------------------------------------------------------
    // TAB GROUPS
    // -------------------------------------------------------------------------

    pub fn create_group(
        &mut self,
        window_label: &str,
        name: String,
    ) -> Result<GroupRecord, String> {
        let runtime = self
            .windows
            .get_mut(window_label)
            .ok_or_else(|| format!("Window '{}' not found", window_label))?;

        let order = runtime.groups.len();
        let group = GroupRecord::new(name, order);
        runtime.groups.push(group.clone());
        Ok(group)
    }

    pub fn rename_group(&mut self, group_id: &str, name: String) -> Result<(), String> {
        for runtime in self.windows.values_mut() {
            if let Some(g) = runtime.groups.iter_mut().find(|g| g.id == group_id) {
                g.name = name;
                return Ok(());
            }
        }
        Err(format!("Group '{}' not found", group_id))
    }

    pub fn set_group_collapsed(&mut self, group_id: &str, collapsed: bool) -> Result<(), String> {
        for runtime in self.windows.values_mut() {
            if let Some(g) = runtime.groups.iter_mut().find(|g| g.id == group_id) {
                g.collapsed = collapsed;
                return Ok(());
            }
        }
        Err(format!("Group '{}' not found", group_id))
    }

    pub fn move_tab_to_group(&mut self, tab_id: &str, group_id: Option<String>) -> Result<(), String> {
        let tab = self
            .tabs
            .get_mut(tab_id)
            .ok_or_else(|| format!("Tab '{}' not found", tab_id))?;
        tab.group_id = group_id;
        Ok(())
    }

    pub fn close_group(&mut self, group_id: &str) -> Result<(), String> {
        let tabs_to_close: Vec<TabId> = self
            .tabs
            .values()
            .filter(|t| t.group_id.as_deref() == Some(group_id))
            .map(|t| t.id.clone())
            .collect();

        for tab_id in tabs_to_close {
            let _ = self.close_tab(&tab_id);
        }

        for runtime in self.windows.values_mut() {
            runtime.groups.retain(|g| g.id != group_id);
        }

        Ok(())
    }

    // -------------------------------------------------------------------------
    // CLOSED TABS RECOVERY
    // -------------------------------------------------------------------------

    pub fn reopen_closed_tab(
        &mut self,
        entry_id: &str,
        window_label: &str,
    ) -> Result<Option<TabRecord>, String> {
        let entry_idx = self
            .windows
            .get(window_label)
            .and_then(|w| w.closed_tabs.iter().position(|e| e.id == entry_id));

        let entry = if let Some(idx) = entry_idx {
            let runtime = self.windows.get_mut(window_label).unwrap();
            runtime.closed_tabs.remove(idx)
        } else {
            return Err(format!("Closed tab entry '{}' not found", entry_id));
        };

        let tab = self.create_tab(window_label, Some(entry.url), false, entry.group_id)?;
        Ok(Some(tab))
    }

    /// Reopens the most recently closed tab in `window_label`.
    pub fn reopen_last_closed_tab(
        &mut self,
        window_label: &str,
    ) -> Result<Option<TabRecord>, String> {
        let entry = {
            let runtime = self
                .windows
                .get_mut(window_label)
                .ok_or_else(|| format!("Window '{}' not found", window_label))?;
            if !runtime.closed_tabs.is_empty() {
                Some(runtime.closed_tabs.remove(0))
            } else {
                None
            }
        };

        if let Some(entry) = entry {
            let tab = self.create_tab(window_label, Some(entry.url), false, entry.group_id)?;
            Ok(Some(tab))
        } else {
            Ok(None)
        }
    }

    pub fn forget_closed_tab(&mut self, entry_id: &str) -> Result<(), String> {
        for runtime in self.windows.values_mut() {
            if let Some(idx) = runtime.closed_tabs.iter().position(|e| e.id == entry_id) {
                runtime.closed_tabs.remove(idx);
                return Ok(());
            }
        }
        Err(format!("Closed tab entry '{}' not found", entry_id))
    }

    pub fn clear_closed_tabs(&mut self, window_label: &str) -> Result<(), String> {
        let runtime = self
            .windows
            .get_mut(window_label)
            .ok_or_else(|| format!("Window '{}' not found", window_label))?;
        runtime.closed_tabs.clear();
        Ok(())
    }

    // -------------------------------------------------------------------------
    // PROJECTIONS
    // -------------------------------------------------------------------------

    /// Builds the safe, serializable state projection of all browser windows and tabs.
    pub fn projection(&self) -> StateProjection {
        let windows: Vec<WindowProjection> = self
            .windows
            .values()
            .map(|w| {
                let tabs: Vec<TabRecord> = w
                    .tab_order
                    .iter()
                    .filter_map(|id| self.tabs.get(id).cloned())
                    .collect();
                WindowProjection::from_runtime_with_tabs(w, tabs)
            })
            .collect();

        StateProjection {
            windows,
            focused_window_id: self.focused_window_id.clone(),
            adblock_enabled: self.adblock_enabled,
            blocking_ready: self.blocking_ready,
            total_blocked: self.total_blocked,
            blocking_error: self.blocking_error.clone(),
        }
    }

    /// Builds the projection for a single window.
    pub fn window_projection(&self, window_label: &str) -> Option<WindowProjection> {
        let runtime = self.windows.get(window_label)?;
        let tabs: Vec<TabRecord> = runtime
            .tab_order
            .iter()
            .filter_map(|id| self.tabs.get(id).cloned())
            .collect();
        Some(WindowProjection::from_runtime_with_tabs(runtime, tabs))
    }

    pub fn get_tabs_for_window(&self, window_label: &str) -> Vec<TabRecord> {
        if let Some(runtime) = self.windows.get(window_label) {
            runtime
                .tab_order
                .iter()
                .filter_map(|id| self.tabs.get(id).cloned())
                .collect()
        } else {
            Vec::new()
        }
    }

    pub fn get_active_tab_for_window(&self, window_label: &str) -> Option<TabRecord> {
        let runtime = self.windows.get(window_label)?;
        let active_id = runtime.active_tab_id.as_deref()?;
        self.tabs.get(active_id).cloned()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn default_window_initializes_with_one_tab() {
        let state = BrowserState::with_default_window();
        assert_eq!(state.windows.len(), 1);
        let win = state.get_window("main").unwrap();
        assert_eq!(win.tab_order.len(), 1);
        assert!(win.active_tab_id.is_some());
        assert_eq!(state.tabs.len(), 1);
    }

    #[test]
    fn multi_window_isolation() {
        let mut state = BrowserState::new();
        state.ensure_window("win-1", None, false);
        state.ensure_window("win-2", None, false);

        let tab1 = state
            .create_tab("win-1", Some("https://example.com".into()), false, None)
            .unwrap();
        let tab2 = state
            .create_tab("win-2", Some("https://blancbrowser.com".into()), false, None)
            .unwrap();

        assert_eq!(state.get_window("win-1").unwrap().active_tab_id, Some(tab1.id));
        assert_eq!(state.get_window("win-2").unwrap().active_tab_id, Some(tab2.id));
        assert_ne!(
            state.get_window("win-1").unwrap().active_tab_id,
            state.get_window("win-2").unwrap().active_tab_id
        );
    }

    #[test]
    fn closing_tab_activates_adjacent_tab() {
        let mut state = BrowserState::new();
        state.ensure_window("main", None, false);
        let t1 = state.create_tab("main", Some("https://1.com".into()), false, None).unwrap();
        let t2 = state.create_tab("main", Some("https://2.com".into()), false, None).unwrap();
        let t3 = state.create_tab("main", Some("https://3.com".into()), false, None).unwrap();

        assert_eq!(state.get_window("main").unwrap().active_tab_id, Some(t3.id.clone()));

        // Close active tab t3 -> t2 should become active
        let next = state.close_tab(&t3.id).unwrap();
        assert_eq!(next.unwrap().id, t2.id);
        assert_eq!(state.get_window("main").unwrap().active_tab_id, Some(t2.id.clone()));

        // Close active tab t2 -> t1 should become active
        let next2 = state.close_tab(&t2.id).unwrap();
        assert_eq!(next2.unwrap().id, t1.id);
        assert_eq!(state.get_window("main").unwrap().active_tab_id, Some(t1.id));
    }

    #[test]
    fn private_tabs_never_enter_closed_tabs_history() {
        let mut state = BrowserState::new();
        state.ensure_window("main", None, false);

        // Regular tab
        let reg = state.create_tab("main", Some("https://regular.com".into()), false, None).unwrap();
        state.close_tab(&reg.id).unwrap();

        // Private tab
        let priv_tab = state.create_tab("main", Some("https://secret.com".into()), true, None).unwrap();
        state.close_tab(&priv_tab.id).unwrap();

        let win = state.get_window("main").unwrap();
        assert_eq!(win.closed_tabs.len(), 1);
        assert_eq!(win.closed_tabs[0].url, "https://regular.com");
    }

    #[test]
    fn tab_groups_and_moving_tabs() {
        let mut state = BrowserState::new();
        state.ensure_window("main", None, false);

        let group = state.create_group("main", "Research".to_string()).unwrap();
        let tab = state.create_tab("main", Some("https://arxiv.org".into()), false, Some(group.id.clone())).unwrap();

        assert_eq!(tab.group_id, Some(group.id.clone()));

        // Move out of group
        state.move_tab_to_group(&tab.id, None).unwrap();
        assert_eq!(state.tabs.get(&tab.id).unwrap().group_id, None);
    }

    #[test]
    fn state_projection_contains_accurate_window_and_tab_hierarchy() {
        let state = BrowserState::with_default_window();
        let proj = state.projection();

        assert_eq!(proj.windows.len(), 1);
        assert_eq!(proj.windows[0].tabs.len(), 1);
        assert!(proj.adblock_enabled);
        assert!(proj.blocking_ready);
    }

    #[test]
    fn reopens_last_closed_tab_in_lifo_order() {
        let mut state = BrowserState::new();
        state.ensure_window("main", None, false);

        let t1 = state.create_tab("main", Some("https://site-a.com".into()), false, None).unwrap();
        let t2 = state.create_tab("main", Some("https://site-b.com".into()), false, None).unwrap();

        state.close_tab(&t1.id).unwrap();
        state.close_tab(&t2.id).unwrap();

        // Reopen last closed tab (should be t2 / site-b)
        let reopened_1 = state.reopen_last_closed_tab("main").unwrap().unwrap();
        assert_eq!(reopened_1.url, "https://site-b.com");

        // Reopen next closed tab (should be t1 / site-a)
        let reopened_2 = state.reopen_last_closed_tab("main").unwrap().unwrap();
        assert_eq!(reopened_2.url, "https://site-a.com");

        // No more closed tabs
        let empty = state.reopen_last_closed_tab("main").unwrap();
        assert!(empty.is_none());
    }
}

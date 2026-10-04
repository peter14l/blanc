use serde::{Deserialize, Serialize};
use std::sync::atomic::{AtomicU64, Ordering};
use std::time::{SystemTime, UNIX_EPOCH};

static TAB_COUNTER: AtomicU64 = AtomicU64::new(1);

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Tab {
    pub id: String,
    pub url: String,
    pub title: String,
    pub is_active: bool,
    pub is_loading: bool,
    pub blocked_trackers: u32,
    pub can_go_back: bool,
    pub can_go_forward: bool,
}

impl Tab {
    pub fn new(id: String, url: String) -> Self {
        Self {
            id,
            url: url.clone(),
            title: if url.is_empty() || url == "about:blank" {
                "New Tab".to_string()
            } else {
                url
            },
            is_active: false,
            is_loading: false,
            blocked_trackers: 0,
            can_go_back: false,
            can_go_forward: false,
        }
    }
}

#[derive(Debug, Default)]
pub struct BrowserState {
    pub tabs: Vec<Tab>,
    pub active_tab_id: Option<String>,
}

impl BrowserState {
    pub fn new() -> Self {
        Self {
            tabs: Vec::new(),
            active_tab_id: None,
        }
    }

    pub fn generate_tab_id() -> String {
        let counter = TAB_COUNTER.fetch_add(1, Ordering::Relaxed);
        let millis = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap_or_default()
            .as_millis();
        format!("tab-{}-{}", millis, counter)
    }

    pub fn add_tab(&mut self, url: Option<String>) -> Tab {
        let tab_id = Self::generate_tab_id();
        let target_url = url.unwrap_or_else(|| "about:blank".to_string());
        let mut tab = Tab::new(tab_id.clone(), target_url);

        // Deactivate all existing tabs
        for existing in &mut self.tabs {
            existing.is_active = false;
        }

        // New tab becomes active
        tab.is_active = true;
        self.active_tab_id = Some(tab_id);
        self.tabs.push(tab.clone());

        tab
    }

    pub fn remove_tab(&mut self, tab_id: &str) -> Option<Tab> {
        let index = self.tabs.iter().position(|t| t.id == tab_id)?;
        let removed = self.tabs.remove(index);

        if self.active_tab_id.as_deref() == Some(tab_id) {
            if self.tabs.is_empty() {
                self.active_tab_id = None;
            } else {
                // Activate the adjacent tab (prioritize previous, or index 0)
                let new_index = if index > 0 { index - 1 } else { 0 };
                for (i, tab) in self.tabs.iter_mut().enumerate() {
                    tab.is_active = i == new_index;
                }
                self.active_tab_id = Some(self.tabs[new_index].id.clone());
            }
        }

        Some(removed)
    }

    pub fn get_active_tab(&self) -> Option<Tab> {
        self.active_tab_id
            .as_ref()
            .and_then(|id| self.tabs.iter().find(|t| &t.id == id).cloned())
    }

    pub fn set_active_tab(&mut self, tab_id: &str) -> Result<(), String> {
        let mut found = false;
        for tab in &mut self.tabs {
            if tab.id == tab_id {
                tab.is_active = true;
                found = true;
            } else {
                tab.is_active = false;
            }
        }

        if found {
            self.active_tab_id = Some(tab_id.to_string());
            Ok(())
        } else {
            Err(format!("Tab with id '{}' not found", tab_id))
        }
    }

    pub fn update_tab_url(&mut self, tab_id: &str, url: String) -> Result<(), String> {
        if let Some(tab) = self.tabs.iter_mut().find(|t| t.id == tab_id) {
            tab.url = url;
            Ok(())
        } else {
            Err(format!("Tab with id '{}' not found", tab_id))
        }
    }

    pub fn update_tab_title(&mut self, tab_id: &str, title: String) -> Result<(), String> {
        if let Some(tab) = self.tabs.iter_mut().find(|t| t.id == tab_id) {
            tab.title = title;
            Ok(())
        } else {
            Err(format!("Tab with id '{}' not found", tab_id))
        }
    }

    pub fn increment_trackers(&mut self, tab_id: &str, count: u32) -> Result<u32, String> {
        if let Some(tab) = self.tabs.iter_mut().find(|t| t.id == tab_id) {
            tab.blocked_trackers = tab.blocked_trackers.saturating_add(count);
            Ok(tab.blocked_trackers)
        } else {
            Err(format!("Tab with id '{}' not found", tab_id))
        }
    }

    pub fn update_navigation_state(
        &mut self,
        tab_id: &str,
        can_go_back: bool,
        can_go_forward: bool,
    ) -> Result<(), String> {
        if let Some(tab) = self.tabs.iter_mut().find(|t| t.id == tab_id) {
            tab.can_go_back = can_go_back;
            tab.can_go_forward = can_go_forward;
            Ok(())
        } else {
            Err(format!("Tab with id '{}' not found", tab_id))
        }
    }

    pub fn set_loading(&mut self, tab_id: &str, is_loading: bool) -> Result<(), String> {
        if let Some(tab) = self.tabs.iter_mut().find(|t| t.id == tab_id) {
            tab.is_loading = is_loading;
            Ok(())
        } else {
            Err(format!("Tab with id '{}' not found", tab_id))
        }
    }

    pub fn get_tabs(&self) -> Vec<Tab> {
        self.tabs.clone()
    }
}

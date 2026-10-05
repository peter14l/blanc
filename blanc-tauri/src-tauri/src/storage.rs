//! Local browser data persistence using the atomic, versioned JsonStore backend.
//!
//! Behavioral references:
//! - `src/main/store.js` — atomic replacement, debounce, owner-only permissions.
//! - `docs/ELECTRON_PARITY_ROADMAP.md` — Section 3: Persistence and Section 8: Favorites, History.

use serde::{Deserialize, Serialize};
use std::fs;
use std::path::PathBuf;
use crate::store::JsonStore;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct HistoryEntry {
    pub id: String,
    pub url: String,
    pub title: String,
    pub visited_at: u64,
    pub visit_count: u32,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct HistoryPage {
    pub entries: Vec<HistoryEntry>,
    pub total: u32,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Bookmark {
    pub id: String,
    pub url: String,
    pub title: String,
    pub favicon: Option<String>,
    pub created_at: u64,
    pub folder: Option<String>,
    pub tags: Vec<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Favorite {
    pub id: String,
    pub url: String,
    pub title: String,
    pub favicon: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct UserSettings {
    pub search_engine: String,
    pub theme: String,
    pub adblock_enabled: bool,
    pub block_third_party_cookies: bool,
    pub auto_https: bool,
    pub startup_behavior: String,
}

impl Default for UserSettings {
    fn default() -> Self {
        Self {
            search_engine: "duckduckgo".to_string(),
            theme: "dark".to_string(),
            adblock_enabled: true,
            block_third_party_cookies: true,
            auto_https: true,
            startup_behavior: "newtab".to_string(),
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct StorageData {
    pub history: Vec<HistoryEntry>,
    pub bookmarks: Vec<Bookmark>,
    pub favorites: Vec<Favorite>,
    pub settings: UserSettings,
    pub session_urls: Vec<String>,
}

impl Default for StorageData {
    fn default() -> Self {
        Self {
            history: Vec::new(),
            bookmarks: Vec::new(),
            favorites: vec![
                Favorite {
                    id: "fav-1".into(),
                    title: "Blanc".into(),
                    url: "https://blancbrowser.com".into(),
                    favicon: None,
                },
                Favorite {
                    id: "fav-2".into(),
                    title: "GitHub".into(),
                    url: "https://github.com/bnfy/blanc".into(),
                    favicon: None,
                },
                Favorite {
                    id: "fav-3".into(),
                    title: "DuckDuckGo".into(),
                    url: "https://duckduckgo.com".into(),
                    favicon: None,
                },
                Favorite {
                    id: "fav-4".into(),
                    title: "Wikipedia".into(),
                    url: "https://wikipedia.org".into(),
                    favicon: None,
                },
            ],
            settings: UserSettings::default(),
            session_urls: Vec::new(),
        }
    }
}

pub struct StorageManager {
    store: JsonStore<StorageData>,
}

impl Default for StorageManager {
    fn default() -> Self {
        Self::new()
    }
}

impl StorageManager {
    pub fn new() -> Self {
        let mut dir = dirs::data_local_dir().unwrap_or_else(|| PathBuf::from("."));
        dir.push("blanc-tauri");
        let _ = fs::create_dir_all(&dir);
        let file_path = dir.join("browser-data.json");
        Self {
            store: JsonStore::open(file_path),
        }
    }

    pub fn load(&self) -> StorageData {
        let (data, _report) = self.store.load();
        data
    }

    pub fn save(&self, data: &StorageData) -> Result<(), String> {
        self.store.save(data).map_err(|e| e.to_string())?;
        Ok(())
    }

    // -------------------------------------------------------------------------
    // HISTORY
    // -------------------------------------------------------------------------

    pub fn add_history(&self, url: String, title: String) -> Result<HistoryEntry, String> {
        let mut data = self.load();
        let now = std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .unwrap_or_default()
            .as_secs();

        if let Some(existing) = data.history.iter_mut().find(|h| h.url == url) {
            existing.visited_at = now;
            existing.visit_count += 1;
            if !title.is_empty() {
                existing.title = title.clone();
            }
            let entry = existing.clone();
            self.save(&data)?;
            return Ok(entry);
        }

        let entry = HistoryEntry {
            id: format!("hist-{}", now),
            url,
            title,
            visited_at: now,
            visit_count: 1,
        };
        data.history.insert(0, entry.clone());
        if data.history.len() > 2000 {
            data.history.truncate(2000);
        }
        self.save(&data)?;
        Ok(entry)
    }

    pub fn history_list(&self, limit: u32, offset: u32, query: Option<String>) -> HistoryPage {
        let data = self.load();
        let query_lower = query.map(|q| q.to_lowercase());

        let filtered: Vec<HistoryEntry> = data
            .history
            .into_iter()
            .filter(|h| {
                if let Some(ref q) = query_lower {
                    h.title.to_lowercase().contains(q) || h.url.to_lowercase().contains(q)
                } else {
                    true
                }
            })
            .collect();

        let total = filtered.len() as u32;
        let start = (offset as usize).min(filtered.len());
        let end = (start + limit as usize).min(filtered.len());

        HistoryPage {
            entries: filtered[start..end].to_vec(),
            total,
        }
    }

    pub fn history_remove(&self, id: &str) -> Result<(), String> {
        let mut data = self.load();
        data.history.retain(|h| h.id != id);
        self.save(&data)
    }

    pub fn clear_history(&self) -> Result<(), String> {
        let mut data = self.load();
        data.history.clear();
        self.save(&data)
    }

    // -------------------------------------------------------------------------
    // BOOKMARKS
    // -------------------------------------------------------------------------

    pub fn add_bookmark(&self, bookmark: Bookmark) -> Result<Bookmark, String> {
        let mut data = self.load();
        data.bookmarks.retain(|b| b.id != bookmark.id && b.url != bookmark.url);
        data.bookmarks.insert(0, bookmark.clone());
        self.save(&data)?;
        Ok(bookmark)
    }

    pub fn remove_bookmark(&self, id: &str) -> Result<(), String> {
        let mut data = self.load();
        data.bookmarks.retain(|b| b.id != id);
        self.save(&data)
    }

    pub fn update_bookmark(&self, id: &str, bookmark: Bookmark) -> Result<Bookmark, String> {
        let mut data = self.load();
        if let Some(existing) = data.bookmarks.iter_mut().find(|b| b.id == id) {
            existing.title = bookmark.title.clone();
            existing.url = bookmark.url.clone();
            existing.folder = bookmark.folder.clone();
            existing.tags = bookmark.tags.clone();
            existing.favicon = bookmark.favicon.clone();
            let updated = existing.clone();
            self.save(&data)?;
            return Ok(updated);
        }
        Err(format!("Bookmark '{}' not found", id))
    }

    // -------------------------------------------------------------------------
    // FAVORITES
    // -------------------------------------------------------------------------

    pub fn favorites_list(&self) -> Vec<Favorite> {
        self.load().favorites
    }

    pub fn favorites_add(&self, mut favorite: Favorite) -> Result<Favorite, String> {
        let mut data = self.load();
        if favorite.id.is_empty() {
            favorite.id = format!("fav-{}", crate::model::now_millis());
        }
        data.favorites.retain(|f| f.id != favorite.id && f.url != favorite.url);
        data.favorites.push(favorite.clone());
        self.save(&data)?;
        Ok(favorite)
    }

    pub fn favorites_update(&self, id: &str, favorite: Favorite) -> Result<Favorite, String> {
        let mut data = self.load();
        if let Some(existing) = data.favorites.iter_mut().find(|f| f.id == id) {
            existing.title = favorite.title.clone();
            existing.url = favorite.url.clone();
            existing.favicon = favorite.favicon.clone();
            let updated = existing.clone();
            self.save(&data)?;
            return Ok(updated);
        }
        Err(format!("Favorite '{}' not found", id))
    }

    pub fn favorites_remove(&self, id: &str) -> Result<(), String> {
        let mut data = self.load();
        data.favorites.retain(|f| f.id != id);
        self.save(&data)
    }

    // -------------------------------------------------------------------------
    // SETTINGS
    // -------------------------------------------------------------------------

    pub fn save_settings(&self, settings: UserSettings) -> Result<UserSettings, String> {
        let mut data = self.load();
        data.settings = settings.clone();
        self.save(&data)?;
        Ok(settings)
    }

    pub fn save_session(&self, urls: Vec<String>) -> Result<(), String> {
        let mut data = self.load();
        data.session_urls = urls;
        self.save(&data)
    }
}

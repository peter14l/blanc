use serde::{Deserialize, Serialize};
use std::fs;
use std::path::PathBuf;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct HistoryEntry {
    pub id: String,
    pub url: String,
    pub title: String,
    pub visited_at: u64,
    pub visit_count: u32,
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

#[derive(Debug, Clone, Serialize, Deserialize, Default)]
pub struct StorageData {
    pub history: Vec<HistoryEntry>,
    pub bookmarks: Vec<Bookmark>,
    pub settings: UserSettings,
    pub session_urls: Vec<String>,
}

pub struct StorageManager {
    file_path: PathBuf,
}

impl StorageManager {
    pub fn new() -> Self {
        let mut dir = dirs::data_local_dir().unwrap_or_else(|| PathBuf::from("."));
        dir.push("blanc-tauri");
        let _ = fs::create_dir_all(&dir);
        let file_path = dir.join("browser-data.json");
        Self { file_path }
    }

    pub fn load(&self) -> StorageData {
        if let Ok(bytes) = fs::read(&self.file_path) {
            if let Ok(data) = serde_json::from_slice::<StorageData>(&bytes) {
                return data;
            }
        }
        StorageData::default()
    }

    pub fn save(&self, data: &StorageData) -> Result<(), String> {
        let json = serde_json::to_string_pretty(data).map_err(|e| e.to_string())?;
        fs::write(&self.file_path, json).map_err(|e| e.to_string())?;
        Ok(())
    }

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

    pub fn clear_history(&self) -> Result<(), String> {
        let mut data = self.load();
        data.history.clear();
        self.save(&data)
    }

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

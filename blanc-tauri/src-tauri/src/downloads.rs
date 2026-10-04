//! Native download manager with lifecycle tracking, collision handling, and private isolation.
//!
//! Behavioral references in the Electron app (the port's specification):
//!
//! - `src/main/downloads.js` — MAX_PERSISTED = 200, private in-memory separation, coalesce updates
//! - `docs/ELECTRON_PARITY_ROADMAP.md` — Section 7: Downloads and external protocol handoff
//! - `docs/PARITY_IPC_CONTRACT.md` — `blanc:download-updated` and download commands

use crate::model::{new_id, now_millis, ProfileId, TabId, WindowId, PERSONAL_PROFILE};
use serde::{Deserialize, Serialize};
use std::collections::HashMap;
use std::path::{Path, PathBuf};

const MAX_PERSISTED_DOWNLOADS: usize = 200;

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub enum DownloadState {
    Pending,
    InProgress,
    Completed,
    Cancelled,
    Failed,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct DownloadItem {
    pub id: String,
    pub window_id: WindowId,
    pub tab_id: TabId,
    pub url: String,
    pub file_name: String,
    pub state: DownloadState,
    pub received_bytes: u64,
    pub total_bytes: Option<u64>,
    pub target_path: String,
    pub error: Option<String>,
    pub is_private: bool,
    pub profile_id: ProfileId,
    pub started_at: u64,
    pub completed_at: Option<u64>,
}

#[derive(Debug, Default)]
pub struct DownloadManager {
    /// In-memory active downloads.
    active: HashMap<String, DownloadItem>,
    /// Persisted completed/failed downloads for regular profiles.
    persisted: Vec<DownloadItem>,
    /// Ephemeral completed downloads for private sessions (never persisted).
    private_finished: Vec<DownloadItem>,
}

impl DownloadManager {
    pub fn new() -> Self {
        Self {
            active: HashMap::new(),
            persisted: Vec::new(),
            private_finished: Vec::new(),
        }
    }

    /// Normalizes a requested filename, preventing directory traversal and reserving collisions.
    pub fn resolve_unique_target_path(download_dir: &Path, raw_filename: &str) -> PathBuf {
        let clean_name = sanitize_filename(raw_filename);
        let target = download_dir.join(&clean_name);

        if !target.exists() {
            return target;
        }

        let stem = target
            .file_stem()
            .and_then(|s| s.to_str())
            .unwrap_or("download");
        let extension = target
            .extension()
            .and_then(|e| e.to_str())
            .map(|e| format!(".{}", e))
            .unwrap_or_default();

        let mut counter = 1;
        loop {
            let candidate_name = format!("{} ({}){}", stem, counter, extension);
            let candidate_path = download_dir.join(candidate_name);
            if !candidate_path.exists() {
                return candidate_path;
            }
            counter += 1;
        }
    }

    /// Registers a new incoming download.
    pub fn start_download(
        &mut self,
        window_id: WindowId,
        tab_id: TabId,
        url: String,
        raw_filename: String,
        total_bytes: Option<u64>,
        is_private: bool,
        profile_id: Option<ProfileId>,
    ) -> DownloadItem {
        let download_dir = dirs::download_dir().unwrap_or_else(|| PathBuf::from("."));
        let target_path = Self::resolve_unique_target_path(&download_dir, &raw_filename);
        let file_name = target_path
            .file_name()
            .and_then(|n| n.to_str())
            .unwrap_or("download")
            .to_string();

        let item = DownloadItem {
            id: new_id("dl"),
            window_id,
            tab_id,
            url,
            file_name,
            state: DownloadState::InProgress,
            received_bytes: 0,
            total_bytes,
            target_path: target_path.to_string_lossy().to_string(),
            error: None,
            is_private,
            profile_id: profile_id.unwrap_or_else(|| PERSONAL_PROFILE.to_string()),
            started_at: now_millis(),
            completed_at: None,
        };

        self.active.insert(item.id.clone(), item.clone());
        item
    }

    /// Updates download progress byte counts.
    pub fn update_progress(&mut self, id: &str, received_bytes: u64) -> Option<DownloadItem> {
        let item = self.active.get_mut(id)?;
        item.received_bytes = received_bytes;
        item.state = DownloadState::InProgress;
        Some(item.clone())
    }

    /// Marks a download completed.
    pub fn complete_download(&mut self, id: &str) -> Option<DownloadItem> {
        let mut item = self.active.remove(id)?;
        item.state = DownloadState::Completed;
        item.completed_at = Some(now_millis());

        if item.is_private {
            self.private_finished.insert(0, item.clone());
        } else {
            self.persisted.insert(0, item.clone());
            if self.persisted.len() > MAX_PERSISTED_DOWNLOADS {
                self.persisted.truncate(MAX_PERSISTED_DOWNLOADS);
            }
        }

        Some(item)
    }

    /// Marks a download cancelled.
    pub fn cancel_download(&mut self, id: &str) -> Option<DownloadItem> {
        let mut item = self.active.remove(id)?;
        item.state = DownloadState::Cancelled;
        item.completed_at = Some(now_millis());

        if item.is_private {
            self.private_finished.insert(0, item.clone());
        } else {
            self.persisted.insert(0, item.clone());
        }

        Some(item)
    }

    /// Marks a download failed with an error message.
    pub fn fail_download(&mut self, id: &str, error: String) -> Option<DownloadItem> {
        let mut item = self.active.remove(id)?;
        item.state = DownloadState::Failed;
        item.error = Some(error);
        item.completed_at = Some(now_millis());

        if item.is_private {
            self.private_finished.insert(0, item.clone());
        } else {
            self.persisted.insert(0, item.clone());
        }

        Some(item)
    }

    /// Lists downloads, filtering out private downloads if requested or scoped by profile.
    pub fn list_downloads(&self, profile_id: &str, include_private: bool) -> Vec<DownloadItem> {
        let mut list = Vec::new();

        // Active downloads
        for item in self.active.values() {
            if item.profile_id == profile_id && (!item.is_private || include_private) {
                list.push(item.clone());
            }
        }

        // Persisted downloads
        for item in &self.persisted {
            if item.profile_id == profile_id {
                list.push(item.clone());
            }
        }

        // Private finished downloads
        if include_private {
            for item in &self.private_finished {
                list.push(item.clone());
            }
        }

        list.sort_by(|a, b| b.started_at.cmp(&a.started_at));
        list
    }

    pub fn get_download(&self, id: &str) -> Option<DownloadItem> {
        self.active
            .get(id)
            .cloned()
            .or_else(|| self.persisted.iter().find(|i| i.id == id).cloned())
            .or_else(|| self.private_finished.iter().find(|i| i.id == id).cloned())
    }

    pub fn clear_completed(&mut self) {
        self.persisted.retain(|i| i.state != DownloadState::Completed && i.state != DownloadState::Cancelled);
        self.private_finished.clear();
    }
}

/// Sanitizes a filename, preventing directory traversal (`..`, `/`, `\`) and illegal characters.
pub fn sanitize_filename(raw: &str) -> String {
    let raw = raw.trim();
    if raw.is_empty() {
        return "download".to_string();
    }

    // Strip path prefixes
    let base = Path::new(raw)
        .file_name()
        .and_then(|f| f.to_str())
        .unwrap_or("download");

    // Replace invalid/dangerous characters
    let mut clean: String = base
        .chars()
        .map(|c| match c {
            '/' | '\\' | ':' | '*' | '?' | '"' | '<' | '>' | '|' | '\0'..='\x1f' => '_',
            _ => c,
        })
        .collect();

    // Do not allow '.' or '..' as whole filename
    if clean == "." || clean == ".." || clean.is_empty() {
        clean = "download".to_string();
    }

    clean
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn sanitizes_dangerous_filenames() {
        assert_eq!(sanitize_filename("../../etc/passwd"), "passwd");
        assert_eq!(sanitize_filename("file:name*.pdf"), "file_name_.pdf");
        assert_eq!(sanitize_filename(".."), "download");
        assert_eq!(sanitize_filename(""), "download");
    }

    #[test]
    fn registers_and_completes_download() {
        let mut dm = DownloadManager::new();
        let item = dm.start_download(
            "main".into(),
            "tab-1".into(),
            "https://example.com/test.zip".into(),
            "test.zip".into(),
            Some(1024),
            false,
            None,
        );

        assert_eq!(item.state, DownloadState::InProgress);
        assert_eq!(item.file_name, "test.zip");

        let updated = dm.update_progress(&item.id, 512).unwrap();
        assert_eq!(updated.received_bytes, 512);

        let completed = dm.complete_download(&item.id).unwrap();
        assert_eq!(completed.state, DownloadState::Completed);
        assert!(completed.completed_at.is_some());
    }

    #[test]
    fn private_downloads_are_ephemeral_and_never_persist() {
        let mut dm = DownloadManager::new();
        let item = dm.start_download(
            "main".into(),
            "tab-1".into(),
            "https://example.com/secret.pdf".into(),
            "secret.pdf".into(),
            Some(2048),
            true, // private
            None,
        );

        dm.complete_download(&item.id);

        // Not present in persisted list
        assert_eq!(dm.persisted.len(), 0);
        // Only present in private finished
        assert_eq!(dm.private_finished.len(), 1);

        // When listing with include_private: false, private items are omitted
        let regular_list = dm.list_downloads("personal", false);
        assert_eq!(regular_list.len(), 0);

        // When listing with include_private: true, private items are visible
        let private_list = dm.list_downloads("personal", true);
        assert_eq!(private_list.len(), 1);
    }
}

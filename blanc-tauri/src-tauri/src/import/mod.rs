//! Browser data import subsystem for Blanc Tauri.
//!
//! Respects F30 (Browser Favorites Migration) invariants:
//! - Explicit user action only — never runs in background.
//! - Add-only & idempotent — re-importing never creates duplicates.
//! - Privacy boundary — passwords are a separate, opt-in choice.

pub mod bookmarks;
pub mod discovery;
pub mod history;
pub mod html;
pub mod passwords;
pub mod sessions;

use serde::{Deserialize, Serialize};

/// A discovered browser profile that can be imported from.
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct BrowserProfileSource {
    /// Unique id for this profile, e.g. "chrome-Default".
    pub id: String,
    /// Machine-readable browser key, e.g. "chrome", "edge", "brave", "firefox".
    pub browser_id: String,
    /// Human-readable browser name, e.g. "Google Chrome".
    pub browser_name: String,
    /// Profile display name, e.g. "Default", "Profile 1".
    pub profile_name: String,
    /// Absolute path to the profile directory.
    pub profile_path: String,
    pub has_bookmarks: bool,
    pub has_history: bool,
    pub has_passwords: bool,
    pub has_sessions: bool,
}

/// User-selected options for a single import run.
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ImportOptions {
    /// Which `BrowserProfileSource.id` to import from.
    pub source_id: String,
    pub import_bookmarks: bool,
    pub import_history: bool,
    pub import_passwords: bool,
    pub import_open_tabs: bool,
}

/// Summary returned to the frontend after a completed import.
#[derive(Debug, Clone, Serialize, Deserialize, Default)]
#[serde(rename_all = "camelCase")]
pub struct ImportResult {
    pub bookmarks_imported: usize,
    pub bookmarks_skipped: usize,
    pub history_imported: usize,
    pub passwords_imported: usize,
    pub tabs_imported: usize,
    /// Non-fatal warnings (e.g. locked file, ABE notice).
    pub warnings: Vec<String>,
}

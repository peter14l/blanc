//! Profile Sync payload packaging and privacy boundaries.
//!
//! Behavioral references in the Electron app (the port's specification):
//!
//! - `src/main/sync.js` — sync protocol and entity whitelist
//! - `src/main/sync-crypto.js` — HKDF and AES-GCM derivation
//! - `docs/ELECTRON_PARITY_ROADMAP.md` — Section 11: Profile Sync, telemetry, and Patron
//!
//! Security contract:
//! Sync ONLY permits Favorites, approved settings, and workspace metadata.
//! Under NO circumstances are cookies, browsing history, downloads, permissions,
//! private tabs, or supporter license keys included in sync payloads.

use crate::storage::{Favorite, UserSettings};
use crate::workspaces::WorkspaceRecord;
use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct SyncEligibility {
    pub eligible: bool,
    pub reason: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct SyncableData {
    pub version: u32,
    pub profile_id: String,
    pub favorites: Vec<Favorite>,
    pub settings: UserSettings,
    pub workspaces: Vec<WorkspaceRecord>,
}

impl SyncableData {
    pub fn new(profile_id: String, favorites: Vec<Favorite>, settings: UserSettings, workspaces: Vec<WorkspaceRecord>) -> Self {
        Self {
            version: 1,
            profile_id,
            favorites,
            settings,
            workspaces,
        }
    }
}

/// Validates that an outgoing sync payload contains ONLY whitelisted entities
/// and zero prohibited private records.
pub fn validate_sync_payload(payload: &SyncableData) -> Result<(), &'static str> {
    if payload.profile_id.starts_with("private-") {
        return Err("Private profiles cannot be synced");
    }

    // Verify favorites URLs are valid and not private blanc:// surfaces
    for fav in &payload.favorites {
        if fav.url.starts_with("blanc://") && fav.url != "blanc://newtab/" {
            return Err("Internal utility surfaces cannot be synced as favorites");
        }
    }

    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn permits_valid_payload() {
        let payload = SyncableData::new(
            "personal".into(),
            vec![Favorite {
                id: "fav-1".into(),
                title: "Blanc".into(),
                url: "https://blancbrowser.com".into(),
                favicon: None,
            }],
            UserSettings::default(),
            Vec::new(),
        );

        assert!(validate_sync_payload(&payload).is_ok());
    }

    #[test]
    fn rejects_private_profile_sync() {
        let payload = SyncableData::new(
            "private-main".into(),
            Vec::new(),
            UserSettings::default(),
            Vec::new(),
        );

        assert!(validate_sync_payload(&payload).is_err());
    }

    #[test]
    fn rejects_private_internal_surfaces_in_favorites() {
        let payload = SyncableData::new(
            "personal".into(),
            vec![Favorite {
                id: "fav-bad".into(),
                title: "Settings".into(),
                url: "blanc://settings/".into(),
                favicon: None,
            }],
            UserSettings::default(),
            Vec::new(),
        );

        assert!(validate_sync_payload(&payload).is_err());
    }
}

//! Named Workspaces and Local Profiles management.
//!
//! Behavioral references in the Electron app (the port's specification):
//!
//! - `src/main/workspaces-model.js` — MAX_WORKSPACES = 25, MAX_NAME_LENGTH = 60, name sanitization
//! - `src/main/local-profile-model.js` — reserved Personal profile, directory tokens
//! - `docs/ELECTRON_PARITY_ROADMAP.md` — Section 10: Workspaces and local profiles

use crate::model::{new_id, now_millis, ProfileId, WorkspaceId, PERSONAL_PROFILE};
use serde::{Deserialize, Serialize};

pub const MAX_WORKSPACES: usize = 25;
pub const MAX_NAME_LENGTH: usize = 60;

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct WorkspaceRecord {
    pub id: WorkspaceId,
    pub name: String,
    pub profile_id: ProfileId,
    pub created_at: u64,
    pub updated_at: u64,
    pub urls: Vec<String>,
    pub active_index: usize,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ProfileRecord {
    pub id: ProfileId,
    pub name: String,
    pub created_at: u64,
    pub is_reserved: bool,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct WorkspacesData {
    pub workspaces: Vec<WorkspaceRecord>,
    pub profiles: Vec<ProfileRecord>,
    pub active_workspace_id: Option<WorkspaceId>,
}

impl Default for WorkspacesData {
    fn default() -> Self {
        Self {
            workspaces: Vec::new(),
            profiles: vec![ProfileRecord {
                id: PERSONAL_PROFILE.to_string(),
                name: "Personal".to_string(),
                created_at: 0,
                is_reserved: true,
            }],
            active_workspace_id: None,
        }
    }
}

pub struct WorkspacesManager {
    data: WorkspacesData,
}

impl Default for WorkspacesManager {
    fn default() -> Self {
        Self::new()
    }
}

impl WorkspacesManager {
    pub fn new() -> Self {
        Self {
            data: WorkspacesData::default(),
        }
    }

    pub fn sanitize_name(raw: &str) -> Result<String, String> {
        let trimmed = raw.trim();
        if trimmed.is_empty() {
            return Err("Workspace name cannot be empty".to_string());
        }
        let clean = trimmed.chars().take(MAX_NAME_LENGTH).collect();
        Ok(clean)
    }

    // -------------------------------------------------------------------------
    // WORKSPACES
    // -------------------------------------------------------------------------

    pub fn create_workspace(
        &mut self,
        profile_id: &str,
        name: String,
        urls: Vec<String>,
    ) -> Result<WorkspaceRecord, String> {
        let clean_name = Self::sanitize_name(&name)?;
        let count = self
            .data
            .workspaces
            .iter()
            .filter(|w| w.profile_id == profile_id)
            .count();

        if count >= MAX_WORKSPACES {
            return Err(format!("Maximum limit of {} workspaces reached", MAX_WORKSPACES));
        }

        let now = now_millis();
        let ws = WorkspaceRecord {
            id: new_id("ws"),
            name: clean_name,
            profile_id: profile_id.to_string(),
            created_at: now,
            updated_at: now,
            urls,
            active_index: 0,
        };

        self.data.workspaces.push(ws.clone());
        Ok(ws)
    }

    pub fn rename_workspace(&mut self, id: &str, name: String) -> Result<WorkspaceRecord, String> {
        let clean_name = Self::sanitize_name(&name)?;
        let ws = self
            .data
            .workspaces
            .iter_mut()
            .find(|w| w.id == id)
            .ok_or_else(|| format!("Workspace '{}' not found", id))?;

        ws.name = clean_name;
        ws.updated_at = now_millis();
        Ok(ws.clone())
    }

    pub fn update_workspace_tabs(
        &mut self,
        id: &str,
        urls: Vec<String>,
        active_index: usize,
    ) -> Result<WorkspaceRecord, String> {
        let ws = self
            .data
            .workspaces
            .iter_mut()
            .find(|w| w.id == id)
            .ok_or_else(|| format!("Workspace '{}' not found", id))?;

        ws.urls = urls;
        ws.active_index = active_index;
        ws.updated_at = now_millis();
        Ok(ws.clone())
    }

    pub fn delete_workspace(&mut self, id: &str) -> Result<(), String> {
        let pos = self.data.workspaces.iter().position(|w| w.id == id);
        if let Some(idx) = pos {
            self.data.workspaces.remove(idx);
            if self.data.active_workspace_id.as_deref() == Some(id) {
                self.data.active_workspace_id = None;
            }
            Ok(())
        } else {
            Err(format!("Workspace '{}' not found", id))
        }
    }

    pub fn list_workspaces(&self, profile_id: &str) -> Vec<WorkspaceRecord> {
        self.data
            .workspaces
            .iter()
            .filter(|w| w.profile_id == profile_id)
            .cloned()
            .collect()
    }

    // -------------------------------------------------------------------------
    // PROFILES
    // -------------------------------------------------------------------------

    pub fn create_profile(&mut self, name: String) -> Result<ProfileRecord, String> {
        let clean_name = Self::sanitize_name(&name)?;
        let profile = ProfileRecord {
            id: new_id("prof"),
            name: clean_name,
            created_at: now_millis(),
            is_reserved: false,
        };
        self.data.profiles.push(profile.clone());
        Ok(profile)
    }

    pub fn delete_profile(&mut self, id: &str) -> Result<(), String> {
        if id == PERSONAL_PROFILE {
            return Err("Cannot delete the reserved Personal profile".to_string());
        }

        let pos = self.data.profiles.iter().position(|p| p.id == id);
        if let Some(idx) = pos {
            self.data.profiles.remove(idx);
            // Cascade delete associated workspaces
            self.data.workspaces.retain(|w| w.profile_id != id);
            Ok(())
        } else {
            Err(format!("Profile '{}' not found", id))
        }
    }

    pub fn list_profiles(&self) -> Vec<ProfileRecord> {
        self.data.profiles.clone()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn personal_profile_is_default_and_cannot_be_deleted() {
        let mut mgr = WorkspacesManager::new();
        let profiles = mgr.list_profiles();
        assert_eq!(profiles.len(), 1);
        assert_eq!(profiles[0].id, PERSONAL_PROFILE);
        assert!(profiles[0].is_reserved);

        assert!(mgr.delete_profile(PERSONAL_PROFILE).is_err());
    }

    #[test]
    fn creates_and_manages_workspaces() {
        let mut mgr = WorkspacesManager::new();
        let ws = mgr
            .create_workspace("personal", "Research".into(), vec!["https://arxiv.org".into()])
            .unwrap();

        assert_eq!(ws.name, "Research");
        assert_eq!(ws.profile_id, "personal");
        assert_eq!(ws.urls.len(), 1);

        let renamed = mgr.rename_workspace(&ws.id, "Deep Learning".into()).unwrap();
        assert_eq!(renamed.name, "Deep Learning");

        mgr.delete_workspace(&ws.id).unwrap();
        assert_eq!(mgr.list_workspaces("personal").len(), 0);
    }

    #[test]
    fn caps_workspaces_at_maximum_limit() {
        let mut mgr = WorkspacesManager::new();
        for i in 0..MAX_WORKSPACES {
            mgr.create_workspace("personal", format!("Workspace {}", i), Vec::new())
                .unwrap();
        }

        let err = mgr.create_workspace("personal", "Overflow".into(), Vec::new());
        assert!(err.is_err());
    }
}

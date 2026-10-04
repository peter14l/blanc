//! Origin-scoped, deny-by-default permission broker.
//!
//! Behavioral references in the Electron app (the port's specification):
//!
//! - `src/main/permissions.js` — three tiers: auto-allowed, prompted, denied
//! - `src/main/permission-decisions.js` — origin-scoped decision keys and legacy normalization
//! - `docs/ELECTRON_PARITY_ROADMAP.md` — Section 6: Permissions, media, WebRTC, and capture
//! - `docs/PARITY_IPC_CONTRACT.md` — IPC commands and events for permissions
//!
//! Guarantees:
//! 1. Default to deny for any unrecognized permission request.
//! 2. Only admitted main-frame requests trigger user prompts.
//! 3. Private sessions store decisions in memory only; they never touch disk.
//! 4. Blocked origins receive denied responses without leaking hardware state.

use crate::model::{new_id, now_millis, ProfileId, WindowId, PERSONAL_PROFILE};
use serde::{Deserialize, Serialize};
use std::collections::HashMap;

/// The capability/resource requested by web content.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub enum PermissionResource {
    Camera,
    Microphone,
    Geolocation,
    Notifications,
    DisplayCapture,
    Fullscreen,
    PointerLock,
    ClipboardSanitizedWrite,
    Other,
}

impl PermissionResource {
    pub fn from_str_name(name: &str) -> Self {
        match name.to_lowercase().as_str() {
            "camera" | "video" => PermissionResource::Camera,
            "microphone" | "audio" => PermissionResource::Microphone,
            "geolocation" => PermissionResource::Geolocation,
            "notifications" => PermissionResource::Notifications,
            "display-capture" | "screen" => PermissionResource::DisplayCapture,
            "fullscreen" => PermissionResource::Fullscreen,
            "pointerlock" => PermissionResource::PointerLock,
            "clipboard-sanitized-write" | "clipboard" => PermissionResource::ClipboardSanitizedWrite,
            _ => PermissionResource::Other,
        }
    }

    /// Whether this resource is low-risk and safe to grant silently.
    pub fn is_auto_allowed(&self) -> bool {
        matches!(
            self,
            PermissionResource::Fullscreen
                | PermissionResource::PointerLock
                | PermissionResource::ClipboardSanitizedWrite
        )
    }

    /// Whether this resource requires an explicit user confirmation prompt.
    pub fn is_prompted(&self) -> bool {
        matches!(
            self,
            PermissionResource::Camera
                | PermissionResource::Microphone
                | PermissionResource::Geolocation
                | PermissionResource::Notifications
                | PermissionResource::DisplayCapture
        )
    }
}

/// The decision outcome for a permission request.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub enum PermissionDecision {
    Allow,
    Block,
    Ask,
}

/// A stored or active decision record.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct PermissionDecisionRecord {
    pub origin: String,
    pub resource: PermissionResource,
    pub decision: PermissionDecision,
    pub remembered: bool,
    pub updated_at: u64,
}

/// An active, pending permission prompt sent to the user interface.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct PermissionRequest {
    pub id: String,
    pub window_id: WindowId,
    pub tab_id: String,
    pub origin: String,
    pub resource: PermissionResource,
}

/// Coordinates permission requests, persistent profile storage, and ephemeral private sessions.
#[derive(Debug, Default)]
pub struct PermissionBroker {
    /// Persistent decisions keyed by (ProfileId, Origin, Resource).
    persistent_decisions: HashMap<(ProfileId, String, PermissionResource), PermissionDecision>,
    /// Ephemeral decisions keyed by (WindowId, Origin, Resource), kept in memory for private windows.
    ephemeral_decisions: HashMap<(WindowId, String, PermissionResource), PermissionDecision>,
    /// Pending prompt requests waiting for a user decision.
    pending_requests: HashMap<String, PermissionRequest>,
}

impl PermissionBroker {
    pub fn new() -> Self {
        Self {
            persistent_decisions: HashMap::new(),
            ephemeral_decisions: HashMap::new(),
            pending_requests: HashMap::new(),
        }
    }

    /// Checks the permission status for `origin` and `resource`.
    pub fn check_permission(
        &self,
        profile_id: &str,
        window_id: &str,
        is_private: bool,
        origin: &str,
        resource: PermissionResource,
    ) -> PermissionDecision {
        if resource.is_auto_allowed() {
            return PermissionDecision::Allow;
        }

        if !resource.is_prompted() {
            // Deny unrecognized or dangerous permissions by default
            return PermissionDecision::Block;
        }

        // Check private ephemeral decisions first if private
        if is_private {
            if let Some(&decision) = self
                .ephemeral_decisions
                .get(&(window_id.to_string(), origin.to_string(), resource))
            {
                return decision;
            }
            return PermissionDecision::Ask;
        }

        // Check persistent profile decisions
        let profile = if profile_id.is_empty() {
            PERSONAL_PROFILE
        } else {
            profile_id
        };

        if let Some(&decision) = self
            .persistent_decisions
            .get(&(profile.to_string(), origin.to_string(), resource))
        {
            return decision;
        }

        PermissionDecision::Ask
    }

    /// Creates and registers an admitted main-frame permission request.
    pub fn create_request(
        &mut self,
        window_id: WindowId,
        tab_id: String,
        origin: String,
        resource: PermissionResource,
    ) -> PermissionRequest {
        let request = PermissionRequest {
            id: new_id("perm"),
            window_id,
            tab_id,
            origin,
            resource,
        };
        self.pending_requests
            .insert(request.id.clone(), request.clone());
        request
    }

    /// Resolves a pending permission request with the user's response.
    pub fn resolve_request(
        &mut self,
        request_id: &str,
        allow: bool,
        remember: bool,
        is_private: bool,
        profile_id: &str,
    ) -> Result<PermissionDecisionRecord, String> {
        let request = self
            .pending_requests
            .remove(request_id)
            .ok_or_else(|| format!("Pending permission request '{}' not found", request_id))?;

        let decision = if allow {
            PermissionDecision::Allow
        } else {
            PermissionDecision::Block
        };

        let record = PermissionDecisionRecord {
            origin: request.origin.clone(),
            resource: request.resource,
            decision,
            remembered: remember,
            updated_at: now_millis(),
        };

        // If private session, always store in ephemeral memory
        if is_private {
            self.ephemeral_decisions.insert(
                (request.window_id, request.origin, request.resource),
                decision,
            );
        } else if remember {
            let profile = if profile_id.is_empty() {
                PERSONAL_PROFILE
            } else {
                profile_id
            };
            self.persistent_decisions.insert(
                (profile.to_string(), request.origin, request.resource),
                decision,
            );
        }

        Ok(record)
    }

    /// Manually sets or overrides a permission decision (e.g. from the Settings surface).
    pub fn set_decision(
        &mut self,
        profile_id: &str,
        origin: String,
        resource: PermissionResource,
        decision: PermissionDecision,
    ) {
        let profile = if profile_id.is_empty() {
            PERSONAL_PROFILE
        } else {
            profile_id
        };

        if decision == PermissionDecision::Ask {
            self.persistent_decisions
                .remove(&(profile.to_string(), origin, resource));
        } else {
            self.persistent_decisions
                .insert((profile.to_string(), origin, resource), decision);
        }
    }

    /// Lists all remembered decisions for a given profile.
    pub fn list_decisions(&self, profile_id: &str) -> Vec<PermissionDecisionRecord> {
        let profile = if profile_id.is_empty() {
            PERSONAL_PROFILE
        } else {
            profile_id
        };

        self.persistent_decisions
            .iter()
            .filter(|((p, _, _), _)| p == profile)
            .map(|((_, origin, resource), &decision)| PermissionDecisionRecord {
                origin: origin.clone(),
                resource: *resource,
                decision,
                remembered: true,
                updated_at: now_millis(),
            })
            .collect()
    }

    /// Clears ephemeral decisions when a private window closes.
    pub fn clear_private_window(&mut self, window_id: &str) {
        self.ephemeral_decisions
            .retain(|(win, _, _), _| win != window_id);
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn auto_allows_low_risk_features() {
        let broker = PermissionBroker::new();
        assert_eq!(
            broker.check_permission("personal", "main", false, "https://example.com", PermissionResource::Fullscreen),
            PermissionDecision::Allow
        );
        assert_eq!(
            broker.check_permission("personal", "main", false, "https://example.com", PermissionResource::PointerLock),
            PermissionDecision::Allow
        );
        assert_eq!(
            broker.check_permission("personal", "main", false, "https://example.com", PermissionResource::ClipboardSanitizedWrite),
            PermissionDecision::Allow
        );
    }

    #[test]
    fn denies_unrecognized_permissions_by_default() {
        let broker = PermissionBroker::new();
        assert_eq!(
            broker.check_permission("personal", "main", false, "https://example.com", PermissionResource::Other),
            PermissionDecision::Block
        );
    }

    #[test]
    fn prompts_for_media_and_geolocation_when_unknown() {
        let broker = PermissionBroker::new();
        assert_eq!(
            broker.check_permission("personal", "main", false, "https://example.com", PermissionResource::Camera),
            PermissionDecision::Ask
        );
        assert_eq!(
            broker.check_permission("personal", "main", false, "https://example.com", PermissionResource::Geolocation),
            PermissionDecision::Ask
        );
    }

    #[test]
    fn remembers_decisions_per_origin() {
        let mut broker = PermissionBroker::new();
        let req = broker.create_request("main".into(), "tab-1".into(), "https://meet.jit.si".into(), PermissionResource::Microphone);
        let res = broker.resolve_request(&req.id, true, true, false, "personal").unwrap();

        assert_eq!(res.decision, PermissionDecision::Allow);
        assert_eq!(
            broker.check_permission("personal", "main", false, "https://meet.jit.si", PermissionResource::Microphone),
            PermissionDecision::Allow
        );
        // Another origin still asks
        assert_eq!(
            broker.check_permission("personal", "main", false, "https://zoom.us", PermissionResource::Microphone),
            PermissionDecision::Ask
        );
    }

    #[test]
    fn private_decisions_never_enter_persistent_store_and_clear_on_close() {
        let mut broker = PermissionBroker::new();
        let req = broker.create_request("priv-win".into(), "tab-p1".into(), "https://example.com".into(), PermissionResource::Camera);
        let res = broker.resolve_request(&req.id, true, true, true, "personal").unwrap();

        assert_eq!(res.decision, PermissionDecision::Allow);

        // Persistent store has 0 records
        assert_eq!(broker.list_decisions("personal").len(), 0);

        // Active private window allows
        assert_eq!(
            broker.check_permission("personal", "priv-win", true, "https://example.com", PermissionResource::Camera),
            PermissionDecision::Allow
        );

        // Window closes
        broker.clear_private_window("priv-win");
        assert_eq!(
            broker.check_permission("personal", "priv-win", true, "https://example.com", PermissionResource::Camera),
            PermissionDecision::Ask
        );
    }
}

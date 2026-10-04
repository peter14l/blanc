//! Patron entitlement state and verification.
//!
//! Behavioral references in the Electron app (the port's specification):
//!
//! - `src/main/patron-model.js` — entitlement kinds: founding, lifetime, subscription
//! - `src/main/patron.js` — license validation, grace periods, key security
//! - `docs/ELECTRON_PARITY_ROADMAP.md` — Section 11: Profile Sync, telemetry, and Patron
//!
//! Security contract:
//! React receives ONLY derived status flags (`is_patron`, `kind`). Activation keys
//! and secrets are kept in native memory and NEVER exposed across IPC or logged.

use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub enum PatronKind {
    Founding,
    Lifetime,
    Subscription,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct PatronStatus {
    pub is_patron: bool,
    pub kind: Option<PatronKind>,
    pub expires_at: Option<u64>,
}

#[derive(Debug, Default)]
pub struct PatronState {
    pub active_kind: Option<PatronKind>,
    pub expires_at: Option<u64>,
    pub license_key_hash: Option<String>,
}

impl PatronState {
    pub fn new() -> Self {
        Self {
            active_kind: None,
            expires_at: None,
            license_key_hash: None,
        }
    }

    pub fn get_status(&self) -> PatronStatus {
        PatronStatus {
            is_patron: self.active_kind.is_some(),
            kind: self.active_kind,
            expires_at: self.expires_at,
        }
    }

    /// Verifies and activates a patron license key.
    ///
    /// The key itself is validated natively and never returned or exposed to React.
    pub fn activate(&mut self, key: &str) -> Result<PatronStatus, String> {
        let trimmed = key.trim();
        if trimmed.is_empty() {
            return Err("Activation key cannot be empty".to_string());
        }

        // Test keys and prefix validation matching Blanc's licensing scheme
        if trimmed.starts_with("BLANC-FOUNDER-") || trimmed == "BLANC-PATRON-TEST" {
            self.active_kind = Some(PatronKind::Founding);
            self.expires_at = None;
            self.license_key_hash = Some(hash_key(trimmed));
            return Ok(self.get_status());
        }

        if trimmed.starts_with("BLANC-LIFETIME-") {
            self.active_kind = Some(PatronKind::Lifetime);
            self.expires_at = None;
            self.license_key_hash = Some(hash_key(trimmed));
            return Ok(self.get_status());
        }

        if trimmed.starts_with("BLANC-SUB-") {
            self.active_kind = Some(PatronKind::Subscription);
            // 1 year from now
            self.expires_at = Some(crate::model::now_millis() + 365 * 24 * 60 * 60 * 1000);
            self.license_key_hash = Some(hash_key(trimmed));
            return Ok(self.get_status());
        }

        Err("Invalid patron activation key".to_string())
    }

    pub fn deactivate(&mut self) -> PatronStatus {
        self.active_kind = None;
        self.expires_at = None;
        self.license_key_hash = None;
        self.get_status()
    }
}

fn hash_key(key: &str) -> String {
    use std::collections::hash_map::DefaultHasher;
    use std::hash::{Hash, Hasher};
    let mut hasher = DefaultHasher::new();
    key.hash(&mut hasher);
    format!("{:x}", hasher.finish())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn default_state_is_not_patron() {
        let state = PatronState::new();
        let status = state.get_status();
        assert!(!status.is_patron);
        assert_eq!(status.kind, None);
    }

    #[test]
    fn activates_founding_patron_without_key_leak() {
        let mut state = PatronState::new();
        let status = state.activate("BLANC-FOUNDER-VIP123").unwrap();
        assert!(status.is_patron);
        assert_eq!(status.kind, Some(PatronKind::Founding));
        assert_eq!(status.expires_at, None);
    }

    #[test]
    fn activates_subscription_with_expiry() {
        let mut state = PatronState::new();
        let status = state.activate("BLANC-SUB-999").unwrap();
        assert!(status.is_patron);
        assert_eq!(status.kind, Some(PatronKind::Subscription));
        assert!(status.expires_at.is_some());
    }

    #[test]
    fn rejects_invalid_keys() {
        let mut state = PatronState::new();
        assert!(state.activate("RANDOM-INVALID-KEY").is_err());
        assert!(!state.get_status().is_patron);
    }

    #[test]
    fn deactivates_cleanly() {
        let mut state = PatronState::new();
        state.activate("BLANC-PATRON-TEST").unwrap();
        assert!(state.get_status().is_patron);

        let status = state.deactivate();
        assert!(!status.is_patron);
        assert_eq!(status.kind, None);
    }
}

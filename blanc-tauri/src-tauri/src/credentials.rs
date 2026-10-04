//! 1Password and credential fill broker interface.
//!
//! Behavioral references in the Electron app (the port's specification):
//!
//! - `src/main/fill-status-kinds.js` — fixed-kind status events (decision vs notice)
//! - `src/main/fill-hint.js` — origin and frame matching before fill
//! - `docs/ELECTRON_PARITY_ROADMAP.md` — Section 12: 1Password credentials and fill
//!
//! Security contract:
//! Under NO circumstances are passwords, passkeys, or credential secrets sent across
//! React IPC or logged. The frontend only receives discrete `FillStatus` events.

use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub enum FillMode {
    Decision,
    Notice,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "kebab-case")]
pub enum FillStatusKind {
    SetupEnable,
    SetupAccount,
    ConfirmHeuristic,
    Busy,
    UnsupportedPage,
    PageChanged,
    NoForm,
    NoMatch,
    EmptyLogin,
    NothingFilled,
    Unexpected,
    DesktopUnavailable,
    AccountNotFound,
    NotAuthorized,
    SessionExpired,
    TimedOut,
    BrokerStopped,
    SdkError,
    SelectionChanged,
    Filled,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct FillStatus {
    pub kind: FillStatusKind,
    pub mode: FillMode,
    pub level: &'static str,
    pub origin: Option<String>,
}

impl FillStatusKind {
    pub fn mode(&self) -> FillMode {
        match self {
            FillStatusKind::SetupEnable
            | FillStatusKind::SetupAccount
            | FillStatusKind::ConfirmHeuristic => FillMode::Decision,
            _ => FillMode::Notice,
        }
    }

    pub fn level(&self) -> &'static str {
        match self {
            FillStatusKind::Filled => "success",
            FillStatusKind::SetupEnable
            | FillStatusKind::SetupAccount
            | FillStatusKind::ConfirmHeuristic => "info",
            _ => "error",
        }
    }
}

#[derive(Debug, Default)]
pub struct CredentialBroker {
    pub is_available: bool,
    pub is_busy: bool,
    pub last_status: Option<FillStatus>,
}

impl CredentialBroker {
    pub fn new() -> Self {
        Self {
            is_available: false,
            is_busy: false,
            last_status: None,
        }
    }

    /// Preflights credential availability for `origin`.
    pub fn check_available(&self, origin: &str) -> FillStatus {
        if !origin.starts_with("https://") && !origin.starts_with("http://localhost") {
            return FillStatus {
                kind: FillStatusKind::UnsupportedPage,
                mode: FillMode::Notice,
                level: "error",
                origin: Some(origin.to_string()),
            };
        }

        if !self.is_available {
            return FillStatus {
                kind: FillStatusKind::DesktopUnavailable,
                mode: FillMode::Notice,
                level: "error",
                origin: Some(origin.to_string()),
            };
        }

        FillStatus {
            kind: FillStatusKind::SetupEnable,
            mode: FillMode::Decision,
            level: "info",
            origin: Some(origin.to_string()),
        }
    }

    /// Simulates initiating an explicit-invoke fill attempt.
    pub fn trigger_fill(&mut self, origin: &str) -> FillStatus {
        if !origin.starts_with("https://") && !origin.starts_with("http://localhost") {
            let status = FillStatus {
                kind: FillStatusKind::UnsupportedPage,
                mode: FillMode::Notice,
                level: "error",
                origin: Some(origin.to_string()),
            };
            self.last_status = Some(status.clone());
            return status;
        }

        if !self.is_available {
            let status = FillStatus {
                kind: FillStatusKind::DesktopUnavailable,
                mode: FillMode::Notice,
                level: "error",
                origin: Some(origin.to_string()),
            };
            self.last_status = Some(status.clone());
            return status;
        }

        let status = FillStatus {
            kind: FillStatusKind::NoMatch,
            mode: FillMode::Notice,
            level: "error",
            origin: Some(origin.to_string()),
        };
        self.last_status = Some(status.clone());
        status
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn fill_modes_and_levels_match_contract() {
        assert_eq!(FillStatusKind::Filled.level(), "success");
        assert_eq!(FillStatusKind::Filled.mode(), FillMode::Notice);

        assert_eq!(FillStatusKind::SetupEnable.mode(), FillMode::Decision);
        assert_eq!(FillStatusKind::SetupEnable.level(), "info");

        assert_eq!(FillStatusKind::NoMatch.mode(), FillMode::Notice);
        assert_eq!(FillStatusKind::NoMatch.level(), "error");
    }

    #[test]
    fn non_https_pages_are_unsupported() {
        let broker = CredentialBroker::new();
        let status = broker.check_available("http://insecure.example.com");
        assert_eq!(status.kind, FillStatusKind::UnsupportedPage);
    }

    #[test]
    fn desktop_unavailable_when_1password_bridge_is_unconnected() {
        let broker = CredentialBroker::new();
        let status = broker.check_available("https://example.com");
        assert_eq!(status.kind, FillStatusKind::DesktopUnavailable);
    }
}

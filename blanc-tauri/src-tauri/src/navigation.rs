//! Navigation admission, classification, popup policy, and protocol handoff.
//!
//! Behavioral references in the Electron app (the port's specification):
//!
//! - `src/main/top-level-url-policy.js` — forbidden top-level schemes (data:, file:, javascript:)
//! - `src/main/external-protocols.js` — OS protocol handoff and dangerous protocol blocklist
//! - `src/main/profile-navigation-gates.js` — popup admission and opener inheritance
//! - `src/main/pages.js` — `blanc://` surface routing and trust validation
//!
//! Rust owns all navigation classification and policy decisions. React must not
//! pre-normalize or decide URL destinations.

use crate::model::Surface;
use serde::{Deserialize, Serialize};

/// Protocols explicitly forbidden as top-level navigations.
///
/// Web content must never navigate the main frame to these schemes.
pub const FORBIDDEN_TOP_LEVEL_PROTOCOLS: &[&str] = &[
    "data:",
    "file:",
    "javascript:",
    "vbscript:",
    "blanc-chrome:",
];

/// OS and internal protocols blocked from web execution to prevent RCE or elevation.
pub const BLOCKED_OS_PROTOCOLS: &[&str] = &[
    "chrome:",
    "chrome-extension:",
    "devtools:",
    "blanc-chrome:",
    "blanc-import:",
    "view-source:",
    "afp:",
    "applescript:",
    "disk:",
    "disks:",
    "hcp:",
    "ie.http:",
    "mk:",
    "ms-help:",
    "nntp:",
    "res:",
    "shell:",
    "vnd.ms.radio:",
    "cmd:",
    "powershell:",
    "terminal:",
    "osascript:",
    "ms-msdt:",
    "ms-appinstaller:",
    "ms-settings:",
    "search-ms:",
    "search:",
    "smb:",
    "ssh:",
    "x-apple.systempreferences:",
];

/// Protocols that can be directly handed off to the OS without prompting.
pub const DIRECT_HANDOFF_PROTOCOLS: &[&str] = &[
    "mailto:",
    "tel:",
    "facetime:",
    "sms:",
];

/// The parsed classification of a user input or navigation target.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum NavigationTarget {
    Blank,
    Internal(Surface),
    WebUrl(String),
    SearchQuery(String),
    ExternalHandoff {
        protocol: String,
        url: String,
        direct: bool,
    },
    ForbiddenProtocol(String),
    BlockedOSProtocol(String),
    InvalidInput(String),
}

/// A decision made when admitting a top-level navigation.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(tag = "action", rename_all = "camelCase")]
pub enum NavigationDecision {
    Allow { url: String },
    ShowInternal { surface: Surface, url: String },
    OpenExternal { url: String, protocol: String },
    Deny { reason: String },
}

/// A decision made when a webview attempts to open a popup window.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(tag = "action", rename_all = "camelCase")]
pub enum PopupDecision {
    AllowNewTab { url: String, private: bool },
    OpenExternal { url: String },
    Deny { reason: String },
}

/// Classifies raw user input from the address bar or navigation call.
pub fn classify_input(raw: &str) -> NavigationTarget {
    let trimmed = raw.trim();
    if trimmed.is_empty() || trimmed == "about:blank" || trimmed == "about:newtab" {
        return NavigationTarget::Blank;
    }

    // Check for internal surface URLs (blanc://<surface>/)
    if trimmed.starts_with("blanc://") {
        if let Some(surface) = Surface::from_url(trimmed) {
            return NavigationTarget::Internal(surface);
        }
        // Unknown blanc:// host or path traversal is rejected
        return NavigationTarget::ForbiddenProtocol("blanc:".to_string());
    }

    // Check for scheme
    if let Some((scheme, _)) = trimmed.split_once(':') {
        let scheme_lower = format!("{}:", scheme.to_lowercase());

        for &forbidden in FORBIDDEN_TOP_LEVEL_PROTOCOLS {
            if scheme_lower == forbidden {
                return NavigationTarget::ForbiddenProtocol(scheme_lower);
            }
        }

        for &blocked in BLOCKED_OS_PROTOCOLS {
            if scheme_lower == blocked {
                return NavigationTarget::BlockedOSProtocol(scheme_lower);
            }
        }

        for &direct in DIRECT_HANDOFF_PROTOCOLS {
            if scheme_lower == direct {
                return NavigationTarget::ExternalHandoff {
                    protocol: scheme_lower,
                    url: trimmed.to_string(),
                    direct: true,
                };
            }
        }

        if scheme_lower == "http:" || scheme_lower == "https:" {
            return NavigationTarget::WebUrl(trimmed.to_string());
        }
    }

    // Detect if input is a domain or web target without scheme
    if is_likely_url(trimmed) {
        return NavigationTarget::WebUrl(format!("https://{}", trimmed));
    }

    // Otherwise, treat as search query
    NavigationTarget::SearchQuery(trimmed.to_string())
}

/// Checks whether an input string looks like a hostname/URL rather than a search query.
fn is_likely_url(input: &str) -> bool {
    // If it contains spaces, it's a search query
    if input.contains(' ') {
        return false;
    }

    // localhost or localhost:port
    if input == "localhost" || input.starts_with("localhost:") {
        return true;
    }

    // IPv4 address check
    let parts: Vec<&str> = input.split('.').collect();
    if parts.len() == 4 && parts.iter().all(|p| p.parse::<u8>().is_ok()) {
        return true;
    }

    // Must have at least one dot separating domain labels, with valid chars
    if let Some((host, _path)) = input.split_once('/') {
        has_valid_domain_syntax(host)
    } else {
        has_valid_domain_syntax(input)
    }
}

fn has_valid_domain_syntax(host: &str) -> bool {
    let host = host.split(':').next().unwrap_or(host);
    let parts: Vec<&str> = host.split('.').collect();
    if parts.len() < 2 {
        return false;
    }
    // Last segment should look like a valid TLD (2+ alpha characters)
    let tld = parts.last().unwrap();
    if tld.len() < 2 || !tld.chars().all(|c| c.is_ascii_alphabetic()) {
        return false;
    }
    // Each part must not be empty and contain valid domain characters
    parts.iter().all(|p| !p.is_empty() && p.chars().all(|c| c.is_ascii_alphanumeric() || c == '-'))
}

/// Normalizes raw navigation input into an executable URL using the configured search engine.
pub fn normalize_navigation_target(raw: &str, search_engine: &str) -> Result<String, String> {
    match classify_input(raw) {
        NavigationTarget::Blank => Ok("about:blank".to_string()),
        NavigationTarget::Internal(surface) => Ok(surface.url()),
        NavigationTarget::WebUrl(url) => Ok(url),
        NavigationTarget::SearchQuery(query) => Ok(search_engine_url(&query, search_engine)),
        NavigationTarget::ExternalHandoff { url, .. } => Ok(url),
        NavigationTarget::ForbiddenProtocol(proto) => {
            Err(format!("Navigation to forbidden protocol '{}' was blocked", proto))
        }
        NavigationTarget::BlockedOSProtocol(proto) => {
            Err(format!("Navigation to dangerous OS protocol '{}' was blocked", proto))
        }
        NavigationTarget::InvalidInput(msg) => Err(msg),
    }
}

/// Builds the search query URL for a given provider.
pub fn search_engine_url(query: &str, search_engine: &str) -> String {
    let encoded = urlencoding::encode(query);
    match search_engine.to_lowercase().as_str() {
        "google" => format!("https://www.google.com/search?q={}", encoded),
        "bing" => format!("https://www.bing.com/search?q={}", encoded),
        "ecosia" => format!("https://www.ecosia.org/search?q={}", encoded),
        "kagi" => format!("https://kagi.com/search?q={}", encoded),
        "youtube" => format!("https://www.youtube.com/results?search_query={}", encoded),
        _ => format!("https://duckduckgo.com/?q={}", encoded),
    }
}

/// Evaluates whether a top-level navigation request should be allowed, routed internally,
/// handed off to the OS, or denied.
pub fn admit_top_level_navigation(raw_url: &str) -> NavigationDecision {
    match classify_input(raw_url) {
        NavigationTarget::Blank => NavigationDecision::Allow {
            url: "about:blank".to_string(),
        },
        NavigationTarget::Internal(surface) => NavigationDecision::ShowInternal {
            surface,
            url: surface.url(),
        },
        NavigationTarget::WebUrl(url) => NavigationDecision::Allow { url },
        NavigationTarget::ExternalHandoff { protocol, url, .. } => {
            NavigationDecision::OpenExternal { url, protocol }
        }
        NavigationTarget::ForbiddenProtocol(proto) => NavigationDecision::Deny {
            reason: format!("Top-level navigation to '{}' is forbidden", proto),
        },
        NavigationTarget::BlockedOSProtocol(proto) => NavigationDecision::Deny {
            reason: format!("Navigation to OS scheme '{}' is blocked", proto),
        },
        NavigationTarget::SearchQuery(query) => NavigationDecision::Allow {
            url: search_engine_url(&query, "duckduckgo"),
        },
        NavigationTarget::InvalidInput(msg) => NavigationDecision::Deny { reason: msg },
    }
}

/// Evaluates popup/window-open requests from child webviews.
pub fn admit_popup(raw_url: &str, opener_private: bool) -> PopupDecision {
    match classify_input(raw_url) {
        NavigationTarget::Blank => PopupDecision::AllowNewTab {
            url: "about:blank".to_string(),
            private: opener_private,
        },
        NavigationTarget::Internal(surface) => PopupDecision::AllowNewTab {
            url: surface.url(),
            private: opener_private,
        },
        NavigationTarget::WebUrl(url) => PopupDecision::AllowNewTab {
            url,
            private: opener_private,
        },
        NavigationTarget::ExternalHandoff { url, .. } => PopupDecision::OpenExternal { url },
        NavigationTarget::ForbiddenProtocol(proto) => PopupDecision::Deny {
            reason: format!("Popup with forbidden scheme '{}' was blocked", proto),
        },
        NavigationTarget::BlockedOSProtocol(proto) => PopupDecision::Deny {
            reason: format!("Popup with dangerous OS scheme '{}' was blocked", proto),
        },
        NavigationTarget::SearchQuery(_) | NavigationTarget::InvalidInput(_) => PopupDecision::Deny {
            reason: "Invalid popup target URL".to_string(),
        },
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn classifies_direct_and_secure_web_urls() {
        assert_eq!(
            classify_input("https://blancbrowser.com"),
            NavigationTarget::WebUrl("https://blancbrowser.com".to_string())
        );
        assert_eq!(
            classify_input("http://example.com/test?q=1"),
            NavigationTarget::WebUrl("http://example.com/test?q=1".to_string())
        );
    }

    #[test]
    fn infers_https_for_naked_domains() {
        assert_eq!(
            classify_input("blancbrowser.com"),
            NavigationTarget::WebUrl("https://blancbrowser.com".to_string())
        );
        assert_eq!(
            classify_input("sub.domain.org/path"),
            NavigationTarget::WebUrl("https://sub.domain.org/path".to_string())
        );
        assert_eq!(
            classify_input("localhost:3000"),
            NavigationTarget::WebUrl("https://localhost:3000".to_string())
        );
    }

    #[test]
    fn classifies_search_queries_with_spaces_or_non_domains() {
        assert_eq!(
            classify_input("minimal electron browser"),
            NavigationTarget::SearchQuery("minimal electron browser".to_string())
        );
        assert_eq!(
            classify_input("what is rust"),
            NavigationTarget::SearchQuery("what is rust".to_string())
        );
        assert_eq!(
            classify_input("something"),
            NavigationTarget::SearchQuery("something".to_string())
        );
    }

    #[test]
    fn blocks_forbidden_top_level_protocols() {
        assert_eq!(
            classify_input("javascript:alert(1)"),
            NavigationTarget::ForbiddenProtocol("javascript:".to_string())
        );
        assert_eq!(
            classify_input("data:text/html,<h1>XSS</h1>"),
            NavigationTarget::ForbiddenProtocol("data:".to_string())
        );
        assert_eq!(
            classify_input("file:///etc/passwd"),
            NavigationTarget::ForbiddenProtocol("file:".to_string())
        );
    }

    #[test]
    fn blocks_dangerous_os_protocols() {
        assert_eq!(
            classify_input("cmd:do-something"),
            NavigationTarget::BlockedOSProtocol("cmd:".to_string())
        );
        assert_eq!(
            classify_input("powershell:test"),
            NavigationTarget::BlockedOSProtocol("powershell:".to_string())
        );
        assert_eq!(
            classify_input("terminal:open"),
            NavigationTarget::BlockedOSProtocol("terminal:".to_string())
        );
    }

    #[test]
    fn admits_external_handoff_protocols() {
        match classify_input("mailto:support@blancbrowser.com") {
            NavigationTarget::ExternalHandoff { protocol, direct, .. } => {
                assert_eq!(protocol, "mailto:");
                assert!(direct);
            }
            other => panic!("Unexpected classification: {:?}", other),
        }

        match classify_input("tel:+1234567890") {
            NavigationTarget::ExternalHandoff { protocol, direct, .. } => {
                assert_eq!(protocol, "tel:");
                assert!(direct);
            }
            other => panic!("Unexpected classification: {:?}", other),
        }
    }

    #[test]
    fn routes_internal_surfaces() {
        assert_eq!(
            classify_input("blanc://newtab/"),
            NavigationTarget::Internal(Surface::NewTab)
        );
        assert_eq!(
            classify_input("blanc://settings/"),
            NavigationTarget::Internal(Surface::Settings)
        );
        assert_eq!(
            classify_input("blanc://history/"),
            NavigationTarget::Internal(Surface::History)
        );
        // Traversal is blocked
        assert_eq!(
            classify_input("blanc://newtab/../../secret"),
            NavigationTarget::ForbiddenProtocol("blanc:".to_string())
        );
    }

    #[test]
    fn popup_admission_preserves_opener_privacy() {
        let private_popup = admit_popup("https://example.com", true);
        assert_eq!(
            private_popup,
            PopupDecision::AllowNewTab {
                url: "https://example.com".to_string(),
                private: true
            }
        );

        let standard_popup = admit_popup("https://example.com", false);
        assert_eq!(
            standard_popup,
            PopupDecision::AllowNewTab {
                url: "https://example.com".to_string(),
                private: false
            }
        );

        let forbidden_popup = admit_popup("javascript:alert(1)", true);
        assert!(matches!(forbidden_popup, PopupDecision::Deny { .. }));
    }

    #[test]
    fn normalization_formats_search_query_with_selected_engine() {
        let ddg = normalize_navigation_target("hello world", "duckduckgo").unwrap();
        assert_eq!(ddg, "https://duckduckgo.com/?q=hello%20world");

        let google = normalize_navigation_target("hello world", "google").unwrap();
        assert_eq!(google, "https://www.google.com/search?q=hello%20world");

        let kagi = normalize_navigation_target("test query", "kagi").unwrap();
        assert_eq!(kagi, "https://kagi.com/search?q=test%20query");
    }
}

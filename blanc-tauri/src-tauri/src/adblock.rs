//! Built-in ad and tracker blocking engine with per-site exceptions and metrics.
//!
//! Behavioral references in the Electron app (the port's specification):
//!
//! - `src/main/adblock.js` — Ghostery engine and rule compilation
//! - `src/main/adblock-exceptions.js` — per-site allowlist / exceptions model
//! - `docs/ELECTRON_PARITY_ROADMAP.md` — Section 5: Real ad/tracker blocking
//! - `docs/PARITY_IPC_CONTRACT.md` — adblock commands and `BlockingStatus`

use serde::{Deserialize, Serialize};
use std::collections::HashSet;
use std::sync::atomic::{AtomicU64, Ordering};

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct BlockingStatus {
    pub enabled: bool,
    pub ready: bool,
    pub total_blocked: u64,
    pub error: Option<String>,
    pub exceptions: Vec<String>,
}

pub struct AdblockEngine {
    blocked_domains: HashSet<&'static str>,
    exceptions: HashSet<String>,
    total_blocked: AtomicU64,
    enabled: bool,
    ready: bool,
    error: Option<String>,
}

impl Default for AdblockEngine {
    fn default() -> Self {
        Self::new()
    }
}

impl AdblockEngine {
    pub fn new() -> Self {
        let mut set = HashSet::new();

        // High-frequency tracker and ad hostnames compiled from EasyList & EasyPrivacy
        let trackers = [
            "google-analytics.com",
            "googletagmanager.com",
            "doubleclick.net",
            "adservice.google.com",
            "pagead2.googlesyndication.com",
            "facebook.net",
            "connect.facebook.net",
            "pixel.facebook.com",
            "ads.twitter.com",
            "static.ads-twitter.com",
            "analytics.twitter.com",
            "criteo.com",
            "criteo.net",
            "taboola.com",
            "outbrain.com",
            "scorecardresearch.com",
            "quantserve.com",
            "hotjar.com",
            "mixpanel.com",
            "segment.io",
            "segment.com",
            "amplitude.com",
            "appsflyer.com",
            "branch.io",
            "adjust.com",
            "adroll.com",
            "adsystem.com",
            "pubmatic.com",
            "rubiconproject.com",
            "openx.net",
            "casalemedia.com",
            "yieldmo.com",
            "smartadserver.com",
            "moatads.com",
            "chartbeat.com",
            "optimizely.com",
            "newrelic.com",
            "nr-data.net",
            "sentry.io",
            "bugsnag.com",
            "adtech.de",
            "advertising.com",
            "exponential.com",
            "mediav.com",
            "serving-sys.com",
            "adnxs.com",
            "amazon-adsystem.com",
            "trackcmp.net",
            "fullstory.com",
            "mouseflow.com",
            "crazyegg.com",
            "fls-na.amazon.com",
            "ad-delivery.net",
            "ads-twitter.com",
            "bat.bing.com",
            "clarity.ms",
        ];

        for domain in trackers {
            set.insert(domain);
        }

        Self {
            blocked_domains: set,
            exceptions: HashSet::new(),
            total_blocked: AtomicU64::new(0),
            enabled: true,
            ready: true,
            error: None,
        }
    }

    /// Checks if a request URL is an ad/tracker, respecting the master toggle and per-site exceptions.
    pub fn is_blocked(&self, url: &str) -> bool {
        if !self.enabled || !self.ready {
            return false;
        }

        if let Ok(parsed) = url::Url::parse(url) {
            if let Some(host) = parsed.host_str() {
                let host_lower = host.to_lowercase();

                // Check per-site exceptions (allowlist)
                if self.is_host_excepted(&host_lower) {
                    return false;
                }

                // Check blocked domains
                for domain in &self.blocked_domains {
                    if host_lower == *domain || host_lower.ends_with(&format!(".{}", domain)) {
                        self.total_blocked.fetch_add(1, Ordering::Relaxed);
                        return true;
                    }
                }
            }
        }

        false
    }

    /// Checks if a hostname matches any per-site exception rule.
    pub fn is_host_excepted(&self, host: &str) -> bool {
        let clean = host.to_lowercase();
        self.exceptions
            .iter()
            .any(|exc| clean == *exc || clean.ends_with(&format!(".{}", exc)))
    }

    pub fn is_url_excepted(&self, url: &str) -> bool {
        if let Ok(parsed) = url::Url::parse(url) {
            if let Some(host) = parsed.host_str() {
                return self.is_host_excepted(host);
            }
        }
        false
    }

    pub fn add_exception(&mut self, hostname: &str) {
        let clean = hostname.trim().to_lowercase();
        if !clean.is_empty() {
            self.exceptions.insert(clean);
        }
    }

    pub fn remove_exception(&mut self, hostname: &str) {
        let clean = hostname.trim().to_lowercase();
        self.exceptions.remove(&clean);
    }

    pub fn get_status(&self) -> BlockingStatus {
        let mut exc: Vec<String> = self.exceptions.iter().cloned().collect();
        exc.sort();
        BlockingStatus {
            enabled: self.enabled,
            ready: self.ready,
            total_blocked: self.total_blocked.load(Ordering::Relaxed),
            error: self.error.clone(),
            exceptions: exc,
        }
    }

    pub fn get_total_blocked(&self) -> u64 {
        self.total_blocked.load(Ordering::Relaxed)
    }

    pub fn is_enabled(&self) -> bool {
        self.enabled
    }

    pub fn set_enabled(&mut self, enabled: bool) {
        self.enabled = enabled;
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn blocks_known_trackers() {
        let engine = AdblockEngine::new();
        assert!(engine.is_blocked("https://www.google-analytics.com/analytics.js"));
        assert!(engine.is_blocked("https://sub.doubleclick.net/ad"));
        assert!(engine.is_blocked("https://connect.facebook.net/en_US/fbevents.js"));
    }

    #[test]
    fn permits_benign_urls() {
        let engine = AdblockEngine::new();
        assert!(!engine.is_blocked("https://blancbrowser.com"));
        assert!(!engine.is_blocked("https://en.wikipedia.org/wiki/Rust_(programming_language)"));
        assert!(!engine.is_blocked("https://github.com/bnfy/blanc"));
    }

    #[test]
    fn per_site_exceptions_bypass_blocking() {
        let mut engine = AdblockEngine::new();
        let tracker = "https://analytics.twitter.com/i/adsct";
        assert!(engine.is_blocked(tracker));

        // Add exception
        engine.add_exception("analytics.twitter.com");
        assert!(!engine.is_blocked(tracker));

        // Remove exception
        engine.remove_exception("analytics.twitter.com");
        assert!(engine.is_blocked(tracker));
    }

    #[test]
    fn toggle_disables_blocking() {
        let mut engine = AdblockEngine::new();
        assert!(engine.is_blocked("https://doubleclick.net/pixel"));

        engine.set_enabled(false);
        assert!(!engine.is_blocked("https://doubleclick.net/pixel"));
    }
}

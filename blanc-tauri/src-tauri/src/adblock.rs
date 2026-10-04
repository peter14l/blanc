use std::collections::HashSet;
use std::sync::atomic::{AtomicU64, Ordering};

pub struct AdblockEngine {
    blocked_domains: HashSet<&'static str>,
    total_blocked: AtomicU64,
    enabled: bool,
}

impl AdblockEngine {
    pub fn new() -> Self {
        let mut set = HashSet::new();

        // Common high-frequency ad and tracker hostnames (from EasyList / EasyPrivacy)
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
        ];

        for domain in trackers {
            set.insert(domain);
        }

        Self {
            blocked_domains: set,
            total_blocked: AtomicU64::new(0),
            enabled: true,
        }
    }

    pub fn is_blocked(&self, url: &str) -> bool {
        if !self.enabled {
            return false;
        }

        if let Ok(parsed) = url::Url::parse(url) {
            if let Some(host) = parsed.host_str() {
                let host_lower = host.to_lowercase();
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

    pub fn get_total_blocked(&self) -> u64 {
        self.total_blocked.load(Ordering::Relaxed)
    }

    pub fn set_enabled(&mut self, enabled: bool) {
        self.enabled = enabled;
    }

    pub fn is_enabled(&self) -> bool {
        self.enabled
    }
}

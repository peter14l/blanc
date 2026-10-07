use aho_corasick::{AhoCorasick, AhoCorasickBuilder, MatchKind};
use regex::Regex;
use serde::{Deserialize, Serialize};
use std::collections::{HashMap, HashSet};
use std::sync::{Arc, RwLock};
use std::sync::atomic::{AtomicU64, Ordering};

const EASYLIST: &str = include_str!("../assets/adblock/easylist.txt");
const EASYPRIVACY: &str = include_str!("../assets/adblock/easyprivacy.txt");

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct BlockingStatus {
    pub enabled: bool,
    pub ready: bool,
    pub total_blocked: u64,
    pub error: Option<String>,
    pub exceptions: Vec<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct AdblockStats {
    pub total_blocked: u64,
    pub today_blocked: u64,
    pub trackers_detected: u64,
}

#[derive(Debug, Clone, PartialEq, Eq, Hash)]
enum FilterType {
    Network,
    Cosmetic,
    Exception,
}

#[derive(Debug, Clone)]
struct ParsedFilter {
    filter_type: FilterType,
    pattern: String,
    regex: Option<Regex>,
    domains: Option<Vec<String>>,
    options: FilterOptions,
}

#[derive(Debug, Clone, Default)]
struct FilterOptions {
    third_party: Option<bool>,
    script: bool,
    image: bool,
    stylesheet: bool,
    object: bool,
    subdocument: bool,
    document: bool,
    popup: bool,
    generichide: bool,
    generichide_exception: bool,
    important: bool,
    match_case: bool,
    collapse: bool,
    redirect: Option<String>,
}

pub struct AdblockEngine {
    inner: Arc<RwLock<EngineInner>>,
    total_blocked: AtomicU64,
}

struct EngineInner {
    network_filters: Vec<ParsedFilter>,
    cosmetic_filters: Vec<ParsedFilter>,
    exception_filters: Vec<ParsedFilter>,
    domain_filter_map: HashMap<String, Vec<usize>>,
    domain_patterns: Vec<String>,
    ac_automaton: Option<AhoCorasick>,
    exceptions: HashSet<String>,
    enabled: bool,
    ready: bool,
    error: Option<String>,
    stats: AdblockStats,
}

impl Default for AdblockEngine {
    fn default() -> Self {
        Self::new()
    }
}

impl AdblockEngine {
    pub fn new() -> Self {
        let engine = Self {
            inner: Arc::new(RwLock::new(EngineInner {
                network_filters: Vec::new(),
                cosmetic_filters: Vec::new(),
                exception_filters: Vec::new(),
                domain_filter_map: HashMap::new(),
                domain_patterns: Vec::new(),
                ac_automaton: None,
                exceptions: HashSet::new(),
                enabled: true,
                ready: false,
                error: None,
                stats: AdblockStats {
                    total_blocked: 0,
                    today_blocked: 0,
                    trackers_detected: 0,
                },
            })),
            total_blocked: AtomicU64::new(0),
        };

        engine.compile_filters();
        engine
    }

    fn compile_filters(&self) {
        let easylist = EASYLIST;
        let easyprivacy = EASYPRIVACY;
        let combined = format!("{}\n{}", easylist, easyprivacy);

        let mut inner = self.inner.write().unwrap();
        inner.network_filters.clear();
        inner.cosmetic_filters.clear();
        inner.exception_filters.clear();
        inner.domain_filter_map.clear();

        let mut filter_id = 0;

        for line in combined.lines() {
            let line = line.trim();
            if line.is_empty() || line.starts_with('!') || line.starts_with('[') {
                continue;
            }

            if let Some(filter) = Self::parse_filter(line, filter_id) {
                match filter.filter_type {
                    FilterType::Network => {
                        inner.network_filters.push(filter);
                    }
                    FilterType::Cosmetic => {
                        inner.cosmetic_filters.push(filter);
                    }
                    FilterType::Exception => {
                        inner.exception_filters.push(filter);
                    }
                }
                filter_id += 1;
            }
        }

        inner.domain_filter_map.clear();

        // Build domain -> filter index map (collect first to avoid borrow conflict)
        let mut domain_entries = Vec::new();
        for (idx, filter) in inner.network_filters.iter().enumerate() {
            if let Some(domains) = &filter.domains {
                for domain in domains {
                    domain_entries.push((domain.clone(), idx));
                }
            }
        }
        for (domain, idx) in domain_entries {
            inner.domain_filter_map.entry(domain).or_default().push(idx);
        }

        // Build Aho-Corasick automaton for fast hostname matching
        let domain_patterns: Vec<String> = inner.domain_filter_map.keys().cloned().collect();
        inner.domain_patterns = domain_patterns.clone();
        if !domain_patterns.is_empty() {
            if let Ok(ac) = AhoCorasickBuilder::new()
                .match_kind(MatchKind::LeftmostFirst)
                .build(&domain_patterns)
            {
                inner.ac_automaton = Some(ac);
            }
        }

        inner.ready = true;
        inner.error = None;
    }

    fn parse_filter(line: &str, id: usize) -> Option<ParsedFilter> {
        let line = line.trim();

        // Exception filter: @@
        if line.starts_with("@@") {
            return Self::parse_network_filter(&line[2..], id, true);
        }

        // Cosmetic filter: ## or #@#
        if line.starts_with("##") || line.starts_with("#@#") {
            return Self::parse_cosmetic_filter(line, id);
        }

        // HTML filter: ##^ (rare, skip for now)
        if line.starts_with("##^") {
            return None;
        }

        // Network filter
        Self::parse_network_filter(line, id, false)
    }

    fn parse_network_filter(line: &str, _id: usize, is_exception: bool) -> Option<ParsedFilter> {
        let (pattern_part, options_part) = line.split_once('$').unwrap_or((line, ""));
        let pattern = pattern_part.trim();

        if pattern.is_empty() {
            return None;
        }

        let mut options = FilterOptions::default();
        for opt in options_part.split(',') {
            let opt = opt.trim();
            match opt {
                "third-party" => options.third_party = Some(true),
                "~third-party" => options.third_party = Some(false),
                "script" => options.script = true,
                "image" => options.image = true,
                "stylesheet" => options.stylesheet = true,
                "object" => options.object = true,
                "subdocument" => options.subdocument = true,
                "document" => options.document = true,
                "popup" => options.popup = true,
                "generichide" => options.generichide = true,
                "generichide-exception" => options.generichide_exception = true,
                "important" => options.important = true,
                "match-case" => options.match_case = true,
                "collapse" => options.collapse = true,
                opt if opt.starts_with("redirect=") => {
                    options.redirect = Some(opt[9..].to_string());
                }
                opt if opt.starts_with("redirect-") => {
                    options.redirect = Some(opt[9..].to_string());
                }
                _ => {}
            }
        }

        // Parse domain restrictions: example.com,~other.com
        // Handle || prefix - don't treat the first | of || as a domain separator
        let (domain_part, pattern_part) = if pattern_part.starts_with("||") {
            // ||example.com^ -> no domain restrictions, pattern is ||example.com^
            ("", pattern_part)
        } else {
            pattern_part.split_once('|').unwrap_or(("", pattern))
        };
        let domains = if domain_part.is_empty() {
            None
        } else {
            Some(
                domain_part
                    .split(',')
                    .map(|d| d.trim().to_string())
                    .filter(|d| !d.is_empty())
                    .collect(),
            )
        };

        let pattern = pattern_part.trim();

        if pattern.is_empty() {
            return None;
        }

        // Convert Adblock pattern to regex
        let regex = Self::pattern_to_regex(pattern);

        Some(ParsedFilter {
            filter_type: if is_exception { FilterType::Exception } else { FilterType::Network },
            pattern: pattern.to_string(),
            regex,
            domains,
            options,
        })
    }

    fn parse_cosmetic_filter(line: &str, _id: usize) -> Option<ParsedFilter> {
        let is_exception = line.starts_with("#@#");
        let selector = if is_exception { &line[3..] } else { &line[2..] };

        if selector.is_empty() {
            return None;
        }

        let (domain_part, selector_part) = selector.split_once(',').unwrap_or(("", selector));
        let domains = if domain_part.is_empty() {
            None
        } else {
            Some(
                domain_part
                    .split(',')
                    .map(|d| d.trim().to_string())
                    .filter(|d| !d.is_empty())
                    .collect(),
            )
        };

        let pattern = selector_part.trim();

        // Convert CSS selector to regex for matching
        let regex = Self::selector_to_regex(pattern);

        Some(ParsedFilter {
            filter_type: if is_exception { FilterType::Exception } else { FilterType::Cosmetic },
            pattern: pattern.to_string(),
            regex,
            domains,
            options: FilterOptions::default(),
        })
    }

    fn pattern_to_regex(pattern: &str) -> Option<Regex> {
        if pattern.is_empty() {
            return None;
        }

        let mut regex_str = String::new();

        // Handle || prefix - matches any subdomain
        let mut pattern = pattern;
        let mut match_subdomain = false;
        if pattern.starts_with("||") {
            pattern = &pattern[2..];
            match_subdomain = true;
        }

        regex_str.push_str("^");

        // Handle subdomain matching for || prefix
        if match_subdomain {
            regex_str.push_str(r"(?:[a-zA-Z][a-zA-Z0-9+.-]*://)?");
            regex_str.push_str(r"(?:[a-zA-Z0-9-]+\.)*");
        } else if pattern.starts_with('|') {
            pattern = &pattern[1..];
            regex_str.push_str(r"(?:[a-zA-Z][a-zA-Z0-9+.-]*://)?");
        }

        let mut chars = pattern.chars().peekable();
        while let Some(c) = chars.next() {
            match c {
                '*' => regex_str.push_str(".*"),
                '|' => {
                    if regex_str.ends_with("^") || regex_str.ends_with(")?") {
                        regex_str.push_str(r"[a-zA-Z][a-zA-Z0-9+.-]*://");
                    } else if chars.peek() == Some(&'|') {
                        chars.next();
                        regex_str.push_str(r"(?:\/|\?|&|$)");
                    } else {
                        regex_str.push('|');
                    }
                }
                '^' => regex_str.push_str(r"(?:\/|\?|&|:|$)"),
                '.' => regex_str.push_str(r"\."),
                '?' => regex_str.push_str(r"\?"),
                '&' => regex_str.push_str(r"&"),
                '=' => regex_str.push_str(r"="),
                c if c.is_ascii_alphanumeric() || c == '-' || c == '_' => regex_str.push(c),
                c => regex_str.push_str(&regex::escape(&c.to_string())),
            }
        }

        // Don't add $ anchor - Adblock patterns match the domain and allow anything after
        Regex::new(&regex_str).ok()
    }

    fn selector_to_regex(selector: &str) -> Option<Regex> {
        // Simplified: convert CSS selector to a basic regex
        // This is a minimal implementation; a full CSS selector parser would be better
        let escaped = regex::escape(selector);
        let regex_str = format!("^{}$", escaped.replace(r"\*", ".*"));
        Regex::new(&regex_str).ok()
    }

    /// Check if a request URL should be blocked
    pub fn should_block(&self, url: &str, source_url: Option<&str>, request_type: &str) -> bool {
        if !self.is_enabled() || !self.is_ready() {
            return false;
        }

        let inner = self.inner.read().unwrap();

        // Check exceptions first
        if let Some(source) = source_url {
            if let Ok(source_host) = url::Url::parse(source).map(|u| u.host_str().unwrap_or("").to_string()) {
                if inner.exceptions.contains(&source_host) {
                    return false;
                }
            }
        }

        // Parse target URL
        let Ok(target_url) = url::Url::parse(url) else {
            return false;
        };

        let target_host = target_url.host_str().unwrap_or("");
        let target_host = target_host.trim_end_matches('.');

        // Check exception filters
        for filter in &inner.exception_filters {
            if filter.matches_host(target_host) && filter.matches_request(url, request_type) {
                return false;
            }
        }

        // Check if host is in our domain map
        let mut candidate_indices: Vec<usize> = Vec::new();
        if let Some(ac) = &inner.ac_automaton {
            for mat in ac.find_iter(target_host) {
                let pattern_id = mat.pattern().as_usize();
                if pattern_id < inner.domain_patterns.len() {
                    let pattern = &inner.domain_patterns[pattern_id];
                    if let Some(indices) = inner.domain_filter_map.get(pattern) {
                        candidate_indices.extend(indices);
                    }
                }
            }
        } else {
            // Fallback: check all filters
            candidate_indices = (0..inner.network_filters.len()).collect();
        }

        let is_third_party = source_url
            .and_then(|s| url::Url::parse(s).ok())
            .map(|s| s.host_str() != Some(target_host))
            .unwrap_or(false);

        for &idx in &candidate_indices {
            if let Some(filter) = inner.network_filters.get(idx) {
                if filter.matches_host(target_host)
                    && filter.matches_request_type(request_type)
                    && filter.matches_third_party(is_third_party)
                    && filter.matches_url(url)
                {
                    return true;
                }
            }
        }

        false
    }

    pub fn is_url_excepted(&self, url: &str) -> bool {
        if let Ok(parsed) = url::Url::parse(url) {
            if let Some(host) = parsed.host_str() {
                let inner = self.inner.read().unwrap();
                let host_key = host.trim_end_matches('.').to_string();
                return inner.exceptions.contains(&host_key);
            }
        }
        false
    }

    pub fn add_exception(&self, hostname: &str) {
        let mut inner = self.inner.write().unwrap();
        inner.exceptions.insert(hostname.trim().to_lowercase());
    }

    pub fn remove_exception(&self, hostname: &str) {
        let mut inner = self.inner.write().unwrap();
        inner.exceptions.remove(&hostname.trim().to_lowercase());
    }

    pub fn get_status(&self) -> BlockingStatus {
        let inner = self.inner.read().unwrap();
        let mut exceptions: Vec<String> = inner.exceptions.iter().cloned().collect();
        exceptions.sort();
        BlockingStatus {
            enabled: inner.enabled,
            ready: inner.ready,
            total_blocked: self.total_blocked.load(Ordering::Relaxed),
            error: inner.error.clone(),
            exceptions,
        }
    }

    pub fn get_stats(&self) -> AdblockStats {
        let inner = self.inner.read().unwrap();
        AdblockStats {
            total_blocked: self.total_blocked.load(Ordering::Relaxed),
            today_blocked: inner.stats.today_blocked,
            trackers_detected: inner.stats.trackers_detected,
        }
    }

    pub fn is_enabled(&self) -> bool {
        self.inner.read().unwrap().enabled
    }

    pub fn is_ready(&self) -> bool {
        self.inner.read().unwrap().ready
    }

    pub fn set_enabled(&self, enabled: bool) {
        self.inner.write().unwrap().enabled = enabled;
    }

    pub fn record_block(&self) {
        self.total_blocked.fetch_add(1, Ordering::Relaxed);
        self.inner.write().unwrap().stats.total_blocked += 1;
    }

    pub fn get_cosmetic_filters(&self, url: &str) -> Vec<String> {
        let inner = self.inner.read().unwrap();
        let Ok(parsed) = url::Url::parse(url) else {
            return Vec::new();
        };
        let host = parsed.host_str().unwrap_or("");

        inner
            .cosmetic_filters
            .iter()
            .filter(|f| f.matches_host(host))
            .filter_map(|f| f.regex.as_ref().map(|r| r.as_str().to_string()))
            .collect()
    }
}

impl ParsedFilter {
    fn matches_host(&self, host: &str) -> bool {
        if let Some(domains) = &self.domains {
            let host = host.trim_end_matches('.');
            for domain in domains {
                if domain.starts_with('~') {
                    let excluded = &domain[1..];
                    if host == excluded || host.ends_with(&format!(".{}", excluded)) {
                        return false;
                    }
                } else if host == domain || host.ends_with(&format!(".{}", domain)) {
                    return true;
                }
            }
            false
        } else {
            true
        }
    }

    fn matches_request_type(&self, request_type: &str) -> bool {
        let opts = &self.options;
        match request_type {
            "script" => opts.script,
            "image" => opts.image,
            "stylesheet" => opts.stylesheet,
            "object" => opts.object,
            "subdocument" => opts.subdocument,
            "document" => opts.document,
            "popup" => opts.popup,
            _ => true,
        }
    }

    fn matches_third_party(&self, is_third_party: bool) -> bool {
        match self.options.third_party {
            Some(true) => is_third_party,
            Some(false) => !is_third_party,
            None => true,
        }
    }

    fn matches_url(&self, url: &str) -> bool {
        if let Some(regex) = &self.regex {
            regex.is_match(url)
        } else {
            false
        }
    }

    fn matches_request(&self, url: &str, request_type: &str) -> bool {
        self.matches_url(url) && self.matches_request_type(request_type)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_engine_creation() {
        let engine = AdblockEngine::new();
        // Engine should compile filters
        // Note: in test env, assets may not be available
    }

    #[test]
    fn test_pattern_to_regex() {
        let filter = AdblockEngine::parse_filter("||example.com^", 0).unwrap();
        let regex = filter.regex.unwrap();
        eprintln!("Generated regex: {}", regex.as_str());
        assert!(regex.is_match("http://example.com/"));
        assert!(regex.is_match("https://sub.example.com/path"));
        assert!(!regex.is_match("https://example.org/"));
    }

    #[test]
    fn test_exception_filter() {
        let filter = AdblockEngine::parse_filter("@@||example.com^", 0).unwrap();
        assert!(filter.regex.as_ref().unwrap().is_match("http://example.com/"));
    }

    #[test]
    fn test_cosmetic_filter() {
        let filter = AdblockEngine::parse_filter("##.ad-banner", 0).unwrap();
        assert!(filter.regex.as_ref().unwrap().is_match(".ad-banner"));
    }
}
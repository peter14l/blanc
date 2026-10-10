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
    has_type_restriction: bool,
    script: bool,
    image: bool,
    stylesheet: bool,
    object: bool,
    subdocument: bool,
    document: bool,
    popup: bool,
    xmlhttprequest: bool,
    fetch: bool,
    ping: bool,
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
    generic_filter_indices: Vec<usize>,
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
                generic_filter_indices: Vec::new(),
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

    fn extract_domain_from_pattern(pattern: &str) -> Option<String> {
        let p = pattern.trim();
        if p.starts_with("||") {
            let rest = &p[2..];
            let end = rest.find(['^', '/', '*', '?', ':']).unwrap_or(rest.len());
            let domain = rest[..end].trim_end_matches('.');
            if !domain.is_empty() {
                return Some(domain.to_lowercase());
            }
        }
        None
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
        inner.generic_filter_indices.clear();

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

        // Build domain -> filter index map and track generic filters
        let mut domain_entries = Vec::new();
        let mut generic_indices = Vec::new();

        for (idx, filter) in inner.network_filters.iter().enumerate() {
            let mut indexed = false;
            if let Some(domains) = &filter.domains {
                for domain in domains {
                    let d = domain.trim_start_matches('~').trim();
                    if !d.is_empty() {
                        domain_entries.push((d.to_lowercase(), idx));
                        indexed = true;
                    }
                }
            }
            if let Some(domain) = Self::extract_domain_from_pattern(&filter.pattern) {
                domain_entries.push((domain, idx));
                indexed = true;
            }
            if !indexed {
                generic_indices.push(idx);
            }
        }

        for (domain, idx) in domain_entries {
            inner.domain_filter_map.entry(domain).or_default().push(idx);
        }
        inner.generic_filter_indices = generic_indices;

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
        if line.contains("##") || line.contains("#@#") {
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
        let mut domains: Option<Vec<String>> = None;

        if !options_part.is_empty() {
            for opt in options_part.split(',') {
                let opt = opt.trim();
                match opt {
                    "third-party" => options.third_party = Some(true),
                    "~third-party" => options.third_party = Some(false),
                    "script" => {
                        options.script = true;
                        options.has_type_restriction = true;
                    }
                    "image" => {
                        options.image = true;
                        options.has_type_restriction = true;
                    }
                    "stylesheet" => {
                        options.stylesheet = true;
                        options.has_type_restriction = true;
                    }
                    "object" => {
                        options.object = true;
                        options.has_type_restriction = true;
                    }
                    "subdocument" => {
                        options.subdocument = true;
                        options.has_type_restriction = true;
                    }
                    "document" => {
                        options.document = true;
                        options.has_type_restriction = true;
                    }
                    "popup" => {
                        options.popup = true;
                        options.has_type_restriction = true;
                    }
                    "xmlhttprequest" | "xhr" => {
                        options.xmlhttprequest = true;
                        options.has_type_restriction = true;
                    }
                    "fetch" => {
                        options.fetch = true;
                        options.has_type_restriction = true;
                    }
                    "ping" => {
                        options.ping = true;
                        options.has_type_restriction = true;
                    }
                    "generichide" => options.generichide = true,
                    "generichide-exception" => options.generichide_exception = true,
                    "important" => options.important = true,
                    "match-case" => options.match_case = true,
                    "collapse" => options.collapse = true,
                    opt if opt.starts_with("domain=") => {
                        let doms: Vec<String> = opt[7..]
                            .split('|')
                            .map(|d| d.trim().to_string())
                            .filter(|d| !d.is_empty())
                            .collect();
                        if !doms.is_empty() {
                            domains = Some(doms);
                        }
                    }
                    opt if opt.starts_with("redirect=") => {
                        options.redirect = Some(opt[9..].to_string());
                    }
                    opt if opt.starts_with("redirect-") => {
                        options.redirect = Some(opt[9..].to_string());
                    }
                    _ => {}
                }
            }
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
        let (is_exception, separator) = if line.contains("#@#") {
            (true, "#@#")
        } else {
            (false, "##")
        };

        let (domain_part, selector_part) = line.split_once(separator).unwrap_or(("", line));
        let domains = if domain_part.trim().is_empty() {
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

        let selector = selector_part.trim();
        if selector.is_empty() || selector.starts_with('^') {
            return None;
        }

        Some(ParsedFilter {
            filter_type: if is_exception { FilterType::Exception } else { FilterType::Cosmetic },
            pattern: selector.to_string(),
            regex: None,
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

pub fn get_etld_plus_one(host: &str) -> &str {
    let host = host.trim_end_matches('.');
    let parts: Vec<&str> = host.split('.').collect();
    if parts.len() <= 2 {
        return host;
    }
    let last = parts[parts.len() - 1];
    let second_last = parts[parts.len() - 2];
    let is_two_part_tld = (last.len() == 2 && matches!(second_last, "co" | "com" | "org" | "net" | "edu" | "gov"))
        && !matches!(second_last, "fna" | "xx");

    if is_two_part_tld && parts.len() >= 3 {
        let start = host.len() - last.len() - 1 - second_last.len() - 1 - parts[parts.len() - 3].len();
        &host[start..]
    } else {
        let start = host.len() - last.len() - 1 - second_last.len();
        &host[start..]
    }
}

pub fn is_same_entity(source_host: &str, target_host: &str) -> bool {
    if source_host == target_host {
        return true;
    }
    let s_etld = Self::get_etld_plus_one(source_host);
    let t_etld = Self::get_etld_plus_one(target_host);
    if s_etld == t_etld {
        return true;
    }
    // Meta / Instagram / Facebook / Threads cluster
    const META_DOMAINS: &[&str] = &[
        "instagram.com",
        "cdninstagram.com",
        "facebook.com",
        "fbcdn.net",
        "fbsbx.com",
        "threads.net",
        "messenger.com",
        "meta.com",
    ];
    if META_DOMAINS.contains(&s_etld) && META_DOMAINS.contains(&t_etld) {
        return true;
    }
    // Google / YouTube cluster
    const GOOGLE_DOMAINS: &[&str] = &[
        "google.com",
        "youtube.com",
        "gstatic.com",
        "googleusercontent.com",
        "ggpht.com",
        "ytimg.com",
    ];
    if GOOGLE_DOMAINS.contains(&s_etld) && GOOGLE_DOMAINS.contains(&t_etld) {
        return true;
    }
    // Twitter / X cluster
    const TWITTER_DOMAINS: &[&str] = &[
        "twitter.com",
        "x.com",
        "twimg.com",
    ];
    if TWITTER_DOMAINS.contains(&s_etld) && TWITTER_DOMAINS.contains(&t_etld) {
        return true;
    }
    // Reddit cluster
    const REDDIT_DOMAINS: &[&str] = &[
        "reddit.com",
        "redd.it",
        "redditstatic.com",
        "redditmedia.com",
    ];
    if REDDIT_DOMAINS.contains(&s_etld) && REDDIT_DOMAINS.contains(&t_etld) {
        return true;
    }
    false
}

    /// Check if a request URL should be blocked
    pub fn should_block(&self, url: &str, source_url: Option<&str>, request_type: &str) -> bool {
        if !self.is_enabled() || !self.is_ready() {
            return false;
        }

        let inner = self.inner.read().unwrap();

        // Check exceptions first
        if let Some(source) = source_url {
            if let Ok(source_parsed) = url::Url::parse(source) {
                let source_host = source_parsed.host_str().unwrap_or("").trim_end_matches('.');
                if inner.exceptions.contains(source_host) {
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

        // Check if target host is excepted
        if inner.exceptions.contains(target_host) {
            return false;
        }

        // Always protect known media CDNs from being blocked as general images/media
        if request_type == "image" || request_type == "media" {
            let t_etld = Self::get_etld_plus_one(target_host);
            if matches!(
                t_etld,
                "fbcdn.net"
                    | "cdninstagram.com"
                    | "twimg.com"
                    | "redditstatic.com"
                    | "redditmedia.com"
                    | "ytimg.com"
                    | "googleusercontent.com"
                    | "ggpht.com"
            ) {
                return false;
            }
        }

        // Check exception filters
        for filter in &inner.exception_filters {
            if filter.matches_host(target_host) && filter.matches_request(url, request_type) {
                return false;
            }
        }

        // Determine if request is third-party using entity-aware domain matching
        let is_third_party = if let Some(source) = source_url {
            if let Ok(source_parsed) = url::Url::parse(source) {
                let source_host = source_parsed.host_str().unwrap_or("").trim_end_matches('.');
                if source_host.is_empty() {
                    true
                } else {
                    !Self::is_same_entity(source_host, target_host)
                }
            } else {
                true
            }
        } else {
            true
        };

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
        candidate_indices.extend(&inner.generic_filter_indices);
        candidate_indices.sort_unstable();
        candidate_indices.dedup();

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
        let host = parsed.host_str().unwrap_or("").trim_end_matches('.');

        inner
            .cosmetic_filters
            .iter()
            .filter(|f| f.matches_host(host))
            .map(|f| f.pattern.clone())
            .collect()
    }

    pub fn get_cosmetic_css(&self, url: &str) -> Option<String> {
        let filters = self.get_cosmetic_filters(url);
        if filters.is_empty() {
            return None;
        }
        Some(format!("{} {{ display: none !important; }}", filters.join(", ")))
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
        if !self.options.has_type_restriction {
            return true;
        }
        let opts = &self.options;
        match request_type {
            "script" => opts.script,
            "image" => opts.image,
            "stylesheet" => opts.stylesheet,
            "object" => opts.object,
            "subdocument" => opts.subdocument,
            "document" => opts.document,
            "popup" => opts.popup,
            "xmlhttprequest" => opts.xmlhttprequest || opts.fetch,
            "fetch" => opts.fetch || opts.xmlhttprequest,
            "ping" => opts.ping,
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
        let _engine = AdblockEngine::new();
    }

    #[test]
    fn test_pattern_to_regex() {
        let filter = AdblockEngine::parse_filter("||example.com^", 0).unwrap();
        let regex = filter.regex.unwrap();
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
        assert_eq!(filter.pattern, ".ad-banner");
        assert!(filter.matches_host("example.com"));

        let site_filter = AdblockEngine::parse_filter("example.com##.sponsor", 1).unwrap();
        assert_eq!(site_filter.pattern, ".sponsor");
        assert!(site_filter.matches_host("example.com"));
        assert!(!site_filter.matches_host("other.com"));
    }

    #[test]
    fn test_should_block() {
        let engine = AdblockEngine::new();
        assert!(engine.should_block("https://google-analytics.com/analytics.js", None, "script"));
        assert!(engine.should_block("https://doubleclick.net/ad.js", None, "script"));
        assert!(!engine.should_block("https://example.com/main.js", None, "script"));

        // Instagram and Meta media CDN tests
        assert!(!engine.should_block(
            "https://instagram.fccu27-1.fna.fbcdn.net/v/t51.82787-19/628349641_profile_pic.jpg",
            Some("https://www.instagram.com/"),
            "image"
        ));
        assert!(!engine.should_block(
            "https://scontent.fccu20-1.fna.fbcdn.net/v/t1.30497-1/post_image.jpg",
            Some("https://www.instagram.com/"),
            "image"
        ));
        assert!(!engine.should_block(
            "https://static.cdninstagram.com/rsrc.php/v3/yP/r/bundle.js",
            Some("https://www.instagram.com/"),
            "script"
        ));

        // Entity matching tests
        assert!(AdblockEngine::is_same_entity("www.instagram.com", "instagram.fccu27-1.fna.fbcdn.net"));
        assert!(AdblockEngine::is_same_entity("instagram.com", "static.cdninstagram.com"));
        assert!(!AdblockEngine::is_same_entity("example.com", "google-analytics.com"));
    }
}
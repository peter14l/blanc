//! Discovers installed browser profiles on the current OS.
//!
//! Supports:
//! - Chromium family: Chrome, Edge, Brave, Vivaldi, Opera, Arc.
//! - Mozilla Firefox.
//!
//! Only runs on explicit user request (F30 invariant).

use std::fs;
use std::path::{Path, PathBuf};

use super::BrowserProfileSource;

/// Enumerate all detectable browser profiles on this machine.
pub fn discover_all() -> Vec<BrowserProfileSource> {
    let mut sources = Vec::new();
    sources.extend(discover_chromium_family());
    sources.extend(discover_firefox());
    sources
}

// ---------------------------------------------------------------------------
// Chromium family
// ---------------------------------------------------------------------------

struct ChromiumBrowser {
    id: &'static str,
    name: &'static str,
    /// Relative path from the platform base dir to the User Data directory.
    user_data_subdirs: &'static [&'static str],
}

const CHROMIUM_BROWSERS: &[ChromiumBrowser] = &[
    ChromiumBrowser {
        id: "chrome",
        name: "Google Chrome",
        user_data_subdirs: &["Google/Chrome/User Data"],
    },
    ChromiumBrowser {
        id: "edge",
        name: "Microsoft Edge",
        user_data_subdirs: &["Microsoft/Edge/User Data"],
    },
    ChromiumBrowser {
        id: "brave",
        name: "Brave",
        user_data_subdirs: &["BraveSoftware/Brave-Browser/User Data"],
    },
    ChromiumBrowser {
        id: "vivaldi",
        name: "Vivaldi",
        user_data_subdirs: &["Vivaldi/User Data"],
    },
    ChromiumBrowser {
        id: "opera",
        name: "Opera",
        user_data_subdirs: &["Opera Software/Opera Stable"],
    },
    ChromiumBrowser {
        id: "chromium",
        name: "Chromium",
        user_data_subdirs: &["Chromium/User Data"],
    },
];

fn chromium_base_dirs() -> Vec<PathBuf> {
    let mut dirs = Vec::new();

    // Windows: %LOCALAPPDATA%
    #[cfg(target_os = "windows")]
    if let Some(local) = dirs::data_local_dir() {
        dirs.push(local);
    }

    // macOS: ~/Library/Application Support
    #[cfg(target_os = "macos")]
    if let Some(app_support) = dirs::config_dir() {
        dirs.push(app_support);
    }

    // Linux: ~/.config
    #[cfg(target_os = "linux")]
    if let Some(cfg) = dirs::config_dir() {
        dirs.push(cfg);
    }

    dirs
}

fn discover_chromium_family() -> Vec<BrowserProfileSource> {
    let mut sources = Vec::new();
    let base_dirs = chromium_base_dirs();

    for browser in CHROMIUM_BROWSERS {
        for base in &base_dirs {
            for subdir in browser.user_data_subdirs {
                let user_data = base.join(subdir);
                if !user_data.is_dir() {
                    continue;
                }
                // Read profile list from Local State JSON if available.
                let profile_names = read_chromium_profile_names(&user_data);

                for (dir_name, display_name) in &profile_names {
                    let profile_path = user_data.join(dir_name);
                    if !profile_path.is_dir() {
                        continue;
                    }
                    let has_bookmarks = profile_path.join("Bookmarks").is_file();
                    let has_history = profile_path.join("History").is_file();
                    let has_passwords = profile_path.join("Login Data").is_file();
                    let has_sessions = profile_path.join("Sessions").is_dir();

                    if !has_bookmarks && !has_history && !has_passwords && !has_sessions {
                        continue;
                    }

                    sources.push(BrowserProfileSource {
                        id: format!("{}-{}", browser.id, dir_name),
                        browser_id: browser.id.to_string(),
                        browser_name: browser.name.to_string(),
                        profile_name: display_name.clone(),
                        profile_path: profile_path.to_string_lossy().into_owned(),
                        has_bookmarks,
                        has_history,
                        has_passwords,
                        has_sessions,
                    });
                }
            }
        }
    }

    sources
}

/// Parse `Local State` JSON to get profile directory name -> display name mapping.
fn read_chromium_profile_names(user_data: &Path) -> Vec<(String, String)> {
    let local_state_path = user_data.join("Local State");
    if let Ok(content) = fs::read_to_string(&local_state_path) {
        if let Ok(json) = serde_json::from_str::<serde_json::Value>(&content) {
            if let Some(profiles) = json
                .get("profile")
                .and_then(|p| p.get("info_cache"))
                .and_then(|c| c.as_object())
            {
                return profiles
                    .iter()
                    .map(|(dir_name, info)| {
                        let display = info
                            .get("name")
                            .and_then(|n| n.as_str())
                            .unwrap_or(dir_name.as_str())
                            .to_string();
                        (dir_name.clone(), display)
                    })
                    .collect();
            }
        }
    }
    // Fall back: look for a Default profile directory.
    if user_data.join("Default").is_dir() {
        return vec![("Default".to_string(), "Default".to_string())];
    }
    Vec::new()
}

// ---------------------------------------------------------------------------
// Firefox
// ---------------------------------------------------------------------------

fn firefox_base_dirs() -> Vec<PathBuf> {
    let mut dirs = Vec::new();

    #[cfg(target_os = "windows")]
    if let Some(roaming) = dirs::data_dir() {
        dirs.push(roaming.join("Mozilla/Firefox"));
    }

    #[cfg(target_os = "macos")]
    if let Some(home) = dirs::home_dir() {
        dirs.push(home.join("Library/Application Support/Firefox"));
    }

    #[cfg(target_os = "linux")]
    if let Some(home) = dirs::home_dir() {
        dirs.push(home.join(".mozilla/firefox"));
    }

    dirs
}

fn discover_firefox() -> Vec<BrowserProfileSource> {
    let mut sources = Vec::new();

    for base in firefox_base_dirs() {
        let profiles_ini = base.join("profiles.ini");
        if !profiles_ini.is_file() {
            continue;
        }

        // Parse the simple INI format.
        let profiles = parse_firefox_profiles_ini(&profiles_ini);

        for (name, path) in profiles {
            let full_path = if Path::new(&path).is_absolute() {
                PathBuf::from(&path)
            } else {
                base.join(&path)
            };

            if !full_path.is_dir() {
                continue;
            }

            let has_bookmarks = full_path.join("places.sqlite").is_file();
            let has_history = full_path.join("places.sqlite").is_file();
            let has_passwords = full_path.join("logins.json").is_file();

            if !has_bookmarks && !has_history && !has_passwords {
                continue;
            }

            sources.push(BrowserProfileSource {
                id: format!("firefox-{}", name.replace(' ', "-")),
                browser_id: "firefox".to_string(),
                browser_name: "Mozilla Firefox".to_string(),
                profile_name: name,
                profile_path: full_path.to_string_lossy().into_owned(),
                has_bookmarks,
                has_history,
                has_passwords,
                has_sessions: false,
            });
        }
    }

    sources
}

/// Parses Firefox `profiles.ini`, returning (Name, Path) pairs.
fn parse_firefox_profiles_ini(path: &Path) -> Vec<(String, String)> {
    let Ok(content) = fs::read_to_string(path) else {
        return Vec::new();
    };

    let mut results = Vec::new();
    let mut current_name: Option<String> = None;
    let mut current_path: Option<String> = None;

    for line in content.lines() {
        let line = line.trim();
        if line.starts_with('[') {
            // Flush previous profile.
            if let (Some(name), Some(path)) = (current_name.take(), current_path.take()) {
                results.push((name, path));
            }
        } else if let Some(rest) = line.strip_prefix("Name=") {
            current_name = Some(rest.to_string());
        } else if let Some(rest) = line.strip_prefix("Path=") {
            current_path = Some(rest.to_string());
        }
    }
    // Flush last.
    if let (Some(name), Some(path)) = (current_name, current_path) {
        results.push((name, path));
    }

    results
}

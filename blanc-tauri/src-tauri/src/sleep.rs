//! Quiet Tabs policy: which tabs may lose their renderer, and what navigation state is kept.
//!
//! Behavioral references in the Electron app (the port's specification):
//!
//! - `src/main/tab-sleep.js` — sleep candidate selection and snapshot trimming
//! - `src/main/renderer-discard.js` — safe renderer release rules
//! - `docs/ELECTRON_PARITY_ROADMAP.md` — Section 9: Tab groups, pinned tabs, closed tabs, and quiet tabs
//!
//! Pure Rust policy functions: clock and collections are injected, never global.

use serde::{Deserialize, Serialize};
use std::collections::HashSet;

/// Maximum retained snapshots before refusing to put more tabs to sleep.
pub const MAX_SLEEP_SNAPSHOTS: usize = 50;
/// Maximum page state byte length in retained snapshots (512 KB).
pub const MAX_PAGE_STATE_BYTES: usize = 512 * 1024;

/// Delay threshold options in milliseconds. None indicates auto-sleep is disabled.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub enum SleepThreshold {
    Off,
    Minutes30,
    Hour1,
    Hours6,
}

impl SleepThreshold {
    pub fn to_millis(&self) -> Option<u64> {
        match self {
            SleepThreshold::Off => None,
            SleepThreshold::Minutes30 => Some(30 * 60 * 1000),
            SleepThreshold::Hour1 => Some(60 * 60 * 1000),
            SleepThreshold::Hours6 => Some(6 * 60 * 60 * 1000),
        }
    }
}

/// A candidate tab evaluated for sleeping.
#[derive(Debug, Clone)]
pub struct SleepCandidate {
    pub id: String,
    pub is_active: bool,
    pub is_visible: bool,
    pub is_asleep: bool,
    pub is_loading: bool,
    pub is_audible: bool,
    pub is_muted: bool,
    pub is_capturing: bool,
    pub is_pinned: bool,
    pub is_private: bool,
    pub has_pending_permissions: bool,
    pub opener_tab_id: Option<String>,
    pub child_popup_count: usize,
    pub last_active_at: u64,
}

/// One navigation history entry retained for a quiet tab.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct TrimmedHistoryEntry {
    pub url: String,
    pub title: String,
    pub page_state: Option<String>,
}

/// A trimmed navigation snapshot retained while a tab is asleep.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct TrimmedSnapshot {
    pub entries: Vec<TrimmedHistoryEntry>,
    pub active_index: usize,
    pub dropped_page_state: bool,
}

/// Evaluates candidates and returns the IDs of tabs eligible to have their renderer released,
/// sorted longest-idle first.
pub fn select_sleep_candidates(
    candidates: &[SleepCandidate],
    now: u64,
    threshold: SleepThreshold,
    current_snapshot_count: usize,
    max_snapshots: usize,
) -> Vec<String> {
    let threshold_ms = match threshold.to_millis() {
        Some(ms) => ms,
        None => return Vec::new(),
    };

    if candidates.is_empty() || current_snapshot_count >= max_snapshots {
        return Vec::new();
    }

    let available_room = max_snapshots - current_snapshot_count;

    // Track active live IDs and opener IDs
    let live_ids: HashSet<&str> = candidates.iter().map(|c| c.id.as_str()).collect();
    let live_opener_ids: HashSet<&str> = candidates
        .iter()
        .filter_map(|c| c.opener_tab_id.as_deref())
        .collect();

    let mut survivors: Vec<(&SleepCandidate, usize)> = Vec::new();

    for (index, tab) in candidates.iter().enumerate() {
        // Exclude active or visible tabs
        if tab.is_active || tab.is_visible {
            continue;
        }

        // Exclude tabs already asleep or busy
        if tab.is_asleep || tab.is_loading {
            continue;
        }

        // Exclude tabs using media, capturing, or pinned
        if tab.is_audible || tab.is_muted || tab.is_capturing || tab.is_pinned {
            continue;
        }

        // Exclude tabs with pending permissions or popups
        if tab.has_pending_permissions || tab.child_popup_count > 0 {
            continue;
        }

        // Keep opener families together while both sides exist
        if let Some(ref opener) = tab.opener_tab_id {
            if live_ids.contains(opener.as_str()) {
                continue;
            }
        }
        if live_opener_ids.contains(tab.id.as_str()) {
            continue;
        }

        // Threshold check
        if now.saturating_sub(tab.last_active_at) < threshold_ms {
            continue;
        }

        survivors.push((tab, index));
    }

    // Sort by longest idle first
    survivors.sort_by(|(a, a_idx), (b, b_idx)| {
        if a.last_active_at != b.last_active_at {
            a.last_active_at.cmp(&b.last_active_at)
        } else {
            a_idx.cmp(b_idx)
        }
    });

    survivors
        .into_iter()
        .take(available_room)
        .map(|(c, _)| c.id.clone())
        .collect()
}

/// Trims and sanitizes navigation history entries for a quiet tab.
///
/// Private tabs NEVER retain pageState on any entry.
/// Back entries never retain pageState (they hold past POST bodies and form values).
pub fn trim_snapshot(
    entries: Vec<(String, String, Option<String>)>,
    active_index: usize,
    is_private: bool,
    max_page_state_bytes: usize,
) -> Option<TrimmedSnapshot> {
    if entries.is_empty() {
        return None;
    }

    let clamped_index = active_index.min(entries.len() - 1);
    let mut dropped_page_state = false;

    let out: Vec<TrimmedHistoryEntry> = entries
        .into_iter()
        .enumerate()
        .map(|(i, (url, title, page_state))| {
            // Only the active entry may keep page state
            if i != clamped_index {
                return TrimmedHistoryEntry {
                    url,
                    title,
                    page_state: None,
                };
            }

            match page_state {
                Some(state) => {
                    if is_private || state.len() > max_page_state_bytes {
                        dropped_page_state = true;
                        TrimmedHistoryEntry {
                            url,
                            title,
                            page_state: None,
                        }
                    } else {
                        TrimmedHistoryEntry {
                            url,
                            title,
                            page_state: Some(state),
                        }
                    }
                }
                None => TrimmedHistoryEntry {
                    url,
                    title,
                    page_state: None,
                },
            }
        })
        .collect();

    Some(TrimmedSnapshot {
        entries: out,
        active_index: clamped_index,
        dropped_page_state,
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn selects_eligible_idle_tabs() {
        let now = 100_000_000;
        let candidates = vec![
            SleepCandidate {
                id: "tab-active".into(),
                is_active: true, // Active -> not eligible
                is_visible: true,
                is_asleep: false,
                is_loading: false,
                is_audible: false,
                is_muted: false,
                is_capturing: false,
                is_pinned: false,
                is_private: false,
                has_pending_permissions: false,
                opener_tab_id: None,
                child_popup_count: 0,
                last_active_at: now - 3_600_000,
            },
            SleepCandidate {
                id: "tab-pinned".into(),
                is_active: false,
                is_visible: false,
                is_asleep: false,
                is_loading: false,
                is_audible: false,
                is_muted: false,
                is_capturing: false,
                is_pinned: true, // Pinned -> not eligible
                is_private: false,
                has_pending_permissions: false,
                opener_tab_id: None,
                child_popup_count: 0,
                last_active_at: now - 3_600_000,
            },
            SleepCandidate {
                id: "tab-idle-1".into(),
                is_active: false,
                is_visible: false,
                is_asleep: false,
                is_loading: false,
                is_audible: false,
                is_muted: false,
                is_capturing: false,
                is_pinned: false,
                is_private: false,
                has_pending_permissions: false,
                opener_tab_id: None,
                child_popup_count: 0,
                last_active_at: now - 4_000_000, // Eligible
            },
            SleepCandidate {
                id: "tab-idle-2".into(),
                is_active: false,
                is_visible: false,
                is_asleep: false,
                is_loading: false,
                is_audible: false,
                is_muted: false,
                is_capturing: false,
                is_pinned: false,
                is_private: false,
                has_pending_permissions: false,
                opener_tab_id: None,
                child_popup_count: 0,
                last_active_at: now - 5_000_000, // Eligible, longer idle
            },
        ];

        let selected = select_sleep_candidates(
            &candidates,
            now,
            SleepThreshold::Minutes30,
            0,
            MAX_SLEEP_SNAPSHOTS,
        );

        assert_eq!(selected, vec!["tab-idle-2", "tab-idle-1"]);
    }

    #[test]
    fn private_snapshots_never_keep_page_state() {
        let entries = vec![
            ("https://a.com".into(), "A".into(), Some("form_token_123".into())),
            ("https://b.com".into(), "B".into(), Some("password_data".into())),
        ];

        let snapshot = trim_snapshot(entries, 1, true, MAX_PAGE_STATE_BYTES).unwrap();
        assert_eq!(snapshot.entries[0].page_state, None);
        assert_eq!(snapshot.entries[1].page_state, None);
        assert!(snapshot.dropped_page_state);
    }

    #[test]
    fn non_private_active_entry_keeps_page_state_under_ceiling() {
        let entries = vec![
            ("https://a.com".into(), "A".into(), Some("old_post_body".into())),
            ("https://b.com".into(), "B".into(), Some("scroll_pos_400".into())),
        ];

        let snapshot = trim_snapshot(entries, 1, false, MAX_PAGE_STATE_BYTES).unwrap();
        assert_eq!(snapshot.entries[0].page_state, None, "back entry drops page state");
        assert_eq!(snapshot.entries[1].page_state, Some("scroll_pos_400".into()));
        assert!(!snapshot.dropped_page_state);
    }
}

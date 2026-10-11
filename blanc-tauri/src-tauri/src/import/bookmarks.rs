//! Parses Chromium-family JSON `Bookmarks` files.
//!
//! Limits: 20 MiB / 100,000 nodes (matching Electron browser-data-import.js).

use std::path::Path;
use std::time::SystemTime;

use crate::storage::Bookmark;

const MAX_BYTES: u64 = 20 * 1024 * 1024;
const MAX_NODES: usize = 100_000;
/// Chromium timestamp epoch offset in milliseconds (microseconds since 1601-01-01).
const CHROMIUM_EPOCH_OFFSET_MS: u64 = 11_644_473_600_000;

/// Parse a Chromium `Bookmarks` file at `path`.
/// Returns a flat list of `Bookmark` structs preserving folder names.
pub fn parse_chromium_bookmarks(path: &Path) -> Result<Vec<Bookmark>, String> {
    // Size guard.
    let meta = std::fs::metadata(path).map_err(|e| e.to_string())?;
    if meta.len() > MAX_BYTES {
        return Err(format!("Bookmarks file exceeds 20 MiB limit ({} bytes)", meta.len()));
    }

    let content = std::fs::read_to_string(path).map_err(|e| e.to_string())?;
    let json: serde_json::Value = serde_json::from_str(&content).map_err(|e| e.to_string())?;

    let roots = json
        .get("roots")
        .ok_or("Missing 'roots' key in Bookmarks JSON")?;

    let mut out = Vec::new();
    let mut count = 0usize;

    for root_name in &["bookmark_bar", "other", "synced"] {
        if let Some(node) = roots.get(root_name) {
            walk_chromium_node(node, None, &mut out, &mut count);
        }
    }

    Ok(out)
}

fn walk_chromium_node(
    node: &serde_json::Value,
    parent_folder: Option<&str>,
    out: &mut Vec<Bookmark>,
    count: &mut usize,
) {
    if *count >= MAX_NODES {
        return;
    }

    let node_type = node.get("type").and_then(|t| t.as_str()).unwrap_or("");
    let name = node
        .get("name")
        .and_then(|n| n.as_str())
        .unwrap_or("Untitled")
        .to_string();

    match node_type {
        "url" => {
            if let Some(url) = node.get("url").and_then(|u| u.as_str()) {
                if !url.starts_with("http://") && !url.starts_with("https://") {
                    // Skip javascript:, chrome-extension:, etc.
                    return;
                }
                let date_added = node
                    .get("date_added")
                    .and_then(|d| d.as_str())
                    .and_then(|s| s.parse::<u64>().ok())
                    .map(chromium_timestamp_to_unix_ms)
                    .unwrap_or_else(|| {
                        SystemTime::now()
                            .duration_since(std::time::UNIX_EPOCH)
                            .unwrap_or_default()
                            .as_millis() as u64
                    });

                out.push(Bookmark {
                    id: format!(
                        "import-{}",
                        node.get("id")
                            .and_then(|i| i.as_str())
                            .unwrap_or("0")
                    ),
                    url: url.to_string(),
                    title: name,
                    favicon: None,
                    created_at: date_added,
                    folder: parent_folder.map(|f| f.to_string()),
                    tags: Vec::new(),
                });
                *count += 1;
            }
        }
        "folder" => {
            if let Some(children) = node.get("children").and_then(|c| c.as_array()) {
                for child in children {
                    walk_chromium_node(child, Some(&name), out, count);
                }
            }
        }
        _ => {}
    }
}

/// Converts Chromium microseconds-since-1601 to Unix milliseconds.
fn chromium_timestamp_to_unix_ms(chromium_micros: u64) -> u64 {
    // Chromium stores microseconds since 1601-01-01; convert to ms since Unix epoch.
    let millis = chromium_micros / 1000;
    millis.saturating_sub(CHROMIUM_EPOCH_OFFSET_MS)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_chromium_timestamp_conversion() {
        // 2020-01-01 00:00:00 UTC in Chromium micros (since 1601) = 13222310400000000
        let ts = chromium_timestamp_to_unix_ms(13_222_310_400_000_000);
        // Should be 2020-01-01 Unix ms = 1577836800000
        assert_eq!(ts, 1_577_836_800_000);
    }
}

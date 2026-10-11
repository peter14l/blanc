//! Reads browsing history from Chromium and Firefox SQLite databases.
//!
//! Both databases may be locked by the source browser if it is running.
//! We copy the database (+ WAL / SHM) to a tempdir and open in read-only mode.

use std::path::Path;

use rusqlite::{Connection, OpenFlags};

use crate::storage::HistoryEntry;

/// Chromium stores visit timestamps as microseconds since 1601-01-01.
const CHROMIUM_EPOCH_OFFSET_US: u64 = 11_644_473_600_000_000;

const MAX_HISTORY: usize = 5_000;

// ---------------------------------------------------------------------------
// Chromium History
// ---------------------------------------------------------------------------

/// Import up to `MAX_HISTORY` entries from a Chromium `History` SQLite file.
pub fn read_chromium_history(history_path: &Path) -> Result<Vec<HistoryEntry>, String> {
    let conn = open_copy_readonly(history_path)?;

    let mut stmt = conn
        .prepare(
            "SELECT url, title, visit_count, last_visit_time \
             FROM urls \
             WHERE url LIKE 'http%' \
             ORDER BY last_visit_time DESC \
             LIMIT ?1",
        )
        .map_err(|e| e.to_string())?;

    let rows = stmt
        .query_map([MAX_HISTORY as i64], |row| {
            let url: String = row.get(0)?;
            let title: String = row.get::<_, Option<String>>(1)?.unwrap_or_default();
            let visit_count: u32 = row.get::<_, i64>(2)? as u32;
            let last_visit_time: i64 = row.get(3)?;

            let visited_at = chromium_time_to_unix_secs(last_visit_time as u64);

            Ok(HistoryEntry {
                id: format!("ch-import-{}", url.len()), // temp; dedup key is URL
                url,
                title,
                visited_at,
                visit_count,
            })
        })
        .map_err(|e| e.to_string())?;

    let mut entries = Vec::new();
    for row in rows {
        match row {
            Ok(e) => entries.push(e),
            Err(_) => continue,
        }
    }

    // Assign stable import IDs.
    for (i, e) in entries.iter_mut().enumerate() {
        e.id = format!("ch-hist-{}", i);
    }

    Ok(entries)
}

fn chromium_time_to_unix_secs(chromium_micros: u64) -> u64 {
    // Convert Chromium microseconds (since 1601-01-01) to Unix seconds.
    chromium_micros
        .saturating_sub(CHROMIUM_EPOCH_OFFSET_US)
        .wrapping_div(1_000_000)
}

// ---------------------------------------------------------------------------
// Firefox History (places.sqlite)
// ---------------------------------------------------------------------------

/// Import up to `MAX_HISTORY` entries from a Firefox `places.sqlite` file.
pub fn read_firefox_history(places_path: &Path) -> Result<Vec<HistoryEntry>, String> {
    let conn = open_copy_readonly(places_path)?;

    let mut stmt = conn
        .prepare(
            "SELECT p.url, p.title, p.visit_count, \
                    MAX(h.visit_date) as last_visit \
             FROM moz_places p \
             JOIN moz_historyvisits h ON h.place_id = p.id \
             WHERE p.url LIKE 'http%' \
             GROUP BY p.id \
             ORDER BY last_visit DESC \
             LIMIT ?1",
        )
        .map_err(|e| e.to_string())?;

    let rows = stmt
        .query_map([MAX_HISTORY as i64], |row| {
            let url: String = row.get(0)?;
            let title: String = row.get::<_, Option<String>>(1)?.unwrap_or_default();
            let visit_count: u32 = row.get::<_, i64>(2)? as u32;
            let last_visit_micros: i64 = row.get(3)?;

            // Firefox stores microseconds since Unix epoch.
            let visited_at = (last_visit_micros as u64).wrapping_div(1_000_000);

            Ok(HistoryEntry {
                id: String::new(),
                url,
                title,
                visited_at,
                visit_count,
            })
        })
        .map_err(|e| e.to_string())?;

    let mut entries = Vec::new();
    for row in rows {
        if let Ok(e) = row {
            entries.push(e);
        }
    }

    for (i, e) in entries.iter_mut().enumerate() {
        e.id = format!("ff-hist-{}", i);
    }

    Ok(entries)
}

// ---------------------------------------------------------------------------
// Shared helpers
// ---------------------------------------------------------------------------

/// Copy a SQLite database (+ -wal, -shm) to a tempdir and open read-only.
/// This avoids EBUSY when the source browser is still running.
fn open_copy_readonly(db_path: &Path) -> Result<Connection, String> {
    let tmp = tempfile::Builder::new()
        .prefix("blanc-import-")
        .tempdir()
        .map_err(|e| format!("tempdir: {}", e))?;

    let db_name = db_path
        .file_name()
        .ok_or("Invalid db path")?;
    let dest = tmp.path().join(db_name);

    std::fs::copy(db_path, &dest)
        .map_err(|e| format!("copy db: {}", e))?;

    // Copy WAL and SHM if present (needed for a consistent read).
    for suffix in &["-wal", "-shm"] {
        let wal = db_path.with_extension(
            format!(
                "{}{}",
                db_path.extension().and_then(|e| e.to_str()).unwrap_or(""),
                suffix
            )
        );
        if wal.is_file() {
            let dest_wal = tmp.path().join(wal.file_name().unwrap());
            let _ = std::fs::copy(&wal, &dest_wal);
        }
    }

    // Move tempdir ownership into a "forget" so the files stay alive during the connection.
    let dest_path = dest.clone();
    std::mem::forget(tmp);

    Connection::open_with_flags(
        &dest_path,
        OpenFlags::SQLITE_OPEN_READ_ONLY | OpenFlags::SQLITE_OPEN_NO_MUTEX,
    )
    .map_err(|e| format!("sqlite open: {}", e))
}

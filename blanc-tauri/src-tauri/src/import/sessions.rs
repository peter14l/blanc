//! Parses Chromium-family `Sessions/Session_*` SNSS command logs (F40 open tabs).
//!
//! Chromium documents this format in `components/sessions/core`:
//! - `command_storage_backend.cc` / `session_command.cc` (SNSS framing)
//! - `session_service_commands.cc` (command IDs and payloads)
//! - `serialized_navigation_entry.cc` (navigation pickle order)
//!
//! This is a faithful port of `src/main/chromium-session.js`. Structurally the
//! same bounds and fail-closed rules apply: 64 MiB file cap, 500 candidate cap,
//! pickle string caps, URL/title length limits, and vendor-fork leniency for
//! optional metadata commands.

use std::collections::{HashMap, HashSet};
use std::path::Path;

const MAX_SESSION_BYTES: usize = 64 * 1024 * 1024;
const MAX_SESSION_CANDIDATES: usize = 500;
const MAX_PICKLE_STRING_BYTES: usize = 2 * 1024 * 1024;
const MAX_URL_CHARS: usize = 16_384;
const MAX_TITLE_CODE_POINTS: usize = 240;

const FILE_VERSION_CLEARTEXT: i32 = 3;
const FILE_VERSION_ENCRYPTED: i32 = 5;
const INITIAL_STATE_MARKER: u8 = 255;

/// Chromium `session_service_commands.cc` command IDs.
mod cmd {
    pub const SET_TAB_WINDOW: u8 = 0;
    pub const SET_TAB_INDEX: u8 = 2;
    pub const PRUNE_BACK: u8 = 5;
    pub const UPDATE_NAVIGATION: u8 = 6;
    pub const SET_SELECTED_NAVIGATION: u8 = 7;
    pub const SET_SELECTED_TAB: u8 = 8;
    pub const SET_WINDOW_TYPE: u8 = 9;
    pub const PRUNE_FRONT: u8 = 11;
    pub const SET_PINNED: u8 = 12;
    pub const TAB_CLOSED: u8 = 16;
    pub const WINDOW_CLOSED: u8 = 17;
    pub const SET_ACTIVE_WINDOW: u8 = 20;
    pub const SET_LAST_ACTIVE_TIME: u8 = 21;
    pub const PRUNE_RANGE: u8 = 24;
    pub const SET_TAB_GROUP: u8 = 25;
    pub const SET_TAB_GROUP_METADATA: u8 = 27;
}

/// One imported open tab.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct SessionCandidate {
    pub url: String,
    pub title: String,
    /// 1-based source window index (active window first).
    pub source_window: usize,
    pub pinned: bool,
    pub source_group_name: Option<String>,
}

/// Result of reconstructing a session command log.
#[derive(Debug)]
pub struct SessionData {
    pub candidates: Vec<SessionCandidate>,
    pub window_count: usize,
    pub excluded_count: usize,
    pub partial_tail: bool,
}

/// Read the newest `Session_*` file in `<profile>/Sessions` and return the open-tab URLs.
pub fn read_open_tabs(profile_path: &Path) -> Result<Vec<String>, String> {
    let sessions_dir = profile_path.join("Sessions");
    let mut entries: Vec<(u64, std::path::PathBuf)> = Vec::new();
    for entry in std::fs::read_dir(&sessions_dir).map_err(|e| e.to_string())? {
        let entry = entry.map_err(|e| e.to_string())?;
        if !entry.file_type().map(|t| t.is_file()).unwrap_or(false) {
            continue;
        }
        let name = entry.file_name().to_string_lossy().into_owned();
        if let Some(stamp) = name
            .strip_prefix("Session_")
            .and_then(|n| n.parse::<u64>().ok())
        {
            entries.push((stamp, entry.path()));
        }
    }
    entries.sort_by_key(|(stamp, _)| std::cmp::Reverse(*stamp));
    let (_stamp, newest) = entries
        .first()
        .ok_or_else(|| "empty".to_string())?;

    let bytes = std::fs::read(newest).map_err(|e| e.to_string())?;
    let data = parse_chromium_session(&bytes, MAX_SESSION_CANDIDATES)?;
    if data.candidates.is_empty() {
        return Err("empty".to_string());
    }
    Ok(data.candidates.into_iter().map(|candidate| candidate.url).collect())
}

/// Parse a full SNSS session file into reconstructed open tabs.
pub fn parse_chromium_session(
    input: &[u8],
    max_candidates: usize,
) -> Result<SessionData, String> {
    let (commands, partial_tail) = parse_command_frames(input, MAX_SESSION_BYTES)?;
    let mut data = reconstruct_session(&commands, max_candidates)?;
    data.partial_tail = partial_tail;
    Ok(data)
}

/// One SNSS command frame (id + raw payload), marker frames excluded.
#[derive(Debug)]
struct Command {
    id: u8,
    payload: Vec<u8>,
}

/// Walk the SNSS framing: `SNSS` magic, cleartext version, length-prefixed frames.
/// Errors mirror the Electron parser. A tail shorter than one frame after the
/// initial-state marker is tolerated (`partial_tail`), not an error.
fn parse_command_frames(
    input: &[u8],
    max_bytes: usize,
) -> Result<(Vec<Command>, bool), String> {
    if input.len() > max_bytes {
        return Err("session-too-large".to_string());
    }
    if input.len() < 8 || &input[..4] != b"SNSS" {
        return Err("invalid-session-header".to_string());
    }
    let version = i32::from_le_bytes(input[4..8].try_into().map_err(|_| "invalid-session-header")?);
    if version == FILE_VERSION_ENCRYPTED {
        return Err("encrypted-session".to_string());
    }
    if version != FILE_VERSION_CLEARTEXT {
        return Err("unsupported-session-version".to_string());
    }

    let mut commands = Vec::new();
    let mut offset = 8usize;
    let mut marker_seen = false;
    let mut partial_tail = false;
    while offset < input.len() {
        if input.len() - offset < 2 {
            if !marker_seen {
                return Err("incomplete-session".to_string());
            }
            partial_tail = true;
            break;
        }
        let framed_size = u16::from_le_bytes(input[offset..offset + 2].try_into().unwrap()) as usize;
        if framed_size < 1 {
            return Err("invalid-session-command".to_string());
        }
        let end = offset + 2 + framed_size;
        if end > input.len() {
            if !marker_seen {
                return Err("incomplete-session".to_string());
            }
            partial_tail = true;
            break;
        }
        let id = input[offset + 2];
        let payload = &input[offset + 3..end];
        if id == INITIAL_STATE_MARKER {
            marker_seen = true;
        } else {
            commands.push(Command {
                id,
                payload: payload.to_vec(),
            });
        }
        offset = end;
    }
    if !marker_seen {
        return Err("missing-session-marker".to_string());
    }
    Ok((commands, partial_tail))
}

// ---------------------------------------------------------------------------
// Pickle reader (Chromium base::Pickle — payloads are 4-byte aligned)
// ---------------------------------------------------------------------------

fn align4(value: usize) -> usize {
    (value + 3) & !3
}

/// Reads aligned fields from a pickled payload, mirroring `PickleCursor` in
/// chromium-session.js.
struct PickleCursor<'a> {
    buffer: &'a [u8],
    offset: usize,
    valid: bool,
}

impl<'a> PickleCursor<'a> {
    fn new(buffer: &'a [u8], with_header: bool) -> Self {
        if with_header {
            if buffer.len() < 4
                || u32::from_le_bytes(buffer[..4].try_into().unwrap()) as usize != buffer.len() - 4
            {
                return Self {
                    buffer,
                    offset: 0,
                    valid: false,
                };
            }
            Self {
                buffer,
                offset: 4,
                valid: true,
            }
        } else {
            Self {
                buffer,
                offset: 0,
                valid: true,
            }
        }
    }

    fn take(&mut self, byte_length: usize) -> Option<&'a [u8]> {
        if !self.valid {
            return None;
        }
        let end = self.offset.checked_add(byte_length)?;
        if end > self.buffer.len() {
            return None;
        }
        let value = &self.buffer[self.offset..end];
        self.offset = align4(end);
        if self.offset > self.buffer.len() {
            return None;
        }
        Some(value)
    }

    fn int32(&mut self) -> Option<i32> {
        self.take(4).map(|b| i32::from_le_bytes(b.try_into().unwrap()))
    }

    fn uint64(&mut self) -> Option<u64> {
        self.take(8).map(|b| u64::from_le_bytes(b.try_into().unwrap()))
    }

    /// A pickled length field must be non-negative.
    fn length(&mut self) -> Option<usize> {
        self.int32().and_then(|value| usize::try_from(value).ok())
    }

    fn string(&mut self, max_bytes: usize) -> Option<String> {
        let byte_length = self.length()?;
        if byte_length > max_bytes {
            return None;
        }
        self.take(byte_length)
            .map(|b| String::from_utf8_lossy(b).into_owned())
    }

    fn string16(&mut self, max_bytes: usize) -> Option<String> {
        let code_units = self.length()?;
        let byte_length = code_units.checked_mul(2)?;
        if byte_length > max_bytes {
            return None;
        }
        self.take(byte_length)
            .map(|b| decode_utf16le(b))
    }
}

/// Buffer.toString('utf16le') equivalent — lone surrogates become U+FFFD.
fn decode_utf16le(bytes: &[u8]) -> String {
    let units: Vec<u16> = bytes
        .chunks_exact(2)
        .map(|pair| u16::from_le_bytes([pair[0], pair[1]]))
        .collect();
    String::from_utf16_lossy(&units)
}

fn bounded_text(value: &str, max_codepoints: usize) -> String {
    value
        .chars()
        .take(max_codepoints)
        .collect::<String>()
        .trim()
        .to_string()
}

fn token_key(high: u64, low: u64) -> Option<String> {
    if high == 0 && low == 0 {
        return None;
    }
    Some(format!("{:016x}{:016x}", high, low))
}

fn read_int32_at(payload: &[u8], offset: usize) -> Option<i32> {
    let end = offset.checked_add(4)?;
    if end > payload.len() {
        return None;
    }
    Some(i32::from_le_bytes(payload[offset..end].try_into().unwrap()))
}

fn read_u64_at(payload: &[u8], offset: usize) -> Option<u64> {
    let end = offset.checked_add(8)?;
    if end > payload.len() {
        return None;
    }
    Some(u64::from_le_bytes(payload[offset..end].try_into().unwrap()))
}

// ---------------------------------------------------------------------------
// Navigation / group metadata pickles
// ---------------------------------------------------------------------------

#[derive(Debug, Clone)]
struct Navigation {
    tab_id: i32,
    index: i32,
    url: String,
    title: String,
}

fn parse_navigation(payload: &[u8]) -> Option<Navigation> {
    let mut cursor = PickleCursor::new(payload, true);
    let tab_id = cursor.int32()?;
    let index = cursor.int32()?;
    let url = cursor.string(MAX_URL_CHARS * 4)?;
    let title = cursor.string16(MAX_PICKLE_STRING_BYTES)?;
    let page_state_length = cursor.length()?;
    if page_state_length > MAX_PICKLE_STRING_BYTES {
        return None;
    }
    cursor.take(page_state_length)?;
    cursor.int32()?;
    Some(Navigation {
        tab_id,
        index,
        url: url.chars().take(MAX_URL_CHARS).collect(),
        title: bounded_text(&title, MAX_TITLE_CODE_POINTS),
    })
}

fn parse_group_metadata(payload: &[u8]) -> Option<(String, String)> {
    let mut cursor = PickleCursor::new(payload, true);
    let high = cursor.uint64()?;
    let low = cursor.uint64()?;
    let title = cursor.string16(8_192)?;
    Some((token_key(high, low)?, bounded_text(&title, 40)))
}

fn normal_http_url(value: &str) -> Option<String> {
    let parsed = url::Url::parse(value).ok()?;
    match parsed.scheme() {
        "http" | "https" => Some(parsed.to_string()),
        _ => None,
    }
}

// ---------------------------------------------------------------------------
// Command-log reconstruction
// ---------------------------------------------------------------------------

#[derive(Debug)]
struct TabState {
    window_id: Option<i32>,
    tab_visual_index: i32,
    current_navigation_index: i32,
    navigations: HashMap<i32, Navigation>,
    pinned: bool,
    group_token: Option<String>,
    first_seen: u64,
}

#[derive(Debug)]
struct WindowState {
    id: i32,
    kind: Option<i32>,
    first_seen: u64,
}

fn tab_for<'a>(
    tabs: &'a mut HashMap<i32, TabState>,
    sequence: &mut u64,
    id: i32,
) -> &'a mut TabState {
    tabs.entry(id).or_insert_with(|| {
        let first_seen = *sequence;
        *sequence += 1;
        TabState {
            window_id: None,
            tab_visual_index: i32::MAX,
            current_navigation_index: -1,
            navigations: HashMap::new(),
            pinned: false,
            group_token: None,
            first_seen,
        }
    })
}

fn window_for<'a>(
    windows: &'a mut HashMap<i32, WindowState>,
    sequence: &mut u64,
    id: i32,
) -> &'a mut WindowState {
    windows.entry(id).or_insert_with(|| {
        let first_seen = *sequence;
        *sequence += 1;
        WindowState {
            id,
            kind: None,
            first_seen,
        }
    })
}

/// Removes navigation indices in `[index, index + count)` and renumbers the rest.
fn prune_range(tab: &mut TabState, index: i32, count: i32) -> bool {
    if index < 0 || count <= 0 {
        return false;
    }
    let hi = i64::from(index) + i64::from(count);
    let current = i64::from(tab.current_navigation_index);
    if current >= i64::from(index) && current < hi {
        tab.current_navigation_index = index.saturating_sub(1);
    } else if current >= hi {
        tab.current_navigation_index = tab.current_navigation_index.saturating_sub(count);
    }
    let mut next = HashMap::new();
    for (nav_index, navigation) in &tab.navigations {
        if i64::from(*nav_index) >= i64::from(index) && i64::from(*nav_index) < hi {
            continue;
        }
        let adjusted = if *nav_index >= index {
            (*nav_index).saturating_sub(count)
        } else {
            *nav_index
        };
        next.insert(adjusted, navigation.clone());
    }
    tab.navigations = next;
    true
}

fn reconstruct_session(commands: &[Command], max_candidates: usize) -> Result<SessionData, String> {
    let mut tabs: HashMap<i32, TabState> = HashMap::new();
    let mut windows: HashMap<i32, WindowState> = HashMap::new();
    let mut discarded_window_ids: HashSet<i32> = HashSet::new();
    let mut group_names: HashMap<String, String> = HashMap::new();
    let mut active_window_id: Option<i32> = None;
    let mut sequence = 0u64;

    for command in commands {
        let payload = &command.payload;
        match command.id {
            cmd::SET_TAB_WINDOW => {
                let (Some(window_id), Some(tab_id)) =
                    (read_int32_at(payload, 0), read_int32_at(payload, 4))
                else {
                    return Err("invalid-session-command".to_string());
                };
                window_for(&mut windows, &mut sequence, window_id);
                tab_for(&mut tabs, &mut sequence, tab_id).window_id = Some(window_id);
            }
            cmd::SET_TAB_INDEX => {
                let (Some(tab_id), Some(index)) =
                    (read_int32_at(payload, 0), read_int32_at(payload, 4))
                else {
                    return Err("invalid-session-command".to_string());
                };
                tab_for(&mut tabs, &mut sequence, tab_id).tab_visual_index = index;
            }
            cmd::UPDATE_NAVIGATION => {
                let Some(navigation) = parse_navigation(payload) else {
                    return Err("invalid-session-command".to_string());
                };
                tab_for(&mut tabs, &mut sequence, navigation.tab_id)
                    .navigations
                    .insert(navigation.index, navigation);
            }
            cmd::SET_SELECTED_NAVIGATION => {
                let (Some(tab_id), Some(index)) =
                    (read_int32_at(payload, 0), read_int32_at(payload, 4))
                else {
                    return Err("invalid-session-command".to_string());
                };
                tab_for(&mut tabs, &mut sequence, tab_id).current_navigation_index = index;
            }
            cmd::SET_SELECTED_TAB | cmd::SET_WINDOW_TYPE => {
                // SET_SELECTED_TAB's selected index and SET_WINDOW_TYPE's kind
                // must validate, but only the latter feeds candidate ordering.
                let (Some(window_id), Some(value)) =
                    (read_int32_at(payload, 0), read_int32_at(payload, 4))
                else {
                    return Err("invalid-session-command".to_string());
                };
                if command.id == cmd::SET_WINDOW_TYPE {
                    window_for(&mut windows, &mut sequence, window_id).kind = Some(value);
                }
            }
            cmd::PRUNE_BACK => {
                let (Some(tab_id), Some(index)) =
                    (read_int32_at(payload, 0), read_int32_at(payload, 4))
                else {
                    return Err("invalid-session-command".to_string());
                };
                tab_for(&mut tabs, &mut sequence, tab_id)
                    .navigations
                    .retain(|nav_index, _| *nav_index < index);
            }
            cmd::PRUNE_FRONT => {
                let (Some(tab_id), Some(count)) =
                    (read_int32_at(payload, 0), read_int32_at(payload, 4))
                else {
                    return Err("invalid-session-command".to_string());
                };
                if count <= 0 || !prune_range(tab_for(&mut tabs, &mut sequence, tab_id), 0, count) {
                    return Err("invalid-session-command".to_string());
                }
            }
            cmd::PRUNE_RANGE => {
                let (Some(tab_id), Some(index), Some(count)) = (
                    read_int32_at(payload, 0),
                    read_int32_at(payload, 4),
                    read_int32_at(payload, 8),
                ) else {
                    return Err("invalid-session-command".to_string());
                };
                if !prune_range(tab_for(&mut tabs, &mut sequence, tab_id), index, count) {
                    return Err("invalid-session-command".to_string());
                }
            }
            cmd::SET_PINNED => {
                let Some(tab_id) = read_int32_at(payload, 0) else {
                    return Err("invalid-session-command".to_string());
                };
                if payload.len() < 5 {
                    return Err("invalid-session-command".to_string());
                }
                tab_for(&mut tabs, &mut sequence, tab_id).pinned = payload[4] != 0;
            }
            cmd::TAB_CLOSED => {
                let Some(tab_id) = read_int32_at(payload, 0) else {
                    return Err("invalid-session-command".to_string());
                };
                tabs.remove(&tab_id);
            }
            cmd::WINDOW_CLOSED => {
                let Some(window_id) = read_int32_at(payload, 0) else {
                    return Err("invalid-session-command".to_string());
                };
                windows.remove(&window_id);
                discarded_window_ids.insert(window_id);
            }
            cmd::SET_ACTIVE_WINDOW => {
                let Some(window_id) = read_int32_at(payload, 0) else {
                    return Err("invalid-session-command".to_string());
                };
                active_window_id = Some(window_id);
            }
            cmd::SET_LAST_ACTIVE_TIME => {
                // Vendor forks can reuse upstream command IDs for private
                // payloads; optional metadata must never reject valid tabs.
                if payload.len() != 16 {
                    continue;
                }
            }
            cmd::SET_TAB_GROUP => {
                // Upstream's token payload is 32 bytes on supported 64-bit
                // builds. Vivaldi has shipped a shorter vendor payload here;
                // ignore optional metadata rather than rejecting the open tabs.
                if payload.len() < 25 {
                    continue;
                }
                let (Some(tab_id), Some(high), Some(low)) = (
                    read_int32_at(payload, 0),
                    read_u64_at(payload, 8),
                    read_u64_at(payload, 16),
                ) else {
                    return Err("invalid-session-command".to_string());
                };
                let token = if payload[24] != 0 {
                    token_key(high, low)
                } else {
                    None
                };
                tab_for(&mut tabs, &mut sequence, tab_id).group_token = token;
            }
            cmd::SET_TAB_GROUP_METADATA => {
                if let Some((key, title)) = parse_group_metadata(payload) {
                    group_names.insert(key, title);
                }
            }
            _ => {}
        }
    }

    let mut ordered_windows: Vec<&WindowState> = windows
        .values()
        .filter(|window| {
            !discarded_window_ids.contains(&window.id) && window.kind == Some(0)
        })
        .collect();
    ordered_windows.sort_by(|a, b| {
        let a_active = Some(a.id) == active_window_id;
        let b_active = Some(b.id) == active_window_id;
        if a_active != b_active {
            return if a_active {
                std::cmp::Ordering::Less
            } else {
                std::cmp::Ordering::Greater
            };
        }
        a.first_seen.cmp(&b.first_seen).then(a.id.cmp(&b.id))
    });

    let mut candidates = Vec::new();
    let mut excluded_count = 0usize;
    for (source_window_index, window) in ordered_windows.iter().enumerate() {
        let mut window_tabs: Vec<&TabState> = tabs
            .values()
            .filter(|tab| tab.window_id == Some(window.id))
            .collect();
        window_tabs.sort_by(|a, b| {
            a.tab_visual_index
                .cmp(&b.tab_visual_index)
                .then(a.first_seen.cmp(&b.first_seen))
        });
        for tab in window_tabs {
            let current = tab.navigations.get(&tab.current_navigation_index);
            let navigation = current.or_else(|| {
                tab.navigations
                    .iter()
                    .max_by_key(|(index, _)| *index)
                    .map(|(_, navigation)| navigation)
            });
            let Some(navigation) = navigation else {
                excluded_count += 1;
                continue;
            };
            let Some(url) = normal_http_url(&navigation.url) else {
                excluded_count += 1;
                continue;
            };
            let title = if navigation.title.is_empty() {
                url.clone()
            } else {
                navigation.title.clone()
            };
            let source_group_name = tab
                .group_token
                .as_ref()
                .and_then(|token| group_names.get(token))
                .map(|group| bounded_text(group, 40))
                .filter(|group| !group.is_empty());

            candidates.push(SessionCandidate {
                url,
                title,
                source_window: source_window_index + 1,
                pinned: tab.pinned,
                source_group_name,
            });
            if candidates.len() > max_candidates {
                return Err("too-many-candidates".to_string());
            }
        }
    }

    if candidates.is_empty() {
        return Err("empty".to_string());
    }
    Ok(SessionData {
        candidates,
        window_count: ordered_windows.len(),
        excluded_count,
        partial_tail: false,
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    fn ints(values: &[i32]) -> Vec<u8> {
        let mut out = Vec::with_capacity(values.len() * 4);
        for value in values {
            out.extend_from_slice(&value.to_le_bytes());
        }
        out
    }

    struct PickleWriter {
        buf: Vec<u8>,
    }

    impl PickleWriter {
        fn new() -> Self {
            Self { buf: Vec::new() }
        }
        fn append(&mut self, bytes: &[u8]) -> &mut Self {
            let start = self.buf.len();
            let end = align4(start + bytes.len());
            self.buf.resize(end, 0);
            self.buf[start..start + bytes.len()].copy_from_slice(bytes);
            self
        }
        fn int32(mut self, value: i32) -> Self {
            self.append(&value.to_le_bytes());
            self
        }
        fn uint32(mut self, value: u32) -> Self {
            self.append(&value.to_le_bytes());
            self
        }
        fn uint64(mut self, value: u64) -> Self {
            self.append(&value.to_le_bytes());
            self
        }
        // rusfmt: ignore
        fn bool(mut self, value: bool) -> Self {
            self.append(&[value as u8]);
            self
        }
        fn string(mut self, value: &str) -> Self {
            self.append(&(value.len() as u32).to_le_bytes());
            self.append(value.as_bytes());
            self
        }
        fn string16(mut self, value: &str) -> Self {
            let bytes: Vec<u8> = value
                .encode_utf16()
                .flat_map(|unit| unit.to_le_bytes())
                .collect();
            self.append(&((bytes.len() / 2) as u32).to_le_bytes());
            self.append(&bytes);
            self
        }
        fn done(self) -> Vec<u8> {
            let mut out = Vec::with_capacity(4 + self.buf.len());
            out.extend_from_slice(&(self.buf.len() as u32).to_le_bytes());
            out.extend_from_slice(&self.buf);
            out
        }
    }

    fn navigation_pickle(tab_id: i32, index: i32, url: &str, title: &str) -> Vec<u8> {
        PickleWriter::new()
            .int32(tab_id)
            .int32(index)
            .string(url)
            .string16(title)
            .string("")
            .int32(1)
            .done()
    }

    fn pinned_payload(tab_id: i32, pinned: bool) -> Vec<u8> {
        let mut out = ints(&[tab_id]);
        out.resize(8, 0);
        out[4] = pinned as u8;
        out
    }

    fn group_payload(tab_id: i32, high: u64, low: u64, has_group: bool) -> Vec<u8> {
        let mut out = Vec::with_capacity(32);
        out.extend_from_slice(&tab_id.to_le_bytes());
        out.resize(8, 0);
        out.extend_from_slice(&high.to_le_bytes());
        out.extend_from_slice(&low.to_le_bytes());
        out.resize(32, 0);
        out[24] = has_group as u8;
        out
    }

    fn group_metadata_payload(high: u64, low: u64, title: &str) -> Vec<u8> {
        PickleWriter::new()
            .uint64(high)
            .uint64(low)
            .string16(title)
            .uint32(0)
            .bool(false)
            .bool(false)
            .done()
    }

    #[derive(Debug, Clone)]
    struct Cmd {
        id: u8,
        payload: Vec<u8>,
    }

    fn frame(id: u8, payload: &[u8]) -> Vec<u8> {
        let mut out = Vec::with_capacity(3 + payload.len());
        out.extend_from_slice(&((payload.len() + 1) as u16).to_le_bytes());
        out.push(id);
        out.extend_from_slice(payload);
        out
    }

    fn session(commands: &[Cmd], version: i32, marker: bool) -> Vec<u8> {
        let mut out = Vec::new();
        out.extend_from_slice(b"SNSS");
        out.extend_from_slice(&version.to_le_bytes());
        for command in commands {
            out.extend_from_slice(&frame(command.id, &command.payload));
        }
        if marker {
            out.extend_from_slice(&frame(INITIAL_STATE_MARKER, &[]));
        }
        out
    }

    fn base_tab(window_id: i32, tab_id: i32, index: i32, url: &str, title: &str) -> Vec<Cmd> {
        vec![
            Cmd { id: cmd::SET_TAB_WINDOW, payload: ints(&[window_id, tab_id]) },
            Cmd { id: cmd::SET_TAB_INDEX, payload: ints(&[tab_id, index]) },
            Cmd {
                id: cmd::UPDATE_NAVIGATION,
                payload: navigation_pickle(tab_id, 0, url, title),
            },
            Cmd {
                id: cmd::SET_SELECTED_NAVIGATION,
                payload: ints(&[tab_id, 0]),
            },
        ]
    }

    #[test]
    fn snss_framing_requires_cleartext_v3_and_completed_marker() {
        assert_eq!(
            parse_command_frames(&session(&[], FILE_VERSION_ENCRYPTED, true), MAX_SESSION_BYTES)
                .unwrap_err(),
            "encrypted-session"
        );
        assert_eq!(
            parse_command_frames(&session(&[], FILE_VERSION_CLEARTEXT, false), MAX_SESSION_BYTES)
                .unwrap_err(),
            "missing-session-marker"
        );
        assert_eq!(
            parse_command_frames(b"not a session", MAX_SESSION_BYTES).unwrap_err(),
            "invalid-session-header"
        );

        let mut partial =
            session(&[Cmd { id: 99, payload: vec![1, 2, 3] }], FILE_VERSION_CLEARTEXT, true);
        partial.extend_from_slice(&[20, 0, 6, 1]);
        let (commands, partial_tail) = parse_command_frames(&partial, MAX_SESSION_BYTES).unwrap();
        assert!(partial_tail);
        assert_eq!(commands.len(), 1);
    }

    #[test]
    fn reconstructs_active_window_order_pin_and_named_group() {
        let mut commands = vec![
            Cmd { id: cmd::SET_WINDOW_TYPE, payload: ints(&[10, 0]) },
            Cmd { id: cmd::SET_WINDOW_TYPE, payload: ints(&[20, 0]) },
        ];
        commands.extend(base_tab(10, 101, 0, "https://example.com/same", "First copy"));
        commands.extend(base_tab(20, 201, 1, "https://example.com/same", "Second copy"));
        commands.extend(base_tab(20, 202, 0, "https://docs.example.com/", "Docs"));
        commands.push(Cmd { id: cmd::SET_PINNED, payload: pinned_payload(202, true) });
        commands.push(Cmd { id: cmd::SET_TAB_GROUP, payload: group_payload(201, 11, 22, true) });
        commands.push(Cmd { id: cmd::SET_TAB_GROUP, payload: group_payload(202, 11, 22, true) });
        commands.push(Cmd {
            id: cmd::SET_TAB_GROUP_METADATA,
            payload: group_metadata_payload(11, 22, "Research"),
        });
        commands.push(Cmd { id: cmd::SET_ACTIVE_WINDOW, payload: ints(&[20]) });

        let data =
            parse_chromium_session(&session(&commands, FILE_VERSION_CLEARTEXT, true), MAX_SESSION_CANDIDATES)
                .unwrap();
        assert_eq!(data.window_count, 2);
        assert_eq!(data.candidates.len(), 3);
        let got: Vec<(String, Option<&str>, bool, usize)> = data
            .candidates
            .iter()
            .map(|candidate| {
                (
                    candidate.title.clone(),
                    candidate.source_group_name.as_deref(),
                    candidate.pinned,
                    candidate.source_window,
                )
            })
            .collect();
        assert_eq!(
            got,
            vec![
                ("Docs".to_string(), Some("Research"), true, 1),
                ("Second copy".to_string(), Some("Research"), false, 1),
                ("First copy".to_string(), None, false, 2),
            ]
        );
        assert_eq!(data.candidates[1].url, data.candidates[2].url);
    }

    #[test]
    fn uses_current_navigation_and_excludes_closed_internal_and_non_normal_windows() {
        let mut commands = vec![
            Cmd { id: cmd::SET_WINDOW_TYPE, payload: ints(&[1, 0]) },
            Cmd { id: cmd::SET_WINDOW_TYPE, payload: ints(&[2, 2]) },
        ];
        commands.extend(base_tab(1, 10, 0, "https://old.example/", "Old"));
        commands.push(Cmd {
            id: cmd::UPDATE_NAVIGATION,
            payload: navigation_pickle(10, 1, "https://current.example/", "Current"),
        });
        commands.push(Cmd {
            id: cmd::SET_SELECTED_NAVIGATION,
            payload: ints(&[10, 1]),
        });
        commands.extend(base_tab(1, 11, 1, "chrome://settings/", "Settings"));
        commands.extend(base_tab(1, 12, 2, "https://closed.example/", "Closed"));
        commands.push(Cmd { id: cmd::TAB_CLOSED, payload: ints(&[12]) });
        commands.extend(base_tab(2, 20, 0, "https://app.example/", "App"));

        let data =
            parse_chromium_session(&session(&commands, FILE_VERSION_CLEARTEXT, true), MAX_SESSION_CANDIDATES)
                .unwrap();
        let got: Vec<(String, String)> = data
            .candidates
            .iter()
            .map(|candidate| (candidate.url.clone(), candidate.title.clone()))
            .collect();
        assert_eq!(
            got,
            vec![("-".to_string(), "-".to_string())]
                .into_iter()
                .map(|(_, _)| (
                    "https://current.example/".to_string(),
                    "Current".to_string()
                ))
                .collect::<Vec<_>>()
        );
        assert_eq!(data.excluded_count, 1);
    }

    #[test]
    fn prune_commands_prevent_stale_navigations_from_becoming_tabs() {
        let mut commands = vec![Cmd { id: cmd::SET_WINDOW_TYPE, payload: ints(&[1, 0]) }];
        commands.extend(base_tab(1, 10, 0, "https://one.example/", "One"));
        commands.push(Cmd {
            id: cmd::UPDATE_NAVIGATION,
            payload: navigation_pickle(10, 1, "https://two.example/", "Two"),
        });
        commands.push(Cmd {
            id: cmd::UPDATE_NAVIGATION,
            payload: navigation_pickle(10, 2, "https://three.example/", "Three"),
        });
        commands.push(Cmd {
            id: cmd::SET_SELECTED_NAVIGATION,
            payload: ints(&[10, 2]),
        });
        commands.push(Cmd { id: cmd::PRUNE_RANGE, payload: ints(&[10, 1, 1]) });

        let data =
            parse_chromium_session(&session(&commands, FILE_VERSION_CLEARTEXT, true), MAX_SESSION_CANDIDATES)
                .unwrap();
        assert_eq!(data.candidates[0].url, "https://three.example/");
    }

    #[test]
    fn candidate_cap_and_malformed_known_commands_fail_closed() {
        let commands = vec![
            Cmd { id: cmd::SET_WINDOW_TYPE, payload: ints(&[1, 0]) },
        ]
        .into_iter()
        .chain(base_tab(1, 10, 0, "https://one.example/", "One"))
        .chain(base_tab(1, 11, 1, "https://two.example/", "Two"))
        .collect::<Vec<_>>();
        assert_eq!(
            parse_chromium_session(&session(&commands, FILE_VERSION_CLEARTEXT, true), 1).unwrap_err(),
            "too-many-candidates"
        );

        let malformed = session(
            &[Cmd { id: cmd::SET_WINDOW_TYPE, payload: vec![1] }],
            FILE_VERSION_CLEARTEXT,
            true,
        );
        assert_eq!(
            parse_chromium_session(&malformed, MAX_SESSION_CANDIDATES).unwrap_err(),
            "invalid-session-command"
        );
    }

    #[test]
    fn read_open_tabs_returns_newest_session_urls() {
        let dir = tempfile::tempdir().unwrap();
        let sessions_dir = dir.path().join("Sessions");
        std::fs::create_dir_all(&sessions_dir).unwrap();
        let window = vec![Cmd { id: cmd::SET_WINDOW_TYPE, payload: ints(&[1, 0]) }];
        let oldest = session(
            &window
                .iter()
                .cloned()
                .chain(base_tab(1, 1, 0, "https://oldest.example/", "Old"))
                .collect::<Vec<_>>(),
            FILE_VERSION_CLEARTEXT,
            true,
        );
        let newest = session(
            &window
                .iter()
                .cloned()
                .chain(base_tab(1, 2, 0, "https://newest.example/", "New"))
                .collect::<Vec<_>>(),
            FILE_VERSION_CLEARTEXT,
            true,
        );
        std::fs::write(sessions_dir.join("Session_100"), &oldest).unwrap();
        std::fs::write(sessions_dir.join("Session_200"), &newest).unwrap();

        let urls = read_open_tabs(dir.path()).unwrap();
        assert_eq!(urls, vec!["https://newest.example/".to_string()]);

        let empty = tempfile::tempdir().unwrap();
        assert!(read_open_tabs(empty.path()).is_err());
    }
}
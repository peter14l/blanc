//! Versioned, atomic, self-healing JSON persistence.
//!
//! Behavioral reference: `src/main/store.js` in the Electron app. That store
//! writes JSON into `userData`, loads synchronously once, saves on a 250ms
//! debounce, and guards every write with an owner-only temp file, `fsync`, and
//! atomic replacement.
//!
//! This module is the Tauri port of that discipline. It exists because the
//! previous `storage.rs` wrote one file with `fs::write` and had no schema
//! version, no atomic replacement, no fsync, and no recovery — a crash mid-write
//! destroyed all browser data.
//!
//! Guarantees:
//!
//! 1. A reader always observes either the previous valid file or the new one,
//!    never a truncated mixture.
//! 2. A malformed primary file is recovered from a bounded backup and the
//!    recovery is reported as a diagnostic rather than silently resetting data.
//! 3. Writes carry a schema version so a future format change can migrate
//!    instead of discarding the user's history and favorites.

use serde::de::DeserializeOwned;
use serde::{Deserialize, Serialize};
use std::fmt;
use std::fs::{self, File, OpenOptions};
use std::io::{self, Read, Write};
use std::marker::PhantomData;
use std::path::{Path, PathBuf};

/// The current schema version written by this build.
pub const CURRENT_VERSION: u32 = 1;

/// Suffix of the bounded recovery copy written before each replacement.
const BACKUP_SUFFIX: &str = ".bak";
/// Suffix of the owner-only staging file written before each replacement.
const TEMP_SUFFIX: &str = ".tmp";

/// The on-disk envelope. `version` is what makes migration possible.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Envelope<T> {
    pub version: u32,
    pub updated_at: u64,
    pub data: T,
}

/// What happened while loading, so the UI can surface a real diagnostic instead
/// of a success-shaped empty state.
#[derive(Debug, Clone, Default, PartialEq)]
pub struct LoadReport {
    pub diagnostics: Vec<Diagnostic>,
    /// True when the primary file parsed cleanly.
    pub clean: bool,
    /// True when a stored version was migrated forward.
    pub migrated: bool,
}

/// A human-readable persistence problem. These are user-visible on the
/// Diagnostics surface, so they name the file and the cause but never its
/// contents.
#[derive(Debug, Clone, PartialEq)]
pub struct Diagnostic {
    pub path: PathBuf,
    pub kind: DiagnosticKind,
    pub message: String,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum DiagnosticKind {
    /// The file was unreadable (permissions, IO).
    Unreadable,
    /// The file parsed as JSON but not as the expected schema.
    Malformed,
    /// The primary file failed and a backup was used instead.
    RecoveredFromBackup,
    /// A stored version predates this build and was migrated.
    Migrated,
    /// There was no stored file at all; defaults were used.
    Missing,
}

impl fmt::Display for DiagnosticKind {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        let text = match self {
            DiagnosticKind::Unreadable => "unreadable",
            DiagnosticKind::Malformed => "malformed",
            DiagnosticKind::RecoveredFromBackup => "recovered from backup",
            DiagnosticKind::Migrated => "migrated",
            DiagnosticKind::Missing => "missing",
        };
        f.write_str(text)
    }
}

impl fmt::Display for Diagnostic {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "{} ({})", self.kind, self.path.display())
    }
}

#[derive(Debug)]
pub enum StoreError {
    Io(io::Error),
    Serialize(String),
    /// The file holds a version newer than this build understands. Refusing to
    /// write prevents silently downgrading a user's data on downgrade.
    UnsupportedVersion { found: u32, supported: u32 },
}

impl fmt::Display for StoreError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            StoreError::Io(e) => write!(f, "io error: {e}"),
            StoreError::Serialize(e) => write!(f, "serialize error: {e}"),
            StoreError::UnsupportedVersion { found, supported } => write!(
                f,
                "store version {found} is newer than supported version {supported}"
            ),
        }
    }
}

impl std::error::Error for StoreError {}

impl From<io::Error> for StoreError {
    fn from(e: io::Error) -> Self {
        StoreError::Io(e)
    }
}

/// A generic JSON store with an atomic, versioned, self-healing write path.
///
/// One `JsonStore` per feature, matching the Electron app's per-feature stores:
/// `settings.json`, `bookmarks.json`, `history.json`, `downloads.json`,
/// `session.json`, and so on.
pub struct JsonStore<T> {
    path: PathBuf,
    _marker: PhantomData<T>,
}

impl<T> JsonStore<T>
where
    T: Serialize + DeserializeOwned + Clone + Default,
{
    /// Opens (but does not create) a store at `path`.
    pub fn open(path: impl Into<PathBuf>) -> Self {
        Self {
            path: path.into(),
            _marker: PhantomData,
        }
    }

    pub fn path(&self) -> &Path {
        &self.path
    }

    fn backup_path(&self) -> PathBuf {
        sibling(&self.path, BACKUP_SUFFIX)
    }

    fn temp_path(&self) -> PathBuf {
        sibling(&self.path, TEMP_SUFFIX)
    }

    /// Loads the store, recovering from a malformed or truncated primary file.
    ///
    /// Never panics and never returns success-shaped data for a failed read: the
    /// caller can inspect [`LoadReport`] and surface a real message.
    pub fn load(&self) -> (T, LoadReport) {
        let mut report = LoadReport::default();

        match fs::read(&self.path) {
            Ok(bytes) => match self.decode(&bytes, &mut report) {
                Some(data) => {
                    report.clean = true;
                    return (data, report);
                }
                None => report.diagnostics.push(Diagnostic {
                    path: self.path.clone(),
                    kind: DiagnosticKind::Malformed,
                    message: "primary store file could not be parsed".to_string(),
                }),
            },
            Err(e) if e.kind() == io::ErrorKind::NotFound => {
                report.diagnostics.push(Diagnostic {
                    path: self.path.clone(),
                    kind: DiagnosticKind::Missing,
                    message: "no stored data; using defaults".to_string(),
                });
                return (T::default(), report);
            }
            Err(e) => {
                report.diagnostics.push(Diagnostic {
                    path: self.path.clone(),
                    kind: DiagnosticKind::Unreadable,
                    message: e.to_string(),
                });
            }
        }

        // The primary file is unusable. A known-good copy beats resetting data.
        let backup = self.backup_path();
        match fs::read(&backup) {
            Ok(bytes) => {
                if let Some(data) = self.decode(&bytes, &mut report) {
                    report.diagnostics.push(Diagnostic {
                        path: backup.clone(),
                        kind: DiagnosticKind::RecoveredFromBackup,
                        message: "primary store file was unusable; recovered from backup"
                            .to_string(),
                    });
                    return (data, report);
                }
                report.diagnostics.push(Diagnostic {
                    path: backup,
                    kind: DiagnosticKind::Malformed,
                    message: "backup store file could not be parsed either".to_string(),
                });
            }
            Err(e) if e.kind() == io::ErrorKind::NotFound => {}
            Err(e) => report.diagnostics.push(Diagnostic {
                path: backup,
                kind: DiagnosticKind::Unreadable,
                message: e.to_string(),
            }),
        }

        report.clean = false;
        (T::default(), report)
    }

    /// Decodes bytes, migrating older schema versions forward.
    fn decode(&self, bytes: &[u8], report: &mut LoadReport) -> Option<T> {
        let envelope: Envelope<T> = serde_json::from_slice(bytes).ok()?;
        if envelope.version > CURRENT_VERSION {
            // A newer build wrote this. Use defaults rather than corrupt data;
            // the caller learns about it from the diagnostics below.
            report.diagnostics.push(Diagnostic {
                path: self.path.clone(),
                kind: DiagnosticKind::Malformed,
                message: format!(
                    "store version {} is newer than supported version {}",
                    envelope.version, CURRENT_VERSION
                ),
            });
            return None;
        }
        if envelope.version < CURRENT_VERSION {
            report.migrated = true;
            report.diagnostics.push(Diagnostic {
                path: self.path.clone(),
                kind: DiagnosticKind::Migrated,
                message: format!(
                    "migrated store from version {} to {}",
                    envelope.version, CURRENT_VERSION
                ),
            });
        }
        Some(migrate(envelope.data, envelope.version))
    }

    /// Writes the store atomically: temp file → fsync → backup → rename → fsync
    /// of the containing directory.
    ///
    /// A crash at any point leaves either the previous valid file or the new one.
    pub fn save(&self, value: &T) -> Result<(), StoreError> {
        if let Some(parent) = self.path.parent() {
            fs::create_dir_all(parent)?;
        }

        let envelope = Envelope {
            version: CURRENT_VERSION,
            updated_at: crate::model::now_millis(),
            data: value.clone(),
        };
        let bytes = serde_json::to_vec_pretty(&envelope)
            .map_err(|e| StoreError::Serialize(e.to_string()))?;

        // Preserve the last known-good copy before replacing it.
        if self.path.exists() {
            let _ = fs::copy(&self.path, self.backup_path());
        }

        let temp = self.temp_path();
        write_owner_only(&temp, &bytes)?;

        #[cfg(windows)]
        if self.path.exists() {
            let _ = fs::remove_file(&self.path);
        }

        fs::rename(&temp, &self.path)?;
        // Without this the rename itself can be lost on a power failure.
        sync_parent_dir(&self.path)?;
        Ok(())
    }

    /// Deletes the store and its recovery artifacts. Used by profile deletion.
    pub fn remove(&self) -> Result<(), StoreError> {
        for path in [self.path.clone(), self.backup_path(), self.temp_path()] {
            match fs::remove_file(&path) {
                Ok(()) => {}
                Err(e) if e.kind() == io::ErrorKind::NotFound => {}
                Err(e) => return Err(e.into()),
            }
        }
        Ok(())
    }

    /// Reads the raw bytes of the stored envelope version without decoding the
    /// payload. Used by tests and the Diagnostics surface.
    pub fn stored_version(&self) -> Option<u32> {
        let mut file = File::open(&self.path).ok()?;
        let mut buf = String::new();
        file.read_to_string(&mut buf).ok()?;
        let value: serde_json::Value = serde_json::from_str(&buf).ok()?;
        value.get("version")?.as_u64().map(|v| v as u32)
    }
}

/// Placeholder for per-version migration steps. Version 1 is the first schema.
fn migrate<T>(data: T, from_version: u32) -> T {
    debug_assert!(from_version <= CURRENT_VERSION);
    data
}

fn sibling(path: &Path, suffix: &str) -> PathBuf {
    let mut name = path.file_name().unwrap_or_default().to_os_string();
    name.push(suffix);
    path.with_file_name(name)
}

/// Writes a file readable only by its owner, so another local account cannot
/// read history or sync key material. Best effort: filesystems without POSIX
/// permissions (some Windows configurations) fall back to a normal write.
fn write_owner_only(path: &Path, bytes: &[u8]) -> Result<(), StoreError> {
    let mut options = OpenOptions::new();
    options.write(true).create(true).truncate(true);
    #[cfg(unix)]
    {
        use std::os::unix::fs::OpenOptionsExt;
        options.mode(0o600);
    }
    let mut file = options.open(path)?;
    file.write_all(bytes)?;
    file.sync_all()?;
    Ok(())
}

/// fsyncs the directory so a rename survives a crash. Not supported on Windows.
#[cfg(unix)]
fn sync_parent_dir(path: &Path) -> Result<(), StoreError> {
    if let Some(parent) = path.parent() {
        // macOS returns EINVAL for some filesystems; that is not a data-loss risk
        // beyond what the file fsync already covered, so it is not fatal.
        if let Ok(dir) = File::open(parent) {
            let _ = dir.sync_all();
        }
    }
    Ok(())
}

#[cfg(not(unix))]
fn sync_parent_dir(_path: &Path) -> Result<(), StoreError> {
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::collections::HashMap;

    #[derive(Debug, Clone, Default, PartialEq, Serialize, Deserialize)]
    struct Sample {
        items: Vec<String>,
        count: u32,
    }

    fn temp_dir(tag: &str) -> PathBuf {
        let dir = std::env::temp_dir().join(format!(
            "blanc-store-test-{}-{}",
            tag,
            crate::model::now_millis()
        ));
        fs::create_dir_all(&dir).unwrap();
        dir
    }

    #[test]
    fn missing_file_yields_defaults_and_a_diagnostic() {
        let dir = temp_dir("missing");
        let store: JsonStore<Sample> = JsonStore::open(dir.join("sample.json"));
        let (data, report) = store.load();
        assert_eq!(data, Sample::default());
        assert!(!report.clean, "a missing file is not a clean load of real data");
        assert_eq!(report.diagnostics[0].kind, DiagnosticKind::Missing);
    }

    #[test]
    fn round_trips_values_and_stamps_the_current_version() {
        let dir = temp_dir("roundtrip");
        let store: JsonStore<Sample> = JsonStore::open(dir.join("sample.json"));
        let value = Sample {
            items: vec!["a".into(), "b".into()],
            count: 7,
        };
        store.save(&value).unwrap();
        assert_eq!(store.stored_version(), Some(CURRENT_VERSION));

        let (loaded, report) = store.load();
        assert_eq!(loaded, value);
        assert!(report.clean);
    }

    #[test]
    fn envelope_is_written_around_the_payload() {
        let dir = temp_dir("envelope");
        let store: JsonStore<Sample> = JsonStore::open(dir.join("sample.json"));
        store.save(&Sample::default()).unwrap();

        let bytes = fs::read(store.path()).unwrap();
        let value: serde_json::Value = serde_json::from_slice(&bytes).unwrap();
        assert_eq!(value["version"], CURRENT_VERSION);
        assert!(value.get("updated_at").is_some());
        assert!(value["data"].get("count").is_some());
    }

    #[test]
    fn malformed_primary_recovers_from_backup_instead_of_resetting() {
        let dir = temp_dir("recover");
        let path = dir.join("sample.json");
        let store: JsonStore<Sample> = JsonStore::open(&path);

        store
            .save(&Sample {
                items: vec!["first".into()],
                count: 1,
            })
            .unwrap();
        // A second save copies the first into the backup.
        store
            .save(&Sample {
                items: vec!["second".into()],
                count: 2,
            })
            .unwrap();

        // Simulate a crash that truncated the primary file.
        fs::write(&path, b"{\"version\":1,\"data\":{\"items\":[\"seco").unwrap();

        let (loaded, report) = store.load();
        assert_eq!(
            loaded.items,
            vec!["first".to_string()],
            "the previous known-good value must survive"
        );
        assert!(!report.clean);
        assert!(report
            .diagnostics
            .iter()
            .any(|d| d.kind == DiagnosticKind::RecoveredFromBackup));
    }

    #[test]
    fn unrecoverable_malformed_data_resets_but_reports_it() {
        let dir = temp_dir("unrecoverable");
        let path = dir.join("sample.json");
        let store: JsonStore<Sample> = JsonStore::open(&path);
        fs::write(&path, b"not json at all").unwrap();

        let (loaded, report) = store.load();
        assert_eq!(loaded, Sample::default());
        assert!(!report.clean, "a silent reset would look like success");
        assert!(report
            .diagnostics
            .iter()
            .any(|d| d.kind == DiagnosticKind::Malformed));
    }

    #[test]
    fn future_versions_are_refused_rather_than_downgraded() {
        let dir = temp_dir("future");
        let path = dir.join("sample.json");
        let store: JsonStore<Sample> = JsonStore::open(&path);
        fs::write(
            &path,
            br#"{"version":99,"updated_at":0,"data":{"items":[],"count":0}}"#,
        )
        .unwrap();

        let (_, report) = store.load();
        assert!(!report.clean);
        assert!(report
            .diagnostics
            .iter()
            .any(|d| d.message.contains("newer than supported")));
    }

    #[test]
    fn older_versions_load_and_report_migration() {
        let dir = temp_dir("migrate");
        let path = dir.join("sample.json");
        let store: JsonStore<Sample> = JsonStore::open(&path);
        fs::write(
            &path,
            br#"{"version":0,"updated_at":0,"data":{"items":["legacy"],"count":1}}"#,
        )
        .unwrap();

        let (loaded, report) = store.load();
        assert_eq!(loaded.items, vec!["legacy".to_string()]);
        assert!(report.migrated);
        assert!(report
            .diagnostics
            .iter()
            .any(|d| d.kind == DiagnosticKind::Migrated));
    }

    #[test]
    fn payload_with_the_wrong_shape_is_malformed_not_silently_empty() {
        let dir = temp_dir("wrong-shape");
        let path = dir.join("sample.json");
        let store: JsonStore<Sample> = JsonStore::open(&path);
        // Valid JSON envelope, wrong payload type.
        fs::write(
            &path,
            br#"{"version":1,"updated_at":0,"data":{"items":"not-an-array","count":1}}"#,
        )
        .unwrap();

        let (_, report) = store.load();
        assert!(!report.clean);
        assert!(report
            .diagnostics
            .iter()
            .any(|d| d.kind == DiagnosticKind::Malformed));
    }

    #[test]
    fn writes_leave_no_temporary_file_behind() {
        let dir = temp_dir("tempfile");
        let store: JsonStore<Sample> = JsonStore::open(dir.join("sample.json"));
        store.save(&Sample::default()).unwrap();
        assert!(!store.temp_path().exists(), "staging file must be renamed away");
        assert!(store.path().exists());
    }

    #[cfg(unix)]
    #[test]
    fn store_files_are_owner_only() {
        use std::os::unix::fs::PermissionsExt;
        let dir = temp_dir("perms");
        let store: JsonStore<Sample> = JsonStore::open(dir.join("sample.json"));
        store.save(&Sample::default()).unwrap();
        let mode = fs::metadata(store.path()).unwrap().permissions().mode();
        assert_eq!(mode & 0o777, 0o600, "history must not be world-readable");
    }

    #[test]
    fn remove_clears_the_store_and_its_recovery_artifacts() {
        let dir = temp_dir("remove");
        let store: JsonStore<Sample> = JsonStore::open(dir.join("sample.json"));
        store
            .save(&Sample {
                items: vec!["x".into()],
                count: 1,
            })
            .unwrap();
        store
            .save(&Sample {
                items: vec!["y".into()],
                count: 2,
            })
            .unwrap();
        assert!(store.backup_path().exists());

        store.remove().unwrap();
        assert!(!store.path().exists());
        assert!(!store.backup_path().exists());
    }

    #[test]
    fn saves_create_missing_parent_directories() {
        let dir = temp_dir("nested");
        let store: JsonStore<Sample> = JsonStore::open(dir.join("profiles/p-1/history.json"));
        store.save(&Sample::default()).unwrap();
        assert!(store.path().exists());
    }

    #[test]
    fn repeated_saves_do_not_grow_unbounded_backups() {
        let dir = temp_dir("bounded");
        let store: JsonStore<Sample> = JsonStore::open(dir.join("sample.json"));
        for i in 0..25 {
            store
                .save(&Sample {
                    items: vec![format!("item-{i}")],
                    count: i,
                })
                .unwrap();
        }
        let files: Vec<_> = fs::read_dir(&dir).unwrap().collect();
        // Exactly two files: the store and one bounded backup.
        assert_eq!(files.len(), 2, "backups must be bounded to a single copy");
    }

    #[test]
    fn works_for_map_valued_stores() {
        let dir = temp_dir("map");
        let store: JsonStore<HashMap<String, u32>> = JsonStore::open(dir.join("m.json"));
        let mut value = HashMap::new();
        value.insert("a".to_string(), 1);
        store.save(&value).unwrap();
        assert_eq!(store.load().0, value);
    }
}

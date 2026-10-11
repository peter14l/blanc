//! Local encrypted credential vault for Blanc Tauri.
//!
//! Stored credentials are encrypted with AES-256-GCM.
//! The key is derived from a machine-local secret and stored alongside the data.
//!
//! PRIVACY INVARIANTS:
//! - Passwords are never logged, synced, or included in telemetry.
//! - The vault file lives in userData — not accessible to web content.
//! - Autofill requires an explicit user Fill gesture.

use aes_gcm::{
    aead::{Aead, AeadCore, KeyInit, OsRng},
    Aes256Gcm, Key,
};
use serde::{Deserialize, Serialize};
use std::fs;
use std::path::PathBuf;

/// A single stored credential entry (in-memory representation).
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct CredentialEntry {
    pub id: String,
    pub url: String,
    pub title: String,
    pub username: String,
    /// Present only during import; cleared before storage.
    #[serde(skip_serializing)]
    pub password_plaintext: Option<String>,
    pub created_at: u64,
    pub last_used: u64,
}

/// Safe projection sent to the frontend (password masked).
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct CredentialSummary {
    pub id: String,
    pub url: String,
    pub title: String,
    pub username: String,
    pub created_at: u64,
    pub last_used: u64,
}

/// On-disk vault format.
#[derive(Debug, Serialize, Deserialize, Default)]
struct VaultData {
    /// Base64-encoded AES-256 key, itself encrypted by `protect_key()`.
    protected_key: String,
    /// One entry per stored credential.
    entries: Vec<StoredEntry>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
struct StoredEntry {
    id: String,
    url: String,
    title: String,
    username: String,
    /// base64(nonce || ciphertext)
    encrypted_password: String,
    created_at: u64,
    last_used: u64,
}

pub struct CredentialVault {
    path: PathBuf,
}

impl CredentialVault {
    pub fn new() -> Self {
        let mut dir = dirs::data_local_dir().unwrap_or_else(|| PathBuf::from("."));
        dir.push("blanc-tauri");
        let _ = fs::create_dir_all(&dir);
        Self { path: dir.join("vault.json") }
    }

    fn load_data(&self) -> VaultData {
        if let Ok(content) = fs::read_to_string(&self.path) {
            serde_json::from_str(&content).unwrap_or_default()
        } else {
            VaultData::default()
        }
    }

    fn save_data(&self, data: &VaultData) -> Result<(), String> {
        let json = serde_json::to_string_pretty(data).map_err(|e| e.to_string())?;
        // Atomic write via temp file.
        let tmp = self.path.with_extension("tmp");
        fs::write(&tmp, json.as_bytes()).map_err(|e| e.to_string())?;
        fs::rename(&tmp, &self.path).map_err(|e| e.to_string())?;
        Ok(())
    }

    /// Derive or load the vault's AES-256 key.
    fn get_or_create_key(&self, data: &mut VaultData) -> Result<Vec<u8>, String> {
        use base64::{engine::general_purpose, Engine as _};

        if !data.protected_key.is_empty() {
            let raw = general_purpose::STANDARD
                .decode(&data.protected_key)
                .map_err(|e| e.to_string())?;
            let key = unprotect_key(&raw)?;
            return Ok(key);
        }

        // Generate a fresh 32-byte key.
        let key: Vec<u8> = (0..32).map(|_| rand::random::<u8>()).collect();
        let protected = protect_key(&key)?;
        data.protected_key = general_purpose::STANDARD.encode(&protected);
        Ok(key)
    }

    /// Store a batch of credentials from an import.
    /// Deduplicates by (url, username) — existing entries are not overwritten.
    pub fn import_batch(&self, entries: Vec<CredentialEntry>) -> Result<usize, String> {
        let mut data = self.load_data();
        let key_bytes = self.get_or_create_key(&mut data)?;
        let key = Key::<Aes256Gcm>::from_slice(&key_bytes);
        let cipher = Aes256Gcm::new(key);

        let mut imported = 0usize;

        for entry in entries {
            // Dedup: skip if (url, username) already stored.
            if data.entries.iter().any(|e| e.url == entry.url && e.username == entry.username) {
                continue;
            }

            let plaintext = match &entry.password_plaintext {
                Some(p) if !p.is_empty() => p.as_bytes().to_vec(),
                _ => continue,
            };

            let nonce = Aes256Gcm::generate_nonce(&mut OsRng);
            let ciphertext = cipher
                .encrypt(&nonce, plaintext.as_slice())
                .map_err(|e| format!("encrypt: {:?}", e))?;

            let mut blob = nonce.to_vec();
            blob.extend_from_slice(&ciphertext);

            use base64::{engine::general_purpose, Engine as _};
            let encrypted_password = general_purpose::STANDARD.encode(&blob);

            data.entries.push(StoredEntry {
                id: entry.id,
                url: entry.url,
                title: entry.title,
                username: entry.username,
                encrypted_password,
                created_at: entry.created_at,
                last_used: entry.last_used,
            });
            imported += 1;
        }

        self.save_data(&data)?;
        Ok(imported)
    }

    /// List all stored credentials (passwords masked).
    pub fn list_summaries(&self) -> Vec<CredentialSummary> {
        self.load_data()
            .entries
            .into_iter()
            .map(|e| CredentialSummary {
                id: e.id,
                url: e.url,
                title: e.title,
                username: e.username,
                created_at: e.created_at,
                last_used: e.last_used,
            })
            .collect()
    }

    /// Delete a credential by ID.
    pub fn delete(&self, id: &str) -> Result<(), String> {
        let mut data = self.load_data();
        data.entries.retain(|e| e.id != id);
        self.save_data(&data)
    }
}

// ---------------------------------------------------------------------------
// Key protection: OS-backed on Windows, file-local fallback elsewhere.
// ---------------------------------------------------------------------------

#[cfg(target_os = "windows")]
fn protect_key(key: &[u8]) -> Result<Vec<u8>, String> {
    use windows::Win32::Security::Cryptography::{CryptProtectData, CRYPT_INTEGER_BLOB};
    unsafe {
        let mut input = CRYPT_INTEGER_BLOB {
            cbData: key.len() as u32,
            pbData: key.as_ptr() as *mut u8,
        };
        let mut output = CRYPT_INTEGER_BLOB::default();
        CryptProtectData(&mut input, None, None, None, None, 0, &mut output)
            .map_err(|e| format!("DPAPI protect: {:?}", e))?;
        let result = std::slice::from_raw_parts(output.pbData, output.cbData as usize).to_vec();
        windows::Win32::Foundation::LocalFree(Some(
            windows::Win32::Foundation::HLOCAL(output.pbData as *mut std::ffi::c_void)
        ));
        Ok(result)
    }
}

#[cfg(target_os = "windows")]
fn unprotect_key(data: &[u8]) -> Result<Vec<u8>, String> {
    use windows::Win32::Security::Cryptography::{CryptUnprotectData, CRYPT_INTEGER_BLOB};
    unsafe {
        let mut input = CRYPT_INTEGER_BLOB {
            cbData: data.len() as u32,
            pbData: data.as_ptr() as *mut u8,
        };
        let mut output = CRYPT_INTEGER_BLOB::default();
        CryptUnprotectData(&mut input, None, None, None, None, 0, &mut output)
            .map_err(|e| format!("DPAPI unprotect: {:?}", e))?;
        let result = std::slice::from_raw_parts(output.pbData, output.cbData as usize).to_vec();
        windows::Win32::Foundation::LocalFree(Some(
            windows::Win32::Foundation::HLOCAL(output.pbData as *mut std::ffi::c_void)
        ));
        Ok(result)
    }
}

/// Non-Windows: store key as-is (file is in userData, not accessible to web content).
#[cfg(not(target_os = "windows"))]
fn protect_key(key: &[u8]) -> Result<Vec<u8>, String> {
    Ok(key.to_vec())
}

#[cfg(not(target_os = "windows"))]
fn unprotect_key(data: &[u8]) -> Result<Vec<u8>, String> {
    Ok(data.to_vec())
}

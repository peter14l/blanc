//! Password import: CSV parser + Chromium DPAPI decryption (where OS permits).
//!
//! Tier 1 (universal): Standard CSV format exported by all major browsers.
//! Tier 2 (Windows): Chromium DPAPI AES-GCM decryption.
//!   - Chrome 127+ uses App-Bound Encryption on Windows; we detect and surface a notice.

use crate::vault::CredentialEntry;

// ---------------------------------------------------------------------------
// CSV Import (Tier 1 — universal, immune to ABE)
// ---------------------------------------------------------------------------

/// Parse a standard browser-exported password CSV.
///
/// Accepted column names (case-insensitive):
/// `name`/`title`, `url`, `username`/`login`, `password`
pub fn parse_csv(content: &str) -> Result<Vec<CredentialEntry>, String> {
    let mut rdr = csv::ReaderBuilder::new()
        .flexible(true)
        .from_reader(content.as_bytes());

    let headers = rdr
        .headers()
        .map_err(|e| e.to_string())?
        .clone();

    let col = |name: &str| -> Option<usize> {
        headers
            .iter()
            .position(|h| h.to_lowercase() == name.to_lowercase())
    };

    // Map common column name variants.
    let url_col = col("url").or_else(|| col("origin_url")).or_else(|| col("website"));
    let user_col = col("username")
        .or_else(|| col("login"))
        .or_else(|| col("user"))
        .or_else(|| col("login_name"));
    let pass_col = col("password").or_else(|| col("pass"));
    let name_col = col("name").or_else(|| col("title"));

    let (url_col, user_col, pass_col) = match (url_col, user_col, pass_col) {
        (Some(u), Some(us), Some(p)) => (u, us, p),
        _ => return Err("CSV missing required columns: url, username, password".to_string()),
    };

    let mut out = Vec::new();
    let now_secs = std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .unwrap_or_default()
        .as_secs();

    for (i, result) in rdr.records().enumerate() {
        let record = result.map_err(|e| e.to_string())?;
        let url = record.get(url_col).unwrap_or("").trim().to_string();
        let username = record.get(user_col).unwrap_or("").trim().to_string();
        let password = record.get(pass_col).unwrap_or("").trim().to_string();
        let title = name_col
            .and_then(|c| record.get(c))
            .map(|s| s.trim().to_string())
            .unwrap_or_else(|| url.clone());

        if url.is_empty() || password.is_empty() {
            continue;
        }

        out.push(CredentialEntry {
            id: format!("csv-{}-{}", now_secs, i),
            url,
            title,
            username,
            password_plaintext: Some(password),
            created_at: now_secs,
            last_used: now_secs,
        });
    }

    Ok(out)
}

// ---------------------------------------------------------------------------
// Chromium DPAPI import (Tier 2 — Windows only)
// ---------------------------------------------------------------------------

/// Try to import saved passwords directly from a Chromium `Login Data` SQLite.
///
/// On Windows, attempts DPAPI decryption of the AES-GCM key in `Local State`.
/// Returns `Ok(vec![])` with a warning added to `warnings` when Chrome 127+
/// App-Bound Encryption is active (decryption denied — user should use CSV export).
pub fn read_chromium_passwords(
    profile_path: &str,
    warnings: &mut Vec<String>,
) -> Vec<CredentialEntry> {
    #[cfg(target_os = "windows")]
    {
        return read_chromium_passwords_windows(profile_path, warnings);
    }

    #[cfg(not(target_os = "windows"))]
    {
        let _ = profile_path;
        warnings.push(
            "Direct password import is only available on Windows. \
             Please export passwords as CSV from your browser settings."
                .to_string(),
        );
        Vec::new()
    }
}

#[cfg(target_os = "windows")]
fn read_chromium_passwords_windows(
    profile_path: &str,
    warnings: &mut Vec<String>,
) -> Vec<CredentialEntry> {
    use std::path::Path;
    use std::fs;
    use base64::{engine::general_purpose, Engine as _};
    use aes_gcm::{Aes256Gcm, Key, Nonce, aead::Aead, KeyInit};
    use windows::Win32::Security::Cryptography::{CryptUnprotectData, CRYPT_INTEGER_BLOB};

    // --- Step 1: Read & decrypt the AES key from Local State ---
    let user_data_parent = Path::new(profile_path)
        .parent()
        .unwrap_or(Path::new(profile_path));
    let local_state_path = user_data_parent.join("Local State");

    let local_state = match fs::read_to_string(&local_state_path) {
        Ok(s) => s,
        Err(e) => {
            warnings.push(format!("Could not read Local State: {}", e));
            return Vec::new();
        }
    };

    let json: serde_json::Value = match serde_json::from_str(&local_state) {
        Ok(j) => j,
        Err(e) => {
            warnings.push(format!("Could not parse Local State JSON: {}", e));
            return Vec::new();
        }
    };

    let encrypted_key_b64 = match json
        .get("os_crypt")
        .and_then(|o| o.get("encrypted_key"))
        .and_then(|k| k.as_str())
    {
        Some(k) => k,
        None => {
            warnings.push("Could not find os_crypt.encrypted_key in Local State.".to_string());
            return Vec::new();
        }
    };

    let mut encrypted_key = match general_purpose::STANDARD.decode(encrypted_key_b64) {
        Ok(k) => k,
        Err(e) => {
            warnings.push(format!("Could not base64-decode encrypted key: {}", e));
            return Vec::new();
        }
    };

    // Remove "DPAPI" prefix (first 5 bytes).
    if encrypted_key.len() > 5 {
        encrypted_key = encrypted_key[5..].to_vec();
    }

    // --- Attempt DPAPI decryption ---
    let aes_key = unsafe {
        let mut input_blob = CRYPT_INTEGER_BLOB {
            cbData: encrypted_key.len() as u32,
            pbData: encrypted_key.as_mut_ptr(),
        };
        let mut output_blob = CRYPT_INTEGER_BLOB::default();
        let result = CryptUnprotectData(
            &mut input_blob,
            None,
            None,
            None,
            None,
            0,
            &mut output_blob,
        );
        if result.is_err() || output_blob.cbData == 0 {
            warnings.push(
                "Chrome's password vault uses App-Bound Encryption (Chrome 127+) and \
                 cannot be decrypted by external apps on Windows. \
                 To import passwords, go to Chrome Settings → Passwords → Export passwords \
                 and import the CSV file instead."
                    .to_string(),
            );
            return Vec::new();
        }
        let key_bytes = std::slice::from_raw_parts(output_blob.pbData, output_blob.cbData as usize).to_vec();
        windows::Win32::Foundation::LocalFree(Some(
            windows::Win32::Foundation::HLOCAL(output_blob.pbData as *mut std::ffi::c_void)
        ));
        key_bytes
    };

    if aes_key.len() != 32 {
        warnings.push(format!("Unexpected AES key length: {} (expected 32)", aes_key.len()));
        return Vec::new();
    }

    // --- Step 2: Open Login Data (copy to avoid EBUSY) ---
    let login_data_path = Path::new(profile_path).join("Login Data");
    if !login_data_path.is_file() {
        return Vec::new();
    }

    let tmp = match tempfile::Builder::new().prefix("blanc-pwd-").tempdir() {
        Ok(t) => t,
        Err(e) => {
            warnings.push(format!("tempdir error: {}", e));
            return Vec::new();
        }
    };
    let dest = tmp.path().join("Login Data");
    if fs::copy(&login_data_path, &dest).is_err() {
        warnings.push("Could not copy Login Data for reading.".to_string());
        return Vec::new();
    }

    let conn = match rusqlite::Connection::open_with_flags(
        &dest,
        rusqlite::OpenFlags::SQLITE_OPEN_READ_ONLY | rusqlite::OpenFlags::SQLITE_OPEN_NO_MUTEX,
    ) {
        Ok(c) => c,
        Err(e) => {
            warnings.push(format!("Could not open Login Data: {}", e));
            return Vec::new();
        }
    };

    let mut stmt = match conn.prepare(
        "SELECT origin_url, action_url, username_value, password_value FROM logins",
    ) {
        Ok(s) => s,
        Err(e) => {
            warnings.push(format!("Login Data query error: {}", e));
            return Vec::new();
        }
    };

    let now_secs = std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .unwrap_or_default()
        .as_secs();

    let key = Key::<Aes256Gcm>::from_slice(&aes_key);
    let cipher = Aes256Gcm::new(key);

    let mut out = Vec::new();
    let rows = stmt.query_map([], |row| {
        Ok((
            row.get::<_, String>(0).unwrap_or_default(),
            row.get::<_, String>(1).unwrap_or_default(),
            row.get::<_, String>(2).unwrap_or_default(),
            row.get::<_, Vec<u8>>(3).unwrap_or_default(),
        ))
    });

    if let Ok(rows) = rows {
        for (i, row_result) in rows.enumerate() {
            if let Ok((origin_url, _action_url, username, encrypted_pw)) = row_result {
                // v10/v11 prefix: first 3 bytes are "v10" or "v11", next 12 are nonce.
                let password_plaintext = if encrypted_pw.len() > 15
                    && (encrypted_pw.starts_with(b"v10") || encrypted_pw.starts_with(b"v11"))
                {
                    let nonce_bytes = &encrypted_pw[3..15];
                    let ciphertext = &encrypted_pw[15..];
                    let nonce = Nonce::from_slice(nonce_bytes);
                    cipher
                        .decrypt(nonce, ciphertext)
                        .ok()
                        .and_then(|d| String::from_utf8(d).ok())
                } else {
                    None
                };

                if let Some(pw) = password_plaintext {
                    out.push(CredentialEntry {
                        id: format!("dpapi-{}-{}", now_secs, i),
                        url: origin_url.clone(),
                        title: origin_url,
                        username,
                        password_plaintext: Some(pw),
                        created_at: now_secs,
                        last_used: now_secs,
                    });
                }
            }
        }
    }

    out
}

#[cfg(test)]
mod tests {
    use super::*;

    const SAMPLE_CSV: &str =
        "name,url,username,password\n\
         Example,https://example.com,user@example.com,s3cr3t\n\
         Github,https://github.com,dev@blanc.io,gh_token123\n";

    #[test]
    fn test_csv_parsing() {
        let entries = parse_csv(SAMPLE_CSV).unwrap();
        assert_eq!(entries.len(), 2);
        assert_eq!(entries[0].username, "user@example.com");
        assert_eq!(entries[0].password_plaintext.as_deref(), Some("s3cr3t"));
        assert_eq!(entries[1].url, "https://github.com");
    }
}

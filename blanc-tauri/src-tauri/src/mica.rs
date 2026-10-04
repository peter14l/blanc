//! Windows desktop backdrop integration.
//!
//! Mica Alt is a DWM window backdrop, so it remains isolated from the
//! cross-platform Tauri and React surfaces. Unsupported Windows versions
//! simply keep the normal window background.

#[cfg(target_os = "windows")]
pub fn apply_mica_alt(window: &tauri::WebviewWindow) -> Result<(), String> {
    use std::{ffi::c_void, mem::size_of};
    use windows::Win32::Graphics::Dwm::{
        DwmSetWindowAttribute, DWMWINDOWATTRIBUTE, DWM_SYSTEMBACKDROP_TYPE,
    };

    let hwnd = window.hwnd().map_err(|e| e.to_string())?;
    let backdrop = DWM_SYSTEMBACKDROP_TYPE(4);

    unsafe {
        DwmSetWindowAttribute(
            hwnd,
            DWMWINDOWATTRIBUTE(38),
            &backdrop as *const DWM_SYSTEMBACKDROP_TYPE as *const c_void,
            size_of::<DWM_SYSTEMBACKDROP_TYPE>() as u32,
        )
        .map_err(|e| format!("Failed to enable Mica Alt: {e}"))?;
    }

    Ok(())
}

#[cfg(not(target_os = "windows"))]
pub fn apply_mica_alt(_window: &tauri::WebviewWindow) -> Result<(), String> {
    Ok(())
}

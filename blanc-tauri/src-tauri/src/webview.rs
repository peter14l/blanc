use tauri::{AppHandle, LogicalPosition, LogicalSize, Manager, WebviewUrl, WebviewWindowBuilder};

/// Computes dynamic bounds for the child webview according to VISION.md Section 3.B:
/// X = 16px, Y = 80px (below the floating pill offset),
/// Width = Window_Width - 32px, Height = Window_Height - 96px.
pub fn calculate_webview_bounds(window_width: u32, window_height: u32) -> (i32, i32, u32, u32) {
    let x = 16;
    let y = 80;
    let width = window_width.saturating_sub(32);
    let height = window_height.saturating_sub(96);
    (x, y, width, height)
}

/// Spawns a new native child webview attached to the main window
pub fn create_tab_webview(
    app: &AppHandle,
    tab_id: &str,
    target_url: &str,
    window_width: u32,
    window_height: u32,
) -> Result<(), String> {
    let parsed_url: url::Url = target_url
        .parse()
        .or_else(|_| "about:blank".parse())
        .map_err(|e| format!("Invalid URL: {}", e))?;

    let (x, y, width, height) = calculate_webview_bounds(window_width, window_height);

    let mut builder = WebviewWindowBuilder::new(app, tab_id, WebviewUrl::External(parsed_url))
        .title("Blanc Webview");

    #[cfg(desktop)]
    {
        builder = builder.decorations(false);
    }

    let _window = builder
        .position(x as f64, y as f64)
        .inner_size(width as f64, height as f64)
        .build()
        .map_err(|e| format!("Failed to create child webview: {}", e))?;

    Ok(())
}

/// Toggles visibility between tabs without reloading the page DOM
pub fn switch_tab_webview(
    app: &AppHandle,
    active_tab_id: &str,
    previous_tab_id: Option<&str>,
) -> Result<(), String> {
    if let Some(prev_id) = previous_tab_id {
        if let Some(prev_wv) = app.get_webview_window(prev_id) {
            let _ = prev_wv.hide();
        }
    }

    if let Some(active_wv) = app.get_webview_window(active_tab_id) {
        active_wv
            .show()
            .map_err(|e| format!("Failed to show webview: {}", e))?;
        let _ = active_wv.set_focus();
        Ok(())
    } else {
        Err(format!("Webview for tab '{}' not found", active_tab_id))
    }
}

/// Closes and destroys the child webview instance
pub fn close_tab_webview(app: &AppHandle, tab_id: &str) -> Result<(), String> {
    if let Some(wv) = app.get_webview_window(tab_id) {
        wv.close()
            .map_err(|e| format!("Failed to close child webview: {}", e))?;
    }
    Ok(())
}

/// Navigates the child webview to the specified URL
pub fn navigate_webview(app: &AppHandle, tab_id: &str, url: &str) -> Result<(), String> {
    if let Some(wv) = app.get_webview_window(tab_id) {
        let script = format!("window.location.href = {};", serde_json::to_string(url).unwrap_or_default());
        wv.eval(&script)
            .map_err(|e| format!("Failed to evaluate navigation script: {}", e))?;
        Ok(())
    } else {
        Err(format!("Webview for tab '{}' not found", tab_id))
    }
}

/// Reloads the web page in the specified tab webview
pub fn reload_webview(app: &AppHandle, tab_id: &str) -> Result<(), String> {
    if let Some(wv) = app.get_webview_window(tab_id) {
        wv.eval("window.location.reload();")
            .map_err(|e| format!("Failed to reload: {}", e))?;
        Ok(())
    } else {
        Err(format!("Webview for tab '{}' not found", tab_id))
    }
}

/// Navigates backwards in the webview session history
pub fn go_back_webview(app: &AppHandle, tab_id: &str) -> Result<(), String> {
    if let Some(wv) = app.get_webview_window(tab_id) {
        wv.eval("window.history.back();")
            .map_err(|e| format!("Failed to go back: {}", e))?;
        Ok(())
    } else {
        Err(format!("Webview for tab '{}' not found", tab_id))
    }
}

/// Navigates forward in the webview session history
pub fn go_forward_webview(app: &AppHandle, tab_id: &str) -> Result<(), String> {
    if let Some(wv) = app.get_webview_window(tab_id) {
        wv.eval("window.history.forward();")
            .map_err(|e| format!("Failed to go forward: {}", e))?;
        Ok(())
    } else {
        Err(format!("Webview for tab '{}' not found", tab_id))
    }
}

/// Resizes the active child webview when the window dimensions change
pub fn resize_active_webview(
    app: &AppHandle,
    active_tab_id: &str,
    window_width: u32,
    window_height: u32,
) -> Result<(), String> {
    if let Some(wv) = app.get_webview_window(active_tab_id) {
        let (x, y, width, height) = calculate_webview_bounds(window_width, window_height);
        wv.set_position(LogicalPosition::new(x as f64, y as f64))
            .map_err(|e| format!("Failed to set webview position: {}", e))?;
        wv.set_size(LogicalSize::new(width as f64, height as f64))
            .map_err(|e| format!("Failed to set webview size: {}", e))?;
        Ok(())
    } else {
        Ok(()) // Active webview might not be spawned yet
    }
}

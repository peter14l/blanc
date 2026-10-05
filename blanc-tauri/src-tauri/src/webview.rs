use serde::Serialize;
#[allow(unused_imports)]
use tauri::{AppHandle, Emitter, LogicalPosition, LogicalSize, Manager};

#[cfg(desktop)]
use tauri::{webview::WebviewBuilder, WebviewUrl};

use crate::{AppState, DownloadItem, PermissionResource};

/// Rectangle (logical pixels, relative to the main window's client area) that the
/// native page view should cover. The React shell measures its viewport card and
/// reports it through `set_viewport`, so the page always sits exactly under the pill.
#[derive(Debug, Clone, Copy)]
pub struct Viewport {
    pub x: f64,
    pub y: f64,
    pub width: f64,
    pub height: f64,
}

/// Emitted to the shell whenever a native page navigates or changes its title.
#[derive(Clone, Serialize)]
pub struct TabNavigated {
    pub tab_id: String,
    pub url: Option<String>,
    pub title: Option<String>,
}

/// Matches the shell layout (16px side gutters, 74px top strip, 16px bottom gutter)
/// until the frontend reports real measurements.
fn fallback_viewport(app: &AppHandle) -> Viewport {
    let (w, h) = app
        .get_window("main")
        .and_then(|win| {
            let scale = win.scale_factor().ok()?;
            let size = win.inner_size().ok()?.to_logical::<f64>(scale);
            Some((size.width, size.height))
        })
        .unwrap_or((1280.0, 800.0));
    Viewport {
        x: 16.0,
        y: 74.0,
        width: (w - 32.0).max(1.0),
        height: (h - 90.0).max(1.0),
    }
}

pub fn current_viewport(app: &AppHandle) -> Viewport {
    if let Some(state) = app.try_state::<AppState>() {
        if let Ok(guard) = state.viewport.lock() {
            if let Some(v) = *guard {
                return v;
            }
        }
    }
    fallback_viewport(app)
}

/// Spawns a native page view embedded in the main window (desktop only).
///
/// It is a *child webview* of the main window, not a separate OS window: it moves,
/// resizes and minimizes with Blanc and is positioned relative to the window.
#[cfg(desktop)]
pub fn create_tab_webview(app: &AppHandle, tab_id: &str, target_url: &str) -> Result<(), String> {
    let parsed_url: url::Url = target_url
        .parse()
        .map_err(|e| format!("Invalid URL: {}", e))?;
    let window = app
        .get_window("main")
        .ok_or_else(|| "Main window not found".to_string())?;
    let v = current_viewport(app);

    let nav_app = app.clone();
    let nav_id = tab_id.to_string();
    let title_app = app.clone();
    let title_id = tab_id.to_string();

    let builder = WebviewBuilder::new(tab_id, WebviewUrl::External(parsed_url))
        .on_navigation(move |url| {
            let _ = nav_app.emit(
                "tab-navigated",
                TabNavigated {
                    tab_id: nav_id.clone(),
                    url: Some(url.to_string()),
                    title: None,
                },
            );
            true
        })
        .on_document_title_changed(move |_webview, title| {
            let _ = title_app.emit(
                "tab-navigated",
                TabNavigated {
                    tab_id: title_id.clone(),
                    url: None,
                    title: Some(title),
                },
            );
        });

    window
        .add_child(
            builder,
            LogicalPosition::new(v.x, v.y),
            LogicalSize::new(v.width, v.height),
        )
        .map_err(|e| format!("Failed to create child webview: {}", e))?;

    Ok(())
}

/// Android/iOS cannot host extra webviews from Rust; the shell drives a native
/// Android `WebView` through the `BlancNative` bridge instead (see MainActivity.kt).
#[cfg(not(desktop))]
pub fn create_tab_webview(_app: &AppHandle, _tab_id: &str, _target_url: &str) -> Result<(), String> {
    Ok(())
}

/// Applies the reported viewport to the active tab's webview and hides every other
/// page view. `hidden` is true while an overlay (switcher, quick search) is open or
/// the active tab is an internal page, so React UI is never covered by a native view.
#[cfg(desktop)]
pub fn apply_viewport(
    app: &AppHandle,
    active_tab_id: Option<&str>,
    viewport: Viewport,
    hidden: bool,
) -> Result<(), String> {
    for (label, wv) in app.webviews() {
        if label == "main" {
            continue;
        }
        if Some(label.as_str()) == active_tab_id && !hidden {
            wv.set_position(LogicalPosition::new(viewport.x, viewport.y))
                .map_err(|e| format!("Failed to set webview position: {}", e))?;
            wv.set_size(LogicalSize::new(viewport.width, viewport.height))
                .map_err(|e| format!("Failed to set webview size: {}", e))?;
            wv.show()
                .map_err(|e| format!("Failed to show webview: {}", e))?;
        } else {
            let _ = wv.hide();
        }
    }
    Ok(())
}

#[cfg(not(desktop))]
pub fn apply_viewport(
    _app: &AppHandle,
    _active_tab_id: Option<&str>,
    _viewport: Viewport,
    _hidden: bool,
) -> Result<(), String> {
    Ok(())
}

/// Shows the given tab's webview and hides all the others without reloading pages.
#[cfg(desktop)]
pub fn switch_tab_webview(
    app: &AppHandle,
    active_tab_id: &str,
    previous_tab_id: Option<&str>,
) -> Result<(), String> {
    if let Some(prev_id) = previous_tab_id {
        if let Some(prev_wv) = app.get_webview(prev_id) {
            let _ = prev_wv.hide();
        }
    }

    if let Some(active_wv) = app.get_webview(active_tab_id) {
        let v = current_viewport(app);
        let _ = active_wv.set_position(LogicalPosition::new(v.x, v.y));
        let _ = active_wv.set_size(LogicalSize::new(v.width, v.height));
        active_wv
            .show()
            .map_err(|e| format!("Failed to show webview: {}", e))?;
        let _ = active_wv.set_focus();
        Ok(())
    } else {
        Err(format!("Webview for tab '{}' not found", active_tab_id))
    }
}

#[cfg(not(desktop))]
pub fn switch_tab_webview(
    _app: &AppHandle,
    _active_tab_id: &str,
    _previous_tab_id: Option<&str>,
) -> Result<(), String> {
    Ok(())
}

/// Closes and destroys the child webview instance
#[cfg(desktop)]
pub fn close_tab_webview(app: &AppHandle, tab_id: &str) -> Result<(), String> {
    if let Some(wv) = app.get_webview(tab_id) {
        wv.close()
            .map_err(|e| format!("Failed to close child webview: {}", e))?;
    }
    Ok(())
}

#[cfg(not(desktop))]
pub fn close_tab_webview(_app: &AppHandle, _tab_id: &str) -> Result<(), String> {
    Ok(())
}

/// Navigates the child webview to the specified URL
pub fn navigate_webview(app: &AppHandle, tab_id: &str, url: &str) -> Result<(), String> {
    if let Some(wv) = app.get_webview(tab_id) {
        match url.parse::<url::Url>() {
            Ok(parsed) => wv
                .navigate(parsed)
                .map_err(|e| format!("Failed to navigate: {}", e)),
            Err(_) => Err(format!("Invalid URL: {}", url)),
        }
    } else {
        Err(format!("Webview for tab '{}' not found", tab_id))
    }
}

/// Reloads the web page in the specified tab webview
pub fn reload_webview(app: &AppHandle, tab_id: &str) -> Result<(), String> {
    if let Some(wv) = app.get_webview(tab_id) {
        wv.reload()
            .map_err(|e| format!("Failed to reload: {}", e))?;
        Ok(())
    } else {
        Err(format!("Webview for tab '{}' not found", tab_id))
    }
}

/// Navigates backwards in the webview session history
pub fn go_back_webview(app: &AppHandle, tab_id: &str) -> Result<(), String> {
    if let Some(wv) = app.get_webview(tab_id) {
        wv.eval("window.history.back();")
            .map_err(|e| format!("Failed to go back: {}", e))?;
        Ok(())
    } else {
        Err(format!("Webview for tab '{}' not found", tab_id))
    }
}

/// Navigates forward in the webview session history
pub fn go_forward_webview(app: &AppHandle, tab_id: &str) -> Result<(), String> {
    if let Some(wv) = app.get_webview(tab_id) {
        wv.eval("window.history.forward();")
            .map_err(|e| format!("Failed to go forward: {}", e))?;
        Ok(())
    } else {
        Err(format!("Webview for tab '{}' not found", tab_id))
    }
}

/// Mobile-specific: Navigate the main WebView (the app's primary WebView on Android/iOS)
#[cfg(not(desktop))]
pub fn mobile_navigate_webview(app: &AppHandle, url: &str) -> Result<(), String> {
    // On mobile, the main WebView is the primary one - try to get it
    if let Some(wv) = app.get_webview("main") {
        match url.parse::<url::Url>() {
            Ok(parsed) => wv
                .navigate(parsed)
                .map_err(|e| format!("Failed to navigate main webview: {}", e)),
            Err(_) => Err(format!("Invalid URL: {}", url)),
        }
    } else {
        // Fallback: try to get any webview (the main app webview)
        for (_, wv) in app.webviews() {
            match url.parse::<url::Url>() {
                Ok(parsed) => {
                    return wv
                        .navigate(parsed)
                        .map_err(|e| format!("Failed to navigate main webview: {}", e));
                }
                Err(_) => return Err(format!("Invalid URL: {}", url)),
            }
        }
        Err("Main webview not found".to_string())
    }
}

/// Mobile-specific: Reload the main WebView
#[cfg(not(desktop))]
pub fn mobile_reload_webview(app: &AppHandle) -> Result<(), String> {
    if let Some(wv) = app.get_webview("main") {
        wv.reload()
            .map_err(|e| format!("Failed to reload main webview: {}", e))?;
        Ok(())
    } else {
        for (_, wv) in app.webviews() {
            wv.reload()
                .map_err(|e| format!("Failed to reload main webview: {}", e))?;
            return Ok(());
        }
        Err("Main webview not found".to_string())
    }
}

/// Mobile-specific: Go back in the main WebView history
#[cfg(not(desktop))]
pub fn mobile_go_back_webview(app: &AppHandle) -> Result<(), String> {
    if let Some(wv) = app.get_webview("main") {
        wv.eval("window.history.back();")
            .map_err(|e| format!("Failed to go back: {}", e))?;
        Ok(())
    } else {
        for (_, wv) in app.webviews() {
            wv.eval("window.history.back();")
                .map_err(|e| format!("Failed to go back: {}", e))?;
            return Ok(());
        }
        Err("Main webview not found".to_string())
    }
}

/// Mobile-specific: Go forward in the main WebView history
#[cfg(not(desktop))]
pub fn mobile_go_forward_webview(app: &AppHandle) -> Result<(), String> {
    if let Some(wv) = app.get_webview("main") {
        wv.eval("window.history.forward();")
            .map_err(|e| format!("Failed to go forward: {}", e))?;
        Ok(())
    } else {
        for (_, wv) in app.webviews() {
            wv.eval("window.history.forward();")
                .map_err(|e| format!("Failed to go forward: {}", e))?;
            return Ok(());
        }
        Err("Main webview not found".to_string())
    }
}

/// Mobile-specific: Check if main WebView can go back
#[cfg(not(desktop))]
pub fn mobile_can_go_back(app: &AppHandle) -> Result<bool, String> {
    // This would need JavaScript injection to check history.length > 1
    // For now, return true to enable the button
    Ok(true)
}

/// Mobile-specific: Check if main WebView can go forward
#[cfg(not(desktop))]
pub fn mobile_can_go_forward(app: &AppHandle) -> Result<bool, String> {
    Ok(true)
}

// Desktop stubs for mobile functions
#[cfg(desktop)]
pub fn mobile_navigate_webview(_app: &AppHandle, _url: &str) -> Result<(), String> {
    Ok(())
}

#[cfg(desktop)]
pub fn mobile_reload_webview(_app: &AppHandle) -> Result<(), String> {
    Ok(())
}

#[cfg(desktop)]
pub fn mobile_go_back_webview(_app: &AppHandle) -> Result<(), String> {
    Ok(())
}

#[cfg(desktop)]
pub fn mobile_go_forward_webview(_app: &AppHandle) -> Result<(), String> {
    Ok(())
}

#[cfg(desktop)]
pub fn mobile_can_go_back(_app: &AppHandle) -> Result<bool, String> {
    Ok(false)
}

#[cfg(desktop)]
pub fn mobile_can_go_forward(_app: &AppHandle) -> Result<bool, String> {
    Ok(false)
}

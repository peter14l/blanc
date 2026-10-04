pub mod adblock;
pub mod state;
pub mod storage;
pub mod webview;

use std::sync::Mutex;
use tauri::{AppHandle, Manager, State, WindowEvent};
use state::{BrowserState, Tab};
use storage::{Bookmark, HistoryEntry, StorageManager, UserSettings};
use adblock::AdblockEngine;

pub struct AppState {
    pub browser: Mutex<BrowserState>,
    pub storage: StorageManager,
    pub adblock: Mutex<AdblockEngine>,
}

#[tauri::command]
fn create_tab(
    app: AppHandle,
    state: State<'_, AppState>,
    url: Option<String>,
) -> Result<Tab, String> {
    let mut browser = state.browser.lock().map_err(|e| e.to_string())?;
    let previous_active = browser.active_tab_id.clone();
    let tab = browser.add_tab(url.clone());
    let tab_id = tab.id.clone();
    let target_url = tab.url.clone();

    // Default window dimensions if not queryable
    let (win_width, win_height) = if let Some(main_win) = app.get_webview_window("main") {
        if let Ok(size) = main_win.inner_size() {
            (size.width, size.height)
        } else {
            (1280, 800)
        }
    } else {
        (1280, 800)
    };

    // Hide previous webview if any
    if let Some(prev_id) = previous_active.as_deref() {
        if let Some(prev_wv) = app.get_webview_window(prev_id) {
            let _ = prev_wv.hide();
        }
    }

    // Spawn child webview if not an internal blanc:// page
    if !target_url.starts_with("blanc://") && target_url != "about:blank" {
        let _ = webview::create_tab_webview(&app, &tab_id, &target_url, win_width, win_height);
    }

    // Automatically record history
    if !target_url.starts_with("blanc://") && target_url != "about:blank" {
        let _ = state.storage.add_history(target_url, tab.title.clone());
    }

    Ok(tab)
}

#[tauri::command]
fn close_tab(
    app: AppHandle,
    state: State<'_, AppState>,
    tab_id: String,
) -> Result<String, String> {
    let mut browser = state.browser.lock().map_err(|e| e.to_string())?;
    let _ = webview::close_tab_webview(&app, &tab_id);

    browser.remove_tab(&tab_id);

    // If there is a new active tab, show it
    if let Some(active_id) = browser.active_tab_id.as_deref() {
        let _ = webview::switch_tab_webview(&app, active_id, None);
    }

    Ok(tab_id)
}

#[tauri::command]
fn switch_tab(
    app: AppHandle,
    state: State<'_, AppState>,
    tab_id: String,
) -> Result<(), String> {
    let mut browser = state.browser.lock().map_err(|e| e.to_string())?;
    let previous_id = browser.active_tab_id.clone();

    browser.set_active_tab(&tab_id)?;
    let _ = webview::switch_tab_webview(&app, &tab_id, previous_id.as_deref());

    Ok(())
}

#[tauri::command]
fn navigate(
    app: AppHandle,
    state: State<'_, AppState>,
    tab_id: String,
    url: String,
) -> Result<(), String> {
    let mut browser = state.browser.lock().map_err(|e| e.to_string())?;
    
    // Normalize input (search query vs. URL vs. internal page)
    let target_url = if url.starts_with("blanc://") || url.starts_with("about:") {
        url
    } else if url.starts_with("http://") || url.starts_with("https://") {
        url
    } else if url.contains('.') && !url.contains(' ') {
        format!("https://{}", url)
    } else {
        let settings = state.storage.load().settings;
        match settings.search_engine.as_str() {
            "google" => format!("https://www.google.com/search?q={}", urlencoding::encode(&url)),
            "bing" => format!("https://www.bing.com/search?q={}", urlencoding::encode(&url)),
            "ecosia" => format!("https://www.ecosia.org/search?q={}", urlencoding::encode(&url)),
            "kagi" => format!("https://kagi.com/search?q={}", urlencoding::encode(&url)),
            _ => format!("https://duckduckgo.com/?q={}", urlencoding::encode(&url)),
        }
    };

    browser.update_tab_url(&tab_id, target_url.clone())?;

    // Check if URL is an ad/tracker
    {
        let adblock = state.adblock.lock().map_err(|e| e.to_string())?;
        if adblock.is_blocked(&target_url) {
            let _ = browser.increment_trackers(&tab_id, 1);
        }
    }

    if !target_url.starts_with("blanc://") && target_url != "about:blank" {
        // If webview doesn't exist yet for this tab, create it
        if app.get_webview_window(&tab_id).is_none() {
            let (win_width, win_height) = if let Some(main_win) = app.get_webview_window("main") {
                if let Ok(size) = main_win.inner_size() {
                    (size.width, size.height)
                } else {
                    (1280, 800)
                }
            } else {
                (1280, 800)
            };
            let _ = webview::create_tab_webview(&app, &tab_id, &target_url, win_width, win_height);
        } else {
            webview::navigate_webview(&app, &tab_id, &target_url)?;
        }
        let _ = state.storage.add_history(target_url, "".to_string());
    } else {
        // If internal page, hide child webview so React UI shows internal page
        if let Some(wv) = app.get_webview_window(&tab_id) {
            let _ = wv.hide();
        }
    }

    Ok(())
}

#[tauri::command]
fn reload_tab(app: AppHandle, tab_id: String) -> Result<(), String> {
    webview::reload_webview(&app, &tab_id)
}

#[tauri::command]
fn go_back(app: AppHandle, tab_id: String) -> Result<(), String> {
    webview::go_back_webview(&app, &tab_id)
}

#[tauri::command]
fn go_forward(app: AppHandle, tab_id: String) -> Result<(), String> {
    webview::go_forward_webview(&app, &tab_id)
}

#[tauri::command]
fn get_tabs(state: State<'_, AppState>) -> Result<Vec<Tab>, String> {
    let browser = state.browser.lock().map_err(|e| e.to_string())?;
    Ok(browser.get_tabs())
}

#[tauri::command]
fn get_active_tab(state: State<'_, AppState>) -> Result<Option<Tab>, String> {
    let browser = state.browser.lock().map_err(|e| e.to_string())?;
    Ok(browser.get_active_tab())
}

#[tauri::command]
fn update_window_bounds(
    app: AppHandle,
    state: State<'_, AppState>,
    width: u32,
    height: u32,
) -> Result<(), String> {
    let browser = state.browser.lock().map_err(|e| e.to_string())?;
    if let Some(active_id) = browser.active_tab_id.as_deref() {
        webview::resize_active_webview(&app, active_id, width, height)?;
    }
    Ok(())
}

#[tauri::command]
fn minimize_window(app: AppHandle) -> Result<(), String> {
    if let Some(window) = app.get_webview_window("main") {
        window.minimize().map_err(|e| e.to_string())?;
    }
    Ok(())
}

#[tauri::command]
fn maximize_window(app: AppHandle) -> Result<(), String> {
    if let Some(window) = app.get_webview_window("main") {
        if window.is_maximized().unwrap_or(false) {
            window.unmaximize().map_err(|e| e.to_string())?;
        } else {
            window.maximize().map_err(|e| e.to_string())?;
        }
    }
    Ok(())
}

#[tauri::command]
fn close_window(app: AppHandle) -> Result<(), String> {
    if let Some(window) = app.get_webview_window("main") {
        window.close().map_err(|e| e.to_string())?;
    }
    Ok(())
}

// STORAGE COMMANDS
#[tauri::command]
fn get_history(state: State<'_, AppState>) -> Result<Vec<HistoryEntry>, String> {
    Ok(state.storage.load().history)
}

#[tauri::command]
fn add_history_entry(
    state: State<'_, AppState>,
    url: String,
    title: String,
) -> Result<HistoryEntry, String> {
    state.storage.add_history(url, title)
}

#[tauri::command]
fn clear_history(state: State<'_, AppState>) -> Result<(), String> {
    state.storage.clear_history()
}

#[tauri::command]
fn get_bookmarks(state: State<'_, AppState>) -> Result<Vec<Bookmark>, String> {
    Ok(state.storage.load().bookmarks)
}

#[tauri::command]
fn add_bookmark(state: State<'_, AppState>, bookmark: Bookmark) -> Result<Bookmark, String> {
    state.storage.add_bookmark(bookmark)
}

#[tauri::command]
fn remove_bookmark(state: State<'_, AppState>, id: String) -> Result<(), String> {
    state.storage.remove_bookmark(&id)
}

#[tauri::command]
fn get_settings(state: State<'_, AppState>) -> Result<UserSettings, String> {
    Ok(state.storage.load().settings)
}

#[tauri::command]
fn save_settings(
    state: State<'_, AppState>,
    settings: UserSettings,
) -> Result<UserSettings, String> {
    state.storage.save_settings(settings)
}

// ADBLOCK COMMANDS
#[derive(serde::Serialize)]
struct AdblockStatsPayload {
    total_blocked: u64,
    enabled: bool,
}

#[tauri::command]
fn get_adblock_stats(state: State<'_, AppState>) -> Result<AdblockStatsPayload, String> {
    let adblock = state.adblock.lock().map_err(|e| e.to_string())?;
    Ok(AdblockStatsPayload {
        total_blocked: adblock.get_total_blocked(),
        enabled: adblock.is_enabled(),
    })
}

#[tauri::command]
fn toggle_adblock(state: State<'_, AppState>, enabled: bool) -> Result<bool, String> {
    let mut adblock = state.adblock.lock().map_err(|e| e.to_string())?;
    adblock.set_enabled(enabled);
    Ok(enabled)
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    let storage = StorageManager::new();
    let initial_browser = BrowserState::new();

    let initial_state = AppState {
        browser: Mutex::new(initial_browser),
        storage,
        adblock: Mutex::new(AdblockEngine::new()),
    };

    tauri::Builder::default()
        .manage(initial_state)
        .invoke_handler(tauri::generate_handler![
            create_tab,
            close_tab,
            switch_tab,
            navigate,
            reload_tab,
            go_back,
            go_forward,
            get_tabs,
            get_active_tab,
            update_window_bounds,
            minimize_window,
            maximize_window,
            close_window,
            get_history,
            add_history_entry,
            clear_history,
            get_bookmarks,
            add_bookmark,
            remove_bookmark,
            get_settings,
            save_settings,
            get_adblock_stats,
            toggle_adblock,
        ])
        .on_window_event(|window, event| {
            if let WindowEvent::Resized(size) = event {
                let app = window.app_handle();
                if let Some(state) = app.try_state::<AppState>() {
                    if let Ok(browser) = state.browser.lock() {
                        if let Some(active_id) = browser.active_tab_id.as_deref() {
                            let _ = webview::resize_active_webview(
                                app,
                                active_id,
                                size.width,
                                size.height,
                            );
                        }
                    }
                }
            }
        })
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}

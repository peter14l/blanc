pub mod adblock;
pub mod credentials;
pub mod downloads;
pub mod mica;
pub mod model;
pub mod navigation;
pub mod patron;
pub mod permissions;
pub mod proxy;
pub mod sleep;
pub mod state;
pub mod storage;
pub mod store;
pub mod sync;
pub mod webview;
pub mod workspaces;

use std::sync::Mutex;
use tauri::{AppHandle, Emitter, Manager, State};
use state::BrowserState;
use model::{GroupRecord, SessionPartition, StateProjection, TabRecord, WindowProjection, PERSONAL_PROFILE};
use storage::{Bookmark, Favorite, HistoryEntry, HistoryPage, StorageManager, UserSettings};
use adblock::{AdblockEngine, BlockingStatus};
use permissions::{PermissionBroker, PermissionDecision, PermissionDecisionRecord, PermissionResource};
use downloads::{DownloadItem, DownloadManager};
use workspaces::{ProfileRecord, WorkspaceRecord, WorkspacesManager};
use patron::{PatronState, PatronStatus};
use credentials::{CredentialBroker, FillStatus};
use sync::{SyncEligibility, SyncableData, validate_sync_payload};
use proxy::proxy_fetch;

pub struct AppState {
    pub browser: Mutex<BrowserState>,
    pub storage: StorageManager,
    pub adblock: Mutex<AdblockEngine>,
    pub permissions: Mutex<PermissionBroker>,
    pub downloads: Mutex<DownloadManager>,
    pub workspaces: Mutex<WorkspacesManager>,
    pub patron: Mutex<PatronState>,
    pub credentials: Mutex<CredentialBroker>,
    /// Last page-card rectangle reported by the shell (logical px); None until first report.
    pub viewport: Mutex<Option<webview::Viewport>>,
}

pub fn log_msg(msg: &str) {
    let mut dir = dirs::data_local_dir().unwrap_or_else(|| std::path::PathBuf::from("."));
    dir.push("blanc-tauri");
    let _ = std::fs::create_dir_all(&dir);
    let path = dir.join("blanc_debug.log");
    if let Ok(mut f) = std::fs::OpenOptions::new().create(true).append(true).open(path) {
        use std::io::Write;
        let _ = writeln!(f, "[{}] {}", chrono::Local::now().format("%Y-%m-%d %H:%M:%S%.3f"), msg);
    }
}

fn emit_state_projection(app: &AppHandle, browser: &BrowserState) {
    let proj = browser.projection();
    let _ = app.emit("blanc:state-updated", proj);
}

// -----------------------------------------------------------------------------
// STATE & WINDOW PROJECTIONS
// -----------------------------------------------------------------------------

#[tauri::command]
fn get_state_projection(state: State<'_, AppState>) -> Result<StateProjection, String> {
    let browser = state.browser.lock().map_err(|e| e.to_string())?;
    Ok(browser.projection())
}

#[tauri::command]
fn get_window_projection(
    state: State<'_, AppState>,
    window: Option<String>,
) -> Result<Option<WindowProjection>, String> {
    let browser = state.browser.lock().map_err(|e| e.to_string())?;
    let label = window.as_deref().unwrap_or("main");
    Ok(browser.window_projection(label))
}

#[tauri::command]
fn list_windows(state: State<'_, AppState>) -> Result<Vec<WindowProjection>, String> {
    let browser = state.browser.lock().map_err(|e| e.to_string())?;
    Ok(browser.projection().windows)
}

#[tauri::command]
fn get_tabs(
    state: State<'_, AppState>,
    window: Option<String>,
) -> Result<Vec<TabRecord>, String> {
    let browser = state.browser.lock().map_err(|e| e.to_string())?;
    let label = window.as_deref().unwrap_or("main");
    Ok(browser.get_tabs_for_window(label))
}

#[tauri::command]
fn get_active_tab(
    state: State<'_, AppState>,
    window: Option<String>,
) -> Result<Option<TabRecord>, String> {
    let browser = state.browser.lock().map_err(|e| e.to_string())?;
    let label = window.as_deref().unwrap_or("main");
    Ok(browser.get_active_tab_for_window(label))
}

// -----------------------------------------------------------------------------
// TAB COMMANDS
// -----------------------------------------------------------------------------

#[tauri::command]
async fn create_tab(
    app: AppHandle,
    state: State<'_, AppState>,
    window: Option<String>,
    url: Option<String>,
    private: Option<bool>,
    group: Option<String>,
) -> Result<TabRecord, String> {
    let window_label = window.unwrap_or_else(|| "main".to_string());
    let is_private = private.unwrap_or(false);

    let (tab, prev_active) = {
        let mut browser = state.browser.lock().map_err(|e| e.to_string())?;
        let prev = browser
            .windows
            .get(&window_label)
            .and_then(|w| w.active_tab_id.clone());
        let tab = browser.create_tab(&window_label, url, is_private, group)?;
        emit_state_projection(&app, &browser);
        (tab, prev)
    };

    let tab_id = tab.id.clone();
    let target_url = tab.url.clone();

    // Hide previous webview if any
    #[cfg(desktop)]
    if let Some(prev_id) = prev_active.as_deref() {
        if let Some(prev_wv) = app.get_webview(prev_id) {
            let _ = prev_wv.hide();
        }
    }

    // Spawn child webview if not an internal blanc:// page
    if !target_url.starts_with("blanc://") && target_url != "about:blank" {
        let app_handle = app.clone();
        let id_clone = tab_id.clone();
        let url_clone = target_url.clone();
        let _ = tauri::async_runtime::spawn_blocking(move || {
            webview::create_tab_webview(&app_handle, &id_clone, &url_clone)
        }).await;
    }

    // Automatically record history (non-private only)
    if !tab.is_private() && !target_url.starts_with("blanc://") && target_url != "about:blank" {
        let _ = state.storage.add_history(target_url, tab.title.clone());
    }

    Ok(tab)
}

#[tauri::command]
fn close_tab(
    app: AppHandle,
    state: State<'_, AppState>,
    tab_id: String,
) -> Result<Option<TabRecord>, String> {
    let _ = webview::close_tab_webview(&app, &tab_id);

    let mut browser = state.browser.lock().map_err(|e| e.to_string())?;
    let next_active = browser.close_tab(&tab_id)?;
    emit_state_projection(&app, &browser);

    // If there is a new active tab, show its webview
    if let Some(ref active_tab) = next_active {
        let _ = webview::switch_tab_webview(&app, &active_tab.id, None);
    }

    Ok(next_active)
}

#[tauri::command]
fn close_all_tabs(
    app: AppHandle,
    state: State<'_, AppState>,
    window: Option<String>,
) -> Result<(), String> {
    let window_label = window.unwrap_or_else(|| "main".to_string());
    let mut browser = state.browser.lock().map_err(|e| e.to_string())?;

    if let Some(w) = browser.windows.get(&window_label) {
        for id in &w.tab_order {
            let _ = webview::close_tab_webview(&app, id);
        }
    }

    browser.close_all_tabs(&window_label)?;
    emit_state_projection(&app, &browser);
    Ok(())
}

#[tauri::command]
async fn switch_tab(
    app: AppHandle,
    state: State<'_, AppState>,
    tab_id: String,
) -> Result<(), String> {
    let prev_id = {
        let mut browser = state.browser.lock().map_err(|e| e.to_string())?;
        let window_label = browser
            .tabs
            .get(&tab_id)
            .map(|t| t.window_id.clone())
            .ok_or_else(|| format!("Tab '{}' not found", tab_id))?;

        let prev = browser
            .windows
            .get(&window_label)
            .and_then(|w| w.active_tab_id.clone());

        browser.switch_tab(&tab_id)?;
        emit_state_projection(&app, &browser);
        prev
    };

    let _ = webview::switch_tab_webview(&app, &tab_id, prev_id.as_deref());
    Ok(())
}

#[tauri::command]
async fn navigate(
    app: AppHandle,
    state: State<'_, AppState>,
    tab_id: String,
    url: String,
    _private: Option<bool>,
) -> Result<(), String> {
    log_msg(&format!("navigate called: tab_id={}, url={}", tab_id, url));
    let search_engine = state.storage.load().settings.search_engine;
    let target_url = navigation::normalize_navigation_target(&url, &search_engine)?;

    let decision = navigation::admit_top_level_navigation(&target_url);

    match decision {
        navigation::NavigationDecision::Allow { url: allowed_url } => {
            log_msg(&format!("Navigation allowed: target={}", allowed_url));
            let is_private = {
                let mut browser = state.browser.lock().map_err(|e| e.to_string())?;
                if browser.tabs.get(&tab_id).is_none() {
                    log_msg(&format!("Tab '{}' not found in browser state; creating/adopting it", tab_id));
                    let window_label = "main";
                    let _ = browser.ensure_window(window_label, Some(PERSONAL_PROFILE.to_string()), false);
                    let partition = SessionPartition::Profile(PERSONAL_PROFILE.to_string());
                    let mut new_tab = TabRecord::new(window_label.to_string(), allowed_url.clone(), partition);
                    new_tab.id = tab_id.clone();
                    browser.tabs.insert(tab_id.clone(), new_tab);
                    if let Some(w) = browser.windows.get_mut(window_label) {
                        if !w.tab_order.contains(&tab_id) {
                            w.tab_order.push(tab_id.clone());
                        }
                        w.active_tab_id = Some(tab_id.clone());
                    }
                } else {
                    browser.update_tab_navigation(&tab_id, Some(allowed_url.clone()), None)?;
                    if let Some(tab) = browser.tabs.get(&tab_id) {
                        let win_id = tab.window_id.clone();
                        if let Some(w) = browser.windows.get_mut(&win_id) {
                            w.active_tab_id = Some(tab_id.clone());
                        }
                    }
                }

                // Check if URL is an ad/tracker
                {
                    if let Ok(adblock) = state.adblock.lock() {
                        if adblock.should_block(&allowed_url, None, "document") {
                            let _ = browser.increment_tab_blocked(&tab_id, 1);
                        }
                    }
                }
                emit_state_projection(&app, &browser);

                browser
                    .tabs
                    .get(&tab_id)
                    .map(|t| t.is_private())
                    .unwrap_or(false)
            };

            if !allowed_url.starts_with("blanc://") && allowed_url != "about:blank" {
                if app.get_webview(&tab_id).is_none() {
                    log_msg(&format!("Creating child webview for '{}' -> '{}'", tab_id, allowed_url));
                    let app_handle = app.clone();
                    let id_clone = tab_id.clone();
                    let url_clone = allowed_url.clone();
                    let res = tauri::async_runtime::spawn_blocking(move || {
                        webview::create_tab_webview(&app_handle, &id_clone, &url_clone)
                    }).await.map_err(|e| e.to_string())?;

                    if let Err(err) = res {
                        log_msg(&format!("create_tab_webview failed: {}", err));
                        eprintln!("[blanc] create_tab_webview failed: {}", err);
                        return Err(err);
                    } else {
                        log_msg(&format!("create_tab_webview succeeded for '{}'", tab_id));
                    }
                } else {
                    log_msg(&format!("Navigating existing webview '{}' -> '{}'", tab_id, allowed_url));
                    webview::navigate_webview(&app, &tab_id, &allowed_url)?;
                    let v = webview::current_viewport(&app);
                    let _ = webview::apply_viewport(&app, Some(&tab_id), v, false);
                }
                // Only record history for non-private tabs
                if !is_private {
                    let _ = state.storage.add_history(allowed_url, "".to_string());
                }
            } else {
                #[cfg(desktop)]
                if let Some(wv) = app.get_webview(&tab_id) {
                    let _ = wv.hide();
                }
            }
        }
        navigation::NavigationDecision::ShowInternal { url: internal_url, .. } => {
            log_msg(&format!("Navigation internal: {}", internal_url));
            {
                let mut browser = state.browser.lock().map_err(|e| e.to_string())?;
                if browser.tabs.get(&tab_id).is_none() {
                    let window_label = "main";
                    let _ = browser.ensure_window(window_label, Some(PERSONAL_PROFILE.to_string()), false);
                    let partition = SessionPartition::Profile(PERSONAL_PROFILE.to_string());
                    let mut new_tab = TabRecord::new(window_label.to_string(), internal_url.clone(), partition);
                    new_tab.id = tab_id.clone();
                    browser.tabs.insert(tab_id.clone(), new_tab);
                    if let Some(w) = browser.windows.get_mut(window_label) {
                        if !w.tab_order.contains(&tab_id) {
                            w.tab_order.push(tab_id.clone());
                        }
                        w.active_tab_id = Some(tab_id.clone());
                    }
                } else {
                    browser.update_tab_navigation(&tab_id, Some(internal_url), None)?;
                }
                emit_state_projection(&app, &browser);
            }

            #[cfg(desktop)]
            if let Some(wv) = app.get_webview(&tab_id) {
                let _ = wv.hide();
            }
        }
        navigation::NavigationDecision::OpenExternal { url: ext_url, .. } => {
            #[cfg(desktop)]
            {
                #[cfg(target_os = "windows")]
                let _ = std::process::Command::new("cmd").args(["/C", "start", "", &ext_url]).spawn();
                #[cfg(target_os = "macos")]
                let _ = std::process::Command::new("open").arg(&ext_url).spawn();
                #[cfg(target_os = "linux")]
                let _ = std::process::Command::new("xdg-open").arg(&ext_url).spawn();
            }
        }
        navigation::NavigationDecision::Deny { reason } => {
            return Err(reason);
        }
    }

    Ok(())
}

#[tauri::command]
async fn duplicate_tab(
    app: AppHandle,
    state: State<'_, AppState>,
    tab_id: String,
) -> Result<TabRecord, String> {
    let dup = {
        let mut browser = state.browser.lock().map_err(|e| e.to_string())?;
        let dup = browser.duplicate_tab(&tab_id)?;
        emit_state_projection(&app, &browser);
        dup
    };

    if !dup.url.starts_with("blanc://") && dup.url != "about:blank" {
        let app_handle = app.clone();
        let id_clone = dup.id.clone();
        let url_clone = dup.url.clone();
        let _ = tauri::async_runtime::spawn_blocking(move || {
            webview::create_tab_webview(&app_handle, &id_clone, &url_clone)
        }).await;
    }
    Ok(dup)
}

#[tauri::command]
fn set_tab_pinned(
    app: AppHandle,
    state: State<'_, AppState>,
    tab_id: String,
    pinned: bool,
) -> Result<(), String> {
    let mut browser = state.browser.lock().map_err(|e| e.to_string())?;
    browser.set_tab_pinned(&tab_id, pinned)?;
    emit_state_projection(&app, &browser);
    Ok(())
}

#[tauri::command]
fn set_tab_muted(
    app: AppHandle,
    state: State<'_, AppState>,
    tab_id: String,
    muted: bool,
) -> Result<(), String> {
    let mut browser = state.browser.lock().map_err(|e| e.to_string())?;
    browser.set_tab_muted(&tab_id, muted)?;
    emit_state_projection(&app, &browser);
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

// -----------------------------------------------------------------------------
// MOBILE MAIN WEBVIEW NAVIGATION
// -----------------------------------------------------------------------------

#[tauri::command]
fn mobile_navigate(app: AppHandle, url: String) -> Result<(), String> {
    webview::mobile_navigate_webview(&app, &url)
}

#[tauri::command]
fn mobile_reload(app: AppHandle) -> Result<(), String> {
    webview::mobile_reload_webview(&app)
}

#[tauri::command]
fn mobile_go_back(app: AppHandle) -> Result<(), String> {
    webview::mobile_go_back_webview(&app)
}

#[tauri::command]
fn mobile_go_forward(app: AppHandle) -> Result<(), String> {
    webview::mobile_go_forward_webview(&app)
}

#[tauri::command]
fn mobile_can_go_back(app: AppHandle) -> Result<bool, String> {
    webview::mobile_can_go_back(&app)
}

#[tauri::command]
fn mobile_can_go_forward(app: AppHandle) -> Result<bool, String> {
    webview::mobile_can_go_forward(&app)
}

#[tauri::command]
fn find_in_page(
    app: AppHandle,
    state: State<'_, AppState>,
    tab_id: Option<String>,
    query: String,
    forward: bool,
) -> Result<webview::FindResult, String> {
    let resolved_tab = match tab_id {
        Some(t) => t,
        None => {
            let browser = state.browser.lock().map_err(|e| e.to_string())?;
            browser.get_active_tab_for_window("main")
                .map(|t| t.id)
                .ok_or_else(|| "No active tab".to_string())?
        }
    };
    webview::find_in_page(&app, &resolved_tab, &query, forward)
}

// -----------------------------------------------------------------------------
// TAB GROUPS
// -----------------------------------------------------------------------------

#[tauri::command]
fn create_group(
    app: AppHandle,
    state: State<'_, AppState>,
    window: Option<String>,
    name: String,
) -> Result<GroupRecord, String> {
    let window_label = window.unwrap_or_else(|| "main".to_string());
    let mut browser = state.browser.lock().map_err(|e| e.to_string())?;
    let group = browser.create_group(&window_label, name)?;
    emit_state_projection(&app, &browser);
    Ok(group)
}

#[tauri::command]
fn rename_group(
    app: AppHandle,
    state: State<'_, AppState>,
    group_id: String,
    name: String,
) -> Result<(), String> {
    let mut browser = state.browser.lock().map_err(|e| e.to_string())?;
    browser.rename_group(&group_id, name)?;
    emit_state_projection(&app, &browser);
    Ok(())
}

#[tauri::command]
fn set_group_collapsed(
    app: AppHandle,
    state: State<'_, AppState>,
    group_id: String,
    collapsed: bool,
) -> Result<(), String> {
    let mut browser = state.browser.lock().map_err(|e| e.to_string())?;
    browser.set_group_collapsed(&group_id, collapsed)?;
    emit_state_projection(&app, &browser);
    Ok(())
}

#[tauri::command]
fn move_tab_to_group(
    app: AppHandle,
    state: State<'_, AppState>,
    tab_id: String,
    group: Option<String>,
) -> Result<(), String> {
    let mut browser = state.browser.lock().map_err(|e| e.to_string())?;
    browser.move_tab_to_group(&tab_id, group)?;
    emit_state_projection(&app, &browser);
    Ok(())
}

#[tauri::command]
fn close_group(
    app: AppHandle,
    state: State<'_, AppState>,
    group_id: String,
) -> Result<(), String> {
    let mut browser = state.browser.lock().map_err(|e| e.to_string())?;
    browser.close_group(&group_id)?;
    emit_state_projection(&app, &browser);
    Ok(())
}

// -----------------------------------------------------------------------------
// CLOSED TABS RECOVERY
// -----------------------------------------------------------------------------

#[tauri::command]
async fn reopen_closed_tab(
    app: AppHandle,
    state: State<'_, AppState>,
    entry_id: Option<String>,
    window: Option<String>,
) -> Result<Option<TabRecord>, String> {
    let window_label = window.unwrap_or_else(|| "main".to_string());
    let tab = {
        let mut browser = state.browser.lock().map_err(|e| e.to_string())?;
        let tab = match entry_id {
            Some(ref id) if !id.is_empty() && id != "last" => {
                browser.reopen_closed_tab(id, &window_label)?
            }
            _ => browser.reopen_last_closed_tab(&window_label)?,
        };
        emit_state_projection(&app, &browser);
        tab
    };

    if let Some(ref t) = tab {
        if !t.url.starts_with("blanc://") && t.url != "about:blank" {
            let app_handle = app.clone();
            let id_clone = t.id.clone();
            let url_clone = t.url.clone();
            let _ = tauri::async_runtime::spawn_blocking(move || {
                webview::create_tab_webview(&app_handle, &id_clone, &url_clone)
            }).await;
        }
    }
    Ok(tab)
}

#[tauri::command]
fn forget_closed_tab(
    app: AppHandle,
    state: State<'_, AppState>,
    entry_id: String,
) -> Result<(), String> {
    let mut browser = state.browser.lock().map_err(|e| e.to_string())?;
    browser.forget_closed_tab(&entry_id)?;
    emit_state_projection(&app, &browser);
    Ok(())
}

#[tauri::command]
fn clear_closed_tabs(
    app: AppHandle,
    state: State<'_, AppState>,
    window: Option<String>,
) -> Result<(), String> {
    let window_label = window.unwrap_or_else(|| "main".to_string());
    let mut browser = state.browser.lock().map_err(|e| e.to_string())?;
    browser.clear_closed_tabs(&window_label)?;
    emit_state_projection(&app, &browser);
    Ok(())
}

// -----------------------------------------------------------------------------
// VIEWPORT & WINDOWS
// -----------------------------------------------------------------------------

#[tauri::command]
async fn set_viewport(
    app: AppHandle,
    state: State<'_, AppState>,
    x: f64,
    y: f64,
    width: f64,
    height: f64,
    hidden: bool,
) -> Result<(), String> {
    let viewport = webview::Viewport {
        x,
        y,
        width: width.max(1.0),
        height: height.max(1.0),
    };
    *state.viewport.lock().map_err(|e| e.to_string())? = Some(viewport);

    let active_id = {
        state
            .browser
            .lock()
            .map_err(|e| e.to_string())?
            .get_window("main")
            .and_then(|w| w.active_tab_id.clone())
    };
    webview::apply_viewport(&app, active_id.as_deref(), viewport, hidden)
}

#[tauri::command]
fn minimize_window(app: AppHandle) -> Result<(), String> {
    #[cfg(desktop)]
    if let Some(window) = app.get_webview_window("main") {
        window.minimize().map_err(|e| e.to_string())?;
    }
    #[cfg(not(desktop))]
    let _ = app;
    Ok(())
}

#[tauri::command]
fn maximize_window(app: AppHandle) -> Result<(), String> {
    #[cfg(desktop)]
    if let Some(window) = app.get_webview_window("main") {
        if window.is_maximized().unwrap_or(false) {
            window.unmaximize().map_err(|e| e.to_string())?;
        } else {
            window.maximize().map_err(|e| e.to_string())?;
        }
    }
    #[cfg(not(desktop))]
    let _ = app;
    Ok(())
}

#[tauri::command]
fn close_window(app: AppHandle) -> Result<(), String> {
    #[cfg(desktop)]
    if let Some(window) = app.get_webview_window("main") {
        window.close().map_err(|e| e.to_string())?;
    }
    #[cfg(not(desktop))]
    let _ = app;
    Ok(())
}

// -----------------------------------------------------------------------------
// STORAGE COMMANDS (HISTORY, BOOKMARKS, FAVORITES, SETTINGS)
// -----------------------------------------------------------------------------

#[tauri::command]
fn get_history(state: State<'_, AppState>) -> Result<Vec<HistoryEntry>, String> {
    Ok(state.storage.load().history)
}

#[tauri::command]
fn history_list(
    state: State<'_, AppState>,
    limit: Option<u32>,
    offset: Option<u32>,
    query: Option<String>,
) -> Result<HistoryPage, String> {
    let lim = limit.unwrap_or(50);
    let off = offset.unwrap_or(0);
    Ok(state.storage.history_list(lim, off, query))
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
fn history_record(
    state: State<'_, AppState>,
    url: String,
    title: String,
) -> Result<HistoryEntry, String> {
    state.storage.add_history(url, title)
}

#[tauri::command]
fn history_remove(state: State<'_, AppState>, id: String) -> Result<(), String> {
    state.storage.history_remove(&id)
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
fn favorites_list(state: State<'_, AppState>) -> Result<Vec<Favorite>, String> {
    Ok(state.storage.favorites_list())
}

#[tauri::command]
fn favorites_add(state: State<'_, AppState>, favorite: Favorite) -> Result<Favorite, String> {
    state.storage.favorites_add(favorite)
}

#[tauri::command]
fn favorites_update(
    state: State<'_, AppState>,
    id: String,
    favorite: Favorite,
) -> Result<Favorite, String> {
    state.storage.favorites_update(&id, favorite)
}

#[tauri::command]
fn favorites_remove(state: State<'_, AppState>, id: String) -> Result<(), String> {
    state.storage.favorites_remove(&id)
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

#[tauri::command]
fn get_sync_eligibility() -> Result<SyncEligibility, String> {
    Ok(SyncEligibility {
        eligible: true,
        reason: None,
    })
}

#[tauri::command]
fn sync_get_payload(
    state: State<'_, AppState>,
    profile_id: Option<String>,
) -> Result<SyncableData, String> {
    let profile = profile_id.unwrap_or_else(|| PERSONAL_PROFILE.to_string());
    let favs = state.storage.favorites_list();
    let settings = state.storage.load().settings;
    let workspaces = state
        .workspaces
        .lock()
        .map_err(|e| e.to_string())?
        .list_workspaces(&profile);

    let payload = SyncableData::new(profile, favs, settings, workspaces);
    validate_sync_payload(&payload).map_err(|e| e.to_string())?;
    Ok(payload)
}

// -----------------------------------------------------------------------------
// ADBLOCK COMMANDS
// -----------------------------------------------------------------------------

#[tauri::command]
fn adblock_status(state: State<'_, AppState>) -> Result<BlockingStatus, String> {
    let adblock = state.adblock.lock().map_err(|e| e.to_string())?;
    Ok(adblock.get_status())
}

#[tauri::command]
fn get_adblock_stats(state: State<'_, AppState>) -> Result<BlockingStatus, String> {
    let adblock = state.adblock.lock().map_err(|e| e.to_string())?;
    Ok(adblock.get_status())
}

#[tauri::command]
fn toggle_adblock(
    app: AppHandle,
    state: State<'_, AppState>,
    enabled: bool,
) -> Result<BlockingStatus, String> {
    let status = {
        let adblock = state.adblock.lock().map_err(|e| e.to_string())?;
        adblock.set_enabled(enabled);
        adblock.get_status()
    };
    {
        let mut browser = state.browser.lock().map_err(|e| e.to_string())?;
        browser.adblock_enabled = enabled;
        emit_state_projection(&app, &browser);
    }
    Ok(status)
}

#[tauri::command]
fn adblock_add_exception(state: State<'_, AppState>, hostname: String) -> Result<(), String> {
    let adblock = state.adblock.lock().map_err(|e| e.to_string())?;
    adblock.add_exception(&hostname);
    Ok(())
}

#[tauri::command]
fn adblock_remove_exception(state: State<'_, AppState>, hostname: String) -> Result<(), String> {
    let adblock = state.adblock.lock().map_err(|e| e.to_string())?;
    adblock.remove_exception(&hostname);
    Ok(())
}

#[tauri::command]
fn adblock_except_active(state: State<'_, AppState>, url: String) -> Result<bool, String> {
    let adblock = state.adblock.lock().map_err(|e| e.to_string())?;
    Ok(adblock.is_url_excepted(&url))
}

// -----------------------------------------------------------------------------
// PERMISSIONS COMMANDS
// -----------------------------------------------------------------------------

#[tauri::command]
fn permission_list_decisions(
    state: State<'_, AppState>,
    profile_id: Option<String>,
) -> Result<Vec<PermissionDecisionRecord>, String> {
    let profile = profile_id.as_deref().unwrap_or(PERSONAL_PROFILE);
    let perms = state.permissions.lock().map_err(|e| e.to_string())?;
    Ok(perms.list_decisions(profile))
}

#[tauri::command]
fn permission_set_decision(
    state: State<'_, AppState>,
    profile_id: Option<String>,
    origin: String,
    resource: String,
    decision: String,
) -> Result<(), String> {
    let profile = profile_id.as_deref().unwrap_or(PERSONAL_PROFILE);
    let perm_res = PermissionResource::from_str_name(&resource);
    let perm_dec = match decision.to_lowercase().as_str() {
        "allow" => PermissionDecision::Allow,
        "block" | "deny" => PermissionDecision::Block,
        _ => PermissionDecision::Ask,
    };

    let mut perms = state.permissions.lock().map_err(|e| e.to_string())?;
    perms.set_decision(profile, origin, perm_res, perm_dec);
    Ok(())
}

#[tauri::command]
fn permission_respond(
    app: AppHandle,
    state: State<'_, AppState>,
    id: String,
    allow: bool,
    remember: bool,
) -> Result<PermissionDecisionRecord, String> {
    let mut perms = state.permissions.lock().map_err(|e| e.to_string())?;
    let record = perms.resolve_request(&id, allow, remember, false, PERSONAL_PROFILE)?;
    let _ = app.emit("blanc:permission-resolved", &record);
    Ok(record)
}

// -----------------------------------------------------------------------------
// DOWNLOADS COMMANDS
// -----------------------------------------------------------------------------

#[tauri::command]
fn downloads_list(
    state: State<'_, AppState>,
    profile_id: Option<String>,
    include_private: Option<bool>,
) -> Result<Vec<DownloadItem>, String> {
    let profile = profile_id.as_deref().unwrap_or(PERSONAL_PROFILE);
    let priv_allowed = include_private.unwrap_or(false);
    let dm = state.downloads.lock().map_err(|e| e.to_string())?;
    Ok(dm.list_downloads(profile, priv_allowed))
}

#[tauri::command]
fn downloads_open(state: State<'_, AppState>, id: String) -> Result<(), String> {
    let dm = state.downloads.lock().map_err(|e| e.to_string())?;
    let _item = dm.get_download(&id).ok_or_else(|| format!("Download '{}' not found", id))?;

    #[cfg(desktop)]
    {
        #[cfg(target_os = "windows")]
        let _ = std::process::Command::new("cmd").args(["/C", "start", "", &_item.target_path]).spawn();
        #[cfg(target_os = "macos")]
        let _ = std::process::Command::new("open").arg(&_item.target_path).spawn();
        #[cfg(target_os = "linux")]
        let _ = std::process::Command::new("xdg-open").arg(&_item.target_path).spawn();
    }
    Ok(())
}

#[tauri::command]
fn downloads_show_in_folder(state: State<'_, AppState>, id: String) -> Result<(), String> {
    let dm = state.downloads.lock().map_err(|e| e.to_string())?;
    let _item = dm.get_download(&id).ok_or_else(|| format!("Download '{}' not found", id))?;

    #[cfg(desktop)]
    {
        #[cfg(target_os = "windows")]
        let _ = std::process::Command::new("explorer").args(["/select,", &_item.target_path]).spawn();
        #[cfg(target_os = "macos")]
        let _ = std::process::Command::new("open").args(["-R", &_item.target_path]).spawn();
        #[cfg(target_os = "linux")]
        {
            if let Some(parent) = std::path::Path::new(&_item.target_path).parent() {
                let _ = std::process::Command::new("xdg-open").arg(parent).spawn();
            }
        }
    }
    Ok(())
}

#[tauri::command]
fn downloads_cancel(
    app: AppHandle,
    state: State<'_, AppState>,
    id: String,
) -> Result<Option<DownloadItem>, String> {
    let mut dm = state.downloads.lock().map_err(|e| e.to_string())?;
    let cancelled = dm.cancel_download(&id);
    if let Some(ref item) = cancelled {
        let _ = app.emit("blanc:download-updated", item);
    }
    Ok(cancelled)
}

#[tauri::command]
fn downloads_clear_completed(state: State<'_, AppState>) -> Result<(), String> {
    let mut dm = state.downloads.lock().map_err(|e| e.to_string())?;
    dm.clear_completed();
    Ok(())
}

// -----------------------------------------------------------------------------
// WORKSPACES & PROFILES COMMANDS
// -----------------------------------------------------------------------------

#[tauri::command]
fn workspace_create(
    state: State<'_, AppState>,
    profile_id: Option<String>,
    name: String,
    urls: Option<Vec<String>>,
) -> Result<WorkspaceRecord, String> {
    let profile = profile_id.as_deref().unwrap_or(PERSONAL_PROFILE);
    let mut mgr = state.workspaces.lock().map_err(|e| e.to_string())?;
    mgr.create_workspace(profile, name, urls.unwrap_or_default())
}

#[tauri::command]
fn workspace_rename(
    state: State<'_, AppState>,
    id: String,
    name: String,
) -> Result<WorkspaceRecord, String> {
    let mut mgr = state.workspaces.lock().map_err(|e| e.to_string())?;
    mgr.rename_workspace(&id, name)
}

#[tauri::command]
fn workspace_update_tabs(
    state: State<'_, AppState>,
    id: String,
    urls: Vec<String>,
    active_index: usize,
) -> Result<WorkspaceRecord, String> {
    let mut mgr = state.workspaces.lock().map_err(|e| e.to_string())?;
    mgr.update_workspace_tabs(&id, urls, active_index)
}

#[tauri::command]
fn workspace_delete(state: State<'_, AppState>, id: String) -> Result<(), String> {
    let mut mgr = state.workspaces.lock().map_err(|e| e.to_string())?;
    mgr.delete_workspace(&id)
}

#[tauri::command]
fn workspace_list(
    state: State<'_, AppState>,
    profile_id: Option<String>,
) -> Result<Vec<WorkspaceRecord>, String> {
    let profile = profile_id.as_deref().unwrap_or(PERSONAL_PROFILE);
    let mgr = state.workspaces.lock().map_err(|e| e.to_string())?;
    Ok(mgr.list_workspaces(profile))
}

#[tauri::command]
fn profile_create(state: State<'_, AppState>, name: String) -> Result<ProfileRecord, String> {
    let mut mgr = state.workspaces.lock().map_err(|e| e.to_string())?;
    mgr.create_profile(name)
}

#[tauri::command]
fn profile_delete(state: State<'_, AppState>, id: String) -> Result<(), String> {
    let mut mgr = state.workspaces.lock().map_err(|e| e.to_string())?;
    mgr.delete_profile(&id)
}

#[tauri::command]
fn profile_list(state: State<'_, AppState>) -> Result<Vec<ProfileRecord>, String> {
    let mgr = state.workspaces.lock().map_err(|e| e.to_string())?;
    Ok(mgr.list_profiles())
}

// -----------------------------------------------------------------------------
// PATRON COMMANDS
// -----------------------------------------------------------------------------

#[tauri::command]
fn patron_get_status(state: State<'_, AppState>) -> Result<PatronStatus, String> {
    let patron = state.patron.lock().map_err(|e| e.to_string())?;
    Ok(patron.get_status())
}

#[tauri::command]
fn patron_activate(state: State<'_, AppState>, key: String) -> Result<PatronStatus, String> {
    let mut patron = state.patron.lock().map_err(|e| e.to_string())?;
    patron.activate(&key)
}

#[tauri::command]
fn patron_deactivate(state: State<'_, AppState>) -> Result<PatronStatus, String> {
    let mut patron = state.patron.lock().map_err(|e| e.to_string())?;
    Ok(patron.deactivate())
}

// -----------------------------------------------------------------------------
// CREDENTIAL COMMANDS
// -----------------------------------------------------------------------------

#[tauri::command]
fn credential_check(state: State<'_, AppState>, origin: String) -> Result<FillStatus, String> {
    let broker = state.credentials.lock().map_err(|e| e.to_string())?;
    Ok(broker.check_available(&origin))
}

#[tauri::command]
fn credential_trigger_fill(
    state: State<'_, AppState>,
    origin: String,
) -> Result<FillStatus, String> {
    let mut broker = state.credentials.lock().map_err(|e| e.to_string())?;
    Ok(broker.trigger_fill(&origin))
}

// -----------------------------------------------------------------------------
// RUNNER ENTRY POINT
// -----------------------------------------------------------------------------

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    let storage = StorageManager::new();
    let initial_browser = BrowserState::with_default_window();

    let initial_state = AppState {
        browser: Mutex::new(initial_browser),
        storage,
        adblock: Mutex::new(AdblockEngine::new()),
        permissions: Mutex::new(PermissionBroker::new()),
        downloads: Mutex::new(DownloadManager::new()),
        workspaces: Mutex::new(WorkspacesManager::new()),
        patron: Mutex::new(PatronState::new()),
        credentials: Mutex::new(CredentialBroker::new()),
        viewport: Mutex::new(None),
    };

    tauri::Builder::default()
        .manage(initial_state)
        .setup(|app| {
            log_msg("Tauri app initialized in setup hook");
            let app_handle = app.handle().clone();
            if let Some(state) = app_handle.try_state::<AppState>() {
                if let Ok(browser) = state.browser.lock() {
                    emit_state_projection(&app_handle, &browser);
                }
            }
            Ok(())
        })
        .invoke_handler(tauri::generate_handler![
            get_state_projection,
            get_window_projection,
            list_windows,
            get_tabs,
            get_active_tab,
            create_tab,
            close_tab,
            close_all_tabs,
            switch_tab,
            navigate,
            duplicate_tab,
            set_tab_pinned,
            set_tab_muted,
            reload_tab,
            go_back,
            go_forward,
            mobile_navigate,
            mobile_reload,
            mobile_go_back,
            mobile_go_forward,
            mobile_can_go_back,
            mobile_can_go_forward,
            find_in_page,
            create_group,
            rename_group,
            set_group_collapsed,
            move_tab_to_group,
            close_group,
            reopen_closed_tab,
            forget_closed_tab,
            clear_closed_tabs,
            set_viewport,
            minimize_window,
            maximize_window,
            close_window,
            get_history,
            history_list,
            add_history_entry,
            history_record,
            history_remove,
            clear_history,
            get_bookmarks,
            add_bookmark,
            remove_bookmark,
            favorites_list,
            favorites_add,
            favorites_update,
            favorites_remove,
            get_settings,
            save_settings,
            get_sync_eligibility,
            sync_get_payload,
            adblock_status,
            get_adblock_stats,
            toggle_adblock,
            adblock_add_exception,
            adblock_remove_exception,
            adblock_except_active,
            permission_list_decisions,
            permission_set_decision,
            permission_respond,
            downloads_list,
            downloads_open,
            downloads_show_in_folder,
            downloads_cancel,
            downloads_clear_completed,
            workspace_create,
            workspace_rename,
            workspace_update_tabs,
            workspace_delete,
            workspace_list,
            profile_create,
            profile_delete,
            profile_list,
            patron_get_status,
            patron_activate,
            patron_deactivate,
            credential_check,
            credential_trigger_fill,
            proxy_fetch,
        ])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}

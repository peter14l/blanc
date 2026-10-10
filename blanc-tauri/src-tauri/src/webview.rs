use serde::{Deserialize, Serialize};
#[allow(unused_imports)]
use tauri::{AppHandle, Emitter, LogicalPosition, LogicalSize, Manager};

#[cfg(desktop)]
use tauri::{webview::WebviewBuilder, WebviewUrl};

use crate::AppState;

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
    crate::log_msg(&format!("create_tab_webview: tab_id={}, target_url={}", tab_id, target_url));
    let parsed_url: url::Url = target_url
        .parse()
        .map_err(|e| format!("Invalid URL: {}", e))?;
    let window = app
        .get_window("main")
        .ok_or_else(|| "Main window not found".to_string())?;
    let v = current_viewport(app);
    crate::log_msg(&format!("create_tab_webview: viewport=({}, {}, {}, {})", v.x, v.y, v.width, v.height));

    let nav_app = app.clone();
    let nav_id = tab_id.to_string();
    let title_app = app.clone();
    let title_id = tab_id.to_string();

    let init_script = r#"
    (function() {
      if (window.__blanc_nav_injected) return;
      window.__blanc_nav_injected = true;

      // Auxiliary mouse buttons (3: Back, 4: Forward)
      window.addEventListener('mouseup', function(e) {
        if (e.button === 3) {
          window.history.back();
        } else if (e.button === 4) {
          window.history.forward();
        }
      }, true);

      // Mouse gestures & rocker navigation inside page
      let isRightDown = false;
      let isLeftDown = false;
      let startX = 0;
      let startY = 0;
      let didGesture = false;

      window.addEventListener('mousedown', function(e) {
        if (e.button === 0) isLeftDown = true;
        if (e.button === 2) {
          isRightDown = true;
          startX = e.clientX;
          startY = e.clientY;
          didGesture = false;

          // Rocker gesture: Left held + Right click -> Forward
          if (isLeftDown) {
            didGesture = true;
            window.history.forward();
          }
        } else if (e.button === 0 && isRightDown) {
          // Rocker gesture: Right held + Left click -> Back
          didGesture = true;
          window.history.back();
        }
      }, true);

      window.addEventListener('mouseup', function(e) {
        if (e.button === 0) isLeftDown = false;
        if (e.button === 2) {
          if (isRightDown && !didGesture) {
            const dx = e.clientX - startX;
            const dy = e.clientY - startY;
            const dist = Math.sqrt(dx * dx + dy * dy);
            if (dist >= 28) {
              didGesture = true;
              if (Math.abs(dx) > Math.abs(dy)) {
                if (dx < -20) window.history.back();
                else if (dx > 20) window.history.forward();
              } else {
                if (dy < -20) window.scrollTo({ top: 0, behavior: 'smooth' });
                else if (dy > 20) window.scrollTo({ top: document.body.scrollHeight, behavior: 'smooth' });
              }
            }
          }
          isRightDown = false;
        }
      }, true);

      // Suppress context menu if gesture was performed
      window.addEventListener('contextmenu', function(e) {
        if (didGesture) {
          e.preventDefault();
          e.stopPropagation();
          didGesture = false;
        }
      }, true);

      // Keyboard navigation shortcuts inside webview
      window.addEventListener('keydown', function(e) {
        if (e.altKey && e.key === 'ArrowLeft') {
          window.history.back();
        } else if (e.altKey && e.key === 'ArrowRight') {
          window.history.forward();
        } else if (e.key === 'F5' || ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'r')) {
          window.location.reload();
        }
      }, true);

      // Web Notification polyfill and bridge to Blanc shell
      (function() {
        if (window.__blanc_notif_hooked) return;
        window.__blanc_notif_hooked = true;

        function emitBlancNotification(title, options) {
          options = options || {};
          const payload = {
            type: 'BLANC_WEB_NOTIFICATION',
            title: String(title || 'Notification'),
            body: String(options.body || ''),
            icon: String(options.icon || ''),
            tag: String(options.tag || ''),
            origin: window.location.origin
          };
          try {
            if (window.chrome && window.chrome.webview && window.chrome.webview.postMessage) {
              window.chrome.webview.postMessage(JSON.stringify(payload));
            }
          } catch(e) {}
          try {
            if (window.parent && window.parent !== window) {
              window.parent.postMessage(payload, '*');
            }
          } catch(e) {}
        }

        function BlancNotification(title, options) {
          emitBlancNotification(title, options);
          const notif = {
            title: title,
            body: options?.body || '',
            icon: options?.icon || '',
            tag: options?.tag || '',
            onclick: null,
            onclose: null,
            onerror: null,
            onshow: null,
            close: function() {}
          };
          setTimeout(() => { if (typeof notif.onshow === 'function') notif.onshow(); }, 50);
          return notif;
        }

        BlancNotification.permission = 'granted';
        BlancNotification.requestPermission = function(cb) {
          if (typeof cb === 'function') cb('granted');
          return Promise.resolve('granted');
        };

        try {
          Object.defineProperty(window, 'Notification', {
            value: BlancNotification,
            writable: true,
            configurable: true
          });
        } catch(e) {
          window.Notification = BlancNotification;
        }

        if ('ServiceWorkerRegistration' in window && ServiceWorkerRegistration.prototype) {
          const origShow = ServiceWorkerRegistration.prototype.showNotification;
          ServiceWorkerRegistration.prototype.showNotification = function(title, options) {
            emitBlancNotification(title, options);
            return Promise.resolve();
          };
        }
      })();
    })();
    "#;

    let perm_app = app.clone();
    let perm_origin = parsed_url.origin().ascii_serialization();
    let perm_tab_id = tab_id.to_string();

    let builder = WebviewBuilder::new(tab_id, WebviewUrl::External(parsed_url))
        .initialization_script(init_script)
        .user_agent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
        .on_permission_request(move |_webview, kind| {
            let res_str = match kind {
                tauri::webview::PermissionKind::Camera => "camera",
                tauri::webview::PermissionKind::Microphone => "microphone",
                tauri::webview::PermissionKind::Notifications => "notifications",
                tauri::webview::PermissionKind::Geolocation => "geolocation",
                _ => "other",
            };
            let _ = perm_app.emit("blanc:permission-request", serde_json::json!({
                "id": format!("perm-{}", chrono::Utc::now().timestamp_millis()),
                "origin": perm_origin.clone(),
                "resource": res_str,
                "tabId": perm_tab_id.clone(),
            }));
            match kind {
                tauri::webview::PermissionKind::Camera
                | tauri::webview::PermissionKind::Microphone
                | tauri::webview::PermissionKind::Notifications
                | tauri::webview::PermissionKind::Geolocation => {
                    tauri::webview::PermissionResponse::Allow
                }
                _ => tauri::webview::PermissionResponse::Default,
            }
        })
        .on_new_window(move |_url, _features| {
            tauri::webview::NewWindowResponse::Allow
        })
        .on_navigation({
            let nav_app = nav_app.clone();
            let nav_id = nav_id.clone();
            move |url| {
                let _ = nav_app.emit(
                    "tab-navigated",
                    TabNavigated {
                        tab_id: nav_id.clone(),
                        url: Some(url.to_string()),
                        title: None,
                    },
                );

                // Inject cosmetic rules for newly navigated URL if active webview is navigated
                if let Some(wv) = nav_app.get_webview(&nav_id) {
                    if let Some(state) = nav_app.try_state::<AppState>() {
                        if let Ok(adblock) = state.adblock.lock() {
                            if let Some(css) = adblock.get_cosmetic_css(url.as_str()) {
                                let inject_js = format!(
                                    r#"
                                    (function() {{
                                        let style = document.getElementById('__blanc_cosmetic_style');
                                        if (!style) {{
                                            style = document.createElement('style');
                                            style.id = '__blanc_cosmetic_style';
                                            (document.head || document.documentElement).appendChild(style);
                                        }}
                                        style.textContent = {};
                                    }})();
                                    "#,
                                    serde_json::to_string(&css).unwrap_or_default()
                                );
                                let _ = wv.eval(&inject_js);
                            }
                        }
                    }
                }

                true
            }
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

    crate::log_msg("create_tab_webview: calling window.add_child...");
    let wv = window
        .add_child(
            builder,
            LogicalPosition::new(v.x, v.y),
            LogicalSize::new(v.width, v.height),
        )
        .map_err(|e| {
            crate::log_msg(&format!("add_child failed: {}", e));
            format!("Failed to create child webview: {}", e)
        })?;

    crate::log_msg("create_tab_webview: add_child succeeded, showing and focusing");
    let _ = wv.show();
    let _ = wv.set_focus();

    #[cfg(target_os = "windows")]
    {
        use webview2_com::{
            AddScriptToExecuteOnDocumentCreatedCompletedHandler,
            Microsoft::Web::WebView2::Win32::*,
            WebMessageReceivedEventHandler,
            WebResourceRequestedEventHandler,
        };
        use windows::core::{w, Interface};

        let filter_app = app.clone();
        let filter_tab_id = tab_id.to_string();
        let notif_app = app.clone();
        let notif_tab_id = tab_id.to_string();

        let cosmetic_css = if let Some(state) = app.try_state::<AppState>() {
            if let Ok(adblock) = state.adblock.lock() {
                adblock.get_cosmetic_css(target_url)
            } else {
                None
            }
        } else {
            None
        };

        let cosmetic_script = cosmetic_css.map(|css| {
            format!(
                r#"
                (function() {{
                    function __blanc_inject_cosmetic() {{
                        if (document.getElementById('__blanc_cosmetic_style')) return;
                        const style = document.createElement('style');
                        style.id = '__blanc_cosmetic_style';
                        style.textContent = {};
                        (document.head || document.documentElement).appendChild(style);
                    }}
                    if (document.documentElement) __blanc_inject_cosmetic();
                    document.addEventListener('DOMContentLoaded', __blanc_inject_cosmetic);
                }})();
                "#,
                serde_json::to_string(&css).unwrap_or_default()
            )
        });

        let _ = wv.with_webview(move |platform_webview| {
            unsafe {
                let controller = platform_webview.controller();
                if let Ok(core) = controller.CoreWebView2() {
                    // 1. Attach wildcard filter for all web resource requests
                    let _ = core.AddWebResourceRequestedFilter(
                        w!("*"),
                        COREWEBVIEW2_WEB_RESOURCE_CONTEXT_ALL,
                    );

                    // 2. Inject cosmetic CSS rules for active domain via AddScriptToExecuteOnDocumentCreated
                    if let Some(ref script) = cosmetic_script {
                        let script_hstring = windows::core::HSTRING::from(script.as_str());
                        let script_handler = AddScriptToExecuteOnDocumentCreatedCompletedHandler::create(
                            Box::new(|_res, _id| Ok(())),
                        );
                        let _ = core.AddScriptToExecuteOnDocumentCreated(&script_hstring, &script_handler);
                    }

                    // 3. Hook add_WebResourceRequested to intercept and block ad/tracker subresources
                    let mut token = 0i64;
                    let handler = WebResourceRequestedEventHandler::create(Box::new(
                        move |sender, args| {
                            let Some(args) = args else {
                                return Ok(());
                            };

                            let Ok(request) = args.Request() else {
                                return Ok(());
                            };

                            let mut uri_pwstr = windows::core::PWSTR::null();
                            if request.Uri(&mut uri_pwstr).is_err() {
                                return Ok(());
                            }
                            let url = webview2_com::take_pwstr(uri_pwstr);

                            let mut context = COREWEBVIEW2_WEB_RESOURCE_CONTEXT_ALL;
                            if args.ResourceContext(&mut context).is_err() {
                                return Ok(());
                            }

                            let resource_type = match context {
                                COREWEBVIEW2_WEB_RESOURCE_CONTEXT_SCRIPT => "script",
                                COREWEBVIEW2_WEB_RESOURCE_CONTEXT_IMAGE => "image",
                                COREWEBVIEW2_WEB_RESOURCE_CONTEXT_STYLESHEET => "stylesheet",
                                COREWEBVIEW2_WEB_RESOURCE_CONTEXT_XML_HTTP_REQUEST => "xmlhttprequest",
                                COREWEBVIEW2_WEB_RESOURCE_CONTEXT_FETCH => "fetch",
                                COREWEBVIEW2_WEB_RESOURCE_CONTEXT_DOCUMENT => "subdocument",
                                COREWEBVIEW2_WEB_RESOURCE_CONTEXT_MEDIA => "media",
                                COREWEBVIEW2_WEB_RESOURCE_CONTEXT_FONT => "font",
                                COREWEBVIEW2_WEB_RESOURCE_CONTEXT_WEBSOCKET => "websocket",
                                COREWEBVIEW2_WEB_RESOURCE_CONTEXT_PING => "ping",
                                _ => "other",
                            };

                            let mut referer_str = String::new();
                            if let Ok(headers) = request.Headers() {
                                let mut referer_pwstr = windows::core::PWSTR::null();
                                if headers.GetHeader(w!("Referer"), &mut referer_pwstr).is_ok() && !referer_pwstr.is_null() {
                                    referer_str = webview2_com::take_pwstr(referer_pwstr);
                                }
                            }

                            if let Some(state) = filter_app.try_state::<AppState>() {
                                let tab_url = if let Ok(browser) = state.browser.lock() {
                                    browser.tabs.get(&filter_tab_id).map(|t| t.url.clone())
                                } else {
                                    None
                                };

                                let source_url = if !referer_str.is_empty() {
                                    Some(referer_str.as_str())
                                } else {
                                    tab_url.as_deref()
                                };

                                let should_block = if let Ok(adblock) = state.adblock.lock() {
                                    if adblock.should_block(&url, source_url, resource_type) {
                                        adblock.record_block();
                                        true
                                    } else {
                                        false
                                    }
                                } else {
                                    false
                                };

                                if should_block {
                                    crate::log_msg(&format!(
                                        "Adblock blocked subresource: {} [{}] in tab {}",
                                        url, resource_type, filter_tab_id
                                    ));

                                    if let Ok(mut browser) = state.browser.lock() {
                                        let _ = browser.increment_tab_blocked(&filter_tab_id, 1);
                                        crate::emit_state_projection(&filter_app, &browser);
                                    }

                                    // Return a 403 Forbidden empty response
                                    if let Some(core) = sender.as_ref() {
                                        if let Ok(core2) = core.cast::<ICoreWebView2_2>() {
                                            if let Ok(env) = core2.Environment() {
                                                if let Ok(response) = env.CreateWebResourceResponse(
                                                    None::<&windows::Win32::System::Com::IStream>,
                                                    403,
                                                    w!("Blocked"),
                                                    w!("Content-Type: text/plain\r\n"),
                                                ) {
                                                    let _ = args.SetResponse(&response);
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Ok(())
                        },
                    ));
                    let _ = core.add_WebResourceRequested(&handler, &mut token);

                    // 4. Hook add_WebMessageReceived to receive Web Notifications from page
                    let mut msg_token = 0i64;
                    let msg_handler = WebMessageReceivedEventHandler::create(Box::new(
                        move |_sender, args| {
                            let Some(args) = args else {
                                return Ok(());
                            };
                            let mut msg_pwstr = windows::core::PWSTR::null();
                            if args.TryGetWebMessageAsString(&mut msg_pwstr).is_ok() && !msg_pwstr.is_null() {
                                let msg = webview2_com::take_pwstr(msg_pwstr);
                                if let Ok(val) = serde_json::from_str::<serde_json::Value>(&msg) {
                                    if val.get("type").and_then(|t| t.as_str()) == Some("BLANC_WEB_NOTIFICATION") {
                                        crate::log_msg(&format!("Web notification received in tab {}: {:?}", notif_tab_id, val));
                                        let _ = notif_app.emit(
                                            "blanc:web-notification",
                                            serde_json::json!({
                                                "id": format!("notif-{}", chrono::Utc::now().timestamp_millis()),
                                                "title": val.get("title").and_then(|t| t.as_str()).unwrap_or("Notification"),
                                                "body": val.get("body").and_then(|b| b.as_str()).unwrap_or(""),
                                                "icon": val.get("icon").and_then(|i| i.as_str()).unwrap_or(""),
                                                "origin": val.get("origin").and_then(|o| o.as_str()).unwrap_or(""),
                                                "tabId": notif_tab_id.clone(),
                                            }),
                                        );
                                    }
                                }
                            }
                            Ok(())
                        },
                    ));
                    let _ = core.add_WebMessageReceived(&msg_handler, &mut msg_token);
                }
            }
        });
    }

    crate::log_msg(&format!("create_tab_webview: child webview created and shown for '{}'", tab_id));

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
    crate::log_msg(&format!("apply_viewport: active_tab_id={:?}, hidden={}, vp=({}, {}, {}, {})", active_tab_id, hidden, viewport.x, viewport.y, viewport.width, viewport.height));
    for (label, wv) in app.webviews() {
        if label == "main" {
            continue;
        }
        if Some(label.as_str()) == active_tab_id && !hidden {
            let _ = wv.set_position(LogicalPosition::new(viewport.x, viewport.y));
            let _ = wv.set_size(LogicalSize::new(viewport.width, viewport.height));
            let _ = wv.show();
            // Do NOT call wv.set_focus() here: stealing focus during viewport resizing or
            // suggestion popup toggle steals keyboard focus from the omnibox input.
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
            Ok(parsed) => {
                wv.navigate(parsed)
                    .map_err(|e| format!("Failed to navigate: {}", e))?;

                // Inject cosmetic rules for newly navigated URL if available
                if let Some(state) = app.try_state::<AppState>() {
                    if let Ok(adblock) = state.adblock.lock() {
                        if let Some(css) = adblock.get_cosmetic_css(url) {
                            let inject_js = format!(
                                r#"
                                (function() {{
                                    let style = document.getElementById('__blanc_cosmetic_style');
                                    if (!style) {{
                                        style = document.createElement('style');
                                        style.id = '__blanc_cosmetic_style';
                                        (document.head || document.documentElement).appendChild(style);
                                    }}
                                    style.textContent = {};
                                }})();
                                "#,
                                serde_json::to_string(&css).unwrap_or_default()
                            );
                            let _ = wv.eval(&inject_js);
                        }
                    }
                }

                Ok(())
            }
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

// -----------------------------------------------------------------------------
// FIND IN PAGE
// -----------------------------------------------------------------------------

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct FindResult {
    pub match_count: u32,
    pub current_index: u32,
}

#[cfg(desktop)]
pub fn find_in_page(
    app: &AppHandle,
    tab_id: &str,
    query: &str,
    forward: bool,
) -> Result<FindResult, String> {
    if let Some(wv) = app.get_webview(tab_id) {
        wv.eval(&format!(
            r#"
                (function() {{
                    const query = {query:?};
                    const forward = {forward:?};
                    if (!query) return;

                    // Use window.find if available (Chrome/Edge/WebKit)
                    if (window.find) {{
                        window.find(query, false, !forward, true, false, true, false);
                        return;
                    }}

                    // Fallback: use Selection/Range API
                    const queryLower = query.toLowerCase();
                    const walker = document.createTreeWalker(
                        document.body || document.documentElement,
                        NodeFilter.SHOW_TEXT,
                        null,
                        false
                    );

                    const textNodes = [];
                    while (walker.nextNode()) {{
                        const node = walker.currentNode;
                        if (node.textContent && node.textContent.toLowerCase().includes(queryLower)) {{
                            textNodes.push(node);
                        }}
                    }}

                    if (textNodes.length === 0) return;

                    const selection = window.getSelection();
                    let currentIndex = 0;

                    if (selection && selection.rangeCount > 0) {{
                        const selRange = selection.getRangeAt(0);
                        for (let i = 0; i < textNodes.length; i++) {{
                            const range = document.createRange();
                            range.selectNodeContents(textNodes[i]);
                            if (range.compareBoundaryPoints(Range.END_TO_END, selRange) <= 0) {{
                                currentIndex = i + 1;
                            }}
                        }}
                    }}

                    let targetIndex = forward ? currentIndex : currentIndex - 2;
                    if (targetIndex < 0) targetIndex = textNodes.length - 1;
                    if (targetIndex >= textNodes.length) targetIndex = 0;

                    const range = document.createRange();
                    range.selectNodeContents(textNodes[targetIndex]);
                    if (selection) {{
                        selection.removeAllRanges();
                        selection.addRange(range);
                    }}
                    textNodes[targetIndex].parentElement?.scrollIntoView({{ behavior: 'smooth', block: 'center' }});
                }})();
            "#
        ))
        .map_err(|e| format!("Find in page failed: {}", e))?;

        Ok(FindResult {
            match_count: 1,
            current_index: 1,
        })
    } else {
        Err(format!("Webview for tab {} not found", tab_id))
    }
}

#[cfg(not(desktop))]
pub fn find_in_page(
    _app: &AppHandle,
    _tab_id: &str,
    _query: &str,
    _forward: bool,
) -> Result<FindResult, String> {
    Ok(FindResult {
        match_count: 0,
        current_index: 0,
    })
}

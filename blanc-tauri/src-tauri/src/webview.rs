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

    let (x_offset, right_margin) = if let Some(state) = app.try_state::<AppState>() {
        let settings = state.storage.load().settings;
        if settings.vertical_tabs {
            if settings.vertical_tabs_collapsed {
                (86.0, 16.0)
            } else {
                (270.0, 16.0)
            }
        } else {
            (16.0, 16.0)
        }
    } else {
        (16.0, 16.0)
    };

    Viewport {
        x: x_offset,
        y: 80.0,
        width: (w - x_offset - right_margin).max(1.0),
        height: (h - 96.0).max(1.0),
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

      // Keyboard shortcuts forwarder to Blanc shell
      window.addEventListener('keydown', function(e) {
        var isMeta = e.ctrlKey || e.metaKey;
        var key = e.key ? e.key.toLowerCase() : '';
        var isShortcut = false;

        if ((isMeta && key === 'l') || (e.altKey && key === 'd') || e.key === 'F6') {
          isShortcut = true;
        } else if (isMeta && (key === 'k' || key === 'f' || key === 'h' || key === 'y' || key === 'j')) {
          isShortcut = true;
        } else if (isMeta && (key === 't' || key === 'n' || key === 'w')) {
          isShortcut = true;
        } else if (isMeta && (e.key === 'F4' || e.key === 'Tab' || e.key === 'PageUp' || e.key === 'PageDown')) {
          isShortcut = true;
        } else if (isMeta && (e.key >= '1' && e.key <= '9')) {
          isShortcut = true;
        } else if (isMeta && (e.key === '=' || e.key === '+' || e.key === '-' || e.key === '0' || e.key === ',' || e.key === '/')) {
          isShortcut = true;
        } else if (e.key === 'F5' || (isMeta && key === 'r') || e.key === 'F11') {
          isShortcut = true;
        } else if (e.altKey && (e.key === 'ArrowLeft' || e.key === 'ArrowRight' || e.key === 'Home')) {
          isShortcut = true;
        }

        if (isShortcut) {
          e.preventDefault();
          e.stopPropagation();
          var payload = {
            type: 'BLANC_KEYBOARD_SHORTCUT',
            key: e.key,
            code: e.code,
            ctrlKey: e.ctrlKey,
            metaKey: e.metaKey,
            altKey: e.altKey,
            shiftKey: e.shiftKey
          };
          try {
            if (window.parent && window.parent !== window) {
              window.parent.postMessage(payload, '*');
            }
          } catch(err) {}
        }
      }, true);

      window.addEventListener('keyup', function(e) {
        if (e.key === 'Control' || e.key === 'Alt' || e.key === 'Meta') {
          var payload = {
            type: 'BLANC_KEYBOARD_KEYUP',
            key: e.key,
            code: e.code,
            ctrlKey: e.ctrlKey,
            metaKey: e.metaKey,
            altKey: e.altKey,
            shiftKey: e.shiftKey
          };
          try {
            if (window.parent && window.parent !== window) {
              window.parent.postMessage(payload, '*');
            }
          } catch(err) {}
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
                let url_str = url.as_str().to_lowercase();
                let is_download = url_str.contains("download_folder.php")
                    || url_str.contains("forcedownload=1")
                    || url_str.ends_with(".zip")
                    || url_str.ends_with(".tar.gz")
                    || url_str.ends_with(".tar")
                    || url_str.ends_with(".gz")
                    || url_str.ends_with(".tgz")
                    || url_str.ends_with(".exe")
                    || url_str.ends_with(".msi")
                    || url_str.ends_with(".dmg")
                    || url_str.ends_with(".pkg")
                    || url_str.ends_with(".deb")
                    || url_str.ends_with(".rpm")
                    || url_str.ends_with(".apk");

                if !is_download {
                    let _ = nav_app.emit(
                        "tab-navigated",
                        TabNavigated {
                            tab_id: nav_id.clone(),
                            url: Some(url.to_string()),
                            title: None,
                        },
                    );
                }

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
            let lower = title.to_lowercase();
            if !lower.contains("download_folder") && !lower.ends_with(".zip") {
                let _ = title_app.emit(
                    "tab-navigated",
                    TabNavigated {
                        tab_id: title_id.clone(),
                        url: None,
                        title: Some(title),
                    },
                );
            }
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
            AcceleratorKeyPressedEventHandler,
            BytesReceivedChangedEventHandler,
            DownloadStartingEventHandler,
            StateChangedEventHandler,
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
        let accel_app = app.clone();
        let dl_app = app.clone();
        let dl_tab_id = tab_id.to_string();

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
                                    let msg_type = val.get("type").and_then(|t| t.as_str()).unwrap_or("");
                                    if msg_type == "BLANC_WEB_NOTIFICATION" {
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

                    let mut accel_token = 0i64;
                    let accel_handler = AcceleratorKeyPressedEventHandler::create(Box::new(
                        move |_sender, args| {
                            let Some(args) = args else {
                                return Ok(());
                            };
                            let mut event_kind = COREWEBVIEW2_KEY_EVENT_KIND_KEY_DOWN;
                            if args.KeyEventKind(&mut event_kind).is_err() {
                                return Ok(());
                            }

                            let mut vk = 0u32;
                            if args.VirtualKey(&mut vk).is_err() {
                                return Ok(());
                            }

                            let is_down = event_kind == COREWEBVIEW2_KEY_EVENT_KIND_KEY_DOWN
                                || event_kind == COREWEBVIEW2_KEY_EVENT_KIND_SYSTEM_KEY_DOWN;
                            let is_up = event_kind == COREWEBVIEW2_KEY_EVENT_KIND_KEY_UP
                                || event_kind == COREWEBVIEW2_KEY_EVENT_KIND_SYSTEM_KEY_UP;

                            let ctrl_down = (windows::Win32::UI::Input::KeyboardAndMouse::GetKeyState(0x11) as u16 & 0x8000) != 0;
                            let alt_down = (windows::Win32::UI::Input::KeyboardAndMouse::GetKeyState(0x12) as u16 & 0x8000) != 0;
                            let shift_down = (windows::Win32::UI::Input::KeyboardAndMouse::GetKeyState(0x10) as u16 & 0x8000) != 0;

                            if is_up {
                                if vk == 0x11 || vk == 0x12 || vk == 0xA2 || vk == 0xA3 || vk == 0xA4 || vk == 0xA5 {
                                    let key_name = if vk == 0x12 || vk == 0xA4 || vk == 0xA5 { "Alt" } else { "Control" };
                                    let _ = accel_app.emit("blanc:keyboard-keyup", serde_json::json!({
                                        "key": key_name,
                                        "ctrlKey": ctrl_down,
                                        "altKey": alt_down,
                                        "shiftKey": shift_down,
                                    }));
                                }
                                return Ok(());
                            }

                            if is_down {
                                let mut handled_shortcut: Option<(&str, &str)> = None;

                                if ctrl_down {
                                    match vk {
                                        0x54 => handled_shortcut = Some(("t", "KeyT")),
                                        0x57 => handled_shortcut = Some(("w", "KeyW")),
                                        0x73 => handled_shortcut = Some(("F4", "F4")),
                                        0x52 => handled_shortcut = Some(("r", "KeyR")),
                                        0x4C => handled_shortcut = Some(("l", "KeyL")),
                                        0x4B => handled_shortcut = Some(("k", "KeyK")),
                                        0x46 => handled_shortcut = Some(("f", "KeyF")),
                                        0x48 => handled_shortcut = Some(("h", "KeyH")),
                                        0x59 => handled_shortcut = Some(("y", "KeyY")),
                                        0x4A => handled_shortcut = Some(("j", "KeyJ")),
                                        0x4E => handled_shortcut = Some(("n", "KeyN")),
                                        0x44 if !alt_down => handled_shortcut = Some(("d", "KeyD")),
                                        0x42 if shift_down => handled_shortcut = Some(("b", "KeyB")),
                                        0x4F if shift_down => handled_shortcut = Some(("o", "KeyO")),
                                        0x09 => handled_shortcut = Some(("Tab", "Tab")),
                                        0x21 => handled_shortcut = Some(("PageUp", "PageUp")),
                                        0x22 => handled_shortcut = Some(("PageDown", "PageDown")),
                                        0x31 => handled_shortcut = Some(("1", "Digit1")),
                                        0x32 => handled_shortcut = Some(("2", "Digit2")),
                                        0x33 => handled_shortcut = Some(("3", "Digit3")),
                                        0x34 => handled_shortcut = Some(("4", "Digit4")),
                                        0x35 => handled_shortcut = Some(("5", "Digit5")),
                                        0x36 => handled_shortcut = Some(("6", "Digit6")),
                                        0x37 => handled_shortcut = Some(("7", "Digit7")),
                                        0x38 => handled_shortcut = Some(("8", "Digit8")),
                                        0x39 => handled_shortcut = Some(("9", "Digit9")),
                                        0x30 => handled_shortcut = Some(("0", "Digit0")),
                                        0xBB => handled_shortcut = Some(("=", "Equal")),
                                        0xBD => handled_shortcut = Some(("-", "Minus")),
                                        0xBC => handled_shortcut = Some((",", "Comma")),
                                        0xBF => handled_shortcut = Some(("/", "Slash")),
                                        _ => {}
                                    }
                                } else if alt_down {
                                    match vk {
                                        0x44 => handled_shortcut = Some(("d", "KeyD")),
                                        0x24 => handled_shortcut = Some(("Home", "Home")),
                                        0x25 => handled_shortcut = Some(("ArrowLeft", "ArrowLeft")),
                                        0x27 => handled_shortcut = Some(("ArrowRight", "ArrowRight")),
                                        0x09 => handled_shortcut = Some(("Tab", "Tab")),
                                        _ => {}
                                    }
                                } else {
                                    match vk {
                                        0x74 => handled_shortcut = Some(("F5", "F5")),
                                        0x75 => handled_shortcut = Some(("F6", "F6")),
                                        0x7A => handled_shortcut = Some(("F11", "F11")),
                                        _ => {}
                                    }
                                }

                                if let Some((key_name, code_name)) = handled_shortcut {
                                    let _ = args.SetHandled(true);
                                    let _ = accel_app.emit("blanc:keyboard-shortcut", serde_json::json!({
                                        "key": key_name,
                                        "code": code_name,
                                        "ctrlKey": ctrl_down,
                                        "metaKey": false,
                                        "altKey": alt_down,
                                        "shiftKey": shift_down,
                                    }));
                                }
                            }
                            Ok(())
                        },
                    ));
                    let _ = controller.add_AcceleratorKeyPressed(&accel_handler, &mut accel_token);

if let Ok(core4) = core.cast::<ICoreWebView2_4>() {
                        let mut dl_token = 0i64;
                        let dl_handler = DownloadStartingEventHandler::create(Box::new(
                            move |_sender, args| {
                                let Some(args) = args else {
                                    return Ok(());
                                };

                                let mut path_pwstr = windows::core::PWSTR::null();
                                let _ = args.ResultFilePath(&mut path_pwstr);
                                let path_str = webview2_com::take_pwstr(path_pwstr);

                                let Ok(op) = args.DownloadOperation() else {
                                    return Ok(());
                                };

                                let mut uri_pwstr = windows::core::PWSTR::null();
                                let _ = op.Uri(&mut uri_pwstr);
                                let uri = webview2_com::take_pwstr(uri_pwstr);

                                let mut total_bytes = 0i64;
                                let _ = op.TotalBytesToReceive(&mut total_bytes);

                                let filename = std::path::Path::new(&path_str)
                                    .file_name()
                                    .map(|f| f.to_string_lossy().to_string())
                                    .unwrap_or_else(|| {
                                        uri.split('/').last().unwrap_or("download").to_string()
                                    });

                                let dl_id = if let Some(state) = dl_app.try_state::<AppState>() {
                                    if let Ok(mut dm) = state.downloads.lock() {
                                        let item = dm.start_download(
                                            "main".to_string(),
                                            dl_tab_id.clone(),
                                            uri.clone(),
                                            filename.clone(),
                                            if total_bytes > 0 { Some(total_bytes as u64) } else { None },
                                            false,
                                            None,
                                        );
                                        item.id
                                    } else {
                                        format!("dl-{}", chrono::Utc::now().timestamp_millis())
                                    }
                                } else {
                                    format!("dl-{}", chrono::Utc::now().timestamp_millis())
                                };

                                crate::log_msg(&format!(
                                    "Download started in tab {}: id={}, file='{}' -> '{}'",
                                    dl_tab_id, dl_id, filename, path_str
                                ));

                                let _ = dl_app.emit(
                                    "blanc:download-started",
                                    serde_json::json!({
                                        "id": dl_id,
                                        "fileName": filename,
                                        "targetPath": path_str,
                                        "totalBytes": if total_bytes > 0 { Some(total_bytes) } else { None },
                                        "url": uri,
                                    }),
                                );

                                let _ = dl_app.emit(
                                    "blanc:web-notification",
                                    serde_json::json!({
                                        "id": format!("dl-toast-{}", chrono::Utc::now().timestamp_millis()),
                                        "title": "Download Started",
                                        "body": format!("Saving {} to Downloads", filename),
                                        "icon": "",
                                        "origin": "Blanc Downloads",
                                        "tabId": dl_tab_id.clone(),
                                    }),
                                );

                                // If the download was initiated by a login redirect (e.g. Moodle $SESSION->wantsurl),
                                // automatically navigate the webview to the student dashboard /my/ so it doesn't stay on a blank screen.
                                if uri.contains("download_folder.php") || uri.contains("forcedownload=1") {
                                    if let Ok(parsed_uri) = url::Url::parse(&uri) {
                                        if let Some(host) = parsed_uri.host_str() {
                                            let dashboard_str = format!("https://{}/my/", host);
                                            if let Ok(target_nav) = url::Url::parse(&dashboard_str) {
                                                if let Some(wv) = dl_app.get_webview(&dl_tab_id) {
                                                    crate::log_msg(&format!(
                                                        "Moodle download redirect detected in tab {}; navigating to dashboard {}",
                                                        dl_tab_id, dashboard_str
                                                    ));
                                                    let _ = wv.navigate(target_nav);
                                                }
                                            }
                                        }
                                    }
                                }

                                let state_app = dl_app.clone();
                                let state_id = dl_id.clone();
                                let state_filename = filename.clone();
                                let mut state_token = 0i64;
                                let state_handler = StateChangedEventHandler::create(Box::new(
                                    move |sender, _args| {
                                        if let Some(op) = sender {
                                            let mut dl_state = COREWEBVIEW2_DOWNLOAD_STATE_IN_PROGRESS;
                                            if op.State(&mut dl_state).is_ok() {
                                                if dl_state == COREWEBVIEW2_DOWNLOAD_STATE_COMPLETED {
                                                    crate::log_msg(&format!("Download completed: id={}, file='{}'", state_id, state_filename));
                                                    if let Some(app_state) = state_app.try_state::<AppState>() {
                                                        if let Ok(mut dm) = app_state.downloads.lock() {
                                                            let _ = dm.complete_download(&state_id);
                                                        }
                                                    }
                                                    let _ = state_app.emit(
                                                        "blanc:download-completed",
                                                        serde_json::json!({ "id": state_id, "fileName": state_filename }),
                                                    );
                                                    let _ = state_app.emit(
                                                        "blanc:web-notification",
                                                        serde_json::json!({
                                                            "id": format!("dl-done-{}", chrono::Utc::now().timestamp_millis()),
                                                            "title": "Download Complete",
                                                            "body": format!("{} is ready in your Downloads folder.", state_filename),
                                                            "icon": "",
                                                            "origin": "Blanc Downloads",
                                                        }),
                                                    );
                                                } else if dl_state == COREWEBVIEW2_DOWNLOAD_STATE_INTERRUPTED {
                                                    crate::log_msg(&format!("Download interrupted: id={}, file='{}'", state_id, state_filename));
                                                    if let Some(app_state) = state_app.try_state::<AppState>() {
                                                        if let Ok(mut dm) = app_state.downloads.lock() {
                                                            let _ = dm.fail_download(&state_id, "Download interrupted".to_string());
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        Ok(())
                                    }
                                ));
                                let _ = op.add_StateChanged(&state_handler, &mut state_token);

                                let prog_app = dl_app.clone();
                                let prog_id = dl_id.clone();
                                let mut prog_token = 0i64;
                                let prog_handler = BytesReceivedChangedEventHandler::create(Box::new(
                                    move |sender, _args| {
                                        if let Some(op) = sender {
                                            let mut bytes = 0i64;
                                            if op.BytesReceived(&mut bytes).is_ok() {
                                                if let Some(app_state) = prog_app.try_state::<AppState>() {
                                                    if let Ok(mut dm) = app_state.downloads.lock() {
                                                        let _ = dm.update_progress(&prog_id, bytes as u64);
                                                    }
                                                }
                                                let _ = prog_app.emit(
                                                    "blanc:download-progress",
                                                    serde_json::json!({ "id": prog_id, "receivedBytes": bytes }),
                                                );
                                            }
                                        }
                                        Ok(())
                                    }
                                ));
                                let _ = op.add_BytesReceivedChanged(&prog_handler, &mut prog_token);

                                Ok(())
                            }
                        ));
                        let _ = core4.add_DownloadStarting(&dl_handler, &mut dl_token);
                    }

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
    let child_webviews: Vec<_> = app.webviews().into_iter().filter(|(l, _)| l != "main").collect();

    let target_label = if let Some(target) = active_tab_id {
        if child_webviews.iter().any(|(l, _)| l == target) {
            Some(target.to_string())
        } else if let Some((first_label, _)) = child_webviews.first() {
            crate::log_msg(&format!("apply_viewport: target '{:?}' not in webviews, falling back to existing child webview '{}'", target, first_label));
            Some(first_label.clone())
        } else {
            None
        }
    } else if let Some((first_label, _)) = child_webviews.first() {
        Some(first_label.clone())
    } else {
        None
    };

    for (label, wv) in &child_webviews {
        if target_label.as_deref() == Some(label.as_str()) && !hidden {
            let _ = wv.set_position(LogicalPosition::new(viewport.x, viewport.y));
            let _ = wv.set_size(LogicalSize::new(viewport.width, viewport.height));
            let _ = wv.show();
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

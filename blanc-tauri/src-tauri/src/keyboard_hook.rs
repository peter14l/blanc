//! Windows low-level keyboard hook for Alt+Tab in-app tab switching.
//!
//! When Blanc is the foreground window, intercepts Alt+Tab to cycle
//! through Blanc tabs with the visual TabSwitcherHUD instead of switching windows.

#[cfg(target_os = "windows")]
use std::sync::atomic::{AtomicIsize, Ordering};
#[cfg(target_os = "windows")]
use std::sync::OnceLock;
#[cfg(target_os = "windows")]
use tauri::{AppHandle, Emitter, Manager};
#[cfg(target_os = "windows")]
use windows::Win32::Foundation::{LPARAM, LRESULT, WPARAM};
#[cfg(target_os = "windows")]
use windows::Win32::UI::WindowsAndMessaging::*;

#[cfg(target_os = "windows")]
static MAIN_HWND: AtomicIsize = AtomicIsize::new(0);
#[cfg(target_os = "windows")]
static APP_HANDLE: OnceLock<AppHandle> = OnceLock::new();
#[cfg(target_os = "windows")]
static HOOK_HANDLE: AtomicIsize = AtomicIsize::new(0);

#[cfg(target_os = "windows")]
unsafe extern "system" fn low_level_keyboard_proc(
    code: i32,
    wparam: WPARAM,
    lparam: LPARAM,
) -> LRESULT {
    if code >= 0 {
        let main_hwnd_val = MAIN_HWND.load(Ordering::Relaxed);
        if main_hwnd_val != 0 {
            let fg = GetForegroundWindow();
            let is_blanc_focused = fg.0 as isize == main_hwnd_val
                || GetAncestor(fg, GA_ROOT).0 as isize == main_hwnd_val;

            // Also check if foreground window belongs to Blanc process (e.g. child webview)
            let is_our_window = if is_blanc_focused {
                true
            } else {
                let mut fg_pid = 0u32;
                GetWindowThreadProcessId(fg, Some(&mut fg_pid));
                let current_pid = windows::Win32::System::Threading::GetCurrentProcessId();
                fg_pid == current_pid
            };

            if is_our_window {
                let kbd = *(lparam.0 as *const KBDLLHOOKSTRUCT);
                let is_tab = kbd.vkCode == 0x09; // VK_TAB
                let is_alt = (kbd.flags.0 & LLKHF_ALTDOWN.0) != 0
                    || (windows::Win32::UI::Input::KeyboardAndMouse::GetKeyState(0x12) as u16 & 0x8000) != 0; // VK_MENU

                let msg = wparam.0 as u32;

                if is_tab && is_alt && (msg == WM_KEYDOWN || msg == WM_SYSKEYDOWN) {
                    if let Some(app) = APP_HANDLE.get() {
                        let is_shift = (windows::Win32::UI::Input::KeyboardAndMouse::GetKeyState(0x10) as u16 & 0x8000) != 0; // VK_SHIFT
                        let _ = app.emit(
                            "blanc:alt-tab-press",
                            serde_json::json!({ "shift": is_shift }),
                        );
                    }
                    // Swallow the Alt+Tab so Windows OS Task Switcher does not appear!
                    return LRESULT(1);
                }

                let is_menu_key = kbd.vkCode == 0x12 || kbd.vkCode == 0xA4 || kbd.vkCode == 0xA5; // VK_MENU, VK_LMENU, VK_RMENU
                if is_menu_key && (msg == WM_KEYUP || msg == WM_SYSKEYUP) {
                    if let Some(app) = APP_HANDLE.get() {
                        let _ = app.emit("blanc:alt-tab-release", ());
                    }
                }
            }
        }
    }

    let hook_val = HOOK_HANDLE.load(Ordering::Relaxed);
    let hhook = HHOOK(hook_val as *mut _);
    CallNextHookEx(Some(hhook), code, wparam, lparam)
}

pub fn init_keyboard_hook(app: &AppHandle) {
    #[cfg(target_os = "windows")]
    {
        APP_HANDLE.set(app.clone()).ok();

        if let Some(win) = app.get_webview_window("main") {
            if let Ok(hwnd) = win.hwnd() {
                MAIN_HWND.store(hwnd.0 as isize, Ordering::Relaxed);
            }
        }

        unsafe {
            let hook = SetWindowsHookExW(
                WH_KEYBOARD_LL,
                Some(low_level_keyboard_proc),
                None,
                0,
            );
            if let Ok(hook) = hook {
                HOOK_HANDLE.store(hook.0 as isize, Ordering::Relaxed);
                crate::log_msg("Low-level keyboard hook installed for Alt+Tab");
            }
        }
    }
}

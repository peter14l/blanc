# Blanc Tauri IPC Specification & Command Reference

This document defines the typed IPC contract between the React frontend UI and the Rust Tauri v2 core.

## 1. Data Models

### Tab
```typescript
interface Tab {
  id: string;               // Unique identifier (e.g., "tab_1728020000000_abc")
  url: string;              // Current URL
  title: string;            // Web page title
  is_active: boolean;       // Whether this tab is currently displayed
  is_loading: boolean;      // Loading indicator status
  blocked_trackers: number; // Count of blocked network requests/trackers
  can_go_back: boolean;     // Navigation back capability
  can_go_forward: boolean;  // Navigation forward capability
}
```

```rust
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Tab {
    pub id: String,
    pub url: String,
    pub title: String,
    pub is_active: bool,
    pub is_loading: bool,
    pub blocked_trackers: u32,
    pub can_go_back: bool,
    pub can_go_forward: bool,
}
```

---

## 2. Invocation Commands (`invoke`)

### `create_tab`
Creates a new browser tab with an optional initial URL. Spawns an underlying native child webview positioned below the floating Island.
* **Arguments:** `{ url?: string }`
* **Returns:** `Promise<Tab>`
* **Behavior:** New tab becomes active immediately, hiding the previous tab.

### `close_tab`
Destroys the specified tab's child webview and removes it from `BrowserState`.
* **Arguments:** `{ tabId: string }`
* **Returns:** `Promise<string>` (returns closed tabId)
* **Behavior:** If the closed tab was active, activates the adjacent tab. If no tabs remain, spawns a fresh `about:blank` tab.

### `switch_tab`
Switches the active webview without reloading the DOM.
* **Arguments:** `{ tabId: string }`
* **Returns:** `Promise<void>`
* **Behavior:** Calls `.hide()` on currently active webview and `.show()` on the requested webview.

### `navigate`
Navigates the specified tab to a new URL.
* **Arguments:** `{ tabId: string, url: string }`
* **Returns:** `Promise<void>`
* **Behavior:** Normalizes raw input (prepends `https://` if needed or converts search queries to DuckDuckGo/Google search URL).

### `reload_tab`
Reloads the web page in the active tab.
* **Arguments:** `{ tabId: string }`
* **Returns:** `Promise<void>`

### `go_back`
Navigates backwards in the webview session history.
* **Arguments:** `{ tabId: string }`
* **Returns:** `Promise<void>`

### `go_forward`
Navigates forward in the webview session history.
* **Arguments:** `{ tabId: string }`
* **Returns:** `Promise<void>`

### `get_tabs`
Fetches the current list of all open tabs.
* **Arguments:** none
* **Returns:** `Promise<Tab[]>`

### `get_active_tab`
Fetches the currently active tab details.
* **Arguments:** none
* **Returns:** `Promise<Tab | null>`

### `minimize_window`
Minimizes the root transparent window.
* **Arguments:** none
* **Returns:** `Promise<void>`

### `maximize_window`
Toggles maximize/unmaximize on the root transparent window.
* **Arguments:** none
* **Returns:** `Promise<void>`

### `close_window`
Closes the browser application.
* **Arguments:** none
* **Returns:** `Promise<void>`

---

## 3. Asynchronous Native Events (`listen`)

The Rust engine emits events to the frontend via Tauri's event bus:

| Event Name | Payload | Trigger Condition |
| :--- | :--- | :--- |
| `tab-navigated` | `{ tab_id: string, url: string, title: string }` | When a child webview finishes navigating or updates title |
| `tab-loading` | `{ tab_id: string, is_loading: boolean }` | Navigation start/finish events |
| `tracker-blocked` | `{ tab_id: string, count: number }` | When content blocking rule intercepts a tracker request |
| `window-resized` | `{ width: number, height: number }` | Root window dimensions altered |

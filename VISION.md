# PROJECT VISION & SPECIFICATION ARCHITECTURE

```
    ┌──────────────────────────────────────────────────────────┐
    │                NATIVE TAURI V2 CORE (RUST)               │
    │   Window Bounds • Multi-Webview Manager • IPC Routes     │
    └──────────────┬──────────────────────────────┬────────────┘
                   │                              │
                   ▼                              ▼
    ┌─────────────────────────────┐┌────────────────────────────┐
    │   LAYER A: BLANC ISLAND UI  ││  LAYER B: ACTIVE WEB PAGE  │
    │   React + TypeScript + CSS  ││  Tauri Child Webview       │
    │   Floating Glassmorphism    ││  Positioned dynamically    │
    └─────────────────────────────┘└────────────────────────────┘

```

---

## 1. Executive Vision & Core Philosophy

**Project Name:** `blanc-tauri` (working title)

**Target Environment:** GitHub Codespaces (CLI AI Agents like OpenCode, Cursor, GitHub Copilot CLI, Antigravity CLI)

**Primary Target Platforms:** Windows 11 (Desktop) & Android (Mobile)

### The Problem Statement

Mainstream browsers force a heavy top-toolbar paradigm and run bloated process trees. Electron-based floating UI browsers (like standard Blanc or early Arc builds) solve the visual layout issue but suffer high idle RAM usage (300 MB–500 MB+) due to double V8/Chromium runtime layers.

### The Solution Vision

Build an open-source, ultra-lightweight, cross-platform browser that pairs **Blanc's floating "Island" interface** with **Tauri v2's native Rust backend**.

Instead of bundling an entire Chromium runtime via Electron, `blanc-tauri` uses the OS's native web engine (**WebView2 on Windows** and **Android System WebView on Mobile**). The UI layer is decoupled into a transparent, glassmorphic floating pill component, while web content is rendered inside child native WebViews dynamically positioned below the pill.

### Non-Negotiable Metrics

* **Idle Memory Target:** Sub-60 MB base app footprint before loading web pages.
* **Launch Time:** Sub-200 ms cold startup.
* **Cross-Device Parity:** Single Rust core compiling seamlessly to both Windows binary (`.exe`) and Android package (`.apk`).

---

## 2. Technical Stack & Dependencies

```
blanc-tauri/
├── src/                        # UI Layer (React 18 + TS + Tailwind CSS)
│   ├── components/             # Floating Island, Tab Switcher, Quick Switcher
│   ├── styles/                 # Blanc design tokens, glassmorphism, animations
│   └── App.tsx                 # Main UI overlay shell
├── src-tauri/                  # Engine Layer (Rust)
│   ├── src/
│   │   ├── lib.rs              # App initialization & plugin setup
│   │   ├── state.rs            # Tab state & URL management
│   │   └── webview.rs          # Multi-webview spawning & positioning logic
│   ├── Cargo.toml              # Rust crate dependencies
│   └── tauri.conf.json         # Window transparency & IPC capability masks
└── .devcontainer/              # GitHub Codespaces setup scripts

```

### Core Technologies

* **Application Shell:** `Tauri v2` (Rust)
* **Web Engine (Desktop):** OS Native `WebView2` via `wry`
* **Web Engine (Mobile):** OS Native `Android System WebView`
* **Frontend Framework:** `React 18` + `TypeScript` + `Vite`
* **Styling System:** `Tailwind CSS v3` (utilizing design tokens extracted from `bnfy/blanc`)
* **State & IPC:** `@tauri-apps/api` for typed Rust commands

---

## 3. Structural & Architectural Requirements

### A. Window Transparency & Layering (Rust & Tauri Config)

1. The main application window MUST be configured as **frameless (decorations: false)**, **transparent (transparent: true)**, and **shadowed**.
2. The React frontend renders ONLY the floating "Island" capsule at the top center of the viewport, with `bg-transparent` across the canvas body.
3. Native drag regions (`data-tauri-drag-region`) MUST be assigned exclusively to the pill header background to allow moving the window across the screen without interfering with text input or web content interaction.

### B. Multi-Webview Orchestration (`WebviewBuilder`)

1. Websites MUST NOT be embedded using HTML `<iframe>` or `<webview>` tags.
2. Web pages MUST be rendered using Tauri v2's native Rust `WebviewBuilder` API.
3. When a user enters a URL or opens a tab:
* Rust spawns a child webview attached to the main window.
* Bounds are computed dynamically: `X = 16px`, `Y = 80px` (below the floating pill offset), `Width = Window_Width - 32px`, `Height = Window_Height - 96px`.
* Switching tabs MUST toggle visibility (`webview.hide()` / `webview.show()`) or adjust z-index without re-rendering or reloading the page DOM.



---

## 4. Design System & UI Specifications

The UI must faithfully mirror the minimal aesthetic of **Blanc (`bnfy/blanc`)**:

* **The Island Pill:** A centered, floating pill with glassmorphism styling (`backdrop-blur-md`, subtle dark borders `border-white/10`, and dark background `bg-slate-900/80`).
* **Visual Controls:**
* Left: Compact window control indicators or tab indicators.
* Center: Minimalist, borderless URL/Search input field with active site favicon.
* Right: Blocked tracker counter badge and tab switcher toggle.


* **Keyboard Navigation:**
* `Ctrl+K` / `Cmd+K`: Instant focus on the floating Island input field.
* `Ctrl+T` / `Cmd+T`: Open new tab input state.
* `Ctrl+W` / `Cmd+W`: Close active webview instance in Rust.



---

## 5. Development Roadmap for AI Execution

When executing with AI CLI tools in Codespaces, build in these distinct, isolated phases:

```
┌────────────────────────────────────────────────────────────────────────┐
│ PHASE 1: TAURI V2 FOUNDATION                                          │
│ • Setup React + TypeScript + Vite template inside Tauri v2 shell.      │
│ • Configure frameless transparent window and drag-region rules.       │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│ PHASE 2: RUST MULTI-WEBVIEW ENGINE                                     │
│ • Implement `create_tab_webview` command in Rust.                     │
│ • Implement webview bounds calculation and window resize listeners.   │
│ • Implement tab switching (`show`/`hide`) and destruction handlers.   │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│ PHASE 3: BLANC DESIGN SYSTEM INTEGRATION                              │
│ • Port CSS design tokens and glassmorphism styling from `bnfy/blanc`.  │
│ • Build the React floating Island pill component with keyboard shortcuts.│
│ • Connect React state to Rust IPC (`invoke`).                         │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│ PHASE 4: ANDROID MOBILE TARGETING                                     │
│ • Initialize Android target using `tauri android init`.                │
│ • Adapt floating island constraints to touch gestures and bottom sheets.│
└────────────────────────────────────────────────────────────────────────┘

```

---
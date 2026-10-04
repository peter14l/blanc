# Blanc Tauri (`blanc-tauri`)

> **Ultra-lightweight, cross-platform browser shell pairing Blanc's floating Island UI with Tauri v2's native Rust multi-webview core.**

Based on the [VISION.md Architecture Specification](../VISION.md).

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

## Key Features & Highlights

* **Sub-60 MB Idle Memory:** Decouples UI from the web rendering engine, using the OS's native webview (WebView2 on Windows, Android System WebView on mobile) instead of bundling a duplicate Chromium runtime.
* **Sub-200 ms Cold Startup:** Native Rust shell launches immediately with zero Electron overhead.
* **Blanc Island Chrome:** Frameless, transparent floating pill with glassmorphism styling (`backdrop-blur-md`, subtle dark borders, and dark background), URL/Search input, tracker blocking counter, and tab switcher.
* **Multi-Webview Orchestration:** Tabs run inside real child webviews dynamically positioned below the pill (`X = 16px`, `Y = 80px`). Tab switching toggles visibility (`show()`/`hide()`) without reloading DOM.
* **Keyboard-First Navigation:**
  * `Ctrl+K` / `Cmd+K`: Focus Island search or toggle Quick Switcher command palette.
  * `Ctrl+T` / `Cmd+T`: New tab.
  * `Ctrl+W` / `Cmd+W`: Close active tab.
  * `Ctrl+R` / `Cmd+R`: Reload active tab.

---

## Directory Structure

```
blanc-tauri/
├── src/                        # UI Layer (React 18 + TS + Tailwind CSS)
│   ├── components/             # Floating Island, Tab Switcher, Quick Switcher, Window Controls
│   ├── hooks/                  # Typed Tauri IPC hooks with fallback mock mode
│   ├── styles/                 # Blanc design tokens, glassmorphic styles, animations
│   ├── types/                  # Typed Browser & Tab models
│   ├── App.tsx                 # Main UI overlay shell
│   └── main.tsx                # React DOM entry point
├── src-tauri/                  # Engine Layer (Rust)
│   ├── src/
│   │   ├── lib.rs              # App initialization, IPC command handlers & resize listener
│   │   ├── main.rs             # Executable entry point
│   │   ├── state.rs            # Thread-safe BrowserState & tab metadata management
│   │   └── webview.rs          # Multi-webview spawning, dynamic bounds & visibility logic
│   ├── capabilities/           # Tauri v2 security capability masks
│   ├── Cargo.toml              # Rust crate dependencies
│   ├── tauri.conf.json         # Window transparency & IPC configuration
│   └── build.rs                # Tauri build script
├── docs/                       # Technical Specifications & Guides
│   ├── ARCHITECTURE.md         # Detailed 2-layer decoupled architecture & memory profile
│   ├── BUILDING.md             # Developer build instructions & prerequisites
│   ├── IPC_SPEC.md             # Typed IPC contract and event reference
│   ├── ANDROID_TARGET.md       # Mobile architecture & bottom-docked Island adaptation
│   └── WINDOWS_TARGET.md       # Windows 11 WebView2 integration & packaging guide
├── .devcontainer/              # GitHub Codespaces setup scripts (Rust, Node, WebKit)
├── index.html                  # Transparent HTML canvas
├── package.json                # Frontend dependencies and build scripts
├── tailwind.config.js          # Tailwind CSS with Blanc design tokens
├── tsconfig.json               # TypeScript configuration
└── vite.config.ts              # Vite configuration
```

---

## Documentation Quick Links

* [Architecture Specification](docs/ARCHITECTURE.md)
* [Building & Running Guide](docs/BUILDING.md)
* [IPC Command & Event Specification](docs/IPC_SPEC.md)
* [Android Mobile Target Guide](docs/ANDROID_TARGET.md)
* [Windows 11 Target Guide](docs/WINDOWS_TARGET.md)

---

## Development

```bash
# Pure frontend development (with mock IPC)
npm install
npm run dev

# Full native app development (requires Rust + WebKitGTK / WebView2)
npm run tauri dev

# Production build
npm run tauri build
```

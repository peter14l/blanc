# Building Blanc Tauri: Developer Guide

This guide covers building and running `blanc-tauri` across local environments and GitHub Codespaces.

---

## 1. Prerequisites

### Node.js & Package Manager
* **Node.js:** v18.x or v20.x LTS
* **npm:** v9+

### Rust Toolchain
* **Rust:** v1.78+ (Edition 2021)
  ```bash
  curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
  ```

### Platform-Specific Dependencies

#### Linux (Debian / Ubuntu / Codespaces)
```bash
sudo apt-get update && sudo apt-get install -y \
  libwebkit2gtk-4.1-dev \
  build-essential \
  curl \
  wget \
  file \
  libxdo-dev \
  libssl-dev \
  libayatana-appindicator3-dev \
  librsvg2-dev
```

#### Windows 11
* Install **Visual Studio C++ Build Tools** with the "Desktop development with C++" workload.
* Install **WebView2 Runtime** (pre-installed on Windows 11).

#### macOS
* Install **Xcode Command Line Tools**:
  ```bash
  xcode-select --install
  ```

---

## 2. Quick Start: Running in Development

### A. Frontend Pure UI Mode (Mock IPC)
For rapid UI iteration on the floating Island pill, tabs, and Quick Switcher without compiling Rust:
```bash
cd blanc-tauri
npm install
npm run dev
```
Open `http://localhost:5173`. The UI automatically detects the web browser environment and activates mock IPC responses.

### B. Full Native App Mode (Rust + React)
To compile the Tauri v2 binary with native child webview management:
```bash
cd blanc-tauri
npm install
npm run tauri dev
```

---

## 3. Production Builds

### Desktop Release Build
```bash
cd blanc-tauri
npm run tauri build
```
The output binaries will be placed in:
* **Windows:** `src-tauri/target/release/blanc-tauri.exe` (and NSIS installer `.exe`)
* **Linux:** `src-tauri/target/release/bundle/appimage/` or `.deb`
* **macOS:** `src-tauri/target/release/bundle/dmg/`

---

## 4. Codespaces Development Workflow

When developing inside GitHub Codespaces:
1. Open the repository in Codespaces.
2. The devcontainer automatically runs `.devcontainer/setup.sh`, provisioning WebKitGTK headers, Rust, and Node.js dependencies.
3. Run `npm run dev` to access the Vite dev server via forward port 5173.

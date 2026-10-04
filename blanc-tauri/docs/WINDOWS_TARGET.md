# Windows 11 Target Specification: Blanc Tauri

Per Phase 1 and 2 of `VISION.md`, this specification details targeting Windows 11 using Tauri v2 and native WebView2.

---

## 1. Native Window Layering & WebView2 on Windows

Windows 11 provides the native Evergreen **WebView2** runtime (Chromium engine managed by Windows OS).

### Window Configurations:
* **Frameless & Transparent Window:**
  Windows 11 DWM (Desktop Window Manager) supports composition transparency with acrylic/mica effects or alpha transparency.
  In `tauri.conf.json`:
  ```json
  "windows": [
    {
      "title": "Blanc",
      "width": 1280,
      "height": 800,
      "decorations": false,
      "transparent": true,
      "shadow": true
    }
  ]
  ```
* **Native Dragging (`data-tauri-drag-region`):**
  Uses native Windows `WM_NCHITTEST` / `HTCAPTION` under the hood. Only the Island pill header background receives drag events, allowing full window snapping to Windows 11 Snap Layouts.
* **Child WebView2 Bounds:**
  On Windows, `wry` creates a child `ICoreWebView2Controller`.
  Dynamic bounds computation:
  `X = 16px`, `Y = 80px`, `Width = ClientWidth - 32px`, `Height = ClientHeight - 96px`.

---

## 2. Compilation & Signing on Windows

### Build Requirements:
1. Visual Studio 2022 C++ Build Tools
2. Windows 10/11 SDK (10.0.19041.0 or newer)
3. WebView2 Evergreen Runtime

### Development:
```cmd
cd blanc-tauri
npm install
npm run tauri dev
```

### Production Package:
```cmd
cd blanc-tauri
npm run tauri build
```
Generates:
* Standalone executable: `target/release/blanc-tauri.exe`
* NSIS Installer: `target/release/bundle/nsis/Blanc_0.1.0_x64-setup.exe`

---

## 3. Test Certificate Code Signing

For development and pre-release builds, Windows binaries can be signed with an automated test certificate to eliminate unverified executable errors:

```powershell
# Run the automated test-signing script from blanc-tauri/
./scripts/sign-windows-test.ps1 -TargetDir "src-tauri/target/release"
```

The script:
1. Generates an X.509 Code Signing certificate (`CN=Blanc Test Publisher, O=Bananify Creative`).
2. Exports the cert to a temporary `.pfx` file and registers it in the local Root certificate store.
3. Automatically runs Windows `signtool.exe` with SHA256 digest and timestamp verification against `target/release/*.exe` and `target/release/bundle/nsis/*.exe`.

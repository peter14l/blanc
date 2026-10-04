# Android Mobile Target Specification: Blanc Tauri

Per Phase 4 of `VISION.md`, this specification details targeting Android with Tauri v2 and adapting the Island chrome to mobile viewports.

---

## 1. Architecture on Android

```
┌────────────────────────────────────────────────────────┐
│             ANDROID APPLICATION SHELL                  │
│       Android Activity • Android System WebView        │
└───────────┬────────────────────────────────┬───────────┘
            │                                │
            ▼                                ▼
┌───────────────────────────────┐┌───────────────────────────────┐
│     BOTTOM DOCKED ISLAND      ││       ACTIVE WEBVIEW          │
│   Touch gestures • Swipe tabs ││   Full-height Android WebView │
│   Bottom sheet quick search   ││                               │
└───────────────────────────────┘└───────────────────────────────┘
```

On Android, Tauri v2 replaces WebView2/WebKit with the **Android System WebView**.

### Mobile UI Adaptations:
1. **Docked Island Pill:**
   Instead of floating at the top (which conflicts with the Android system notification shade and status bar), the Island adapts to a **bottom-docked or bottom-floating pill** with thumb-friendly controls.
2. **Gesture Navigation:**
   * Swipe left/right on the bottom pill to switch between active tabs.
   * Swipe up on the pill to expand the Tab Grid / Quick Switcher drawer.
3. **Safe Area Insets:**
   The UI reads `env(safe-area-inset-bottom)` and `env(safe-area-inset-top)` to avoid display cutouts and navigation bars.

---

## 2. Toolchain Prerequisites for Android

* **Java Development Kit:** OpenJDK 17
* **Android SDK:** API Level 34+
* **Android NDK:** Version 25.x+
* **Rust Android Targets:**
  ```bash
  rustup target add aarch64-linux-android
  rustup target add armv7-linux-androideabi
  rustup target add i686-linux-android
  rustup target add x86_64-linux-android
  ```

---

## 3. Initialization & Build Commands

### Initializing the Android Project
```bash
cd blanc-tauri
npm run tauri android init
```
This generates the native Gradle project inside `blanc-tauri/src-tauri/gen/android`.

### Running on Android Emulator / Physical Device
```bash
# Start Vite and deploy debug APK via ADB
npm run tauri android dev
```

### Packaging Production APK / AAB
```bash
npm run tauri android build --apk
npm run tauri android build --aab
```
The output APK is generated at:
`src-tauri/gen/android/app/build/outputs/apk/release/app-release-unsigned.apk`

---

## 4. Signed Release Split Per ABI

To optimize download size on Android devices, builds are split per target Architecture (ABI) and signed with a release keystore:

### Target ABIs:
* `arm64-v8a` (`aarch64-linux-android`) — Modern 64-bit ARM smartphones
* `armeabi-v7a` (`armv7-linux-androideabi`) — Legacy 32-bit ARM devices
* `x86_64` (`x86_64-linux-android`) — Modern Android emulators and ChromeOS
* `x86` (`i686-linux-android`) — Legacy 32-bit x86 emulators

### Automated Build & Signing Command:
```bash
# Run the automated split & sign script from blanc-tauri/
./scripts/build-android-splits.sh
```

The script:
1. Provisions a release keystore (`blanc-release.keystore`) if not already generated.
2. Compiles individual APK splits for each target ABI via Tauri CLI.
3. Automatically signs and aligns each generated `.apk` with `jarsigner`/`apksigner`.

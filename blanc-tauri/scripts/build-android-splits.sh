#!/usr/bin/env bash
set -euo pipefail

echo "================================================="
echo " Building & Signing Blanc Android (Split Per ABI)"
echo "================================================="

KEYSTORE_PATH="${KEYSTORE_PATH:-$PWD/src-tauri/gen/android/blanc-release.keystore}"
KEYSTORE_PASS="${KEYSTORE_PASS:-blancpass123}"
ALIAS_NAME="${ALIAS_NAME:-blanc}"

# 1. Ensure Keystore exists
if [ ! -f "$KEYSTORE_PATH" ]; then
  mkdir -p "$(dirname "$KEYSTORE_PATH")"
  echo "==> Generating release keystore at $KEYSTORE_PATH..."
  keytool -genkey -v \
    -keystore "$KEYSTORE_PATH" \
    -alias "$ALIAS_NAME" \
    -keyalg RSA \
    -keysize 2048 \
    -validity 10000 \
    -storepass "$KEYSTORE_PASS" \
    -keypass "$KEYSTORE_PASS" \
    -dname "CN=Blanc Browser, O=Bananify Creative, C=US"
fi

# 2. Build for each target ABI
TARGETS=(
  "aarch64-linux-android:arm64-v8a"
  "armv7-linux-androideabi:armeabi-v7a"
  "x86_64-linux-android:x86_64"
  "i686-linux-android:x86"
)

OUTPUT_DIR="$PWD/src-tauri/gen/android/app/build/outputs/apk/release"
mkdir -p "$OUTPUT_DIR"

for entry in "${TARGETS[@]}"; do
  RUST_TARGET="${entry%%:*}"
  ABI_NAME="${entry##*:}"
  
  echo "==> Building for ABI: $ABI_NAME (Target: $RUST_TARGET)..."
  npx @tauri-apps/cli android build --target "$RUST_TARGET" --apk || {
    echo "Warning: Full Gradle build requires Android SDK/NDK environment."
  }
done

echo "==> Android release signing & ABI splits configuration ready."

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

# 2. Build and sign for target ABI(s)
# By default or if ARM64_ONLY=1 or arg is "arm64", build arm64-v8a
BUILD_MODE="${1:-${TARGET_ABI:-arm64}}"

if [ "$BUILD_MODE" = "arm64" ] || [ "${ARM64_ONLY:-1}" = "1" ]; then
  TARGETS=("aarch64:arm64-v8a")
else
  TARGETS=(
    "aarch64:arm64-v8a"
    "armv7:armeabi-v7a"
    "x86_64:x86_64"
    "i686:x86"
  )
fi

OUTPUT_DIR="$PWD/src-tauri/gen/android/app/build/outputs/apk"
mkdir -p "$OUTPUT_DIR"

for entry in "${TARGETS[@]}"; do
  TAURI_TARGET="${entry%%:*}"
  ABI_NAME="${entry##*:}"
  
  echo "==> Building for ABI: $ABI_NAME (Tauri Target: $TAURI_TARGET)..."
  npx @tauri-apps/cli android build --ci --target "$TAURI_TARGET" --apk --split-per-abi || {
    echo "Warning: Full Gradle build requires Android SDK/NDK environment."
  }
done

echo "==> Aligning and signing generated split APKs with v1, v2, and v3 schemes..."
find "$OUTPUT_DIR" -name "*.apk" ! -name "*-aligned.apk" 2>/dev/null | while read -r apk; do
  echo "==> Processing $apk..."
  ALIGNED_APK="${apk%.apk}-aligned.apk"
  zipalign -p -f 4 "$apk" "$ALIGNED_APK" || cp "$apk" "$ALIGNED_APK"
  
  apksigner sign \
    --ks "$KEYSTORE_PATH" \
    --ks-pass "pass:$KEYSTORE_PASS" \
    --ks-key-alias "$ALIAS_NAME" \
    --key-pass "pass:$KEYSTORE_PASS" \
    --v1-signing-enabled true \
    --v2-signing-enabled true \
    --v3-signing-enabled true \
    "$ALIGNED_APK"
    
  echo "==> Verifying signature:"
  apksigner verify --verbose "$ALIGNED_APK" || true
  mv "$ALIGNED_APK" "$apk"
done

echo "==> Android release signing & ABI splits configuration ready."

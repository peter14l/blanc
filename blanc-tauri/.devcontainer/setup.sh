#!/usr/bin/env bash
set -euo pipefail

echo "=========================================="
echo " Setting up Blanc Tauri Development Environment"
echo "=========================================="

# Ensure Linux dependencies for WebKit/Tauri (when building desktop targets on Linux)
if command -v apt-get &> /dev/null; then
  echo "Installing Linux system dependencies for Tauri..."
  sudo apt-get update -y
  sudo apt-get install -y --no-install-recommends \
    libwebkit2gtk-4.1-dev \
    build-essential \
    curl \
    wget \
    file \
    libxdo-dev \
    libssl-dev \
    libayatana-appindicator3-dev \
    librsvg2-dev
fi

# Ensure Rust and Cargo
if ! command -v cargo &> /dev/null; then
  echo "Installing Rust toolchain..."
  curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh -s -- -y
  source "$HOME/.cargo/env"
fi

# Install frontend dependencies
cd "$(dirname "$0")/.."
echo "Installing Node.js dependencies for blanc-tauri..."
npm install

echo "Environment ready! Run 'npm run dev' for UI development or 'npm run tauri dev' for app development."

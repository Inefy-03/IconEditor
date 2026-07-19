#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
OUT_DIR="$ROOT_DIR/app/src/main/assets/export_toolchain"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
TMP="$(mktemp -d)"

cleanup() { rm -rf "$TMP"; }
trap cleanup EXIT

mkdir -p "$OUT_DIR"

ANDROID_JAR="$SDK/platforms/android-35/android.jar"
if [[ ! -f "$ANDROID_JAR" ]]; then
  echo "Missing $ANDROID_JAR. Install Android SDK platform 35." >&2
  exit 1
fi

if [[ ! -f "$OUT_DIR/android-35.jar" ]]; then
  cp "$ANDROID_JAR" "$OUT_DIR/android-35.jar"
  echo "Wrote android-35.jar"
fi
# 移除旧版 gzip 资源，避免 AGP 与运行时路径不一致
rm -f "$OUT_DIR/android-35.jar.gz"

fetch_aapt2() {
  local abi="$1"
  local zip_name="$2"
  local out_name="$OUT_DIR/aapt2-$abi"
  if [[ -f "$out_name" ]]; then
    echo "Exists aapt2-$abi"
    return
  fi
  local url="https://github.com/lzhiyong/android-sdk-tools/releases/download/35.0.2/$zip_name"
  echo "Downloading $url"
  curl -fsL -o "$TMP/$zip_name" "$url"
  unzip -qo "$TMP/$zip_name" "build-tools/aapt2" -d "$TMP"
  mv "$TMP/build-tools/aapt2" "$out_name"
  chmod +x "$out_name"
  echo "Wrote aapt2-$abi"
}

# 强制刷新：静态 aapt2（ET_EXEC），设备上从 nativeLibraryDir 直接 exec，不用 linker
rm -f "$OUT_DIR/aapt2-arm64" "$OUT_DIR/aapt2-x86_64"
fetch_aapt2 arm64 android-sdk-tools-static-aarch64.zip
fetch_aapt2 x86_64 android-sdk-tools-static-x86_64.zip

echo "Export toolchain ready: $OUT_DIR"

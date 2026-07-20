#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
ASSET_DIR="$ROOT_DIR/app/src/main/assets/export_toolchain"
AAPT2_DIR="$ROOT_DIR/app/export_toolchain"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"

mkdir -p "$ASSET_DIR" "$AAPT2_DIR"

ANDROID_JAR="$SDK/platforms/android-35/android.jar"
FRAMEWORK_JAR="$ASSET_DIR/android-35.jar"

is_compact_framework_jar() {
  local jar_path="$1"
  local entries
  entries="$(unzip -Z1 "$jar_path" 2>/dev/null | LC_ALL=C sort)" || return 1
  [[ "$entries" == $'AndroidManifest.xml\nresources.arsc' ]]
}

write_compact_framework_jar() {
  local source_jar="$ANDROID_JAR"
  if [[ -s "$FRAMEWORK_JAR" ]]; then
    source_jar="$FRAMEWORK_JAR"
  fi
  if [[ ! -s "$source_jar" ]]; then
    echo "Missing compact $FRAMEWORK_JAR and $ANDROID_JAR. Install Android SDK platform 35." >&2
    exit 1
  fi

  local toolchain_tmp_dir
  toolchain_tmp_dir="$(mktemp -d)"
  trap 'rm -rf "$toolchain_tmp_dir"' EXIT
  unzip -q "$source_jar" AndroidManifest.xml resources.arsc -d "$toolchain_tmp_dir"
  (
    cd "$toolchain_tmp_dir"
    zip -q -X -9 "$FRAMEWORK_JAR.tmp" AndroidManifest.xml resources.arsc
  )
  mv "$FRAMEWORK_JAR.tmp" "$FRAMEWORK_JAR"
  rm -rf "$toolchain_tmp_dir"
  trap - EXIT
  echo "Wrote compact android-35.jar"
}

if is_compact_framework_jar "$FRAMEWORK_JAR"; then
  echo "Exists compact android-35.jar"
else
  write_compact_framework_jar
fi

if ! is_compact_framework_jar "$FRAMEWORK_JAR"; then
  echo "Invalid compact framework jar: $FRAMEWORK_JAR" >&2
  exit 1
fi
# Remove the legacy gzip resource so AGP and runtime paths stay consistent.
rm -f "$ASSET_DIR/android-35.jar.gz"

require_aapt2() {
  local abi="$1"
  local out_name="$AAPT2_DIR/aapt2-$abi"
  if [[ ! -s "$out_name" ]]; then
    echo "Missing tracked 16 KB-compatible aapt2 asset: $out_name" >&2
    exit 1
  fi
  chmod +x "$out_name"
  echo "Exists aapt2-$abi"
}

require_aapt2 arm64
require_aapt2 x86_64

echo "Export toolchain ready: $ASSET_DIR, $AAPT2_DIR"

#!/usr/bin/env bash
# Build logo.icns for jpackage / Compose Desktop macOS packaging.
# Requires macOS (sips + iconutil). CI build-macos runs this before Gradle.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="$ROOT/composeApp/src/desktopMain/resources/logo.png"
OUT="$ROOT/composeApp/src/desktopMain/resources/logo.icns"

if [[ ! -f "$SRC" ]]; then
  echo "Missing source icon: $SRC" >&2
  exit 1
fi

if [[ "$(uname -s)" != "Darwin" ]]; then
  if [[ -f "$OUT" ]]; then
    echo "Using existing $OUT (non-macOS host)."
    exit 0
  fi
  echo "Cannot generate $OUT without macOS (sips + iconutil)." >&2
  exit 1
fi

ICONSET_DIR="$(mktemp -d)"
ICONSET="$ICONSET_DIR/Anisurge.iconset"
mkdir -p "$ICONSET"

resize() {
  local px="$1"
  local name="$2"
  sips -z "$px" "$px" "$SRC" --out "$ICONSET/$name" >/dev/null
}

resize 16 icon_16x16.png
resize 32 icon_16x16@2x.png
resize 32 icon_32x32.png
resize 64 icon_32x32@2x.png
resize 128 icon_128x128.png
resize 256 icon_128x128@2x.png
resize 256 icon_256x256.png
resize 512 icon_256x256@2x.png
resize 512 icon_512x512.png
resize 1024 icon_512x512@2x.png

iconutil -c icns "$ICONSET" -o "$OUT"
rm -rf "$ICONSET_DIR"

echo "Wrote $OUT ($(du -h "$OUT" | cut -f1))"

#!/bin/bash
# Build unsigned macOS DMG + portable zip (must run on macOS — jpackage requirement).
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

if [[ "$OSTYPE" != darwin* ]]; then
  echo "macOS DMG builds require a Mac. Options:"
  echo "  1. Run this script on macOS"
  echo "  2. Push a tag and use GitHub Actions job: build-macos"
  exit 1
fi

VERSION_NAME="${1:-$(grep '^app-version' gradle/libs.versions.toml | sed 's/.*= *"\(.*\)"/\1/')}"
BUILD_NUMBER="${2:-$(grep '^app-buildNumber' gradle/libs.versions.toml | sed 's/.*= *"\(.*\)"/\1/')}"

echo "Building Anisurge $VERSION_NAME+$BUILD_NUMBER for macOS..."
chmod +x gradlew
./gradlew :composeApp:packageDmg :composeApp:createPortableZip --no-daemon \
  -PappVersion="$VERSION_NAME" \
  -PappBuildNumber="$BUILD_NUMBER"

echo ""
echo "DMG:"
find composeApp/build/compose/binaries/main/dmg -name '*.dmg' 2>/dev/null || true
echo "Portable zip:"
find composeApp/build/distributions -name '*.zip' 2>/dev/null || true

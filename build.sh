#!/bin/bash

# Anisurge Build Script
# This script handles building Android APKs and Linux distributions (DEB, RPM).

set -e

# Set Java Home to the specific JDK required for the project
export JAVA_HOME=/opt/jdk-17.0.13+11
export PATH=$JAVA_HOME/bin:$PATH

# Default values from libs.versions.toml or overrides
VERSION_NAME="${1:-0.9.21}"
BUILD_NUMBER="${2:-34}"

echo "----------------------------------------------------"
echo "🚀 Starting Anisurge Build Process"
echo "📦 Version: $VERSION_NAME"
echo "🔢 Build Number: $BUILD_NUMBER"
echo "----------------------------------------------------"

# Navigate to project root (in case script is run from elsewhere)
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
cd "$SCRIPT_DIR"

# Ensure gradlew is executable
chmod +x gradlew

# Check for Windows signing credentials in key.properties or signing folder
WIN_SIGNING_ARGS=""
WIN_CERT="$SCRIPT_DIR/signing/windows.pfx"
if [ -f "$WIN_CERT" ]; then
    WIN_SIGNING_ARGS="-Pcompose.desktop.signing.windows.certificateFile=$WIN_CERT -Pcompose.desktop.signing.windows.keyPassword=anisuge2026"
    echo "🔑 Windows signing certificate found, enabling signing."
fi

# Run the build
echo "🔨 Compiling Android, Linux, and Windows packages..."
# We always create the Portable Zip now
BUILD_TASKS=":composeApp:assembleRelease packageDeb packageRpm packageAppImage createPortableZip"
if [[ "$OSTYPE" == "msys" || "$OSTYPE" == "cygwin" ]]; then
    BUILD_TASKS="$BUILD_TASKS packageMsi packageExe"
fi

./gradlew $BUILD_TASKS --no-daemon \
    -PappVersion="$VERSION_NAME" \
    -PappBuildNumber="$BUILD_NUMBER" \
    $WIN_SIGNING_ARGS

echo "----------------------------------------------------"
echo "✅ Build Process Finished!"
echo "----------------------------------------------------"

echo "📱 Android APKs:"
find "$SCRIPT_DIR/composeApp/build/outputs/apk/release" -name "*.apk" -not -name "*universal*" -exec echo "   - {}" \;
echo "   - (Universal) $(find "$SCRIPT_DIR/composeApp/build/outputs/apk/release" -name "*universal-release.apk")"

echo ""
echo "🐧 Linux Packages:"
find "$SCRIPT_DIR/composeApp/build/compose/binaries/main/deb" -name "*.deb" -exec echo "   - DEB: {}" \;
find "$SCRIPT_DIR/composeApp/build/compose/binaries/main/rpm" -name "*.rpm" -exec echo "   - RPM: {}" \;

# Check if AppImage was actually created or just the AppDir
APPIMAGE_DIR="$SCRIPT_DIR/composeApp/build/compose/binaries/main/appimage"
if [ -d "$APPIMAGE_DIR" ]; then
    APPIMAGE_FILE=$(find "$APPIMAGE_DIR" -name "*.AppImage" 2>/dev/null)
else
    APPIMAGE_FILE=""
fi

if [ -z "$APPIMAGE_FILE" ]; then
    echo "   - AppDir (Raw Folder): $SCRIPT_DIR/composeApp/build/compose/binaries/main/app/Anisurge/"
    echo "     (Note: Install 'appimagetool' to generate a single .AppImage file)"
else
    echo "   - AppImage: $APPIMAGE_FILE"
fi

echo ""
echo "🪟 Windows Installers:"
MSI_DIR="$SCRIPT_DIR/composeApp/build/compose/binaries/main/msi"
EXE_DIR="$SCRIPT_DIR/composeApp/build/compose/binaries/main/exe"
MSI_FILE=$( [ -d "$MSI_DIR" ] && find "$MSI_DIR" -name "*.msi" 2>/dev/null || echo "" )
EXE_FILE=$( [ -d "$EXE_DIR" ] && find "$EXE_DIR" -name "*.exe" 2>/dev/null || echo "" )

if [[ -n "$MSI_FILE" || -n "$EXE_FILE" ]]; then
    [ -n "$MSI_FILE" ] && echo "   - MSI: $MSI_FILE"
    [ -n "$EXE_FILE" ] && echo "   - EXE: $EXE_FILE"
else
    echo "   - (Note: Windows builds must be run on a Windows machine or via GitHub Actions)"
fi

echo ""
echo "🚀 Portable ZIP (Windows/Linux):"
DIST_DIR="$SCRIPT_DIR/composeApp/build/distributions"
if [ -d "$DIST_DIR" ]; then
    find "$DIST_DIR" -name "*.zip" -exec echo "   - {}" \;
else
    echo "   - (Not found)"
fi

echo ""
echo "🏗️ Arch Linux Build Dir:"
echo "   - $SCRIPT_DIR/arch_build/"
echo "----------------------------------------------------"

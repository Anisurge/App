<p align="center">
  <img src="composeApp/src/commonMain/composeResources/drawable/logo.png" width="128" height="128" alt="Anisurge Logo">
</p>

<h1 align="center">Anisurge</h1>

<p align="center">
  <b>High-performance anime client</b><br>
  Experience seamless anime streaming across Android, Linux, Windows, and iOS.
</p>

<p align="center">
  <img src="https://img.shields.io/github/v/release/Anisurge/App?include_prereleases&style=flat-square" alt="latest release">
  <img src="https://img.shields.io/github/downloads/Anisurge/App/total?style=flat-square" alt="downloads">
  <img src="https://img.shields.io/github/actions/workflow/status/Anisurge/App/build-release.yml?style=flat-square" alt="build status">
  <img src="https://img.shields.io/github/license/Anisurge/App?style=flat-square" alt="license">
</p>

<h2 align="center">Download</h2>

<p align="center">
  Get the latest version of Anisurge for your platform from the <b><a href="https://github.com/Anisurge/App/releases">Releases</a></b> page.
</p>

---

## Features

*   **Native Performance**: Hardware-accelerated playback via libmpv integration across all platforms (Desktop and Android).
*   **Modern Interface**: Clean, responsive UI built with Jetpack Compose.
*   **Portable Desktop**: Windows support includes a zero-install Portable ZIP distribution.
*   **Linux Native**: Full integration with global media keys and native package formats (DEB, RPM, AppImage).
*   **Multi-Platform**: Unified experience across mobile and desktop devices.

## Build from Source

### Prerequisites

| Tool | Requirement |
| :--- | :--- |
| **JDK** | Java 17 (Temurin 17.0.13+11 recommended) |
| **SDK** | Android SDK (for Mobile builds) |
| **CLI** | ImageMagick (optional, for icon generation) |

### Automation

Execute the build pipeline using the provided script:

```bash
./build.sh <version> <build_number>
```

The script automates the generation of Android APKs, Linux distributions, and Windows portable artifacts. 

## Technical Details

### Linux Support
The application utilizes `JNativeHook` for system-wide media playback control. For enhanced security and compatibility, native libraries are extracted to the user's home directory (`~/.anisurge/native/`).

### Windows Support
Windows distributions support PFX-based code signing. The portable distribution allows the application to run directly from the directory without system modifications.

---

<p align="center">
  Copyright © 2026 Anisurge. Developed for the anime community.
</p>

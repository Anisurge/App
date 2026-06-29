<p align="center">
  <img src="composeApp/src/commonMain/composeResources/drawable/logo.png" width="128" height="128" alt="Anisurge Logo">
</p>

<h1 align="center">Anisurge</h1>

<p align="center">
  <b>Modern, multiplatform anime streaming client</b><br>
  Watch, chat, and sync across Android, Linux, macOS, and Windows.
</p>

<p align="center">
  <img src="https://img.shields.io/github/v/release/Anisurge/App?include_prereleases&style=flat-square" alt="latest release">
  <img src="https://img.shields.io/github/downloads/Anisurge/App/total?style=flat-square" alt="downloads">
  <img src="https://img.shields.io/github/actions/workflow/status/Anisurge/App/build-release.yml?style=flat-square" alt="build status">
  <img src="https://img.shields.io/github/license/Anisurge/App?style=flat-square" alt="license">
</p>

<p align="center">
  <a href="https://anisurge.lol"><b>anisurge.lol</b></a>
  &nbsp;•&nbsp;
  <a href="https://github.com/Anisurge/App/releases"><b>Download</b></a>
</p>

---

## ✨ Features

### 🎬 Streaming
- **Multi-source extensions** — Stream from Suzu, AnimePahe, Anitaku, and more via a Mangayomi-style extension system
- **Sub/Dub support** — Choose between subbed and dubbed sources per show
- **Hardware-accelerated playback** — libmpv-powered on desktop, Media3 on Android
- **Intro/outro skip** — Auto-skip (powered by Aniskip API)
- **Continue watching** — Cross-device progress sync

### 👥 Watch Together (W2G / S2G)
- Create or join rooms to watch anime in sync with friends
- Built-in chat with AI assistant support
- Sub/dub room options

### 🛒 Cosmetics & Berries
- **Berries** — Earnable in-app currency for cosmetics
- **Profile frames, animated pfps, and shop items** — Customize your profile (cosmetic-only, never pay-to-win)

### 🧠 AI Chat
- In-app AI assistant for anime recommendations, episode discussions, and general Q&A

### 📥 Downloads
- Download episodes for offline viewing
- Hardware-compatible remuxing on Android

### 🗄️ Library & Sync
- Watchlist with folders: Watching, Planning, Completed, Paused, Dropped
- Two-way library sync with your account
- MAL/AniList integration

### 🔗 Integrations
- **Discord login** — Connect your Discord account
- **Cloudflare bypass** — Built-in Cloudflare challenge resolution for supported sources

---

## 🖥️ Platforms

| Platform | Status | Formats |
| :--- | :--- | :--- |
| **Android** (Phone) | ✅ Stable | APK |
| **Android** (TV) | ✅ Stable | APK |
| **Linux** | ✅ Stable | DEB, RPM, AppImage, portable ZIP |
| **Windows** | ✅ Stable | Portable ZIP | 
| **macOS** | ✅ Stable | DMG (x64 & ARM64), portable ZIP |

---

## 📸 Screenshots

> Coming soon

---

## 🛠️ Build from Source

### Prerequisites

| Tool | Requirement |
| :--- | :--- |
| **JDK** | Java 17+ (Temurin 17.0.13+11 recommended) |
| **SDK** | Android SDK (for mobile builds) |
| **CLI** | ImageMagick (optional, for icon generation) |

### One-command build

```bash
./build.sh <version> <build_number>
```

This generates Android APKs, Linux distributions, and Windows portable artifacts in one pass.

### Manual build

```bash
# Desktop (Linux/macOS/Windows)
./gradlew :composeApp:packageReleaseDistributionForCurrentOS

# Android (Phone)
./gradlew :composeApp:assemblePhoneRelease

# Android (TV)
./gradlew :composeApp:assembleTvRelease

# All tests
./gradlew :composeApp:allTests
```

---

## 🏗️ Tech Stack

- **Language**: Kotlin Multiplatform
- **UI**: Jetpack Compose Multiplatform
- **Playback**: libmpv (Desktop), Media3 ExoPlayer (Android)
- **Networking**: Ktor
- **Extensions**: Mangayomi-style extension framework
- **Build**: Gradle 8.13

---

## 🤝 Contributing

PRs and ideas are welcome! Check the [AGENTS.md](AGENTS.md) for detailed architecture and dev notes.

---

<p align="center">
  Copyright © 2026 Anisurge. Built for the anime community.
</p>

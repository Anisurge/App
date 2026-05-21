# AniSurge 0.10.1 — Build 36

**Full Changelog**: [v0.10.0...0.10.1](https://github.com/Anisurge/App/compare/v0.9.25...v0.10.1)

<p align="center">
  <video controls playsinline width="100%" style="max-width: 960px; border-radius: 12px;">
    <source src="https://raw.githubusercontent.com/Anisurge/App/main/ad.mp4" type="video/mp4" />
    <a href="https://raw.githubusercontent.com/Anisurge/App/main/ad.mp4">Watch the AniSurge promo</a>
  </video>
</p>

## Shop frames & chat avatars

- Prefetch owned shop APNG frames after login and when opening Settings / Store
- Restore Coil fallback for animated frame previews (fixes dark/broken frame thumbnails)
- Parallel chat frame prefetch for smoother live chat avatars
- Owned frames loaded via `GET /v1/shop/owned`

---

# AniSurge 0.10.0 — Build 44

**Full Changelog**: [v0.9.25...0.10.0](https://github.com/Anisurge/App/compare/v0.9.25...0.10.0)


## ��️ macOS Support

AniSurge now runs on **macOS**! New `build-macos.sh` script produces unsigned DMG and portable zip files for Intel & Apple Silicon. CI builds for **Windows, Linux, and macOS** in parallel.

## 🎭 Profile Pictures & Animated Avatars

- **Custom PFPs** — upload your own profile picture via Settings (stored server-side, served through BFF)
- **Animated APNG profile frames** — browse and purchase animated frames from the Shop catalog; displayed as overlay on your avatar
- **Layered avatar rendering** — draw order: outer decoration → PFP image → main animated frame (overlays effects over the face)
- **APNG support** — uses `apng-core` library on Android & desktop (Skia `Codec` only exposes one APNG frame, so frames are decoded manually)
- Frame is overlaid ~1.16× the avatar size for a nice border effect
- Shop frames served from BFF (`GET /v1/media/pfp-animated/{id}.png`)
- Static PFPs served from `GET /v1/media/pfps/:filename`

## 💬 Community Chat

- Global live chat room with proper lifecycle management
- **Animated APNG profile overlays** in chat — per-user equipped `chatAvatarFrame` / `chatAvatarOuter` displayed as rings and outer decorations
- Chat image upload → Catbox CDN (`POST /v1/chat/upload`)
- Sidebar chat access & improved layout
- Simplified WebSocket auth flow
- BFF username snapshotted per message; avatar from live BFF profile (`customPfpUrl`)

## 🛍️ Shop & Berries Economy

- **Shop catalog** — browse/purchase animated profile picture frames and cosmetics
- **Redeem codes** — enter codes to unlock items
- **Berries settings tab** — view balance, daily claim, redeem codes, earn summary (฿ symbol)
- **Rewards system** — earn Berries via daily claims, episode progress, first chat/day (env-capped)
- **Custom profile picture upload** — dedicated Change Profile Picture button in Settings
- Shop admin UI at `/admin` or `/staff` (staff email allowlist-gated)

## ▶️ Playback Overhaul

- **Intro/Outro Skip** — fully integrated Aniskip service (auto-detect opening/ending)
- **Auto-next** — improved automatic episode advancement
- HLS improvements — PNG-wrapped MPEG-TS segment handling, local media export
- **RxFFmpeg** — local media remuxing for downloads
- Refined seek handling & auto-skip reactivity
- Skip ranges shown on progress bar (no text labels beside timestamps)

## 📱 Continue Watching

- Per-episode progress rows — BFF keeps all rows (no deletion when ReAnime prunes)
- Smart sorting: `latestPerAnime()` = highest episode → most progress → recency
- Estimated duration fallback + refresh on leaving watch
- Full list view showing every row

## 📚 Library Sync

- New `LibrarySyncService` for bidirectional watchlist/continue-watching sync
- Two-way merge — newer `lastUpdated` wins, push local-only rows to ReAnime
- Sync triggers: pull-to-refresh on watchlist, loading continue watching (60s debounce)

## 🔐 Auth & Integrations

- Dual tokens: `project_r_…` (Project-R) + Anisurge JWT (BFF routes)
- Improved async session state & integration sync
- MAL/AniList — push local tokens once instead of wipe on restore
- Legacy session cleanup on startup (ReAnime-only tokens cleared)
- Enhanced error handling & retry logic

## 🔗 Deep Links & Notifications

- `anisurge://` deep links → watch or info screens
- `MainActivity` `singleTop` — skip splash on warm resume, saved rotation, or intents
- `onNewIntent` launches notification content without recreating NavHost/ViewModels

## 🧭 Navigation & UI

- **New pill-shaped blur bottom nav** (20% blur) replacing liquid glass bar
- Mobile navbar with labels & brand purple active state
- Expanded hero carousel toggle in Appearance settings
- Enhanced mobile settings navigation & support section

## 📥 Downloads

- Reworked download management with better task handling & UI feedback
- Failed downloads → remove queue entry + offer fresh download (no resume stall)
- Android Anitaku: skip remote HLS export, remux local TS via RxFFmpeg

## 🔍 Search

- Search filters with improved API mapping

## 🛠️ Internal

- BFF admin UI bundled in source (no CDN dependency)
- CI workflow improvements for version resolution & artifact collection
- `.gitignore` updated for Xcode/workspace files
- Dependencies bumped & codebase cleanup

---

| Quick Stats | |
|-------------|---|
| Commits | 58 |
| Contributors | 2 |
| Files changed | **157** |
| Lines added | **11,088** |
| Lines removed | **1,617** |
| New platforms | **macOS** (DMG + portable zip) |
| Build | 35 → **44** |

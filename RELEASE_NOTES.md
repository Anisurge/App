# Anisurge v0.12.37 — What's New

Build 65

Full changes: [v0.12.15...v0.12.37](https://github.com/Anisurge/App/compare/v0.12.15...v0.12.37)

## Discord Rich Presence

- **Discord RPC** — show what you're watching on your Discord profile, with anime art as the rich image
- **WebView login** — sign in to Discord directly inside the app (no manual token extraction needed); desktop fallback with paste
- **Auto-reconnect** — gateway reconnects automatically on disconnect
- **BFF sync** — your Discord token syncs to your account so you don't need to re-link after reinstall

## Anime Discovery & Info

- **Franchise/relations viewer** — explore sequels, prequels, side stories, and spin-offs from any anime info page
- **Anime themes** — opening and ending theme songs with artist info, linked to each anime
- **Discovery tools** — find similar anime and explore by connected series

## Backup & Restore

- **Full backup** — export your settings, watchlist, and library to a portable file
- **Restore** — import from a backup file to get everything back after a clean install or device switch
- **Android file picker** — native file save/load on Android; desktop uses system dialogs; iOS ready

## Release Notifications

- **Personalized alerts** — get notified when a new episode of a followed anime releases
- **Schedule integration** — notifications link directly to the schedule with your followed anime highlighted
- **Notification preferences** — toggle notifications per-anime or globally in Settings

## Auto-Tracking & Library Sync

- **Auto-tracking sync** — background sync for MyAnimeList and AniList progress (configurable interval)
- **Improved integration sync** — token management for MAL and AniList through BFF
- **Better watch history sync** — progress is pushed to both BFF and ReAnime, with fresh sync on pull-to-refresh
- **Watchlist sync fix** — your library status is accurately reflected after login

## Player Enhancements

- **Anime4K shaders** — 12 bundled GLSL shader modes (Fast, Heavy, Very Heavy) for upscaling and restoration
- **Color presets** — Natural, Anime, Cinema, Vivid, Dark Room, Warm, Cool, and Grayscale
- **Visual controls** — brightness, contrast, saturation, gamma, hue, debanding, interpolation, dithering, tone mapping, scaling
- **Subtitle tools** — font, text color, outline, background, opacity, position, and independent audio/subtitle delay
- **Double-tap seek** — configurable 5/10/15/20/30s skip durations
- **Sleep timer** — 15/30/45/60/90 min or end-of-episode
- **Screenshots** — capture frames with rendered subtitles, saved to `Downloads/Anisurge/Screenshots`
- **Global defaults** — player enhancement defaults configurable in Settings; in-player changes remain session-only

## Downloads

- **Remote HLS remux** — Android downloads now support streams that need remote processing (for encrypted or provider-specific HLS)
- **Better MKV muxing** — improved subtitle handling and final remux reliability
- **Clean failures** — failed downloads are removed cleanly (no stuck retry), start fresh
- **Direct MP4 improvements** — better detection, headers, buffering, and timeout handling
- **Suzu downloads** — use direct streams and headers from API
- **Server accuracy** — downloads no longer silently switch servers after failure

## Streaming & Playback Fixes

- **Episode position fix** — episodes no longer start at the previous episode's position
- **Server fallback** — automatic fallback to another compatible server when playback fails
- **Suzu streaming** — improved direct stream handling with proper request headers
- **Reduced proxying** — less unnecessary stream proxying, kept only for providers that need special segment handling
- **Server display** — more accurate currently-selected server indicator

## Crash Reporter & Resilience

- **Crash screen** — clear error display with copy/restart/report options on both Android and desktop
- **Async reporting** — crash reports are sent in the background without blocking the crashed thread
- **WebView crash handling** — gracefully handles WebView renderer process loss (Discord login, etc.)
- **WebView API 36 fix** — fixed compile crash on Android SDK 36 (`RenderProcessGoneDetail` rename)

## Surge2Gether (Watch Together)

- **Renamed** — Watch2Gether is now Surge2Gether throughout the app
- **External subtitles** — load external subtitle files in sync rooms (host and participants)
- **Room refresh** — fixed behavior after leaving a room

## Library & Watchlist

- **Watchlist search** — search and filter by title, genre, format, and status
- **Schedule watchlist actions** — add/remove library entries directly from schedule
- **My List fix** — loading against updated BFF watchlist service
- **Watchlist response handling** — support for different response shapes and folder names

## Linux Desktop

- **AppImage conversion** — added `AppRun`, `.desktop`, and icon for easy conversion of the jpackage app-image to a proper AppImage via `appimagetool`
- **Gradle task** — run `./gradlew :composeApp:prepareAppImageDir` to set up the directory

## AI Chat

- **Surge AI** — personal AI assistant with 20 messages/day (100 for premium)
- **Markdown responses** — formatted replies with anime card links
- **Search integration** — AI can look up anime in the catalog
- **Chat history** — conversations persist across restarts

## Games (Berries)

- **Berry Wheel** — spin to win
- **Coin Flip** — double or nothing
- **Minesweeper** — reveal tiles and cash out before hitting a mine
- **Daily cap** — 100 berries/day to keep it fair

## Community Chat

- **Staff badges** — colored name gradients for staff
- **Chat images** — share images in global chat
- **Image zoom** — pinch-to-zoom in fullscreen

## Other Improvements

- Seasonal anime section on home page
- Customizable home feed layout
- Announcements hub
- Orientation crash fix in fullscreen playback
- Surge2Gether room list improvements

## Under the Hood

- BFF auth with dual tokens (Project-R + Anisurge JWT)
- FFmpeg bundling for Windows MKV remux
- Trimmed JRE modules for smaller downloads
- Refactored Discord Rich Presence with cross-platform architecture (Android/Desktop/iOS)
- New tests for auto-tracking sync, franchise models, and schedule watchlist matching

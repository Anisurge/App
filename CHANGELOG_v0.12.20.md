# Anisurge v0.12.20 - What's New

Build 63

Full changes: [v0.12.15...v0.12.20](https://github.com/Anisurge/App/compare/v0.12.15...v0.12.20)

## Player Enhancements

- Added 12 bundled Anime4K shader modes on Android and desktop, with Fast, Heavy, and Very Heavy options.
- Added Natural, Anime, Cinema, Vivid, Dark Room, Warm, Cool, and Grayscale color presets.
- Added brightness, contrast, saturation, gamma, hue, debanding, interpolation, dithering, tone mapping, scaling, video sync, and decoder controls.
- Added warnings before enabling demanding shader modes that may increase heat, battery use, or dropped frames.
- Player enhancement defaults can now be configured globally in Settings, while in-player changes remain session-only.

## Player Utilities

- Added independent audio and subtitle delay controls.
- Added subtitle font, text color, outline, background, opacity, and position controls.
- Added configurable double-tap seek durations: 5, 10, 15, 20, or 30 seconds.
- Added sleep timers for 15, 30, 45, 60, or 90 minutes, plus an end-of-episode option.
- Added screenshots with rendered subtitles on Android and desktop.
- Screenshots are saved to `Downloads/Anisurge/Screenshots` using the anime name, episode number, and playback timestamp.

## Streaming & Playback

- Fixed episodes incorrectly starting at the previous episode's saved position.
- The player now keeps progress isolated by anime and episode.
- Added automatic fallback to another compatible server when playback fails to start.
- Improved server ordering and made the currently selected server display more accurately.
- Improved Suzu handling for direct streams and required request headers.
- Reduced unnecessary stream proxying while retaining it for providers that need special segment handling.
- Improved episode-list availability while stream details are still loading.

## Downloads

- Added Android remote HLS remux support for streams that could not previously be saved for offline playback.
- Improved encrypted and provider-specific HLS download compatibility.
- Download progress now continues visibly through remote stream processing instead of appearing stuck around 55% or jumping directly to finalization.
- Improved direct MP4 detection, request headers, buffering, and timeout behavior.
- Suzu downloads now use the direct streams and headers returned by the API.
- Downloads no longer silently switch to a different server after a failure.
- Failed downloads are removed cleanly so a fresh download can be started.
- Improved final MKV muxing and subtitle handling.

## Library, Schedule & Anime Info

- Fixed My List loading against the updated watchlist service.
- Fixed watchlist search and advanced filtering across titles, genres, formats, and statuses.
- Improved support for different watchlist response shapes and folder names.
- Anime information pages now correctly show whether an anime is already bookmarked.
- Added watchlist actions directly to schedule entries.
- Improved watchlist synchronization and error handling.

## Surge2Gether

- Renamed Watch2Gether to Surge2Gether throughout the app.
- Added external subtitle loading for hosts and participants.
- Improved room refresh behavior and cleanup after leaving a room.

## Other Improvements

- Added persistent global defaults and reset actions for the new player controls.
- Added Anime4K and AnymeX attribution to third-party notices.
- Improved Android and desktop player consistency.

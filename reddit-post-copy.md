title: Anisurge v0.12.37 — Discord RPC, Anime4K shaders, backup/restore, and more!

body:

Hey everyone, been a while since the last proper update post. Here's what's new in Anisurge from v0.12.15 through v0.12.37.

**Discord Rich Presence**
You can now link your Discord account (in-app WebView login, no manual token nonsense) and show exactly what you're watching on your profile — anime art included.

**Player got a major upgrade**
- 12 bundled Anime4K shader modes (Fast/Heavy/Very Heavy) for upscaling
- Color presets: Natural, Anime, Cinema, Vivid, Dark Room, Warm, Cool, Grayscale
- Full subtitle styling: font, color, outline, background, opacity, position
- Independent audio & subtitle delay controls
- Double-tap seek (configurable 5–30s)
- Sleep timer (15/30/45/60/90 min or end-of-episode)
- Screenshots with rendered subtitles

**Anime discovery**
Info pages now show franchise trees (sequels, prequels, side stories), opening/ending themes with song details, and staff. Also added a dedicated "Seasonal" section on home.

**Backup & Restore**
Export your settings + library to a file and restore it later — useful for clean installs or switching devices.

**Release notifications**
Get notified when a new episode of a followed anime drops. Toggle per-anime or globally.

**Auto-tracking sync**
Background sync for MAL and AniList progress. Configurable interval in Settings.

**Crash reporter rewrite**
The old one could hang on crash and block the app. Now it saves the report first, sends it async, and shows a proper crash screen with copy/restart/report buttons.

**Other bits**
- Surge2Gether with external subtitle support
- Server fallback when playback fails
- Episode position bleed fixed (no more starting ep 5 at ep 4's timestamp)
- Download reliability improvements
- Watchlist search & filtering

**Desktop Linux**
AppRun and .desktop files are now included in the repo — run appimagetool on the jpackage output for a proper AppImage. `./gradlew :composeApp:prepareAppImageDir` to set it up.

Downloads and full changelog at https://anisurge.lol

As always, feedback and bug reports are appreciated. Cheers.

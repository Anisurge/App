# Anisurge 0.11.15 — Build 54

**Full Changelog**: [v0.11.14...v0.11.15](https://github.com/Anisurge/App/compare/v0.11.14...v0.11.15)

## 💬 Episode Comments (Now Live!)

- Removed the "Coming Soon" overlay — comments are fully accessible
- Animated profile frames (APNG rings/outers) on every comment
- Post comments, reply, like/dislike, spoiler tags, image insert
- Comments persist when switching episodes or changing sort order
- **BFF fix:** fixed `ANY(($1,$2))` SQL crash that caused comments to disappear on sort change

## 🛡️ VirusTotal False Positive Cleanup

- Trimmed unused JRE modules (`java.sql`, `java.naming`, `java.management`)
- Added Windows code signing guide + false-positive submission helper

## 🎬 Desktop Download Fix

- FFmpeg static binary now bundled in Windows CI for MKV remux
- `muxToMkv()` finds bundled ffmpeg first, falls back to system install

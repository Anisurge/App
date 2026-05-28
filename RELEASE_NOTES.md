# Anisurge 0.11.0 — Build 42

**Full Changelog**: [v0.10.46...v0.11.0](https://github.com/Anisurge/App/compare/v0.10.46...v0.11.0)

## 🐛 Bug Fixes

### Settings no longer auto-revert
- Fixed auto-skip intro/outro toggles reverting after being turned off
- Fixed default language (sub/dub) preference reverting back automatically
- Local settings are now the source of truth — server sync no longer overwrites your local choices during in-flight saves

### Deep links no longer log you out
- Fixed cold-start logout when opening a shared anime link (e.g. from a friend or notification)
- Auth check now properly waits before redirecting to the login screen
- Added `Checking` session state so the app doesn't assume you're logged out before verification completes

### Continue Watching ordering
- Most recently watched show now correctly floats to the top of Continue Watching
- Previously, if you watched ep 3 today but ep 8 had more progress from last week, the card sorted by ep 8's old timestamp
- Now sorts by the most recent activity across ANY episode of that anime

## 💬 Chat Improvements

### Animated multi-color premium gradient
- Premium usernames now flow through a vibrant 5-color rainbow gradient (coral, gold, green, blue, purple)
- Animation is faster and continuous (1.2s loop) — no more static two-color gradient
- All premium users get the gradient automatically (no server-side config needed)

### Surge bot is now Pro
- Surge bot displays the PRO badge in chat
- Bot gets a special purple/cyan/pink/mint animated gradient on its name

### Video PFPs load faster
- Video profile pictures (like Surge bot's) now load and play almost instantly
- Reduced startup buffer from ~2.5s to 200ms
- Halved network timeouts for faster failure recovery
- Doubled the video PFP cache (64MB → 128MB) — more videos stay cached
- Video PFPs now actually display in chat messages (previously fell back to static icon)

## ⚙️ CI/CD

- Fixed GitHub API rate limit in Windows MPV download (authenticated requests)
- Disabled artifact re-compression for faster uploads
- 1-day artifact retention to reduce storage pressure

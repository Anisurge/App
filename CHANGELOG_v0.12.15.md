# Anisurge v0.12.15 — What's New

### AI Chat
- **Surge AI** — new personal AI chat with 20 messages/day free (100 for premium). Uses real Anisurge anime cards instead of images, and understands your watchlist
- **Markdown support** — AI responses support bold, italic, code formatting, and tap-to-navigate anime links
- **Search integration** — AI can look up anime facts in real-time using the catalog
- **Chat history** — your AI conversations are saved locally and persist across restarts

### Games (Berries)
- **Berry Wheel** — spin the wheel to win berries
- **Coin Flip** — double your bet or lose it
- **Minesweeper** — reveal tiles, hit a mine and lose your bet, cash out before then
- **Daily cap** — games are capped at 100 berries earned per day to keep it fair

### Community Chat
- **Staff badges** — staff members have colored name gradients so you know who to ping
- **Chat images** — share images in the global chat (staff canmoderate)
- **Image zoom** — pinch-to-zoom on chat images in fullscreen

### Watch2Gether (Watch Together)
- **Sync rooms** — create or join a watch room and sync playback with friends
- **Room list** — browse available rooms from the W2G tab
- **Host controls** — host can control playback for everyone

### Downloads
- **Better retry** — failed downloads can be retried without getting stuck
- **Download actions** — separate delete (remove file) and remove (clear from list) options
- **Storage permission fix** — properly requests permissions for Android 11+

### Comments
- **Episode comments** — comment on specific episodes with threaded replies
- **Like/dislike** — upvote or downvote comments
- **Sort options** — view by newest, oldest, or best

### Stickers
- **Video stickers** — animated stickers that play while scrolling
- **Shop stickers** — buy animated frames and stickers from the store

### Player & Playback
- **Intro/outro skip** — auto-skip op/ED when timestamps are available from aniskip.com
- **Playback settings** — autoplay, auto-next, skip intro/outro, default language all persist
- **Orientation fix** — no more crash when rotating device in fullscreen
- **Crash screen** — if the player crashes, you can copy error details, restart, or send a report

### Library & Sync
- **Status tabs** — filter your watchlist by Watching, Planning, Completed, Paused, Dropped
- **Two-way sync** — your MAL/AniList imports merge properly with existing entries
- **Library status** — watchlist shows which folder each anime is in

### Other Fixes
- **Latest page** — filter by Sub, Dub, or All
- **Desktop sign-in fix** — login/signup works properly on macOS and Windows
- **Anime relations** — seasons and sequels are properly linked on anime detail pages
- **Seasonal anime** — new "Seasonal" section on home page
- **Home layout** — customize which rows appear on your home feed
- **Announcements hub** — dedicated page for in-app announcements

### Under the Hood
- Migrated to **BFF (Backend-for-Frontend)** auth with dual tokens
- Added **FFmpeg bundling** for Windows so MKV remux works without external dependencies
- Trimmed JRE modules — smaller downloads and faster startup

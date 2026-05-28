# Anisurge 0.10.46 — Build 41

**Full Changelog**: [v0.10.1...v0.10.46](https://github.com/Anisurge/App/compare/v0.10.1...v0.10.46)

## 💎 Premium & Razorpay Integration

- **Razorpay payments** — purchase premium subscriptions directly in-app
- **Premium badge** — active premium users see badge with expiration date on profile
- **50-episode batch downloads** for premium users
- Hide "extend premium" buttons for already-active subscribers

## 📺 Episode Watch Progress

- **Red progress indicator** on episode cards in Anime Info screen showing how much you've watched
- Improved progress item matching for different ID formats across sources

## 💬 Chat Improvements

- **Chat message grouping** — consecutive messages from the same user are visually grouped
- **Chat cooldown timer** — rate limiting with visible countdown
- **Bot support options** in chat member sheet
- Chat member sheet opens fully expanded (no partial state) for better scrolling
- Refined chat profile watchlist layout
- Premium animated avatars in chat profiles
- Silent karma tracking

## 📥 Batch Downloads

- **Batch download all episodes** — limit raised to 1000 episodes
- **Search filter** in season batch picker dialog
- **Select all / Clear all** options for quick batch selection

## 🔗 LunarAnime & Connect

- **LunarAnime OAuth** — connect your Lunar account from the new Connect settings tab
- Brand logo images with fallback for ReAnime and LunarAnime in settings
- Optimized ReAnime connection flow

## 🖥️ Desktop Fixes

- **Profile photo upload** — fixed CMYK and odd JPEG profile handling on desktop
- **Offline playback on Windows** — fixed local file playback
- Dropped AnimeTV submodule (unused)

## 🛍️ Shop & Store

- Fixed store page layout issues
- Fixed Shop frames OOM crash on low-memory devices

## 🔧 Dashboard & Admin

- Rebuilt admin dashboard with improved layout
- Backup sync fixes (cache, persisted, and API pointers)

## ⚙️ CI/CD

- Fixed GitHub API rate limit in Windows MPV download (authenticated requests)
- Disabled artifact re-compression for faster uploads
- 1-day artifact retention to reduce storage pressure

---

| Quick Stats | |
|-------------|---|
| Commits | 40 |
| Files changed | **103** |
| Lines added | **13,667** |
| Lines removed | **2,432** |
| Build | 36 → **41** |

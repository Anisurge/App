## Learned User Preferences

- Always edit files directly when asked to make changes — do not just describe or propose changes
- Use BUN or PNPM (not npm) for package management, unless the user says "Im subbu :)"
- When the user says "refer @api.md", read the API documentation file before making any changes
- The user prefers sub/dub variants as separate server entries (e.g., "suzu-dub", "animepahe-dub") rather than in-player audio switching
- Prefer a strong blur overlay (around half strength) with centered "Coming Soon" text over removing or hiding unfinished features
- The user communicates in a casual/abbreviated style — interpret intent over literal wording
- When asked to test on device, install the app and verify via the connected Android device using adb
- Prefer curl checks against expected API responses (per `api.md`) before starting API integration/parsing work; confirm the app's actual HTTP URL when debugging streaming, downloads, or list-shaped screens
- BFF auth: in-app signup via `POST /v1/auth/signup`; on logout invalidate the Anisurge JWT the same way Project-R invalidates its token
- For Anisurge-owned backend data, prefer self-hosted **raw PostgreSQL** (not Mongo or Scylla for that layer); run Postgres **separately** from the API container so DB stays up when the API restarts
- Do not store user passwords in the Anisurge BFF/Postgres — mirror Project-R auth and link rows via `external_user_id` only
- Custom username and profile photo (`PATCH /v1/me`, `POST /v1/me/pfp/upload`) are **Anisurge-only** — do not sync to Project-R/ReAnime

## Learned Workspace Facts

- This is a Kotlin Multiplatform (KMP) Compose app called "anisuge" under package `to.kuudere.anisuge`
- App uses Ktor HTTP client with `ignoreUnknownKeys = true`; on Android, BFF JSON `PATCH`/`POST` must set `contentType(ContentType.Application.Json)` or body serialization fails
- Auth uses Bearer tokens in `SessionInfo` via `SessionStore`; BFF migration uses **dual tokens** — `project_r_…` for Project-R catalog/social and an **Anisurge JWT** for BFF routes (`/v1/auth/*`, `/v1/me`, watchlist, continue, progress)
- API hosts: anime/catalog/search/comments stay on `https://api.reanime.to/api/v1` (`AppComponent.BASE_URL`); auth, signup, library, and profile mirror use the Anisurge BFF at `https://db.anisurge.qzz.io` (`services/anisurge-api/api.md`); `STREAMING_URL` and `anisurge.lol` hosts unchanged
- Home screen rows load from `GET {BASE_URL}/home` (`latest_aired`, `new_on_site`, `upcoming`); the paginated latest list uses `GET {BASE_URL}/home/latest-aired`
- Streaming uses `AppComponent.STREAMING_URL` (`action=batch_scrape&anilistId=…`); Suzu needs proxy/player Referer; AnimePahe is usually direct. Public site: streaming server catalog and app updates on `anisurge.lol`
- Watchlist folder strings expected by API/UI are uppercase: `WATCHING`, `PLANNING`, `COMPLETED`, `PAUSED`, `DROPPED`
- Project-R (`api.reanime.to`) uses ScyllaDB at scale; Anisurge BFF (`services/anisurge-api/`, separate git repo, Bun+Hono+Drizzle) uses Postgres with `external_user_id`; production `https://db.anisurge.qzz.io`
- On login/sync the BFF upserts full Project-R profile/settings, stores Discord/AniList/MAL integration tokens server-side, pulls watchlist + continue watching into Postgres, and on writes defaults to forwarding to ReAnime (`forwardToReanime`) so both sides stay in sync
- Custom pfps: BFF local disk (`PFP_STORAGE_DIR`, e.g. `/app/data/pfps` via Coolify volume); public `GET /v1/media/pfps/:filename` (no JWT — mount `mediaRoutes` on app root, not under shared `/v1` auth). Catbox is not used for profile upload
- Live chat shows BFF username + avatar; username is snapshotted per message at send time; avatar prefers live BFF profile (`customPfpUrl`) when history is loaded
- App code layout: services under `data/services/`, models under `data/models/`, screens/ViewModels under `screens/`; Navigation Compose path params (e.g. `info/{animeId}`); Gradle tasks from docs/automation often use `--no-configuration-cache`

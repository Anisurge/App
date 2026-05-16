## Learned User Preferences

- Always edit files directly when asked to make changes — do not just describe or propose changes
- Use BUN or PNPM (not npm) for package management, unless the user says "Im subbu :)"
- When the user says "refer @api.md", read the API documentation file before making any changes
- The user prefers sub/dub variants as separate server entries (e.g., "suzu-dub", "animepahe-dub") rather than in-player audio switching
- Prefer a strong blur overlay (around half strength) with centered "Coming Soon" text over removing or hiding unfinished features
- The user communicates in a casual/abbreviated style — interpret intent over literal wording
- When asked to test on device, install the app and verify via the connected Android device using adb
- Prefer curl checks against expected API responses (per `api.md`) before starting API integration/parsing work; confirm the app's actual HTTP URL when debugging streaming, downloads, or list-shaped screens
- Account registration is website-first: the Register flow directs users to **https://reanime.to** to create an account, then they sign in inside the app with the same credentials
- For Anisurge-owned backend data, prefer self-hosted **raw PostgreSQL** (not Mongo or Scylla for that layer); run Postgres **separately** from the API container so DB stays up when the API restarts
- Do not store user passwords in the Anisurge BFF/Postgres — mirror Project-R auth and link rows via `external_user_id` only

## Learned Workspace Facts

- This is a Kotlin Multiplatform (KMP) Compose app called "anisuge" under package `to.kuudere.anisuge`
- App uses Ktor HTTP client with `ignoreUnknownKeys = true` for JSON deserialization
- Auth uses Bearer token (`Authorization: Bearer <token>`) stored in `SessionInfo(token)` via `SessionStore`; the app migrated from legacy cookie auth to Project-R with Bearer tokens
- REST and streaming entry points are defined in `composeApp/src/commonMain/kotlin/to/kuudere/anisuge/AppComponent.kt` (`BASE_URL` is `https://api.reanime.to/api/v1`, `STREAMING_URL` for batch scrape); canonical HTTP contract reference is `api.md` at the project root
- Home screen rows load from `GET {BASE_URL}/home` (`latest_aired`, `new_on_site`, `upcoming`); the paginated latest list uses `GET {BASE_URL}/home/latest-aired`
- Streaming batch scrape calls `AppComponent.STREAMING_URL` with `action=batch_scrape&anilistId={id}&episode={epi}&source={server}` — Suzu needs a proxy/player Referer wiring; AnimePahe streams are typically direct playable URLs
- Public Anisurge site APIs: streaming server catalog at `https://www.anisurge.lol/api/v1/streaming/servers`; app update checks at `GET https://www.anisurge.lol/api/app/updates` (via `UpdateService.checkUpdate()`)
- Watchlist folder strings expected by API/UI are uppercase: `WATCHING`, `PLANNING`, `COMPLETED`, `PAUSED`, `DROPPED`
- Friend's anime backend (Project-R at `api.reanime.to`) uses ScyllaDB at scale; Anisurge's own layer links users with `external_user_id` (Project-R user id) in Postgres
- `services/anisurge-api/` is a Bun + Hono + Drizzle **BFF** (Docker runs the API only; Postgres is external); intended as a **separate git repo** from the main App monorepo
- On login/sync the BFF upserts full Project-R profile/settings, stores Discord/AniList/MAL integration tokens server-side, pulls watchlist + continue watching into Postgres, and on writes defaults to forwarding to ReAnime (`forwardToReanime`) so both sides stay in sync
- App code layout: services under `data/services/`, models under `data/models/`, screens/ViewModels under `screens/`; Navigation Compose path params (e.g. `info/{animeId}`); Gradle tasks from docs/automation often use `--no-configuration-cache`

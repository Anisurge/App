## Learned User Preferences

- Always edit files directly when asked to make changes — do not just describe or propose changes
- Use BUN or PNPM (not npm) for package management, unless the user says "Im subbu :)"
- When the user says "refer @api.md", read the API documentation file before making any changes
- The user prefers sub/dub variants as separate server entries (e.g., "suzu-dub", "animepahe-dub") rather than in-player audio switching
- Prefer a strong blur overlay (around half strength) with centered "Coming Soon" text over removing or hiding unfinished features
- The user communicates in a casual/abbreviated style — interpret intent over literal wording
- When asked to test on device, install the app and verify via the connected Android device using adb
- Prefer curl checks against expected API responses (per `api.md`) before renaming fields or parsers; confirm the app's actual HTTP URL when debugging streaming, downloads, or list-shaped screens

## Learned Workspace Facts

- This is a Kotlin Multiplatform (KMP) Compose app called "anisuge" under package `to.kuudere.anisuge`
- App uses Ktor HTTP client with `ignoreUnknownKeys = true` for JSON deserialization
- Auth uses Bearer token (`Authorization: Bearer <token>`) stored in `SessionInfo(token)` via `SessionStore`; the app migrated from legacy cookie auth to Project-R with Bearer tokens
- API networking config lives in `composeApp/src/commonMain/kotlin/to/kuudere/anisuge/data/network/ApiConfig.kt`; canonical contract reference is `api.md` at the project root
- Streaming scrape URL shape: `https://fetch.anisurge.lol/api?action=batch_scrape&anilistId={id}&episode={epi}&source={server}` — Suzu needs a proxy/player Referer wiring; AnimePahe streams are typically direct playable URLs
- Public streaming server catalog/list used for picker/settings aligns with `https://www.anisurge.lol/api/v1/streaming/servers` (config should stay in sync with deployed site/API)
- Backend Project-R APIs use the `anisurge.qzz.io` subdomain layout (avoid inventing unrelated hostnames)
- Watchlist folder strings expected by API/UI are uppercase: `WATCHING`, `PLANNING`, `COMPLETED`, `PAUSED`, `DROPPED`
- Lightweight client analytics/install pings exist to support aggregate usage on the admin dashboard (keep payloads minimal/non-identifying in code reviews)
- Service implementations live under `composeApp/src/commonMain/kotlin/to/kuudere/anisuge/data/services/`; shared models under `data/models/`; screens and ViewModels under `screens/`
- Builds in this repo often pass `--no-configuration-cache` when running Gradle wrapper tasks from docs/automation
- Navigation uses Jetpack Navigation Compose with path parameters (for example `info/{animeId}`)
- Typical API payloads use nested DTO shapes (object titles such as `{english, romaji, native}`, cover images with `{medium, large}`, etc.)

## Learned User Preferences

- Always edit files directly when asked to make changes — do not just describe or propose changes
- Use BUN or PNPM (not npm) for package management, unless the user says "Im subbu :)"
- When the user says "refer @api.md", read the API documentation file before making any changes
- The user prefers sub/dub variants as separate server entries (e.g., "suzu-dub", "animepahe-dub") rather than in-player audio switching
- The user prefers blur overlays with centered "Coming Soon" text over removing or hiding unfinished features
- The user communicates in a casual/abbreviated style — interpret intent over literal wording
- When asked to test on device, install the app and verify via the connected Android device using adb
- The user wants curl-based API verification before modifying app code to match endpoints
- Always confirm what API endpoint the app is actually calling before debugging streaming/download issues

## Learned Workspace Facts

- This is a Kotlin Multiplatform (KMP) Compose app called "anisuge" under package `to.kuudere.anisuge`
- App uses Ktor HTTP client with `ignoreUnknownKeys = true` for JSON deserialization
- Auth uses Bearer token (`Authorization: Bearer <token>`) stored in `SessionInfo(token)` via `SessionStore`
- API base URL and config are in `composeApp/src/commonMain/kotlin/to/kuudere/anisuge/data/network/ApiConfig.kt`
- The app migrated from a legacy cookie-based auth system to Project-R API with Bearer tokens
- API docs reference file is `api.md` at the project root — contains all endpoint specifications
- Streaming endpoint: `https://fetch.anisurge.lol/api?action=batch_scrape&anilistId={id}&episode={epi}&source={server}`
- Suzu streams require a proxy server with Referer header; AnimePahe streams are direct MP4
- Backend domain is under `anisurge.qzz.io` subdomain structure (not arbitrary URLs)
- Services are in `composeApp/src/commonMain/kotlin/to/kuudere/anisuge/data/services/`
- Models are in `composeApp/src/commonMain/kotlin/to/kuudere/anisuge/data/models/`
- Screens/ViewModels are in `composeApp/src/commonMain/kotlin/to/kuudere/anisuge/screens/`
- Build command uses `--no-configuration-cache` flag
- Navigation uses Jetpack Navigation Compose with path parameters (e.g., `info/{animeId}`)
- API response models use nested objects (e.g., `title: {english, romaji, native}`, `cover_image: {medium, large}`)

# Project-R API Documentation

> **High-Fidelity Enterprise Anime Content Delivery Network**
> Version: `1.0`

---

## Base URL

```
https://api.reanime.to/api/v1
```

---

## Authentication

This API uses **Bearer Token** authentication. Pass your token in the `Authorization` header:

```
Authorization: Bearer <your_token>
```
for testing use:
```
token: project_r_95335f628cb7eb6b94fed6a5dbb9b1cc127055feaf2d6027e7b6580508c735cc
```
Tokens are prefixed:
- `project_r_` — Full session token (obtained via login/signup)
- `project_o_` — Scoped OAuth token (obtained via OAuth authorization flow)

Endpoints marked 🔒 require a valid Bearer token.

---

## Table of Contents

- [Anime](#anime)
- [Anime Comments](#anime-comments)
- [Auth](#auth)
- [OAuth](#oauth)
- [User](#user)
- [Watchlist](#watchlist)
- [Watch](#watch)
- [Anime Streaming](#anime-streaming)
- [Notifications](#notifications)
- [Community](#community)
- [Requests](#requests)
- [Public](#public)
- [Contact](#contact)
- [Schedule](#schedule)
- [Data Models](#data-models)
- [AniSurge site — app usage ping](#anisurge-site--app-usage-ping-public)

---

## Anime

### Get Home Page Data

```
GET /home
```

Returns three sections: latest aired episodes (12), new additions to the site (12), and upcoming anime (12). If authenticated, also returns `has_continue_watching` boolean (not cached).

**Query Parameters**

| Parameter | Type | Description |
|-----------|------|-------------|
| `lang` | string | Filter `latest_aired` by language: `sub`, `dub` (default: all) |
| `Authorization` | header (string) | Bearer token (optional — injects `has_continue_watching`) |

**Responses**

| Status | Description |
|--------|-------------|
| `200` | OK |

---

### Get Latest Aired Episodes (Paginated)

```
GET /home/latest-aired
```

Returns paginated latest aired episodes using ScyllaDB native paging.

**Query Parameters**

| Parameter | Type | Description |
|-----------|------|-------------|
| `lang` | string | Filter by language: `sub`, `dub` (default: all) |
| `limit` | integer | Page size (default: 12, max: 100) |
| `cursor` | string | Opaque base64 paging state from previous page's `next_cursor` |

**Responses**

| Status | Description |
|--------|-------------|
| `200` | OK |

---

### Get Anime Data

```
GET /anime/{slug}
```

Load full unified dataset of an anime including episodes, artworks, and metadata. Supports SEO-friendly slugs (e.g. `solo-leveling-1001` or `solo-leveling:1001`) or raw numeric IDs. Results are cached in Redis for high-performance delivery.

**Optional Auth**: Pass a Bearer token to inject per-user `watchlist` (folder, notes, dates) and `watch_progress` (latest episode, currentTime, server) into the response without polluting the shared cache.

**Path Parameters**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `slug` | string | ✅ | SEO Slug or Numeric ID |

**Query Parameters**

| Parameter | Type | Description |
|-----------|------|-------------|
| `include_episodes` | boolean | Include full episodes list (default: false) |
| `tz` | string | IANA timezone for `sub_release` `airing_at` (default: `UTC`) |
| `Authorization` | header (string) | Bearer token (optional — injects watchlist & watch_progress) |

**Responses**

| Status | Description |
|--------|-------------|
| `200` | OK |
| `400` | Malformed Slug |
| `404` | Anime Not Found |

---

### Get Episode List

```
GET /anime/{slug}/episodes
```

Returns the paginated episode list for an anime. Supports both SEO slugs and raw IDs. Results are cached for 1 hour to optimize delivery for high-traffic shows.

**Path Parameters**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `slug` | string | ✅ | SEO Slug or Numeric ID |

**Query Parameters**

| Parameter | Type | Description |
|-----------|------|-------------|
| `limit` | integer | Page size (default: 30, max: 100) |
| `offset` | integer | Offset (default: 0) |
| `filler` | boolean | Include filler episodes (default: true) |
| `recap` | boolean | Include recap episodes (default: true) |

**Responses**

| Status | Description |
|--------|-------------|
| `200` | OK |
| `400` | Bad Request |

---

### Get Anime Recommendations

```
GET /anime/{slug}/recommendations
```

Provides highly accurate, content-based anime recommendations using a multi-stage pipeline:

1. **Candidate Discovery** — Uses MeiliSearch to find a pool of candidates sharing genres/tags.
2. **Metadata Scoring** — Performs in-memory analysis weighting specific Tags (**5.0 pts**) higher than standard Genres (**2.0 pts**).
3. **Exclusion** — Automatically filters out the current anime and any related works (sequels/prequels).

**Path Parameters**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `slug` | string | ✅ | SEO Slug or ID (e.g. `solo-leveling-1001` or `1001`) |

**Responses**

| Status | Description |
|--------|-------------|
| `200` | List of up to 10 recommendations |
| `400` | Malformed Identifier |
| `404` | Anime Not Found |

---

### Get Top Anime

```
GET /top/anime
```

Returns top anime for different time periods (this hour, today, week, month). Results are cached in Redis with ScyllaDB fallback.

**Query Parameters**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `period` | string | `today` | Time period: `this hour`, `today`, `week`, `month` |
| `limit` | integer | `10` | Number of results (max: 20) |

**Responses**

| Status | Description |
|--------|-------------|
| `200` | OK |
| `500` | Internal Server Error |

---

### Search Anime

```
GET /search
```

Faceted full-text typo-tolerant search across the Meilisearch cluster.

**Query Parameters**

| Parameter | Type | Description |
|-----------|------|-------------|
| `q` | string | Search Term |
| `limit` | integer | Pagination Limit (default: 20) |
| `offset` | integer | Pagination Offset |
| `format` | string | `TV`, `TV_SHORT`, `MOVIE`, `OVA`, `ONA`, `SPECIAL`, `MUSIC` |
| `status` | string | `Finished`, `Releasing`, `Not Yet Released`, `Cancelled` |
| `genre` | string | One or more genres, comma-separated (e.g. `Action`, `Romance,Comedy`) |
| `season` | string | `WINTER`, `SPRING`, `SUMMER`, `FALL` |
| `year` | integer | Season year (e.g. `2024`) |
| `sort` | string | `popularity_desc`, `score_desc`, `latest_desc`, `year_desc`, `episodes_desc` |
| `character` | string | Character Name |
| `staff` | string | Staff / Voice Actor Name |
| `studio` | string | Studio Name |
| `country` | string | Country Code: `JP`, `KR`, `CN` |
| `watchlist` | string | `exclude`, `include`, `only` *(requires auth)* |
| `facets` | boolean | Include facet distributions |

**Responses**

| Status | Description |
|--------|-------------|
| `200` | OK |
| `500` | Meilisearch Engine Timeout |

---

### Facet Value Search (Typeahead)

```
GET /search/facets/{type}
```

Returns matching facet values for a given field. Use for autocomplete dropdowns.

**Path Parameters**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `type` | string | ✅ | Facet field: `tags`, `genres`, `characters`, `staff`, `studios` |

**Query Parameters**

| Parameter | Type | Description |
|-----------|------|-------------|
| `q` | string | Search query (empty returns top values by count) |

**Responses**

| Status | Description |
|--------|-------------|
| `200` | OK — `{ [field]: string[] }` |
| `400` | Bad Request |
| `500` | Internal Server Error |

---

### Get Monthly Schedule

```
GET /schedule
```

Retrieves anime schedules binned by calendar day for a month, converted to the user's timezone. If `year` and/or `month` are omitted, defaults to current year/month in the requested timezone.

**Query Parameters**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `tz` | string | `UTC` | User Local Timezone (IANA) |
| `year` | integer | *(current)* | Designated Year |
| `month` | integer | *(current)* | Designated Month (1–12) |

**Responses**

| Status | Description |
|--------|-------------|
| `200` | OK |

---

## Anime Comments

### Get Comments

```
GET /anime/comments/{slug}/{ep}
```

Fetch episode comments for a specific anime slug and episode. Use `parent_id` to lazy-load replies for a specific parent comment.

> ⚠️ Path uses the public slug (or numeric ID), **not** external document IDs.

**Path Parameters**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `slug` | string | ✅ | Anime public slug or numeric ID (e.g. `solo-leveling-1001` or `1001`) |
| `ep` | integer | ✅ | Episode Number |

**Query Parameters**

| Parameter | Type | Description |
|-----------|------|-------------|
| `parent_id` | string | Parent comment ID for reply-only listing |
| `page` | integer | Page number (default: 1) |
| `sort` | string | Sort mode: `new`, `oldest`, `best` (default: `new`) |
| `nid` | string | Notification ID to highlight and prioritize target thread |

**Responses**

| Status | Description |
|--------|-------------|
| `200` | OK |

---

### Create Comment 🔒

```
POST /anime/comment
```

Create an episode comment or reply with dynamic notification triggers and mention hydration natively ported from Project-R UI.

**Request Body** — `application/json`

| Field | Type | Description |
|-------|------|-------------|
| `anime` | string | Anime slug or ID |
| `ep` | integer | Episode number |
| `content` | string | Comment text |
| `parentCommentId` | string | Parent comment ID (for replies) |

**Responses**

| Status | Description |
|--------|-------------|
| `200` | OK |

---

### Toggle Comment Like 🔒

```
POST /anime/comment/like
```

Natively apply or remove secure upvotes and downvotes tracking.

**Request Body** — `application/json`

| Field | Type | Description |
|-------|------|-------------|
| `commentId` | string | Target comment ID |
| `likeState` | string | `"like"`, `"dislike"`, `"none"` (neutralizes interaction) |

**Responses**

| Status | Description |
|--------|-------------|
| `200` | OK |

---

## Auth

### Create Account

```
POST /auth/signup
```

Register a new account with a 3–10 character alphanumeric username.

**Request Body** — `application/json`

| Field | Type | Description |
|-------|------|-------------|
| `username` | string | 3–10 alphanumeric characters |
| `email` | string | Valid email address |
| `password` | string | Account password |

**Responses**

| Status | Description |
|--------|-------------|
| `201` | Created |
| `400` | Bad Request |
| `500` | Database Failure |

---

### Login

```
POST /auth/login
```

Log into an existing account using email or username to acquire a `project_r_` token.

**Request Body** — `application/json`

| Field | Type | Description |
|-------|------|-------------|
| `identifier` | string | Email or username |
| `password` | string | Account password |

**Responses**

| Status | Description |
|--------|-------------|
| `200` | OK — returns token |
| `401` | Invalid Credentials |

---

### Logout 🔒

```
POST /auth/logout
```

Terminates the token and completely flushes it from DragonflyDB caching and ScyllaDB persistence.

**Responses**

| Status | Description |
|--------|-------------|
| `200` | OK |
| `401` | Unauthorized |

---

### Request Password Reset

```
POST /auth/forgot-password
```

Sends a 6-digit OTP to the registered email for the given username or email. Always returns `200` to prevent user enumeration.

**Request Body** — `application/json`

```json
{ "identifier": "username_or_email" }
```

**Responses**

| Status | Description |
|--------|-------------|
| `200` | OK (always) |

---

### Reset Password with OTP

```
POST /auth/reset-password
```

Verifies the 6-digit OTP and sets a new password. Max 3 wrong attempts before token is invalidated.

**Request Body** — `application/json`

```json
{ "identifier": "string", "otp": "string", "newPassword": "string" }
```

**Responses**

| Status | Description |
|--------|-------------|
| `200` | OK |
| `400` | Bad Request |
| `429` | Too Many Requests (OTP exhausted) |

---

## OAuth

### List Available Scopes

```
GET /auth/oauth/scopes
```

Returns the full list of scopes an OAuth app can request. No auth required.

**Responses**

| Status | Description |
|--------|-------------|
| `200` | OK |

---

### Register OAuth App 🔒

```
POST /auth/oauth/apps
```

Registers a new OAuth application. Returns `client_id` and a **one-time** plaintext `client_secret`.

**Request Body** — `application/json`

| Field | Type | Description |
|-------|------|-------------|
| `app_name` | string | Application name |
| `description` | string | App description |
| `redirect_urls` | string[] | Allowed redirect URLs |
| `scopes` | string[] | Allowed scopes |

**Responses**

| Status | Description |
|--------|-------------|
| `201` | Created — includes `client_id` and one-time `client_secret` |
| `400` | Bad Request |

---

### List OAuth Apps 🔒

```
GET /auth/oauth/apps
```

Returns all OAuth apps registered by the authenticated user.

**Responses**

| Status | Description |
|--------|-------------|
| `200` | OK |

---

### Delete OAuth App 🔒

```
DELETE /auth/oauth/apps/{client_id}
```

Deletes an OAuth app owned by the authenticated user and revokes all its active sessions.

**Path Parameters**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `client_id` | string | ✅ | Client ID of the app to delete |

**Responses**

| Status | Description |
|--------|-------------|
| `200` | OK |
| `403` | Forbidden |
| `404` | Not Found |

---

### Authorize OAuth Token 🔒

```
POST /auth/oauth/authorize
```

Validates a full `project_r_` session then issues a scoped `project_o_` token. The requested scopes must be a **subset** of the app's registered `allowed_scopes`. The `redirect_url` must exactly match one of the app's registered `redirect_urls`.

**Request Body** — `application/json`

| Field | Type | Description |
|-------|------|-------------|
| `client_id` | string | OAuth app client ID |
| `redirect_url` | string | Must match a registered redirect URL |
| `scopes` | string[] | Requested scopes (subset of allowed) |

**Responses**

| Status | Description |
|--------|-------------|
| `200` | OK — returns scoped `project_o_` token |
| `400` | Bad Request |
| `403` | Forbidden |

---

### Revoke OAuth Token 🔒

```
POST /auth/oauth/revoke
```

Revokes a specific OAuth session token. Can be called by the token owner using either their full session or the OAuth token itself.

**Request Body** — `application/json`

```json
{ "token": "project_o_..." }
```

**Responses**

| Status | Description |
|--------|-------------|
| `200` | OK |
| `400` | Bad Request |
| `403` | Forbidden |

---

### List My OAuth Sessions 🔒

```
GET /auth/oauth/sessions
```

Returns all active OAuth sessions for the authenticated user across all apps.

**Responses**

| Status | Description |
|--------|-------------|
| `200` | OK |

---

## User

### Get Profile 🔒

```
GET /user
```

Retrieves all graph data dynamically for the account making the request.

**Responses**

| Status | Description |
|--------|-------------|
| `200` | OK |
| `401` | Token Failed / Expired |
| `500` | Database Failed |

---

### Update Profile 🔒

```
PATCH /user
```

Updates user profile information. Username must be 3–10 alphanumeric characters. Handles username changes with lookup table synchronization.

**Request Body** — `application/json`

| Field | Type | Description |
|-------|------|-------------|
| `username` | string | New username (3–10 alphanumeric) |
| `displayName` | string | Display name |
| `bio` | string | Profile bio |
| `website` | string | Personal website URL |
| `timezone` | string | IANA timezone string |

**Responses**

| Status | Description |
|--------|-------------|
| `200` | OK |

---

### Change Password 🔒

```
POST /user/password
```

Updates user password after verifying the current one.

**Request Body** — `application/json`

| Field | Type | Description |
|-------|------|-------------|
| `currentPassword` | string | Existing password |
| `newPassword` | string | New password |

**Responses**

| Status | Description |
|--------|-------------|
| `200` | OK |

---

### Get User Settings 🔒

```
GET /user/settings
```

Returns the settings for the authenticated user, or defaults if none exist.

**Responses**

| Status | Description |
|--------|-------------|
| `200` | OK — returns `UserSettings` object |

---

### Update User Settings 🔒

```
PATCH /user/settings
```

Updates specific fields in user settings. Validates existence and types. Send a partial `UserSettings` object.

**Request Body** — `application/json` — partial `UserSettings`

| Field | Type | Description |
|-------|------|-------------|
| `autoNext` | boolean | Auto-play next episode |
| `autoPlay` | boolean | Auto-play on load |
| `defaultLang` | boolean | Default language preference |
| `skipIntro` | boolean | Auto-skip intro |
| `skipOutro` | boolean | Auto-skip outro |
| `showComments` | boolean | Show comments panel |
| `publicWatchlist` | boolean | Make watchlist public |
| `syncPercentage` | number | Progress sync threshold percentage |

**Responses**

| Status | Description |
|--------|-------------|
| `200` | OK — returns updated `UserSettings` |

---

## Watchlist

### Get User Watchlist 🔒

```
GET /watchlist
```

Retrieves the user's watchlist with pagination and search. Use query `q` or advanced filters for Meilisearch-powered search. Otherwise returns cached results from DragonflyDB.

**Query Parameters**

| Parameter | Type | Description |
|-----------|------|-------------|
| `q` | string | Search term (triggers Meilisearch) |
| `folder` | string | Filter by status: `WATCHING`, `PLANNING`, `COMPLETED`, `PAUSED`, `DROPPED` |
| `format` | string | Filter by format (TV, MOVIE, OVA, etc.) — comma-separated |
| `status` | string | Filter by anime status (Releasing, Finished, etc.) — comma-separated |
| `season` | string | Filter by season: `WINTER`, `SPRING`, `SUMMER`, `FALL` — comma-separated |
| `year` | integer | Filter by season year |
| `genre` | string | Filter by genres — comma-separated |
| `tag` | string | Filter by tags — comma-separated |
| `min_score` | integer | Filter by minimum average score (1–100) |
| `limit` | integer | Page size (default: 20, max: 100) |
| `offset` | integer | Offset (default: 0) |
| `sort` | string | Sort option. Cached: `last_updated` (default), `folder`, `anime_id`. Search: `score_desc`, `score_asc`, `popularity_desc`, `popularity_asc`, `updated_desc` |

**Responses**

| Status | Description |
|--------|-------------|
| `200` | OK |

---

### Add / Update Watchlist 🔒

```
POST /watchlist
```

Adds or updates an anime in the user's collection. Supports slug-based IDs (e.g. `solo-leveling-1`).

**Auto-dates**: `startedAt` is auto-set when folder → `WATCHING`. `completedAt` is auto-set when folder → `COMPLETED`. Pass them explicitly to override.

**Request Body** — `application/json`

| Field | Type | Description |
|-------|------|-------------|
| `animeId` | string | Integer ID or slug (e.g. `solo-leveling-1`) |
| `folder` | string | `WATCHING`, `PLANNING`, `COMPLETED`, `PAUSED`, `DROPPED` |
| `notes` | string | Optional personal notes |

**Responses**

| Status | Description |
|--------|-------------|
| `200` | OK — returns `WatchlistEntry` |
| `400` | Invalid ID, folder, or body |
| `401` | Unauthorized |
| `404` | Anime not found |

---

### Remove from Watchlist 🔒

```
DELETE /watchlist/{animeId}
```

Deletes an anime from the user's collection. Supports slug-based IDs.

**Path Parameters**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `animeId` | string | ✅ | Anime ID or Slug |

**Responses**

| Status | Description |
|--------|-------------|
| `200` | OK |

---

## Watch

### Get Anime Watch Info

```
GET /watch/{slug}
```

Returns tailored user-specific watch data including basic meta, active folder, and episode progress. Supports automated episode overrides via Notification tokens (`nid`) and manual selection (`ep`). Delimiters supported: `-` and `:`.

**Path Parameters**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `slug` | string | ✅ | SEO Slug or Numeric ID |

**Query Parameters**

| Parameter | Type | Description |
|-----------|------|-------------|
| `nid` | string | Notification ID to force focus on a specific episode |
| `ep` | string | Specific episode number or `latest` to override automatic resume |
| `tz` | string | IANA timezone for `sub_release` `airing_at` (default: `UTC`) |

**Responses**

| Status | Description |
|--------|-------------|
| `200` | OK |
| `400` | Bad Request |
| `404` | Not Found |

---

### Save Watch Progress 🔒

```
POST /watch/progress
```

Saves or updates watch progress for a specific episode. Auto-creates a `continueId` on first save.

- Skips saving if less than **10 seconds** or **1%** has been watched (prevents noise).
- All episode rows are kept permanently — nothing is ever deleted — enabling future rewatch analytics.

**Request Body** — `application/json`

| Field | Type | Description |
|-------|------|-------------|
| `animeId` | string | Slug or integer ID |
| `episodeId` | string | Episode identifier (e.g. `"ep-1"`) |
| `currentTime` | number | Seconds elapsed |
| `duration` | number | Total episode duration in seconds |
| `language` | string | `"sub"` or `"dub"` |
| `server` | string | Streaming server used |

**Responses**

| Status | Description |
|--------|-------------|
| `200` | OK |

---

### Get Continue Watching 🔒

```
GET /watch/continue
```

Returns the user's Continue Watching feed. Supports search/filter via Meilisearch or simple DB fallback.

**Query Parameters**

| Parameter | Type | Description |
|-----------|------|-------------|
| `q` | string | Search term (triggers Meilisearch) |
| `format` | string | Filter by format (TV, MOVIE, OVA, etc.) — comma-separated |
| `status` | string | Filter by anime status (Releasing, Finished, etc.) — comma-separated |
| `season` | string | Filter by season (WINTER, SPRING, SUMMER, FALL) — comma-separated |
| `year` | integer | Filter by season year |
| `genre` | string | Filter by genres — comma-separated |
| `tag` | string | Filter by tags — comma-separated |
| `sort` | string | `last_watched` (default), `progress`, `title`, `score_desc`, `score_asc`, `popularity_desc`, `popularity_asc` |
| `limit` | integer | Page size (default: 20, max: 50) |
| `offset` | integer | Offset (default: 0) |

**Responses**

| Status | Description |
|--------|-------------|
| `200` | OK — body includes `data` array of continue rows |

**`data[]` item fields (client contract)**

| Field | Type | Notes |
|-------|------|--------|
| `animeId` | string | Slug for navigation |
| `continueId` | string | Stable row id |
| `episodeId` | string | Same convention as Save Watch Progress (e.g. `"ep-4"`) — **canonical episode key** |
| `episode` | integer | *(optional)* When omitted, clients MUST derive display/resume episode from `episodeId` (parse after `ep-`). |
| `currentTime` | number | Seconds watched |
| `duration` | number | Episode length (seconds) |
| `progress` | number | Optional; may be a **percentage** (0–100) for list UIs — do not confuse with `currentTime` |
| `server` | string | Last streaming server |
| `language` | string | `"sub"` or `"dub"` |
| `lastUpdated` | string | ISO timestamp |
| `anime` | object | Embedded `AnimeItem` for poster/title |

---

### Remove from Continue Watching 🔒

```
DELETE /watch/continue/{animeId}/{episodeId}
```

Soft-deletes an entry from the Continue Watching feed (sets `removed=true`). Progress data is preserved.

**Path Parameters**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `animeId` | string | ✅ | Anime ID or slug |
| `episodeId` | string | ✅ | Episode ID |

**Responses**

| Status | Description |
|--------|-------------|
| `200` | OK |

---

## Anime Streaming

### Batch Scrape Streams

```
GET https://h7sxsvuavf79b1x16zcf7nz2.n92dev.us.kg/api?action=batch_scrape&anilistId={anilistId}&episode={episodeNumber}&source={serverId}
```

Fetches streaming data for a given anime episode from a specific server. Returns sub and dub streams with direct playlist URLs, quality labels, and required headers.

**Query Parameters**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `action` | string | ✅ | Must be `batch_scrape` |
| `anilistId` | integer | ✅ | AniList ID of the anime |
| `episode` | integer | ✅ | Episode number |
| `source` | string | ✅ | Server ID (see Available Servers below). One ID per provider; the response includes both `sub` and `dub` objects where applicable. |

**Available Servers**

| Server ID | Label | Notes | Status |
|-----------|-------|-------|--------|
| `suzu` | Suzu | Sub + Dub in one response (`sub` / `dub` keys) | ✅ Active |
| `animepahe` | AnimePahe | Sub + Dub in one response | ✅ Active |
| `zen` | Zen | Dual | ❌ Down |
| `zen2` | Zen-2 | Dual | ❌ Down |
| `allmanga` | allmanga| Sub + Dub | ❌ Down |

**Client catalog:** use one entry per `Server ID` (e.g. `suzu`), with type **Sub + Dub** / `sub_dub`. The app picks the `sub` or `dub` block from JSON based on user language / UI; do not use separate fake IDs like `suzu-dub` as the `source` parameter.

**Response Structure**

The response is a JSON object with `sub` and `dub` keys. Each contains:

| Field | Type | Description |
|-------|------|-------------|
| `providerId` | string | Provider URL for the anime |
| `episodeId` | string | Provider URL for the specific episode |
| `streams` | array | Array of stream objects |
| `subtitles` | string | Subtitle data (empty string if none) |

**Stream Object**

| Field | Type | Description |
|-------|------|-------------|
| `url` | string | Direct m3u8 playlist URL |
| `quality` | string | Quality label (e.g. `"1080p (SUB)"`, `"720p (DUB)"`, `"HardSub"`) |
| `headers` | object | Required HTTP headers for playback |

**Headers Object**

| Field | Type | Description |
|-------|------|-------------|
| `Referer` | string | Required Referer header for the stream URL |
| `User-Agent` | string | Required User-Agent (AnimePahe only) |

**Responses**

| Status | Description |
|--------|-------------|
| `200` | OK — returns streaming data |
| `400` | Bad Request — missing or invalid parameters |
| `404` | No streams found |
| `503` | Server is down (zen / zen2) |

---

### Example Response — Suzu Server

```json
{
  "sub": {
    "providerId": "https://senshi.live/anime/59983",
    "episodeId": "https://senshi.live/episode-embeds/59983/1",
    "streams": [
      {
        "url": "https://ninstream.com/tKTNpvdzmwLtKMfhAb1M8Q/1778088639/f4733b85-7cbf-4d29-88bf-21727413ba3f/playlist.m3u8",
        "quality": "Dub",
        "headers": { "Referer": "https://senshi.live" }
      },
      {
        "url": "https://ninstream.com/JvHtoURIBPLBoc8sq5wArA/1778088639/4e52ae7d-b174-400f-9fb0-40f37dd067e9/playlist.m3u8",
        "quality": "HardSub",
        "headers": { "Referer": "https://senshi.live" }
      }
    ],
    "subtitles": ""
  },
  "dub": {
    "providerId": "https://senshi.live/anime/59983",
    "episodeId": "https://senshi.live/episode-embeds/59983/1",
    "streams": [
      {
        "url": "https://ninstream.com/tKTNpvdzmwLtKMfhAb1M8Q/1778088639/f4733b85-7cbf-4d29-88bf-21727413ba3f/playlist.m3u8",
        "quality": "Dub",
        "headers": { "Referer": "https://senshi.live" }
      },
      {
        "url": "https://ninstream.com/JvHtoURIBPLBoc8sq5wArA/1778088639/4e52ae7d-b174-400f-9fb0-40f37dd067e9/playlist.m3u8",
        "quality": "HardSub",
        "headers": { "Referer": "https://senshi.live" }
      }
    ],
    "subtitles": ""
  }
}
```

---

### Example Response — AnimePahe Server

```json
{
  "sub": {
    "providerId": "https://animepahe.pw/anime/9a3b7bed-e62f-f53a-73e0-cb1b63ed8329",
    "episodeId": "https://animepahe.pw/play/9a3b7bed-e62f-f53a-73e0-cb1b63ed8329/5fd6eec1c6682ef032886955c786106d4c7284c3da6d427e311ef830e246a909",
    "streams": [
      {
        "url": "https://vault-16.owocdn.top/stream/16/10/f66689979a0e0c56103015d8d1668b31c61a6a2eea9120b9a5df32e2503ab55f/uwu.m3u8",
        "quality": "360p (SUB)",
        "headers": {
          "Referer": "https://kwik.cx/",
          "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        }
      },
      {
        "url": "https://vault-16.owocdn.top/stream/16/10/c19a146773f01c38de317c8816ceadde01ed2d692d5d802a0a5a45fc88b31072/uwu.m3u8",
        "quality": "720p (SUB)",
        "headers": {
          "Referer": "https://kwik.cx/",
          "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        }
      },
      {
        "url": "https://vault-16.owocdn.top/stream/16/10/2ebf618f355d39f9f86b11777401c3af2441c1d39c50680a8081134f5a547af1/uwu.m3u8",
        "quality": "1080p (SUB)",
        "headers": {
          "Referer": "https://kwik.cx/",
          "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        }
      }
    ],
    "subtitles": ""
  },
  "dub": {
    "providerId": "https://animepahe.pw/anime/9a3b7bed-e62f-f53a-73e0-cb1b63ed8329",
    "episodeId": "https://animepahe.pw/play/9a3b7bed-e62f-f53a-73e0-cb1b63ed8329/5fd6eec1c6682ef032886955c786106d4c7284c3da6d427e311ef830e246a909",
    "streams": [
      {
        "url": "https://vault-99.owocdn.top/stream/99/01/6f0c2c0ed73d68a75d5d1d3eef7afd553d9ada7d2e7e7dd39d2aae8af4a5ec12/uwu.m3u8",
        "quality": "360p (DUB)",
        "headers": {
          "Referer": "https://kwik.cx/",
          "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        }
      },
      {
        "url": "https://vault-99.owocdn.top/stream/99/01/7db1b00e26d546975e68d723146aa21123f373f5c8ac246a5890a065e2b90953/uwu.m3u8",
        "quality": "720p (DUB)",
        "headers": {
          "Referer": "https://kwik.cx/",
          "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        }
      },
      {
        "url": "https://vault-99.owocdn.top/stream/99/01/24af453213da43f59e27b6a3f5b21cb68c3ace1f949f5af15876d4136bd89feb/uwu.m3u8",
        "quality": "1080p (DUB)",
        "headers": {
          "Referer": "https://kwik.cx/",
          "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        }
      }
    ],
    "subtitles": ""
  }
}
```

---

## Notifications

### Get Unread Notification Count 🔒

```
GET /notifications/count
```

Returns the total unread notification count and per-tab breakdown (`anime`, `community`, `system`).

**Responses**

| Status | Description |
|--------|-------------|
| `200` | OK |
| `401` | Unauthorized |

---

### Get User Notifications 🔒

```://go.nyt92.eu.org/website
GET /notifications/{type}
```

Fetch and format user notifications matching SvelteKit logic.

**Path Parameters**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `type` | string | ✅ | Notification category: `community`, `anime`, `system` |

**Query Parameters**

| Parameter | Type | Description |
|-----------|------|-------------|
| `page` | integer | Page number (default: 1) |
| `typeQuery` | string | Specific belongs filter via `?type=...` (e.g. `ANIME_COMMENTS`) |

**Responses**

| Status | Description |
|--------|-------------|
| `200` | OK |
| `401` | Unauthorized |

---

### Mark All Notifications as Read 🔒

```
POST /notifications/mark-all-read
```

Marks user notifications as read, optionally filtered by type.

**Request Body** — `application/json` *(optional)*

| Field | Type | Description |
|-------|------|-------------|
| `type` | string | Empty string or `"community"`, `"anime"`, `"system"` |

**Responses**

| Status | Description |
|--------|-------------|
| `200` | OK |
| `401` | Unauthorized |

---

## Community

### Get Community Stats

```
GET /community/stats
```

Get community statistics (online count, members).

**Responses** — `200` OK

---

### Get Community Categories

```
GET /community/categories
```

Get all community categories with post counts.

**Responses** — `200` OK

---

### Get Trending Posts

```
GET /community/trending
```

Get pinned/trending posts for carousel.

**Responses** — `200` OK

---

### Get Community Posts

```
GET /community/posts
```

Get posts with filtering and sorting.

**Query Parameters**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `sort` | string | `hot` | Sort: `hot`, `new`, `top`, `old` |
| `category` | string | `all` | Category slug filter |
| `limit` | integer | `10` | Limit |
| `offset` | integer | `0` | Offset |

**Responses** — `200` OK

---

### Get Single Post

```
GET /community/posts/{id}
```

Get a single post by ID.

**Path Parameters**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `id` | string | ✅ | Post ID or slug |

**Responses** — `200` OK

---

### Create Post 🔒

```
POST /community/posts
```

Create a new community post.

**Request Body** — `application/json`

| Field | Type | Description |
|-------|------|-------------|
| `title` | string | Post title |
| `content` | string | Post body |
| `category` | string | Category slug |
| `flair` | string | Post flair |
| `images` | string[] | Image URLs |
| `pinned` | boolean | Pin the post (admin) |
| `spoiler` | boolean | Mark as spoiler |

**Responses** — `201` Created

---

### Vote on Post 🔒

```
POST /community/posts/{id}/vote
```

Upvote or downvote a post.

**Path Parameters**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `id` | string | ✅ | Post ID |

**Request Body** — `application/json`

| Field | Type | Description |
|-------|------|-------------|
| `vote` | integer | `1` (upvote), `-1` (downvote), `0` (remove) |

**Responses** — `200` OK

---

### Toggle Pin Post 🔒 *(Admin only)*

```
POST /community/posts/{id}/pin
```

Pin or unpin a post.

**Path Parameters**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `id` | string | ✅ | Post ID |

**Responses** — `200` OK

---

### Get Leaderboard

```
GET /community/leaderboard/users
```

Get top contributors by aura.

**Query Parameters**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `period` | string | `all` | Period: `all`, `weekly`, `monthly` |

**Responses** — `200` OK

---

### Get Community Unread Post Count

```
GET /community/unread-count
```

Get unread community post count for the current user.

**Responses** — `200` OK

---

## Requests

### Get User Requests 🔒

```
GET /requests
```

Get all anime requests for the authenticated user.

**Responses**

| Status | Description |
|--------|-------------|
| `200` | OK — array of `RequestEntry` |
| `401` | Unauthorized |

---

### Add Request 🔒

```
POST /requests/{animeId}
```

Request an anime that is not yet available on the site. Only works if `can_request=true` for the anime.

**Path Parameters**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `animeId` | string | ✅ | Anime ID (slug or numeric) |

**Responses**

| Status | Description |
|--------|-------------|
| `200` | OK — returns `RequestEntry` |
| `400` | Invalid ID or cannot request |
| `401` | Unauthorized |
| `409` | Already requested |

---

### Remove Request 🔒

```
DELETE /requests/{animeId}
```

Remove a request for an anime.

**Path Parameters**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `animeId` | string | ✅ | Anime ID (slug or numeric) |

**Responses**

| Status | Description |
|--------|-------------|
| `200` | OK |
| `400` | Invalid ID |
| `401` | Unauthorized |
| `404` | Request not found |

---

## Public

### Get Public Profile

```
GET /public/{username}
```

Retrieves public information for a user.

**Path Parameters**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `username` | string | ✅ | Username (e.g. `@kmax`) |

**Responses** — `200` OK

---

### Get Public Watchlist

```
GET /public/{username}/watchlist
```

Retrieves a user's watchlist by username. Respects private profile and public watchlist settings. Uses the same pagination and filtering as the authenticated watchlist.

**Path Parameters**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `username` | string | ✅ | Username (optionally prefixed with `@`) |

**Query Parameters**

| Parameter | Type | Description |
|-----------|------|-------------|
| `q` | string | Search term |
| `folder` | string | Filter by folder: `WATCHING`, `PLANNING`, etc. |
| `limit` | integer | Limit |
| `offset` | integer | Offset |

**Responses** — `200` OK

---

## Contact

### Submit Contact Message

```
POST /contact
```

Submit a support/contact message from the user.

**Request Body** — `application/json`

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `title` | string | ✅ | Message subject |
| `message` | string | ✅ | Message body |
| `email` | string | ❌ | Sender email (optional) |

**Responses**

| Status | Description |
|--------|-------------|
| `200` | OK |
| `400` | Invalid body |
| `500` | Internal server error |

---

## Data Models

### `WatchlistEntry`

| Field | Type | Description |
|-------|------|-------------|
| `itemId` | string | Unique entry ID |
| `userId` | string | Owner user ID |
| `animeId` | string | Anime identifier |
| `folder` | string | Status: `WATCHING`, `PLANNING`, `COMPLETED`, `PAUSED`, `DROPPED` |
| `notes` | string | User notes |
| `startedAt` | `FuzzyDate` | Date started watching |
| `completedAt` | `FuzzyDate` | Date completed |
| `createdAt` | string (ISO 8601) | Entry creation timestamp |
| `lastUpdated` | string (ISO 8601) | Last update timestamp |

---

### `FuzzyDate`

| Field | Type | Description |
|-------|------|-------------|
| `year` | integer | Year |
| `month` | integer | Month (1–12) |
| `day` | integer | Day (1–31) |

---

### `UserSettings`

| Field | Type | Description |
|-------|------|-------------|
| `autoNext` | boolean | Auto-play next episode |
| `autoPlay` | boolean | Auto-play on load |
| `defaultLang` | boolean | Default language preference |
| `skipIntro` | boolean | Auto-skip intro |
| `skipOutro` | boolean | Auto-skip outro |
| `showComments` | boolean | Show comments panel |
| `publicWatchlist` | boolean | Make watchlist public |
| `syncPercentage` | number | Progress sync threshold |

---

### `RequestEntry`

| Field | Type | Description |
|-------|------|-------------|
| `userId` | string | Requesting user ID |
| `animeId` | string | Requested anime ID |
| `status` | string | `PENDING` or `SOLVED` |
| `createdAt` | string (ISO 8601) | Request timestamp |
| `solvedAt` | string (ISO 8601) | Resolution timestamp |

---

### `PostCommentRequest`

| Field | Type | Description |
|-------|------|-------------|
| `anime` | string | Anime slug or ID |
| `ep` | integer | Episode number |
| `content` | string | Comment text |
| `parentCommentId` | string | Parent ID (for replies) |

---

### `CreateOAuthAppRequest`

| Field | Type | Description |
|-------|------|-------------|
| `app_name` | string | App name |
| `description` | string | App description |
| `redirect_urls` | string[] | Allowed redirect URLs |
| `scopes` | string[] | Allowed scopes |

---

### `AuthorizeOAuthRequest`

| Field | Type | Description |
|-------|------|-------------|
| `client_id` | string | OAuth app client ID |
| `redirect_url` | string | Must match a registered redirect URL |
| `scopes` | string[] | Requested scopes (must be subset of allowed) |

---

*Generated from Project-R OpenAPI spec v1.0 · Base URL: `https://api.reanime.to/api/v1`*

---

## AniSurge site — app usage ping (public)

The mobile/desktop **AniSurge** client may send anonymous heartbeats to the marketing site (not Project-R). Data is used only for aggregate admin metrics (active installs by platform/version). **No** account id, email, username, or Project-R token is included.

### Record app heartbeat

```
POST https://www.anisurge.lol/api/v1/app/ping
Content-Type: application/json
```

**Body (JSON)**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `installId` | string (UUID) | yes | Stable random id per app install (DataStore) |
| `platform` | string | yes | `android` or `desktop` |
| `appVersion` | string | yes | Client version string |
| `os` | string | no | Short OS hint (e.g. Android release or desktop OS/arch), max 128 chars |

**Responses**

| Status | Description |
|--------|-------------|
| `200` | JSON `{ "ok": true }` — ping stored |
| `400` | Invalid body |
| `429` | Rate limited (per client IP and per `installId`) |
| `500` | Server or database error |

Rate limits are enforced server-side (Redis/Dragonfly when configured). The client should send **at most about once per 24 hours** per install.
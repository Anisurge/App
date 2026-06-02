# Watch2gether (W2G) — Implementation Prompt

## Overview

Add a Watch2gether feature to Anisurge where users can create or join real-time synchronized anime watching rooms. Each room has a synced player (controlled by the host), an isolated in-room chat with no rate limits, and a public listing for discovery.

---

## Repository Structure

Two repos need changes:

1. **`Anisurge-api-server`** (BFF) — Bun + Hono + Drizzle ORM + Postgres
2. **`App`** (KMP) — Kotlin Multiplatform Compose app

---

## BFF Changes (`Anisurge-api-server`)

### 1. Database Schema — `src/db/schema.ts`

Add two tables following the existing patterns (`uuid` PKs, `defaultRandom()`, `cascade` deletes, `createdAt` timestamps):

**`w2g_rooms` table:**

| Column | Type | Notes |
|---|---|---|
| `id` | `uuid().primaryKey().defaultRandom()` | |
| `hostUserId` | `uuid().notNull().references(() => users.id, { onDelete: "cascade" })` | |
| `inviteCode` | `text().notNull().unique()` | Short 6-char code like "X7K2M" |
| `animeId` | `text().notNull()` | Slug/ID for the anime |
| `anilistId` | `integer()` | For resolving metadata |
| `malId` | `integer()` | For aniskip integration |
| `episodeNumber` | `integer().notNull().default(1)` | |
| `server` | `text().notNull().default("suzu")` | Streaming server key |
| `language` | `text()` | "sub" or "dub" |
| `quality` | `text()` | Quality string |
| `animeTitle` | `text()` | Snapshot for room listing |
| `animePoster` | `text()` | Poster URL for room listing |
| `passwordHash` | `text()` | bcrypt hash; null = no password |
| `status` | `text().default("open").notNull()` | "open" or "closed" |
| `createdAt` | `timestamp({ withTimezone: true }).defaultNow().notNull()` | |
| `lastActiveAt` | `timestamp({ withTimezone: true }).defaultNow().notNull()` | |
| `playerState` | `jsonb().$type<{ playing: boolean; currentTime: number; timestamp: number }>().default({ playing: false, currentTime: 0, timestamp: 0 }).notNull()` | |

Add indexes:
- `index("w2g_rooms_status_active_idx")` on `(table.status, table.lastActiveAt.desc())`
- `index("w2g_rooms_invite_code_idx")` on `(table.inviteCode)` (unique already covers this)

**`w2g_room_members` table:**

| Column | Type | Notes |
|---|---|---|
| `id` | `uuid().primaryKey().defaultRandom()` | |
| `roomId` | `uuid().notNull().references(() => w2gRooms.id, { onDelete: "cascade" })` | |
| `userId` | `uuid().notNull().references(() => users.id, { onDelete: "cascade" })` | |
| `joinedAt` | `timestamp({ withTimezone: true }).defaultNow().notNull()` | |

Add indexes:
- `uniqueIndex("w2g_members_room_user_idx")` on `(table.roomId, table.userId)`
- `index("w2g_members_room_idx")` on `(table.roomId)`

Export types: `W2gRoom`, `NewW2gRoom`, `W2gRoomMember`

### 2. Migration — `drizzle/0020_w2g_rooms.sql`

Create a migration that runs `CREATE TABLE IF NOT EXISTS "w2g_rooms" (...)` and `CREATE TABLE IF NOT EXISTS "w2g_room_members" (...)` with all columns, constraints, and indexes above. Add this entry to `drizzle/meta/_journal.json` as idx 20.

### 3. Shared WebSocket Singleton — `src/lib/ws.ts`

Extract `createBunWebSocket()` from `routes/chat.ts` into a shared module because both chat and W2G need it, and calling `createBunWebSocket()` twice overwrites handlers.

```typescript
import { createBunWebSocket } from "hono/bun";
export const { upgradeWebSocket, websocket } = createBunWebSocket();
```

Then update `routes/chat.ts` to import `upgradeWebSocket` from `../lib/ws.ts` instead of calling `createBunWebSocket()` locally. Same for the new `routes/w2g.ts`.

Update `src/index.ts` to import `websocket` from `../lib/ws.ts` for the `Bun.serve()` config.

### 4. W2G Store — `src/services/w2gStore.ts`

Functions for all DB operations:

**`generateInviteCode(): Promise<string>`**
- Generate 6-char alphanumeric (chars: `ABCDEFGHJKLMNPQRSTUVWXYZ23456789` — no I,O,0,1)
- Check uniqueness in DB, retry on collision

**`createRoom(params: { hostUserId, animeId, episodeNumber, server, language?, quality?, password?, animeTitle?, animePoster? })`**
- Hash password with bcrypt (reuse pattern from `src/lib/password.ts`)
- Generate invite code
- Insert room row
- Insert host as first room member
- Return full room + member info

**`listActiveRooms(opts: { limit?, offset? })`**
- Query rooms where `status = "open"` and `lastActiveAt` within last 24h
- Join with `users` table to get host username
- Count members per room (subquery or separate query)
- Return rooms sorted by `lastActiveAt DESC`
- Each room includes: inviteCode, animeTitle, animePoster, animeId, episodeNumber, server, language, hasPassword (true if passwordHash not null), memberCount, hostUsername, hostUserId, lastActiveAt

**`getRoomByInviteCode(inviteCode: string)`**
- Return full room row or null

**`joinRoom(params: { inviteCode, userId, password? })`**
- Get room by invite code
- If room has password, verify with bcrypt
- If room is closed, reject
- Insert member row (ignore if already member — upsert pattern)
- Return room + member list + host info

**`leaveRoom(params: { inviteCode, userId })`**
- Delete member row
- If room has no more members, set status to "closed" and `lastActiveAt`
- If leaving member was host, assign new host (oldest remaining member)
- Return updated state

**`updateRoomPlayerState(params: { inviteCode, playerState })`**
- Update `playerState` and `lastActiveAt`
- Return updated room

**`updateRoomEpisode(params: { inviteCode, animeId?, episodeNumber?, server?, language?, quality?, animeTitle?, animePoster? })`**
- Update the room's current episode/anime info
- Reset playerState to default
- Update `lastActiveAt`

**`getRoomMembers(inviteCode: string)`**
- Join `w2g_room_members` with `users` table
- Return array of `{ userId, username, avatarUrl, joinedAt }`

**`cleanupStaleRooms()`**
- Delete rooms where `lastActiveAt < now() - 24 hours`
- Or set them to "closed"

### 5. W2G Hub — `src/services/w2gHub.ts`

In-memory connection manager (parallel to `chatHub.ts` but with richer state):

```typescript
type W2gConnection = {
  inviteCode: string;
  userId: string;
  username: string;
  avatarUrl: string | null;
  isHost: boolean;
  send: (data: string) => void;
};

// Map: inviteCode -> Map<userId, W2gConnection>
const rooms = new Map<string, Map<string, W2gConnection>>();

function registerConnection(conn: W2gConnection): void
function unregisterConnection(conn: W2gConnection): void
function getMemberCount(inviteCode: string): number
function getMembers(inviteCode: string): W2gConnection[]
function broadcastToRoom(inviteCode: string, message: object, excludeUserId?: string): void
function sendToMember(inviteCode: string, userId: string, message: object): void
function getRoomHost(inviteCode: string): W2gConnection | null
function transferHost(inviteCode: string): void // assign host to next member or close room
```

### 6. W2G Routes — `src/routes/w2g.ts`

Import `upgradeWebSocket` from `../lib/ws.ts`. Export `{ websocket }` from the shared `ws.ts`.

**REST routes:**

```
GET /v1/w2g/rooms
  - No auth required (or optional)
  - Query params: page (default 1), limit (default 20)
  - Returns: { rooms: [...], hasMore: boolean }

POST /v1/w2g/rooms
  - Auth: requireAnisurgeAuth
  - Body: { animeId, episodeNumber, server, language?, quality?, password?, animeTitle?, animePoster? }
  - Returns: { inviteCode, room: { ...full room... } }

POST /v1/w2g/rooms/:inviteCode/join
  - Auth: requireAnisurgeAuth
  - Body: { password? }
  - Returns: { room: { inviteCode, hostUserId, hostUsername, animeId, episodeNumber, server, language, quality, memberCount, members: [{userId, username, avatarUrl}], playerState } }

POST /v1/w2g/rooms/:inviteCode/leave
  - Auth: requireAnisurgeAuth
  - Returns: { ok: true }

PATCH /v1/w2g/rooms/:inviteCode
  - Auth: requireAnisurgeAuth (host only)
  - Body: { animeId?, episodeNumber?, server?, language?, quality?, animeTitle?, animePoster? }
  - Returns: { ok: true }
```

**WebSocket upgrade route:**

```
GET /v1/w2g/ws
  - Query params: room (inviteCode), token (Anisurge JWT)
  - Use upgradeWebSocket() handler
  - On open: authenticate token, verify user is room member, register connection, broadcast "member_joined" to room, send "room_info" to the joiner
  - On message: parse JSON envelope { type, data }, dispatch:
    - "play" / "pause" / "seek" → verify sender is host → update DB playerState → broadcast "player_state" to all
    - "change_episode" → verify sender is host → update DB room → broadcast "episode_change" to all
    - "chat" → sanitize body with sanitizeChatBody() → broadcast "chat" to all with sender info
    - "ping" → send "pong"
  - On close: unregister connection → if room empty, update DB → if host left, transfer host → broadcast "member_left"
```

**Important:** The W2G chat handler must NOT call any rate-limiting function. Just sanitize and broadcast.

### 7. App Registration — `src/app.ts`

Add import and route registration:
```typescript
import { w2gRoutes, websocket } from "./routes/w2g.ts";
app.route("/v1", w2gRoutes);
```

### 8. Server Entry — `src/index.ts`

Replace the current websocket import from `chat.ts` with the shared one from `lib/ws.ts`:
```typescript
import { websocket } from "./lib/ws.ts";
```

### 9. WebSocket Protocol Messages

All messages are JSON. The `type` field discriminates.

**Server → Client:**

```json
{ "type": "room_info", "data": { "inviteCode": "X7K2M", "hostUserId": "...", "hostUsername": "...", "animeId": "...", "episodeNumber": 1, "server": "suzu", "language": "sub", "quality": "1080p", "memberCount": 2, "members": [{"userId": "...", "username": "...", "avatarUrl": "..."}], "playerState": {"playing": false, "currentTime": 0, "timestamp": 0} } }
{ "type": "player_state", "data": { "playing": true, "currentTime": 42.5, "timestamp": 1717200000123 } }
{ "type": "episode_change", "data": { "animeId": "91", "episodeNumber": 5, "server": "gogoanime", "language": "sub", "quality": "1080p" } }
{ "type": "member_joined", "data": { "userId": "...", "username": "User", "avatarUrl": "..." } }
{ "type": "member_left", "data": { "userId": "...", "username": "User" } }
{ "type": "host_changed", "data": { "userId": "...", "username": "User" } }
{ "type": "chat", "data": { "id": "uuid", "userId": "...", "username": "User", "avatarUrl": "...", "body": "hello!", "createdAt": "2024-06-01T00:00:00Z" } }
{ "type": "error", "message": "Only the host can control playback" }
{ "type": "pong" }
```

**Client → Server:**

```json
{ "type": "play", "data": { "currentTime": 42.5 } }
{ "type": "pause", "data": { "currentTime": 42.5 } }
{ "type": "seek", "data": { "currentTime": 120.0 } }
{ "type": "change_episode", "data": { "animeId": "91", "episodeNumber": 5, "server": "gogoanime", "language": "sub", "quality": "1080p" } }
{ "type": "chat", "data": { "body": "yo!" } }
{ "type": "ping" }
```

---

## App Changes (`App` — KMP Compose)

### 1. Models — `composeApp/src/commonMain/kotlin/to/kuudere/anisuge/data/models/W2gModels.kt`

```kotlin
@Serializable
data class W2gRoomSummary(
    val inviteCode: String,
    val animeTitle: String? = null,
    val animePoster: String? = null,
    val animeId: String,
    val episodeNumber: Int,
    val server: String,
    val language: String? = null,
    @SerialName("has_password") val hasPassword: Boolean = false,
    @SerialName("member_count") val memberCount: Int = 0,
    @SerialName("host_username") val hostUsername: String? = null,
    @SerialName("host_user_id") val hostUserId: String? = null,
    @SerialName("last_active_at") val lastActiveAt: String? = null,
)

@Serializable
data class W2gRoomListResponse(
    val rooms: List<W2gRoomSummary> = emptyList(),
    @SerialName("has_more") val hasMore: Boolean = false,
)

@Serializable
data class W2gCreateRoomRequest(
    @SerialName("anime_id") val animeId: String,
    @SerialName("episode_number") val episodeNumber: Int,
    val server: String,
    val language: String? = null,
    val quality: String? = null,
    val password: String? = null,
    @SerialName("anime_title") val animeTitle: String? = null,
    @SerialName("anime_poster") val animePoster: String? = null,
)

@Serializable
data class W2gCreateRoomResponse(
    @SerialName("invite_code") val inviteCode: String,
    val room: W2gRoomDetail? = null,
)

@Serializable
data class W2gJoinRequest(
    val password: String? = null,
)

@Serializable
data class W2gJoinResponse(
    val room: W2gRoomDetail? = null,
)

@Serializable
data class W2gRoomDetail(
    @SerialName("invite_code") val inviteCode: String,
    @SerialName("host_user_id") val hostUserId: String,
    @SerialName("host_username") val hostUsername: String? = null,
    @SerialName("anime_id") val animeId: String,
    @SerialName("episode_number") val episodeNumber: Int,
    val server: String,
    val language: String? = null,
    val quality: String? = null,
    @SerialName("member_count") val memberCount: Int = 0,
    val members: List<W2gRoomMember> = emptyList(),
    @SerialName("player_state") val playerState: W2gPlayerState? = null,
)

@Serializable
data class W2gRoomMember(
    @SerialName("user_id") val userId: String,
    val username: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
)

@Serializable
data class W2gPlayerState(
    val playing: Boolean = false,
    @SerialName("current_time") val currentTime: Double = 0.0,
    val timestamp: Long = 0,
)

// WebSocket envelope
@Serializable
data class W2gWsEnvelope(
    val type: String,
    val data: JsonElement? = null,
    val message: String? = null,
)
```

### 2. Service — `composeApp/src/commonMain/kotlin/to/kuudere/anisuge/data/services/W2gRoomService.kt`

HTTP client matching `CommentService` pattern:
```kotlin
class W2gRoomService(
    private val sessionStore: SessionStore,
    private val httpClient: HttpClient,
) {
    private val baseUrl = AnisurgeApi.v1Base

    suspend fun listRooms(page: Int = 1, limit: Int = 20): W2gRoomListResponse?
    suspend fun createRoom(request: W2gCreateRoomRequest): W2gCreateRoomResponse?
    suspend fun joinRoom(inviteCode: String, password: String? = null): W2gJoinResponse?
    suspend fun leaveRoom(inviteCode: String): Boolean
}
```

Use `applyAnisurgeAuth(stored)` for auth on write routes. For `listRooms`, auth is optional.

### 3. WebSocket Client — `composeApp/src/commonMain/kotlin/to/kuudere/anisuge/data/services/W2gWsClient.kt`

Handle WebSocket connection to `/v1/w2g/ws?room=INVITE_CODE&token=JWT`:

```kotlin
class W2gWsClient(
    private val inviteCode: String,
    private val sessionStore: SessionStore,
    private val httpClient: HttpClient,
) {
    sealed class Event {
        data class RoomInfo(val room: W2gRoomDetail) : Event()
        data class PlayerState(val state: W2gPlayerState) : Event()
        data class EpisodeChange(val animeId: String, val episodeNumber: Int, val server: String, val language: String?, val quality: String?) : Event()
        data class MemberJoined(val userId: String, val username: String?, val avatarUrl: String?) : Event()
        data class MemberLeft(val userId: String, val username: String?) : Event()
        data class HostChanged(val userId: String, val username: String?) : Event()
        data class ChatMessage(val id: String, val userId: String, val username: String?, val avatarUrl: String?, val body: String, val createdAt: String) : Event()
        data class Error(val message: String) : Event()
    }

    val events: SharedFlow<Event> // cold flow, connect on collection
    suspend fun connect()
    fun disconnect()
    fun sendPlay(currentTime: Double)
    fun sendPause(currentTime: Double)
    fun sendSeek(currentTime: Double)
    fun sendChangeEpisode(animeId: String, episodeNumber: Int, server: String, language: String?, quality: String?)
    fun sendChat(body: String)
    fun sendPing()
}
```

### 4. Navigation — `composeApp/src/commonMain/kotlin/to/kuudere/anisuge/navigation/Screen.kt`

Add:
```kotlin
data object W2gRoomList : Screen("w2g")
data class W2gRoom(val inviteCode: String) : Screen("w2g-room/{inviteCode}") {
    companion object {
        const val ROUTE = "w2g-room/{inviteCode}"
        fun route(inviteCode: String) = "w2g-room/$inviteCode"
    }
}
```

### 5. W2G ViewModel — `composeApp/src/commonMain/kotlin/to/kuudere/anisuge/screens/w2g/W2gViewModel.kt`

```kotlin
class W2gViewModel : ViewModel() {
    // Room listing state
    val rooms: StateFlow<List<W2gRoomSummary>>
    val isLoadingRooms: StateFlow<Boolean>
    fun loadRooms()

    // Create room
    fun createRoom(request: W2gCreateRoomRequest): String? // returns inviteCode or null

    // Join room
    fun joinRoom(inviteCode: String, password: String?): Result<W2gRoomDetail>

    // Active room state (after join)
    val roomDetail: StateFlow<W2gRoomDetail?>
    val playerState: StateFlow<W2gPlayerState>
    val members: StateFlow<List<W2gRoomMember>>
    val chatMessages: StateFlow<List<W2gChatMessage>>
    val isHost: StateFlow<Boolean>
    val amIHost: Boolean

    // Player controls (host only)
    fun play(currentTime: Double)
    fun pause(currentTime: Double)
    fun seek(currentTime: Double)
    fun changeEpisode(animeId: String, episodeNumber: Int, server: String, language: String?, quality: String?)

    // Chat
    fun sendMessage(body: String)

    // Lifecycle
    fun connect()
    fun disconnect()
}
```

### 6. Room List Screen — `composeApp/src/commonMain/kotlin/to/kuudere/anisuge/screens/w2g/W2gRoomListScreen.kt`

Layout:
```
Top bar: "Watch2gether" + Create Room button

LazyColumn of room cards, each showing:
  ┌──────────────────────────────────────┐
  │  [poster]  Anime Title — Ep 5        │
  │           Host: Username              │
  │           👥 3 members  🔒/🔓       │
  └──────────────────────────────────────┘
  Tap → if hasPassword, show password dialog → join → navigate to W2G room screen

FAB: "Create Room" → opens W2gCreateRoomSheet
```

### 7. Create Room Sheet — `composeApp/src/commonMain/kotlin/to/kuudere/anisuge/screens/w2g/W2gCreateRoomSheet.kt`

Bottom sheet / dialog with:
- Anime selector (search + select from existing episode data)
- Episode number input
- Server selector (suzu, gogoanime, etc. — reuse existing server list)
- Language toggle (sub/dub)
- Password field (optional, blank = no password)
- Create button → calls API → navigates to W2G room screen with returned inviteCode

### 8. W2G Player Screen — `composeApp/src/commonMain/kotlin/to/kuudere/anisuge/screens/w2g/W2gPlayerScreen.kt`

Split layout:

```
┌──────────────────────────────────────┐
│  Top bar: Invite code + 👥 count     │
├──────────────────────────────────────┤
│                                      │
│  WatchVideoPlayer (reuse existing)   │
│  - Host controls shown as overlay    │
│  - Non-host: player is read-only     │
│  - Sync indicator (green/yellow)     │
│                                      │
│  If host:                            │
│  ┌──────────────────────────────┐   │
│  │ [Change Anime] [Episode +-] │   │
│  │ [Server dropdown]           │   │
│  └──────────────────────────────┘   │
│                                      │
├──────────────────────────────────────┤
│  💬 Room Chat                        │
├──────────────────────────────────────┤
│  Chat messages (scrollable)          │
│  ...                                 │
│  [Message...              [Send]    │
└──────────────────────────────────────┘
```

**Key behaviors:**

- **Host:** The player works normally (play/pause/seek). Every action is sent via WS to all members. Host sees "Host" badge and a gear icon for admin controls (change anime/episode/server).

- **Non-host (member):** The player obeys remote state. Local play/pause buttons are disabled or overridden — when the host plays, all members play. Members see "Following" indicator. Members can still pause for themselves (local-only, doesn't broadcast).

- **Player sync logic:**
  - On receiving `player_state` with `playing: true`: calculate `elapsed = currentTime + (now - timestamp) / 1000`, seek to that position and play.
  - On receiving `player_state` with `playing: false`: seek to `currentTime` and pause.
  - On receiving `episode_change`: load new episode video in the player.
  - Drift correction: every 30s, if member is playing and host sent state, adjust position if drift > 2s.

- **Invite code sharing:** Show the invite code prominently at the top. Tap to copy. Share via Android share sheet.

### 9. W2G Room List Nav Entry — `composeApp/src/commonMain/kotlin/to/kuudere/anisuge/navigation/NavGraph.kt`

Add routes:
```kotlin
composable(Screen.W2gRoomList.ROUTE) { W2gRoomListScreen(onRoomClick = { code -> navController.navigate(Screen.W2gRoom.route(code)) }) }
composable(Screen.W2gRoom.ROUTE, arguments = listOf(navArgument("inviteCode") { type = NavType.StringType })) { backStackEntry ->
    val inviteCode = backStackEntry.arguments?.getString("inviteCode") ?: return@composable
    W2gPlayerScreen(inviteCode = inviteCode, onBack = { navController.popBackStack() })
}
```

### 10. Nav Drawer / Bottom Nav Entry

Add a "Watch Together" entry in the sidebar/bottom nav (alongside Home, Bookmarks, Settings) that navigates to `Screen.W2gRoomList`.

---

## Files to Create / Modify — Summary

### BFF (`Anisurge-api-server`)

**Create:**
- `src/lib/ws.ts` — Shared createBunWebSocket singleton
- `src/services/w2gStore.ts` — DB operations
- `src/services/w2gHub.ts` — In-memory connection hub
- `src/routes/w2g.ts` — REST + WS routes

**Modify:**
- `src/db/schema.ts` — Add w2g_rooms + w2g_room_members tables + export types
- `drizzle/meta/_journal.json` — Add entry for migration 0020
- `drizzle/0020_w2g_rooms.sql` — New migration file
- `src/routes/chat.ts` — Import upgradeWebSocket from lib/ws.ts instead of local createBunWebSocket()
- `src/index.ts` — Import websocket from lib/ws.ts
- `src/app.ts` — Import and register w2gRoutes

### App (`App`)

**Create:**
- `composeApp/src/commonMain/kotlin/to/kuudere/anisuge/data/models/W2gModels.kt`
- `composeApp/src/commonMain/kotlin/to/kuudere/anisuge/data/services/W2gRoomService.kt`
- `composeApp/src/commonMain/kotlin/to/kuudere/anisuge/data/services/W2gWsClient.kt`
- `composeApp/src/commonMain/kotlin/to/kuudere/anisuge/screens/w2g/W2gViewModel.kt`
- `composeApp/src/commonMain/kotlin/to/kuudere/anisuge/screens/w2g/W2gRoomListScreen.kt`
- `composeApp/src/commonMain/kotlin/to/kuudere/anisuge/screens/w2g/W2gCreateRoomSheet.kt`
- `composeApp/src/commonMain/kotlin/to/kuudere/anisuge/screens/w2g/W2gPlayerScreen.kt`

**Modify:**
- `composeApp/src/commonMain/kotlin/to/kuudere/anisuge/navigation/Screen.kt` — Add W2gRoomList + W2gRoom routes
- Navigation/NavGraph — Add composable entries
- App component / sidebar — Add nav entry for Watch2gether

# Watch2gether (W2G) — Implementation Prompt

## Overview

Watch2gether lets users create or join real-time synchronized anime watching rooms. Each room has a synced player (controlled by the host), an isolated in-room chat with no rate limits, and a public listing for discovery.

---

## User Flow (Important)

1. User enters **room name** + optional **password** → room created → gets invite code
2. User enters the room UI: empty player area + chat below
3. Host taps a **Search** button → search popup opens
4. Host searches anime, picks episode, picks server → video starts playing
5. **Everyone in the room sees the same video synced**
6. Below the player: in-room chat
7. Host can **change anime/episode/server anytime** from within the room via the same search popup

**Room is NOT tied to a specific anime at creation.** Anime is chosen inside the room by the host after creation.

---

## BFF Changes (`Anisurge-api-server`)

### 1. Database Schema — `src/db/schema.ts`

**`w2g_rooms` table:**

| Column | Type | Notes |
|---|---|---|
| `id` | `uuid().primaryKey().defaultRandom()` | |
| `hostUserId` | `uuid().notNull().references(() => users.id, { onDelete: "cascade" })` | |
| `inviteCode` | `text().notNull().unique()` | Short 6-char code like "X7K2M" |
| `roomName` | `text().notNull()` | User-chosen room name (e.g. "Naruto marathon") |
| `passwordHash` | `text()` | bcrypt hash; null = no password |
| `status` | `text().default("open").notNull()` | "open" or "closed" |
| `createdAt` | `timestamp({ withTimezone: true }).defaultNow().notNull()` | |
| `lastActiveAt` | `timestamp({ withTimezone: true }).defaultNow().notNull()` | |
| `animeId` | `text()` | Current anime (null until host picks one) |
| `episodeNumber` | `integer().default(1)` | Current episode |
| `server` | `text().default("suzu")` | Current server |
| `language` | `text()` | "sub" or "dub" |
| `quality` | `text()` | Quality string |
| `animeTitle` | `text()` | For room listing display |
| `animePoster` | `text()` | Poster URL for room listing |
| `playerState` | `jsonb().$type<{ playing: boolean; currentTime: number; timestamp: number }>().default({ playing: false, currentTime: 0, timestamp: 0 }).notNull()` | |

Add indexes:
- `index("w2g_rooms_status_active_idx")` on `(table.status, table.lastActiveAt.desc())`

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

Create the two tables.

### 3. Shared WebSocket Singleton — `src/lib/ws.ts`

```typescript
import { createBunWebSocket } from "hono/bun";
export const { upgradeWebSocket, websocket } = createBunWebSocket();
```

Update `routes/chat.ts` and the new `routes/w2g.ts` to import from this shared singleton instead of creating their own.

### 4. W2G Store — `src/services/w2gStore.ts`

**`generateInviteCode(): Promise<string>`**
- 6 chars from `ABCDEFGHJKLMNPQRSTUVWXYZ23456789`

**`createRoom(params: { hostUserId, roomName, password? })`**
- Hash password with bcrypt if provided (reuse `src/lib/password.ts`)
- Generate invite code
- Insert room row + host as first member
- Return room (animeId etc. are null until host picks)

**`listActiveRooms(opts: { limit?, offset? })`**
- Rooms where status = "open", lastActiveAt within 24h
- Join users for host username, count members
- Include: inviteCode, roomName, hasPassword, memberCount, hostUsername, animeTitle (nullable), animePoster (nullable), lastActiveAt

**`getRoomByInviteCode(inviteCode)`**
- Return full room row or null

**`joinRoom(params: { inviteCode, userId, password? })`**
- Verify bcrypt if password set
- Insert member (ignore if already joined)
- Return room + member list + host info

**`leaveRoom(params: { inviteCode, userId })`**
- Delete member; if no members left → close room
- If leaver was host → transfer to oldest member

**`updateRoomEpisode(params: { inviteCode, animeId?, episodeNumber?, server?, language?, quality?, animeTitle?, animePoster? })`**
- Update current anime/episode info
- Reset playerState to default
- Update lastActiveAt

**`updateRoomPlayerState(params: { inviteCode, playerState })`**
- Update playerState and lastActiveAt

**`getRoomMembers(inviteCode)`**
- Join w2g_room_members with users → return array of { userId, username, avatarUrl, joinedAt }

**`cleanupStaleRooms()`**
- Delete/close rooms older than 24h

### 5. W2G Hub — `src/services/w2gHub.ts`

```typescript
type W2gConnection = {
  inviteCode: string;
  userId: string;
  username: string;
  avatarUrl: string | null;
  isHost: boolean;
  send: (data: string) => void;
};

const rooms = new Map<string, Map<string, W2gConnection>>(); // inviteCode → userId → conn

function registerConnection(conn)
function unregisterConnection(conn)
function getMemberCount(inviteCode): number
function getMembers(inviteCode): W2gConnection[]
function broadcastToRoom(inviteCode, message, excludeUserId?)
function sendToMember(inviteCode, userId, message)
function getRoomHost(inviteCode): W2gConnection | null
function transferHost(inviteCode): void
```

### 6. W2G Routes — `src/routes/w2g.ts`

**REST endpoints:**

| Method | Path | Auth | Body | Returns |
|---|---|---|---|---|
| `GET` | `/v1/w2g/rooms` | Optional | Query: page, limit | `{ rooms: [{inviteCode, roomName, hasPassword, memberCount, hostUsername, animeTitle, animePoster, lastActiveAt}], hasMore }` |
| `POST` | `/v1/w2g/rooms` | Required | `{ roomName, password? }` | `{ inviteCode, room: { … } }` |
| `POST` | `/v1/w2g/rooms/:inviteCode/join` | Required | `{ password? }` | `{ room: { inviteCode, hostUserId, hostUsername, roomName, animeId?, episodeNumber?, server?, members, playerState } }` |
| `POST` | `/v1/w2g/rooms/:inviteCode/leave` | Required | — | `{ ok: true }` |
| `PATCH` | `/v1/w2g/rooms/:inviteCode` | Required (host) | `{ animeId?, episodeNumber?, server?, language?, quality?, animeTitle?, animePoster? }` | `{ ok: true }` |

**WebSocket route:** `GET /v1/w2g/ws?room=INVITE_CODE&token=JWT`

Events:

| Server → Client | Data |
|---|---|
| `room_info` | Full room state with members, playerState (anime fields may be null if host hasn't picked yet) |
| `player_state` | `{ playing, currentTime, timestamp }` |
| `episode_change` | `{ animeId, episodeNumber, server, language, quality }` (sent when host picks/changes anime) |
| `member_joined` | `{ userId, username, avatarUrl }` |
| `member_left` | `{ userId, username }` |
| `host_changed` | `{ userId, username }` |
| `chat` | `{ id, userId, username, avatarUrl, body, createdAt }` |
| `error` | `{ message }` |
| `pong` | — |

| Client → Server | Who | Data |
|---|---|---|
| `play` | Host | `{ currentTime }` |
| `pause` | Host | `{ currentTime }` |
| `seek` | Host | `{ currentTime }` |
| `change_episode` | Host | `{ animeId, episodeNumber, server, language?, quality? }` |
| `chat` | Anyone | `{ body }` |
| `ping` | Anyone | — |

**Important:** W2G chat has NO rate limits. Just sanitize with `sanitizeChatBody()` and broadcast.

### 7. Registration — `src/app.ts`

```typescript
import { w2gRoutes } from "./routes/w2g.ts";
app.route("/v1", w2gRoutes);
```

### 8. Server Entry — `src/index.ts`

Import `websocket` from `./lib/ws.ts` for `Bun.serve({ websocket })`.

---

## App Changes (`App` — KMP Compose)

### 1. Models — `composeApp/src/commonMain/kotlin/to/kuudere/anisuge/data/models/W2gModels.kt`

```kotlin
@Serializable
data class W2gRoomSummary(
    @SerialName("invite_code") val inviteCode: String,
    @SerialName("room_name") val roomName: String,
    @SerialName("has_password") val hasPassword: Boolean = false,
    @SerialName("member_count") val memberCount: Int = 0,
    @SerialName("host_username") val hostUsername: String? = null,
    @SerialName("anime_title") val animeTitle: String? = null,
    @SerialName("anime_poster") val animePoster: String? = null,
    @SerialName("last_active_at") val lastActiveAt: String? = null,
)

@Serializable
data class W2gRoomCreateRequest(
    @SerialName("room_name") val roomName: String,
    val password: String? = null,
)

@Serializable
data class W2gRoomCreateResponse(
    @SerialName("invite_code") val inviteCode: String,
    val room: W2gRoomDetail? = null,
)

@Serializable
data class W2gJoinRequest(val password: String? = null)

@Serializable
data class W2gJoinResponse(val room: W2gRoomDetail? = null)

@Serializable
data class W2gRoomDetail(
    @SerialName("invite_code") val inviteCode: String,
    @SerialName("room_name") val roomName: String,
    @SerialName("host_user_id") val hostUserId: String,
    @SerialName("host_username") val hostUsername: String? = null,
    @SerialName("anime_id") val animeId: String? = null,
    @SerialName("episode_number") val episodeNumber: Int? = null,
    val server: String? = null,
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

@Serializable
data class W2gWsEnvelope(
    val type: String,
    val data: JsonElement? = null,
    val message: String? = null,
)
```

### 2. Service — `W2gRoomService.kt`

HTTP client with methods:
- `listRooms(page, limit): W2gRoomListResponse?`
- `createRoom(request: W2gRoomCreateRequest): W2gRoomCreateResponse?`
- `joinRoom(inviteCode, password?): W2gJoinResponse?`
- `leaveRoom(inviteCode): Boolean`

### 3. WebSocket Client — `W2gWsClient.kt`

WS connection to `/v1/w2g/ws?room=INVITE_CODE&token=JWT`.

Sealed class `Event` with: `RoomInfo`, `PlayerState`, `EpisodeChange`, `MemberJoined`, `MemberLeft`, `HostChanged`, `ChatMessage`, `Error`.

Methods: `connect()`, `disconnect()`, `sendPlay(currentTime)`, `sendPause(currentTime)`, `sendSeek(currentTime)`, `sendChangeEpisode(animeId, ep, server, lang, quality)`, `sendChat(body)`, `sendPing()`.

### 4. ViewModel — `W2gViewModel.kt`

State:
- `rooms: StateFlow<List<W2gRoomSummary>>` — room listing
- `roomDetail: StateFlow<W2gRoomDetail?>` — current room after join
- `playerState: StateFlow<W2gPlayerState>` — current playback state
- `members: StateFlow<List<W2gRoomMember>>` — room members
- `chatMessages: StateFlow<List<W2gChatMessage>>` — room chat
- `isHost: StateFlow<Boolean>` — whether current user is host

Actions:
- `loadRooms()`, `createRoom(name, password)`, `joinRoom(inviteCode, password)`, `leaveRoom()`
- `play(t)`, `pause(t)`, `seek(t)` — host only
- `changeEpisode(animeId, ep, server, lang, quality)` — host only
- `sendMessage(body)`
- `connectToWs()`, `disconnectWs()`

### 5. Room List Screen — `W2gRoomListScreen.kt`

```
Top bar: "Watch2gether" + Create Room button

LazyColumn of active rooms:
┌──────────────────────────────────────┐
│  Room: "Naruto marathon"             │
│  👤 Host: User123   👥 3  🔒/🔓    │
│  📺 AOT S4 Ep5 (or "Nothing yet")   │
└──────────────────────────────────────┘
Tap → password dialog if needed → join → navigate to room

FAB "Create Room" → opens create sheet
```

### 6. Create Room Sheet — `W2gCreateRoomSheet.kt`

Simple bottom sheet:
- Room name field
- Password field (optional, blank = no password)
- "Create Room" button → calls API → navigates to room with inviteCode

### 7. W2G Player Screen — `W2gPlayerScreen.kt`

```
┌──────────────────────────────────────┐
│  ← Back    Room: Naruto marathon     │
│  Code: X7K2M  (tap to copy)  👥 3   │
├──────────────────────────────────────┤
│                                      │
│  If no anime selected yet (host):    │
│  ┌──────────────────────────────┐   │
│  │   🔍 Search anime to watch   │   │
│  │   (big prominent button)     │   │
│  └──────────────────────────────┘   │
│                                      │
│  After host picks:                   │
│  ┌──────────────────────────────┐   │
│  │       PLAYER (synced)        │   │
│  │  Host controls overlay:      │   │
│  │  [🔍 Change] [Episode +-]   │   │
│  │  [Server ▼]                 │   │
│  └──────────────────────────────┘   │
│                                      │
├──────────────────────────────────────┤
│  💬 Room Chat                        │
├──────────────────────────────────────┤
│  messages...                         │
│  [Type a message...]      [Send]    │
└──────────────────────────────────────┘
```

**Key behaviors:**

- **Host sees "Search anime" button** when no anime is selected. Tapping opens a search dialog where they can search anime, pick an episode, pick a server. On selection → sends `change_episode` via WS → everyone starts watching.

- **Host can change anytime** via a search icon overlay on the player.

- **Members see "Waiting for host"** until host picks something. Then player syncs automatically.

- **Host controls**: play/pause/seek broadcast to all members via WS.

- **Chat**: always available below the player regardless of whether anime is playing.

- **Invite code**: shown at top, tap to copy.

### 8. Search Popup — Reuse existing search UI

The host's "Search anime" opens the same search screen used elsewhere in the app (the one that searches Project-R catalog). After picking an anime, a second dialog lets them pick episode number and server. On confirm → broadcasts to room.

### 9. Navigation — `Screen.kt`

```kotlin
data object W2gRoomList : Screen("w2g")
data class W2gRoom(val inviteCode: String) : Screen("w2g-room/{inviteCode}") {
    companion object {
        const val ROUTE = "w2g-room/{inviteCode}"
        fun route(inviteCode: String) = "w2g-room/$inviteCode"
    }
}
```

### 10. Nav Entry

- **Desktop sidebar:** SidebarIcon with Group icon → `onW2gClick` → navigates to `W2gRoomList`
- **Mobile top bar:** Group icon button between chat and download icons → navigates to `W2gRoomList`

---

## Files to Create / Modify

### BFF
**Create:** `src/lib/ws.ts`, `src/services/w2gStore.ts`, `src/services/w2gHub.ts`, `src/routes/w2g.ts`, `drizzle/0020_w2g_rooms.sql`
**Modify:** `src/db/schema.ts`, `src/routes/chat.ts`, `src/index.ts`, `src/app.ts`, `drizzle/meta/_journal.json`

### App
**Create:** `data/models/W2gModels.kt`, `data/services/W2gRoomService.kt`, `data/services/W2gWsClient.kt`, `screens/w2g/W2gViewModel.kt`, `screens/w2g/W2gRoomListScreen.kt`, `screens/w2g/W2gCreateRoomSheet.kt`, `screens/w2g/W2gPlayerScreen.kt`
**Modify:** `navigation/Screen.kt`, `App.kt`, `AppComponent.kt`, `HomeScreen.kt`

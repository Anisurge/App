# W2G Host Detection Fix — Detailed Prompt

## The Bug

When a user creates a room or joins a room, they always see the **non-host UI** ("Waiting for host to pick an anime...") instead of the **host UI** ("Search Anime to Watch"). This happens because:

1. The `joinRoom()` in `W2gViewModel` compares `room.hostUserId == currentUserId` but `currentUserId` might not be set yet at that point
2. More importantly: when navigating from the room list to the player screen, `joinRoom()` is **never called at all** — the `W2gPlayerScreen` only calls `setCurrentUserId()` + `connect()` (WebSocket), skipping the REST join entirely
3. The WebSocket's `room_info` event also checks `hostUserId == currentUserId`, but if the user never joined via REST, the server might not recognize them as a member

## Root Cause

The `W2gPlayerScreen`'s `LaunchedEffect(inviteCode)` at lines 62-65 of `W2gPlayerScreen.kt`:
```kotlin
LaunchedEffect(inviteCode) {
    viewModel.setCurrentUserId(userId ?: "")
    viewModel.connect(inviteCode)
}
```

This only connects via WebSocket but **doesn't call `joinRoom` first**. The WebSocket server verifies the user is a member of the room before accepting the connection, and if the REST `joinRoom` was never called, the server doesn't know about this user.

## Fix Required

### Fix 1: In `W2gPlayerScreen.kt`

Change the `LaunchedEffect(inviteCode)` block to call `joinRoom` BEFORE `connect`:

```kotlin
LaunchedEffect(inviteCode) {
    viewModel.setCurrentUserId(userId ?: "")
    // First join the room via REST, then connect via WebSocket
    viewModel.joinRoom(inviteCode)
    viewModel.connect(inviteCode)
}
```

### Fix 2: In `W2gViewModel.kt`

The `joinRoom` function currently takes an optional `password` parameter and expects `currentUserId` to already be set. Update it:

```kotlin
suspend fun joinRoom(inviteCode: String, password: String? = null): Result<W2gRoomDetail> {
    _state.value = _state.value.copy(error = null)
    val response = roomService.joinRoom(inviteCode, password)
    val room = response?.room
    if (room != null) {
        _state.value = _state.value.copy(
            roomDetail = room,
            members = room.members,
            playerState = room.playerState ?: W2gPlayerState(),
            isHost = room.hostUserId == currentUserId,
        )
        return Result.success(room)
    }
    return Result.failure(Exception("Failed to join room"))
}
```

Make sure `currentUserId` is set BEFORE `joinRoom` is called. The `setCurrentUserId` is called at line 63 of W2gPlayerScreen before `connect`, so it should be set before `joinRoom` too.

### Fix 3: In `W2gViewModel.kt`

Also check that the WebSocket `room_info` handler correctly identifies the host. The comparison `event.room.hostUserId == currentUserId` should work IF `currentUserId` is the same ID used when creating the room. The `hostUserId` in the room comes from the BFF's `users.id` (UUID), and the `currentUserId` comes from `SessionInfo` (also `users.id` UUID). They should match.

If they don't match, the issue is that the app sends a different user identifier than what the BFF stores as `hostUserId`. Check the `W2gRoomService.joinRoom` and `W2gRoomService.createRoom` to ensure they use the same auth mechanism (the Anisurge JWT) which maps to the same `userId` on the BFF.

## Files to Modify

1. `composeApp/src/commonMain/kotlin/to/kuudere/anisuge/screens/w2g/W2gPlayerScreen.kt` — Add `joinRoom` call before `connect`
2. `composeApp/src/commonMain/kotlin/to/kuudere/anisuge/screens/w2g/W2gViewModel.kt` — Ensure `joinRoom` works correctly with `currentUserId`
3. `composeApp/src/commonMain/kotlin/to/kuudere/anisuge/data/services/W2gRoomService.kt` — Check that the REST API calls use the Anisurge JWT correctly

## Verification

After the fix:
- Room creator should see "🔍 Search Anime to Watch" button (host UI)
- Room joiner should see "Waiting for host to pick an anime..." (member UI)
- The top bar should show a green "Live" indicator for everyone

## BFF Note

The BFF's WebSocket upgrade route at `routes/w2g.ts` checks:
1. `authenticateChatToken` to verify the Anisurge JWT → extracts `userId` from JWT
2. Then verifies the user is a room member via DB lookup

If `joinRoom` (REST) was never called, the user won't be in `w2g_room_members` table, and the WS connection will be rejected with an error. This is likely why the user sees the non-host UI — the WS connection might be silently failing.

To debug this, check the BFF server logs for `[w2g]` messages when the user tries to join via WS.

package to.kuudere.anisuge.screens.w2g


import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import kotlin.math.abs
import to.kuudere.anisuge.data.models.AnimeItem
import to.kuudere.anisuge.data.models.ServerInfo
import to.kuudere.anisuge.player.StreamProxy
import to.kuudere.anisuge.player.VideoPlayerSurface
import to.kuudere.anisuge.player.rememberVideoPlayerState
import to.kuudere.anisuge.theme.AppColors
import to.kuudere.anisuge.ui.ProfileAvatar
import to.kuudere.anisuge.platform.LockScreenOrientation
import to.kuudere.anisuge.platform.SyncFullscreen
import to.kuudere.anisuge.platform.isAndroidPlatform
import to.kuudere.anisuge.utils.currentTimeMillis
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun W2gPlayerScreen(
    inviteCode: String,
    viewModel: W2gViewModel,
    userId: String?,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var chatInput by remember { mutableStateOf("") }
    val chatListState = rememberLazyListState()
    var isFullscreen by remember { mutableStateOf(false) }
    var hostShouldPlay by remember { mutableStateOf(true) }
    var lastRemoteSeekAtMs by remember { mutableStateOf(0L) }

    LaunchedEffect(inviteCode, userId) {
        viewModel.enterRoom(inviteCode, userId)
    }

    LaunchedEffect(state.chatMessages.size) {
        if (state.chatMessages.isNotEmpty()) {
            chatListState.animateScrollToItem(state.chatMessages.size - 1)
        }
    }

    LaunchedEffect(state.playerState.timestamp, state.playerState.playing) {
        if (state.isHost && state.playerState.timestamp > 0) {
            hostShouldPlay = state.playerState.playing
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.disconnect()
        }
    }

    LockScreenOrientation(to.kuudere.anisuge.platform.isAndroidPlatform && isFullscreen)
    SyncFullscreen(isFullscreen)

    Scaffold(
        topBar = {
            if (!isFullscreen) {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                state.roomDetail?.roomName ?: "Room",
                                color = Color.White,
                                fontSize = 16.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.width(4.dp))
                            IconButton(
                                onClick = {
                                    clipboard.setText(AnnotatedString(inviteCode))
                                },
                                modifier = Modifier.size(24.dp),
                            ) {
                                Icon(
                                    Icons.Outlined.ContentCopy,
                                    "Copy invite code",
                                    tint = Color.Gray,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            scope.launch {
                                viewModel.leaveRoom(inviteCode)
                                onBack()
                            }
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                        }
                    },
                    actions = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (state.isConnected) {
                                Box(
                                    Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF4CAF50))
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Live", color = Color(0xFF4CAF50), fontSize = 12.sp)
                                Spacer(Modifier.width(12.dp))
                            }
                            Icon(Icons.Outlined.Group, null, tint = Color.Gray, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("${state.members.size}", color = Color.Gray, fontSize = 14.sp)
                            Spacer(Modifier.width(12.dp))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = AppColors.background,
                    ),
                )
            }
        },
        containerColor = AppColors.background,
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(Modifier.fillMaxSize()) {
                // Player area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (isFullscreen) Modifier.weight(1f)
                            else Modifier.height(250.dp)
                        )
                        .background(Color.Black),
                    contentAlignment = Alignment.Center,
                ) {
                    val animeId = state.roomDetail?.animeId
                    val playback = state.playbackSource
                    if (playback != null) {
                        val room = state.roomDetail
                        val playbackIdentity = listOf(
                            room?.animeId.orEmpty(),
                            room?.anilistId?.toString().orEmpty(),
                            room?.episodeNumber?.toString().orEmpty(),
                            room?.server.orEmpty(),
                            room?.language.orEmpty(),
                            playback.url,
                        ).joinToString("|")
                        key(playbackIdentity) {
                            val playbackUrl = remember(playback.url, playback.headers) {
                                StreamProxy.proxyUrl(playback.url, playback.headers)
                            }
                            val videoState = rememberVideoPlayerState(
                                url = playbackUrl,
                                showControls = false,
                                autoPlay = true,
                                headers = playback.headers,
                            )
                            DisposableEffect(playbackUrl) {
                                onDispose { StreamProxy.release(playback.url) }
                            }
                            VideoPlayerSurface(
                                state = videoState,
                                modifier = Modifier.fillMaxSize(),
                            )
                            videoState.error?.let { err ->
                                Text(
                                    err,
                                    color = Color(0xFFFFB74D),
                                    fontSize = 12.sp,
                                    modifier = Modifier
                                        .align(Alignment.TopCenter)
                                        .padding(8.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.Black.copy(alpha = 0.75f))
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                )
                            }

                            // W2GPlayerControls overlay
                            W2gPlayerControls(
                                playerState = videoState,
                                streamingData = null,
                                title = state.roomDetail?.animeTitle ?: "",
                                isFullscreen = isFullscreen,
                                showMediaControls = state.isHost,
                                showFullscreenButton = true,
                                showLibraryActions = false,
                                onFullscreenToggle = { isFullscreen = !isFullscreen },
                                onBack = {},
                                onChatClick = {
                                    viewModel.setChatSheetOpen(true)
                                },
                                onSettingsClick = {},
                                onPlayPause = if (state.isHost) { shouldPlay ->
                                    hostShouldPlay = shouldPlay
                                    if (shouldPlay) viewModel.play(videoState.position)
                                    else viewModel.pause(videoState.position)
                                } else null,
                                onSeek = if (state.isHost) { pos ->
                                    viewModel.seek(pos, playing = hostShouldPlay)
                                } else null,
                                modifier = Modifier.fillMaxSize(),
                            )

                            // Host: periodic position broadcast so non-hosts can correct drift
                            if (state.isHost) {
                                LaunchedEffect(playbackIdentity) {
                                    hostShouldPlay = true
                                    while (true) {
                                        delay(3_000)
                                        val syncPosition = if (hostShouldPlay) {
                                            val ledPosition = videoState.position + 2.0
                                            if (videoState.duration > 0) ledPosition.coerceAtMost(videoState.duration) else ledPosition
                                        } else {
                                            videoState.position
                                        }
                                        viewModel.syncPosition(syncPosition, playing = hostShouldPlay)
                                    }
                                }
                            }

                            // Non-host remote sync
                            if (!state.isHost) {
                                LaunchedEffect(state.playerState, playbackIdentity) {
                                    val ps = state.playerState
                                    if (ps.timestamp > 0) {
                                        val target = estimateW2gHostPosition(ps, videoState.duration)
                                        if (target > 0) {
                                            val diff = abs(videoState.position - target)
                                            val threshold = if (ps.playing) 5.0 else 0.75
                                            val now = currentTimeMillis()
                                            val cooldownElapsed = now - lastRemoteSeekAtMs >= 2_000
                                            val isLargeJump = diff > 12.0
                                            if (diff > threshold && (!ps.playing || cooldownElapsed || isLargeJump)) {
                                                videoState.seekTarget = target
                                                lastRemoteSeekAtMs = now
                                            }
                                        }
                                        if (ps.playing && videoState.isPaused) {
                                            videoState.pauseRequested = false
                                        } else if (!ps.playing && videoState.isPlaying) {
                                            videoState.pauseRequested = true
                                        }
                                    } else if (videoState.isPaused) {
                                        videoState.pauseRequested = false
                                    }
                                }
                            }
                        }
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        ) {
                            if (animeId != null) {
                                Text(
                                    "${state.roomDetail?.animeTitle ?: animeId} \u2014 Ep ${state.roomDetail?.episodeNumber ?: 1}",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    if (state.isLoadingPlayback) "Loading stream..."
                                    else "${state.roomDetail?.server ?: "suzu"} \u00b7 ${state.roomDetail?.language ?: "sub"} \u00b7 auto",
                                    color = Color.Gray,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            } else if (state.isHost) {
                                Button(
                                    onClick = { viewModel.openHostPicker() },
                                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.accent),
                                ) {
                                    Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Search Anime to Watch")
                                }
                            } else {
                                Text(
                                    state.error ?: "Waiting for host to pick an anime...",
                                    color = if (state.error != null) Color.Red else Color.Gray,
                                    fontSize = 16.sp,
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            if (state.isConnected) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF4CAF50))
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text("Connected", color = Color(0xFF4CAF50), fontSize = 12.sp)
                                }
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFFFA500))
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(state.loadingMessage ?: "Connecting...", color = Color(0xFFFFA500), fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                // Host controls & members & chat (only when not fullscreen)
                if (!isFullscreen) {
                    val detail = state.roomDetail
                    if (state.isHost && detail?.animeId != null) {
                        RoomControls(
                            animeId = detail.animeId ?: "",
                            episodeNumber = detail.episodeNumber ?: 1,
                            server = detail.server ?: "suzu",
                            language = detail.language,
                            quality = detail.quality,
                            onChangeAnime = { viewModel.openHostPicker() },
                            onChangeEpisode = { animeId, ep, server, lang, quality ->
                                viewModel.changeEpisode(animeId, ep, server, lang, quality)
                            },
                        )
                    }

                    // Members row
                    if (state.members.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Members (${state.members.size})",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            state.members.forEach { member ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.width(56.dp),
                                ) {
                                    ProfileAvatar(
                                        url = member.avatarUrl,
                                        avatarSize = 40.dp,
                                        contentDescription = member.username ?: "Member",
                                        backgroundColor = Color.White.copy(alpha = 0.1f),
                                        playVideo = false,
                                    )
                                    Text(
                                        member.username ?: "User",
                                        color = Color.Gray,
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }

                    // Chat section (always visible, below members)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Room Chat",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    Spacer(Modifier.height(4.dp))

                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        state = chatListState,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(state.chatMessages, key = { it.id }) { msg ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White.copy(alpha = 0.03f))
                                    .padding(8.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        msg.username ?: "User",
                                        color = AppColors.accent,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    if (msg.userId == state.roomDetail?.hostUserId) {
                                        Spacer(Modifier.width(4.dp))
                                        Icon(
                                            Icons.Outlined.Verified,
                                            null,
                                            tint = AppColors.accent,
                                            modifier = Modifier.size(12.dp),
                                        )
                                    }
                                }
                                Text(
                                    msg.body,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                )
                            }
                        }
                    }

                    // Chat input
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = chatInput,
                            onValueChange = { chatInput = it },
                            placeholder = { Text("Type a message...", color = Color.Gray) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = {
                                if (chatInput.isNotBlank()) {
                                    viewModel.sendMessage(chatInput.trim())
                                    chatInput = ""
                                }
                            }),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = AppColors.accent,
                                unfocusedBorderColor = Color.Gray,
                                cursorColor = AppColors.accent,
                            ),
                        )
                        Spacer(Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                if (chatInput.isNotBlank()) {
                                    viewModel.sendMessage(chatInput.trim())
                                    chatInput = ""
                                }
                            },
                        ) {
                            Icon(
                                Icons.Outlined.Send,
                                "Send",
                                tint = AppColors.accent,
                            )
                        }
                    }
                }
            }
        }
    }

    if (state.hostPicker.isOpen) {
        HostEpisodeSheet(
            picker = state.hostPicker,
            onDismiss = viewModel::dismissHostPicker,
            onQueryChange = viewModel::updateHostPickerQuery,
            onAnimeSelected = viewModel::selectHostAnime,
            onEpisodeChange = viewModel::setHostEpisode,
            onLanguageSelected = viewModel::setHostLanguage,
            onServerSelected = viewModel::setHostServer,
            onStart = viewModel::applyHostPickerSelection,
        )
    }

    if (state.chatSheetOpen) {
        ChatSheet(
            chatMessages = state.chatMessages,
            chatListState = chatListState,
            onDismiss = { viewModel.setChatSheetOpen(false) },
            onSend = { body ->
                if (body.isNotBlank()) {
                    viewModel.sendMessage(body.trim())
                }
            },
        )
    }
}

@Composable
private fun RoomControls(
    animeId: String,
    episodeNumber: Int,
    server: String,
    language: String?,
    quality: String?,
    onChangeAnime: () -> Unit,
    onChangeEpisode: (String, Int, String, String?, String?) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .padding(12.dp),
    ) {
        Text("Host Controls", color = AppColors.accent, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = onChangeAnime,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f)),
                modifier = Modifier.weight(1f).height(40.dp),
                contentPadding = PaddingValues(horizontal = 6.dp),
            ) {
                Text("Change", fontSize = 11.sp, color = Color.White, maxLines = 1)
            }
            Button(
                onClick = { onChangeEpisode(animeId, episodeNumber + 1, server, language, quality) },
                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f)),
                modifier = Modifier.weight(1f).height(40.dp),
                contentPadding = PaddingValues(horizontal = 6.dp),
            ) {
                Text("Next", fontSize = 11.sp, color = Color.White, maxLines = 1)
            }
            Button(
                onClick = { onChangeEpisode(animeId, (episodeNumber - 1).coerceAtLeast(1), server, language, quality) },
                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f)),
                modifier = Modifier.weight(1f).height(40.dp),
                contentPadding = PaddingValues(horizontal = 6.dp),
            ) {
                Text("Prev", fontSize = 11.sp, color = Color.White, maxLines = 1)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HostEpisodeSheet(
    picker: W2gHostPickerState,
    onDismiss: () -> Unit,
    onQueryChange: (String) -> Unit,
    onAnimeSelected: (AnimeItem) -> Unit,
    onEpisodeChange: (String) -> Unit,
    onLanguageSelected: (String) -> Unit,
    onServerSelected: (String) -> Unit,
    onStart: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AppColors.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Text("Search Anime to Watch", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(14.dp))

            OutlinedTextField(
                value = picker.query,
                onValueChange = onQueryChange,
                label = { Text("Search anime") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                colors = w2gFieldColors(),
            )

            Spacer(Modifier.height(10.dp))

            if (picker.selectedAnime == null) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().height(320.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (picker.isSearching) {
                        item {
                            Text("Searching...", color = Color.Gray, fontSize = 14.sp, modifier = Modifier.padding(vertical = 12.dp))
                        }
                    }
                    items(picker.results, key = { it.animeId }) { anime ->
                        W2gAnimeSearchRow(anime = anime, onClick = { onAnimeSelected(anime) })
                    }
                }
            } else {
                W2gSelectedAnimeSummary(anime = picker.selectedAnime)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = picker.episode,
                    onValueChange = onEpisodeChange,
                    label = { Text("Episode") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    colors = w2gFieldColors(),
                )

                Spacer(Modifier.height(12.dp))
                Text("Audio", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    W2gChoiceButton("Sub", picker.language == "sub", Modifier.weight(1f)) { onLanguageSelected("sub") }
                    W2gChoiceButton("Dub", picker.language == "dub", Modifier.weight(1f)) { onLanguageSelected("dub") }
                }

                if (picker.language != null) {
                    Spacer(Modifier.height(12.dp))
                    Text("Server", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().height(180.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(picker.servers, key = { it.id }) { server ->
                            W2gServerRow(
                                server = server,
                                selected = picker.server == server.id,
                                onClick = { onServerSelected(server.id) },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onStart,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.accent),
                    enabled = picker.server != null && !picker.isApplying,
                ) {
                    Text(if (picker.isApplying) "Starting..." else "Start Streaming", fontSize = 16.sp)
                }
            }

            if (picker.error != null) {
                Spacer(Modifier.height(8.dp))
                Text(picker.error, color = Color.Red, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun W2gAnimeSearchRow(anime: AnimeItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .background(Color.White.copy(alpha = 0.05f))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = anime.imageUrl,
            contentDescription = anime.displayTitle,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(52.dp).clip(RoundedCornerShape(6.dp)).background(Color.White.copy(alpha = 0.08f)),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(anime.displayTitle, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val meta = listOfNotNull(
                anime.format.takeIf { it.isNotBlank() },
                anime.seasonYear?.toString(),
                anime.epCount?.let { "$it eps" },
            ).joinToString(" · ")
            if (meta.isNotBlank()) {
                Text(meta, color = Color.Gray, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private fun estimateW2gHostPosition(
    playerState: to.kuudere.anisuge.data.models.W2gPlayerState,
    duration: Double,
): Double {
    if (playerState.currentTime <= 0) return 0.0
    val elapsedSeconds = if (playerState.playing && playerState.timestamp > 0) {
        ((currentTimeMillis() - playerState.timestamp).coerceAtLeast(0L) / 1000.0)
    } else {
        0.0
    }
    val target = playerState.currentTime + elapsedSeconds
    return if (duration > 0) target.coerceIn(0.0, duration) else target.coerceAtLeast(0.0)
}

@Composable
private fun W2gSelectedAnimeSummary(anime: AnimeItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = anime.imageUrl,
            contentDescription = anime.displayTitle,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(6.dp)).background(Color.White.copy(alpha = 0.08f)),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(anime.displayTitle, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("Sub ${anime.subbed} · Dub ${anime.dubbed}", color = Color.Gray, fontSize = 12.sp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatSheet(
    chatMessages: List<W2gChatMessage>,
    chatListState: androidx.compose.foundation.lazy.LazyListState,
    onDismiss: () -> Unit,
    onSend: (String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var chatInput by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AppColors.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp)
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp),
        ) {
            Text("Room Chat", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                state = chatListState,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(chatMessages, key = { it.id }) { msg ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.03f))
                            .padding(8.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                msg.username ?: "User",
                                color = AppColors.accent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Text(msg.body, color = Color.White, fontSize = 14.sp)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = chatInput,
                    onValueChange = { chatInput = it },
                    placeholder = { Text("Type a message...", color = Color.Gray) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        onSend(chatInput)
                        chatInput = ""
                    }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = AppColors.accent,
                        unfocusedBorderColor = Color.Gray,
                        cursorColor = AppColors.accent,
                    ),
                )
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = {
                    onSend(chatInput)
                    chatInput = ""
                }) {
                    Icon(Icons.Outlined.Send, "Send", tint = AppColors.accent)
                }
            }
        }
    }
}

@Composable
private fun W2gChoiceButton(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.height(42.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) AppColors.accent else Color.White.copy(alpha = 0.1f),
        ),
    ) {
        Text(label, color = Color.White)
    }
}

@Composable
private fun W2gServerRow(server: ServerInfo, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .background(if (selected) AppColors.accent.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.05f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(server.label, color = Color.White, fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (selected) {
            Text("Selected", color = AppColors.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun w2gFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedLabelColor = AppColors.accent,
    unfocusedLabelColor = Color.Gray,
    focusedBorderColor = AppColors.accent,
    unfocusedBorderColor = Color.Gray,
    cursorColor = AppColors.accent,
)

private fun formatDuration(seconds: Long): String {
    val m = (seconds / 60).coerceAtLeast(0)
    val s = (seconds % 60).coerceAtLeast(0)
    return "%d:%02d".format(m, s)
}

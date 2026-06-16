package to.kuudere.anisuge.screens.w2g

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import to.kuudere.anisuge.AppComponent
import to.kuudere.anisuge.data.models.AnimeItem
import to.kuudere.anisuge.data.models.FALLBACK_SERVERS
import to.kuudere.anisuge.data.models.ServerInfo
import to.kuudere.anisuge.data.models.Sticker
import to.kuudere.anisuge.data.models.StickerMessage
import to.kuudere.anisuge.data.models.StreamHeaders
import to.kuudere.anisuge.data.models.W2gRoomCreateRequest
import to.kuudere.anisuge.data.models.W2gPlayerState
import to.kuudere.anisuge.data.models.W2gRoomDetail
import to.kuudere.anisuge.data.models.W2gRoomMember
import to.kuudere.anisuge.data.models.W2gRoomSummary
import to.kuudere.anisuge.data.models.SubtitleData
import to.kuudere.anisuge.data.models.W2gRoomUpdateRequest
import to.kuudere.anisuge.data.models.asHttpHeaderMap
import to.kuudere.anisuge.utils.BatchSubtitleExtract
import to.kuudere.anisuge.data.repository.ServerRepository
import to.kuudere.anisuge.data.services.InfoService
import to.kuudere.anisuge.data.services.SearchService
import to.kuudere.anisuge.data.services.SessionStore
import to.kuudere.anisuge.data.services.StickerService
import to.kuudere.anisuge.data.services.W2gRoomService
import to.kuudere.anisuge.data.services.W2gWsClient
import to.kuudere.anisuge.data.services.toSticker

data class W2gChatMessage(
    val id: String,
    val userId: String,
    val username: String?,
    val avatarUrl: String?,
    val body: String,
    val sticker: StickerMessage? = null,
    val createdAt: String,
)

data class W2gPlaybackSource(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val subtitles: List<SubtitleData> = emptyList(),
)

data class W2gHostPickerState(
    val isOpen: Boolean = false,
    val query: String = "",
    val results: List<AnimeItem> = emptyList(),
    val isSearching: Boolean = false,
    val isApplying: Boolean = false,
    val error: String? = null,
    val selectedAnime: AnimeItem? = null,
    val episode: String = "1",
    val language: String? = null,
    val server: String? = null,
    val servers: List<ServerInfo> = emptyList(),
)

data class W2gUiState(
    val rooms: List<W2gRoomSummary> = emptyList(),
    val searchQuery: String = "",
    val isLoadingRooms: Boolean = false,
    val roomDetail: W2gRoomDetail? = null,
    val playerState: W2gPlayerState = W2gPlayerState(),
    val members: List<W2gRoomMember> = emptyList(),
    val chatMessages: List<W2gChatMessage> = emptyList(),
    val isHost: Boolean = false,
    val isConnected: Boolean = false,
    val playbackSource: W2gPlaybackSource? = null,
    val isLoadingPlayback: Boolean = false,
    val hostPicker: W2gHostPickerState = W2gHostPickerState(),
    val error: String? = null,
    val loadingMessage: String? = null,
    val chatSheetOpen: Boolean = false,
    val stickers: List<Sticker> = emptyList(),
    val stickerCoins: Int = 0,
    val isPremium: Boolean = false,
    val isLoadingStickers: Boolean = false,
    val stickerError: String? = null,
    val purchasingStickerId: String? = null,
) {
    val filteredRooms: List<W2gRoomSummary>
        get() = if (searchQuery.isBlank()) rooms
        else rooms.filter { room ->
            listOf(
                room.roomName,
                room.inviteCode,
                room.hostUsername,
                room.animeTitle,
            ).any { it?.contains(searchQuery, ignoreCase = true) == true }
        }
}

private data class W2gResolvedIds(
    val anilistId: Int?,
    val malId: Int?,
)

class W2gViewModel(
    private val sessionStore: SessionStore,
    private val roomService: W2gRoomService,
    private val searchService: SearchService,
    private val serverRepository: ServerRepository,
    private val infoService: InfoService,
    private val stickerService: StickerService,
) : ViewModel() {
    private val _state = MutableStateFlow(W2gUiState())
    val state: StateFlow<W2gUiState> = _state.asStateFlow()
    private val json = Json { ignoreUnknownKeys = true }

    private var wsClient: W2gWsClient? = null
    private var wsJob: Job? = null
    private var hostPickerSearchJob: Job? = null
    private var roomsRefreshJob: Job? = null
    private var roomSearchJob: Job? = null
    private var playbackLoadJob: Job? = null
    private var currentUserId: String? = null
    private var shouldReconnect = false
    private var reconnectAttempt = 0
    private var reconnectInviteCode: String? = null

    private fun isCurrentUserHost(hostUserId: String?): Boolean {
        val userId = currentUserId?.takeIf { it.isNotBlank() } ?: return false
        return hostUserId == userId
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun anisurgeUserIdFromJwt(token: String?): String? {
        val payload = token
            ?.takeIf { it.isNotBlank() }
            ?.split(".")
            ?.getOrNull(1)
            ?: return null
        return runCatching {
            val padded = payload + "=".repeat((4 - payload.length % 4) % 4)
            val raw = Base64.UrlSafe.decode(padded).decodeToString()
            json.parseToJsonElement(raw).jsonObject["sub"]?.jsonPrimitive?.content
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    private suspend fun resolveCurrentUserId(fallbackUserId: String? = null) {
        val stored = sessionStore.get()
        anisurgeUserIdFromJwt(stored?.anisurgeToken)?.let {
            setCurrentUserId(it)
            return
        }
        fallbackUserId?.takeIf { it.isNotBlank() }?.let { setCurrentUserId(it) }
    }

    private fun clearActiveRoom(error: String? = null, loadingMessage: String? = null) {
        _state.value = _state.value.copy(
            roomDetail = null,
            playerState = W2gPlayerState(),
            members = emptyList(),
            chatMessages = emptyList(),
            isHost = false,
            isConnected = false,
            playbackSource = null,
            isLoadingPlayback = false,
            error = error,
            loadingMessage = loadingMessage,
        )
    }

    private suspend fun resolveStreamingIds(
        animeId: String?,
        anilistId: Int?,
        malId: Int?,
    ): W2gResolvedIds {
        val directAnilistId = anilistId?.takeIf { it > 0 }
        val directMalId = malId?.takeIf { it > 0 }
        if (directAnilistId != null) {
            return W2gResolvedIds(directAnilistId, directMalId)
        }

        val slug = animeId?.takeIf { it.isNotBlank() }
        val details = slug?.let {
            runCatching { infoService.getAnimeDetails(it) }.getOrNull()
        }
        val resolvedAnilistId = details?.anilistId?.takeIf { it > 0 }
            ?: slug?.toIntOrNull()?.takeIf { it > 0 }
        val resolvedMalId = directMalId ?: details?.malId?.takeIf { it > 0 }
        return W2gResolvedIds(resolvedAnilistId, resolvedMalId)
    }

    private suspend fun loadPlaybackFor(room: W2gRoomDetail?) {
        val animeId = room?.animeId
        val initialAnilistId = room?.anilistId
        val initialMalId = room?.malId
        val episode = room?.episodeNumber
        val server = room?.server?.takeIf { it.isNotBlank() }
        val language = room?.language
        val roomStreamUrl = room?.streamUrl?.takeIf { it.isNotBlank() }
        if (episode == null || server == null) {
            _state.value = _state.value.copy(playbackSource = null, isLoadingPlayback = false)
            return
        }

        // Non-host with shared stream URL: use it directly, fetch subtitles from the API
        if (!isCurrentUserHost(room.hostUserId) && roomStreamUrl != null) {
            val nonHostIds = resolveStreamingIds(animeId, initialAnilistId, initialMalId)
            val nonHostSubtitles = if (nonHostIds.anilistId != null) {
                val apiSource = if (server.endsWith("-dub", ignoreCase = true)) server.dropLast(4) else server
                val subResponse = runCatching { infoService.getVideoStream(nonHostIds.anilistId, episode, apiSource) }.getOrNull()
                val wantsDub = server.endsWith("-dub", ignoreCase = true) || language == "dub"
                val subSection = (if (wantsDub) subResponse?.dub else subResponse?.sub)
                    ?.takeIf { it.streams.isNotEmpty() }
                    ?: (if (wantsDub) subResponse?.sub else subResponse?.dub)?.takeIf { it.streams.isNotEmpty() }
                if (subSection != null) BatchSubtitleExtract.forPlayback(subSection, room?.quality) else emptyList()
            } else emptyList()
            _state.value = _state.value.copy(
                isLoadingPlayback = false,
                playbackSource = W2gPlaybackSource(roomStreamUrl, room.streamHeaders ?: emptyMap(), nonHostSubtitles),
                error = null,
            )
            return
        }

        _state.value = _state.value.copy(isLoadingPlayback = true, playbackSource = null, error = null)
        val resolvedIds = resolveStreamingIds(animeId, initialAnilistId, initialMalId)
        val anilistId = resolvedIds.anilistId
        if (anilistId == null) {
            _state.value = _state.value.copy(
                isLoadingPlayback = false,
                playbackSource = null,
                error = "Could not resolve streaming ID for this anime",
            )
            return
        }
        if (resolvedIds.anilistId != initialAnilistId || resolvedIds.malId != initialMalId) {
            val current = _state.value.roomDetail
            if (current != null && current.inviteCode == room.inviteCode) {
                _state.value = _state.value.copy(
                    roomDetail = current.copy(
                        anilistId = resolvedIds.anilistId,
                        malId = resolvedIds.malId,
                    ),
                )
            }
        }

        val apiSource = if (server.endsWith("-dub", ignoreCase = true)) server.dropLast(4) else server
        val response = runCatching {
            var result = infoService.getVideoStream(anilistId, episode, apiSource)
            if (
                result == null ||
                (result.sub?.streams.isNullOrEmpty() && result.dub?.streams.isNullOrEmpty())
            ) {
                val fallback = when {
                    apiSource.equals("anitaku-1", ignoreCase = true) -> "anitaku"
                    apiSource.equals("anitaku-2", ignoreCase = true) -> "anitaku"
                    else -> null
                }
                if (fallback != null) result = infoService.getVideoStream(anilistId, episode, fallback)
            }
            result
        }.getOrNull()

        var subStreams = response?.sub
        var dubStreams = response?.dub
        if (apiSource.equals("suzu", ignoreCase = true)) {
            val embedUrl = subStreams?.episodeId ?: dubStreams?.episodeId
            if (!embedUrl.isNullOrBlank()) {
                val fresh = infoService.fetchSuzuEmbedStreams(embedUrl).orEmpty()
                if (fresh.isNotEmpty()) {
                    val referer = embedUrl.substringBeforeLast("/", "https://senshi.live")
                    val mapped = fresh.map {
                        to.kuudere.anisuge.data.models.StreamInfo(
                            url = it.url,
                            quality = it.status ?: "Auto",
                            headers = StreamHeaders(Referer = referer),
                        )
                    }
                    val dubFresh = mapped.filter { it.quality.equals("Dub", ignoreCase = true) }
                    val subFresh = mapped.filter { !it.quality.equals("Dub", ignoreCase = true) }
                    if (subFresh.isNotEmpty()) subStreams = (subStreams ?: to.kuudere.anisuge.data.models.BatchScrapeStreamData()).copy(streams = subFresh)
                    if (dubFresh.isNotEmpty()) dubStreams = (dubStreams ?: to.kuudere.anisuge.data.models.BatchScrapeStreamData()).copy(streams = dubFresh)
                }
            }
        }

        val wantsDub = server.endsWith("-dub", ignoreCase = true) || language == "dub"
        val section = (if (wantsDub) dubStreams else subStreams)
            ?.takeIf { it.streams.isNotEmpty() }
            ?: (if (wantsDub) subStreams else dubStreams)?.takeIf { it.streams.isNotEmpty() }
        val stream = section?.streams?.firstOrNull { it.url.isNotBlank() }
        val resolvedUrl = stream?.url
        val resolvedHeaders = stream?.headers.asHttpHeaderMap()
        val resolvedSubtitles = if (section != null) BatchSubtitleExtract.forPlayback(section, room?.quality) else emptyList()
        _state.value = _state.value.copy(
            isLoadingPlayback = false,
            playbackSource = resolvedUrl?.let { W2gPlaybackSource(it, resolvedHeaders, resolvedSubtitles) },
            error = if (stream == null) "No stream found for this episode/server" else null,
        )

        // Host: store resolved stream URL on the room so late joiners can use it
        if (resolvedUrl != null && isCurrentUserHost(room.hostUserId)) {
            val detail = _state.value.roomDetail
            if (detail != null) {
                _state.value = _state.value.copy(
                    roomDetail = detail.copy(streamUrl = resolvedUrl, streamHeaders = resolvedHeaders),
                )
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _state.value = _state.value.copy(searchQuery = query)
        roomSearchJob?.cancel()
        roomSearchJob = viewModelScope.launch {
            delay(if (query.isBlank()) 0 else 250)
            loadRooms()
        }
    }

    private suspend fun refreshRooms() {
        _state.value = _state.value.copy(isLoadingRooms = true, error = null)
        val response = roomService.listRooms(query = _state.value.searchQuery.takeIf { it.isNotBlank() })
        println("[W2gViewModel] loaded rooms count=${response?.rooms?.size ?: -1}")
        _state.value = _state.value.copy(
            rooms = response?.rooms ?: emptyList(),
            isLoadingRooms = false,
            error = if (response == null) "Failed to load rooms" else null,
        )
    }

    fun loadRooms() {
        viewModelScope.launch {
            refreshRooms()
        }
    }

    fun startRoomAutoRefresh() {
        roomsRefreshJob?.cancel()
        roomsRefreshJob = viewModelScope.launch {
            refreshRooms()
            while (true) {
                delay(15_000)
                refreshRooms()
            }
        }
    }

    fun stopRoomAutoRefresh() {
        roomsRefreshJob?.cancel()
        roomsRefreshJob = null
    }

    fun openHostPicker() {
        val servers = serverRepository.getAvailableServers().ifEmpty { FALLBACK_SERVERS }
        val detail = _state.value.roomDetail
        val initialLanguage = detail?.language?.takeIf { it == "sub" || it == "dub" }
        val initialServers = servers.filterForLanguage(initialLanguage)
        _state.value = _state.value.copy(
            hostPicker = W2gHostPickerState(
                isOpen = true,
                episode = (detail?.episodeNumber ?: 1).toString(),
                language = initialLanguage,
                server = detail?.server?.takeIf { id -> initialServers.any { it.id == id } },
                servers = initialServers,
            ),
        )
        searchHostAnime("")
    }

    fun dismissHostPicker() {
        hostPickerSearchJob?.cancel()
        _state.value = _state.value.copy(hostPicker = W2gHostPickerState())
    }

    fun updateHostPickerQuery(query: String) {
        _state.value = _state.value.copy(
            hostPicker = _state.value.hostPicker.copy(query = query),
        )
        searchHostAnime(query)
    }

    private fun searchHostAnime(query: String) {
        hostPickerSearchJob?.cancel()
        hostPickerSearchJob = viewModelScope.launch {
            delay(if (query.isBlank()) 0 else 250)
            _state.value = _state.value.copy(
                hostPicker = _state.value.hostPicker.copy(isSearching = true, error = null),
            )
            val response = runCatching {
                searchService.search(q = query.takeIf { it.isNotBlank() }, limit = 12)
            }.getOrNull()
            _state.value = _state.value.copy(
                hostPicker = _state.value.hostPicker.copy(
                    isSearching = false,
                    results = response?.results.orEmpty(),
                    error = if (response == null) "Could not search anime" else null,
                ),
            )
        }
    }

    fun selectHostAnime(anime: AnimeItem) {
        val episode = anime.episode?.episodeNumber?.takeIf { it > 0 } ?: 1
        val language = when {
            anime.subbed >= episode -> "sub"
            anime.dubbed >= episode -> "dub"
            else -> null
        }
        val servers = serverRepository.getAvailableServers().ifEmpty { FALLBACK_SERVERS }.filterForLanguage(language)
        _state.value = _state.value.copy(
            hostPicker = _state.value.hostPicker.copy(
                selectedAnime = anime,
                episode = episode.toString(),
                language = language,
                server = null,
                servers = servers,
                error = null,
            ),
        )
    }

    fun clearHostAnimeSelection() {
        val picker = _state.value.hostPicker
        _state.value = _state.value.copy(
            hostPicker = picker.copy(
                selectedAnime = null,
                episode = "1",
                language = null,
                server = null,
                servers = emptyList(),
                error = null,
            ),
        )
        if (picker.results.isEmpty()) {
            searchHostAnime(picker.query)
        }
    }

    fun setHostEpisode(value: String) {
        _state.value = _state.value.copy(
            hostPicker = _state.value.hostPicker.copy(
                episode = value.filter { it.isDigit() }.take(4),
                server = null,
            ),
        )
    }

    fun setHostLanguage(language: String) {
        val servers = serverRepository.getAvailableServers().ifEmpty { FALLBACK_SERVERS }.filterForLanguage(language)
        _state.value = _state.value.copy(
            hostPicker = _state.value.hostPicker.copy(
                language = language,
                server = null,
                servers = servers,
                error = null,
            ),
        )
    }

    fun setHostServer(serverId: String) {
        _state.value = _state.value.copy(
            hostPicker = _state.value.hostPicker.copy(server = serverId, error = null),
        )
    }

    fun applyHostPickerSelection() {
        val picker = _state.value.hostPicker
        val anime = picker.selectedAnime
        val episode = picker.episode.toIntOrNull()
        val language = picker.language
        val server = picker.server
        val inviteCode = _state.value.roomDetail?.inviteCode
        when {
            anime == null -> {
                _state.value = _state.value.copy(hostPicker = picker.copy(error = "Choose an anime"))
                return
            }
            episode == null || episode <= 0 -> {
                _state.value = _state.value.copy(hostPicker = picker.copy(error = "Choose a valid episode"))
                return
            }
            language == null -> {
                _state.value = _state.value.copy(hostPicker = picker.copy(error = "Choose sub or dub"))
                return
            }
            server == null -> {
                _state.value = _state.value.copy(hostPicker = picker.copy(error = "Choose a server"))
                return
            }
        }

        viewModelScope.launch {
            _state.value = _state.value.copy(hostPicker = picker.copy(isApplying = true, error = null))
            val resolvedIds = resolveStreamingIds(anime.animeId, anime.anilistId, anime.malId)
            val anilistId = resolvedIds.anilistId
            if (anilistId == null) {
                _state.value = _state.value.copy(
                    hostPicker = _state.value.hostPicker.copy(
                        isApplying = false,
                        error = "Could not resolve streaming ID for this anime",
                    ),
                )
                return@launch
            }

            _state.value = _state.value.copy(hostPicker = W2gHostPickerState())
            changeEpisode(
                anime.animeId,
                episode,
                server,
                language,
                "auto",
                anime.displayTitle,
                anime.imageUrl,
                anilistId,
                resolvedIds.malId,
            )
            if (!state.value.isConnected && inviteCode != null) {
                roomService.updateRoom(
                    inviteCode,
                    W2gRoomUpdateRequest(
                        animeId = anime.animeId,
                        episodeNumber = episode,
                        server = server,
                        language = language,
                        quality = "auto",
                        animeTitle = anime.displayTitle,
                        animePoster = anime.imageUrl,
                        anilistId = anilistId,
                        malId = resolvedIds.malId,
                    ),
                )
            }
        }
    }

    private fun List<ServerInfo>.filterForLanguage(language: String?): List<ServerInfo> {
        return when (language) {
            "sub" -> filter { it.supportsSub && !it.id.endsWith("-dub", ignoreCase = true) }
            "dub" -> filter { it.supportsDub || it.id.endsWith("-dub", ignoreCase = true) }
            else -> this
        }.filter { it.active }.ifEmpty { this }
    }

    suspend fun createRoom(request: W2gRoomCreateRequest): String? {
        disconnect()
        clearActiveRoom(loadingMessage = "Creating room...")
        resolveCurrentUserId()
        val response = roomService.createRoom(request)
        val room = response?.room
        if (room != null && currentUserId.isNullOrBlank()) {
            setCurrentUserId(room.hostUserId)
        }
        if (room != null) {
            _state.value = _state.value.copy(
                loadingMessage = null,
                roomDetail = room,
                members = room.members,
                playerState = room.playerState ?: W2gPlayerState(),
                isHost = isCurrentUserHost(room.hostUserId),
                error = null,
            )
            loadPlaybackFor(room)
            loadRooms()
        } else {
            clearActiveRoom(error = "Failed to create room")
        }
        return response?.inviteCode
    }

    suspend fun joinRoom(inviteCode: String, password: String? = null): Result<W2gRoomDetail> {
        _state.value = _state.value.copy(error = null)
        val response = roomService.joinRoom(inviteCode, password)
        val room = response?.room
        if (room != null) {
            _state.value = _state.value.copy(
                roomDetail = room,
                members = room.members,
                playerState = room.playerState ?: W2gPlayerState(),
                isHost = isCurrentUserHost(room.hostUserId),
            )
            loadPlaybackFor(room)
            return Result.success(room)
        }
        val error = roomService.lastError ?: "Failed to join room"
        _state.value = _state.value.copy(error = error)
        return Result.failure(Exception(error))
    }

    fun setCurrentUserId(userId: String) {
        currentUserId = userId.takeIf { it.isNotBlank() }
    }

    suspend fun enterRoom(inviteCode: String, fallbackUserId: String? = null): Result<W2gRoomDetail> {
        val previousRoom = _state.value.roomDetail
        if (previousRoom?.inviteCode != null && previousRoom.inviteCode != inviteCode) {
            disconnect()
            clearActiveRoom(loadingMessage = "Joining room...")
        } else {
            _state.value = _state.value.copy(error = null, loadingMessage = "Joining room...")
        }
        resolveCurrentUserId(fallbackUserId)

        println("[W2gViewModel] enterRoom invite=$inviteCode user=${currentUserId ?: "<missing>"}")
        val existingRoom = _state.value.roomDetail
        val userId = currentUserId
        val alreadyInRoom = existingRoom?.inviteCode == inviteCode &&
            userId != null &&
            (existingRoom.hostUserId == userId || existingRoom.members.any { it.userId == userId })
        if (alreadyInRoom) {
            _state.value = _state.value.copy(
                loadingMessage = null,
                isHost = isCurrentUserHost(existingRoom.hostUserId),
            )
            println("[W2gViewModel] using existing room invite=$inviteCode host=${existingRoom.hostUserId} self=$userId isHost=${_state.value.isHost}")
            connect(inviteCode)
            return Result.success(existingRoom)
        }

        val result = joinRoom(inviteCode)
        _state.value = _state.value.copy(loadingMessage = null)
        result
            .onSuccess {
                println("[W2gViewModel] joined room invite=$inviteCode host=${it.hostUserId} self=${currentUserId ?: "<missing>"} isHost=${_state.value.isHost}")
                connect(inviteCode)
            }
            .onFailure {
                println("[W2gViewModel] join room failed invite=$inviteCode: ${it.message}")
            }
        return result
    }

    suspend fun leaveRoom(inviteCode: String, closeRoom: Boolean = false): Boolean {
        val left = roomService.leaveRoom(inviteCode, action = if (closeRoom) "close" else "transfer")
        if (!left) {
            _state.value = _state.value.copy(error = roomService.lastError ?: "Failed to leave room")
            return false
        }
        disconnect()
        clearActiveRoom()
        _state.value = _state.value.copy(searchQuery = "")
        refreshRooms()
        return left
    }

    fun connect(inviteCode: String) {
        disconnect()
        viewModelScope.launch {
            val stored = sessionStore.get()
            if (stored?.anisurgeToken.isNullOrBlank()) {
                _state.value = _state.value.copy(error = "Missing Anisurge session token")
                println("[W2gViewModel] connect skipped: missing Anisurge token")
                return@launch
            }
            println("[W2gViewModel] connecting websocket invite=$inviteCode")
            doConnect(inviteCode, stored.anisurgeToken)
        }
    }



    private fun doConnect(inviteCode: String, token: String) {
        shouldReconnect = true
        reconnectInviteCode = inviteCode
        reconnectAttempt = 0

        wsJob = viewModelScope.launch {
            while (shouldReconnect && isActive) {
                wsClient = W2gWsClient(inviteCode, sessionStore, AppComponent.httpClient)
                wsClient?.connect()?.collect { event ->
                    when (event) {
                        is W2gWsClient.Event.Connected -> {
                            reconnectAttempt = 0
                            _state.value = _state.value.copy(isConnected = true, error = null)
                        }

                        is W2gWsClient.Event.Disconnected -> {
                            _state.value = _state.value.copy(isConnected = false)
                        }

                        is W2gWsClient.Event.RoomInfo -> {
                            _state.value = _state.value.copy(
                                roomDetail = event.room,
                                members = event.room.members,
                                playerState = event.room.playerState ?: W2gPlayerState(),
                                isHost = isCurrentUserHost(event.room.hostUserId),
                            )
                            loadPlaybackFor(event.room)
                        }

                        is W2gWsClient.Event.PlayerState -> {
                            _state.value = _state.value.copy(playerState = event.state)
                        }

                    is W2gWsClient.Event.EpisodeChange -> {
                        playbackLoadJob?.cancel()
                        val current = _state.value.roomDetail
                        _state.value = _state.value.copy(
                            roomDetail = current?.copy(
                                animeId = event.animeId,
                                animeTitle = event.animeTitle,
                                animePoster = event.animePoster,
                                anilistId = event.anilistId,
                                malId = event.malId,
                                episodeNumber = event.episodeNumber,
                                server = event.server,
                                language = event.language,
                                quality = event.quality,
                                streamUrl = event.streamUrl,
                                streamHeaders = event.streamHeaders,
                            ),
                            playerState = W2gPlayerState(),
                            playbackSource = null,
                            isLoadingPlayback = true,
                            error = null,
                        )
                        playbackLoadJob = viewModelScope.launch {
                            loadPlaybackFor(_state.value.roomDetail)
                        }
                    }

                    is W2gWsClient.Event.RoomClosed -> {
                        _state.value = _state.value.copy(
                            error = "Room was closed by the host",
                            isConnected = false,
                        )
                        disconnect()
                    }

                        is W2gWsClient.Event.MemberJoined -> {
                            val newMember = W2gRoomMember(
                                userId = event.userId,
                                username = event.username,
                                avatarUrl = event.avatarUrl,
                            )
                            val updated = _state.value.members.toMutableList()
                            if (updated.none { it.userId == event.userId }) {
                                updated.add(newMember)
                            }
                            _state.value = _state.value.copy(members = updated)
                        }

                        is W2gWsClient.Event.MemberLeft -> {
                            _state.value = _state.value.copy(
                                members = _state.value.members.filter { it.userId != event.userId }
                            )
                        }

                        is W2gWsClient.Event.HostChanged -> {
                            val current = _state.value.roomDetail
                            _state.value = _state.value.copy(
                                roomDetail = current?.copy(
                                    hostUserId = event.userId,
                                    hostUsername = event.username,
                                ),
                                isHost = isCurrentUserHost(event.userId),
                            )
                        }

                        is W2gWsClient.Event.ChatMessage -> {
                            val msg = W2gChatMessage(
                                id = event.id,
                                userId = event.userId,
                                username = event.username,
                                avatarUrl = event.avatarUrl,
                                body = event.body,
                                sticker = event.sticker,
                                createdAt = event.createdAt,
                            )
                            _state.value = _state.value.copy(
                                chatMessages = _state.value.chatMessages + msg
                            )
                        }

                        is W2gWsClient.Event.Error -> {
                            _state.value = _state.value.copy(error = event.message)
                        }
                    }
                }

                if (shouldReconnect && isActive) {
                    reconnectAttempt++
                    val delayMs = (1_000L * (1 shl minOf(reconnectAttempt - 1, 5))).coerceAtMost(30_000L)
                    _state.value = _state.value.copy(loadingMessage = "Reconnecting in ${delayMs / 1000}s...")
                    delay(delayMs)
                    val stored = sessionStore.get()
                    if (stored?.anisurgeToken.isNullOrBlank()) {
                        _state.value = _state.value.copy(error = "Session expired, reconnection failed")
                        break
                    }
                }
            }
        }
    }

    fun disconnect() {
        shouldReconnect = false
        reconnectInviteCode = null
        wsJob?.cancel()
        wsJob = null
        wsClient?.disconnect()
        wsClient = null
        _state.value = _state.value.copy(isConnected = false, loadingMessage = null)
    }

    fun play(currentTime: Double) = wsClient?.sendPlay(currentTime)
    fun pause(currentTime: Double) = wsClient?.sendPause(currentTime)
    fun seek(currentTime: Double, playing: Boolean = true) = wsClient?.sendSeek(currentTime, playing)
    fun syncPosition(currentTime: Double, playing: Boolean) = wsClient?.sendSeek(currentTime, playing)
    fun changeEpisode(
        animeId: String,
        episodeNumber: Int,
        server: String,
        language: String?,
        quality: String?,
        animeTitle: String? = _state.value.roomDetail?.animeTitle,
        animePoster: String? = _state.value.roomDetail?.animePoster,
        anilistId: Int? = _state.value.roomDetail?.anilistId,
        malId: Int? = _state.value.roomDetail?.malId,
    ) {
        val current = _state.value.roomDetail
        _state.value = _state.value.copy(
            roomDetail = current?.copy(
                animeId = animeId,
                animeTitle = animeTitle,
                animePoster = animePoster,
                anilistId = anilistId,
                malId = malId,
                episodeNumber = episodeNumber,
                server = server,
                language = language,
                quality = quality,
                streamUrl = null,
                streamHeaders = null,
            ),
            playerState = W2gPlayerState(),
            playbackSource = null,
            isLoadingPlayback = true,
            error = null,
        )
        playbackLoadJob?.cancel()
        playbackLoadJob = viewModelScope.launch {
            loadPlaybackFor(_state.value.roomDetail)
            // After resolution, streamUrl/streamHeaders are set on roomDetail
            val detail = _state.value.roomDetail
            wsClient?.sendChangeEpisode(
                animeId,
                episodeNumber,
                server,
                language,
                quality,
                animeTitle,
                animePoster,
                anilistId,
                malId,
                streamUrl = detail?.streamUrl,
                streamHeaders = detail?.streamHeaders,
            )
        }
    }

    fun sendMessage(body: String) = wsClient?.sendChat(body)

    fun sendSticker(sticker: Sticker, body: String = "") {
        wsClient?.sendChat(body.trim(), sticker.id)
    }

    fun loadStickers(force: Boolean = false) {
        val current = _state.value
        if (!force && (current.stickers.isNotEmpty() || current.isLoadingStickers)) return
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoadingStickers = true, stickerError = null)
            stickerService.fetchCatalog(catalogLimit = 100).fold(
                onSuccess = { res ->
                    _state.value = _state.value.copy(
                        stickers = res.catalog.map { it.toSticker() },
                        stickerCoins = res.coins,
                        isPremium = res.isPremium,
                        isLoadingStickers = false,
                        stickerError = null,
                    )
                },
                onFailure = { e ->
                    _state.value = _state.value.copy(
                        isLoadingStickers = false,
                        stickerError = e.message ?: "Failed to load stickers",
                    )
                },
            )
        }
    }

    fun purchaseSticker(sticker: Sticker) {
        if (sticker.accessMode != "sell" || sticker.owned || _state.value.purchasingStickerId != null) return
        viewModelScope.launch {
            _state.value = _state.value.copy(purchasingStickerId = sticker.id, stickerError = null)
            stickerService.purchase(sticker.id).fold(
                onSuccess = { res ->
                    val purchased = res.item.toSticker()
                    _state.value = _state.value.copy(
                        purchasingStickerId = null,
                        stickerCoins = res.coins,
                        stickers = _state.value.stickers.map {
                            if (it.id == purchased.id) purchased else it
                        },
                    )
                },
                onFailure = { e ->
                    _state.value = _state.value.copy(
                        purchasingStickerId = null,
                        stickerError = e.message ?: "Failed to buy sticker",
                    )
                },
            )
        }
    }

    fun setChatSheetOpen(open: Boolean) {
        _state.value = _state.value.copy(chatSheetOpen = open)
    }

    val amIHost: Boolean get() = _state.value.isHost

    override fun onCleared() {
        super.onCleared()
        shouldReconnect = false
        reconnectInviteCode = null
        roomSearchJob?.cancel()
        stopRoomAutoRefresh()
        disconnect()
    }
}

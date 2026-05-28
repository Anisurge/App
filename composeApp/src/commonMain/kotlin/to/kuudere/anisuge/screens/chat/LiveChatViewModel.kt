package to.kuudere.anisuge.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import to.kuudere.anisuge.data.models.ChatLiveEvent
import to.kuudere.anisuge.data.models.ChatAnimeCard
import to.kuudere.anisuge.data.models.ChatMemberProfile
import to.kuudere.anisuge.data.models.ChatMessage
import to.kuudere.anisuge.data.models.ChatMessageMetadata
import to.kuudere.anisuge.data.models.AnimeItem
import to.kuudere.anisuge.data.models.SessionCheckResult
import to.kuudere.anisuge.data.services.AuthService
import to.kuudere.anisuge.data.services.ChatService
import to.kuudere.anisuge.data.services.SearchService
import to.kuudere.anisuge.navigation.Screen
import to.kuudere.anisuge.ui.ChatFramePrefetch

enum class LiveChatConnectionState {
    Connecting,
    Connected,
    Disconnected,
    Error,
}

data class LiveChatUiState(
    val isLoading: Boolean = true,
    val needsAuth: Boolean = false,
    val messages: List<ChatMessage> = emptyList(),
    val draft: String = "",
    val isSending: Boolean = false,
    val onlineCount: Int = 0,
    val roomName: String = Screen.LiveChat.displayName,
    val connectionState: LiveChatConnectionState = LiveChatConnectionState.Connecting,
    val error: String? = null,
    val currentUserId: String? = null,
    val currentUserAvatar: String? = null,
    val currentUserFrameUrl: String? = null,
    val currentUserOuterFrameUrl: String? = null,
    val currentUsername: String? = null,
    val hasMoreOlder: Boolean = false,
    val isLoadingOlder: Boolean = false,
    val selectedMember: ChatMemberProfile? = null,
    val animePickerOpen: Boolean = false,
    val animePickerQuery: String = "",
    val animePickerResults: List<AnimeItem> = emptyList(),
    val animePickerLoading: Boolean = false,
    val animePickerError: String? = null,
    val cooldownSecondsLeft: Int = 0,
)

class LiveChatViewModel(
    private val chatService: ChatService,
    private val authService: AuthService,
    private val searchService: SearchService,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LiveChatUiState())
    val uiState: StateFlow<LiveChatUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            authService.authState.collect { result ->
                if (result is SessionCheckResult.Valid) {
                    applyCurrentUserProfile(result.user)
                }
            }
        }
    }

    private fun applyCurrentUserProfile(profile: to.kuudere.anisuge.data.models.UserProfile?) {
        isCurrentUserPremium = profile?.isPremium == true
        prefetchChatFrameEntries(
            ChatFramePrefetch.entriesFromProfile(
                frameUrl = profile?.equippedFrameUrl,
                outerFrameUrl = profile?.equippedOuterFrameUrl,
                userId = profile?.effectiveId,
            ),
        )
        _uiState.update { state ->
            val next = state.copy(
                currentUserId = profile?.effectiveId,
                currentUserAvatar = profile?.effectiveAvatar,
                currentUserFrameUrl = profile?.equippedFrameUrl,
                currentUserOuterFrameUrl = profile?.equippedOuterFrameUrl,
                currentUsername = profile?.displayName ?: profile?.username,
            )
            next.copy(messages = next.messages.map { enrichOwnMessage(it, next) })
        }
    }

    private fun prefetchChatFrames(messages: List<ChatMessage>) {
        if (messages.isEmpty()) return
        viewModelScope.launch { ChatFramePrefetch.prefetch(messages) }
    }

    private fun prefetchChatFrameEntries(entries: List<Pair<String, String?>>) {
        if (entries.isEmpty()) return
        viewModelScope.launch { ChatFramePrefetch.prefetchEntries(entries) }
    }

    private fun enrichOwnMessage(message: ChatMessage, state: LiveChatUiState): ChatMessage {
        val userId = state.currentUserId ?: return message
        if (message.userId != userId) return message
        return message.copy(
            username = message.username.ifBlank { state.currentUsername ?: message.username },
            avatarUrl = message.avatarUrl?.takeIf { it.isNotBlank() } ?: state.currentUserAvatar,
            avatarFrameUrl = state.currentUserFrameUrl ?: message.avatarFrameUrl,
            avatarOuterUrl = state.currentUserOuterFrameUrl ?: message.avatarOuterUrl,
        )
    }

    private var wsJob: Job? = null
    private var loadOlderJob: Job? = null
    private var reconnectJob: Job? = null
    private var animeSearchJob: Job? = null
    private var memberProfileJob: Job? = null
    private var cooldownJob: Job? = null
    private var screenActive = false
    private var hasInitialized = false
    private var lastSentAtMs: Long = 0L
    private var isCurrentUserPremium: Boolean = false

    companion object {
        private const val COOLDOWN_MS = 5_000L
    }

    /** Called when the chat screen enters composition (including return from back stack). */
    fun onScreenVisible() {
        screenActive = true
        if (!hasInitialized || _uiState.value.needsAuth) {
            start()
        } else {
            resumeSession()
        }
    }

    /** Called when leaving the chat screen — tear down WS so we can reconnect cleanly on return. */
    fun onScreenHidden() {
        screenActive = false
        reconnectJob?.cancel()
        reconnectJob = null
        wsJob?.cancel()
        wsJob = null
    }

    fun start() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    error = null,
                    connectionState = LiveChatConnectionState.Connecting,
                    messages = emptyList(),
                    hasMoreOlder = false,
                )
            }

            when (val auth = authService.checkSession()) {
                is SessionCheckResult.Valid -> {
                    applyCurrentUserProfile(auth.user)
                    _uiState.update { it.copy(needsAuth = false) }
                }
                else -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            needsAuth = true,
                            connectionState = LiveChatConnectionState.Disconnected,
                        )
                    }
                    return@launch
                }
            }

            val room = chatService.fetchRoom()
            val history = chatService.fetchHistory(limit = 50)
            val historyMessages = (history?.messages ?: emptyList())
                .map { enrichOwnMessage(it, _uiState.value) }
            prefetchChatFrames(historyMessages)

            _uiState.update {
                it.copy(
                    isLoading = false,
                    roomName = room?.name ?: Screen.LiveChat.displayName,
                    onlineCount = room?.onlineCount ?: 0,
                    messages = historyMessages,
                    hasMoreOlder = history?.hasMore == true,
                    error = if (history == null) {
                        "Could not load chat history. Pull to refresh after the server is updated."
                    } else {
                        null
                    },
                )
            }

            hasInitialized = true
            connectWebSocket()
        }
    }

    private fun resumeSession() {
        viewModelScope.launch {
            reconnectJob?.cancel()
            _uiState.update {
                it.copy(
                    error = null,
                    connectionState = LiveChatConnectionState.Connecting,
                )
            }
            when (val auth = authService.checkSession()) {
                is SessionCheckResult.Valid -> {
                    applyCurrentUserProfile(auth.user)
                    _uiState.update { it.copy(needsAuth = false) }
                }
                else -> {
                    _uiState.update {
                        it.copy(
                            needsAuth = true,
                            connectionState = LiveChatConnectionState.Disconnected,
                        )
                    }
                    return@launch
                }
            }
            refreshRoomAndHistory()
            connectWebSocket()
        }
    }

    private suspend fun refreshRoomAndHistory() {
        val room = chatService.fetchRoom()
        val history = chatService.fetchHistory(limit = 50)
        _uiState.update { state ->
            val merged = if (history != null) {
                mergeMessages(state.messages, history.messages)
            } else {
                state.messages
            }
            val enriched = merged.map { enrichOwnMessage(it, state) }
            prefetchChatFrames(enriched)
            state.copy(
                roomName = room?.name ?: state.roomName,
                onlineCount = room?.onlineCount ?: state.onlineCount,
                messages = enriched,
                hasMoreOlder = history?.hasMore ?: state.hasMoreOlder,
            )
        }
    }

    fun onDraftChange(value: String) {
        if (value.trim().equals("/anime", ignoreCase = true)) {
            _uiState.update { it.copy(draft = "", error = null) }
            openAnimePicker()
            return
        }
        _uiState.update { it.copy(draft = value.take(500), error = null) }
    }

    fun insertSurgeMention() {
        val current = _uiState.value.draft
        val lastAt = current.lastIndexOf('@')
        val next = if (lastAt >= 0 && current.substring(lastAt).none { it.isWhitespace() }) {
            current.substring(0, lastAt) + "@surge "
        } else {
            (current.trimEnd() + " @surge ").trimStart()
        }
        _uiState.update { it.copy(draft = next.take(500), error = null) }
    }

    fun sendMessage() {
        val text = _uiState.value.draft.trim()
        if (text.isEmpty() || _uiState.value.isSending) return
        if (text.equals("/anime", ignoreCase = true) || text.startsWith("/anime ", ignoreCase = true)) {
            val query = text.drop("/anime".length).trim()
            _uiState.update { it.copy(draft = "") }
            openAnimePicker(query)
            return
        }

        // Cooldown check for non-premium users (5 seconds between messages)
        if (!isCurrentUserPremium) {
            val now = Clock.System.now().toEpochMilliseconds()
            val elapsed = now - lastSentAtMs
            if (elapsed < COOLDOWN_MS) {
                val remainingSec = ((COOLDOWN_MS - elapsed) / 1000).toInt() + 1
                _uiState.update { it.copy(error = "Wait ${remainingSec}s before sending again", cooldownSecondsLeft = remainingSec) }
                startCooldownTimer(COOLDOWN_MS - elapsed)
                return
            }
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true, error = null, cooldownSecondsLeft = 0) }
            postChatMessage(text)
            lastSentAtMs = Clock.System.now().toEpochMilliseconds()
            if (!isCurrentUserPremium) {
                startCooldownTimer(COOLDOWN_MS)
            }
        }
    }

    fun openAnimePicker(initialQuery: String = "") {
        _uiState.update {
            it.copy(
                animePickerOpen = true,
                animePickerQuery = initialQuery,
                animePickerResults = emptyList(),
                animePickerError = null,
            )
        }
        searchAnimeForPicker(initialQuery)
    }

    fun dismissAnimePicker() {
        animeSearchJob?.cancel()
        _uiState.update {
            it.copy(
                animePickerOpen = false,
                animePickerLoading = false,
                animePickerError = null,
            )
        }
    }

    fun onAnimePickerQueryChange(value: String) {
        val query = value.take(80)
        _uiState.update { it.copy(animePickerQuery = query, animePickerError = null) }
        searchAnimeForPicker(query)
    }

    fun sendAnimeCard(anime: AnimeItem) {
        val title = anime.displayTitle.ifBlank { anime.animeId }
        val metadata = ChatMessageMetadata(
            kind = "anime_card",
            anime = ChatAnimeCard(
                animeId = anime.animeId,
                title = title,
                description = anime.description,
                imageUrl = anime.imageUrl,
                format = anime.format.takeIf { it.isNotBlank() },
                year = anime.seasonYear,
                score = anime.score,
                episodes = anime.epCount,
            ),
        )

        // Cooldown check for non-premium users
        if (!isCurrentUserPremium) {
            val now = Clock.System.now().toEpochMilliseconds()
            val elapsed = now - lastSentAtMs
            if (elapsed < COOLDOWN_MS) {
                val remainingSec = ((COOLDOWN_MS - elapsed) / 1000).toInt() + 1
                _uiState.update {
                    it.copy(
                        animePickerOpen = false,
                        error = "Wait ${remainingSec}s before sending again",
                        cooldownSecondsLeft = remainingSec,
                    )
                }
                startCooldownTimer(COOLDOWN_MS - elapsed)
                return
            }
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    animePickerOpen = false,
                    animePickerLoading = false,
                    isSending = true,
                    error = null,
                    cooldownSecondsLeft = 0,
                )
            }
            postChatMessage("Shared an anime: $title", metadata)
            lastSentAtMs = Clock.System.now().toEpochMilliseconds()
            if (!isCurrentUserPremium) {
                startCooldownTimer(COOLDOWN_MS)
            }
        }
    }

    private fun startCooldownTimer(remainingMs: Long) {
        cooldownJob?.cancel()
        cooldownJob = viewModelScope.launch {
            var left = remainingMs
            while (left > 0) {
                _uiState.update { it.copy(cooldownSecondsLeft = ((left + 999) / 1000).toInt()) }
                delay(1000)
                left -= 1000
            }
            _uiState.update { it.copy(cooldownSecondsLeft = 0, error = null) }
        }
    }

    private suspend fun postChatMessage(
        text: String,
        metadata: ChatMessageMetadata? = null,
    ) {
        val result = chatService.postMessage(text, metadata = metadata)
        result.fold(
            onSuccess = { message ->
                val snapshot = _uiState.value
                val enriched = enrichOwnMessage(
                    message.copy(
                        username = message.username.ifBlank {
                            snapshot.currentUsername ?: message.username
                        },
                        avatarUrl = message.avatarUrl?.takeIf { it.isNotBlank() }
                            ?: snapshot.currentUserAvatar,
                    ),
                    snapshot,
                )
                prefetchChatFrames(listOf(enriched))
                _uiState.update { state ->
                    val merged = mergeMessages(state.messages, listOf(enriched))
                    state.copy(
                        draft = "",
                        isSending = false,
                        messages = merged,
                        error = null,
                    )
                }
            },
            onFailure = { e ->
                _uiState.update {
                    it.copy(
                        isSending = false,
                        error = e.message ?: "Failed to send",
                    )
                }
            },
        )
    }

    private fun searchAnimeForPicker(query: String) {
        animeSearchJob?.cancel()
        animeSearchJob = viewModelScope.launch {
            delay(if (query.isBlank()) 0 else 250)
            _uiState.update { it.copy(animePickerLoading = true, animePickerError = null) }
            val response = runCatching {
                searchService.search(q = query.takeIf { it.isNotBlank() }, limit = 12)
            }.getOrNull()
            _uiState.update {
                it.copy(
                    animePickerLoading = false,
                    animePickerResults = response?.results.orEmpty(),
                    animePickerError = if (response == null) "Could not search anime" else null,
                )
            }
        }
    }

    fun loadOlderMessages() {
        val state = _uiState.value
        if (state.isLoadingOlder || !state.hasMoreOlder || state.messages.isEmpty()) return
        val before = state.messages.first().createdAt
        if (before.isBlank()) return

        loadOlderJob?.cancel()
        loadOlderJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoadingOlder = true) }
            val page = chatService.fetchHistory(limit = 50, before = before)
            if (page == null) {
                _uiState.update { it.copy(isLoadingOlder = false) }
                return@launch
            }
            _uiState.update { current ->
                val merged = mergeMessages(current.messages, page.messages)
                prefetchChatFrames(merged)
                current.copy(
                    isLoadingOlder = false,
                    hasMoreOlder = page.hasMore,
                    messages = merged,
                )
            }
        }
    }

    fun showMemberProfile(message: ChatMessage) {
        val snapshot = message.toMemberProfile().copy(isLoadingDetails = true, detailError = null)
        memberProfileJob?.cancel()
        _uiState.update { it.copy(selectedMember = snapshot) }
        memberProfileJob = viewModelScope.launch {
            val profile = chatService.fetchMemberProfile(snapshot.userId)
            _uiState.update { state ->
                if (state.selectedMember?.userId != snapshot.userId) return@update state
                if (profile == null) {
                    state.copy(
                        selectedMember = state.selectedMember.copy(
                            isLoadingDetails = false,
                            detailError = "Could not load profile details",
                        ),
                    )
                } else {
                    state.copy(
                        selectedMember = profile.toProfile(state.selectedMember)
                            .copy(isLoadingDetails = false),
                    )
                }
            }
        }
    }

    fun dismissMemberProfile() {
        memberProfileJob?.cancel()
        memberProfileJob = null
        _uiState.update { it.copy(selectedMember = null) }
    }

    fun refresh() {
        reconnectJob?.cancel()
        wsJob?.cancel()
        loadOlderJob?.cancel()
        memberProfileJob?.cancel()
        start()
    }

    override fun onCleared() {
        screenActive = false
        reconnectJob?.cancel()
        wsJob?.cancel()
        loadOlderJob?.cancel()
        animeSearchJob?.cancel()
        memberProfileJob?.cancel()
        cooldownJob?.cancel()
        super.onCleared()
    }

    private fun scheduleReconnect() {
        if (!screenActive || _uiState.value.needsAuth) return
        reconnectJob?.cancel()
        reconnectJob = viewModelScope.launch {
            delay(1_500)
            if (!screenActive || _uiState.value.needsAuth) return@launch
            val state = _uiState.value.connectionState
            if (state != LiveChatConnectionState.Connected) {
                _uiState.update {
                    it.copy(
                        connectionState = LiveChatConnectionState.Connecting,
                        error = null,
                    )
                }
                connectWebSocket()
            }
        }
    }

    private fun connectWebSocket() {
        wsJob?.cancel()
        wsJob = viewModelScope.launch {
            chatService.connectLive().collect { event ->
                when (event) {
                    is ChatLiveEvent.Connected -> {
                        reconnectJob?.cancel()
                        _uiState.update {
                            it.copy(
                                connectionState = LiveChatConnectionState.Connected,
                                error = null,
                            )
                        }
                    }
                    is ChatLiveEvent.Disconnected -> {
                        _uiState.update {
                            it.copy(connectionState = LiveChatConnectionState.Disconnected)
                        }
                        scheduleReconnect()
                    }
                    is ChatLiveEvent.Error -> {
                        _uiState.update {
                            it.copy(
                                connectionState = LiveChatConnectionState.Error,
                                error = event.message,
                            )
                        }
                        scheduleReconnect()
                    }
                    is ChatLiveEvent.Message -> {
                        val snapshot = _uiState.value
                        val incoming = enrichOwnMessage(event.message, snapshot)
                        prefetchChatFrames(listOf(incoming))
                        _uiState.update { state ->
                            state.copy(
                                messages = mergeMessages(state.messages, listOf(incoming)),
                                error = null,
                                connectionState = LiveChatConnectionState.Connected,
                            )
                        }
                    }
                }
            }
        }
    }

    private fun mergeMessages(
        existing: List<ChatMessage>,
        incoming: List<ChatMessage>,
    ): List<ChatMessage> {
        if (incoming.isEmpty()) return existing
        val snapshot = _uiState.value
        val byId = linkedMapOf<String, ChatMessage>()
        for (m in existing) {
            if (m.id.isNotBlank()) byId[m.id] = enrichOwnMessage(m, snapshot)
        }
        for (m in incoming) {
            if (m.id.isNotBlank()) byId[m.id] = enrichOwnMessage(m, snapshot)
        }
        return byId.values.sortedBy { it.createdAt }
    }
}

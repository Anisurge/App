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
import to.kuudere.anisuge.data.models.ChatLiveEvent
import to.kuudere.anisuge.data.models.ChatMemberProfile
import to.kuudere.anisuge.data.models.ChatMessage
import to.kuudere.anisuge.data.models.SessionCheckResult
import to.kuudere.anisuge.data.services.AuthService
import to.kuudere.anisuge.data.services.ChatService
import to.kuudere.anisuge.navigation.Screen

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
)

class LiveChatViewModel(
    private val chatService: ChatService,
    private val authService: AuthService,
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
    private var screenActive = false
    private var hasInitialized = false

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

            _uiState.update {
                it.copy(
                    isLoading = false,
                    roomName = room?.name ?: Screen.LiveChat.displayName,
                    onlineCount = room?.onlineCount ?: 0,
                    messages = (history?.messages ?: emptyList()).map { enrichOwnMessage(it, _uiState.value) },
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
            state.copy(
                roomName = room?.name ?: state.roomName,
                onlineCount = room?.onlineCount ?: state.onlineCount,
                messages = merged.map { enrichOwnMessage(it, state) },
                hasMoreOlder = history?.hasMore ?: state.hasMoreOlder,
            )
        }
    }

    fun onDraftChange(value: String) {
        _uiState.update { it.copy(draft = value.take(500), error = null) }
    }

    fun sendMessage() {
        val text = _uiState.value.draft.trim()
        if (text.isEmpty() || _uiState.value.isSending) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true, error = null) }
            val result = chatService.postMessage(text)
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
                current.copy(
                    isLoadingOlder = false,
                    hasMoreOlder = page.hasMore,
                    messages = mergeMessages(current.messages, page.messages),
                )
            }
        }
    }

    fun showMemberProfile(message: ChatMessage) {
        _uiState.update { it.copy(selectedMember = message.toMemberProfile()) }
    }

    fun dismissMemberProfile() {
        _uiState.update { it.copy(selectedMember = null) }
    }

    fun refresh() {
        reconnectJob?.cancel()
        wsJob?.cancel()
        loadOlderJob?.cancel()
        start()
    }

    override fun onCleared() {
        screenActive = false
        reconnectJob?.cancel()
        wsJob?.cancel()
        loadOlderJob?.cancel()
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
                        _uiState.update { state ->
                            val incoming = enrichOwnMessage(event.message, state)
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

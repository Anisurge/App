package to.kuudere.anisuge.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
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
    val roomName: String = "Live Chat",
    val connectionState: LiveChatConnectionState = LiveChatConnectionState.Connecting,
    val error: String? = null,
    val currentUserId: String? = null,
    val currentUserAvatar: String? = null,
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

    private var wsJob: Job? = null
    private var loadOlderJob: Job? = null

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
                    val profile = auth.user
                    _uiState.update {
                        it.copy(
                            needsAuth = false,
                            currentUserId = profile?.effectiveId,
                            currentUserAvatar = profile?.effectiveAvatar,
                            currentUsername = profile?.displayName
                                ?: profile?.username,
                        )
                    }
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
                    roomName = room?.name ?: "Live Chat",
                    onlineCount = room?.onlineCount ?: 0,
                    messages = history?.messages ?: emptyList(),
                    hasMoreOlder = history?.hasMore == true,
                    error = if (history == null) {
                        "Could not load chat history. Pull to refresh after the server is updated."
                    } else {
                        null
                    },
                )
            }

            connectWebSocket()
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
                    val enriched = message.copy(
                        username = message.username.ifBlank {
                            snapshot.currentUsername ?: message.username
                        },
                        avatarUrl = message.avatarUrl?.takeIf { it.isNotBlank() }
                            ?: snapshot.currentUserAvatar,
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
        wsJob?.cancel()
        loadOlderJob?.cancel()
        start()
    }

    override fun onCleared() {
        wsJob?.cancel()
        loadOlderJob?.cancel()
        super.onCleared()
    }

    private fun connectWebSocket() {
        wsJob?.cancel()
        wsJob = viewModelScope.launch {
            chatService.connectLive().collect { event ->
                when (event) {
                    is ChatLiveEvent.Connected -> {
                        _uiState.update {
                            it.copy(connectionState = LiveChatConnectionState.Connected)
                        }
                    }
                    is ChatLiveEvent.Disconnected -> {
                        _uiState.update {
                            it.copy(connectionState = LiveChatConnectionState.Disconnected)
                        }
                    }
                    is ChatLiveEvent.Error -> {
                        _uiState.update {
                            it.copy(
                                connectionState = LiveChatConnectionState.Error,
                                error = event.message,
                            )
                        }
                    }
                    is ChatLiveEvent.Message -> {
                        _uiState.update { state ->
                            state.copy(
                                messages = mergeMessages(state.messages, listOf(event.message)),
                                error = null,
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
        val byId = linkedMapOf<String, ChatMessage>()
        for (m in existing) {
            if (m.id.isNotBlank()) byId[m.id] = m
        }
        for (m in incoming) {
            if (m.id.isNotBlank()) byId[m.id] = m
        }
        return byId.values.sortedBy { it.createdAt }
    }
}

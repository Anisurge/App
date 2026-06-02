package to.kuudere.anisuge.screens.w2g

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import to.kuudere.anisuge.AppComponent
import to.kuudere.anisuge.data.models.W2gRoomCreateRequest
import to.kuudere.anisuge.data.models.W2gPlayerState
import to.kuudere.anisuge.data.models.W2gRoomDetail
import to.kuudere.anisuge.data.models.W2gRoomMember
import to.kuudere.anisuge.data.models.W2gRoomSummary
import to.kuudere.anisuge.data.services.SessionStore
import to.kuudere.anisuge.data.services.W2gRoomService
import to.kuudere.anisuge.data.services.W2gWsClient

data class W2gChatMessage(
    val id: String,
    val userId: String,
    val username: String?,
    val avatarUrl: String?,
    val body: String,
    val createdAt: String,
)

data class W2gUiState(
    val rooms: List<W2gRoomSummary> = emptyList(),
    val isLoadingRooms: Boolean = false,
    val roomDetail: W2gRoomDetail? = null,
    val playerState: W2gPlayerState = W2gPlayerState(),
    val members: List<W2gRoomMember> = emptyList(),
    val chatMessages: List<W2gChatMessage> = emptyList(),
    val isHost: Boolean = false,
    val isConnected: Boolean = false,
    val error: String? = null,
    val loadingMessage: String? = null,
)

class W2gViewModel(
    private val sessionStore: SessionStore,
    private val roomService: W2gRoomService,
) : ViewModel() {
    private val _state = MutableStateFlow(W2gUiState())
    val state: StateFlow<W2gUiState> = _state.asStateFlow()

    private var wsClient: W2gWsClient? = null
    private var wsJob: Job? = null
    private var currentUserId: String? = null

    private fun isCurrentUserHost(hostUserId: String?): Boolean {
        val userId = currentUserId?.takeIf { it.isNotBlank() } ?: return false
        return hostUserId == userId
    }

    fun loadRooms() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoadingRooms = true, error = null)
            val response = roomService.listRooms()
            _state.value = _state.value.copy(
                rooms = response?.rooms ?: emptyList(),
                isLoadingRooms = false,
                error = if (response == null) "Failed to load rooms" else null,
            )
        }
    }

    suspend fun createRoom(request: W2gRoomCreateRequest): String? {
        _state.value = _state.value.copy(error = null, loadingMessage = "Creating room...")
        val response = roomService.createRoom(request)
        _state.value = _state.value.copy(loadingMessage = null)
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
            return Result.success(room)
        }
        val error = "Failed to join room"
        _state.value = _state.value.copy(error = error)
        return Result.failure(Exception(error))
    }

    fun setCurrentUserId(userId: String) {
        currentUserId = userId
    }

    suspend fun leaveRoom(inviteCode: String): Boolean {
        disconnect()
        return roomService.leaveRoom(inviteCode)
    }

    fun connect(inviteCode: String) {
        disconnect()
        viewModelScope.launch {
            val stored = sessionStore.get()
            if (stored?.anisurgeToken.isNullOrBlank()) return@launch
            doConnect(inviteCode, stored?.anisurgeToken ?: return@launch)
        }
    }

    private fun doConnect(inviteCode: String, token: String) {

        wsClient = W2gWsClient(inviteCode, sessionStore, AppComponent.httpClient)
        wsJob = viewModelScope.launch {
            wsClient?.connect()?.collect { event ->
                when (event) {
                    is W2gWsClient.Event.Connected -> {
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
                    }

                    is W2gWsClient.Event.PlayerState -> {
                        _state.value = _state.value.copy(playerState = event.state)
                    }

                    is W2gWsClient.Event.EpisodeChange -> {
                        val current = _state.value.roomDetail
                        _state.value = _state.value.copy(
                            roomDetail = current?.copy(
                                animeId = event.animeId,
                                episodeNumber = event.episodeNumber,
                                server = event.server,
                                language = event.language,
                                quality = event.quality,
                            ),
                            playerState = W2gPlayerState(),
                        )
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
                        _state.value = _state.value.copy(
                            isHost = isCurrentUserHost(event.userId)
                        )
                    }

                    is W2gWsClient.Event.ChatMessage -> {
                        val msg = W2gChatMessage(
                            id = event.id,
                            userId = event.userId,
                            username = event.username,
                            avatarUrl = event.avatarUrl,
                            body = event.body,
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
        }
    }

    fun disconnect() {
        wsJob?.cancel()
        wsJob = null
        wsClient?.disconnect()
        wsClient = null
        _state.value = _state.value.copy(isConnected = false)
    }

    fun play(currentTime: Double) = wsClient?.sendPlay(currentTime)
    fun pause(currentTime: Double) = wsClient?.sendPause(currentTime)
    fun seek(currentTime: Double) = wsClient?.sendSeek(currentTime)
    fun changeEpisode(animeId: String, episodeNumber: Int, server: String, language: String?, quality: String?) =
        wsClient?.sendChangeEpisode(animeId, episodeNumber, server, language, quality)

    fun sendMessage(body: String) = wsClient?.sendChat(body)

    val amIHost: Boolean get() = _state.value.isHost

    override fun onCleared() {
        super.onCleared()
        disconnect()
    }
}

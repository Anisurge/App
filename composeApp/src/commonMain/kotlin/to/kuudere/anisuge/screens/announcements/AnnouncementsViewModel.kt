package to.kuudere.anisuge.screens.announcements

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import to.kuudere.anisuge.data.models.Announcement
import to.kuudere.anisuge.data.models.AnnouncementCreatePoll
import to.kuudere.anisuge.data.models.AnnouncementCreatePollOption
import to.kuudere.anisuge.data.models.AnnouncementCreateRequest
import to.kuudere.anisuge.data.models.AnnouncementPoll
import to.kuudere.anisuge.data.models.AnnouncementPollOption
import to.kuudere.anisuge.data.services.AnnouncementService
import to.kuudere.anisuge.data.services.ChatService
import to.kuudere.anisuge.platform.ChatImagePick

data class AnnouncementComposerState(
    val open: Boolean = false,
    val title: String = "",
    val body: String = "",
    val imageUrl: String = "",
    val videoUrl: String = "",
    val pinned: Boolean = false,
    val publishNow: Boolean = true,
    val pollQuestion: String = "",
    val pollOptions: List<String> = emptyList(),
    val uploadingImage: Boolean = false,
)

data class AnnouncementsUiState(
    val isLoading: Boolean = false,
    val isRefreshingStatus: Boolean = false,
    val announcements: List<Announcement> = emptyList(),
    val unreadCount: Int = 0,
    val latestId: String? = null,
    val hasMore: Boolean = false,
    val isStaff: Boolean = false,
    val error: String? = null,
    val toast: String? = null,
    val composer: AnnouncementComposerState = AnnouncementComposerState(),
    val saving: Boolean = false,
)

class AnnouncementsViewModel(
    private val announcementService: AnnouncementService,
    private val chatService: ChatService,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AnnouncementsUiState())
    val uiState: StateFlow<AnnouncementsUiState> = _uiState.asStateFlow()

    fun refreshStatus() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshingStatus = true) }
            val status = announcementService.getStatus()
            _uiState.update {
                it.copy(
                    isRefreshingStatus = false,
                    unreadCount = status?.unreadCount ?: it.unreadCount,
                    latestId = status?.latestId ?: it.latestId,
                )
            }
        }
    }

    fun load(force: Boolean = false) {
        if (_uiState.value.isLoading && !force) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            announcementService.list(includeDrafts = true)
                .onSuccess { response ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            announcements = response.announcements,
                            hasMore = response.hasMore,
                            unreadCount = response.unreadCount,
                            isStaff = response.isStaff,
                            error = null,
                        )
                    }
                    markAllRead()
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = e.message ?: "Failed to load announcements",
                        )
                    }
                }
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || !state.hasMore || state.announcements.isEmpty()) return
        val before = state.announcements.lastOrNull { it.publishedAt != null }?.publishedAt ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            announcementService.list(before = before, includeDrafts = state.isStaff)
                .onSuccess { response ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            announcements = it.announcements + response.announcements,
                            hasMore = response.hasMore,
                            unreadCount = response.unreadCount,
                            isStaff = response.isStaff,
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(isLoading = false, toast = e.message ?: "Failed to load more")
                    }
                }
        }
    }

    private fun markAllRead() {
        viewModelScope.launch {
            announcementService.markAllRead().onSuccess { response ->
                _uiState.update { state ->
                    state.copy(
                        unreadCount = response.unreadCount,
                        announcements = state.announcements.map { it.copy(read = true) },
                    )
                }
            }
        }
    }

    fun toggleUpvote(id: String) {
        val current = _uiState.value.announcements.firstOrNull { it.id == id } ?: return
        _uiState.update { state ->
            state.copy(
                announcements = state.announcements.map {
                    if (it.id == id) {
                        it.copy(
                            upvoted = !it.upvoted,
                            upvoteCount = (it.upvoteCount + if (it.upvoted) -1 else 1).coerceAtLeast(0),
                        )
                    } else it
                },
            )
        }
        viewModelScope.launch {
            announcementService.toggleUpvote(id)
                .onSuccess { response ->
                    _uiState.update { state ->
                        state.copy(
                            announcements = state.announcements.map {
                                if (it.id == id) it.copy(upvoted = response.upvoted, upvoteCount = response.upvoteCount) else it
                            },
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update { state ->
                        state.copy(
                            announcements = state.announcements.map { if (it.id == id) current else it },
                            toast = e.message ?: "Could not update vote",
                        )
                    }
                }
        }
    }

    fun votePoll(announcementId: String, optionId: String) {
        viewModelScope.launch {
            announcementService.votePoll(announcementId, optionId)
                .onSuccess { response ->
                    _uiState.update { state ->
                        state.copy(
                            announcements = state.announcements.map { announcement ->
                                if (announcement.id != announcementId) return@map announcement
                                val poll = announcement.poll ?: return@map announcement
                                val counts = response.options.associateBy { it.id }
                                announcement.copy(
                                    poll = AnnouncementPoll(
                                        question = poll.question,
                                        myOptionId = response.myOptionId,
                                        options = poll.options.map { option ->
                                            AnnouncementPollOption(
                                                id = option.id,
                                                text = option.text,
                                                votes = counts[option.id]?.votes ?: option.votes,
                                            )
                                        },
                                    ),
                                )
                            },
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(toast = e.message ?: "Could not vote in poll") }
                }
        }
    }

    fun openComposer() {
        _uiState.update { it.copy(composer = it.composer.copy(open = true)) }
    }

    fun dismissComposer() {
        _uiState.update { it.copy(composer = AnnouncementComposerState()) }
    }

    fun updateComposer(transform: (AnnouncementComposerState) -> AnnouncementComposerState) {
        _uiState.update { it.copy(composer = transform(it.composer)) }
    }

    fun addPollOption() {
        _uiState.update {
            val options = it.composer.pollOptions
            if (options.size >= 6) return@update it.copy(toast = "Polls can have up to 6 options")
            it.copy(composer = it.composer.copy(pollOptions = options + ""))
        }
    }

    fun removePollOption(index: Int) {
        _uiState.update {
            it.copy(composer = it.composer.copy(pollOptions = it.composer.pollOptions.filterIndexed { i, _ -> i != index }))
        }
    }

    fun updatePollOption(index: Int, value: String) {
        _uiState.update {
            it.copy(
                composer = it.composer.copy(
                    pollOptions = it.composer.pollOptions.mapIndexed { i, option -> if (i == index) value else option },
                ),
            )
        }
    }

    fun uploadImage(pick: ChatImagePick?) {
        if (pick == null) return
        viewModelScope.launch {
            _uiState.update { it.copy(composer = it.composer.copy(uploadingImage = true)) }
            chatService.uploadChatImage(pick)
                .onSuccess { image ->
                    _uiState.update {
                        it.copy(
                            composer = it.composer.copy(imageUrl = image.url, uploadingImage = false),
                            toast = "Image attached",
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            composer = it.composer.copy(uploadingImage = false),
                            toast = e.message ?: "Image upload failed",
                        )
                    }
                }
        }
    }

    fun saveAnnouncement() {
        val composer = _uiState.value.composer
        val title = composer.title.trim()
        val body = composer.body.trim()
        if (title.length < 3 || body.isBlank()) {
            _uiState.update { it.copy(toast = "Add a title and body first") }
            return
        }
        val image = composer.imageUrl.trim().takeIf { it.isNotBlank() }
        val video = composer.videoUrl.trim().takeIf { it.isNotBlank() }
        if ((image != null && !image.startsWith("http")) || (video != null && !video.startsWith("http"))) {
            _uiState.update { it.copy(toast = "Media links must start with http") }
            return
        }
        val pollOptions = composer.pollOptions.map { it.trim() }.filter { it.isNotBlank() }
        val poll = if (pollOptions.size >= 2) {
            AnnouncementCreatePoll(
                question = composer.pollQuestion.trim().takeIf { it.isNotBlank() },
                options = pollOptions.take(6).mapIndexed { index, text ->
                    AnnouncementCreatePollOption(id = "opt-${index + 1}", text = text)
                },
            )
        } else null
        viewModelScope.launch {
            _uiState.update { it.copy(saving = true, toast = null) }
            announcementService.create(
                AnnouncementCreateRequest(
                    title = title,
                    body = body,
                    status = if (composer.publishNow) "published" else "draft",
                    pinned = composer.pinned,
                    imageUrl = image,
                    videoUrl = video,
                    poll = poll,
                ),
            ).onSuccess {
                _uiState.update {
                    it.copy(
                        saving = false,
                        composer = AnnouncementComposerState(),
                        toast = "Announcement posted",
                    )
                }
                load(force = true)
            }.onFailure { e ->
                _uiState.update {
                    it.copy(saving = false, toast = e.message ?: "Could not post announcement")
                }
            }
        }
    }

    fun clearToast() {
        _uiState.update { it.copy(toast = null) }
    }
}

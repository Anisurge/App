package to.kuudere.anisuge.screens.aichat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import to.kuudere.anisuge.AppComponent
import to.kuudere.anisuge.data.models.AiChatAnimeCard
import to.kuudere.anisuge.data.models.AiChatMessageRequest
import to.kuudere.anisuge.data.models.AiChatUiMessage
import to.kuudere.anisuge.data.services.AiChatService
import to.kuudere.anisuge.data.services.AiChatToken
import to.kuudere.anisuge.data.services.SettingsStore

// ── UI State ──────────────────────────────────────────────────────────────────

data class AiChatUiState(
    val messages: List<AiChatUiMessage> = emptyList(),
    val streaming: Boolean = false,
    /** Accumulates the current in-progress streaming reply */
    val streamingText: String = "",
    val error: String? = null,
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

class AiChatViewModel(
    private val aiChatService: AiChatService = AppComponent.aiChatService,
    private val settingsStore: SettingsStore = AppComponent.settingsStore,
) : ViewModel() {

    var state by mutableStateOf(AiChatUiState())
        private set

    private var streamJob: Job? = null
    private var msgCounter = 0L

    init {
        loadHistory()
    }

    private fun loadHistory() {
        viewModelScope.launch {
            val savedMessages = settingsStore.getAiChatHistory()
            state = state.copy(messages = savedMessages)
            msgCounter = savedMessages.maxOfOrNull {
                it.id.removePrefix("u-").removePrefix("a-").toLongOrNull() ?: 0L
            } ?: 0L
        }
    }

    fun sendMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || state.streaming) return

        // Add user message
        val userMsgId = "u-${++msgCounter}"
        val userMsg = AiChatUiMessage(id = userMsgId, role = "user", content = trimmed)
        val assistantMsgId = "a-${++msgCounter}"

        // Build history for context (exclude current message)
        val history = state.messages.map { m ->
            AiChatMessageRequest(role = m.role, content = m.content)
        }

        state = state.copy(
            messages = state.messages + userMsg,
            streaming = true,
            streamingText = "",
            error = null,
        )

        // Save immediately when user message is added
        viewModelScope.launch {
            settingsStore.setAiChatHistory(state.messages)
        }

        streamJob?.cancel()
        var latestAnimeCard: AiChatAnimeCard? = null

        streamJob = viewModelScope.launch {
            try {
                aiChatService.streamMessage(
                    message = trimmed,
                    history = history,
                ).collect { token ->
                    when (token) {
                        is AiChatToken.Chunk -> {
                            state = state.copy(streamingText = state.streamingText + token.text)
                        }
                        is AiChatToken.AnimeCard -> {
                            latestAnimeCard = token.card
                        }
                        is AiChatToken.Done -> {
                            val fullText = token.fullText.ifBlank { state.streamingText }
                            val assistantMsg = AiChatUiMessage(
                                id = assistantMsgId,
                                role = "assistant",
                                content = fullText,
                                anime = token.anime ?: latestAnimeCard,
                            )
                            state = state.copy(
                                messages = state.messages + assistantMsg,
                                streaming = false,
                                streamingText = "",
                            )
                            // Save to settings store on completed streaming response
                            settingsStore.setAiChatHistory(state.messages)
                        }
                    }
                }
            } catch (e: Exception) {
                state = state.copy(
                    streaming = false,
                    streamingText = "",
                    error = e.message ?: "Failed to get AI response",
                )
            }
        }
    }

    fun clearError() {
        state = state.copy(error = null)
    }

    fun clearHistory() {
        streamJob?.cancel()
        state = AiChatUiState()
        viewModelScope.launch {
            settingsStore.clearAiChatHistory()
        }
    }

    override fun onCleared() {
        super.onCleared()
        streamJob?.cancel()
    }
}

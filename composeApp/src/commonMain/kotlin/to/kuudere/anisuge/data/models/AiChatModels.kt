package to.kuudere.anisuge.data.models

import kotlinx.serialization.Serializable

@Serializable
data class AiChatMessageRequest(
    val role: String, // "user" or "assistant"
    val content: String,
)

@Serializable
data class AiChatSendRequest(
    val message: String,
    val history: List<AiChatMessageRequest> = emptyList(),
)

@Serializable
data class AiChatQuotaResponse(
    val used: Int,
    val limit: Int,
    val remaining: Int,
    val isPremium: Boolean,
    val resetAt: String,
)

@Serializable
data class AiChatErrorResponse(
    val error: String,
)

@Serializable
data class AiChatAnimeCard(
    val animeId: String,
    val title: String,
    val description: String? = null,
    val imageUrl: String? = null,
    val format: String? = null,
    val year: Int? = null,
    val score: Int? = null,
    val episodes: Int? = null,
)

@Serializable
data class AiChatUiMessage(
    val id: String,
    val role: String, // "user" | "assistant"
    val content: String,
    val anime: AiChatAnimeCard? = null,
    val isStreaming: Boolean = false,
)

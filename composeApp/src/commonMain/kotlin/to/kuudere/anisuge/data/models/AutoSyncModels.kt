package to.kuudere.anisuge.data.models

import kotlinx.serialization.Serializable

@Serializable
enum class TrackerSyncAction { UPSERT, DELETE }

@Serializable
data class TrackerSyncJob(
    val key: String,
    val action: TrackerSyncAction,
    val animeId: String,
    val malId: Int? = null,
    val anilistId: Int? = null,
    val status: String? = null,
    val progress: Int? = null,
    val totalEpisodes: Int? = null,
    val startedAt: String? = null,
    val completedAt: String? = null,
    val pendingMal: Boolean = false,
    val pendingAnilist: Boolean = false,
    val updatedAt: Long,
    val malError: String? = null,
    val anilistError: String? = null,
)

data class AutoSyncStatus(
    val enabled: Boolean = false,
    val pendingCount: Int = 0,
    val lastSuccessAt: Long? = null,
    val malError: String? = null,
    val anilistError: String? = null,
)

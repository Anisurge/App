package to.kuudere.anisuge.data.models

import kotlinx.serialization.Serializable

/**
 * Stable identifier for a configurable Home Screen row.
 *
 * The [storageId] is the canonical literal persisted in DataStore JSON and used
 * by any future BFF settings sync. Compared case-sensitively per Req 1.1.
 */
@Serializable
enum class RowId(val storageId: String) {
    CONTINUE_WATCHING("continue_watching"),
    LATEST_EPISODES("latest_episodes"),
    TRENDING_WEEK("trending_week"),
    NEW_SEASONS("new_seasons"),
    NEW_ON_APP("new_on_app"),
    RECOMMENDED("recommended"),
    UPCOMING("upcoming"),
    HIDDEN_GEMS("hidden_gems");

    companion object {
        /** Case-sensitive lookup. Returns null for unknown ids. (Req 1.1, 1.3) */
        fun fromStorageId(id: String): RowId? = entries.firstOrNull { it.storageId == id }

        /** TV-supported subset. (Req 9.1, 9.2) */
        val TV_SUPPORTED: Set<RowId> = setOf(CONTINUE_WATCHING, LATEST_EPISODES)
    }
}

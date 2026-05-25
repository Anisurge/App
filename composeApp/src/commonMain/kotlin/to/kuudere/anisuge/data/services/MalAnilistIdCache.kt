package to.kuudere.anisuge.data.services

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Persistent cache: MAL numeric id ↔ AniList media id (from public idMal lookup).
 */
class MalAnilistIdCache(private val dataStore: DataStore<Preferences>) {
    companion object {
        private val MAL_TO_ANILIST_KEY = stringPreferencesKey("mal_anilist_id_cache_v1")
        private val ANILIST_TO_MAL_KEY = stringPreferencesKey("anilist_mal_id_cache_v1")
        private val json = Json { ignoreUnknownKeys = true }
    }

    suspend fun getAll(): Map<Int, Int> {
        val raw = dataStore.data.map { it[MAL_TO_ANILIST_KEY] }.first() ?: return emptyMap()
        return parseMap(raw)
    }

    suspend fun getMalForAnilist(anilistId: Int): Int? {
        if (anilistId <= 0) return null
        val raw = dataStore.data.map { it[ANILIST_TO_MAL_KEY] }.first() ?: return null
        return parseMap(raw)[anilistId]
    }

    suspend fun put(malId: Int, anilistId: Int) {
        if (malId <= 0 || anilistId <= 0) return
        dataStore.edit { prefs ->
            val malToAni = parseMap(prefs[MAL_TO_ANILIST_KEY]) + (malId to anilistId)
            val aniToMal = parseMap(prefs[ANILIST_TO_MAL_KEY]) + (anilistId to malId)
            prefs[MAL_TO_ANILIST_KEY] = json.encodeToString(malToAni.mapKeys { it.key.toString() })
            prefs[ANILIST_TO_MAL_KEY] = json.encodeToString(aniToMal.mapKeys { it.key.toString() })
        }
    }

    suspend fun putAll(mappings: Map<Int, Int>) {
        if (mappings.isEmpty()) return
        dataStore.edit { prefs ->
            val malToAni = parseMap(prefs[MAL_TO_ANILIST_KEY]) + mappings
            val aniToMal = parseMap(prefs[ANILIST_TO_MAL_KEY]) + mappings.map { it.value to it.key }
            prefs[MAL_TO_ANILIST_KEY] = json.encodeToString(malToAni.mapKeys { it.key.toString() })
            prefs[ANILIST_TO_MAL_KEY] = json.encodeToString(aniToMal.mapKeys { it.key.toString() })
        }
    }

    private fun parseMap(raw: String?): Map<Int, Int> {
        if (raw.isNullOrBlank()) return emptyMap()
        return try {
            json.decodeFromString<Map<String, Int>>(raw)
                .mapNotNull { (k, v) ->
                    val key = k.toIntOrNull() ?: return@mapNotNull null
                    if (key > 0 && v > 0) key to v else null
                }
                .toMap()
        } catch (_: Exception) {
            emptyMap()
        }
    }
}

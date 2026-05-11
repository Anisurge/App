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
 * Persistent cache: MAL numeric id → AniList media id (from public idMal lookup).
 */
class MalAnilistIdCache(private val dataStore: DataStore<Preferences>) {
    companion object {
        private val MAP_JSON_KEY = stringPreferencesKey("mal_anilist_id_cache_v1")
        private val json = Json { ignoreUnknownKeys = true }
    }

    suspend fun getAll(): Map<Int, Int> {
        val raw = dataStore.data.map { it[MAP_JSON_KEY] }.first() ?: return emptyMap()
        return try {
            json.decodeFromString<Map<String, Int>>(raw)
                .mapKeys { it.key.toIntOrNull() ?: 0 }
                .filterKeys { it > 0 }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    suspend fun put(malId: Int, anilistId: Int) {
        if (malId <= 0 || anilistId <= 0) return
        dataStore.edit { prefs ->
            val cur = parseMap(prefs[MAP_JSON_KEY])
            val next = cur + (malId to anilistId)
            prefs[MAP_JSON_KEY] = json.encodeToString(next.mapKeys { it.key.toString() })
        }
    }

    private fun parseMap(raw: String?): Map<Int, Int> {
        if (raw.isNullOrBlank()) return emptyMap()
        return try {
            json.decodeFromString<Map<String, Int>>(raw)
                .mapKeys { it.key.toIntOrNull() ?: 0 }
                .filterKeys { it > 0 }
        } catch (_: Exception) {
            emptyMap()
        }
    }
}

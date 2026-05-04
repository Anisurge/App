package to.kuudere.anisuge.data.services

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.flow.Flow
import to.kuudere.anisuge.data.models.SessionInfo

class SessionStore(private val dataStore: DataStore<Preferences>) {
    companion object {
        private val SESSION_KEY = stringPreferencesKey("session_info")
    }

    val sessionFlow: Flow<SessionInfo?> = dataStore.data
        .map { prefs ->
            prefs[SESSION_KEY]?.let { Json.decodeFromString(it) }
        }

    suspend fun save(session: SessionInfo) {
        dataStore.edit { prefs ->
            prefs[SESSION_KEY] = Json.encodeToString(session)
        }
    }

    suspend fun get(): SessionInfo? {
        return dataStore.data
            .map { it[SESSION_KEY] }
            .firstOrNull()
            ?.let { Json.decodeFromString(it) }
    }

    suspend fun clear() {
        dataStore.edit { it.remove(SESSION_KEY) }
    }

    fun isExpired(session: SessionInfo): Boolean {
        if (session.expire.isBlank()) return false
        return try {
            val expire = kotlinx.datetime.Instant.parse(session.expire)
            val now    = kotlinx.datetime.Clock.System.now()
            now > expire
        } catch (e: Exception) {
            println("[SessionStore] parse error for expire date '${session.expire}': ${e.message}")
            false
        }
    }
}

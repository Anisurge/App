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
        private const val TOKEN_PREFIX = "project_r_"
        private val json = Json { ignoreUnknownKeys = true }
    }

    val sessionFlow: Flow<SessionInfo?> = dataStore.data
        .map { prefs ->
            prefs[SESSION_KEY]?.let {
                try { json.decodeFromString<SessionInfo>(it) } catch (_: Exception) { null }
            }
        }

    suspend fun save(session: SessionInfo) {
        dataStore.edit { prefs ->
            prefs[SESSION_KEY] = json.encodeToString(session)
        }
    }

    suspend fun get(): SessionInfo? {
        return dataStore.data
            .map { it[SESSION_KEY] }
            .firstOrNull()
            ?.let {
                try { json.decodeFromString<SessionInfo>(it) } catch (_: Exception) { null }
            }
    }

    suspend fun clear() {
        dataStore.edit { it.remove(SESSION_KEY) }
    }

    fun isValid(session: SessionInfo): Boolean {
        return session.token.isNotBlank() && session.token.startsWith(TOKEN_PREFIX)
    }
}

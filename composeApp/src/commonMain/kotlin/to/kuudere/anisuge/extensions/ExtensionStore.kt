package to.kuudere.anisuge.extensions

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ExtensionStore(private val dataStore: DataStore<Preferences>) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val configKey = stringPreferencesKey("extension_config_v1")

    val configFlow: Flow<ExtensionBackupConfig> = dataStore.data.map { preferences ->
        preferences[configKey]?.let {
            runCatching { json.decodeFromString<ExtensionBackupConfig>(it) }.getOrNull()
        } ?: ExtensionBackupConfig()
    }

    suspend fun update(block: (ExtensionBackupConfig) -> ExtensionBackupConfig) {
        dataStore.edit { preferences ->
            val current = preferences[configKey]?.let {
                runCatching { json.decodeFromString<ExtensionBackupConfig>(it) }.getOrNull()
            } ?: ExtensionBackupConfig()
            preferences[configKey] = json.encodeToString(block(current))
        }
    }

    suspend fun restore(config: ExtensionBackupConfig) {
        dataStore.edit { it[configKey] = json.encodeToString(config) }
    }
}

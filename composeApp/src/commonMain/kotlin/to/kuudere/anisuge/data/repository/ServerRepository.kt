package to.kuudere.anisuge.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.isSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import to.kuudere.anisuge.AppComponent
import to.kuudere.anisuge.data.models.FALLBACK_SERVERS
import to.kuudere.anisuge.data.models.ServerInfo
import to.kuudere.anisuge.data.models.expandForSelection
import to.kuudere.anisuge.data.models.orderSelectableServerIds
import to.kuudere.anisuge.data.services.SettingsStore

class ServerRepository(
    private val httpClient: HttpClient,
    private val dataStore: DataStore<Preferences>,
    private val settingsStore: SettingsStore
) {
    companion object {
        private const val CACHE_KEY_SERVERS = "cached_servers"
        private const val CACHE_KEY_TIMESTAMP = "servers_cache_timestamp"
        private const val REFRESH_INTERVAL_MS = 30 * 60 * 1000L
        private const val CACHE_VALIDITY_MS = 7 * 24 * 60 * 60 * 1000L

        /** Default stream `source` order when the user has not saved a custom priority (matches api.md / site catalog). */
        val DEFAULT_STREAM_SOURCE_ORDER = listOf("zen2", "zen", "suzu", "animepahe")

        /**
         * Order for the Settings servers list — same rules as [ServerRepository.getFallbackPriority]
         * so the UI matches playback fallback order (Sub/Dub split for `sub_dub` catalog rows).
         */
        fun sortServersForSettingsDisplay(
            servers: List<ServerInfo>,
            savedPriority: List<String>,
        ): List<ServerInfo> {
            if (servers.isEmpty()) return emptyList()
            val expanded = servers.expandForSelection()
            val order = orderSelectableServerIds(servers, savedPriority, DEFAULT_STREAM_SOURCE_ORDER)
            val orderIndex = order.withIndex().associate { it.value to it.index }
            return expanded.sortedBy { orderIndex[it.id] ?: Int.MAX_VALUE }
        }
    }

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val _servers = MutableStateFlow<List<ServerInfo>>(emptyList())
    val servers: StateFlow<List<ServerInfo>> = _servers.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _userPriority = MutableStateFlow<List<String>>(emptyList())
    val userPriority: StateFlow<List<String>> = _userPriority.asStateFlow()

    init {
        scope.launch {
            settingsStore.serverPriorityFlow.collect { priority ->
                _userPriority.value = priority
            }
        }
        scope.launch {
            loadCachedServers()
            fetchServers()
        }
        startBackgroundRefresh()
    }

    val serverIds: List<String>
        get() = _servers.value.map { it.id }

    fun getServerById(id: String): ServerInfo? {
        return _servers.value.find { it.id.equals(id, ignoreCase = true) }
    }

    fun getFallbackPriority(): List<String> {
        val catalog = _servers.value
        if (catalog.isEmpty()) {
            return orderSelectableServerIds(FALLBACK_SERVERS, emptyList(), DEFAULT_STREAM_SOURCE_ORDER)
        }
        return orderSelectableServerIds(catalog, _userPriority.value, DEFAULT_STREAM_SOURCE_ORDER)
    }

    suspend fun setUserPriority(priority: List<String>) {
        _userPriority.value = priority
        settingsStore.setServerPriority(priority)
    }

    suspend fun resetUserPriority() {
        _userPriority.value = emptyList()
        settingsStore.setServerPriority(emptyList())
    }

    fun getAvailableServers(): List<ServerInfo> {
        val catalog = _servers.value
        val priority = getFallbackPriority()
        val expanded = catalog.expandForSelection()
        return expanded.sortedBy { server ->
            val index = priority.indexOf(server.id)
            if (index == -1) Int.MAX_VALUE else index
        }
    }

    /** Label row for a streaming source id (e.g. `suzu` / `suzu-dub`) in expanded Sub/Dub lists. */
    fun getSelectableServerInfo(id: String): ServerInfo? =
        _servers.value.expandForSelection().find { it.id.equals(id, ignoreCase = true) }

    suspend fun refreshServers(): Boolean = fetchServers()

    private suspend fun fetchServers(): Boolean {
        if (_isLoading.value) return false

        _isLoading.value = true
        return try {
            val response = httpClient.get(AppComponent.STREAMING_SERVERS_CATALOG_URL)
            if (!response.status.isSuccess()) {
                throw IllegalStateException("streaming servers HTTP ${response.status}")
            }
            val list = response.body<List<ServerInfo>>()
            val activeServers = list.filter { it.active }
                .ifEmpty { FALLBACK_SERVERS.filter { it.active } }
            _servers.value = activeServers
            cacheServers(activeServers)
            true
        } catch (e: Exception) {
            if (_servers.value.isEmpty()) {
                _servers.value = FALLBACK_SERVERS
            }
            false
        } finally {
            _isLoading.value = false
        }
    }

    private suspend fun loadCachedServers() {
        try {
            val preferences = dataStore.data.first()
            val cachedJson = preferences[stringPreferencesKey(CACHE_KEY_SERVERS)]
            val cacheTimestamp = preferences[longPreferencesKey(CACHE_KEY_TIMESTAMP)] ?: 0L
            val now = System.currentTimeMillis()

            if (cachedJson != null && (now - cacheTimestamp) < CACHE_VALIDITY_MS) {
                val cachedServers = json.decodeFromString<List<ServerInfo>>(cachedJson)
                if (cachedServers.isNotEmpty()) {
                    _servers.value = cachedServers
                }
            } else {
                _servers.value = FALLBACK_SERVERS
            }
        } catch (e: Exception) {
            _servers.value = FALLBACK_SERVERS
        }
    }

    private suspend fun cacheServers(servers: List<ServerInfo>) {
        try {
            dataStore.edit { preferences ->
                preferences[stringPreferencesKey(CACHE_KEY_SERVERS)] = json.encodeToString(servers)
                preferences[longPreferencesKey(CACHE_KEY_TIMESTAMP)] = System.currentTimeMillis()
            }
        } catch (e: Exception) {
            // Silently fail caching
        }
    }

    private fun startBackgroundRefresh() {
        scope.launch {
            while (isActive) {
                delay(REFRESH_INTERVAL_MS)
                fetchServers()
            }
        }
    }
}

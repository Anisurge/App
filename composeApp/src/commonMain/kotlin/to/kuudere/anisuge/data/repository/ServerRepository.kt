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
import to.kuudere.anisuge.data.models.collapseToBaseServerPriority
import to.kuudere.anisuge.data.models.expandForSelection
import to.kuudere.anisuge.data.models.excludingHidden
import to.kuudere.anisuge.data.models.excludingUnsupportedPlatformServers
import to.kuudere.anisuge.data.models.hidesServer
import to.kuudere.anisuge.data.models.orderSelectableServerIds
import to.kuudere.anisuge.data.services.SettingsStore
import to.kuudere.anisuge.extensions.ExtensionManager
import to.kuudere.anisuge.extensions.isDubSelectableServerId

class ServerRepository(
    private val httpClient: HttpClient,
    private val dataStore: DataStore<Preferences>,
    private val settingsStore: SettingsStore,
    private val extensionManager: ExtensionManager,
) {
    companion object {
        private const val CACHE_KEY_SERVERS = "cached_servers"
        private const val CACHE_KEY_TIMESTAMP = "servers_cache_timestamp"
        private const val REFRESH_INTERVAL_MS = 30 * 60 * 1000L
        private const val CACHE_VALIDITY_MS = 7 * 24 * 60 * 60 * 1000L

        /** Default stream `source` order when the user has not saved a custom priority (matches api.md / site catalog). */
        val DEFAULT_STREAM_SOURCE_ORDER = listOf("anikoto", "megaplay", "flix-if", "flix", "zen2", "zen", "allmanga", "suzu", "comti", "oush")

        /**
         * Order for the Settings servers list — one row per provider (Sub/Dub chosen at playback).
         * Saved priority may contain legacy `-dub` ids; they collapse to the base id for display.
         */
        fun sortServersForSettingsDisplay(
            servers: List<ServerInfo>,
            savedPriority: List<String>,
        ): List<ServerInfo> {
            if (servers.isEmpty()) return emptyList()
            val collapsedPriority = savedPriority.collapseToBaseServerPriority()
            val serverIds = servers.map { it.id }.toSet()
            val order = if (collapsedPriority.isEmpty()) {
                val priority = mutableListOf<String>()
                for (baseId in DEFAULT_STREAM_SOURCE_ORDER) {
                    if (baseId in serverIds) priority.add(baseId)
                }
                for (id in servers.map { it.id }) {
                    if (id !in priority) priority.add(id)
                }
                priority
            } else {
                val result = collapsedPriority.filter { it in serverIds }.toMutableList()
                for (id in servers.map { it.id }) {
                    if (id !in result) result.add(id)
                }
                result
            }
            val orderIndex = order.withIndex().associate { it.value to it.index }
            return servers.sortedBy { orderIndex[it.id] ?: Int.MAX_VALUE }
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
    private val _hiddenServerIds = MutableStateFlow<Set<String>>(emptySet())
    val hiddenServerIds: StateFlow<Set<String>> = _hiddenServerIds.asStateFlow()
    private var apiServers: List<ServerInfo> = emptyList()

    init {
        scope.launch {
            settingsStore.serverPriorityFlow.collect { priority ->
                _userPriority.value = priority
            }
        }
        scope.launch {
            settingsStore.hiddenServerIdsFlow.collect { hidden ->
                _hiddenServerIds.value = hidden
            }
        }
        scope.launch {
            loadCachedServers()
            fetchServers()
        }
        scope.launch {
            extensionManager.installed.collect { publishServers() }
        }
        startBackgroundRefresh()
    }

    private fun publishServers() {
        val localByLowerId = FALLBACK_SERVERS
            .filter { it.active }
            .associateBy { it.id.lowercase() }
        val mergedApiServers = apiServers.map { server ->
            localByLowerId[server.id.lowercase()]?.let { local ->
                server.copy(label = local.label, type = local.type, playerType = local.playerType)
            } ?: server
        }
        val apiIds = mergedApiServers.map { it.id.lowercase() }.toSet()
        val localOnlyServers = localByLowerId.values.filter { it.id.lowercase() !in apiIds }
        _servers.value = mergedApiServers + localOnlyServers + extensionManager.servers()
    }

    val serverIds: List<String>
        get() = _servers.value.map { it.id }

    fun getServerById(id: String): ServerInfo? {
        return _servers.value.find { it.id.equals(id, ignoreCase = true) }
            ?: if (isDubSelectableServerId(id)) {
                val baseId = id.removeSuffix("-dub").removeSuffix(":dub")
                _servers.value.find { it.id.equals(baseId, ignoreCase = true) }
            } else null
    }

    private fun priorityCatalog(): List<ServerInfo> =
        _servers.value
            .excludingUnsupportedPlatformServers()
            .expandForSelection()
            .excludingHidden(_hiddenServerIds.value)

    fun getFallbackPriority(): List<String> {
        val hidden = _hiddenServerIds.value
        val catalog = _servers.value.excludingUnsupportedPlatformServers()
        val ordered = if (catalog.isEmpty()) {
            orderSelectableServerIds(
                FALLBACK_SERVERS.excludingUnsupportedPlatformServers(),
                emptyList(),
                DEFAULT_STREAM_SOURCE_ORDER,
            )
        } else {
            orderSelectableServerIds(catalog, _userPriority.value, DEFAULT_STREAM_SOURCE_ORDER)
        }
        return ordered.filterNot { hidden.hidesServer(it) }
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
        val catalog = priorityCatalog()
        val priority = getFallbackPriority()
        return catalog.sortedBy { server ->
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
            apiServers = activeServers
            publishServers()
            cacheServers(activeServers)
            true
        } catch (e: Exception) {
            if (apiServers.isEmpty()) {
                apiServers = FALLBACK_SERVERS
                publishServers()
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
            val now = to.kuudere.anisuge.utils.currentTimeMillis()

            if (cachedJson != null && (now - cacheTimestamp) < CACHE_VALIDITY_MS) {
                val cachedServers = json.decodeFromString<List<ServerInfo>>(cachedJson)
                if (cachedServers.isNotEmpty()) {
                    apiServers = cachedServers.filterNot { it.id.startsWith("ext:") }
                    publishServers()
                }
            } else {
                apiServers = FALLBACK_SERVERS
                publishServers()
            }
        } catch (e: Exception) {
            apiServers = FALLBACK_SERVERS
            publishServers()
        }
    }

    private suspend fun cacheServers(servers: List<ServerInfo>) {
        try {
            dataStore.edit { preferences ->
                preferences[stringPreferencesKey(CACHE_KEY_SERVERS)] =
                    json.encodeToString(servers.filterNot { it.id.startsWith("ext:") })
                preferences[longPreferencesKey(CACHE_KEY_TIMESTAMP)] = to.kuudere.anisuge.utils.currentTimeMillis()
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

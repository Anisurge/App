package to.kuudere.anisuge.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import to.kuudere.anisuge.data.models.FALLBACK_SERVERS
import to.kuudere.anisuge.data.models.ServerInfo
import to.kuudere.anisuge.data.models.ServersResponse
import to.kuudere.anisuge.data.services.SettingsStore

/**
 * Repository for managing streaming servers.
 * Fetches servers from API, caches them locally, and provides fallback.
 * Also manages user-defined server priority.
 */
class ServerRepository(
    private val httpClient: HttpClient,
    private val dataStore: DataStore<Preferences>,
    private val settingsStore: SettingsStore
) {
    companion object {
        private const val BASE_URL = "https://api.reanime.to"
        private const val CACHE_KEY_SERVERS = "cached_servers"
        private const val CACHE_KEY_TIMESTAMP = "servers_cache_timestamp"
        private const val REFRESH_INTERVAL_MS = 30 * 60 * 1000L // 30 minutes
        private const val CACHE_VALIDITY_MS = 7 * 24 * 60 * 60 * 1000L // 7 days
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

    init {
        // Load cached servers immediately
        scope.launch {
            loadCachedServers()
            // Fetch fresh servers on launch
            fetchServers()
        }

        // Start background refresh
        startBackgroundRefresh()
    }

    /**
     * Returns the list of server IDs (e.g., "zen2", "zen", "hiya", "hiya-dub")
     * This matches the format used in the existing UI code
     */
    val serverIds: List<String>
        get() = _servers.value.map { it.id }

    /**
     * Get server info by ID
     */
    fun getServerById(id: String): ServerInfo? {
        return _servers.value.find { it.id.equals(id, ignoreCase = true) }
    }

    /**
     * User-defined server priority (empty = use default)
     */
    private val _userPriority = MutableStateFlow<List<String>>(emptyList())
    val userPriority: StateFlow<List<String>> = _userPriority.asStateFlow()

    init {
        // Load user priority from settings
        scope.launch {
            settingsStore.serverPriorityFlow.collect { priority ->
                _userPriority.value = priority
                println("[ServerRepository] Loaded user priority: $priority")
            }
        }

        // Load cached servers immediately
        scope.launch {
            loadCachedServers()
            // Fetch fresh servers on launch
            fetchServers()
        }

        // Start background refresh
        startBackgroundRefresh()
    }

    /**
     * Get the fallback priority list for auto-selecting servers.
     * Uses user-defined priority if set, otherwise uses default (zen2 > zen > others).
     * Filters out servers that no longer exist in the available servers list.
     */
    fun getFallbackPriority(): List<String> {
        val currentServers = _servers.value.map { it.id }
        if (currentServers.isEmpty()) {
            return FALLBACK_SERVERS.map { it.id }
        }

        val userPriorityList = _userPriority.value

        return if (userPriorityList.isNotEmpty()) {
            // Use user's priority, but only include servers that still exist
            val filtered = userPriorityList.filter { it in currentServers }
            // Add any new servers not in user's list at the end
            val newServers = currentServers.filter { it !in userPriorityList }
            filtered + newServers
        } else {
            // Default priority order: animepahe > suzu > animekai > others
            val priority = mutableListOf<String>()
            if (currentServers.contains("animepahe")) priority.add("animepahe")
            if (currentServers.contains("suzu")) priority.add("suzu")
            if (currentServers.contains("animekai")) priority.add("animekai")
            priority.addAll(currentServers.filter { it != "animepahe" && it != "suzu" && it != "animekai" })
            priority
        }
    }

    /**
     * Update user-defined server priority
     */
    suspend fun setUserPriority(priority: List<String>) {
        _userPriority.value = priority
        settingsStore.setServerPriority(priority)
        println("[ServerRepository] Saved user priority: $priority")
    }

    /**
     * Reset user priority to default (empty = use default logic)
     */
    suspend fun resetUserPriority() {
        _userPriority.value = emptyList()
        settingsStore.setServerPriority(emptyList())
        println("[ServerRepository] Reset user priority to default")
    }

    /**
     * Get all available servers sorted by priority
     */
    fun getAvailableServers(): List<ServerInfo> {
        val priority = getFallbackPriority()
        return _servers.value.sortedBy { server ->
            val index = priority.indexOf(server.id)
            if (index == -1) Int.MAX_VALUE else index
        }
    }

    /**
     * Manually trigger a server refresh
     */
    suspend fun refreshServers(): Boolean {
        return fetchServers()
    }

    /**
     * Fetch servers from API and update cache
     */
    private suspend fun fetchServers(): Boolean {
        if (_isLoading.value) return false

        _isLoading.value = true
        return try {
            val response = httpClient.get("$BASE_URL/api/v1/servers")
            val serversResponse: ServersResponse = response.body()

            if (serversResponse.servers.isNotEmpty()) {
                _servers.value = serversResponse.servers
                cacheServers(serversResponse.servers)
                println("[ServerRepository] Fetched ${serversResponse.servers.size} servers from API")
                true
            } else {
                println("[ServerRepository] API returned empty server list")
                // Keep existing servers if API returns empty
                if (_servers.value.isEmpty()) {
                    _servers.value = FALLBACK_SERVERS
                }
                false
            }
        } catch (e: Exception) {
            println("[ServerRepository] Failed to fetch servers: ${e.message}")
            // Use fallback if no servers loaded
            if (_servers.value.isEmpty()) {
                _servers.value = FALLBACK_SERVERS
                println("[ServerRepository] Using fallback servers")
            }
            false
        } finally {
            _isLoading.value = false
        }
    }

    /**
     * Load cached servers from DataStore
     */
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
                    println("[ServerRepository] Loaded ${cachedServers.size} servers from cache")
                }
            } else {
                // Use fallback if cache is expired or empty
                _servers.value = FALLBACK_SERVERS
                println("[ServerRepository] Cache expired or empty, using fallback servers")
            }
        } catch (e: Exception) {
            println("[ServerRepository] Failed to load cached servers: ${e.message}")
            _servers.value = FALLBACK_SERVERS
        }
    }

    /**
     * Cache servers to DataStore
     */
    private suspend fun cacheServers(servers: List<ServerInfo>) {
        try {
            dataStore.edit { preferences ->
                preferences[stringPreferencesKey(CACHE_KEY_SERVERS)] = json.encodeToString(servers)
                preferences[longPreferencesKey(CACHE_KEY_TIMESTAMP)] = System.currentTimeMillis()
            }
            println("[ServerRepository] Cached ${servers.size} servers")
        } catch (e: Exception) {
            println("[ServerRepository] Failed to cache servers: ${e.message}")
        }
    }

    /**
     * Start background refresh every 30 minutes
     */
    private fun startBackgroundRefresh() {
        scope.launch {
            while (isActive) {
                delay(REFRESH_INTERVAL_MS)
                println("[ServerRepository] Running background refresh")
                fetchServers()
            }
        }
    }
}

package to.kuudere.anisuge.extensions

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.CompletableDeferred
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import to.kuudere.anisuge.data.models.ServerInfo
import kotlin.math.max

class ExtensionManager(
    private val httpClient: HttpClient,
    private val store: ExtensionStore,
    val runtime: ExtensionRuntime,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val _config = MutableStateFlow(ExtensionBackupConfig())
    val config: StateFlow<ExtensionBackupConfig> = _config.asStateFlow()
    private val _available = MutableStateFlow<List<ExtensionSource>>(emptyList())
    val available: StateFlow<List<ExtensionSource>> = _available.asStateFlow()
    private val _installed = MutableStateFlow<List<ExtensionSource>>(emptyList())
    val installed: StateFlow<List<ExtensionSource>> = _installed.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private val _pendingMatch = MutableStateFlow<PendingExtensionMatch?>(null)
    val pendingMatch: StateFlow<PendingExtensionMatch?> = _pendingMatch.asStateFlow()
    private val pendingChoices = mutableMapOf<String, CompletableDeferred<ExtensionMedia?>>()
    private var bridgeIdsSyncedAt = 0L

    init {
        scope.launch {
            store.configFlow.collect {
                _config.value = it
                refresh()
            }
        }
        scope.launch {
            var wasReady = runtime.state.value.ready
            runtime.state.collect { state ->
                if (state.ready && !wasReady) {
                    republishInstalled()
                }
                wasReady = state.ready
            }
        }
    }

    suspend fun acceptWarning() = store.update { it.copy(acceptedWarning = true) }

    suspend fun addRepository(url: String, engine: ExtensionEngine) {
        require(url.startsWith("https://") || url.startsWith("http://")) { "Enter a valid HTTP repository URL" }
        val response = httpClient.get(url)
        require(response.status.isSuccess()) { "Repository returned HTTP ${response.status.value}" }
        parseRepository(response.bodyAsText(), url, engine)
        store.update { current ->
            if (current.repositories.any { it.url == url && it.engine == engine }) current
            else current.copy(repositories = current.repositories + ExtensionRepository(url, engine))
        }
    }

    suspend fun removeRepository(repository: ExtensionRepository) {
        store.update { current ->
            current.copy(repositories = current.repositories - repository)
        }
    }

    suspend fun addRepositoryAutoDetect(url: String): ExtensionEngine {
        require(url.startsWith("https://") || url.startsWith("http://")) { "Enter a valid HTTP repository URL" }
        val response = httpClient.get(url)
        require(response.status.isSuccess()) { "Repository returned HTTP ${response.status.value}" }
        val body = response.bodyAsText()
        val root = json.parseToJsonElement(body)
        val engine = detectEngine(root, body)
        val sources = parseRepository(body, url, engine)
        require(sources.isNotEmpty()) { "No extensions found in repository" }
        store.update { current ->
            if (current.repositories.any { it.url == url && it.engine == engine }) current
            else current.copy(repositories = current.repositories + ExtensionRepository(url, engine))
        }
        return engine
    }

    suspend fun refresh() {
        if (_busy.value) return
        _busy.value = true
        _error.value = null
        try {
            val config = store.configFlow.first()
            val fetched = config.repositories.flatMap { repo ->
                runCatching {
                    val response = httpClient.get(repo.url)
                    if (!response.status.isSuccess()) emptyList()
                    else parseRepository(response.bodyAsText(), repo.url, repo.engine)
                }.getOrElse { emptyList() }
            }.distinctBy { "${it.engine.name}:${it.id}" }
            val installedById = normalizeInstalledIds(config.installedSourceIds)
            val loaded = if (runtime.state.value.ready) {
                runtime.loadInstalledSources(installedLocalPaths(fetched, installedById))
            } else {
                emptyList()
            }
            publishSources(fetched, loaded, installedById)
        } catch (e: Exception) {
            _error.value = e.message ?: "Extension refresh failed"
        } finally {
            _busy.value = false
        }
    }

    suspend fun install(source: ExtensionSource) {
        _busy.value = true
        _error.value = null
        try {
            if (!runtime.state.value.ready) runtime.install()
            val url = source.downloadUrl ?: error("Extension has no download URL")
            val path = ExtensionPlatformFiles.stagingPathFor(source)
            ExtensionPlatformFiles.ensureParentDirs(path)
            ExtensionPlatformFiles.download(httpClient, url, path)
            val installed = runtime.installExtension(path, source.installUsesAnimeDir())
            if (!installed) {
                println("[ExtensionManager] installSourceInternal returned false for ${source.name}, trying fallback via folder scan")
            }
            val compositeId = compositeSourceId(source)
            store.update { current ->
                current.copy(installedSourceIds = (current.installedSourceIds + compositeId).distinct())
            }
            republishInstalled()
        } catch (e: Exception) {
            _error.value = e.message ?: "Install failed"
            throw e
        } finally {
            _busy.value = false
        }
    }

    suspend fun uninstall(source: ExtensionSource) {
        _busy.value = true
        _error.value = null
        try {
            val pkg = source.uninstallKey()
            ExtensionPlatformFiles.installedApkPathCandidates(source).forEach { path ->
                ExtensionPlatformFiles.delete(path)
            }
            runCatching { runtime.uninstallExtension(pkg, source.isAnime) }
                .onFailure { println("[ExtensionManager] bridge uninstall failed for ${source.name}: ${it.message}") }
            runtime.invalidateInstalledSourcesCache()
            store.update { current ->
                current.copy(
                    installedSourceIds = current.installedSourceIds.filterNot { raw ->
                        installedIdMatchesSource(raw, source)
                    },
                    sourceOrder = current.sourceOrder.filterNot { id ->
                        installedIdMatchesSource(id, source)
                    },
                    mappings = current.mappings.filterNot { installedIdMatchesSource(it.sourceId, source) },
                    episodeCaches = current.episodeCaches.filterNot { installedIdMatchesSource(it.sourceId, source) },
                )
            }
            republishInstalled()
        } catch (e: Exception) {
            _error.value = e.message ?: "Uninstall failed"
            throw e
        } finally {
            _busy.value = false
        }
    }

    private suspend fun republishInstalled() {
        val config = store.configFlow.first()
        val installedById = normalizeInstalledIds(config.installedSourceIds)
        val loaded = if (runtime.state.value.ready) {
            runtime.loadInstalledSources(installedLocalPaths(_available.value, installedById))
        } else {
            emptyList()
        }
        publishSources(_available.value.ifEmpty { fetchedFromRepositories(config) }, loaded, installedById)
    }

    fun servers(): List<ServerInfo> {
        val order = _config.value.sourceOrder
        val bridgeInstalled = _installed.value.filter { it.isBridgeCapableOnPlatform() }
        val grouped = bridgeInstalled.groupBy { variantGroupKey(it) }
        return bridgeInstalled
            .sortedBy { order.indexOf(compositeSourceId(it)).takeIf { index -> index >= 0 } ?: Int.MAX_VALUE }
            .map { source ->
                val siblings = grouped[variantGroupKey(source)].orEmpty()
                val explicitDub = extensionVariantIsExplicitDub(source, siblings)
                ServerInfo(
                    id = source.selectableServerId(dub = explicitDub),
                    label = buildExtensionServerLabel(source, siblings),
                    type = if (explicitDub) "dub" else "sub_dub",
                )
            }
            .distinctBy { it.id }
    }

    fun preferredExtensionServerId(serverPriority: List<String>): String? {
        val bridgeInstalled = _installed.value.filter { it.isBridgeCapableOnPlatform() }
        val grouped = bridgeInstalled.groupBy { variantGroupKey(it) }
        val installedIds = bridgeInstalled.flatMap { source ->
            val siblings = grouped[variantGroupKey(source)].orEmpty()
            val explicitDub = extensionVariantIsExplicitDub(source, siblings)
            if (explicitDub) {
                listOf(source.selectableServerId(dub = true))
            } else {
                listOf(
                    source.selectableServerId(dub = false),
                    source.selectableServerId(dub = true),
                )
            }
        }.toSet()
        return serverPriority.firstOrNull { id ->
            parseExtensionServerId(id) != null && id in installedIds
        } ?: bridgeInstalled.firstOrNull()?.let { source ->
            source.selectableServerId(dub = false)
        }
    }

    fun episodeCacheFor(animeId: String, serverId: String): ExtensionEpisodeCache? {
        val source = findInstalledSource(serverId) ?: return null
        return _config.value.episodeCaches.firstOrNull {
            it.animeId == animeId && it.sourceId == compositeSourceId(source)
        }
    }

    /** Direct search inside a specific installed extension (for standalone Aniyomi-like use). */
    suspend fun searchExtension(sourceId: String, query: String): List<ExtensionMedia> {
        val source = findInstalledSource(sourceId) ?: return emptyList()
        if (!source.isBridgeCapableOnPlatform()) return emptyList()
        return try {
            runtime.search(source, query).distinctBy { it.url }
        } catch (e: Exception) {
            println("[ExtensionManager] direct search failed: ${e.message}")
            emptyList()
        }
    }

    /** Get episodes directly from extension for a media (standalone use). */
    suspend fun getExtensionEpisodes(sourceId: String, media: ExtensionMedia): List<ExtensionEpisode> {
        val source = findInstalledSource(sourceId) ?: return emptyList()
        return try {
            val (_, episodes) = runtime.details(source, media)
            episodes
        } catch (e: Exception) {
            println("[ExtensionManager] get episodes failed: ${e.message}")
            emptyList()
        }
    }

    /** Resolve stream videos directly for a standalone extension episode (no catalog matching). */
    suspend fun getExtensionVideos(sourceId: String, episode: ExtensionEpisode): List<ExtensionVideo> {
        val source = findInstalledSource(sourceId) ?: return emptyList()
        if (!source.isBridgeCapableOnPlatform()) return emptyList()
        return try {
            runtime.videosStream(source, episode)
        } catch (e: Exception) {
            println("[ExtensionManager] direct get videos failed: ${e.message}")
            emptyList()
        }
    }

    suspend fun prefetchMapping(
        animeId: String,
        titles: List<String>,
        synonyms: List<String> = emptyList(),
        serverId: String,
    ): ExtensionPrefetchResult {
        ensureBridgeReady()
        val source = findInstalledSource(serverId) ?: error("Extension is not installed")
        val compositeId = compositeSourceId(source)
        val cached = _config.value.episodeCaches.firstOrNull {
            it.animeId == animeId && it.sourceId == compositeId
        }
        if (cached != null && cached.episodes.isNotEmpty()) {
            return ExtensionPrefetchResult(source, cached.media, cached.episodes, cached.mappedTitle)
        }
        val mapping = ensureMapping(animeId, source, titles, synonyms)
        val (media, episodes) = runtime.details(source, mapping.media)
        require(episodes.isNotEmpty()) { "${source.name} returned no episodes" }
        val cache = ExtensionEpisodeCache(
            animeId = animeId,
            sourceId = compositeId,
            mappedTitle = media.title,
            media = media,
            episodes = episodes,
            updatedAt = Clock.System.now().toEpochMilliseconds(),
        )
        store.update { current ->
            current.copy(
                episodeCaches = current.episodeCaches.filterNot {
                    it.animeId == animeId && it.sourceId == compositeId
                } + cache,
            )
        }
        return ExtensionPrefetchResult(source, media, episodes, media.title)
    }

    suspend fun resolveStreamForEpisode(
        animeId: String,
        titles: List<String>,
        synonyms: List<String> = emptyList(),
        episodeNumber: Int,
        serverId: String,
    ): ExtensionStreamResult = resolveStream(
        animeId = animeId,
        titles = titles,
        synonyms = synonyms,
        episodeNumber = episodeNumber,
        serverId = serverId,
    )

    suspend fun resolveStream(
        animeId: String,
        titles: List<String>,
        episodeNumber: Int,
        serverId: String,
        synonyms: List<String> = emptyList(),
    ): ExtensionStreamResult {
        ensureBridgeReady()
        val parsed = parseExtensionServerId(serverId) ?: error("Invalid extension server")
        val source = findInstalledSource(serverId) ?: error("Extension is not installed")
        require(source.isBridgeCapableOnPlatform()) {
            "${source.name} requires the AnymeX JS runtime (Mangayomi/Sora scripts are not supported on Android yet)"
        }
        if (source.engine == ExtensionEngine.ANIYOMI || source.engine == ExtensionEngine.SORA) {
            require(source.hasNumericBridgeId()) {
                "${source.name} is not loaded in the extension bridge — open Settings → Extensions and refresh"
            }
        }
        val compositeId = compositeSourceId(source)
        val prefetch = prefetchMapping(animeId, titles, synonyms, serverId)
        val episode = findExtensionEpisode(prefetch.episodes, episodeNumber, preferDub = parsed.dub)
            ?: error("Episode $episodeNumber was not found in ${source.name}")
        val videos = runtime.videosStream(source, episode)
        require(videos.isNotEmpty()) { "No playable streams returned by ${source.name}" }
        return ExtensionStreamResult(prefetch.source, prefetch.media, episode, videos)
    }

    suspend fun clearMapping(animeId: String, serverId: String) {
        val source = findInstalledSource(serverId) ?: return
        val compositeId = compositeSourceId(source)
        store.update { current ->
            current.copy(
                mappings = current.mappings.filterNot { it.animeId == animeId && it.sourceId == compositeId },
                episodeCaches = current.episodeCaches.filterNot { it.animeId == animeId && it.sourceId == compositeId },
            )
        }
    }

    private suspend fun ensureBridgeReady() {
        if (!runtime.state.value.ready) error("Install the extension runtime first")
        if (_busy.value) refresh()
        if (_busy.value) {
            busy.first { !it }
        }
        val config = store.configFlow.first()
        val installedById = normalizeInstalledIds(config.installedSourceIds)
        if (installedById.isEmpty()) return
        val needsBridgeIds = _installed.value.any { source ->
            source.isBridgeCapableOnPlatform() &&
                (source.engine == ExtensionEngine.ANIYOMI || source.engine == ExtensionEngine.SORA) &&
                !source.hasNumericBridgeId()
        }
        val stale = to.kuudere.anisuge.utils.currentTimeMillis() - bridgeIdsSyncedAt > 30_000L
        if (!needsBridgeIds && !stale && _installed.value.isNotEmpty()) return
        val loaded = runtime.loadInstalledSources(installedLocalPaths(_available.value, installedById))
        if (_installed.value.isEmpty()) {
            publishSources(_available.value, loaded, installedById)
        } else {
            applyLoadedRuntimeIds(loaded)
        }
        bridgeIdsSyncedAt = to.kuudere.anisuge.utils.currentTimeMillis()
    }

    private fun applyLoadedRuntimeIds(loaded: List<ExtensionSource>) {
        if (loaded.isEmpty()) return
        fun merge(source: ExtensionSource): ExtensionSource {
            val match = findLoadedMatch(source, loaded) ?: return source
            return source.copy(
                runtimeId = match.runtimeId?.takeIf { it.isNotBlank() } ?: source.runtimeId,
                pkgName = match.pkgName?.takeIf { it.isNotBlank() } ?: source.pkgName,
                isAnime = match.isAnime,
                installedPath = match.installedPath?.takeIf { it.isNotBlank() } ?: source.installedPath,
            )
        }
        _available.value = _available.value.map(::merge)
        _installed.value = _installed.value.map(::merge)
    }

    private suspend fun fetchedFromRepositories(config: ExtensionBackupConfig): List<ExtensionSource> =
        config.repositories.flatMap { repo ->
            runCatching {
                val response = httpClient.get(repo.url)
                if (!response.status.isSuccess()) emptyList()
                else parseRepository(response.bodyAsText(), repo.url, repo.engine)
            }.getOrElse { emptyList() }
        }.distinctBy { "${it.engine.name}:${it.id}" }

    private fun normalizeInstalledIds(ids: List<String>): Set<String> = ids.map { raw ->
        if (":" in raw) raw
        else "${ExtensionEngine.ANIYOMI.name}:$raw"
    }.toSet()

    private fun installedIdVariants(source: ExtensionSource): Set<String> {
        val pkg = source.pkgName?.takeIf { it.isNotBlank() } ?: source.id
        return setOf(
            compositeSourceId(source),
            "${source.engine.name}:$pkg",
            pkg,
            source.id,
        )
    }

    private fun installedIdMatchesSource(rawId: String, source: ExtensionSource): Boolean {
        if (rawId in installedIdVariants(source)) return true
        val pkg = source.pkgName?.takeIf { it.isNotBlank() } ?: source.id
        val stem = rawId.substringAfter(':', rawId)
        return stem == pkg || stem == source.id
    }

    private fun isInstalledSource(source: ExtensionSource, installedById: Set<String>): Boolean {
        val composite = compositeSourceId(source)
        if (composite in installedById) return true
        val pkg = source.pkgName?.takeIf { it.isNotBlank() } ?: source.id
        return "${source.engine.name}:$pkg" in installedById ||
            pkg in installedById ||
            installedById.any { it.endsWith(":$pkg") || it == pkg }
    }

    private fun installedLocalPaths(
        catalog: List<ExtensionSource>,
        installedById: Set<String>,
    ): List<String> = catalog
        .filter { isInstalledSource(it, installedById) }
        .mapNotNull { installedPathFor(it, installedById) }

    private fun publishSources(
        fetched: List<ExtensionSource>,
        loaded: List<ExtensionSource>,
        installedById: Set<String>,
    ) {
        val mergedCatalog = fetched.map { source -> mergeCatalogWithLoaded(source, loaded, installedById) }
        val catalogComposites = mergedCatalog.map { compositeSourceId(it) }.toSet()
        val bridgeOnly = loaded.filter { loadedSource ->
            isInstalledSource(loadedSource, installedById) &&
                compositeSourceId(loadedSource) !in catalogComposites &&
                mergedCatalog.none { sourcesEquivalent(it, loadedSource) }
        }.map { loadedSource ->
            val catalogMatch = mergedCatalog.firstOrNull { sourcesEquivalent(it, loadedSource) }
            val installedVersion = loadedSource.version
            val latestVersion = catalogMatch?.latestVersion?.ifBlank { catalogMatch.version }.orEmpty()
            loadedSource.copy(
                installed = true,
                installedPath = loadedSource.installedPath ?: installedPathFor(loadedSource),
                latestVersion = latestVersion.ifBlank { loadedSource.latestVersion },
                hasUpdate = catalogMatch != null &&
                    latestVersion.isNotBlank() &&
                    installedVersion.isNotBlank() &&
                    compareVersions(installedVersion, latestVersion) < 0,
            )
        }
        _available.value = (mergedCatalog + bridgeOnly).distinctBy { compositeSourceId(it) }
        _installed.value = _available.value.filter { it.installed }
    }

    private fun mergeCatalogWithLoaded(
        source: ExtensionSource,
        loaded: List<ExtensionSource>,
        installedById: Set<String>,
    ): ExtensionSource {
        val loadedSource = findLoadedMatch(source, loaded)
        val installed = isInstalledSource(source, installedById)
        val installedVersion = loadedSource?.version?.takeIf { it.isNotBlank() }
            ?: source.version.takeIf { installed }
        val latestVersion = source.latestVersion.ifBlank { source.version }
        return source.copy(
            installed = installed,
            installedPath = loadedSource?.installedPath?.takeIf { it.isNotBlank() }
                ?: installedPathFor(source, installedById),
            runtimeId = loadedSource?.runtimeId?.takeIf { it.isNotBlank() } ?: source.runtimeId,
            pkgName = loadedSource?.pkgName?.takeIf { it.isNotBlank() } ?: source.pkgName,
            isAnime = loadedSource?.isAnime ?: source.isAnime,
            version = installedVersion ?: source.version,
            hasUpdate = installed &&
                loadedSource != null &&
                latestVersion.isNotBlank() &&
                !installedVersion.isNullOrBlank() &&
                compareVersions(installedVersion.orEmpty(), latestVersion) < 0,
        )
    }

    private fun findLoadedMatch(
        source: ExtensionSource,
        loaded: List<ExtensionSource>,
    ): ExtensionSource? {
        loaded.firstOrNull { loadedSource -> sourcesEquivalent(source, loadedSource) }?.let { return it }
        source.runtimeId?.takeIf { it.isNotBlank() }?.let { runtimeId ->
            loaded.firstOrNull { it.runtimeId == runtimeId }?.let { return it }
        }
        val pkg = source.pkgName?.takeIf { it.isNotBlank() }
        if (pkg != null) {
            loaded.firstOrNull {
                it.pkgName == pkg && it.language.equals(source.language, ignoreCase = true)
            }?.let { return it }
            if (source.language.equals("all", ignoreCase = true)) {
                loaded.firstOrNull { it.pkgName == pkg }?.let { return it }
            }
        }
        return null
    }

    private suspend fun ensureMapping(
        animeId: String,
        source: ExtensionSource,
        titles: List<String>,
        synonyms: List<String>,
    ): ExtensionMapping {
        val compositeId = compositeSourceId(source)
        _config.value.mappings.firstOrNull {
            it.animeId == animeId && it.sourceId == compositeId
        }?.let { return it }

        val searchTitles = ExtensionSourceMapper.formatSearchTitles(titles, synonyms)
        require(searchTitles.isNotEmpty()) { "No searchable titles available" }
        val savedTitle = _config.value.mappings.firstOrNull {
            it.animeId == animeId && it.sourceId == compositeId
        }?.media?.title

        val mapped = ExtensionSourceMapper.mapMedia(
            source = source,
            runtime = runtime,
            searchTitles = searchTitles,
            savedTitle = savedTitle,
        ) { candidates, animeTitle ->
            val requestId = "${source.id}:$animeId:${kotlin.random.Random.nextLong()}"
            val deferred = CompletableDeferred<ExtensionMedia?>()
            pendingChoices[requestId] = deferred
            _pendingMatch.value = PendingExtensionMatch(
                requestId = requestId,
                sourceName = source.name,
                animeTitle = animeTitle,
                candidates = candidates,
            )
            try {
                deferred.await()
            } finally {
                pendingChoices.remove(requestId)
                if (_pendingMatch.value?.requestId == requestId) _pendingMatch.value = null
            }
        } ?: error("No title match found in ${source.name}")

        val mapping = ExtensionMapping(animeId, compositeId, mapped.first, mapped.second)
        store.update { current ->
            current.copy(
                mappings = current.mappings.filterNot {
                    it.animeId == animeId && it.sourceId == compositeId
                } + mapping,
            )
        }
        return mapping
    }

    private fun sourcesEquivalent(catalog: ExtensionSource, loaded: ExtensionSource): Boolean {
        if (catalog.engine != loaded.engine &&
            !(catalog.engine == ExtensionEngine.SORA && loaded.engine == ExtensionEngine.ANIYOMI)
        ) {
            return false
        }
        val catalogPkg = catalog.pkgName?.takeIf { it.isNotBlank() } ?: catalog.id.substringBefore(':')
        val loadedPkg = loaded.pkgName?.takeIf { it.isNotBlank() }
        if (loadedPkg != null && (catalogPkg == loadedPkg || catalog.id == loadedPkg)) {
            if (catalog.language != loaded.language &&
                !catalog.language.equals("all", ignoreCase = true) &&
                !loaded.language.equals("all", ignoreCase = true)
            ) {
                return false
            }
            return true
        }
        if (catalog.id == loaded.id ||
            catalog.runtimeId == loaded.runtimeId ||
            catalog.runtimeId == loaded.id ||
            catalog.id == loaded.runtimeId
        ) {
            return true
        }
        val catalogStem = catalog.installedPath?.substringAfterLast("/")?.substringBeforeLast(".")
            ?: catalog.id.substringAfterLast(".")
        if (catalogStem.isNotBlank() && (
                catalogStem == loaded.id ||
                    catalogStem == loaded.pkgName ||
                    catalogStem == loaded.runtimeId
                )
        ) {
            return true
        }
        return catalog.name.equals(loaded.name, ignoreCase = true) ||
            catalog.name.contains(loaded.name, ignoreCase = true) ||
            loaded.name.contains(catalog.name, ignoreCase = true)
    }

    private fun findInstalledSource(serverId: String): ExtensionSource? {
        val parsed = parseExtensionServerId(serverId) ?: return null
        val grouped = _installed.value.groupBy { variantGroupKey(it) }
        val byBridgeId = _installed.value.filter { source ->
            source.engine == parsed.engine && source.bridgeSourceId() == parsed.sourceId
        }
        if (byBridgeId.isNotEmpty()) {
            if (byBridgeId.size == 1) return byBridgeId.first()
            return byBridgeId.firstOrNull { source ->
                val siblings = grouped[variantGroupKey(source)].orEmpty()
                extensionVariantIsExplicitDub(source, siblings) == parsed.dub
            } ?: byBridgeId.first()
        }
        return _installed.value.firstOrNull { source ->
            source.engine == parsed.engine && (
                source.id == parsed.sourceId ||
                    source.pkgName == parsed.sourceId ||
                    source.selectableServerId(dub = false) == serverId ||
                    source.selectableServerId(dub = true) == serverId
                )
        }
    }

    private fun variantGroupKey(source: ExtensionSource): String =
        source.pkgName?.takeIf { it.isNotBlank() } ?: compositeSourceId(source)

    suspend fun setOrder(ids: List<String>) = store.update { it.copy(sourceOrder = ids.distinct()) }
    fun choosePendingMatch(requestId: String, media: ExtensionMedia?) {
        pendingChoices[requestId]?.complete(media)
    }

    suspend fun update(source: ExtensionSource) {
        require(source.installed) { "Extension is not installed" }
        install(source)
    }

    suspend fun updateAllAvailable(): Int {
        val pending = _available.value.filter { it.installed && it.hasUpdate }
        pending.forEach { update(it) }
        return pending.size
    }

    suspend fun repairSource(source: ExtensionSource): String {
        require(source.installed) { "Install the extension first" }
        require(source.isBridgeCapableOnPlatform()) {
            "${source.name} cannot be repaired on this platform"
        }
        runtime.invalidateInstalledSourcesCache()
        val config = store.configFlow.first()
        val installedById = normalizeInstalledIds(config.installedSourceIds)
        val loaded = runtime.loadInstalledSources(installedLocalPaths(_available.value, installedById))
        applyLoadedRuntimeIds(loaded)
        republishInstalled()
        val match = findLoadedMatch(source, loaded) ?: findInstalledSource(source.serverId())
        if (source.engine == ExtensionEngine.ANIYOMI || source.engine == ExtensionEngine.SORA) {
            require(match?.hasNumericBridgeId() == true) {
                "${source.name} failed to reload — try uninstalling and reinstalling"
            }
        }
        return "${source.name} reloaded in bridge"
    }
    suspend fun exportConfig(): ExtensionBackupConfig = store.configFlow.first()
    suspend fun restoreConfig(value: ExtensionBackupConfig) = store.restore(value)

    private fun parseRepository(body: String, repoUrl: String, engine: ExtensionEngine): List<ExtensionSource> {
        val root = json.parseToJsonElement(body)
        return when (engine) {
            ExtensionEngine.ANIYOMI -> parseAniyomi(root, repoUrl)
            ExtensionEngine.CLOUDSTREAM -> parseCloudStream(root, repoUrl)
            ExtensionEngine.MANGAYOMI -> parseMangayomi(root, repoUrl)
            ExtensionEngine.SORA -> parseSora(root, repoUrl)
        }
    }

    private fun parseAniyomi(root: JsonElement, repoUrl: String): List<ExtensionSource> {
        val array = root as? JsonArray ?: root.jsonObject["extensions"] as? JsonArray ?: JsonArray(emptyList())
        val base = repoUrl.substringBeforeLast("/")
        return array.mapNotNull { element ->
            val o = element as? JsonObject ?: return@mapNotNull null
            val pkg = o.string("pkg") ?: o.string("pkgName") ?: return@mapNotNull null
            val lang = o.string("lang") ?: "all"
            val name = o.string("name")?.removePrefix("Aniyomi: ") ?: pkg.substringAfterLast(".")
            val apk = o.string("apk") ?: "$pkg.apk"
            val runtimeId = (o["sources"] as? JsonArray)
                ?.firstOrNull()
                ?.let { it as? JsonObject }
                ?.string("id")
            val catalogId = runtimeId?.takeIf { it.isNotBlank() }
                ?: if (lang != "all") "$pkg:$lang" else pkg
            ExtensionSource(
                id = catalogId,
                name = name,
                engine = ExtensionEngine.ANIYOMI,
                language = lang,
                version = o.string("version") ?: o.string("versionName") ?: "",
                latestVersion = o.string("version") ?: o.string("versionName") ?: "",
                iconUrl = o.string("icon") ?: "$base/icon/$pkg.png",
                downloadUrl = if (apk.startsWith("http")) apk else "$base/apk/$apk",
                repositoryUrl = repoUrl,
                runtimeId = runtimeId ?: pkg,
                pkgName = pkg,
                isAnime = true,
                isNsfw = o.bool("nsfw"),
            )
        }.filter { source ->
            val pkg = source.pkgName ?: source.id
            val type = (array.firstOrNull { (it as? JsonObject)?.string("pkg") == pkg } as? JsonObject)?.string("type")
            type == null || type.contains("anime", true)
        }
    }

    private fun parseCloudStream(root: JsonElement, repoUrl: String): List<ExtensionSource> {
        val array = when (root) {
            is JsonArray -> root
            is JsonObject -> root["plugins"] as? JsonArray ?: root["pluginLists"] as? JsonArray ?: JsonArray(emptyList())
            else -> JsonArray(emptyList())
        }
        return array.mapNotNull { element ->
            val o = element as? JsonObject ?: return@mapNotNull null
            val url = o.string("url") ?: o.string("downloadUrl") ?: return@mapNotNull null
            val name = o.string("name") ?: o.string("internalName") ?: return@mapNotNull null
            ExtensionSource(
                id = o.string("internalName") ?: name,
                name = name,
                engine = ExtensionEngine.CLOUDSTREAM,
                language = o.string("language") ?: "all",
                version = o.string("version") ?: o.int("version").toString(),
                latestVersion = o.string("version") ?: o.int("version").toString(),
                iconUrl = o.string("iconUrl"),
                downloadUrl = url,
                repositoryUrl = repoUrl,
            )
        }
    }

    private fun parseMangayomi(root: JsonElement, repoUrl: String): List<ExtensionSource> =
        genericJsSources(root, repoUrl, ExtensionEngine.MANGAYOMI)

    private fun parseSora(root: JsonElement, repoUrl: String): List<ExtensionSource> =
        genericJsSources(root, repoUrl, ExtensionEngine.SORA)

    private fun genericJsSources(root: JsonElement, repoUrl: String, engine: ExtensionEngine): List<ExtensionSource> {
        val array = when {
            root is JsonArray -> root
            root is JsonObject && root["sourceName"] != null -> JsonArray(listOf(root))
            else -> (root as? JsonObject)?.get("sources") as? JsonArray
                ?: (root as? JsonObject)?.get("extensions") as? JsonArray
                ?: JsonArray(emptyList())
        }
        return array.mapNotNull { element ->
            val o = element as? JsonObject ?: return@mapNotNull null
            val name = o.string("name") ?: o.string("sourceName") ?: return@mapNotNull null
            ExtensionSource(
                id = o.string("id") ?: "$name@$repoUrl",
                name = name,
                engine = engine,
                language = o.string("lang") ?: o.string("language") ?: "all",
                version = o.string("version") ?: "",
                latestVersion = o.string("version") ?: "",
                iconUrl = o.string("iconUrl") ?: o.string("icon"),
                downloadUrl = o.string("url") ?: o.string("scriptUrl") ?: o.string("sourceUrl") ?: o.string("downloadUrl") ?: o.string("sourceCodeUrl"),
                repositoryUrl = repoUrl,
                runtimeId = o.string("id"),
                isAnime = engine != ExtensionEngine.MANGAYOMI &&
                    (o.string("type")?.contains("anime", true) != false),
            )
        }
    }

    private fun installedPathFor(
        source: ExtensionSource,
        installedById: Set<String> = normalizeInstalledIds(_config.value.installedSourceIds),
    ): String? = if (isInstalledSource(source, installedById)) extensionDownloadPath(source) else null

    private fun detectEngine(root: JsonElement, body: String): ExtensionEngine {
        if (root is JsonObject && root["sourceName"] != null &&
            (root["scriptUrl"] != null || root["sourceUrl"] != null)
        ) return ExtensionEngine.SORA
        val items = when {
            root is JsonArray -> root
            root is JsonObject -> root["extensions"] as? JsonArray
                ?: root["plugins"] as? JsonArray
                ?: root["pluginLists"] as? JsonArray
                ?: root["sources"] as? JsonArray
            else -> null
        } ?: return ExtensionEngine.ANIYOMI
        val hasPkg = items.any { (it as? JsonObject)?.get("pkg") != null || (it as? JsonObject)?.get("pkgName") != null }
        val hasName = items.any { (it as? JsonObject)?.get("name") != null || (it as? JsonObject)?.get("sourceName") != null }
        val hasSourceUrl = items.any { (it as? JsonObject)?.get("sourceUrl") != null }
        if (hasPkg) return ExtensionEngine.ANIYOMI
        if (hasSourceUrl) return ExtensionEngine.MANGAYOMI
        val hasLang = items.any { (it as? JsonObject)?.get("lang") != null }
        val hasUrl = items.any { (it as? JsonObject)?.get("url") != null || (it as? JsonObject)?.get("downloadUrl") != null }
        if (hasUrl && hasName && !hasLang) return ExtensionEngine.CLOUDSTREAM
        if (hasName) return ExtensionEngine.MANGAYOMI
        return ExtensionEngine.ANIYOMI
    }

    private fun extensionDownloadPath(source: ExtensionSource): String =
        ExtensionPlatformFiles.stagingPathFor(source)

    private fun safe(value: String) = value.replace(Regex("[^A-Za-z0-9._-]"), "_")

    private fun compareVersions(a: String, b: String): Int {
        val aa = a.split(".").map { it.toIntOrNull() ?: 0 }
        val bb = b.split(".").map { it.toIntOrNull() ?: 0 }
        return (0 until max(aa.size, bb.size)).firstNotNullOfOrNull { index ->
            (aa.getOrElse(index) { 0 } - bb.getOrElse(index) { 0 }).takeIf { it != 0 }
        } ?: 0
    }
}

private fun JsonObject.string(key: String) = this[key]?.jsonPrimitive?.run {
    contentOrNull ?: content
}
private fun JsonObject.bool(key: String) = this[key]?.jsonPrimitive?.booleanOrNull ?: false
private fun JsonObject.int(key: String) = this[key]?.jsonPrimitive?.intOrNull ?: 0

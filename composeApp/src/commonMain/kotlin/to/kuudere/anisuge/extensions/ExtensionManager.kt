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

    init {
        scope.launch {
            store.configFlow.collect {
                _config.value = it
                refresh()
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
            val installedById = config.installedSourceIds.toSet()
            val localPaths = fetched.filter { compositeSourceId(it) in installedById }
                .mapNotNull { installedPathFor(it) }
            val loaded = if (runtime.state.value.ready) runtime.loadInstalledSources(localPaths) else emptyList()
            _available.value = fetched.map { source ->
                val loadedSource = loaded.firstOrNull {
                    it.engine == source.engine && (
                        it.id == source.id ||
                            it.name.equals(source.name, ignoreCase = true) ||
                            it.installedPath == installedPathFor(source)
                        )
                }
                source.copy(
                    installed = compositeSourceId(source) in installedById,
                    installedPath = installedPathFor(source),
                    runtimeId = loadedSource?.runtimeId ?: source.runtimeId,
                    hasUpdate = loadedSource != null && compareVersions(source.version, source.latestVersion) < 0,
                )
            }
            _installed.value = _available.value.filter { it.installed }
        } catch (e: Exception) {
            _error.value = e.message ?: "Extension refresh failed"
        } finally {
            _busy.value = false
        }
    }

    suspend fun install(source: ExtensionSource) {
        require(source.engine.nativeRuntime) {
            "${source.engine.displayName} execution needs a Kotlin runtime port and is not available in this build."
        }
        _busy.value = true
        _error.value = null
        try {
            if (!runtime.state.value.ready) runtime.install()
            val url = source.downloadUrl ?: error("Extension has no download URL")
            val path = extensionDownloadPath(source)
            ExtensionPlatformFiles.download(httpClient, url, path)
            val isAnime = source.engine == ExtensionEngine.ANIYOMI
            val installed = runtime.installExtension(path, isAnime)
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
            val isAnime = source.engine == ExtensionEngine.ANIYOMI
            runCatching { runtime.uninstallExtension(source.id, isAnime) }
            installedPathFor(source)?.let { ExtensionPlatformFiles.delete(it) }
            val compositeId = compositeSourceId(source)
            store.update { current ->
                current.copy(
                    installedSourceIds = current.installedSourceIds - compositeId,
                    sourceOrder = current.sourceOrder - compositeId,
                    mappings = current.mappings.filterNot { it.sourceId == compositeId },
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
        val installedById = config.installedSourceIds.toSet()
        val currentAvailable = _available.value
        val localPaths = currentAvailable.filter { compositeSourceId(it) in installedById }
            .mapNotNull { installedPathFor(it) }
        val loaded = if (runtime.state.value.ready) runtime.loadInstalledSources(localPaths) else emptyList()
        _available.value = currentAvailable.map { source ->
            val loadedSource = loaded.firstOrNull {
                it.engine == source.engine && (
                    it.id == source.id ||
                        it.name.equals(source.name, ignoreCase = true) ||
                        it.installedPath == installedPathFor(source)
                    )
            }
            source.copy(
                installed = compositeSourceId(source) in installedById,
                installedPath = installedPathFor(source),
                runtimeId = loadedSource?.runtimeId ?: source.runtimeId,
                hasUpdate = loadedSource != null && compareVersions(source.version, source.latestVersion) < 0,
            )
        }
        _installed.value = _available.value.filter { it.installed }
    }

    fun servers(): List<ServerInfo> {
        val order = _config.value.sourceOrder
        return _installed.value
            .filter { it.engine.nativeRuntime }
            .sortedBy { order.indexOf(compositeSourceId(it)).takeIf { index -> index >= 0 } ?: Int.MAX_VALUE }
            .map {
                ServerInfo(
                    id = it.serverId(),
                    label = "${it.name} · ${it.engine.displayName} · Extension",
                    type = "sub_dub",
                )
            }
    }

    suspend fun resolveStream(
        animeId: String,
        titles: List<String>,
        episodeNumber: Int,
        serverId: String,
    ): ExtensionStreamResult {
        val parsed = parseExtensionServerId(serverId) ?: error("Invalid extension server")
        val source = _installed.value.firstOrNull {
            it.id == parsed.sourceId && it.engine == parsed.engine
        } ?: error("Extension is not installed")
        println("[ExtensionManager] resolveStream: source=${source.name} engine=${source.engine.name} runtimeId=${source.runtimeId} serverId=$serverId")
        var mapping = _config.value.mappings.firstOrNull {
            it.animeId == animeId && it.sourceId == compositeSourceId(source)
        }
        if (mapping == null) {
            println("[ExtensionManager] resolveStream: no cached mapping, searching titles...")
            val candidates = titles.filter { it.isNotBlank() }.flatMap { runtime.search(source, it) }
                .distinctBy { it.url }
                .map { it to matchConfidence(titles, it.title) }
                .sortedByDescending { it.second }
            println("[ExtensionManager] resolveStream: ${candidates.size} candidates, best=${candidates.firstOrNull()?.second}")
            val best = candidates.firstOrNull() ?: error("No title match found in ${source.name}")
            val chosen = if (best.second >= 0.92 || candidates.size == 1) {
                best.first
            } else {
                val requestId = "${source.id}:${animeId}:${kotlin.random.Random.nextLong()}"
                val deferred = CompletableDeferred<ExtensionMedia?>()
                pendingChoices[requestId] = deferred
                _pendingMatch.value = PendingExtensionMatch(
                    requestId = requestId,
                    sourceName = source.name,
                    animeTitle = titles.firstOrNull().orEmpty(),
                    candidates = candidates.take(8),
                )
                try {
                    deferred.await() ?: error("Extension title selection was cancelled")
                } finally {
                    pendingChoices.remove(requestId)
                    if (_pendingMatch.value?.requestId == requestId) _pendingMatch.value = null
                }
            }
            val confidence = candidates.firstOrNull { it.first.url == chosen.url }?.second ?: best.second
            mapping = ExtensionMapping(animeId, compositeSourceId(source), chosen, confidence)
            store.update { it.copy(mappings = it.mappings.filterNot { old -> old.animeId == animeId && old.sourceId == compositeSourceId(source) } + mapping) }
        }
        val (media, episodes) = runtime.details(source, mapping.media)
        println("[ExtensionManager] resolveStream: got ${episodes.size} episodes, looking for ep $episodeNumber")
        val episode = episodes.minByOrNull { kotlin.math.abs(it.number - episodeNumber) }
            ?.takeIf { kotlin.math.abs(it.number - episodeNumber) < 1.5 }
            ?: episodes.firstOrNull { it.name.contains(episodeNumber.toString()) }
            ?: episodes.firstOrNull()
            ?: error("Episode $episodeNumber was not found in ${source.name}")
        println("[ExtensionManager] resolveStream: matched episode=${episode.name} number=${episode.number}")
        val allVideos = runtime.videos(source, episode)
        println("[ExtensionManager] resolveStream: got ${allVideos.size} videos from runtime")
        val videos = allVideos
            .filter { video ->
                val text = "${video.quality} ${video.url}".lowercase()
                if (parsed.dub) "dub" in text || videosHaveNoLanguageMarkers(allVideos)
                else "dub" !in text
            }
            .ifEmpty { allVideos }
        require(videos.isNotEmpty()) { "No playable streams returned by ${source.name}" }
        return ExtensionStreamResult(source, media, episode, videos)
    }

    suspend fun setOrder(ids: List<String>) = store.update { it.copy(sourceOrder = ids.distinct()) }
    fun choosePendingMatch(requestId: String, media: ExtensionMedia?) {
        pendingChoices[requestId]?.complete(media)
    }

    suspend fun testSource(source: ExtensionSource): String {
        require(source.installed) { "Install the extension first" }
        val results = runtime.search(source, "Naruto")
        require(results.isNotEmpty()) { "Search returned no results" }
        val (_, episodes) = runtime.details(source, results.first())
        require(episodes.isNotEmpty()) { "Details returned no episodes" }
        return "Search and episode loading passed"
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
            val name = o.string("name")?.removePrefix("Aniyomi: ") ?: pkg.substringAfterLast(".")
            val apk = o.string("apk") ?: "$pkg.apk"
            val runtimeId = (o["sources"] as? JsonArray)
                ?.firstOrNull()
                ?.let { it as? JsonObject }
                ?.string("id")
            ExtensionSource(
                id = o.string("id") ?: pkg,
                name = name,
                engine = ExtensionEngine.ANIYOMI,
                language = o.string("lang") ?: "all",
                version = o.string("version") ?: o.string("versionName") ?: "",
                latestVersion = o.string("version") ?: o.string("versionName") ?: "",
                iconUrl = o.string("icon") ?: "$base/icon/$pkg.png",
                downloadUrl = if (apk.startsWith("http")) apk else "$base/apk/$apk",
                repositoryUrl = repoUrl,
                runtimeId = runtimeId,
                isNsfw = o.bool("nsfw"),
            )
        }.filter { source ->
            val pkg = source.id
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
        val array = root as? JsonArray
            ?: (root as? JsonObject)?.get("sources") as? JsonArray
            ?: (root as? JsonObject)?.get("extensions") as? JsonArray
            ?: JsonArray(emptyList())
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
                downloadUrl = o.string("url") ?: o.string("sourceUrl"),
                repositoryUrl = repoUrl,
            )
        }
    }

    private fun installedPathFor(source: ExtensionSource): String? =
        if (compositeSourceId(source) in _config.value.installedSourceIds) extensionDownloadPath(source) else null

    private fun compositeSourceId(source: ExtensionSource): String = "${source.engine.name}:${source.id}"

    private fun detectEngine(root: JsonElement, body: String): ExtensionEngine {
        val items = when {
            root is JsonArray -> root
            root is JsonObject -> root["extensions"] as? JsonArray
                ?: root["plugins"] as? JsonArray
                ?: root["pluginLists"] as? JsonArray
                ?: root["sources"] as? JsonArray
            else -> null
        } ?: return ExtensionEngine.ANIYOMI
        val hasPkg = items.any { (it as? JsonObject)?.get("pkg") != null || (it as? JsonObject)?.get("pkgName") != null }
        val hasDownloadUrl = items.any { (it as? JsonObject)?.get("url") != null || (it as? JsonObject)?.get("downloadUrl") != null }
        val hasName = items.any { (it as? JsonObject)?.get("name") != null || (it as? JsonObject)?.get("sourceName") != null }
        if (hasPkg) return ExtensionEngine.ANIYOMI
        if (hasDownloadUrl && hasName && items.none { (it as? JsonObject)?.get("pkg") != null }) return ExtensionEngine.CLOUDSTREAM
        if (hasName) return ExtensionEngine.MANGAYOMI
        return ExtensionEngine.ANIYOMI
    }

    private fun extensionDownloadPath(source: ExtensionSource): String {
        val ext = when (source.engine) {
            ExtensionEngine.ANIYOMI -> if (ExtensionPlatformFiles.isAndroid) "apk" else "jar"
            ExtensionEngine.CLOUDSTREAM -> if (ExtensionPlatformFiles.isAndroid) "cs3" else "jar"
            else -> "js"
        }
        return "${ExtensionPlatformFiles.rootDir()}/extensions/${source.engine.name.lowercase()}/${safe(source.id)}.$ext"
    }

    private fun safe(value: String) = value.replace(Regex("[^A-Za-z0-9._-]"), "_")

    private fun matchConfidence(titles: List<String>, candidate: String): Double {
        val normalizedCandidate = normalize(candidate)
        return titles.maxOfOrNull { title ->
            val normalized = normalize(title)
            when {
                normalized == normalizedCandidate -> 1.0
                normalizedCandidate.contains(normalized) || normalized.contains(normalizedCandidate) -> 0.88
                else -> {
                    val a = normalized.split(" ").toSet()
                    val b = normalizedCandidate.split(" ").toSet()
                    if (a.isEmpty() || b.isEmpty()) 0.0 else a.intersect(b).size.toDouble() / max(a.size, b.size)
                }
            }
        } ?: 0.0
    }

    private fun normalize(value: String) = value.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()
    private fun videosHaveNoLanguageMarkers(videos: List<ExtensionVideo>) =
        videos.none { "${it.quality} ${it.url}".contains("dub", true) }
    private fun compareVersions(a: String, b: String): Int {
        val aa = a.split(".").map { it.toIntOrNull() ?: 0 }
        val bb = b.split(".").map { it.toIntOrNull() ?: 0 }
        return (0 until max(aa.size, bb.size)).firstNotNullOfOrNull { index ->
            (aa.getOrElse(index) { 0 } - bb.getOrElse(index) { 0 }).takeIf { it != 0 }
        } ?: 0
    }
}

private fun JsonObject.string(key: String) = this[key]?.jsonPrimitive?.contentOrNull
private fun JsonObject.bool(key: String) = this[key]?.jsonPrimitive?.booleanOrNull ?: false
private fun JsonObject.int(key: String) = this[key]?.jsonPrimitive?.intOrNull ?: 0

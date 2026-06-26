package to.kuudere.anisuge.extensions

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.ZipInputStream

actual class ExtensionRuntime actual constructor() {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val _state = MutableStateFlow(ExtensionRuntimeState())
    actual val state: StateFlow<ExtensionRuntimeState> = _state.asStateFlow()
    private val pending = ConcurrentHashMap<String, CompletableDeferred<kotlinx.serialization.json.JsonElement>>()
    private val ids = AtomicLong()
    private val logLines = ArrayDeque<String>()
    private var process: Process? = null
    private val runtimeFile get() = File(ExtensionPlatformFiles.rootDir(), "runtime/anymex_desktop_runtime.jar")

    init {
        if (runtimeFile.exists()) runCatching { startRuntime() }.onFailure { fail("Runtime load failed", it) }
    }

    actual suspend fun install(force: Boolean) = withContext(Dispatchers.IO) {
        _state.value = _state.value.copy(busy = true, error = null, status = "Downloading runtime...")
        try {
            if (force || !runtimeFile.exists()) {
                ExtensionPlatformFiles.download(HttpClient(), RUNTIME_URL, runtimeFile.absolutePath)
            }
            startRuntime()
        } catch (e: Exception) {
            fail("Runtime installation failed", e)
            throw e
        } finally {
            _state.value = _state.value.copy(busy = false)
        }
    }

    actual suspend fun remove() = withContext(Dispatchers.IO) {
        runCatching { call("exit") }
        process?.destroyForcibly()
        process = null
        runtimeFile.delete()
        _state.value = ExtensionRuntimeState()
    }

    actual suspend fun installExtension(path: String, isAnime: Boolean): Boolean = withContext(Dispatchers.IO) {
        ensureReady()
        val result = call("installExtension", mapOf("path" to path, "isAnime" to isAnime))
        result as? Boolean ?: false
    }

    actual suspend fun uninstallExtension(packageName: String, isAnime: Boolean): Boolean = withContext(Dispatchers.IO) {
        ensureReady()
        val result = call("uninstallExtension", mapOf("packageName" to packageName, "isAnime" to isAnime))
        result as? Boolean ?: false
    }

    actual suspend fun invalidateInstalledSourcesCache() = Unit

    actual suspend fun loadInstalledSources(paths: List<String>): List<ExtensionSource> {
        ensureReady()
        return paths.groupBy { path ->
            when {
                path.endsWith(".cs3") -> ExtensionEngine.CLOUDSTREAM
                path.contains("/exts_manga/") || path.contains("/mangayomi/") -> ExtensionEngine.MANGAYOMI
                path.contains("/sora/") -> ExtensionEngine.SORA
                else -> ExtensionEngine.ANIYOMI
            }
        }.flatMap { (engine, files) ->
            val folder = File(files.firstOrNull() ?: return@flatMap emptyList()).parent ?: return@flatMap emptyList()
            val data = call(if (engine == ExtensionEngine.CLOUDSTREAM) "csLoadExtensions" else "loadExtensions", mapOf("folderPath" to folder))
            parseDesktopSources(data, engine, files)
        }
    }

    actual suspend fun search(source: ExtensionSource, query: String): List<ExtensionMedia> {
        val method = if (source.engine == ExtensionEngine.CLOUDSTREAM) "csSearch" else "search"
        return parseDesktopMedia(call(method, mapOf("sourceId" to source.bridgeSourceId(), "query" to query, "page" to 1, "isAnime" to source.isAnime)))
    }

    actual suspend fun details(
        source: ExtensionSource,
        media: ExtensionMedia,
    ): Pair<ExtensionMedia, List<ExtensionEpisode>> {
        val args = if (source.engine == ExtensionEngine.CLOUDSTREAM) {
            mapOf("sourceId" to source.bridgeSourceId(), "url" to media.url)
        } else {
            mapOf(
                "sourceId" to source.bridgeSourceId(),
                "isAnime" to source.isAnime,
                "media" to mapOf("title" to media.title, "url" to media.url, "thumbnail_url" to media.thumbnailUrl),
            )
        }
        return parseDesktopDetails(call(if (source.engine == ExtensionEngine.CLOUDSTREAM) "csGetDetail" else "getDetail", args), media)
    }

    actual suspend fun videos(source: ExtensionSource, episode: ExtensionEpisode): List<ExtensionVideo> =
        fetchVideos(source, episode)

    actual suspend fun videosStream(
        source: ExtensionSource,
        episode: ExtensionEpisode,
        onVideo: (suspend (ExtensionVideo) -> Unit)?,
    ): List<ExtensionVideo> {
        if (source.engine == ExtensionEngine.CLOUDSTREAM) {
            val deadline = System.currentTimeMillis() + 30_000
            val collected = linkedMapOf<String, ExtensionVideo>()
            while (System.currentTimeMillis() < deadline) {
                val batch = fetchVideos(source, episode)
                for (video in batch) {
                    val key = "${video.quality}|${video.url}"
                    if (key !in collected) {
                        collected[key] = video
                        onVideo?.invoke(video)
                    }
                }
                if (collected.isNotEmpty()) return collected.values.toList()
                delay(500)
            }
            return collected.values.toList()
        }
        var result = fetchVideos(source, episode)
        result.forEach { onVideo?.invoke(it) }
        if (result.isEmpty()) {
            delay(2_000)
            result = fetchVideos(source, episode)
            result.forEach { onVideo?.invoke(it) }
        }
        return result
    }

    private suspend fun fetchVideos(source: ExtensionSource, episode: ExtensionEpisode): List<ExtensionVideo> {
        val episodePayload = buildMap<String, Any?> {
            put("name", episode.name)
            put("url", episode.url)
            put("episode_number", episode.number)
            if (episode.sortMap.isNotEmpty()) put("sortMap", episode.sortMap)
        }
        val args = if (source.engine == ExtensionEngine.CLOUDSTREAM) {
            mapOf("sourceId" to source.bridgeSourceId(), "url" to episode.url)
        } else {
            mapOf("sourceId" to source.bridgeSourceId(), "isAnime" to source.isAnime, "episode" to episodePayload)
        }
        return parseDesktopVideos(call(if (source.engine == ExtensionEngine.CLOUDSTREAM) "csGetVideoList" else "getVideoList", args))
    }

    actual suspend fun cancel(token: String) {
        process?.outputStream?.bufferedWriter()?.apply {
            write(json.encodeToString(JsonObject.serializer(), buildJsonObject {
                put("method", "cancel"); put("id", token)
                put("args", buildJsonObject { put("id", token) })
            }))
            newLine(); flush()
        }
    }

    actual suspend fun reloadAndResolveBridgeId(source: ExtensionSource): String? {
        return source.runtimeId?.takeIf { it.isNotBlank() } ?: source.id
    }

    actual fun logs(): List<String> = logLines.toList()

    actual suspend fun setCookies(url: String, cookieString: String) {
        log("[Desktop] setCookies not supported without WebView")
    }

    actual suspend fun setUserAgent(url: String, userAgent: String) {
        log("[Desktop] setUserAgent not supported without WebView")
    }

    actual suspend fun getCookies(url: String): String? {
        log("[Desktop] getCookies not supported without WebView")
        return null
    }

    actual fun openCloudflareBypass(url: String) {
        log("[Desktop] Cloudflare bypass not supported ($url)")
    }

    private fun startRuntime() {
        process?.takeIf { it.isAlive }?.let {
            _state.value = _state.value.copy(installed = true, ready = true, status = "Ready")
            return
        }
        val java = findJava()
        val started = ProcessBuilder(java, "-jar", runtimeFile.absolutePath).start()
        process = started
        Thread {
            started.inputStream.bufferedReader().forEachLine { line ->
                runCatching {
                    val response = json.parseToJsonElement(line).jsonObject
                    val id = response["id"]?.jsonPrimitive?.contentOrNull ?: return@runCatching
                    pending.remove(id)?.complete(response["data"] ?: JsonPrimitive(""))
                }.onFailure { log("Invalid runtime response: $line") }
            }
        }.apply { isDaemon = true; start() }
        Thread {
            started.errorStream.bufferedReader().forEachLine(::log)
        }.apply { isDaemon = true; start() }
        _state.value = ExtensionRuntimeState(true, true, version = "1.8.2", status = "Ready")
    }

    private suspend fun call(method: String, args: Map<String, Any?> = emptyMap()): kotlinx.serialization.json.JsonElement {
        ensureReady()
        val id = ids.incrementAndGet().toString()
        val deferred = CompletableDeferred<kotlinx.serialization.json.JsonElement>()
        pending[id] = deferred
        val request = buildJsonObject {
            put("method", method)
            put("id", id)
            put("args", anyToJson(args))
        }
        val writer = process?.outputStream?.bufferedWriter() ?: error("Runtime process stopped")
        synchronized(writer) {
            writer.write(request.toString())
            writer.newLine()
            writer.flush()
        }
        return deferred.await()
    }

    private fun ensureReady() {
        if (!_state.value.ready) error("Install the extension runtime first")
    }
    private fun findJava(): String {
        val bundled = File(ExtensionPlatformFiles.rootDir(), "runtime/jre/bin/${if (System.getProperty("os.name").contains("win", true)) "java.exe" else "java"}")
        return if (bundled.exists()) bundled.absolutePath else "java"
    }
    private fun log(value: String) {
        if (logLines.size >= 200) logLines.removeFirst()
        logLines.addLast(value)
    }
    private fun fail(message: String, error: Throwable) {
        log("$message: ${error.message}")
        _state.value = _state.value.copy(installed = runtimeFile.exists(), ready = false, status = message, error = error.message)
    }

    companion object {
        private const val RUNTIME_URL =
            "https://github.com/RyanYuuki/AnymeXExtensionRuntimeBridge/releases/download/v1.8.2/anymex_desktop_runtime.jar"
    }
}

actual object ExtensionPlatformFiles {
    actual val isAndroid: Boolean = false
    actual fun rootDir(): String =
        File(System.getProperty("user.home"), ".anisurge/extensions").apply { mkdirs() }.absolutePath

    actual fun stagingPathFor(source: ExtensionSource): String {
        val safeId = source.uninstallKey().replace(Regex("[^A-Za-z0-9._-]"), "_")
        val ext = source.artifactExtension()
        val engineDir = when (source.engine) {
            ExtensionEngine.CLOUDSTREAM -> "cloudstream"
            ExtensionEngine.MANGAYOMI -> source.bridgeStorageDir()
            else -> source.engine.name.lowercase()
        }
        val dir = File(rootDir(), engineDir).apply { mkdirs() }
        return File(dir, "$safeId.$ext").absolutePath
    }

    actual fun ensureParentDirs(path: String) {
        File(path).parentFile?.mkdirs()
    }

    actual suspend fun download(client: HttpClient, url: String, destination: String) = withContext(Dispatchers.IO) {
        val file = File(destination)
        ensureParentDirs(destination)
        val target = if (destination.endsWith(".jar") && url.substringBefore("?").endsWith(".apk")) {
            File("$destination.apk")
        } else file
        val channel = client.get(url).bodyAsChannel()
        target.outputStream().use { output ->
            val buffer = ByteArray(64 * 1024)
            while (!channel.isClosedForRead) {
                val read = channel.readAvailable(buffer)
                if (read > 0) output.write(buffer, 0, read)
            }
        }
        if (target != file) {
            convertApkToJar(client, target, file)
            target.delete()
        }
    }
    actual fun delete(path: String): Boolean = File(path).delete()

    actual fun installedApkPathCandidates(source: ExtensionSource): List<String> {
        val pkg = source.uninstallKey()
        val safeId = source.id.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val ext = source.artifactExtension()
        val engineDir = when (source.engine) {
            ExtensionEngine.CLOUDSTREAM -> "cloudstream"
            ExtensionEngine.MANGAYOMI -> source.bridgeStorageDir()
            else -> source.engine.name.lowercase()
        }
        val root = File(ExtensionPlatformFiles.rootDir())
        return buildList {
            source.installedPath?.takeIf { it.isNotBlank() }?.let(::add)
            add(stagingPathFor(source))
            add(File(root, "$engineDir/$safeId.$ext").absolutePath)
            add(File(root, "$engineDir/$pkg.$ext").absolutePath)
        }.distinct()
    }
}

private suspend fun convertApkToJar(client: HttpClient, apk: File, output: File) {
    val tools = File(ExtensionPlatformFiles.rootDir(), "runtime/dex2jar")
    val executable = File(
        tools,
        "dex-tools-v2.4/${if (System.getProperty("os.name").contains("win", true)) "d2j-dex2jar.bat" else "d2j-dex2jar.sh"}",
    )
    if (!executable.exists()) {
        val archive = File(tools, "dex2jar.zip")
        archive.parentFile.mkdirs()
        val channel = client.get(
            "https://github.com/pxb1988/dex2jar/releases/download/v2.4/dex-tools-v2.4.zip",
        ).bodyAsChannel()
        archive.outputStream().use { output ->
            val buffer = ByteArray(64 * 1024)
            while (!channel.isClosedForRead) {
                val read = channel.readAvailable(buffer)
                if (read > 0) output.write(buffer, 0, read)
            }
        }
        ZipInputStream(archive.inputStream().buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val destination = File(tools, entry.name).canonicalFile
                require(destination.path.startsWith(tools.canonicalPath + File.separator)) { "Invalid dex2jar archive entry" }
                if (entry.isDirectory) destination.mkdirs()
                else {
                    destination.parentFile?.mkdirs()
                    destination.outputStream().use { zip.copyTo(it) }
                }
            }
        }
        archive.delete()
        if (!System.getProperty("os.name").contains("win", true)) executable.setExecutable(true)
    }
    val command = if (System.getProperty("os.name").contains("win", true)) {
        listOf("cmd", "/c", executable.absolutePath, "--force", apk.absolutePath, "-o", output.absolutePath)
    } else {
        listOf(executable.absolutePath, "--force", apk.absolutePath, "-o", output.absolutePath)
    }
    val process = ProcessBuilder(command).redirectErrorStream(true).start()
    val outputText = process.inputStream.bufferedReader().readText()
    check(process.waitFor() == 0 && output.exists()) { "dex2jar failed: $outputText" }
}

private fun anyToJson(value: Any?): kotlinx.serialization.json.JsonElement = when (value) {
    null -> kotlinx.serialization.json.JsonNull
    is String -> JsonPrimitive(value)
    is Number -> JsonPrimitive(value)
    is Boolean -> JsonPrimitive(value)
    is Map<*, *> -> JsonObject(value.entries.associate { it.key.toString() to anyToJson(it.value) })
    is Iterable<*> -> JsonArray(value.map(::anyToJson))
    else -> JsonPrimitive(value.toString())
}

private fun parseDesktopSources(
    data: kotlinx.serialization.json.JsonElement,
    engine: ExtensionEngine,
    paths: List<String>,
): List<ExtensionSource> = (data as? JsonArray)?.mapNotNull { item ->
    val o = item as? JsonObject ?: return@mapNotNull null
    val pkg = o["pkg"]?.jsonPrimitive?.contentOrNull
    val bridgeId = o["id"]?.jsonPrimitive?.contentOrNull ?: pkg ?: return@mapNotNull null
    val catalogId = pkg?.takeIf { it.isNotBlank() } ?: bridgeId
    val itemType = o["itemType"]?.jsonPrimitive?.contentOrNull?.lowercase()
    val isAnime = when {
        itemType?.contains("anime") == true -> true
        itemType?.contains("manga") == true -> false
        engine == ExtensionEngine.MANGAYOMI -> false
        else -> true
    }
    ExtensionSource(
        id = catalogId,
        name = o["name"]?.jsonPrimitive?.contentOrNull ?: catalogId,
        engine = engine,
        language = o["lang"]?.jsonPrimitive?.contentOrNull ?: "all",
        version = o["version"]?.jsonPrimitive?.contentOrNull ?: "",
        iconUrl = o["iconUrl"]?.jsonPrimitive?.contentOrNull,
        installedPath = paths.firstOrNull(),
        runtimeId = bridgeId,
        pkgName = pkg,
        isAnime = isAnime,
        installed = true,
    )
}.orEmpty()

private fun parseDesktopMedia(data: kotlinx.serialization.json.JsonElement): List<ExtensionMedia> {
    val list = (data as? JsonObject)?.get("list") as? JsonArray ?: data as? JsonArray ?: JsonArray(emptyList())
    return list.mapNotNull { item ->
        val o = item as? JsonObject ?: return@mapNotNull null
        val title = o["title"]?.jsonPrimitive?.contentOrNull ?: o["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
        val url = o["url"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
        ExtensionMedia(
            title = title,
            url = url,
            thumbnailUrl = o["thumbnail_url"]?.jsonPrimitive?.contentOrNull ?: o["posterUrl"]?.jsonPrimitive?.contentOrNull ?: o["cover"]?.jsonPrimitive?.contentOrNull,
        )
    }
}

private fun parseDesktopDetails(
    data: kotlinx.serialization.json.JsonElement,
    fallback: ExtensionMedia,
): Pair<ExtensionMedia, List<ExtensionEpisode>> {
    val o = data as? JsonObject ?: return fallback to emptyList()
    val media = fallback.copy(
        title = o["title"]?.jsonPrimitive?.contentOrNull ?: fallback.title,
        thumbnailUrl = o["cover"]?.jsonPrimitive?.contentOrNull ?: o["thumbnail_url"]?.jsonPrimitive?.contentOrNull ?: o["posterUrl"]?.jsonPrimitive?.contentOrNull ?: fallback.thumbnailUrl,
        description = o["description"]?.jsonPrimitive?.contentOrNull ?: fallback.description,
    )
    val episodes = (o["episodes"] as? JsonArray)?.mapNotNull { item ->
        val ep = item as? JsonObject ?: return@mapNotNull null
        val name = ep["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
        val url = ep["url"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
        val number = ep["episode_number"]?.jsonPrimitive?.doubleOrNull
            ?: ep["episode"]?.jsonPrimitive?.doubleOrNull
            ?: ep["ep"]?.jsonPrimitive?.doubleOrNull
            ?: 0.0
        val sortMap = (ep["sortMap"] as? JsonObject)?.mapValues { it.value.jsonPrimitive.contentOrNull.orEmpty() }.orEmpty()
        ExtensionEpisode(name, url, number, sortMap)
    }.orEmpty()
    return media to episodes
}

private fun parseDesktopVideos(data: kotlinx.serialization.json.JsonElement): List<ExtensionVideo> =
    (data as? JsonArray)?.mapNotNull { item ->
        val o = item as? JsonObject ?: return@mapNotNull null
        val url = o["url"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
        val headers = (o["headers"] as? JsonObject)?.mapValues { it.value.jsonPrimitive.contentOrNull.orEmpty() }.orEmpty()
        val subtitles = (o["subtitles"] as? JsonArray)?.mapNotNull { sub ->
            val s = sub as? JsonObject ?: return@mapNotNull null
            val subUrl = s["file"]?.jsonPrimitive?.contentOrNull ?: s["url"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            ExtensionSubtitle(
                subUrl,
                s["label"]?.jsonPrimitive?.contentOrNull ?: s["lang"]?.jsonPrimitive?.contentOrNull ?: "Default",
            )
        }.orEmpty()
        val audios = (o["audios"] as? JsonArray)?.mapNotNull { audio ->
            val a = audio as? JsonObject ?: return@mapNotNull null
            val audioUrl = a["file"]?.jsonPrimitive?.contentOrNull ?: a["url"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            ExtensionSubtitle(
                audioUrl,
                a["label"]?.jsonPrimitive?.contentOrNull ?: a["lang"]?.jsonPrimitive?.contentOrNull ?: "Default",
            )
        }.orEmpty()
        val allSubtitles = (subtitles + audios).distinctBy { it.url }
        ExtensionVideo(url, o["quality"]?.jsonPrimitive?.contentOrNull ?: o["name"]?.jsonPrimitive?.contentOrNull ?: "Auto", headers, allSubtitles)
    }.orEmpty()

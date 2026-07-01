package to.kuudere.anisuge.extensions

import android.annotation.SuppressLint
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import dalvik.system.DexClassLoader
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
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import to.kuudere.anisuge.platform.androidAppContext
import java.io.File
import java.lang.reflect.Method
import java.util.UUID

actual class ExtensionRuntime actual constructor() {
    private val _state = MutableStateFlow(ExtensionRuntimeState())
    actual val state: StateFlow<ExtensionRuntimeState> = _state.asStateFlow()
    private val logLines = ArrayDeque<String>()
    private var bridge: Any? = null
    private var bridgeClass: Class<*>? = null
    private var bridgeSourceRegistry = emptyMap<String, String>()
    private var cachedLoadedSources: List<ExtensionSource> = emptyList()
    private var cachedLoadedSourcesAt = 0L
    private var lastAnimeBridgeReloadAt = 0L
    private var lastMangaBridgeReloadAt = 0L
    private val soraJson = Json { ignoreUnknownKeys = true; isLenient = true }
    private val soraHttp = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
    private val soraResults = mutableMapOf<String, CompletableDeferred<String>>()
    private var soraWebView: WebView? = null
    private var soraWebViewReady = false
    private val runtimeFile get() = File(ExtensionPlatformFiles.rootDir(), "runtime/anymex_runtime_host.apk")

    init {
        if (runtimeFile.exists()) {
            runCatching { loadRuntime() }.onFailure { fail("Runtime load failed", it) }
        }
    }

    actual suspend fun install(force: Boolean) = withContext(Dispatchers.IO) {
        if (_state.value.busy) return@withContext
        _state.value = _state.value.copy(busy = true, error = null, status = "Downloading runtime...")
        try {
            if (force || !runtimeFile.exists()) {
                ExtensionPlatformFiles.download(
                    HttpClient(),
                    RUNTIME_URL,
                    runtimeFile.absolutePath,
                )
            }
            loadRuntime()
        } catch (e: Exception) {
            fail("Runtime installation failed", e)
            throw e
        } finally {
            _state.value = _state.value.copy(busy = false)
        }
    }

    actual suspend fun remove() = withContext(Dispatchers.IO) {
        runCatching { invoke("shutdown") }
        bridge = null
        bridgeClass = null
        runtimeFile.delete()
        _state.value = ExtensionRuntimeState()
    }

    actual suspend fun installExtension(path: String, isAnime: Boolean): Boolean = withContext(Dispatchers.IO) {
        if (isIntegratedScriptPath(path)) {
            val srcFile = File(path)
            if (!srcFile.exists() || !srcFile.canRead()) {
                log("Sora install failed: cannot read staged file $path")
                return@withContext false
            }
            log("Installed Sora source: ${srcFile.name} → ${srcFile.absolutePath}")
            return@withContext true
        }

        ensureReady()
        val ctx = androidAppContext
        val dirName = if (isAnime) "exts" else "exts_manga"
        val privateDir = File(ctx.filesDir, dirName).apply { mkdirs() }
        if (!privateDir.exists() || !privateDir.canWrite()) {
            log("Install failed: bridge dir not writable: ${privateDir.absolutePath}")
            return@withContext false
        }

        val srcFile = File(path)
        if (!srcFile.exists() || !srcFile.canRead()) {
            log("Install failed: cannot read staged file $path (exists=${srcFile.exists()})")
            return@withContext false
        }

        if (path.endsWith(".dart", ignoreCase = true) || path.endsWith(".js", ignoreCase = true)) {
            val dstFile = File(privateDir, srcFile.name)
            if (srcFile.absolutePath != dstFile.absolutePath) {
                makeWritableForReplace(dstFile)
                srcFile.inputStream().use { input ->
                    dstFile.outputStream().use { output -> input.copyTo(output) }
                }
            }
            finalizeInstalledArtifact(dstFile)
            log("Installed manga extension source: ${dstFile.name} → ${dstFile.absolutePath}")
            reloadInstalledSources(isAnime)
            return@withContext true
        }

        val pm = ctx.packageManager
        val pkgInfo = pm.getPackageArchiveInfo(srcFile.absolutePath, 0)
            ?: run {
                log("Install failed: not a valid APK at $path")
                return@withContext false
            }
        val pkgName = pkgInfo.packageName

        val dstFile = File(privateDir, "$pkgName.apk")
        val tmpFile = File(privateDir, "$pkgName.apk.tmp")
        makeWritableForReplace(dstFile)
        makeWritableForReplace(tmpFile)
        tmpFile.delete()

        srcFile.inputStream().use { input ->
            tmpFile.outputStream().use { output -> input.copyTo(output) }
        }
        if (!tmpFile.renameTo(dstFile)) {
            makeWritableForReplace(dstFile)
            srcFile.copyTo(dstFile, overwrite = true)
            tmpFile.delete()
        }
        finalizeInstalledArtifact(dstFile)
        log("Installed extension APK: $pkgName → ${dstFile.absolutePath}")

        reloadInstalledSources(isAnime)
        true
    }

    actual suspend fun uninstallExtension(packageName: String, isAnime: Boolean): Boolean = withContext(Dispatchers.IO) {
        ensureReady()
        val dirName = if (isAnime) "exts" else "exts_manga"
        val privateDir = File(androidAppContext.filesDir, dirName)
        var deleted = false
        File(privateDir, "$packageName.apk").takeIf { it.exists() }?.let { file ->
            makeWritableForReplace(file)
            deleted = !file.exists() || deleted
        }
        privateDir.listFiles()
            ?.filter { it.isFile && it.extension.equals("apk", ignoreCase = true) && it.nameWithoutExtension == packageName }
            ?.forEach { file ->
                makeWritableForReplace(file)
                deleted = !file.exists() || deleted
            }
        val iconFile = File(androidAppContext.cacheDir, "${packageName}_icon.png")
        if (iconFile.exists()) iconFile.delete()
        log("Uninstalled extension: $packageName (apkDeleted=$deleted)")

        reloadInstalledSources(isAnime)
        true
    }

    actual suspend fun invalidateInstalledSourcesCache() = withContext(Dispatchers.IO) {
        cachedLoadedSources = emptyList()
        cachedLoadedSourcesAt = 0L
        lastAnimeBridgeReloadAt = 0L
        lastMangaBridgeReloadAt = 0L
        bridgeSourceRegistry = emptyMap()
    }

    private fun appFilesDir(): String = androidAppContext.filesDir.absolutePath

    private fun animeExtensionsDir(): File =
        File(androidAppContext.filesDir, "exts").apply { mkdirs() }

    private fun mangaExtensionsDir(): File =
        File(androidAppContext.filesDir, "exts_manga").apply { mkdirs() }

    private fun reloadInstalledSources(isAnime: Boolean) {
        cachedLoadedSources = emptyList()
        cachedLoadedSourcesAt = 0L
        if (isAnime) {
            lastAnimeBridgeReloadAt = 0L
            ensureBridgeDirArtifactsReadOnly(animeExtensionsDir(), ::log)
            invoke("getInstalledAnimeExtensions", androidAppContext, appFilesDir())
        } else {
            lastMangaBridgeReloadAt = 0L
            ensureBridgeDirArtifactsReadOnly(mangaExtensionsDir(), ::log)
            invoke("getInstalledMangaExtensions", androidAppContext, appFilesDir())
        }
    }



    private fun updateBridgeRegistry(sources: List<ExtensionSource>) {
        bridgeSourceRegistry = buildMap {
            for (source in sources) {
                val bridgeId = source.runtimeId?.takeIf { it.isNotBlank() } ?: continue
                source.pkgName?.takeIf { it.isNotBlank() }?.let { put(it, bridgeId) }
                put(source.id, bridgeId)
                put(source.name.lowercase(), bridgeId)
            }
        }
    }

    private fun resolveBridgeSourceId(source: ExtensionSource): String {
        if (source.engine == ExtensionEngine.CLOUDSTREAM) {
            return source.runtimeId?.takeIf { it.isNotBlank() } ?: source.id
        }
        source.runtimeId?.takeIf { it.isNotBlank() && it.all(Char::isDigit) }?.let { return it }
        val keys = listOfNotNull(
            source.pkgName?.takeIf { it.isNotBlank() },
            source.id.takeIf { it.isNotBlank() },
            source.name.takeIf { it.isNotBlank() }?.lowercase(),
        )
        for (key in keys) {
            bridgeSourceRegistry[key]?.let { return it }
        }
        return source.runtimeId ?: source.id
    }

    private fun bridgeIsAnime(source: ExtensionSource): Boolean =
        when (source.engine) {
            ExtensionEngine.MANGAYOMI -> false
            else -> source.isAnime
        }

    private fun reloadBridgeForSource(source: ExtensionSource, force: Boolean = false) {
        when (source.engine) {
            ExtensionEngine.MANGAYOMI -> {
                val now = System.currentTimeMillis()
                if (!force && now - lastMangaBridgeReloadAt < BRIDGE_RELOAD_COOLDOWN_MS) return
                lastMangaBridgeReloadAt = now
                val raw = invoke("getInstalledMangaExtensions", androidAppContext, appFilesDir())
                updateBridgeRegistry(parseSources(raw, ExtensionEngine.MANGAYOMI, emptyList()))
                Log.i(TAG, "reloadBridge: manga APK extensions for ${source.name}")
            }
            ExtensionEngine.CLOUDSTREAM -> {
                source.installedPath?.let { invoke("csLoadPlugin", androidAppContext, it) }
            }
            ExtensionEngine.ANIYOMI, ExtensionEngine.SORA -> {
                val now = System.currentTimeMillis()
                if (!force && now - lastAnimeBridgeReloadAt < BRIDGE_RELOAD_COOLDOWN_MS) return
                lastAnimeBridgeReloadAt = now
                val raw = invoke("getInstalledAnimeExtensions", androidAppContext, appFilesDir())
                val parsed = parseSources(raw, ExtensionEngine.ANIYOMI, emptyList())
                updateBridgeRegistry(parsed)
                cachedLoadedSources = parsed
                cachedLoadedSourcesAt = now
                Log.i(TAG, "reloadBridge: anime extensions (${parsed.size}) for ${source.name}")
            }
        }
    }

    actual suspend fun loadInstalledSources(paths: List<String>): List<ExtensionSource> = withContext<List<ExtensionSource>>(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (
            cachedLoadedSources.isNotEmpty() &&
            now - cachedLoadedSourcesAt < BRIDGE_RELOAD_COOLDOWN_MS
        ) {
            return@withContext cachedLoadedSources
        }
        val integratedScriptPaths = paths.filter { path ->
            isIntegratedScriptPath(path)
        }
        val integratedScriptSources = integratedScriptPaths.mapNotNull { parseInstalledScriptSource(it) }
        val bridgePaths = paths.filterNot { it in integratedScriptPaths }
        val hasAnimeApks = animeExtensionsDir().listFiles()?.any { it.isFile && it.extension == "apk" } == true
        val hasMangaApks = mangaExtensionsDir().listFiles()?.any { it.isFile && it.extension == "apk" } == true
        if (bridgePaths.isEmpty() && !hasAnimeApks && !hasMangaApks) {
            updateBridgeRegistry(integratedScriptSources)
            cachedLoadedSources = integratedScriptSources
            cachedLoadedSourcesAt = now
            return@withContext integratedScriptSources
        }

        ensureReady()
        val mangaPaths = paths.filter { path ->
            !path.endsWith(".cs3") && (path.contains("/exts_manga/") || path.contains("/mangayomi/"))
        }
        val animePaths = paths.filter { path ->
            !path.endsWith(".cs3") &&
                !path.contains("/exts_manga/") &&
                !path.contains("/mangayomi/") &&
                !path.contains("/sora/")
        }
        val cloudStreamPaths = paths.filter { it.endsWith(".cs3") }
        val loaded = buildList {
            addAll(integratedScriptSources)
            if (animePaths.isNotEmpty() || integratedScriptPaths.any { it.contains("/sora/") } || hasAnimeApks) {
                val raw = invoke("getInstalledAnimeExtensions", androidAppContext, appFilesDir())
                val hintPaths = (animePaths + integratedScriptPaths.filter { it.contains("/sora/") }).ifEmpty { paths }
                val parsed = parseSources(raw, ExtensionEngine.ANIYOMI, hintPaths)
                addAll(
                    parsed.map { source ->
                        val soraPath = integratedScriptPaths.firstOrNull { path -> path.contains("/sora/") && pathMatchesSource(path, source) }
                        if (soraPath != null) source.copy(engine = ExtensionEngine.SORA) else source
                    },
                )
            }
            if (mangaPaths.isNotEmpty() || hasMangaApks) {
                val raw = invoke("getInstalledMangaExtensions", androidAppContext, appFilesDir())
                val hintPaths = mangaPaths.ifEmpty { paths }
                addAll(parseSources(raw, ExtensionEngine.MANGAYOMI, hintPaths))
            }
            if (cloudStreamPaths.isNotEmpty()) {
                cloudStreamPaths.forEach { invoke("csLoadPlugin", androidAppContext, it) }
                val raw = invoke("csGetRegisteredProviders")
                addAll(parseSources(raw, ExtensionEngine.CLOUDSTREAM, cloudStreamPaths))
            }
        }.distinctBy { "${it.engine.name}:${it.runtimeId ?: it.id}:${it.language}" }
        updateBridgeRegistry(loaded)
        cachedLoadedSources = loaded
        cachedLoadedSourcesAt = now
        lastAnimeBridgeReloadAt = now
        loaded
    }

    actual suspend fun search(source: ExtensionSource, query: String): List<ExtensionMedia> = withContext(Dispatchers.IO) {
        if (source.usesIntegratedScriptRuntime()) return@withContext scriptSearch(source, query)
        ensureReady()
        reloadBridgeForSource(source)
        val rtId = resolveBridgeSourceId(source)
        Log.i(TAG, "search: engine=${source.engine.name} runtimeId=$rtId query=$query")
        val raw = when (source.engine) {
            ExtensionEngine.ANIYOMI, ExtensionEngine.SORA -> invoke(
                "aniyomiSearch",
                androidAppContext,
                rtId,
                bridgeIsAnime(source),
                query,
                1,
                requestParams(),
            )
            ExtensionEngine.MANGAYOMI -> {
                invoke(
                    "aniyomiSearch",
                    androidAppContext,
                    rtId,
                    bridgeIsAnime(source),
                    query,
                    1,
                    requestParams(),
                )
            }
            ExtensionEngine.CLOUDSTREAM -> invoke(
                "csSearch", androidAppContext, query, rtId, 1, requestParams()
            )
        }
        val results = parseMedia(raw)
        Log.i(TAG, "search: got ${results.size} results")
        results
    }

    actual suspend fun details(
        source: ExtensionSource,
        media: ExtensionMedia,
    ): Pair<ExtensionMedia, List<ExtensionEpisode>> = withContext(Dispatchers.IO) {
        if (source.usesIntegratedScriptRuntime()) return@withContext scriptDetails(source, media)
        ensureReady()
        reloadBridgeForSource(source)
        val rtId = resolveBridgeSourceId(source)
        val raw = when (source.engine) {
            ExtensionEngine.ANIYOMI, ExtensionEngine.SORA -> invoke(
                "aniyomiGetDetail",
                androidAppContext,
                rtId,
                bridgeIsAnime(source),
                mapOf(
                    "title" to media.title,
                    "url" to media.url,
                    "thumbnail_url" to media.thumbnailUrl,
                    "description" to media.description,
                ),
                requestParams(),
            )
            ExtensionEngine.MANGAYOMI -> {
                invoke(
                    "aniyomiGetDetail",
                    androidAppContext,
                    rtId,
                    bridgeIsAnime(source),
                    mapOf(
                        "title" to media.title,
                        "url" to media.url,
                        "thumbnail_url" to media.thumbnailUrl,
                        "description" to media.description,
                    ),
                    requestParams(),
                )
            }
            ExtensionEngine.CLOUDSTREAM -> invoke(
                "csGetDetail", androidAppContext, rtId, media.url, requestParams()
            )
        }
        parseDetails(raw, media)
    }

    actual suspend fun videos(
        source: ExtensionSource,
        episode: ExtensionEpisode,
    ): List<ExtensionVideo> = withContext(Dispatchers.IO) {
        fetchVideos(source, episode)
    }

    actual suspend fun videosStream(
        source: ExtensionSource,
        episode: ExtensionEpisode,
        onVideo: (suspend (ExtensionVideo) -> Unit)?,
    ): List<ExtensionVideo> = withContext(Dispatchers.IO) {
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
                if (collected.isNotEmpty()) return@withContext collected.values.toList()
                delay(500)
            }
            return@withContext collected.values.toList()
        }

        var result = fetchVideos(source, episode)
        result.forEach { onVideo?.invoke(it) }
        if (result.isEmpty()) {
            delay(2_000)
            result = fetchVideos(source, episode)
            result.forEach { onVideo?.invoke(it) }
        }
        result
    }

    private suspend fun fetchVideos(source: ExtensionSource, episode: ExtensionEpisode): List<ExtensionVideo> {
        if (source.usesIntegratedScriptRuntime()) return scriptVideos(source, episode)
        ensureReady()
        reloadBridgeForSource(source)
        val rtId = resolveBridgeSourceId(source)
        val episodePayload = episodePayload(episode)
        Log.i(TAG, "videos: engine=${source.engine.name} runtimeId=$rtId episode=${episode.name} url=${episode.url}")
        val raw = when (source.engine) {
            ExtensionEngine.ANIYOMI, ExtensionEngine.SORA -> invoke(
                "aniyomiGetVideoList",
                androidAppContext,
                rtId,
                bridgeIsAnime(source),
                episodePayload,
                requestParams(),
            )
            ExtensionEngine.MANGAYOMI -> {
                invoke(
                    "aniyomiGetVideoList",
                    androidAppContext,
                    rtId,
                    bridgeIsAnime(source),
                    episodePayload,
                    requestParams(),
                )
            }
            ExtensionEngine.CLOUDSTREAM -> invoke(
                "csGetVideoList", androidAppContext, rtId, episode.url, requestParams()
            )
        }
        Log.i(TAG, "videos: raw type=${raw?.javaClass?.simpleName}")
        val parsed = parseVideos(raw)
        Log.i(TAG, "videos: parsed ${parsed.size} videos")
        return parsed
    }

    private fun episodePayload(episode: ExtensionEpisode): Map<String, Any?> = buildMap {
        put("name", episode.name)
        put("url", episode.url)
        put("episode_number", episode.number)
        if (episode.sortMap.isNotEmpty()) put("sortMap", episode.sortMap)
    }

    actual suspend fun cancel(token: String) {
        runCatching { invoke("cancelRequest", token) }
    }

    actual fun logs(): List<String> = logLines.toList()

    private fun loadRuntime() {
        val context = androidAppContext
        val cached = File(context.filesDir, "anisurge_extension_runtime_${runtimeFile.length()}.apk")
        if (!cached.exists()) runtimeFile.copyTo(cached, overwrite = true)
        cached.setReadOnly()
        val optimized = File(context.codeCacheDir, "extension_runtime").apply { mkdirs() }
        val loader = ChildFirstClassLoader(
            cached.absolutePath,
            optimized.absolutePath,
            cached.absolutePath,
            context.classLoader,
        )
        val clazz = loader.loadClass("com.anymex.runtimehost.RuntimeBridge")
        val instance = clazz.getField("INSTANCE").get(null)
        bridgeClass = clazz
        bridge = instance
        invoke("initialize", context, mapOf(
            "aniyomiExtensionsPath" to appFilesDir(),
            "mangayomiExtensionsPath" to appFilesDir(),
        ))
        ensureBridgeDirArtifactsReadOnly(animeExtensionsDir(), ::log)
        ensureBridgeDirArtifactsReadOnly(mangaExtensionsDir(), ::log)
        reloadInstalledSources(isAnime = true)
        reloadInstalledSources(isAnime = false)
        _state.value = ExtensionRuntimeState(
            installed = true,
            ready = true,
            version = "1.8.2",
            status = "Ready",
        )
        log("Runtime loaded from ${runtimeFile.absolutePath}")
    }

    private fun invoke(name: String, vararg args: Any?): Any? {
        val clazz = bridgeClass ?: error("Extension runtime is not loaded")
        val target = bridge ?: error("Extension runtime is not loaded")
        val candidates = clazz.methods.filter { it.name == name && it.parameterTypes.size == args.size }
        val method = candidates.firstOrNull { candidate -> parametersMatch(candidate, args) }
            ?: run {
                val argTypes = args.map { it?.javaClass?.name ?: "null" }
                val signatures = candidates.joinToString { candidate ->
                    candidate.parameterTypes.joinToString { it.name }
                }
                Log.e(TAG, "Runtime method $name/${args.size} unavailable. args=$argTypes candidates=[$signatures]")
                error("Runtime method $name/${args.size} is unavailable")
            }
        return try {
            method.invoke(target, *args)
        } catch (error: Throwable) {
            val cause = error.rootCause()
            val detail = cause.message?.takeIf { it.isNotBlank() } ?: cause::class.java.simpleName
            Log.e(TAG, "Runtime method $name failed: $detail", cause)
            log("Runtime method $name failed: $detail")
            throw IllegalStateException("$name failed: $detail", cause)
        }
    }

    private fun parametersMatch(method: Method, args: Array<out Any?>): Boolean =
        method.parameterTypes.zip(args).all { (type, value) ->
            if (value == null) return@all !type.isPrimitive
            when (type) {
                java.lang.Boolean.TYPE -> value is Boolean
                java.lang.Integer.TYPE -> value is Int || value is Long
                java.lang.Long.TYPE -> value is Long || value is Int
                java.lang.Double.TYPE -> value is Double || value is Float
                java.lang.Float.TYPE -> value is Float || value is Double
                else -> type.isAssignableFrom(value.javaClass) ||
                    (type == java.lang.String::class.java && value is String) ||
                    (type == java.lang.Long::class.java && value is Long) ||
                    (type == java.lang.Integer::class.java && value is Int)
            }
        }

    private fun ensureReady() {
        if (!_state.value.ready) error("Install the extension runtime first")
    }

    private fun requestParams() = mapOf("token" to "anisurge-${System.nanoTime()}")
    private fun log(message: String) {
        Log.i(TAG, message)
        if (logLines.size >= 200) logLines.removeFirst()
        logLines.addLast(message)
    }
    private fun fail(message: String, error: Throwable) {
        val cause = error.rootCause()
        val detail = cause.message?.takeIf { it.isNotBlank() } ?: cause::class.java.simpleName
        log("$message: $detail")
        _state.value = _state.value.copy(
            installed = runtimeFile.exists(),
            ready = false,
            status = message,
            error = detail,
        )
    }

    // Cookie injection — matches AnymeX (WebView cookies → OkHttp via android.webkit.CookieManager)
    // The bridge's AndroidCookieJar reads from CookieManager automatically when WebView loads pages.
    // No explicit setCookies() needed — CookieManager is system-wide and shared.
    actual suspend fun setCookies(url: String, cookieString: String) {
        // No-op: CookieManager handles this automatically when a WebView loads the URL.
        // The bridge's AndroidCookieJar reads cookies from CookieManager for OkHttp requests.
    }

    actual suspend fun setUserAgent(url: String, userAgent: String) {
        val host = url.substringAfter("://").substringBefore("/").substringBefore(":")
        System.setProperty("anymex.ua.$host", userAgent)
        println("[CookieInjection] Set User-Agent for $host")
    }

    actual suspend fun getCookies(url: String): String? {
        val cookieManager = android.webkit.CookieManager.getInstance()
        return cookieManager.getCookie(url)
    }

    actual fun openCloudflareBypass(url: String) {
        println("[CookieInjection] Opening Cloudflare bypass WebView for $url")
        val ctx = androidAppContext
        val intent = android.content.Intent(ctx, CloudflareBypassActivity::class.java).apply {
            putExtra(CloudflareBypassActivity.EXTRA_URL, url)
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(intent)
    }

    private fun parseInstalledScriptSource(path: String): ExtensionSource? {
        val file = File(path)
        if (!file.exists() || !file.isFile || !file.extension.equals("js", ignoreCase = true)) return null
        val stem = file.nameWithoutExtension
        val engine = if (path.contains("/sora/") || path.contains("\\sora\\")) ExtensionEngine.SORA else ExtensionEngine.MANGAYOMI
        return ExtensionSource(
            id = stem,
            name = stem.replace('_', ' ').replaceFirstChar { it.uppercase() },
            engine = engine,
            language = "all",
            installedPath = file.absolutePath,
            runtimeId = stem,
            pkgName = stem,
            installed = true,
        )
    }

    private fun scriptSourcePath(source: ExtensionSource): String =
        source.installedPath?.takeIf { it.isNotBlank() } ?: ExtensionPlatformFiles.stagingPathFor(source)

    private suspend fun scriptSearch(source: ExtensionSource, query: String): List<ExtensionMedia> {
        val raw = callScript(source, if (source.engine == ExtensionEngine.SORA) "searchResults" else "search", listOf(query))
        val array = raw.parseJsonArrayOrEmpty()
        return array.mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            val title = item.string("title") ?: item.string("name") ?: return@mapNotNull null
            val url = item.string("href") ?: item.string("url") ?: return@mapNotNull null
            ExtensionMedia(
                title = title,
                url = url,
                thumbnailUrl = item.string("image") ?: item.string("thumbnail") ?: item.string("poster"),
            )
        }
    }

    private suspend fun scriptDetails(source: ExtensionSource, media: ExtensionMedia): Pair<ExtensionMedia, List<ExtensionEpisode>> {
        if (source.engine == ExtensionEngine.MANGAYOMI) {
            val raw = callScript(source, "getDetail", listOf(media.url))
            return parseScriptDetailObject(raw, media)
        }
        val detailsRaw = callScript(source, "extractDetails", listOf(media.url))
        val details = detailsRaw.parseJsonArrayOrEmpty().firstOrNull() as? JsonObject
        val episodesRaw = callScript(source, "extractEpisodes", listOf(media.url))
        val episodes = episodesRaw.parseJsonArrayOrEmpty().mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            val href = item.string("href") ?: item.string("url") ?: return@mapNotNull null
            val number = item.double("number") ?: item.double("episode") ?: 0.0
            ExtensionEpisode(
                name = item.string("name") ?: "Episode ${number.toInt().takeIf { it > 0 } ?: number}",
                url = href,
                number = number,
            )
        }
        return media.copy(
            description = details?.string("description") ?: media.description,
        ) to episodes
    }

    private suspend fun scriptVideos(source: ExtensionSource, episode: ExtensionEpisode): List<ExtensionVideo> {
        if (source.engine == ExtensionEngine.MANGAYOMI) {
            val raw = callScript(source, "getVideoList", listOf(episode.url))
            return parseScriptVideos(raw)
        }
        val url = callScript(source, "extractStreamUrl", listOf(episode.url)).trim().trim('"')
        if (url.isBlank()) return emptyList()
        return listOf(ExtensionVideo(url = url, quality = "Auto"))
    }

    private suspend fun callScript(source: ExtensionSource, method: String, args: List<String>): String {
        val sourceFile = File(scriptSourcePath(source))
        require(sourceFile.exists()) { "${source.name} script is missing — reinstall the extension" }
        val sourceCode = sourceFile.readText()
        val requestId = UUID.randomUUID().toString()
        val deferred = CompletableDeferred<String>()
        synchronized(soraResults) { soraResults[requestId] = deferred }
        val jsArgs = args.joinToString(",") { JsonPrimitive(it).toString() }
        val moduleName = source.id.replace(Regex("[^A-Za-z0-9_]"), "_")
        val script = """
            (async function() {
              try {
                const __anisurgeFetch = async function(url, h, m, b) {
                  const t = AnisurgeSora.fetch(String(url), JSON.stringify(h || {}), m || 'GET', b == null ? null : String(b));
                  if (typeof t === 'string' && t.startsWith('__ERROR__:')) throw new Error(t.substring(10));
                  return { ok: true, status: 200, text: async function() { return t; }, json: async function() { return JSON.parse(t); } };
                };
                globalThis.fetch = async function(url, opts) {
                  opts = opts || {};
                  return await __anisurgeFetch(url, opts.headers || {}, opts.method || 'GET', opts.body == null ? null : opts.body);
                };
                globalThis.fetchv2 = __anisurgeFetch;
                globalThis['$moduleName'] = (function() {
                  $sourceCode
                  const __exports = {};
                  if (typeof searchResults === 'function') __exports.searchResults = searchResults;
                  if (typeof extractDetails === 'function') __exports.extractDetails = extractDetails;
                  if (typeof extractEpisodes === 'function') __exports.extractEpisodes = extractEpisodes;
                  if (typeof extractStreamUrl === 'function') __exports.extractStreamUrl = extractStreamUrl;
                  return __exports;
                })();
                const target = globalThis['$moduleName'];
                if (!target || typeof target['$method'] !== 'function') throw new Error('Sora method missing: $method');
                const result = await target['$method']($jsArgs);
                AnisurgeSora.resolve('$requestId', JSON.stringify({ ok: true, value: result == null ? '' : (typeof result === 'string' ? result : JSON.stringify(result)) }));
              } catch (e) {
                AnisurgeSora.resolve('$requestId', JSON.stringify({ ok: false, error: String((e && e.stack) || e) }));
              }
            })();
        """.trimIndent()
        withContext(Dispatchers.Main) {
            val wv = ensureSoraWebView()
            wv.evaluateJavascript(script, null)
        }
        val payload = deferred.await()
        val obj = soraJson.parseToJsonElement(payload).jsonObject
        if (obj["ok"]?.jsonPrimitive?.contentOrNull == "false") {
            error(obj["error"]?.jsonPrimitive?.contentOrNull ?: "Sora script failed")
        }
        return obj["value"]?.jsonPrimitive?.contentOrNull.orEmpty()
    }

    private fun isIntegratedScriptPath(path: String): Boolean =
        path.endsWith(".js", ignoreCase = true) && (
            path.contains("/sora/") || path.contains("\\sora\\") ||
                path.contains("/exts_manga/") || path.contains("\\exts_manga\\") ||
                path.contains("/mangayomi/") || path.contains("\\mangayomi\\")
            )

    private fun ExtensionSource.usesIntegratedScriptRuntime(): Boolean =
        engine == ExtensionEngine.SORA || (engine == ExtensionEngine.MANGAYOMI && artifactExtension() == "js")

    private fun parseScriptDetailObject(raw: String, fallback: ExtensionMedia): Pair<ExtensionMedia, List<ExtensionEpisode>> {
        val obj = raw.parseJsonObjectOrNull() ?: return fallback to emptyList()
        val episodesArray = (obj["episodes"] as? JsonArray)
            ?: (obj["chapters"] as? JsonArray)
            ?: JsonArray(emptyList())
        val episodes = episodesArray.mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            val url = item.string("url") ?: item.string("link") ?: return@mapNotNull null
            val number = item.double("episode_number") ?: item.double("number") ?: item.double("chapterNumber") ?: 0.0
            ExtensionEpisode(
                name = item.string("name") ?: item.string("title") ?: "Episode ${number.toInt().takeIf { it > 0 } ?: number}",
                url = url,
                number = number,
            )
        }
        return fallback.copy(
            title = obj.string("title") ?: obj.string("name") ?: fallback.title,
            thumbnailUrl = obj.string("thumbnailUrl") ?: obj.string("imageUrl") ?: obj.string("cover") ?: fallback.thumbnailUrl,
            description = obj.string("description") ?: fallback.description,
        ) to episodes
    }

    private fun parseScriptVideos(raw: String): List<ExtensionVideo> {
        val array = raw.parseJsonArrayOrEmpty()
        return array.mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            val url = item.string("url") ?: item.string("link") ?: return@mapNotNull null
            ExtensionVideo(url, item.string("quality") ?: item.string("name") ?: "Auto")
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun ensureSoraWebView(): WebView {
        soraWebView?.let { return it }
        val readyLatch = java.util.concurrent.CountDownLatch(1)
        val webView = WebView(androidAppContext).apply {
            settings.javaScriptEnabled = true
            addJavascriptInterface(SoraJsBridge(), "AnisurgeSora")
            webViewClient = object : android.webkit.WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    readyLatch.countDown()
                }
            }
            loadUrl("about:blank")
        }
        soraWebView = webView
        // wait for about:blank to finish so the JS bridge is ready
        // use a short timeout to avoid deadlock
        kotlin.runCatching { readyLatch.await(2, java.util.concurrent.TimeUnit.SECONDS) }
        return webView
    }

    private inner class SoraJsBridge {
        @JavascriptInterface
        fun resolve(id: String, payload: String) {
            synchronized(soraResults) { soraResults.remove(id) }?.complete(payload)
        }

        @JavascriptInterface
        fun fetch(url: String, headersJson: String, method: String?, body: String?): String {
            val result = runCatching {
                val builder = Request.Builder().url(url)
                    .header("User-Agent", DEFAULT_SCRIPT_USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .header("Referer", url.substringBefore("?"))
                runCatching { soraJson.parseToJsonElement(headersJson).jsonObject }.getOrNull()?.forEach { (key, value) ->
                    value.jsonPrimitive.contentOrNull?.takeIf { it.isNotBlank() }?.let { builder.header(key, it) }
                }
                val verb = method?.uppercase()?.takeIf { it.isNotBlank() } ?: "GET"
                val requestBody = if (verb == "GET" || verb == "HEAD") null else (body ?: "").toRequestBody("text/plain; charset=utf-8".toMediaTypeOrNull())
                val request = builder.method(verb, requestBody).build()
                soraHttp.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@runCatching "__ERROR__:HTTP ${response.code}"
                    val bodyText = response.body.string()
                    if (bodyText.length < 5000) log("fetch OK: $url → ${bodyText.take(200)}")
                    bodyText
                }
            }.getOrElse { e ->
                val msg = "__ERROR__:${e.message ?: e.toString()}"
                log("fetch FAILED: $url → $msg")
                msg
            }
            return result
        }

        private fun log(msg: String) {
            android.util.Log.d(TAG, msg)
        }
    }

    companion object {
        private const val TAG = "AnisurgeExtensions"
        private const val BRIDGE_RELOAD_COOLDOWN_MS = 15_000L
        private const val DEFAULT_SCRIPT_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"
        private const val RUNTIME_URL =
            "https://github.com/RyanYuuki/AnymeXExtensionRuntimeBridge/releases/download/v1.8.2/anymex_runtime_host.apk"
    }
}

private fun Throwable.rootCause(): Throwable =
    generateSequence(this) { it.cause }.last()

/** Android 14+ rejects writable APKs in DexClassLoader — match Aniyomi/AnymeX install behavior. */
private fun finalizeInstalledArtifact(file: File) {
    file.setReadable(true, false)
    file.setWritable(false, false)
    file.setReadOnly()
}

private fun makeWritableForReplace(file: File) {
    if (!file.exists()) return
    file.setWritable(true, false)
    file.delete()
}

private fun makeWritableForStagingDownload(file: File) = makeWritableForReplace(file)

private fun ensureBridgeDirArtifactsReadOnly(dir: File, log: ((String) -> Unit)? = null) {
    dir.listFiles()
        ?.asSequence()
        ?.filter { it.isFile && it.extension.lowercase() in setOf("apk", "js", "dart") }
        ?.forEach { file ->
            if (file.canWrite()) {
                finalizeInstalledArtifact(file)
                log?.invoke("Repaired bridge artifact permissions: ${file.name}")
            }
        }
}

actual object ExtensionPlatformFiles {
    actual val isAndroid: Boolean = true
    actual fun rootDir(): String =
        File(androidAppContext.filesDir, "extensions").apply { mkdirs() }.absolutePath

    actual fun stagingPathFor(source: ExtensionSource): String {
        val safeId = source.uninstallKey().replace(Regex("[^A-Za-z0-9._-]"), "_")
        val ext = source.artifactExtension()
        return when (source.engine) {
            ExtensionEngine.MANGAYOMI -> {
                val dir = File(androidAppContext.filesDir, source.bridgeStorageDir()).apply { mkdirs() }
                File(dir, "$safeId.$ext").absolutePath
            }
            ExtensionEngine.CLOUDSTREAM -> {
                val dir = File(rootDir(), "cloudstream").apply { mkdirs() }
                File(dir, "$safeId.$ext").absolutePath
            }
            ExtensionEngine.ANIYOMI, ExtensionEngine.SORA -> {
                val dir = File(rootDir(), source.engine.name.lowercase()).apply { mkdirs() }
                File(dir, "$safeId.$ext").absolutePath
            }
        }
    }

    actual fun ensureParentDirs(path: String) {
        val file = File(path)
        val parent = file.parentFile ?: return
        if (!parent.exists()) parent.mkdirs()
        if (!parent.exists() || !parent.canWrite()) {
            error("Cannot write extension files to ${parent.absolutePath}")
        }
    }

    actual suspend fun download(client: HttpClient, url: String, destination: String) = withContext(Dispatchers.IO) {
        val file = File(destination)
        ensureParentDirs(destination)
        makeWritableForStagingDownload(file)
        val channel = client.get(url).bodyAsChannel()
        file.outputStream().use { output ->
            val buffer = ByteArray(64 * 1024)
            while (!channel.isClosedForRead) {
                val read = channel.readAvailable(buffer)
                if (read > 0) output.write(buffer, 0, read)
            }
        }
        file.setReadable(true, false)
        Unit
    }

    actual fun delete(path: String): Boolean {
        val file = File(path)
        if (!file.exists()) return true
        file.setWritable(true, false)
        return file.delete()
    }

    actual fun installedApkPathCandidates(source: ExtensionSource): List<String> {
        val pkg = source.uninstallKey()
        val filesDir = androidAppContext.filesDir
        val extsDir = File(filesDir, source.bridgeStorageDir())
        val ext = source.artifactExtension()
        val safeId = source.id.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return buildList {
            source.installedPath?.takeIf { it.isNotBlank() }?.let(::add)
            add(stagingPathFor(source))
            add(File(extsDir, "$safeId.$ext").absolutePath)
            add(File(extsDir, "$pkg.$ext").absolutePath)
            if (ext == "apk") {
                add(File(extsDir, "$pkg.apk").absolutePath)
                extsDir.listFiles()
                    ?.filter { it.isFile && it.extension.equals("apk", ignoreCase = true) && it.nameWithoutExtension == pkg }
                    ?.forEach { add(it.absolutePath) }
            }
        }.distinct()
    }

}

private class ChildFirstClassLoader(
    dexPath: String,
    optimizedDirectory: String,
    librarySearchPath: String?,
    parent: ClassLoader,
) : DexClassLoader(dexPath, optimizedDirectory, librarySearchPath, parent) {
    override fun loadClass(name: String, resolve: Boolean): Class<*> {
        if (name.startsWith("com.anymex.") || name.startsWith("com.lagradost.") || name.startsWith("eu.kanade.")) {
            runCatching { findClass(name) }.getOrNull()?.let {
                if (resolve) resolveClass(it)
                return it
            }
        }
        return super.loadClass(name, resolve)
    }
}

private fun pathFileStem(path: String): String =
    path.substringAfterLast("/").substringBeforeLast(".")

private fun pathStemMatchesBridgeName(stem: String, bridgeName: String): Boolean {
    val normalizedStem = stem.lowercase()
    val normalizedName = bridgeName.lowercase()
    if (normalizedStem == normalizedName) return true
    val stemToken = stem.substringAfterLast('.').lowercase()
    return stemToken.isNotBlank() && (
        normalizedName.contains(stemToken) ||
            stemToken.contains(normalizedName.substringBefore('.'))
        )
}

private fun pathMatchesSource(path: String, source: ExtensionSource): Boolean {
    val fileStem = pathFileStem(path)
    return source.pkgName == fileStem ||
        source.id == fileStem ||
        source.runtimeId == fileStem ||
        source.name.equals(fileStem, ignoreCase = true) ||
        pathStemMatchesBridgeName(fileStem, source.name)
}

private fun parseSources(raw: Any?, engine: ExtensionEngine, paths: List<String>): List<ExtensionSource> {
    val list = raw as? List<*> ?: return emptyList()
    val parsed = list.mapIndexedNotNull { index: Int, item: Any? ->
        val map = item as? Map<*, *> ?: return@mapIndexedNotNull null
        val pkg = map["pkg"]?.toString()
            ?: map["pkgName"]?.toString()
            ?: map["packageName"]?.toString()
        val bridgeId = map["id"]?.toString() ?: map["sourceId"]?.toString() ?: pkg ?: return@mapIndexedNotNull null
        val bridgeName = map["name"]?.toString().orEmpty()
        val path = paths.firstOrNull { candidate ->
            val stem = pathFileStem(candidate)
            (pkg != null && stem == pkg) ||
                stem == bridgeId ||
                (bridgeName.isNotBlank() && pathStemMatchesBridgeName(stem, bridgeName))
        } ?: paths.getOrNull(index) ?: paths.firstOrNull()
        val fallbackId = path?.let(::pathFileStem) ?: bridgeId
        val lang = map["lang"]?.toString() ?: "all"
        val catalogId = when {
            !pkg.isNullOrBlank() && lang != "all" -> "$pkg:$lang"
            !pkg.isNullOrBlank() -> pkg
            else -> fallbackId.takeIf { it.isNotBlank() && !it.all(Char::isDigit) } ?: bridgeId
        }
        val runtimeId = bridgeId.ifBlank { fallbackId }
        val itemType = map["itemType"]?.toString()?.lowercase()
        val isAnime = when {
            itemType?.contains("anime") == true -> true
            itemType?.contains("manga") == true -> false
            engine == ExtensionEngine.MANGAYOMI -> itemType?.contains("manga") != true
            else -> true
        }
        Log.i("AnisurgeExtensions", "parseSources: engine=$engine id=$catalogId runtimeId=$runtimeId name=${map["name"]}")
        ExtensionSource(
            id = catalogId,
            name = map["name"]?.toString() ?: catalogId,
            engine = engine,
            language = map["lang"]?.toString() ?: "all",
            version = map["version"]?.toString() ?: "",
            iconUrl = map["iconUrl"]?.toString(),
            installedPath = path,
            runtimeId = runtimeId,
            pkgName = pkg,
            isAnime = isAnime,
            installed = true,
        )
    }
    if (paths.isEmpty()) return parsed
    return parsed.map { source ->
        val matchedPath = paths.firstOrNull { path -> pathMatchesSource(path, source) }
            ?: paths.firstOrNull { path ->
                val stem = pathFileStem(path)
                source.pkgName == stem || source.id == stem
            }
        if (matchedPath != null) source.copy(installedPath = matchedPath) else source
    }
}

private fun parseMedia(raw: Any?): List<ExtensionMedia> {
    val map = raw as? Map<*, *>
    val list = map?.get("list") as? List<*> ?: raw as? List<*> ?: emptyList<Any?>()
    return list.mapNotNull { item ->
        val value = item as? Map<*, *> ?: return@mapNotNull null
        val title = value["title"]?.toString() ?: value["name"]?.toString() ?: return@mapNotNull null
        val url = value["url"]?.toString() ?: return@mapNotNull null
        ExtensionMedia(
            title = title,
            url = url,
            thumbnailUrl = value["thumbnail_url"]?.toString() ?: value["posterUrl"]?.toString() ?: value["cover"]?.toString(),
        )
    }
}

private fun parseDetails(raw: Any?, fallback: ExtensionMedia): Pair<ExtensionMedia, List<ExtensionEpisode>> {
    val map = raw as? Map<*, *> ?: return fallback to emptyList()
    val media = fallback.copy(
        title = map["title"]?.toString() ?: fallback.title,
        thumbnailUrl = map["cover"]?.toString() ?: map["thumbnail_url"]?.toString() ?: map["posterUrl"]?.toString() ?: fallback.thumbnailUrl,
        description = map["description"]?.toString() ?: fallback.description,
    )
    val episodes = (map["episodes"] as? List<*>)?.mapNotNull { item ->
        val value = item as? Map<*, *> ?: return@mapNotNull null
        val name = value["name"]?.toString() ?: return@mapNotNull null
        val url = value["url"]?.toString() ?: return@mapNotNull null
        val number = (value["episode_number"] as? Number)?.toDouble()
            ?: (value["episode"] as? Number)?.toDouble()
            ?: (value["ep"] as? Number)?.toDouble()
            ?: 0.0
        val sortMap = (value["sortMap"] as? Map<*, *>)?.mapNotNull { (key, raw) ->
            key?.toString()?.let { k -> k to (raw?.toString() ?: "") }
        }?.toMap().orEmpty().toMutableMap()
        value["translationType"]?.toString()?.takeIf { it.isNotBlank() }?.let {
            sortMap["translationType"] = it
        }
        value["translation_type"]?.toString()?.takeIf { it.isNotBlank() }?.let {
            sortMap["translation_type"] = it
        }
        ExtensionEpisode(name = name, url = url, number = number, sortMap = sortMap)
    }.orEmpty()
    return media to episodes
}

private fun parseVideos(raw: Any?): List<ExtensionVideo> =
    (raw as? List<*>)?.mapNotNull { item ->
        val map = item as? Map<*, *> ?: return@mapNotNull null
        val url = map["url"]?.toString() ?: return@mapNotNull null
        val headers = (map["headers"] as? Map<*, *>)?.mapNotNull { (key, value) ->
            key?.toString()?.let { k -> k to (value?.toString() ?: "") }
        }?.toMap().orEmpty()
        val subtitles = (map["subtitles"] as? List<*>)?.mapNotNull { subtitle ->
            val s = subtitle as? Map<*, *> ?: return@mapNotNull null
            val subUrl = s["file"]?.toString() ?: s["url"]?.toString() ?: return@mapNotNull null
            ExtensionSubtitle(subUrl, s["label"]?.toString() ?: s["lang"]?.toString() ?: "Default")
        }.orEmpty()
        val audios = (map["audios"] as? List<*>)?.mapNotNull { audio ->
            val a = audio as? Map<*, *> ?: return@mapNotNull null
            val audioUrl = a["file"]?.toString() ?: a["url"]?.toString() ?: return@mapNotNull null
            ExtensionSubtitle(audioUrl, a["label"]?.toString() ?: a["lang"]?.toString() ?: "Default")
        }.orEmpty()
        val allSubtitles = (subtitles + audios).distinctBy { it.url }
        ExtensionVideo(url, map["quality"]?.toString() ?: map["name"]?.toString() ?: "Auto", headers, allSubtitles)
    }.orEmpty()

private fun String.parseJsonArrayOrEmpty(): JsonArray =
    runCatching {
        when (val root = Json { ignoreUnknownKeys = true; isLenient = true }.parseToJsonElement(this)) {
            is JsonArray -> root
            is JsonObject -> root["results"] as? JsonArray ?: root["data"] as? JsonArray ?: JsonArray(emptyList())
            else -> JsonArray(emptyList())
        }
    }.getOrElse { JsonArray(emptyList()) }

private fun String.parseJsonObjectOrNull(): JsonObject? =
    runCatching { Json { ignoreUnknownKeys = true; isLenient = true }.parseToJsonElement(this) as? JsonObject }.getOrNull()

private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

private fun JsonObject.double(key: String): Double? =
    this[key]?.jsonPrimitive?.doubleOrNull ?: this[key]?.jsonPrimitive?.intOrNull?.toDouble()

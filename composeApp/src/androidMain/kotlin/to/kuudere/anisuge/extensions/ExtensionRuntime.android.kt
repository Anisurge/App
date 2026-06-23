package to.kuudere.anisuge.extensions

import android.util.Log
import dalvik.system.DexClassLoader
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import to.kuudere.anisuge.platform.androidAppContext
import java.io.File
import java.lang.reflect.Method

actual class ExtensionRuntime actual constructor() {
    private val _state = MutableStateFlow(ExtensionRuntimeState())
    actual val state: StateFlow<ExtensionRuntimeState> = _state.asStateFlow()
    private val logLines = ArrayDeque<String>()
    private var bridge: Any? = null
    private var bridgeClass: Class<*>? = null
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
        ensureReady()
        val ctx = androidAppContext
        val pm = ctx.packageManager
        val pkgInfo = pm.getPackageArchiveInfo(path, 0)
            ?: return@withContext false
        val pkgName = pkgInfo.packageName

        val dirName = if (isAnime) "exts" else "exts_manga"
        val privateDir = File(ctx.filesDir, dirName).apply { mkdirs() }
        val dstFile = File(privateDir, "$pkgName.apk")
        val tmpFile = File(privateDir, "$pkgName.apk.tmp")

        File(path).inputStream().use { input ->
            tmpFile.outputStream().use { output -> input.copyTo(output) }
        }
        if (!tmpFile.renameTo(dstFile)) {
            tmpFile.delete()
            return@withContext false
        }
        dstFile.setReadOnly()
        log("Installed extension APK: $pkgName → ${dstFile.absolutePath}")

        reloadInstalledSources(isAnime)
        true
    }

    actual suspend fun uninstallExtension(packageName: String, isAnime: Boolean): Boolean = withContext(Dispatchers.IO) {
        ensureReady()
        val dirName = if (isAnime) "exts" else "exts_manga"
        val privateDir = File(androidAppContext.filesDir, dirName)
        val apkFile = File(privateDir, "$packageName.apk")
        if (apkFile.exists()) apkFile.delete()
        val iconFile = File(androidAppContext.cacheDir, "${packageName}_icon.png")
        if (iconFile.exists()) iconFile.delete()
        log("Uninstalled extension: $packageName")

        reloadInstalledSources(isAnime)
        true
    }

    private fun reloadInstalledSources(isAnime: Boolean) {
        val dirName = if (isAnime) "exts" else "exts_manga"
        val privateDir = File(androidAppContext.filesDir, dirName)
        if (isAnime) {
            invoke("getInstalledAnimeExtensions", androidAppContext, privateDir.absolutePath)
        } else {
            invoke("getInstalledMangaExtensions", androidAppContext, privateDir.absolutePath)
        }
    }

    actual suspend fun loadInstalledSources(paths: List<String>): List<ExtensionSource> = withContext(Dispatchers.IO) {
        ensureReady()
        paths.groupBy { if (it.endsWith(".cs3")) ExtensionEngine.CLOUDSTREAM else ExtensionEngine.ANIYOMI }
            .flatMap { (engine, files) ->
                val raw = if (engine == ExtensionEngine.CLOUDSTREAM) {
                    files.forEach { invoke("csLoadPlugin", androidAppContext, it) }
                    invoke("csGetRegisteredProviders")
                } else {
                    val folder = File(files.firstOrNull() ?: return@flatMap emptyList()).parentFile?.absolutePath
                        ?: return@flatMap emptyList()
                    invoke("getInstalledAnimeExtensions", androidAppContext, folder)
                }
                parseSources(raw, engine, files)
            }
    }

    actual suspend fun search(source: ExtensionSource, query: String): List<ExtensionMedia> = withContext(Dispatchers.IO) {
        ensureReady()
        val rtId = source.runtimeId ?: source.id
        Log.i(TAG, "search: engine=${source.engine.name} runtimeId=$rtId query=$query")
        val raw = when (source.engine) {
            ExtensionEngine.ANIYOMI -> invoke(
                "aniyomiSearch", androidAppContext, rtId, true, query, 1, requestParams()
            )
            ExtensionEngine.CLOUDSTREAM -> invoke(
                "csSearch", androidAppContext, query, rtId, 1, requestParams()
            )
            else -> error("${source.engine.displayName} runtime is unavailable")
        }
        val results = parseMedia(raw)
        Log.i(TAG, "search: got ${results.size} results")
        results
    }

    actual suspend fun details(
        source: ExtensionSource,
        media: ExtensionMedia,
    ): Pair<ExtensionMedia, List<ExtensionEpisode>> = withContext(Dispatchers.IO) {
        ensureReady()
        val raw = when (source.engine) {
            ExtensionEngine.ANIYOMI -> invoke(
                "aniyomiGetDetail",
                androidAppContext,
                source.runtimeId ?: source.id,
                true,
                mapOf(
                    "title" to media.title,
                    "url" to media.url,
                    "thumbnail_url" to media.thumbnailUrl,
                    "description" to media.description,
                ),
                requestParams(),
            )
            ExtensionEngine.CLOUDSTREAM -> invoke(
                "csGetDetail", androidAppContext, source.runtimeId ?: source.id, media.url, requestParams()
            )
            else -> error("${source.engine.displayName} runtime is unavailable")
        }
        parseDetails(raw, media)
    }

    actual suspend fun videos(
        source: ExtensionSource,
        episode: ExtensionEpisode,
    ): List<ExtensionVideo> = withContext(Dispatchers.IO) {
        ensureReady()
        val rtId = source.runtimeId ?: source.id
        Log.i(TAG, "videos: engine=${source.engine.name} runtimeId=$rtId episode=${episode.name} url=${episode.url}")
        val raw = when (source.engine) {
            ExtensionEngine.ANIYOMI -> invoke(
                "aniyomiGetVideoList",
                androidAppContext,
                rtId,
                true,
                mapOf("name" to episode.name, "url" to episode.url, "episode_number" to episode.number),
                requestParams(),
            )
            ExtensionEngine.CLOUDSTREAM -> invoke(
                "csGetVideoList", androidAppContext, rtId, episode.url, requestParams()
            )
            else -> error("${source.engine.displayName} runtime is unavailable")
        }
        Log.i(TAG, "videos: raw type=${raw?.javaClass?.simpleName} raw=$raw")
        val parsed = parseVideos(raw)
        Log.i(TAG, "videos: parsed ${parsed.size} videos")
        parsed
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
        invoke("initialize", context, mapOf("aniyomiExtensionsPath" to File(ExtensionPlatformFiles.rootDir(), "extensions/aniyomi").absolutePath))
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
        val method = clazz.methods
            .filter { it.name == name && it.parameterTypes.size == args.size }
            .firstOrNull { method -> parametersMatch(method, args) }
            ?: error("Runtime method $name/${args.size} is unavailable")
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
            value == null || type.isAssignableFrom(value.javaClass) ||
                (type == java.lang.Boolean.TYPE && value is Boolean) ||
                (type == java.lang.Integer.TYPE && value is Int)
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

    companion object {
        private const val TAG = "AnisurgeExtensions"
        private const val RUNTIME_URL =
            "https://github.com/RyanYuuki/AnymeXExtensionRuntimeBridge/releases/download/v1.8.2/anymex_runtime_host.apk"
    }
}

private fun Throwable.rootCause(): Throwable =
    generateSequence(this) { it.cause }.last()

actual object ExtensionPlatformFiles {
    actual val isAndroid: Boolean = true
    actual fun rootDir(): String =
        File(androidAppContext.filesDir, "extensions").apply { mkdirs() }.absolutePath

    actual suspend fun download(client: HttpClient, url: String, destination: String) = withContext(Dispatchers.IO) {
        val file = File(destination)
        file.parentFile?.mkdirs()
        val channel = client.get(url).bodyAsChannel()
        file.outputStream().use { output ->
            val buffer = ByteArray(64 * 1024)
            while (!channel.isClosedForRead) {
                val read = channel.readAvailable(buffer)
                if (read > 0) output.write(buffer, 0, read)
            }
        }
    }

    actual fun delete(path: String): Boolean = File(path).delete()
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

private fun parseSources(raw: Any?, engine: ExtensionEngine, paths: List<String>): List<ExtensionSource> =
    (raw as? List<*>)?.mapNotNull { item ->
        val map = item as? Map<*, *> ?: return@mapNotNull null
        val id = map["id"]?.toString() ?: return@mapNotNull null
        ExtensionSource(
            id = id,
            name = map["name"]?.toString() ?: id,
            engine = engine,
            language = map["lang"]?.toString() ?: "all",
            version = map["version"]?.toString() ?: "",
            iconUrl = map["iconUrl"]?.toString(),
            installedPath = paths.firstOrNull(),
            runtimeId = id,
            installed = true,
        )
    }.orEmpty()

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
        ExtensionEpisode(name = name, url = url, number = number)
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

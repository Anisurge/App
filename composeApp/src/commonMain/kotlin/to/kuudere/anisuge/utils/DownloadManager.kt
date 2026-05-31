package to.kuudere.anisuge.utils

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.head
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readBytes
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okio.buffer
import okio.FileSystem
import okio.Path.Companion.toPath
import to.kuudere.anisuge.AppComponent
import to.kuudere.anisuge.platform.KmpFileSystem
import to.kuudere.anisuge.platform.isAndroidPlatform
import to.kuudere.anisuge.data.models.BatchScrapeResponse
import kotlinx.coroutines.Job
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

@Serializable
data class DownloadTask(
    val id: String, // animeId_epNum
    val animeId: String,
    val title: String,
    val episodeNumber: Int,
    val coverImage: String? = null,
    val progress: Float = 0f,
    val status: String = "Queued", // Queued, Downloading, Finished, Failed, Paused
    val downloadSpeed: String = "",
    val eta: String = "",
    val localPath: String? = null,
    val isPaused: Boolean = false,
    val headers: Map<String, String>? = null,
    /** Persisted so Resume can restart the coroutine after pause or process death. */
    val serverId: String? = null,
    val anilistId: Int? = null,
    val subLang: String? = null,
    val audioLang: String? = null,
    val streamUrl: String? = null,
    val preferBatchDub: Boolean = false,
    val useParallelSegments: Boolean = false,
    val fallbackAttemptedServers: List<String> = emptyList(),
    @kotlinx.serialization.Transient internal var job: Job? = null
)

data class BatchEpisodeDownload(
    val animeId: String,
    val anilistId: Int,
    val episodeNumber: Int,
    val title: String,
    val coverImage: String?,
)

object DownloadManager {
    val tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    private val scope = CoroutineScope(Dispatchers.Default)
    private val httpClient = AppComponent.httpClient
    private val infoService = AppComponent.infoService

    private val persistenceFile = "${getCacheDirectory()}/tasks.json"
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private const val MP4_BUFFER_SIZE = 8 * 1024
    private const val ANDROID_HLS_PARALLELISM = 4
    private const val DESKTOP_HLS_PARALLELISM = 6
    private const val SEASON_BATCH_DOWNLOAD_CONCURRENCY = 3
    const val MAX_SEASON_BATCH_EPISODES = 1000

    private fun downloadLog(taskId: String, message: String) {
        println("[Download][$taskId] $message")
    }

    init {
        loadTasks()

        // Update system notification when tasks change
        scope.launch {
            tasks.collect { list ->
                val active = list.filter {
                    (it.status.contains("Downloading") || it.status.contains("...") || it.status.contains("task")) &&
                            !it.isPaused
                }

                if (active.isEmpty()) {
                    to.kuudere.anisuge.platform.clearDownloadNotification()
                } else {
                    val count = active.size
                    val totalProgress = if (count > 0) active.sumOf { it.progress.toDouble() }.toFloat() / count else 0f
                    to.kuudere.anisuge.platform.updateDownloadNotification(count, totalProgress)
                }
            }
        }
    }

    private fun loadTasks() {
        scope.launch {
            try {
                if (KmpFileSystem.exists(persistenceFile)) {
                    // JSON files are always small, use standard read
                    val content: String = KmpFileSystem.source(persistenceFile).buffer().use { it.readUtf8() }
                    val loaded = json.decodeFromString<List<DownloadTask>>(content)
                    // Reset transient states
                    val sanitized = loaded.mapNotNull { task ->
                        val normalizedStatus = when {
                            task.status.startsWith("Done") -> "Finished"
                            else -> task.status
                        }
                        if (normalizedStatus.startsWith("Failed")) {
                            scope.launch { cleanupPartialDownloadFiles(task) }
                            return@mapNotNull null
                        }
                        if (normalizedStatus != "Finished") {
                            val almostDone = task.progress >= 0.92f ||
                                    normalizedStatus.contains("Finaliz", ignoreCase = true) ||
                                    normalizedStatus.contains("Mux", ignoreCase = true)
                            task.copy(
                                status = if (almostDone) "Almost done — tap Resume" else "Paused",
                                isPaused = true,
                            )
                        } else {
                            task.copy(status = normalizedStatus, isPaused = false)
                        }
                    }
                    tasks.value = sanitized
                }
            } catch (e: Exception) {
                println("Failed to load tasks: ${e.message}")
            }
        }
    }

    private fun saveTasks() {
        scope.launch {
            try {
                val content = json.encodeToString(tasks.value)
                KmpFileSystem.write(persistenceFile, content.encodeToByteArray())
            } catch (e: Exception) {
                println("Failed to save tasks: ${e.message}")
            }
        }
    }

    fun startDownload(
        animeId: String,
        anilistId: Int,
        episodeNumber: Int,
        title: String,
        coverImage: String?,
        server: String,
        subLang: String?,
        audioLang: String?,
        downloadFonts: Boolean,
        headers: Map<String, String>? = null,
        m3u8Url: String? = null,
        preferBatchDub: Boolean = false,
        useParallelSegments: Boolean = false,
    ) {
        val taskId = "${animeId}_$episodeNumber"
        val existing = tasks.value.find { it.id == taskId }
        when {
            existing?.isPaused == true -> {
                resumeDownload(taskId)
                return
            }

            existing != null && isDownloadFinished(existing.status) -> return
            existing != null && !existing.status.startsWith("Failed") -> return
            existing != null && existing.status.startsWith("Failed") -> {
                scope.launch {
                    removeFailedTaskSync(existing)
                    enqueueDownload(
                        taskId,
                        animeId,
                        anilistId,
                        episodeNumber,
                        title,
                        coverImage,
                        server,
                        subLang,
                        audioLang,
                        downloadFonts,
                        headers,
                        m3u8Url,
                        preferBatchDub,
                        useParallelSegments,
                    )
                }
                return
            }
        }
        enqueueDownload(
            taskId, animeId, anilistId, episodeNumber, title, coverImage,
            server, subLang, audioLang, downloadFonts, headers, m3u8Url, preferBatchDub, useParallelSegments,
        )
    }

    fun startSeasonBatchDownload(
        episodes: List<BatchEpisodeDownload>,
        server: String,
        subLang: String?,
        audioLang: String?,
        downloadFonts: Boolean,
        headers: Map<String, String>? = null,
        preferBatchDub: Boolean = false,
    ) {
        val batch = episodes
            .distinctBy { "${it.animeId}_${it.episodeNumber}" }
            .take(MAX_SEASON_BATCH_EPISODES)
        if (batch.isEmpty()) return
        scope.launch {
            val queued = batch.mapNotNull { episode ->
                val taskId = "${episode.animeId}_${episode.episodeNumber}"
                val existing = tasks.value.find { it.id == taskId }
                when {
                    existing != null && isDownloadFinished(existing.status) -> null
                    existing != null && !existing.status.startsWith("Failed") -> existing
                    else -> DownloadTask(
                        id = taskId,
                        animeId = episode.animeId,
                        title = episode.title,
                        episodeNumber = episode.episodeNumber,
                        coverImage = episode.coverImage,
                        progress = 0f,
                        status = "Queued",
                        serverId = server,
                        anilistId = episode.anilistId,
                        subLang = subLang,
                        audioLang = audioLang,
                        headers = headers,
                        preferBatchDub = preferBatchDub,
                        useParallelSegments = true,
                    )
                }
            }
            val newQueued = queued.filter { queuedTask -> tasks.value.none { it.id == queuedTask.id } }
            if (newQueued.isNotEmpty()) {
                tasks.update { current -> current + newQueued }
                saveTasks()
            }

            batch.chunked(SEASON_BATCH_DOWNLOAD_CONCURRENCY).forEach { window ->
                coroutineScope {
                    window.map { episode ->
                        async {
                            runSeasonBatchEpisode(
                                episode = episode,
                                server = server,
                                downloadFonts = downloadFonts,
                            )
                        }
                    }.awaitAll()
                }
            }
        }
    }

    private suspend fun runSeasonBatchEpisode(
        episode: BatchEpisodeDownload,
        server: String,
        downloadFonts: Boolean,
    ) {
        val taskId = "${episode.animeId}_${episode.episodeNumber}"
        val existing = tasks.value.find { it.id == taskId }
        if (existing != null && isDownloadFinished(existing.status)) return
        if (existing != null && !existing.status.startsWith("Failed") && !existing.isPaused && existing.status != "Queued") {
            waitForTaskToSettle(taskId)
            return
        }
        val task = tasks.value.find { it.id == taskId } ?: return
        updateTask(taskId) { it.copy(isPaused = false, status = "Fetching stream...", progress = 0f) }
        executeDownload(
            task.copy(isPaused = false, status = "Fetching stream..."),
            episode.anilistId,
            task.serverId ?: server,
            task.subLang,
            task.audioLang,
            downloadFonts,
            preResolvedM3u8 = null,
            preferBatchDub = task.preferBatchDub,
        )
        waitForTaskToSettle(taskId)
    }

    private fun enqueueDownload(
        taskId: String,
        animeId: String,
        anilistId: Int,
        episodeNumber: Int,
        title: String,
        coverImage: String?,
        server: String,
        subLang: String?,
        audioLang: String?,
        downloadFonts: Boolean,
        headers: Map<String, String>?,
        m3u8Url: String?,
        preferBatchDub: Boolean,
        useParallelSegments: Boolean,
    ) {
        val newTask = DownloadTask(
            id = taskId,
            animeId = animeId,
            title = title,
            episodeNumber = episodeNumber,
            coverImage = coverImage,
            progress = 0f,
            status = "Fetching stream...",
            headers = headers,
            serverId = server,
            anilistId = anilistId,
            subLang = subLang,
            audioLang = audioLang,
            streamUrl = m3u8Url,
            preferBatchDub = preferBatchDub,
            useParallelSegments = useParallelSegments,
        )
        tasks.update { it + newTask }
        saveTasks()
        executeDownload(newTask, anilistId, server, subLang, audioLang, downloadFonts, m3u8Url, preferBatchDub)
    }

    fun startMp4Download(
        animeId: String,
        episodeNumber: Int,
        title: String,
        coverImage: String?,
        mp4Url: String,
        headers: Map<String, String>,
        qualityLabel: String,
    ) {
        val taskId = "${animeId}_$episodeNumber"
        val existing = tasks.value.find { it.id == taskId }
        when {
            existing?.isPaused == true -> {
                resumeDownload(taskId)
                return
            }

            existing != null && isDownloadFinished(existing.status) -> return
            existing != null && !existing.status.startsWith("Failed") -> return
            existing != null && existing.status.startsWith("Failed") -> {
                scope.launch {
                    removeFailedTaskSync(existing)
                    enqueueMp4Download(taskId, animeId, episodeNumber, title, coverImage, mp4Url, headers)
                }
                return
            }
        }
        enqueueMp4Download(taskId, animeId, episodeNumber, title, coverImage, mp4Url, headers)
    }

    private fun enqueueMp4Download(
        taskId: String,
        animeId: String,
        episodeNumber: Int,
        title: String,
        coverImage: String?,
        mp4Url: String,
        headers: Map<String, String>,
    ) {
        val newTask = DownloadTask(
            id = taskId,
            animeId = animeId,
            title = title,
            episodeNumber = episodeNumber,
            coverImage = coverImage,
            progress = 0f,
            status = "Queued (MP4)...",
            headers = headers,
            streamUrl = mp4Url,
        )
        tasks.update { it + newTask }
        saveTasks()
        executeMp4Download(newTask, mp4Url, "")
    }

    private fun executeMp4Download(task: DownloadTask, mp4Url: String, qualityLabel: String) {
        val taskId = task.id
        val hdrs = task.headers ?: emptyMap()
        val job = scope.launch {
            try {
                val currentPath = AppComponent.settingsStore.downloadPathFlow.first()
                val baseDir = if (currentPath.isNotBlank()) currentPath else getDownloadsDirectory()
                KmpFileSystem.createDirectories(baseDir)
                val outputPath = "$baseDir/$taskId.mp4"
                val probe = probeDirectMp4(mp4Url, hdrs)
                if (!probe.isDirectMp4) {
                    abortDownload(taskId, "not a direct MP4 stream")
                    return@launch
                }
                val downloaded = downloadDirectMp4(taskId, mp4Url, hdrs, outputPath, probe.contentLength)
                if (!downloaded) return@launch
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    abortDownload(taskId, e.message ?: "unknown error")
                }
            }
        }
        updateTask(taskId) { it.copy(job = job) }
    }

    private fun premiumDownloadFallbackServers(currentApiServer: String): List<String> {
        val current = currentApiServer.lowercase()
        val available = AppComponent.serverRepository.getAvailableServers()
            .map { it.id.lowercase().removeSuffix("-dub") }
            .filter { it.isNotBlank() && it != current }
            .distinct()
        val preferred = listOf("anitaku-1", "anitaku", "anikage")
        return (preferred.filter { it in available } + available.filter { it !in preferred })
            .distinct()
    }

    private fun executeDownload(
        task: DownloadTask,
        anilistId: Int,
        server: String,
        subLang: String?,
        audioLang: String?,
        downloadFonts: Boolean,
        preResolvedM3u8: String? = null,
        preferBatchDub: Boolean = false,
    ) {
        val taskId = task.id
        val taskHeaders = task.headers
        var resolvedServer = server
        val job = scope.launch {
            try {
                val m3u8Url: String
                val currentHeaders: MutableMap<String, String>
                var apiSubtitleTracks = emptyList<Pair<String, String>>()

                if (!preResolvedM3u8.isNullOrBlank()) {
                    // Use pre-resolved M3U8 URL (quality was selected in dialog)
                    m3u8Url = preResolvedM3u8
                    currentHeaders = (taskHeaders ?: emptyMap()).toMutableMap()
                    try {
                        val legacyDub = server.endsWith("-dub", ignoreCase = true)
                        val apiServer = if (legacyDub) server.dropLast(4) else server
                        val meta = AppComponent.serverRepository.getServerById(server)
                            ?: AppComponent.serverRepository.getServerById(apiServer)
                        val useDub = when {
                            legacyDub -> true
                            meta?.type == "dub" -> true
                            meta?.type == "sub" -> false
                            else -> preferBatchDub
                        }
                        val response = infoService.getVideoStream(anilistId, task.episodeNumber, apiServer)
                        val streamData = if (useDub) response?.dub else response?.sub
                        apiSubtitleTracks = BatchSubtitleExtract.trackUrls(
                            streamData,
                            m3u8Url = preResolvedM3u8,
                        )
                    } catch (_: Exception) {
                    }
                } else {
                    val legacyDub = server.endsWith("-dub", ignoreCase = true)
                    val apiServer = if (legacyDub) server.dropLast(4) else server
                    val meta = AppComponent.serverRepository.getServerById(server)
                        ?: AppComponent.serverRepository.getServerById(apiServer)
                    val useDub = when {
                        legacyDub -> true
                        meta?.type == "dub" -> true
                        meta?.type == "sub" -> false
                        else -> preferBatchDub
                    }

                    val response = infoService.getVideoStream(anilistId, task.episodeNumber, apiServer)

                    var streamData = if (useDub) response?.dub else response?.sub
                    var streamInfo = streamData?.streams?.firstOrNull()

                    // For suzu server, fetch fresh stream URLs from the embed page
                    if (apiServer.equals("suzu", ignoreCase = true)) {
                        val embedUrl = streamData?.episodeId
                        if (!embedUrl.isNullOrBlank()) {
                            val embedStreams = infoService.fetchSuzuEmbedStreams(embedUrl)
                            if (embedStreams != null && embedStreams.isNotEmpty()) {
                                val referer = try {
                                    val schemeHost =
                                        embedUrl.substringBefore("://", "") + "://" + embedUrl.substringAfter("://")
                                            .substringBefore("/")
                                    if (schemeHost.contains("://")) schemeHost else "https://senshi.live"
                                } catch (_: Exception) {
                                    "https://senshi.live"
                                }
                                val freshStreams = embedStreams.map { embedStream ->
                                    to.kuudere.anisuge.data.models.StreamInfo(
                                        url = embedStream.url,
                                        quality = embedStream.status ?: "Auto",
                                        headers = to.kuudere.anisuge.data.models.StreamHeaders(
                                            Referer = referer
                                        )
                                    )
                                }
                                val targetStreams = if (useDub) {
                                    freshStreams.filter { it.quality.equals("Dub", ignoreCase = true) }
                                } else {
                                    freshStreams.filter { !it.quality.equals("Dub", ignoreCase = true) }
                                }
                                if (targetStreams.isNotEmpty()) {
                                    streamInfo = targetStreams.firstOrNull()
                                }
                            }
                        }
                    }

                    if (streamInfo == null && task.useParallelSegments) {
                        for (candidate in premiumDownloadFallbackServers(apiServer)) {
                            val fallbackResponse = runCatching {
                                infoService.getVideoStream(anilistId, task.episodeNumber, candidate)
                            }.getOrNull()
                            val fallbackData = if (useDub) fallbackResponse?.dub else fallbackResponse?.sub
                            val fallbackInfo = fallbackData?.streams?.firstOrNull()
                            if (fallbackInfo != null) {
                                resolvedServer = candidate
                                streamData = fallbackData
                                streamInfo = fallbackInfo
                                updateTask(taskId) { it.copy(status = "Switched to $candidate") }
                                break
                            }
                        }
                    }

                    if (streamInfo == null) {
                        abortDownload(taskId, "no stream")
                        return@launch
                    }

                    m3u8Url = streamInfo.url
                    if (m3u8Url.isBlank()) {
                        abortDownload(taskId, "no M3U8 URL")
                        return@launch
                    }

                    currentHeaders = (taskHeaders ?: emptyMap()).toMutableMap()
                    streamInfo.headers?.Referer?.let { currentHeaders["Referer"] = it }
                    streamInfo.headers?.userAgent?.let { currentHeaders["User-Agent"] = it }
                    streamInfo.headers?.Origin?.let { currentHeaders["Origin"] = it }
                    apiSubtitleTracks = BatchSubtitleExtract.trackUrls(streamData, m3u8Url = m3u8Url)
                }

                updateTask(taskId) {
                    it.copy(
                        streamUrl = m3u8Url,
                        serverId = resolvedServer,
                        anilistId = anilistId,
                        subLang = subLang,
                        audioLang = audioLang,
                        preferBatchDub = preferBatchDub,
                        headers = currentHeaders,
                        fallbackAttemptedServers = if (resolvedServer.equals(server, ignoreCase = true)) {
                            it.fallbackAttemptedServers
                        } else {
                            (it.fallbackAttemptedServers + server)
                                .map { attempted -> attempted.lowercase().removeSuffix("-dub") }
                                .distinct()
                        },
                    )
                }
                downloadLog(
                    taskId,
                    "stream server=$resolvedServer urlHost=${urlHost(m3u8Url)} parallel=${task.useParallelSegments} headers=${
                        currentHeaders.keys.joinToString(
                            ","
                        )
                    }",
                )

                // Create folder
                val currentPath = AppComponent.settingsStore.downloadPathFlow.first()
                val baseDir = if (currentPath.isNotBlank()) currentPath else getDownloadsDirectory()
                val animeSafe = task.animeId.replace("[^A-Za-z0-9]".toRegex(), "_")
                val epDir = "$baseDir/$animeSafe/ep_${task.episodeNumber}"
                KmpFileSystem.createDirectories(epDir)

                if (m3u8Url.contains(".mpd", ignoreCase = true)) {
                    abortDownload(taskId, "DASH (.mpd) download not supported yet")
                    return@launch
                }

                // Some providers return a direct video file (MP4) instead of an HLS playlist.
                val probe = probeDirectMp4(m3u8Url, currentHeaders)
                if (probe.isDirectMp4) {
                    val outputPath = "$baseDir/$taskId.mp4"
                    val downloaded = downloadDirectMp4(taskId, m3u8Url, currentHeaders, outputPath, probe.contentLength)
                    if (!downloaded) return@launch
                    return@launch
                }

                // 2. Parse master playlist (video + optional embedded subtitle tracks)
                updateTask(taskId) { it.copy(status = "Parsing playlist...") }
                val legacyForRemuxGuess = server.endsWith("-dub", ignoreCase = true)
                val apiServerForRemux = if (legacyForRemuxGuess) server.dropLast(4) else server

                val masterPlaylist = httpClient.get(m3u8Url) {
                    currentHeaders.forEach { (k, v) -> header(k, v) }
                }.bodyAsText()

                val subsToDownload = mutableListOf<Pair<String, String>>()
                updateTask(taskId) { it.copy(status = "Downloading subtitles...") }
                collectDownloadedSubtitles(
                    epDir = epDir,
                    apiSubtitleTracks = apiSubtitleTracks,
                    masterPlaylistUrl = m3u8Url,
                    masterPlaylist = masterPlaylist,
                    headers = currentHeaders,
                    out = subsToDownload,
                )

                // 3. Download Video & Audio

                val audioPlaylistUrl = parseAudioPlaylistUrl(m3u8Url, masterPlaylist, audioLang ?: "jpn")
                val videoPlaylistUrl = getFinalPlaylistUrl(m3u8Url, masterPlaylist)

                val videoPlaylistText = httpClient.get(videoPlaylistUrl) {
                    currentHeaders.forEach { (k, v) -> header(k, v) }
                }.bodyAsText()

                val isEncrypted = videoPlaylistText.contains("#EXT-X-KEY") || masterPlaylist.contains("#EXT-X-KEY")
                val fmp4LikePlaylist = videoPlaylistText.contains("#EXT-X-MAP", ignoreCase = true) ||
                        videoPlaylistText.lines().any { line ->
                            val l = line.trim()
                            l.isNotEmpty() && !l.startsWith("#") &&
                                    (l.endsWith(".m4s", ignoreCase = true) ||
                                            l.endsWith(".mp4", ignoreCase = true))
                        }

                /** fMP4/Suzu HLS: desktop muxes from playlist URLs; Android always segments + Media3 export (no native FFmpeg). */
                val useHlsUrlRemux = !isAndroidPlatform && !isEncrypted && (
                        fmp4LikePlaylist || apiServerForRemux.equals("suzu", ignoreCase = true)
                        )

                val videoSegments = if (isEncrypted || useHlsUrlRemux) emptyList<String>()
                else parseSegments(videoPlaylistUrl, videoPlaylistText)

                val audioSegments = if (audioPlaylistUrl != null) {
                    parseSegments(audioPlaylistUrl, httpClient.get(audioPlaylistUrl) {
                        currentHeaders.forEach { (k, v) -> header(k, v) }
                    }.bodyAsText())
                } else emptyList()

                if (videoSegments.isEmpty() && !isEncrypted && !useHlsUrlRemux) {
                    abortDownload(taskId, "no video segments")
                    return@launch
                }
                downloadLog(
                    taskId,
                    "playlist encrypted=$isEncrypted fmp4=$fmp4LikePlaylist urlRemux=$useHlsUrlRemux videoSegments=${videoSegments.size} audioSegments=${audioSegments.size}",
                )

                val rawVideoPath = "$epDir/video_raw.ts"
                val rawAudioPath = "$epDir/audio_raw.ts"
                val finalOutputPath = buildDownloadOutputPath(epDir, task.title, task.episodeNumber)

                var totalBytesDownloaded = 0L
                var lastMeasureTime = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
                var bytesSinceLastMeasure = 0L

                // Download Video — skip concat when FFmpeg will demux HLS from URLs (encrypted, fMP4/Suzu, …)
                if (!isEncrypted && !useHlsUrlRemux) {
                    downloadHlsSegmentsToFile(
                        taskId = taskId,
                        segments = videoSegments,
                        headers = currentHeaders,
                        outputPath = rawVideoPath,
                        statusPrefix = "Downloading Video",
                        progressOffset = 0,
                        totalSegments = videoSegments.size + audioSegments.size,
                        parallel = task.useParallelSegments,
                    ) { index, bytes, speed ->
                        totalBytesDownloaded += bytes
                        bytesSinceLastMeasure += bytes
                        val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
                        val diff = now - lastMeasureTime
                        if (diff >= 1000) {
                            val measuredSpeed =
                                if (speed > 0.0) speed else bytesSinceLastMeasure.toDouble() / (diff / 1000.0)
                            val completed = index + 1
                            val remainingBytes = (videoSegments.size + audioSegments.size - completed) *
                                    (totalBytesDownloaded.toDouble() / completed.coerceAtLeast(1))
                            updateTask(taskId) {
                                it.copy(
                                    downloadSpeed = formatSpeed(measuredSpeed),
                                    eta = formatEta(if (measuredSpeed > 0) (remainingBytes / measuredSpeed).toInt() else 0),
                                )
                            }
                            lastMeasureTime = now
                            bytesSinceLastMeasure = 0
                        }
                    }
                } else if (isEncrypted || useHlsUrlRemux) {
                    updateTask(taskId) { it.copy(status = "Downloading Stream (HLS)...", progress = 0.5f) }
                }

                // Download Audio if separate (Skip if encrypted or URL remux — FFmpeg pulls alternate audio playlists)
                if (audioSegments.isNotEmpty() && !isEncrypted && !useHlsUrlRemux) {
                    downloadHlsSegmentsToFile(
                        taskId = taskId,
                        segments = audioSegments,
                        headers = currentHeaders,
                        outputPath = rawAudioPath,
                        statusPrefix = "Downloading Audio",
                        progressOffset = videoSegments.size,
                        totalSegments = videoSegments.size + audioSegments.size,
                        parallel = task.useParallelSegments,
                    )
                }

                // 4. Muxing
                val muxStatus = if (isAndroidPlatform) "Finalizing download..." else "Muxing into MKV..."
                updateTask(taskId) { it.copy(status = muxStatus, progress = 0.99f, downloadSpeed = "", eta = "") }

                val muxVideoSource = when {
                    isEncrypted || useHlsUrlRemux -> videoPlaylistUrl
                    else -> rawVideoPath
                }
                val muxAudioSource =
                    if (isEncrypted || useHlsUrlRemux) {
                        audioPlaylistUrl?.takeIf { audioSegments.isNotEmpty() }
                    } else if (audioSegments.isNotEmpty()) {
                        rawAudioPath
                    } else null

                val preferLocalTsRemux = HlsPngTsStrip.prefersLocalSegmentMux(
                    masterUrl = m3u8Url,
                    segmentUrls = videoSegments,
                    apiServer = apiServerForRemux,
                )
                downloadLog(
                    taskId,
                    "finalising android=$isAndroidPlatform apiServer=$apiServerForRemux preferLocalTsRemux=$preferLocalTsRemux videoSource=${
                        if (muxVideoSource.startsWith(
                                "http"
                            )
                        ) "remote:${urlHost(muxVideoSource)}" else "local"
                    } audio=${muxAudioSource != null} subtitles=${subsToDownload.size}",
                )

                val muxSuccess = muxToMkv(
                    videoPath = muxVideoSource,
                    audioPath = muxAudioSource,
                    subtitles = subsToDownload,
                    fonts = emptyList(),
                    metadataPath = null,
                    outputPath = finalOutputPath,
                    inputHeaders = currentHeaders,
                    masterPlaylistUrl = m3u8Url,
                    preferLocalTsRemux = preferLocalTsRemux,
                )

                if (muxSuccess) {
                    downloadLog(taskId, "finalising ok output=$finalOutputPath")
                    // Cleanup
                    try {
                        if (!isEncrypted && !useHlsUrlRemux) {
                            KmpFileSystem.delete(rawVideoPath)
                            if (audioSegments.isNotEmpty()) KmpFileSystem.delete(rawAudioPath)
                        }
                        subsToDownload.forEach { (path, _) -> KmpFileSystem.delete(path) }
                    } catch (e: Exception) {
                    }

                    updateTask(taskId) {
                        it.copy(
                            status = "Finished",
                            progress = 1f,
                            localPath = finalOutputPath,
                            isPaused = false,
                        )
                    }
                } else {
                    downloadLog(taskId, "finalising failed output=$finalOutputPath")
                    updateTask(taskId) {
                        it.copy(
                            status = "Finished (Mux Failed)",
                            progress = 1f,
                            localPath = if (!isEncrypted && !useHlsUrlRemux) rawVideoPath else null,
                            isPaused = false,
                        )
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    val current = tasks.value.find { it.id == taskId }
                    if (current != null && !isDownloadFinished(current.status) && current.progress >= 0.5f) {
                        updateTask(taskId) { it.copy(isPaused = true, status = "Paused") }
                    }
                    return@launch
                }
                val message = e.message ?: "Unknown error"
                val reason = if (message.contains("EPERM") || message.contains("Permission denied")) {
                    "permission denied — try using the Downloads folder"
                } else {
                    message
                }
                abortDownload(taskId, reason)
            }
        }
        updateTask(taskId) { it.copy(job = job) }
    }

    private data class DirectMp4Probe(
        val isDirectMp4: Boolean,
        val contentLength: Long,
    )

    private fun urlPathEndsWithMp4(url: String): Boolean {
        val normalized = url.substringBefore('#').substringBefore('?').lowercase()
        return normalized.endsWith(".mp4")
    }

    private suspend fun probeDirectMp4(url: String, headers: Map<String, String>): DirectMp4Probe {
        val byPath = urlPathEndsWithMp4(url)
        return try {
            val response = httpClient.head(url) {
                headers.forEach { (k, v) -> header(k, v) }
            }
            val contentType = response.headers["Content-Type"]
                ?.substringBefore(';')
                ?.trim()
                ?.lowercase()
                .orEmpty()
            val byContentType =
                contentType == "video/mp4" || contentType == "application/octet-stream"
            val contentLength = response.headers["Content-Length"]?.toLongOrNull()?.takeIf { it > 0L } ?: -1L
            DirectMp4Probe(isDirectMp4 = byPath || byContentType, contentLength = contentLength)
        } catch (_: Exception) {
            DirectMp4Probe(isDirectMp4 = byPath, contentLength = -1L)
        }
    }

    private suspend fun downloadDirectMp4(
        taskId: String,
        url: String,
        headers: Map<String, String>,
        outputPath: String,
        contentLengthFromHead: Long,
    ): Boolean {
        updateTask(taskId) { it.copy(status = "Preparing direct MP4...", progress = 0f) }
        return try {
            var httpOk = false
            httpClient.prepareGet(url) {
                headers.forEach { (k, v) -> header(k, v) }
            }.execute { response ->
                if (!response.status.isSuccess()) {
                    abortDownload(taskId, "HTTP ${response.status.value}")
                    return@execute
                }
                httpOk = true
                val expectedLength = if (contentLengthFromHead > 0L) {
                    contentLengthFromHead
                } else {
                    response.headers["Content-Length"]?.toLongOrNull()?.takeIf { it > 0L } ?: -1L
                }

                val sink = KmpFileSystem.sink(outputPath).buffer()
                val channel = response.bodyAsChannel()
                val buffer = ByteArray(MP4_BUFFER_SIZE)
                var bytesDownloaded = 0L
                var lastPercent = -1
                var lastUiUpdateAt = 0L

                try {
                    while (true) {
                        while (tasks.value.find { it.id == taskId }?.isPaused == true) {
                            kotlinx.coroutines.delay(1000)
                        }

                        val read = channel.readAvailable(buffer)
                        if (read == -1) break
                        if (read == 0) {
                            kotlinx.coroutines.delay(8)
                            continue
                        }

                        sink.write(buffer, offset = 0, byteCount = read)
                        bytesDownloaded += read

                        val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
                        if (expectedLength > 0L) {
                            val percent = ((bytesDownloaded * 100) / expectedLength).toInt().coerceIn(0, 100)
                            if (percent != lastPercent && (now - lastUiUpdateAt) >= 200L) {
                                updateTask(taskId, persist = false) {
                                    it.copy(
                                        progress = percent / 100f,
                                        status = "Downloading... $percent%",
                                    )
                                }
                                lastPercent = percent
                                lastUiUpdateAt = now
                            }
                        } else if ((now - lastUiUpdateAt) >= 500L) {
                            updateTask(taskId, persist = false) { it.copy(status = "Downloading...") }
                            lastUiUpdateAt = now
                        }
                    }
                } finally {
                    sink.close()
                }
            }

            if (!httpOk) return false

            updateTask(taskId) {
                it.copy(
                    progress = 1f,
                    status = "Finished",
                    localPath = outputPath,
                    downloadSpeed = "",
                    eta = "",
                    isPaused = false,
                )
            }
            true
        } catch (e: Exception) {
            abortDownload(taskId, e.message ?: "unknown error")
            false
        }
    }

    private fun parseAudioPlaylistUrl(baseUrl: String, content: String, targetLang: String): String? {
        val lines = content.lines()
        val mediaLine = lines.find {
            it.startsWith("#EXT-X-MEDIA:TYPE=AUDIO") && (it.contains("LANGUAGE=\"$targetLang\"") || it.contains(
                "LANGUAGE=\"$targetLang-",
                true
            ))
        }
            ?: lines.find { it.startsWith("#EXT-X-MEDIA:TYPE=AUDIO") && it.contains("DEFAULT=YES") }

        if (mediaLine != null) {
            val uriMatch = Regex("URI=\"([^\"]+)\"").find(mediaLine)
            val uri = uriMatch?.groupValues?.get(1)
            if (uri != null) {
                return HlsPngTsStrip.resolvePlaylistUrl(baseUrl, uri)
            }
        }
        return null
    }

    private fun getFinalPlaylistUrl(baseUrl: String, content: String): String {
        if (content.contains("#EXT-X-STREAM-INF")) {
            val lines = content.lines()
            val variantLine = lines.firstOrNull { it.isNotBlank() && !it.startsWith("#") }
            if (variantLine != null) {
                return HlsPngTsStrip.resolvePlaylistUrl(baseUrl, variantLine)
            }
        }
        return baseUrl
    }

    private suspend fun fetchHlsSegmentBytes(
        segmentUrl: String,
        headers: Map<String, String>,
    ): ByteArray {
        val requestHeaders = headers.toMutableMap()
        if (!requestHeaders.containsKey("Origin")) {
            requestHeaders["Referer"]?.let { requestHeaders["Origin"] = it }
        }
        val raw = httpClient.get(segmentUrl) {
            requestHeaders.forEach { (k, v) -> header(k, v) }
        }.readBytes()
        return HlsPngTsStrip.stripSegmentPayloadIfNeeded(segmentUrl, raw)
    }

    private suspend fun downloadHlsSegmentsToFile(
        taskId: String,
        segments: List<String>,
        headers: Map<String, String>,
        outputPath: String,
        statusPrefix: String,
        progressOffset: Int,
        totalSegments: Int,
        parallel: Boolean,
        onSegmentWritten: (index: Int, bytes: Long, speed: Double) -> Unit = { _, _, _ -> },
    ) {
        if (segments.isEmpty()) return
        val concurrency =
            if (!parallel) 1 else if (isAndroidPlatform) ANDROID_HLS_PARALLELISM else DESKTOP_HLS_PARALLELISM
        val semaphore = Semaphore(concurrency)
        val sink = KmpFileSystem.sink(outputPath).buffer()
        var lastMeasureTime = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        var bytesSinceLastMeasure = 0L
        try {
            var index = 0
            while (index < segments.size) {
                while (tasks.value.find { it.id == taskId }?.isPaused == true) {
                    kotlinx.coroutines.delay(1000)
                    lastMeasureTime = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
                    bytesSinceLastMeasure = 0
                }

                val window = segments.drop(index).take(concurrency)
                val fetched = coroutineScope {
                    window.mapIndexed { windowIndex, segmentUrl ->
                        async {
                            semaphore.withPermit {
                                index + windowIndex to fetchHlsSegmentBytes(segmentUrl, headers)
                            }
                        }
                    }.awaitAll()
                }.sortedBy { it.first }

                for ((segmentIndex, bytes) in fetched) {
                    while (tasks.value.find { it.id == taskId }?.isPaused == true) {
                        kotlinx.coroutines.delay(1000)
                        lastMeasureTime = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
                        bytesSinceLastMeasure = 0
                    }
                    sink.write(bytes)
                    bytesSinceLastMeasure += bytes.size

                    val completed = progressOffset + segmentIndex + 1
                    val progress = completed.toFloat() / totalSegments.coerceAtLeast(1)
                    val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
                    val diff = now - lastMeasureTime
                    val speed = if (diff >= 1000) {
                        (bytesSinceLastMeasure.toDouble() / (diff / 1000.0)).also {
                            lastMeasureTime = now
                            bytesSinceLastMeasure = 0
                        }
                    } else {
                        0.0
                    }
                    updateTask(taskId, persist = false) {
                        it.copy(
                            status = "$statusPrefix: ${(progress * 100).toInt()}%",
                            progress = progress,
                        )
                    }
                    onSegmentWritten(segmentIndex, bytes.size.toLong(), speed)
                }
                index += window.size
            }
        } finally {
            sink.close()
        }
    }

    private fun parseSegments(playlistUrl: String, content: String): List<String> {
        val segments = mutableListOf<String>()
        content.lines().forEach { line ->
            if (line.startsWith("#EXT-X-MAP", ignoreCase = true)) {
                val uri = Regex("URI=\"([^\"]+)\"").find(line)?.groupValues?.getOrNull(1)
                if (uri != null) {
                    segments.add(HlsPngTsStrip.resolvePlaylistUrl(playlistUrl, uri))
                }
            }
        }
        content.lines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .forEach { line ->
                segments.add(HlsPngTsStrip.resolvePlaylistUrl(playlistUrl, line))
            }
        return segments
    }

    private fun parseSubtitlePlaylistUrls(baseUrl: String, content: String): List<Pair<String, String>> {
        val results = mutableListOf<Pair<String, String>>()
        content.lines().forEach { line ->
            if (!line.startsWith("#EXT-X-MEDIA:TYPE=SUBTITLES", ignoreCase = true)) return@forEach
            val uri = Regex("URI=\"([^\"]+)\"").find(line)?.groupValues?.getOrNull(1) ?: return@forEach
            val name = Regex("NAME=\"([^\"]+)\"").find(line)?.groupValues?.get(1) ?: "Subtitles"
            results.add(HlsPngTsStrip.resolvePlaylistUrl(baseUrl, uri) to name)
        }
        return results
    }

    private suspend fun collectDownloadedSubtitles(
        epDir: String,
        apiSubtitleTracks: List<Pair<String, String>>,
        masterPlaylistUrl: String,
        masterPlaylist: String,
        headers: Map<String, String>,
        out: MutableList<Pair<String, String>>,
    ) {
        val seen = mutableSetOf<String>()
        suspend fun addFromUrl(url: String, label: String) {
            val normalized = url.trim()
            if (normalized.isBlank() || normalized in seen) return
            seen.add(normalized)
            val saved = downloadSubtitleAsset(epDir, normalized, label, headers) ?: return
            out.add(saved)
        }

        apiSubtitleTracks.forEach { (url, label) -> addFromUrl(url, label) }
        parseSubtitlePlaylistUrls(masterPlaylistUrl, masterPlaylist).forEach { (url, name) ->
            addFromUrl(url, name)
        }
    }

    private suspend fun waitForTaskToSettle(taskId: String) {
        while (true) {
            val task = tasks.value.find { it.id == taskId }
            if (task == null || isDownloadFinished(task.status) || task.isPaused) return
            kotlinx.coroutines.delay(1000)
        }
    }

    private suspend fun downloadSubtitleAsset(
        epDir: String,
        url: String,
        label: String,
        headers: Map<String, String>,
    ): Pair<String, String>? {
        val requestHeaders = headers.toMutableMap()
        if (!requestHeaders.containsKey("Origin")) {
            requestHeaders["Referer"]?.let { requestHeaders["Origin"] = it }
        }
        return try {
            val rawBytes = httpClient.get(url) {
                requestHeaders.forEach { (k, v) -> header(k, v) }
            }.readBytes()
            val head = rawBytes.decodeToString(0, minOf(rawBytes.size, 64))
            if (head.contains("#EXTM3U", ignoreCase = true)) {
                val bodyText = rawBytes.decodeToString()
                val segments = parseSegments(url, bodyText)
                if (segments.isEmpty()) return null
                val merged = buildString {
                    segments.forEach { segmentUrl ->
                        val part = httpClient.get(segmentUrl) {
                            requestHeaders.forEach { (k, v) -> header(k, v) }
                        }.bodyAsText()
                        append(part.trim())
                        append('\n')
                    }
                }
                val safeLabel = label.replace("[^A-Za-z0-9_]".toRegex(), "_").ifBlank { "Default" }
                val subFile = "$epDir/subtitle_$safeLabel.vtt"
                KmpFileSystem.write(subFile, merged.encodeToByteArray())
                subFile to label
            } else {
                val ext = when {
                    url.contains(".vtt", ignoreCase = true) || head.startsWith("WEBVTT") -> "vtt"
                    url.contains(".srt", ignoreCase = true) -> "srt"
                    else -> "ass"
                }
                val safeLabel = label.replace("[^A-Za-z0-9_]".toRegex(), "_").ifBlank { "Default" }
                val subFile = "$epDir/subtitle_$safeLabel.$ext"
                KmpFileSystem.write(subFile, rawBytes)
                subFile to label
            }
        } catch (e: Exception) {
            println("[Download] subtitle fetch failed ($label): ${e.message}")
            null
        }
    }

    private fun formatSpeed(bytesPerSec: Double): String {
        return when {
            bytesPerSec >= 1024 * 1024 -> "${formatFloat(bytesPerSec / (1024 * 1024), 1)} MB/s"
            bytesPerSec >= 1024 -> "${formatFloat(bytesPerSec / 1024, 1)} KB/s"
            else -> "${formatFloat(bytesPerSec, 0)} B/s"
        }
    }

    private fun formatEta(seconds: Int): String {
        return when {
            seconds >= 3600 -> "${seconds / 3600}h ${(seconds % 3600) / 60}m left"
            seconds >= 60 -> "${seconds / 60}m ${seconds % 60}s left"
            else -> "${seconds}s left"
        }
    }

    fun pauseDownload(id: String) {
        val task = tasks.value.find { it.id == id } ?: return
        task.job?.cancel()
        updateTask(id) { it.copy(isPaused = true, status = "Paused", job = null) }
    }

    fun resumeDownload(id: String) {
        val task = tasks.value.find { it.id == id } ?: return
        if (isDownloadFinished(task.status)) return
        if (task.job?.isActive == true) {
            updateTask(id) { it.copy(isPaused = false, status = "Resuming...") }
            return
        }
        val streamUrl = task.streamUrl
        if (streamUrl.isNullOrBlank()) {
            updateTask(id) { it.copy(isPaused = true, status = "Paused — restart from episode") }
            return
        }
        updateTask(id) { it.copy(isPaused = false, status = "Resuming...") }
        val resumed = task.copy(isPaused = false, status = "Resuming...")
        val mp4Only = streamUrl.contains(".mp4", ignoreCase = true) ||
                streamUrl.contains("fast4speed", ignoreCase = true)
        if (mp4Only && !streamUrl.contains(".m3u8", ignoreCase = true) && !streamUrl.contains(
                ".mpd",
                ignoreCase = true
            )
        ) {
            executeMp4Download(resumed, streamUrl, "")
        } else {
            val server = task.serverId ?: return
            val anilist = task.anilistId ?: return
            executeDownload(
                resumed,
                anilist,
                server,
                task.subLang,
                task.audioLang,
                downloadFonts = true,
                preResolvedM3u8 = streamUrl,
                preferBatchDub = task.preferBatchDub,
            )
        }
    }

    fun cancelDownload(id: String) {
        removeTask(id)
    }

    fun removeTask(id: String) {
        val task = tasks.value.find { it.id == id } ?: return
        task.job?.cancel()
        tasks.update { it.filterNot { t -> t.id == id } }
        saveTasks()
        scope.launch { cleanupPartialDownloadFiles(task) }
    }

    fun isDownloadFinished(status: String): Boolean =
        status == "Finished" || status.startsWith("Finished (") || status.startsWith("Done")

    fun countFinishedDownloads(tasks: List<DownloadTask> = this.tasks.value): Int =
        tasks.count { isDownloadFinished(it.status) }

    /** Drop failed task from UI/storage and delete partial files so the next download starts clean. */
    private fun abortDownload(taskId: String, reason: String) {
        val task = tasks.value.find { it.id == taskId } ?: return
        val nextServer = nextPremiumDownloadFallback(task)
        if (nextServer != null && task.anilistId != null) {
            scope.launch {
                cleanupPartialDownloadFiles(task)
                updateTask(taskId) {
                    it.copy(
                        status = "Retrying on $nextServer...",
                        progress = 0f,
                        downloadSpeed = "",
                        eta = "",
                        localPath = null,
                        streamUrl = null,
                        headers = null,
                        serverId = nextServer,
                        fallbackAttemptedServers =
                            (it.fallbackAttemptedServers + listOfNotNull(task.serverId))
                                .map { server -> server.lowercase().removeSuffix("-dub") }
                                .distinct(),
                    )
                }
                val retryTask = tasks.value.find { it.id == taskId } ?: return@launch
                executeDownload(
                    retryTask,
                    task.anilistId,
                    nextServer,
                    task.subLang,
                    task.audioLang,
                    downloadFonts = true,
                    preResolvedM3u8 = null,
                    preferBatchDub = task.preferBatchDub,
                )
                println("[Download] premium fallback for $taskId: $reason -> $nextServer")
            }
            return
        }
        scope.launch {
            removeFailedTaskSync(task)
            println("[Download] removed failed task $taskId: $reason")
        }
    }

    private fun nextPremiumDownloadFallback(task: DownloadTask): String? {
        if (!task.useParallelSegments) return null
        val current = task.serverId?.lowercase()?.removeSuffix("-dub") ?: return null
        val tried = (task.fallbackAttemptedServers + current)
            .map { it.lowercase().removeSuffix("-dub") }
            .toSet()
        return premiumDownloadFallbackServers(current).firstOrNull { it !in tried }
    }

    private suspend fun removeFailedTaskSync(task: DownloadTask) {
        task.job?.cancel()
        cleanupPartialDownloadFiles(task)
        tasks.update { list -> list.filterNot { it.id == task.id } }
        saveTasks()
    }

    private suspend fun cleanupPartialDownloadFiles(task: DownloadTask) {
        try {
            val currentPath = AppComponent.settingsStore.downloadPathFlow.first()
            val baseDir = if (currentPath.isNotBlank()) currentPath else getDownloadsDirectory()
            val taskId = task.id
            val mp4Path = "$baseDir/$taskId.mp4"
            if (KmpFileSystem.exists(mp4Path)) {
                KmpFileSystem.delete(mp4Path)
            }
            val animeSafe = task.animeId.replace("[^A-Za-z0-9]".toRegex(), "_")
            val epDir = "$baseDir/$animeSafe/ep_${task.episodeNumber}"
            deleteDirectoryContentsRecursively(epDir)
            task.localPath?.let { path ->
                if (path != mp4Path && KmpFileSystem.exists(path)) {
                    KmpFileSystem.delete(path)
                }
            }
        } catch (e: Exception) {
            println("[Download] cleanup partial files: ${e.message}")
        }
    }

    private fun deleteDirectoryContentsRecursively(dir: String) {
        if (!KmpFileSystem.exists(dir)) return
        try {
            KmpFileSystem.listDir(dir).forEach { name ->
                val child = "$dir/$name"
                try {
                    deleteDirectoryContentsRecursively(child)
                } catch (_: Exception) {
                    try {
                        KmpFileSystem.delete(child)
                    } catch (_: Exception) {
                    }
                }
            }
        } catch (_: Exception) {
        }
    }


    private fun updateTask(
        id: String,
        persist: Boolean = true,
        update: (DownloadTask) -> DownloadTask,
    ) {
        tasks.update { list ->
            list.map { if (it.id == id) update(it) else it }
        }
        if (persist) saveTasks()
    }
}

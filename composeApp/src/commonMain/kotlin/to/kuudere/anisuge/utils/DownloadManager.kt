package to.kuudere.anisuge.utils

import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.head
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
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
import kotlinx.coroutines.withTimeoutOrNull
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
    val localSizeBytes: Long = 0L,
    val sidecarPaths: List<String> = emptyList(),
    val workDir: String? = null,
    val isPaused: Boolean = false,
    val headers: Map<String, String>? = null,
    /** Persisted so Resume can restart the coroutine after pause or process death. */
    val serverId: String? = null,
    val anilistId: Int? = null,
    val selectedSubtitleLabels: List<String> = emptyList(),
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
    private const val MP4_BUFFER_SIZE = 256 * 1024
    private const val DIRECT_MP4_PROBE_TIMEOUT_MS = 8_000L
    private const val DIRECT_MP4_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
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
                        if (!isDownloadFinished(normalizedStatus)) {
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
        subtitleLabels: List<String>,
        audioLang: String?,
        downloadFonts: Boolean,
        headers: Map<String, String>? = null,
        m3u8Url: String? = null,
        preferBatchDub: Boolean = false,
        useParallelSegments: Boolean = false,
    ) {
        println("[DownloadManager] startDownload called: server=$server subtitleLabels=$subtitleLabels preferBatchDub=$preferBatchDub")
        val taskId = "${animeId}_$episodeNumber"
        val existing = tasks.value.find { it.id == taskId }
        when {
            existing?.isPaused == true -> {
                resumeDownload(taskId)
                return
            }

            existing != null && !existing.status.startsWith("Failed") && !isDownloadFinished(existing.status) -> {
                // Download is in progress, don't restart
                return
            }

            existing != null && isDownloadFinished(existing.status) -> {
                // Preserve completed files by default. Replacement can be added as an explicit flow later.
                return
            }

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
                        subtitleLabels,
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
            server, subtitleLabels, audioLang, downloadFonts, headers, m3u8Url, preferBatchDub, useParallelSegments,
        )
    }

    fun startSeasonBatchDownload(
        episodes: List<BatchEpisodeDownload>,
        server: String,
        subtitleLabels: List<String>,
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
                        selectedSubtitleLabels = subtitleLabels,
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
            task.selectedSubtitleLabels,
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
        subtitleLabels: List<String>,
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
            selectedSubtitleLabels = subtitleLabels,
            audioLang = audioLang,
            streamUrl = m3u8Url,
            preferBatchDub = preferBatchDub,
            useParallelSegments = useParallelSegments,
        )
        tasks.update { it + newTask }
        saveTasks()
        executeDownload(newTask, anilistId, server, subtitleLabels, audioLang, downloadFonts, m3u8Url, preferBatchDub)
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

            existing != null && !existing.status.startsWith("Failed") && !isDownloadFinished(existing.status) -> {
                // Download is in progress, don't restart
                return
            }

            existing != null && isDownloadFinished(existing.status) -> {
                // Preserve completed files by default. Replacement can be added as an explicit flow later.
                return
            }

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
        val hdrs = directMp4Headers(task.headers ?: emptyMap())
        val job = scope.launch {
            try {
                val workDir = prepareTaskWorkDir(taskId, task.workDir)
                val outputPath = "$workDir/${buildCompletedFileName(task.title, task.episodeNumber, "mp4")}"
                updateTask(taskId) {
                    it.copy(
                        status = "Checking direct MP4...",
                        progress = 0f,
                        headers = hdrs,
                    )
                }
                downloadLog(taskId, "direct MP4 probe host=${urlHost(mp4Url)} headers=${hdrs.keys.joinToString(",")}")
                val probe = probeDirectMp4(mp4Url, hdrs)
                if (!probe.isDirectMp4) {
                    abortDownload(taskId, "not a direct MP4 stream")
                    return@launch
                }
                val downloaded = downloadDirectMp4(taskId, mp4Url, hdrs, outputPath, probe.contentLength)
                if (!downloaded) return@launch
                completeDownloadedFile(
                    taskId = taskId,
                    videoPath = outputPath,
                    videoMimeType = "video/mp4",
                    sidecarPaths = emptyList(),
                )
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    abortDownload(taskId, e.message ?: "unknown error")
                }
            }
        }
        updateTask(taskId) { it.copy(job = job) }
    }

    private fun executeDownload(
        task: DownloadTask,
        anilistId: Int,
        server: String,
        subtitleLabels: List<String>,
        audioLang: String?,
        downloadFonts: Boolean,
        preResolvedM3u8: String? = null,
        preferBatchDub: Boolean = false,
    ) {
        val taskId = task.id
        val taskHeaders = task.headers
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

                    val streamData = if (useDub) response?.dub else response?.sub
                    var streamInfo = streamData?.streams?.firstOrNull()

                    // Modern Suzu returns direct ninstream HLS with required senshi.live
                    // headers from batch_scrape. Only fall back to the old embed refresh
                    // when the API did not provide a playable stream.
                    if (apiServer.equals("suzu", ignoreCase = true) && streamInfo == null) {
                        val embedUrl = streamData?.episodeId
                        if (!embedUrl.isNullOrBlank() && embedUrl.startsWith("http", ignoreCase = true)) {
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
                        serverId = server,
                        anilistId = anilistId,
                        selectedSubtitleLabels = subtitleLabels,
                        audioLang = audioLang,
                        preferBatchDub = preferBatchDub,
                        headers = currentHeaders,
                    )
                }
                downloadLog(
                    taskId,
                    "stream server=$server urlHost=${urlHost(m3u8Url)} parallel=${task.useParallelSegments} headers=${
                        currentHeaders.keys.joinToString(
                            ","
                        )
                    }",
                )

                // All partial files stay in app-owned work storage until the final publish succeeds.
                val workDir = prepareTaskWorkDir(taskId, task.workDir)
                val epDir = workDir

                if (m3u8Url.contains(".mpd", ignoreCase = true)) {
                    abortDownload(taskId, "DASH (.mpd) download not supported yet")
                    return@launch
                }

                // Some providers return a direct video file (MP4) instead of an HLS playlist.
                val probe = probeDirectMp4(m3u8Url, currentHeaders)
                if (probe.isDirectMp4) {
                    val outputPath = "$workDir/${buildCompletedFileName(task.title, task.episodeNumber, "mp4")}"
                    val downloaded = downloadDirectMp4(taskId, m3u8Url, currentHeaders, outputPath, probe.contentLength)
                    if (!downloaded) return@launch
                    completeDownloadedFile(
                        taskId = taskId,
                        videoPath = outputPath,
                        videoMimeType = "video/mp4",
                        sidecarPaths = emptyList(),
                    )
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
                val filteredSubtitleTracks = if (subtitleLabels.isNotEmpty()) {
                    apiSubtitleTracks.filter { (_, label) -> label in subtitleLabels }
                } else apiSubtitleTracks
                collectDownloadedSubtitles(
                    epDir = epDir,
                    apiSubtitleTracks = filteredSubtitleTracks,
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

                // 4. Muxing. On Android, encrypted/remote HLS is downloaded by FFmpeg during
                // this step, so do not present it as nearly finished before bytes are pulled.
                val remoteHlsExport = isAndroidPlatform && (isEncrypted || useHlsUrlRemux)
                val muxStatus = when {
                    remoteHlsExport -> "Downloading encrypted stream..."
                    isAndroidPlatform -> "Finalizing download..."
                    else -> "Muxing into MKV..."
                }
                val muxProgress = if (remoteHlsExport) 0.55f else 0.99f
                updateTask(taskId) { it.copy(status = muxStatus, progress = muxProgress, downloadSpeed = "", eta = "") }

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
                val remoteHlsDurationSeconds = if (remoteHlsExport) {
                    playlistDurationSeconds(videoPlaylistText).takeIf { it > 0.0 }
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
                    remoteHlsDurationSeconds = remoteHlsDurationSeconds,
                    onRemoteHlsProgress = if (remoteHlsExport) { progressTimeMs, durationSeconds ->
                        val durationMs = ((durationSeconds ?: 0.0) * 1000.0).toLong().coerceAtLeast(1L)
                        val fraction = (progressTimeMs.toDouble() / durationMs.toDouble()).toFloat().coerceIn(0f, 1f)
                        val progress = 0.55f + (fraction * 0.43f)
                        val percent = (progress * 100).toInt().coerceIn(55, 98)
                        updateTask(taskId, persist = false) {
                            it.copy(
                                status = "Downloading encrypted stream: $percent%",
                                progress = progress.coerceIn(0.55f, 0.98f),
                            )
                        }
                    } else null,
                )

                if (muxSuccess) {
                    downloadLog(taskId, "finalising ok output=$finalOutputPath")
                    completeDownloadedFile(
                        taskId = taskId,
                        videoPath = finalOutputPath,
                        videoMimeType = "video/x-matroska",
                        sidecarPaths = subsToDownload.map { it.first },
                    )
                } else {
                    downloadLog(taskId, "finalising failed output=$finalOutputPath")
                    abortDownload(taskId, "finalizing failed")
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

    private fun prepareTaskWorkDir(taskId: String, existingWorkDir: String?): String {
        val workDir = existingWorkDir?.takeIf { it.isNotBlank() } ?: getDownloadWorkDirectory(taskId)
        KmpFileSystem.createDirectories(workDir)
        updateTask(taskId) {
            it.copy(
                workDir = workDir,
                localPath = null,
                localSizeBytes = 0L,
                sidecarPaths = emptyList(),
            )
        }
        return workDir
    }

    private suspend fun completeDownloadedFile(
        taskId: String,
        videoPath: String,
        videoMimeType: String,
        sidecarPaths: List<String>,
    ) {
        val task = tasks.value.find { it.id == taskId } ?: return
        val downloadRoot = AppComponent.settingsStore.downloadPathFlow.first()
        val videoFileName = videoPath.substringAfterLast('/').ifBlank {
            buildCompletedFileName(task.title, task.episodeNumber, videoPath.substringAfterLast('.', "mkv"))
        }
        val sizeBytes = fileSize(videoPath)
        val publishedVideo = publishCompletedDownloadFile(
            tempPath = videoPath,
            fileName = videoFileName,
            mimeType = videoMimeType,
            animeId = task.animeId,
            episodeNumber = task.episodeNumber,
            downloadRoot = downloadRoot,
        )
        if (publishedVideo == null) {
            abortDownload(taskId, "failed to publish final file")
            return
        }

        val publishedSidecars = sidecarPaths.mapNotNull { sidecar ->
            val name = sidecar.substringAfterLast('/').ifBlank { "subtitle.vtt" }
            publishCompletedDownloadFile(
                tempPath = sidecar,
                fileName = name,
                mimeType = subtitleMimeType(name),
                animeId = task.animeId,
                episodeNumber = task.episodeNumber,
                downloadRoot = downloadRoot,
            )
        }

        task.workDir?.let { deleteDownloadWorkDirectory(it) }

        updateTask(taskId) {
            it.copy(
                status = "Finished",
                progress = 1f,
                localPath = publishedVideo,
                localSizeBytes = sizeBytes,
                sidecarPaths = publishedSidecars,
                isPaused = false,
                downloadSpeed = "",
                eta = "",
                job = null,
            )
        }
    }

    private fun buildCompletedFileName(title: String, episodeNumber: Int, extension: String): String {
        val safeTitle = title.replace("[^A-Za-z0-9 ]".toRegex(), "").trim().ifBlank { "Episode" }
        val ext = extension.trimStart('.').ifBlank { "mkv" }
        return "${safeTitle}_Ep_$episodeNumber.$ext"
    }

    private fun subtitleMimeType(fileName: String): String {
        return when (fileName.substringAfterLast('.', "").lowercase()) {
            "vtt" -> "text/vtt"
            "srt" -> "application/x-subrip"
            "ass", "ssa" -> "text/plain"
            else -> "application/octet-stream"
        }
    }

    private data class DirectMp4Probe(
        val isDirectMp4: Boolean,
        val contentLength: Long,
    )

    private fun urlPathEndsWithMp4(url: String): Boolean {
        val normalized = url.substringBefore('#').substringBefore('?').lowercase()
        return normalized.endsWith(".mp4")
    }

    private fun directMp4Headers(headers: Map<String, String>): Map<String, String> {
        if (headers.keys.any { it.equals("User-Agent", ignoreCase = true) }) return headers
        return headers + ("User-Agent" to DIRECT_MP4_USER_AGENT)
    }

    private suspend fun probeDirectMp4(url: String, headers: Map<String, String>): DirectMp4Probe {
        val byPath = urlPathEndsWithMp4(url)
        return try {
            withTimeoutOrNull(DIRECT_MP4_PROBE_TIMEOUT_MS) {
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
            } ?: DirectMp4Probe(isDirectMp4 = byPath, contentLength = -1L)
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
            downloadLog(taskId, "direct MP4 GET start host=${urlHost(url)}")
            httpClient.prepareGet(url) {
                timeout {
                    requestTimeoutMillis = Long.MAX_VALUE
                }
                headers.forEach { (k, v) -> header(k, v) }
            }.execute { response ->
                downloadLog(taskId, "direct MP4 GET status=${response.status.value}")
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

            updateTask(taskId) { it.copy(progress = 0.99f, status = "Finalizing download...") }
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

    private fun playlistDurationSeconds(content: String): Double {
        return content.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("#EXTINF:", ignoreCase = true) }
            .sumOf { line ->
                line.substringAfter(':')
                    .substringBefore(',')
                    .trim()
                    .toDoubleOrNull() ?: 0.0
            }
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
        }.readRawBytes()
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
            }.readRawBytes()
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
                task.selectedSubtitleLabels,
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
        if (!isDownloadFinished(task.status)) {
            scope.launch { cleanupPartialDownloadFiles(task) }
        }
    }

    fun deleteTaskFiles(id: String) {
        scope.launch { deleteTaskFilesSync(id) }
    }

    suspend fun deleteTaskFilesSync(id: String): Boolean {
        val task = tasks.value.find { it.id == id } ?: return true
        task.job?.cancel()
        cleanupPartialDownloadFiles(task)
        val targets = listOfNotNull(task.localPath) + task.sidecarPaths
        val deleted = targets.fold(true) { ok, path -> deleteDownloadedFile(path) && ok }
        tasks.update { it.filterNot { t -> t.id == id } }
        saveTasks()
        return deleted
    }

    fun isDownloadFinished(status: String): Boolean =
        status == "Finished" || status.startsWith("Finished (") || status.startsWith("Done")

    fun countFinishedDownloads(tasks: List<DownloadTask> = this.tasks.value): Int =
        tasks.count { isDownloadFinished(it.status) }

    /** Drop failed task from UI/storage and delete partial files so the next download starts clean. */
    private fun abortDownload(taskId: String, reason: String) {
        val task = tasks.value.find { it.id == taskId } ?: return
        scope.launch {
            removeFailedTaskSync(task)
            println("[Download] removed failed task $taskId: $reason")
        }
    }

    private suspend fun removeFailedTaskSync(task: DownloadTask) {
        task.job?.cancel()
        cleanupPartialDownloadFiles(task)
        tasks.update { list -> list.filterNot { it.id == task.id } }
        saveTasks()
    }

    private suspend fun cleanupPartialDownloadFiles(task: DownloadTask) {
        try {
            task.workDir?.takeIf { it.isNotBlank() }?.let { deleteDownloadWorkDirectory(it) }
        } catch (e: Exception) {
            println("[Download] cleanup partial files: ${e.message}")
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

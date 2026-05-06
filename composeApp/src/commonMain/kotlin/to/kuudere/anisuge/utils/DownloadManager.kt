package to.kuudere.anisuge.utils

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readBytes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okio.buffer
import to.kuudere.anisuge.AppComponent
import to.kuudere.anisuge.platform.KmpFileSystem
import to.kuudere.anisuge.data.models.BatchScrapeResponse
import kotlinx.coroutines.Job
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
    @kotlinx.serialization.Transient internal var job: Job? = null
)

object DownloadManager {
    val tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    private val scope = CoroutineScope(Dispatchers.IO)
    private val httpClient = AppComponent.httpClient
    private val infoService = AppComponent.infoService
    
    private val persistenceFile = "${getCacheDirectory()}/tasks.json"
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

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
                    val content = java.io.File(persistenceFile).readText()
                    val loaded = json.decodeFromString<List<DownloadTask>>(content)
                    // Reset transient states
                    val sanitized = loaded.map { 
                        if (it.status != "Finished" && !it.status.startsWith("Failed")) {
                            it.copy(status = "Paused", isPaused = true)
                        } else it
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
                KmpFileSystem.write(persistenceFile, content.toByteArray())
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
        m3u8Url: String? = null
    ) {
        val taskId = "${animeId}_$episodeNumber"
        val existing = tasks.value.find { it.id == taskId }
        if (existing != null) {
            if (existing.isPaused || existing.status.startsWith("Failed")) resumeDownload(taskId)
            return
        }

        val newTask = DownloadTask(taskId, animeId, title, episodeNumber, coverImage, 0f, "Fetching stream...", headers = headers)
        tasks.update { it + newTask }
        saveTasks()

        executeDownload(newTask, anilistId, server, subLang, audioLang, downloadFonts, m3u8Url)
    }

    private fun executeDownload(
        task: DownloadTask,
        anilistId: Int,
        server: String,
        subLang: String?,
        audioLang: String?,
        downloadFonts: Boolean,
        preResolvedM3u8: String? = null
    ) {
        val taskId = task.id
        val taskHeaders = task.headers
        val job = scope.launch {
            try {
                val m3u8Url: String
                val currentHeaders: MutableMap<String, String>
                var subtitlesUrl: String? = null

                if (!preResolvedM3u8.isNullOrBlank()) {
                    // Use pre-resolved M3U8 URL (quality was selected in dialog)
                    m3u8Url = preResolvedM3u8
                    currentHeaders = (taskHeaders ?: emptyMap()).toMutableMap()
                } else {
                    // 1. Fetch stream URL via batch_scrape
                    val isDubServer = server.endsWith("-dub", ignoreCase = true)
                    val apiServer = if (isDubServer) server.substringBeforeLast("-dub") else server
                    val response = infoService.getVideoStream(anilistId, task.episodeNumber, apiServer)

                    var streamData = if (isDubServer) response?.dub else response?.sub
                    var streamInfo = streamData?.streams?.firstOrNull()

                    // For suzu server, fetch fresh stream URLs from the embed page
                    if (apiServer.equals("suzu", ignoreCase = true)) {
                        val embedUrl = streamData?.episodeId
                        if (!embedUrl.isNullOrBlank()) {
                            val embedStreams = infoService.fetchSuzuEmbedStreams(embedUrl)
                            if (embedStreams != null && embedStreams.isNotEmpty()) {
                                val referer = try {
                                    val uri = java.net.URI(embedUrl)
                                    "${uri.scheme}://${uri.host}"
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
                                val targetStreams = if (isDubServer) {
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
                        updateTask(taskId) { it.copy(status = "Failed: No stream") }
                        return@launch
                    }

                    m3u8Url = streamInfo.url
                    if (m3u8Url.isBlank()) {
                        updateTask(taskId) { it.copy(status = "Failed: No M3U8 URL") }
                        return@launch
                    }

                    currentHeaders = (taskHeaders ?: emptyMap()).toMutableMap()
                    streamInfo.headers?.Referer?.let { currentHeaders["Referer"] = it }
                    streamInfo.headers?.userAgent?.let { currentHeaders["User-Agent"] = it }
                    subtitlesUrl = streamData?.subtitles
                }

                // Create folder
                val currentPath = AppComponent.settingsStore.downloadPathFlow.first()
                val baseDir = if (currentPath.isNotBlank()) currentPath else getDownloadsDirectory()
                val animeSafe = task.animeId.replace("[^A-Za-z0-9]".toRegex(), "_")
                val epDir = "$baseDir/$animeSafe/ep_${task.episodeNumber}"
                KmpFileSystem.createDirectories(epDir)

                // 2. Download Subtitles (only when we fetched from API and have a subtitles URL)
                val subsToDownload = mutableListOf<Pair<String, String>>()
                if (!subtitlesUrl.isNullOrBlank()) {
                    updateTask(taskId) { it.copy(status = "Downloading subtitles...") }
                    try {
                        val subBytes = httpClient.get(subtitlesUrl) {
                            currentHeaders.forEach { (k, v) -> header(k, v) }
                        }.readBytes()
                        val format = if (subtitlesUrl.contains(".vtt")) "vtt" else if (subtitlesUrl.contains(".srt")) "srt" else "ass"
                        val fileName = "subtitle_default.$format"
                        val subFile = "$epDir/$fileName"
                        KmpFileSystem.write(subFile, subBytes)
                        subsToDownload.add(subFile to "Default")
                    } catch (e: Exception) { }
                }

                // 3. Download Video & Audio
                updateTask(taskId) { it.copy(status = "Parsing playlist...") }
                val masterPlaylist = httpClient.get(m3u8Url) {
                    currentHeaders.forEach { (k, v) -> header(k, v) }
                }.bodyAsText()
                
                val audioPlaylistUrl = parseAudioPlaylistUrl(m3u8Url, masterPlaylist, audioLang ?: "jpn")
                val videoPlaylistUrl = getFinalPlaylistUrl(m3u8Url, masterPlaylist)
                
                val videoPlaylistText = httpClient.get(videoPlaylistUrl) {
                    currentHeaders.forEach { (k, v) -> header(k, v) }
                }.bodyAsText()

                val isEncrypted = videoPlaylistText.contains("#EXT-X-KEY") || masterPlaylist.contains("#EXT-X-KEY")
                val videoSegments = if (isEncrypted) emptyList<String>() else parseSegments(videoPlaylistUrl, videoPlaylistText)

                val audioSegments = if (audioPlaylistUrl != null) {
                    parseSegments(audioPlaylistUrl, httpClient.get(audioPlaylistUrl) {
                        currentHeaders.forEach { (k, v) -> header(k, v) }
                    }.bodyAsText())
                } else emptyList()

                if (videoSegments.isEmpty() && !isEncrypted) {
                    updateTask(taskId) { it.copy(status = "Failed: No video segments") }
                    return@launch
                }

                val rawVideoPath = "$epDir/video_raw.ts"
                val rawAudioPath = "$epDir/audio_raw.ts"
                val finalMkvPath = "$epDir/${task.title.replace("[^A-Za-z0-9 ]".toRegex(), "")}_Ep_${task.episodeNumber}.mkv"
                
                var totalBytesDownloaded = 0L
                var lastMeasureTime = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
                var bytesSinceLastMeasure = 0L

                // Download Video (Skip if already using HLS passthrough for encrypted streams)
                if (!isEncrypted) {
                    val videoSink = KmpFileSystem.sink(rawVideoPath).buffer()
                    try {
                    videoSegments.forEachIndexed { index, segmentUrl ->
                        while (tasks.value.find { it.id == taskId }?.isPaused == true) {
                            kotlinx.coroutines.delay(1000)
                            lastMeasureTime = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
                        }

                        val segmentBytes = httpClient.get(segmentUrl) {
                            currentHeaders.forEach { (k, v) -> header(k, v) }
                        }.readBytes()
                        videoSink.write(segmentBytes)
                        totalBytesDownloaded += segmentBytes.size
                        bytesSinceLastMeasure += segmentBytes.size
                        
                        val now = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
                        val diff = now - lastMeasureTime
                        if (diff >= 1000) {
                            val speed = (bytesSinceLastMeasure.toDouble() / (diff / 1000.0))
                            val progress = (index + 1).toFloat() / (videoSegments.size + audioSegments.size)
                            val remainingBytes = (videoSegments.size + audioSegments.size - (index + 1)) * (totalBytesDownloaded.toDouble() / (index + 1))
                            updateTask(taskId) { it.copy(
                                status = "Downloading Video: ${(progress * 100).toInt()}%",
                                progress = progress,
                                downloadSpeed = formatSpeed(speed),
                                eta = formatEta(if (speed > 0) (remainingBytes / speed).toInt() else 0)
                            ) }
                            lastMeasureTime = now
                            bytesSinceLastMeasure = 0
                        }
                    }
                    } finally {
                        videoSink.close()
                    }
                } else {
                    updateTask(taskId) { it.copy(status = "Downloading Stream (HLS)...", progress = 0.5f) }
                }

                // Download Audio if separate (Skip if encrypted as HLS handles it)
                if (audioSegments.isNotEmpty() && !isEncrypted) {
                    val audioSink = KmpFileSystem.sink(rawAudioPath).buffer()
                    try {
                        audioSegments.forEachIndexed { index, segmentUrl ->
                            while (tasks.value.find { it.id == taskId }?.isPaused == true) {
                                kotlinx.coroutines.delay(1000)
                            }
                            val segmentBytes = httpClient.get(segmentUrl) {
                                currentHeaders.forEach { (k, v) -> header(k, v) }
                            }.readBytes()
                            audioSink.write(segmentBytes)
                            
                            val progress = (videoSegments.size + index + 1).toFloat() / (videoSegments.size + audioSegments.size)
                            updateTask(taskId) { it.copy(
                                status = "Downloading Audio: ${(progress * 100).toInt()}%",
                                progress = progress
                            ) }
                        }
                    } finally {
                        audioSink.close()
                    }
                }

                // 4. Muxing
                updateTask(taskId) { it.copy(status = "Muxing into MKV...", progress = 0.99f, downloadSpeed = "", eta = "") }
                
                val muxSuccess = muxToMkv(
                    videoPath = if (isEncrypted) videoPlaylistUrl else rawVideoPath,
                    audioPath = if (audioSegments.isNotEmpty() && !isEncrypted) rawAudioPath else null,
                    subtitles = subsToDownload,
                    fonts = emptyList(),
                    metadataPath = null,
                    outputPath = finalMkvPath,
                    inputHeaders = currentHeaders
                )

                if (muxSuccess) {
                    // Cleanup
                    try {
                        if (!isEncrypted) {
                            KmpFileSystem.delete(rawVideoPath)
                            if (audioSegments.isNotEmpty()) KmpFileSystem.delete(rawAudioPath)
                        }
                        subsToDownload.forEach { (path, _) -> KmpFileSystem.delete(path) }
                    } catch (e: Exception) { }
                    
                    updateTask(taskId) { it.copy(status = "Finished", progress = 1f, localPath = finalMkvPath) }
                } else {
                    updateTask(taskId) { it.copy(status = "Finished (Mux Failed)", progress = 1f, localPath = rawVideoPath) }
                }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    val message = e.message ?: "Unknown error"
                    val finalStatus = if (message.contains("EPERM") || message.contains("Permission denied")) {
                        "Failed: Permission denied. Try using 'Downloads' folder."
                    } else {
                        "Failed: $message"
                    }
                    updateTask(taskId) { it.copy(status = finalStatus) }
                }
            }
        }
        updateTask(taskId) { it.copy(job = job) }
    }

    private fun parseAudioPlaylistUrl(baseUrl: String, content: String, targetLang: String): String? {
        val lines = content.lines()
        val mediaLine = lines.find { it.startsWith("#EXT-X-MEDIA:TYPE=AUDIO") && (it.contains("LANGUAGE=\"$targetLang\"") || it.contains("LANGUAGE=\"$targetLang-", true)) }
            ?: lines.find { it.startsWith("#EXT-X-MEDIA:TYPE=AUDIO") && it.contains("DEFAULT=YES") }
        
        if (mediaLine != null) {
            val uriMatch = Regex("URI=\"([^\"]+)\"").find(mediaLine)
            val uri = uriMatch?.groupValues?.get(1)
            if (uri != null) {
                val base = baseUrl.substringBeforeLast("/")
                return if (uri.startsWith("http")) uri else "$base/$uri"
            }
        }
        return null
    }

    private fun getFinalPlaylistUrl(baseUrl: String, content: String): String {
        if (content.contains("#EXT-X-STREAM-INF")) {
            val lines = content.lines()
            val variantLine = lines.firstOrNull { it.isNotBlank() && !it.startsWith("#") }
            if (variantLine != null) {
                val base = baseUrl.substringBeforeLast("/")
                return if (variantLine.startsWith("http")) variantLine else "$base/$variantLine"
            }
        }
        return baseUrl
    }

    private fun parseSegments(playlistUrl: String, content: String): List<String> {
        val base = playlistUrl.substringBeforeLast("/")
        return content.lines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { line ->
                if (line.startsWith("http")) line else "$base/$line"
            }
    }

    private fun formatSpeed(bytesPerSec: Double): String {
        return when {
            bytesPerSec >= 1024 * 1024 -> String.format("%.1f MB/s", bytesPerSec / (1024 * 1024))
            bytesPerSec >= 1024 -> String.format("%.1f KB/s", bytesPerSec / 1024)
            else -> String.format("%.0f B/s", bytesPerSec)
        }
    }

    private fun formatEta(seconds: Int): String {
        return when {
            seconds >= 3600 -> String.format("%dh %dm left", seconds / 3600, (seconds % 3600) / 60)
            seconds >= 60 -> String.format("%dm %ds left", seconds / 60, seconds % 60)
            else -> String.format("%ds left", seconds)
        }
    }

    fun pauseDownload(id: String) {
        updateTask(id) { it.copy(isPaused = true, status = "Paused") }
        saveTasks()
    }

    fun resumeDownload(id: String) {
        updateTask(id) { it.copy(isPaused = false, status = "Resuming...") }
    }

    fun cancelDownload(id: String) {
        tasks.value.find { it.id == id }?.job?.cancel()
        tasks.update { it.filterNot { t -> t.id == id } }
        saveTasks()
    }

    fun removeTask(id: String) {
        cancelDownload(id)
    }


    private fun updateTask(id: String, update: (DownloadTask) -> DownloadTask) {
        tasks.update { list ->
            list.map { if (it.id == id) update(it) else it }
        }
        saveTasks()
    }
}
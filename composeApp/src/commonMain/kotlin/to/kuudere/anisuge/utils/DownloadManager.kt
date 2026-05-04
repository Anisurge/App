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
import to.kuudere.anisuge.data.models.StreamingData
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
        headers: Map<String, String>? = null
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

        executeDownload(newTask, anilistId, server, subLang, audioLang, downloadFonts)
    }

    private fun executeDownload(
        task: DownloadTask,
        anilistId: Int,
        server: String,
        subLang: String?,
        audioLang: String?,
        downloadFonts: Boolean
    ) {
        val taskId = task.id
        val headers = task.headers
        val job = scope.launch {
            try {
                // 1. Fetch stream URL
                val apiServer = if (server == "zen2") "zen-2" else server
                val response = infoService.getVideoStream(anilistId, task.episodeNumber, apiServer)
                val streamData = response?.directLink?.data ?: response?.data

                if (streamData == null) {
                    updateTask(taskId) { it.copy(status = "Failed: No stream") }
                    return@launch
                }

                val m3u8Url = streamData.m3u8_url
                if (m3u8Url == null) {
                    updateTask(taskId) { it.copy(status = "Failed: No M3U8 URL") }
                    return@launch
                }

                val currentHeaders = (headers ?: emptyMap()).toMutableMap()
                streamData.headers?.forEach { (k, v) -> currentHeaders[k] = v }

                // Create folder
                val currentPath = AppComponent.settingsStore.downloadPathFlow.first()
                val baseDir = if (currentPath.isNotBlank()) currentPath else getDownloadsDirectory()
                val animeSafe = task.animeId.replace("[^A-Za-z0-9]".toRegex(), "_")
                val epDir = "$baseDir/$animeSafe/ep_${task.episodeNumber}"
                KmpFileSystem.createDirectories(epDir)

                // 2. Download Fonts
                val downloadedFonts = mutableListOf<String>()
                if (!streamData.fonts.isNullOrEmpty()) {
                    updateTask(taskId) { it.copy(status = "Downloading fonts...") }
                    streamData.fonts.forEach { font ->
                        if (font.url != null && font.name != null) {
                            try {
                                val fontBytes = httpClient.get(font.url) {
                                    currentHeaders.forEach { (k, v) -> header(k, v) }
                                }.readBytes()
                                val fontFile = "$epDir/${font.name}"
                                KmpFileSystem.write(fontFile, fontBytes)
                                downloadedFonts.add(fontFile)
                            } catch (e: Exception) { }
                        }
                    }
                }

                // 3. Download Subtitles
                val subsToDownload = if (subLang == "All") {
                    streamData.subtitles ?: emptyList()
                } else {
                    val target = streamData.subtitles?.find {
                        it.title?.equals(subLang, ignoreCase = true) == true ||
                        it.resolvedLang?.equals(subLang, ignoreCase = true) == true
                    }
                    if (target != null) listOf(target) else emptyList()
                }

                val downloadedSubs = mutableListOf<Pair<String, String>>()
                if (subsToDownload.isNotEmpty()) {
                    updateTask(taskId) { it.copy(status = "Downloading subtitles...") }
                    subsToDownload.forEach { sub ->
                        if (sub.url != null) {
                            try {
                                val subBytes = httpClient.get(sub.url) {
                                    currentHeaders.forEach { (k, v) -> header(k, v) }
                                }.readBytes()
                                val label = (sub.title ?: sub.resolvedLang)?.replace("[^A-Za-z0-9 ]".toRegex(), "_") ?: "unknown"
                                val format = if (sub.url.contains(".vtt")) "vtt" else if (sub.url.contains(".srt")) "srt" else "ass"
                                val fileName = "subtitle_$label.$format"
                                val subFile = "$epDir/$fileName"
                                KmpFileSystem.write(subFile, subBytes)
                                downloadedSubs.add(subFile to (sub.title ?: sub.resolvedLang ?: "Subtitle"))
                            } catch (e: Exception) { }
                        }
                    }
                }

                // 4. Download Video & Audio
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

                if (videoSegments.isEmpty()) {
                    updateTask(taskId) { it.copy(status = "Failed: No video segments") }
                    return@launch
                }

                // Generate FFmetadata for chapters
                val metadataPath = "$epDir/metadata.txt"
                val chapters = streamData.chapters ?: emptyList()
                if (chapters.isNotEmpty()) {
                    val sb = StringBuilder(";FFMETADATA1\n")
                    chapters.forEach { ch ->
                        val startMs = ((ch.start_time ?: 0.0) * 1000).toLong()
                        val endMs = ((ch.end_time ?: 0.0) * 1000).toLong()
                        sb.append("\n[CHAPTER]\n")
                        sb.append("TIMEBASE=1/1000\n")
                        sb.append("START=$startMs\n")
                        sb.append("END=$endMs\n")
                        sb.append("title=${ch.title ?: "Chapter"}\n")
                    }
                    KmpFileSystem.write(metadataPath, sb.toString().toByteArray())
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
                                headers?.forEach { (k, v) -> header(k, v) }
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

                // 5. Muxing
                updateTask(taskId) { it.copy(status = "Muxing into MKV...", progress = 0.99f, downloadSpeed = "", eta = "") }

                val muxSuccess = muxToMkv(
                    videoPath = if (isEncrypted) m3u8Url else rawVideoPath,
                    audioPath = if (audioSegments.isNotEmpty() && !isEncrypted) rawAudioPath else null,
                    subtitles = downloadedSubs,
                    fonts = downloadedFonts,
                    metadataPath = if (chapters.isNotEmpty()) metadataPath else null,
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
                        downloadedSubs.forEach { (path, _) -> KmpFileSystem.delete(path) }
                        downloadedFonts.forEach { KmpFileSystem.delete(it) }
                        if (chapters.isNotEmpty()) KmpFileSystem.delete(metadataPath)
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
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
        val job = scope.launch {
            // TODO: Streaming download is not yet available in the Project-R API.
            //  Re-implement once the backend adds /watch/* streaming routes.
            updateTask(taskId) { it.copy(status = "Failed: Streaming not yet available in new API") }
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
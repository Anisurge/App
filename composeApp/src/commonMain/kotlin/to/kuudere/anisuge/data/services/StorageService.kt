package to.kuudere.anisuge.data.services

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.buffer
import java.io.File
import to.kuudere.anisuge.data.models.AnimeFolderInfo
import to.kuudere.anisuge.data.models.DownloadStorageInfo
import to.kuudere.anisuge.data.models.StorageCategory
import to.kuudere.anisuge.data.models.StorageInfo
import to.kuudere.anisuge.utils.DownloadManager
import to.kuudere.anisuge.utils.getDownloadsDirectory
import to.kuudere.anisuge.utils.formatFileSize
import to.kuudere.anisuge.utils.formatFileSizeCompact
import to.kuudere.anisuge.utils.formatFloat

expect fun getFontCacheDirectory(): String
expect fun getSettingsDirectory(): String
expect fun getTotalDiskSpace(): Long
expect fun getFreeDiskSpace(): Long

class StorageService {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getStorageInfo(): StorageInfo = withContext(Dispatchers.Default) {
        val downloadsDir = getDownloadsDirectory()
        val fontCacheDir = getFontCacheDirectory()
        val settingsDir = getSettingsDirectory()

        val downloadsInfo = scanDirectory(downloadsDir, "Downloads")
        val fontCacheInfo = scanDirectory(fontCacheDir, "Font Cache")
        val settingsInfo = scanDirectory(settingsDir, "Settings")

        val totalUsed = downloadsInfo.size + fontCacheInfo.size + settingsInfo.size
        val freeSpace = getFreeDiskSpace()

        StorageInfo(
            downloads = downloadsInfo,
            fontCache = fontCacheInfo,
            settings = settingsInfo,
            totalUsed = totalUsed,
            freeSpace = freeSpace
        )
    }

    suspend fun getDownloadStorageInfo(): DownloadStorageInfo = withContext(Dispatchers.Default) {
        val downloadsDir = getDownloadsDirectory()
        val basePath = downloadsDir.toPath()

        if (!FileSystem.SYSTEM.exists(basePath)) {
            return@withContext DownloadStorageInfo()
        }

        val animeFolders = mutableListOf<AnimeFolderInfo>()
        var totalEpisodes = 0
        var totalSize = 0L

        // Read tasks.json to get anime titles
        val tasksFile = "$downloadsDir/tasks.json".toPath()
        val tasks = if (FileSystem.SYSTEM.exists(tasksFile)) {
            try {
                val content = FileSystem.SYSTEM.read(tasksFile) { readUtf8() }
                json.decodeFromString<List<DownloadTaskInfo>>(content)
            } catch (e: Exception) {
                emptyList()
            }
        } else emptyList()

        // Group tasks by animeId to get titles
        val animeTitles = tasks.groupBy { it.animeId }.mapValues { entry ->
            entry.value.firstOrNull()?.title ?: entry.key
        }

        val coverImages = tasks.groupBy { it.animeId }.mapValues { entry ->
            entry.value.firstOrNull()?.coverImage
        }

        // Scan anime folders
        File(downloadsDir).listFiles()?.filter { it.isDirectory }?.forEach { animeDir ->
            val animeId = animeDir.name.replace("_", "-")
            var episodeCount = 0
            var folderSize = 0L

            animeDir.listFiles()?.filter { it.isDirectory }?.forEach { epDir ->
                if (epDir.name.startsWith("ep_")) {
                    episodeCount++
                    epDir.listFiles()?.forEach { file ->
                        if (file.isFile) {
                            folderSize += file.length()
                        }
                    }
                }
            }

            if (episodeCount > 0) {
                animeFolders.add(AnimeFolderInfo(
                    animeId = animeId,
                    title = animeTitles[animeId] ?: animeDir.name.replace("_", " "),
                    episodeCount = episodeCount,
                    size = folderSize,
                    coverImage = coverImages[animeId]
                ))
                totalEpisodes += episodeCount
                totalSize += folderSize
            }
        }

        DownloadStorageInfo(
            animeFolders = animeFolders.sortedByDescending { it.size },
            totalEpisodes = totalEpisodes,
            totalSize = totalSize
        )
    }

    private fun scanDirectory(path: String, name: String): StorageCategory {
        val file = File(path)
        if (!file.exists()) {
            return StorageCategory(name = name, path = path)
        }

        var size = 0L
        var fileCount = 0

        if (file.isDirectory) {
            file.walkTopDown().forEach { f ->
                if (f.isFile) {
                    size += f.length()
                    fileCount++
                }
            }
        } else {
            size = file.length()
            fileCount = 1
        }

        return StorageCategory(
            name = name,
            size = size,
            fileCount = fileCount,
            path = path
        )
    }

    suspend fun clearFontCache(): Boolean = withContext(Dispatchers.Default) {
        try {
            val fontDir = File(getFontCacheDirectory())
            if (fontDir.exists()) {
                fontDir.listFiles()?.forEach { it.delete() }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun deleteAnimeDownloads(animeId: String): Boolean = withContext(Dispatchers.Default) {
        try {
            val downloadsDir = getDownloadsDirectory()
            val safeId = animeId.replace("[^A-Za-z0-9]".toRegex(), "_")
            val animeDir = File(downloadsDir, safeId)
            if (animeDir.exists()) {
                animeDir.deleteRecursively()
            }

            // Also remove from DownloadManager tasks
            val tasksToRemove = DownloadManager.tasks.value.filter { it.animeId == animeId }
            tasksToRemove.forEach { task ->
                DownloadManager.removeTask(task.id)
            }

            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun deleteEpisodeDownload(animeId: String, episodeNumber: Int): Boolean = withContext(Dispatchers.Default) {
        try {
            val downloadsDir = getDownloadsDirectory()
            val safeId = animeId.replace("[^A-Za-z0-9]".toRegex(), "_")
            val epDir = File(downloadsDir, "$safeId/ep_$episodeNumber")
            if (epDir.exists()) {
                epDir.deleteRecursively()
            }

            // Remove from DownloadManager
            val taskId = "${animeId}_$episodeNumber"
            DownloadManager.removeTask(taskId)

            true
        } catch (e: Exception) {
            false
        }
    }

    fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 * 1024 -> "${formatFloat(bytes / (1024.0 * 1024.0 * 1024.0), 2)} GB"
            bytes >= 1024 * 1024 -> "${formatFloat(bytes / (1024.0 * 1024.0), 2)} MB"
            bytes >= 1024 -> "${formatFloat(bytes / 1024.0, 2)} KB"
            else -> "$bytes B"
        }
    }

    fun formatBytesCompact(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 * 1024 -> "${formatFloat(bytes / (1024.0 * 1024.0 * 1024.0), 1)} GB"
            bytes >= 1024 * 1024 -> "${formatFloat(bytes / (1024.0 * 1024.0), 1)} MB"
            bytes >= 1024 -> "${formatFloat(bytes / 1024.0, 1)} KB"
            else -> "$bytes B"
        }
    }
}

@kotlinx.serialization.Serializable
private data class DownloadTaskInfo(
    val id: String,
    val animeId: String,
    val title: String,
    val episodeNumber: Int,
    val coverImage: String? = null,
    val status: String = ""
)

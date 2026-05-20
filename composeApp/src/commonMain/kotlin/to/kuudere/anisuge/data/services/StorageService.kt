package to.kuudere.anisuge.data.services

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.buffer
import to.kuudere.anisuge.data.models.AnimeFolderInfo
import to.kuudere.anisuge.data.models.DownloadStorageInfo
import to.kuudere.anisuge.data.models.StorageCategory
import to.kuudere.anisuge.data.models.StorageInfo
import to.kuudere.anisuge.platform.KmpFileSystem
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
    private val fs = FileSystem.SYSTEM

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

        if (!fs.exists(basePath)) {
            return@withContext DownloadStorageInfo()
        }

        val animeFolders = mutableListOf<AnimeFolderInfo>()
        var totalEpisodes = 0
        var totalSize = 0L

        val tasksFile = "$downloadsDir/tasks.json".toPath()
        val tasks = if (fs.exists(tasksFile)) {
            try {
                val content = fs.read(tasksFile) { readUtf8() }
                json.decodeFromString<List<DownloadTaskInfo>>(content)
            } catch (_: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }

        val animeTitles = tasks.groupBy { it.animeId }.mapValues { entry ->
            entry.value.firstOrNull()?.title ?: entry.key
        }

        val coverImages = tasks.groupBy { it.animeId }.mapValues { entry ->
            entry.value.firstOrNull()?.coverImage
        }

        fs.list(basePath).forEach { entry ->
            val animePath = basePath / entry
            val meta = fs.metadataOrNull(animePath) ?: return@forEach
            if (!meta.isDirectory) return@forEach

            val animeId = entry.toString().replace("_", "-")
            var episodeCount = 0
            var folderSize = 0L

            fs.list(animePath).forEach { epEntry ->
                val epPath = animePath / epEntry
                val epMeta = fs.metadataOrNull(epPath) ?: return@forEach
                if (!epMeta.isDirectory || !epEntry.toString().startsWith("ep_")) return@forEach

                episodeCount++
                folderSize += directorySize(epPath)
            }

            if (episodeCount > 0) {
                animeFolders.add(
                    AnimeFolderInfo(
                        animeId = animeId,
                        title = animeTitles[animeId] ?: entry.toString().replace("_", " "),
                        episodeCount = episodeCount,
                        size = folderSize,
                        coverImage = coverImages[animeId],
                    ),
                )
                totalEpisodes += episodeCount
                totalSize += folderSize
            }
        }

        DownloadStorageInfo(
            animeFolders = animeFolders.sortedByDescending { it.size },
            totalEpisodes = totalEpisodes,
            totalSize = totalSize,
        )
    }

    private fun directorySize(path: Path): Long {
        var size = 0L
        walkFiles(path) { filePath ->
            size += fs.metadataOrNull(filePath)?.size ?: 0L
        }
        return size
    }

    private fun walkFiles(path: Path, onFile: (Path) -> Unit) {
        val meta = fs.metadataOrNull(path) ?: return
        if (meta.isDirectory) {
            fs.list(path).forEach { child -> walkFiles(path / child, onFile) }
        } else if (meta.isRegularFile) {
            onFile(path)
        }
    }

    private fun scanDirectory(path: String, name: String): StorageCategory {
        val okPath = path.toPath()
        if (!fs.exists(okPath)) {
            return StorageCategory(name = name, path = path)
        }

        var size = 0L
        var fileCount = 0
        walkFiles(okPath) { filePath ->
            size += fs.metadataOrNull(filePath)?.size ?: 0L
            fileCount++
        }

        return StorageCategory(
            name = name,
            size = size,
            fileCount = fileCount,
            path = path,
        )
    }

    suspend fun clearFontCache(): Boolean = withContext(Dispatchers.Default) {
        try {
            val fontPath = getFontCacheDirectory().toPath()
            if (!fs.exists(fontPath)) return@withContext true
            fs.list(fontPath).forEach { entry ->
                KmpFileSystem.delete((fontPath / entry).toString(), mustExist = false)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun deleteAnimeDownloads(animeId: String): Boolean = withContext(Dispatchers.Default) {
        try {
            val downloadsDir = getDownloadsDirectory()
            val safeId = animeId.replace("[^A-Za-z0-9]".toRegex(), "_")
            val animeDir = "$downloadsDir/$safeId"
            if (KmpFileSystem.exists(animeDir)) {
                deleteTree(animeDir)
            }

            val tasksToRemove = DownloadManager.tasks.value.filter { it.animeId == animeId }
            tasksToRemove.forEach { task ->
                DownloadManager.removeTask(task.id)
            }

            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun deleteEpisodeDownload(animeId: String, episodeNumber: Int): Boolean = withContext(Dispatchers.Default) {
        try {
            val downloadsDir = getDownloadsDirectory()
            val safeId = animeId.replace("[^A-Za-z0-9]".toRegex(), "_")
            val epDir = "$downloadsDir/$safeId/ep_$episodeNumber"
            if (KmpFileSystem.exists(epDir)) {
                deleteTree(epDir)
            }

            val taskId = "${animeId}_$episodeNumber"
            DownloadManager.removeTask(taskId)

            true
        } catch (_: Exception) {
            false
        }
    }

    private fun deleteTree(path: String) {
        val root = path.toPath()
        if (!fs.exists(root)) return

        fun walkDelete(dir: Path) {
            fs.list(dir).forEach { entry ->
                val child = dir / entry
                if (fs.metadataOrNull(child)?.isDirectory == true) {
                    walkDelete(child)
                }
                KmpFileSystem.delete(child.toString(), mustExist = false)
            }
        }
        walkDelete(root)
        KmpFileSystem.delete(path, mustExist = false)
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
    val status: String = "",
)

package to.kuudere.anisuge.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.github.vinceglb.filekit.absolutePath
import io.github.vinceglb.filekit.dialogs.compose.rememberDirectoryPickerLauncher
import java.io.File
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

actual fun getDownloadsDirectory(): String {
    val home = System.getProperty("user.home")
    val dir = File(home, "Downloads/Anisurge")
    if (!dir.exists()) dir.mkdirs()
    return dir.absolutePath
}

actual fun getCacheDirectory(): String {
    val home = System.getProperty("user.home")
    val dir = File(home, ".anisug")
    if (!dir.exists()) dir.mkdirs()
    return dir.absolutePath
}

actual fun hasStoragePermission(): Boolean = true

actual fun isSharedExternalStoragePath(path: String): Boolean = false

actual fun downloadPathRequiresSafPicker(path: String): Boolean = false

actual fun publishTempDownloadOutput(tempPath: String, outputPath: String): Boolean {
    return try {
        File(tempPath).copyTo(File(outputPath), overwrite = true)
        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

actual fun getDownloadWorkDirectory(taskId: String): String {
    val safeId = taskId.replace("[^A-Za-z0-9_.-]".toRegex(), "_")
    val dir = File(getCacheDirectory(), "download_work/$safeId")
    if (!dir.exists()) dir.mkdirs()
    return dir.absolutePath
}

actual fun publishCompletedDownloadFile(
    tempPath: String,
    fileName: String,
    mimeType: String,
    animeId: String,
    episodeNumber: Int,
    downloadRoot: String,
): String? {
    return try {
        val safeId = animeId.replace("[^A-Za-z0-9]".toRegex(), "_")
        val root = downloadRoot.ifBlank { getDownloadsDirectory() }
        val dest = File(root, "$safeId/ep_$episodeNumber/$fileName")
        dest.parentFile?.mkdirs()
        File(tempPath).copyTo(dest, overwrite = true)
        dest.takeIf { it.exists() && it.length() > 0L }?.absolutePath
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

actual fun deleteDownloadedFile(path: String): Boolean = try {
    val file = File(path)
    !file.exists() || file.delete()
} catch (_: Exception) {
    false
}

actual fun deleteDownloadWorkDirectory(path: String): Boolean = try {
    val file = File(path)
    !file.exists() || file.deleteRecursively()
} catch (_: Exception) {
    false
}

actual fun fileSize(path: String): Long = try {
    File(path).length()
} catch (_: Exception) {
    0L
}

@Composable
actual fun rememberDownloadDirectoryPicker(onPicked: (String?) -> Unit): () -> Unit {
    val launcher = rememberDirectoryPickerLauncher { dir ->
        onPicked(dir?.absolutePath())
    }
    return remember(launcher) {
        { launcher.launch() }
    }
}

@Composable
actual fun RequestStoragePermission(onResult: (Boolean) -> Unit) {
    onResult(true)
}

actual fun hasNotificationPermission(): Boolean = true

@Composable
actual fun RequestNotificationPermission(onResult: (Boolean) -> Unit) {
    onResult(true)
}

actual fun openDirectory(path: String) {
    try {
        val file = File(path)
        val dir = if (file.isDirectory) file else file.parentFile
        if (dir != null && dir.exists()) {
            java.awt.Desktop.getDesktop().open(dir)
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

actual fun buildDownloadOutputPath(epDir: String, title: String, episodeNumber: Int): String {
    val safeTitle = title.replace("[^A-Za-z0-9 ]".toRegex(), "")
    return "$epDir/${safeTitle}_Ep_$episodeNumber.mkv"
}

actual suspend fun muxToMkv(
    videoPath: String,
    audioPath: String?,
    subtitles: List<Pair<String, String>>,
    fonts: List<String>,
    metadataPath: String?,
    outputPath: String,
    inputHeaders: Map<String, String>?,
    masterPlaylistUrl: String?,
    preferLocalTsRemux: Boolean,
): Boolean = withContext(Dispatchers.IO) {
    try {
        val ffmpegPath = findBundledFfmpeg() ?: try {
            ws.schild.jave.process.ffmpeg.DefaultFFMPEGLocator().executablePath
        } catch (e: Exception) {
            "ffmpeg" // Fallback to system path if locator fails
        }

        val args = mutableListOf(ffmpegPath, "-y")

        // Handle HLS/Stream Headers
        inputHeaders?.let { headers ->
            val referer = headers["Referer"] ?: headers["referer"]
            if (referer != null) {
                args.add("-referer"); args.add(referer)
            }

            val userAgent = headers["User-Agent"] ?: headers["user-agent"]
            if (userAgent != null) {
                args.add("-user_agent"); args.add(userAgent)
            }

            val otherHeaders = headers.filterKeys { it.lowercase() != "referer" && it.lowercase() != "user-agent" }
            if (otherHeaders.isNotEmpty()) {
                val headerStrings = otherHeaders.map { "${it.key}: ${it.value}" }.joinToString("\r\n") + "\r\n"
                args.add("-headers"); args.add(headerStrings)
            }
        }

        val localTsVideo = videoPath.endsWith(".ts", ignoreCase = true) &&
                !videoPath.startsWith("http", ignoreCase = true)
        val localTsAudio = audioPath?.endsWith(".ts", ignoreCase = true) == true &&
                !audioPath.startsWith("http", ignoreCase = true)

        if (localTsVideo) {
            args.add("-f"); args.add("mpegts")
        }
        args.add("-i"); args.add(videoPath)
        if (audioPath != null) {
            if (localTsAudio) {
                args.add("-f"); args.add("mpegts")
            }
            args.add("-i"); args.add(audioPath)
        }
        subtitles.forEach { (path, _) ->
            args.add("-i"); args.add(path)
        }

        val metadataIndex = if (metadataPath != null) {
            val index = 1 + (if (audioPath != null) 1 else 0) + subtitles.size
            args.add("-i"); args.add(metadataPath)
            index
        } else -1

        args.add("-map"); args.add("0:v")
        if (audioPath != null) {
            args.add("-map"); args.add("1:a")
        } else {
            args.add("-map"); args.add("0:a?")
        }

        subtitles.forEachIndexed { i, _ ->
            val index = if (audioPath != null) i + 2 else i + 1
            args.add("-map"); args.add("$index:s")
        }

        if (metadataIndex != -1) {
            args.add("-map_metadata"); args.add("$metadataIndex")
        }

        fonts.forEach { fontPath ->
            args.add("-attach"); args.add(fontPath)
        }
        args.add("-metadata:s:t"); args.add("mimetype=application/x-truetype-font")

        subtitles.forEachIndexed { i, (_, label) ->
            args.add("-metadata:s:s:$i"); args.add("title=$label")
        }

        args.add("-c:v"); args.add("copy")
        args.add("-c:a"); args.add("copy")
        if (subtitles.isNotEmpty()) {
            args.add("-c:s"); args.add("copy")
        }
        args.add(outputPath)

        val exitCode = runFfmpeg(args)
        if (exitCode != 0) {
            println("[FFmpeg] Process exited with code $exitCode")
        }
        if (exitCode == 0) return@withContext true

        // Fallback: remux without embedded subs when sidecar .vtt mux fails (player loads subs from ep folder).
        if (localTsVideo && subtitles.isNotEmpty()) {
            val fallbackArgs = mutableListOf(ffmpegPath, "-y", "-f", "mpegts", "-i", videoPath)
            if (audioPath != null) {
                if (localTsAudio) {
                    fallbackArgs.add("-f"); fallbackArgs.add("mpegts")
                }
                fallbackArgs.add("-i"); fallbackArgs.add(audioPath)
                fallbackArgs.addAll(listOf("-map", "0:v", "-map", "1:a", "-c:v", "copy", "-c:a", "copy", outputPath))
            } else {
                fallbackArgs.addAll(listOf("-map", "0:v", "-map", "0:a?", "-c:v", "copy", "-c:a", "copy", outputPath))
            }
            val fallbackExit = runFfmpeg(fallbackArgs)
            if (fallbackExit == 0) return@withContext true
        }
        return@withContext false
    } catch (e: Exception) {
        println("[FFmpeg] Process failed: ${e.message}")
        return@withContext false
    }
}

private fun findBundledFfmpeg(): String? {
    return try {
        val osName = System.getProperty("os.name").lowercase()
        val isWindows = "win" in osName
        val resourceName = if (isWindows) "/native/ffmpeg.exe" else "/native/ffmpeg"

        // Extract bundled ffmpeg from resources to a cache directory
        val cacheDir = java.io.File(getCacheDirectory(), "bin")
        cacheDir.mkdirs()
        val cachedFile = java.io.File(cacheDir, if (isWindows) "ffmpeg.exe" else "ffmpeg")

        // Only extract once (cache hit)
        if (cachedFile.isFile && cachedFile.length() > 0) {
            return cachedFile.absolutePath
        }

        // Extract from classpath resource
        val resourceStream = object {}::class.java.getResourceAsStream(resourceName)
        if (resourceStream != null) {
            cachedFile.outputStream().use { out ->
                resourceStream.use { `in` -> `in`.copyTo(out) }
            }
            cachedFile.setExecutable(true)
            println("[FFmpeg] Extracted bundled binary to ${cachedFile.absolutePath}")
            return cachedFile.absolutePath
        }

        null
    } catch (e: Exception) {
        println("[FFmpeg] Failed to extract bundled binary: ${e.message}")
        null
    }
}

private fun runFfmpeg(args: List<String>): Int {
    val process = ProcessBuilder(args)
        .redirectErrorStream(true)
        .start()
    val outputReader = Thread {
        try {
            process.inputStream.bufferedReader().forEachLine { line ->
                println("[FFmpeg] $line")
            }
        } catch (_: Exception) {
        }
    }
    outputReader.start()
    val exitCode = process.waitFor()
    outputReader.join(1000)
    return exitCode
}

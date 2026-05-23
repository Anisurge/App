package to.kuudere.anisuge.utils

import java.io.File
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

actual fun getDownloadsDirectory(): String {
    val home = System.getProperty("user.home")
    val dir = File(home, "Downloads/Anisug")
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

@androidx.compose.runtime.Composable
actual fun RequestStoragePermission(onResult: (Boolean) -> Unit) {
    onResult(true)
}

actual fun hasNotificationPermission(): Boolean = true

@androidx.compose.runtime.Composable
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
        val ffmpegPath = try {
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
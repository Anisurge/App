package to.kuudere.anisuge.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import io.github.vinceglb.filekit.absolutePath
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.io.path.createTempDirectory
import ws.schild.jave.process.ffmpeg.DefaultFFMPEGLocator

@Composable
actual fun rememberChatImagePicker(
    allowVideo: Boolean,
    onResult: (ChatImagePick?) -> Unit,
): () -> Unit {
    val scope = rememberCoroutineScope()
    val launcher = rememberFilePickerLauncher(
        type = FileKitType.File(),
    ) { platformFile ->
        if (platformFile == null) {
            onResult(null)
            return@rememberFilePickerLauncher
        }
        scope.launch {
            val pick = withContext(Dispatchers.IO) {
                readChatImageFile(File(platformFile.absolutePath()), allowVideo)
            }
            onResult(pick)
        }
    }
    return { launcher.launch() }
}

private fun readChatImageFile(file: File, allowVideo: Boolean): ChatImagePick? {
    if (!file.exists() || !file.isFile) return null
    val size = file.length()
    val mime = when (file.extension.lowercase()) {
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "jpg", "jpeg" -> "image/jpeg"
        "bmp" -> "image/bmp"
        "mp4" -> if (allowVideo) "video/mp4" else return null
        else -> return null
    }
    val maxBytes = if (mime == "video/mp4") PROFILE_VIDEO_MAX_BYTES else CHAT_IMAGE_MAX_BYTES
    if (size <= 0 || size > maxBytes) return null
    if (mime == "video/mp4") {
        val bytes = file.readBytes()
        return ChatImagePick(bytes, mime, file.name, bytes.size.toLong())
    }
    val raw = file.readBytes()
    // Re-encode JPEGs through ImageIO so CMYK / odd ICC profiles never hit Skia's libjpeg on Windows.
    val normalizedJpeg = if (mime == "image/jpeg") {
        decodeImageBitmap(raw)?.let { encodeJpeg(it, quality = 92) }
    } else {
        null
    }
    val bytes = normalizedJpeg ?: raw
    if (bytes.isEmpty() || bytes.size > CHAT_IMAGE_MAX_BYTES) return null
    val outMime = if (normalizedJpeg != null) "image/jpeg" else mime
    val outName = if (normalizedJpeg != null) {
        file.name.substringBeforeLast('.').ifBlank { "image" } + ".jpg"
    } else {
        file.name
    }
    return ChatImagePick(bytes, outMime, outName, bytes.size.toLong())
}

actual suspend fun normalizeProfileVideoForUpload(pick: ChatImagePick): Result<ChatImagePick> =
    withContext(Dispatchers.IO) {
        runCatching {
            require(pick.mimeType == "video/mp4") { "Only MP4 videos are supported" }
            val dir = createTempDirectory("anisurge-profile-video-").toFile()
            val input = File(dir, "input.mp4")
            val output = File(dir, "output.mp4")
            input.writeBytes(pick.bytes)
            try {
                val ffmpeg = resolveFfmpegPath()
                val ok = transcodeProfileVideo(ffmpeg, input, output, scale = 512, crf = 28) ||
                    transcodeProfileVideo(ffmpeg, input, output, scale = 384, crf = 32)
                if (!ok || !output.exists() || output.length() <= 0L) {
                    throw IllegalStateException("Could not crop and trim MP4")
                }
                if (output.length() > PROFILE_VIDEO_MAX_BYTES) {
                    throw IllegalStateException("Prepared MP4 is still over 3 MB")
                }
                val bytes = output.readBytes()
                ChatImagePick(
                    bytes = bytes,
                    mimeType = "video/mp4",
                    fileName = pick.fileName.substringBeforeLast('.').ifBlank { "profile" } + "-square.mp4",
                    sizeBytes = bytes.size.toLong(),
                )
            } finally {
                dir.deleteRecursively()
            }
        }
    }

private fun resolveFfmpegPath(): String =
    runCatching { DefaultFFMPEGLocator().executablePath }
        .getOrNull()
        ?.takeIf { it.isNotBlank() }
        ?: "ffmpeg"

private fun transcodeProfileVideo(
    ffmpeg: String,
    input: File,
    output: File,
    scale: Int,
    crf: Int,
): Boolean {
    if (output.exists()) output.delete()
    val filter = "crop=min(iw\\,ih):min(iw\\,ih),scale=$scale:$scale,setsar=1"
    val process = ProcessBuilder(
        ffmpeg,
        "-y",
        "-i",
        input.absolutePath,
        "-t",
        "6",
        "-vf",
        filter,
        "-an",
        "-c:v",
        "libx264",
        "-preset",
        "veryfast",
        "-crf",
        crf.toString(),
        "-movflags",
        "+faststart",
        output.absolutePath,
    )
        .redirectErrorStream(true)
        .start()
    process.inputStream.bufferedReader().use { it.readText() }
    val rc = process.waitFor()
    return rc == 0 && output.exists() && output.length() in 1..PROFILE_VIDEO_MAX_BYTES
}

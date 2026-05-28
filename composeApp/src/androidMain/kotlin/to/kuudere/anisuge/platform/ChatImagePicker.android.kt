package to.kuudere.anisuge.platform

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.microshow.rxffmpeg.RxFFmpegInvoke
import to.kuudere.anisuge.platform.androidAppContext
import java.io.File

@Composable
actual fun rememberChatImagePicker(
    allowVideo: Boolean,
    onResult: (ChatImagePick?) -> Unit,
): () -> Unit {
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri == null) {
            onResult(null)
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val pick = withContext(Dispatchers.IO) { readChatImage(uri, allowVideo) }
            onResult(pick)
        }
    }
    return { launcher.launch(if (allowVideo) "*/*" else "image/*") }
}

private fun readChatImage(uri: Uri, allowVideo: Boolean): ChatImagePick? {
    val resolver = androidAppContext.contentResolver
    val mime = resolver.getType(uri)?.lowercase() ?: "image/jpeg"
    val allowedImages = setOf("image/jpeg", "image/png", "image/gif", "image/webp")
    if (mime !in allowedImages && !(allowVideo && mime == "video/mp4")) {
        return null
    }

    var name = "image.jpg"
    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0) {
                name = cursor.getString(idx) ?: name
            }
        }
    }

    val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
    val maxBytes = if (mime == "video/mp4") PROFILE_VIDEO_RAW_MAX_BYTES else CHAT_IMAGE_MAX_BYTES
    if (bytes.isEmpty() || bytes.size.toLong() > maxBytes) {
        return null
    }
    return ChatImagePick(bytes, mime, name, bytes.size.toLong())
}

actual suspend fun normalizeProfileVideoForUpload(pick: ChatImagePick): Result<ChatImagePick> =
    withContext(Dispatchers.IO) {
        runCatching {
            require(pick.mimeType == "video/mp4") { "Only MP4 videos are supported" }
            val cacheDir = File(androidAppContext.cacheDir, "profile-video")
            cacheDir.mkdirs()
            val input = File.createTempFile("profile-in-", ".mp4", cacheDir)
            val output = File.createTempFile("profile-out-", ".mp4", cacheDir)
            input.writeBytes(pick.bytes)
            try {
                val ok = transcodeProfileVideo(input, output, scale = 512, crf = 28) ||
                    transcodeProfileVideo(input, output, scale = 384, crf = 32) ||
                    transcodeProfileVideo(input, output, scale = 320, crf = 34) ||
                    transcodeProfileVideo(input, output, scale = 256, crf = 36)
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
                input.delete()
                output.delete()
            }
        }
    }

private fun transcodeProfileVideo(input: File, output: File, scale: Int, crf: Int): Boolean {
    if (output.exists()) output.delete()
    val filter = "crop=min(iw\\,ih):min(iw\\,ih),scale=$scale:$scale,setsar=1"
    val cmd = arrayOf(
        "ffmpeg",
        "-y",
        "-i",
        input.absolutePath,
        "-t",
        "6",
        "-map",
        "0:v:0",
        "-vf",
        filter,
        "-an",
        "-r",
        "30",
        "-c:v",
        "libx264",
        "-pix_fmt",
        "yuv420p",
        "-preset",
        "veryfast",
        "-crf",
        crf.toString(),
        "-movflags",
        "+faststart",
        output.absolutePath,
    )
    val rc = RxFFmpegInvoke.getInstance().runCommand(cmd, null)
    return rc == 0 && output.exists() && output.length() in 1..PROFILE_VIDEO_MAX_BYTES
}

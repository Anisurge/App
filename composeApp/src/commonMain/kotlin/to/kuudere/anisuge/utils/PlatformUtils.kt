package to.kuudere.anisuge.utils

expect fun getDownloadsDirectory(): String
expect fun getCacheDirectory(): String

expect fun openDirectory(path: String)

expect fun hasStoragePermission(): Boolean

expect fun isSharedExternalStoragePath(path: String): Boolean

expect fun downloadPathRequiresSafPicker(path: String): Boolean

expect fun publishTempDownloadOutput(tempPath: String, outputPath: String): Boolean

expect fun getDownloadWorkDirectory(taskId: String): String

expect fun publishCompletedDownloadFile(
    tempPath: String,
    fileName: String,
    mimeType: String,
    animeId: String,
    episodeNumber: Int,
    downloadRoot: String,
): String?

expect fun deleteDownloadedFile(path: String): Boolean

expect fun deleteDownloadWorkDirectory(path: String): Boolean

expect fun fileSize(path: String): Long

@androidx.compose.runtime.Composable
expect fun rememberDownloadDirectoryPicker(onPicked: (String?) -> Unit): () -> Unit

@androidx.compose.runtime.Composable
expect fun RequestStoragePermission(onResult: (Boolean) -> Unit)

expect fun hasNotificationPermission(): Boolean

@androidx.compose.runtime.Composable
expect fun RequestNotificationPermission(onResult: (Boolean) -> Unit)

expect fun buildDownloadOutputPath(epDir: String, title: String, episodeNumber: Int): String

expect suspend fun muxToMkv(
    videoPath: String,
    audioPath: String?,
    subtitles: List<Pair<String, String>>, // Path to Label
    fonts: List<String>,
    metadataPath: String?,
    outputPath: String,
    inputHeaders: Map<String, String>? = null,
    /** Master HLS URL — Android uses Media3 to export the full playlist when set. */
    masterPlaylistUrl: String? = null,
    /** When true (vibeplayer / PNG-wrapped segments), remux the local `.ts` file instead of exporting the master URL. */
    preferLocalTsRemux: Boolean = false,
    remoteHlsDurationSeconds: Double? = null,
    onRemoteHlsProgress: ((progressTimeMs: Long, durationSeconds: Double?) -> Unit)? = null,
): Boolean

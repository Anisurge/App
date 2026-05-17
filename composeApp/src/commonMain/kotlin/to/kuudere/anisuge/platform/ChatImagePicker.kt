package to.kuudere.anisuge.platform

/** Picked chat image within the 2.5 MB limit enforced before upload. */
data class ChatImagePick(
    val bytes: ByteArray,
    val mimeType: String,
    val fileName: String,
    val sizeBytes: Long,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ChatImagePick) return false
        return sizeBytes == other.sizeBytes && mimeType == other.mimeType &&
            fileName == other.fileName && bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int {
        var result = bytes.contentHashCode()
        result = 31 * result + mimeType.hashCode()
        result = 31 * result + fileName.hashCode()
        result = 31 * result + sizeBytes.hashCode()
        return result
    }
}

const val CHAT_IMAGE_MAX_BYTES: Long = (2.5 * 1024 * 1024).toLong()

@androidx.compose.runtime.Composable
expect fun rememberChatImagePicker(
    onResult: (ChatImagePick?) -> Unit,
): () -> Unit

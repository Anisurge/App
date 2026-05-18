package to.kuudere.anisuge.utils

/**
 * Vibeplayer / Anitaku CDN serves HLS segments as a tiny PNG header + MPEG-TS payload.
 * Players and muxers need the TS bytes only.
 */
object HlsPngTsStrip {
    private val PNG_SIGNATURE = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
    )

    fun isDisguisedTsCdnHost(url: String): Boolean {
        val host = url.substringAfter("://", "").substringBefore('/').lowercase()
        return host.contains("ibyteimg.com") || host.contains("byteimg.com")
    }

    fun stripSegmentPayloadIfNeeded(segmentUrl: String, raw: ByteArray): ByteArray {
        if (!isDisguisedTsCdnHost(segmentUrl)) return raw
        return stripPngTsWrapper(raw)
    }

    fun stripPngTsWrapper(raw: ByteArray): ByteArray {
        if (raw.size < 12 || !raw.hasPngSignature()) return raw
        var pos = 8
        while (pos + 12 <= raw.size) {
            val chunkLen = readUInt32Be(raw, pos)
            if (chunkLen < 0 || pos + 12L + chunkLen > raw.size) return raw
            val chunkType = raw.decodeToString(pos + 4, pos + 8)
            pos += 12 + chunkLen
            if (chunkType == "IEND") {
                if (pos >= raw.size) return raw
                val ts = raw.copyOfRange(pos, raw.size)
                return if (ts.size >= 188 && ts[0] == 0x47.toByte()) ts else raw
            }
        }
        return raw
    }

    private fun ByteArray.hasPngSignature(): Boolean {
        if (size < PNG_SIGNATURE.size) return false
        return PNG_SIGNATURE.indices.all { this[it] == PNG_SIGNATURE[it] }
    }

    private fun readUInt32Be(data: ByteArray, offset: Int): Int {
        if (offset + 4 > data.size) return -1
        return ((data[offset].toInt() and 0xFF) shl 24) or
            ((data[offset + 1].toInt() and 0xFF) shl 16) or
            ((data[offset + 2].toInt() and 0xFF) shl 8) or
            (data[offset + 3].toInt() and 0xFF)
    }
}

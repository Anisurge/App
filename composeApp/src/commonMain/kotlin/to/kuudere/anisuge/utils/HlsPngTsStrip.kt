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

    fun isVibeplayerHlsHost(url: String): Boolean {
        val host = url.substringAfter("://", "").substringBefore('/').lowercase()
        return host.contains("vibeplayer.site")
    }

    fun isAnikageHlsHost(url: String): Boolean {
        val host = url.substringAfter("://", "").substringBefore('/').lowercase()
        return host.contains("anikage")
    }

    /** Proxied AES HLS (Anikage): Media3 export via local header proxy, not raw segment concat. */
    fun prefersRemoteHlsExport(apiServer: String?, masterUrl: String?): Boolean {
        if (apiServer.equals("anikage", ignoreCase = true)) return true
        if (masterUrl != null && isAnikageHlsHost(masterUrl)) return true
        return false
    }

    /** Anitaku / vibeplayer HLS: download segments locally and remux; skip Media3 remote playlist export. */
    fun prefersLocalSegmentMux(
        masterUrl: String?,
        segmentUrls: List<String> = emptyList(),
        apiServer: String? = null,
    ): Boolean {
        if (apiServer.equals("anitaku", ignoreCase = true)) return true
        if (masterUrl != null && isVibeplayerHlsHost(masterUrl)) return true
        if (prefersRemoteHlsExport(apiServer, masterUrl)) return false
        return segmentUrls.any { isDisguisedTsCdnHost(it) }
    }

    /** Resolve relative `/proxy/…` and `segment.ts` paths against the playlist URL. */
    fun resolvePlaylistUrl(playlistUrl: String, reference: String): String {
        val ref = reference.trim()
        if (ref.startsWith("http://") || ref.startsWith("https://")) return ref
        if (ref.startsWith("/")) {
            val afterScheme = playlistUrl.substringAfter("://", missingDelimiterValue = playlistUrl)
            val host = afterScheme.substringBefore('/')
            val scheme = playlistUrl.substringBefore("://", missingDelimiterValue = "https")
            return "$scheme://$host$ref"
        }
        val base = playlistUrl.substringBeforeLast("/")
        return "$base/$ref"
    }

    fun stripSegmentPayloadIfNeeded(segmentUrl: String, raw: ByteArray): ByteArray {
        if (isDisguisedTsCdnHost(segmentUrl) || raw.hasPngSignature()) {
            return stripPngTsWrapper(raw)
        }
        return raw
    }

    fun stripPngTsWrapper(raw: ByteArray): ByteArray {
        if (raw.size < 12 || !raw.hasPngSignature()) return raw
        val offset = findTsStartOffset(raw, raw.size)
        if (offset > 0) {
            return raw.copyOfRange(offset, raw.size)
        }
        return raw
    }

    fun findTsStartOffset(raw: ByteArray, size: Int): Int {
        if (size < 12 || !raw.hasPngSignature()) return 0
        var pos = 8
        while (pos + 12 <= size) {
            val chunkLen = readUInt32Be(raw, pos)
            if (chunkLen < 0 || pos + 12L + chunkLen > size) return 0
            val chunkType = raw.decodeToString(pos + 4, pos + 8)
            pos += 12 + chunkLen
            if (chunkType == "IEND") {
                if (pos >= size) return 0
                return if (pos < size && raw[pos] == 0x47.toByte()) pos else 0
            }
        }
        return 0
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

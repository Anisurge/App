package to.kuudere.anisuge.utils

/**
 * Heuristics for the local playback proxy to decide when a progressive (non-HLS) MP4 stream
 * should be fetched with parallel byte-range chunks instead of a single sequential connection.
 *
 * The "All anime" (`allmanga`) server serves progressive MP4 from slow CDNs (Wix video CDN,
 * fast4speed). A single upstream connection throttles to that CDN's per-connection speed, which
 * is what causes buffering/lag during playback. Splitting each read into ranged chunks fetched
 * a few at a time — the same trick `DownloadManager` already uses for HLS segments — lifts the
 * effective throughput well above the single-stream cap.
 */
object ProgressiveStreamAccel {

    /** Max parallel range requests per accelerated stream. Mirrors the HLS download parallelism. */
    const val PARALLELISM = 4

    /** Size of each byte-range chunk requested upstream (1 MiB). */
    const val CHUNK_SIZE = 1L * 1024 * 1024

    /**
     * Whether a target URL should use the parallel range-fetch path.
     *
     * Restricted to known progressive-MP4 CDNs that support HTTP range requests. HLS playlists,
     * disguised PNG+TS segments and small `.ts`/`.jpg` segments are intentionally excluded — those
     * already arrive in small pieces and are handled by the existing playlist/segment paths.
     */
    fun shouldAccelerate(url: String): Boolean {
        val lower = url.substringBefore('#').lowercase()
        val path = lower.substringBefore('?')

        // Never range-split playlists or per-segment files — they are already chunked.
        if (path.endsWith(".m3u8") || path.endsWith(".ts") ||
            path.endsWith(".jpg") || path.endsWith(".jpeg")
        ) {
            return false
        }
        if (HlsPngTsStrip.isDisguisedTsCdnHost(url)) return false

        val host = url.substringAfter("://", "").substringBefore('/').lowercase()
        if (host.isEmpty()) return false

        // Explicit progressive MP4.
        if (path.endsWith(".mp4") || ".mp4?" in lower || "/mp4/" in lower) return true

        // All anime (`allmanga`) Wix video CDN — progressive MP4 that may omit the `.mp4` suffix.
        if (host == "video.wixstatic.com" || host.endsWith(".wixmp.com")) return true

        // All anime fast4speed direct video blobs (no explicit extension), but not their HLS.
        if (host == "tools.fast4speed.rsvp" || host.endsWith(".fast4speed.rsvp")) {
            if ("/videos/" in path && !path.endsWith(".m3u8")) return true
        }

        return false
    }
}

package to.kuudere.anisuge.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import to.kuudere.anisuge.data.models.ChatMessage
import to.kuudere.anisuge.data.services.AnimatedFrameBytesCache

/** Warms APNG frame bytes for chat avatars in parallel (history, live messages, profile). */
object ChatFramePrefetch {
    /** Keep headroom so Coil can load profile photos while frames prefetch. */
    private const val CONCURRENCY = 12

    fun entriesFrom(messages: Collection<ChatMessage>): List<Pair<String, String?>> {
        val seen = LinkedHashSet<String>()
        val entries = mutableListOf<Pair<String, String?>>()
        for (msg in messages) {
            val uid = msg.userId.takeIf { it.isNotBlank() }
            addEntry(entries, seen, msg.avatarFrameUrl, uid?.let { "${it}-ring" })
            addEntry(entries, seen, msg.avatarOuterUrl, uid?.let { "${it}-outer" })
        }
        return entries
    }

    fun entriesFromProfile(
        frameUrl: String?,
        outerFrameUrl: String?,
        userId: String?,
    ): List<Pair<String, String?>> {
        val seen = LinkedHashSet<String>()
        val entries = mutableListOf<Pair<String, String?>>()
        val uid = userId?.takeIf { it.isNotBlank() }
        addEntry(entries, seen, frameUrl, uid?.let { "${it}-ring" })
        addEntry(entries, seen, outerFrameUrl, uid?.let { "${it}-outer" })
        return entries
    }

    private fun addEntry(
        out: MutableList<Pair<String, String?>>,
        seen: LinkedHashSet<String>,
        raw: String?,
        cacheKey: String?,
    ) {
        val resolved = resolveProfileMediaUrl(raw) ?: return
        if (!seen.add(resolved)) return
        out.add(resolved to cacheKey)
    }

    suspend fun prefetch(messages: Collection<ChatMessage>) {
        prefetchEntries(entriesFrom(messages))
    }

    suspend fun prefetchEntries(entries: List<Pair<String, String?>>) {
        if (entries.isEmpty()) return
        withContext(Dispatchers.Default) {
            AnimatedFrameBytesCache.prefetchEntries(entries, concurrency = CONCURRENCY)
        }
    }
}

package to.kuudere.anisuge.data.services

import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okio.buffer
import okio.use
import to.kuudere.anisuge.AppComponent
import to.kuudere.anisuge.platform.KmpFileSystem
import to.kuudere.anisuge.utils.getCacheDirectory

/** In-memory + disk cache for animated shop/chat frame bytes (APNG served as PNG). */
object AnimatedFrameBytesCache {
    private const val MAX_MEMORY_ENTRIES = 10
    private const val MAX_MEMORY_BYTES = 5 * 1024 * 1024L // ~5MB raw compressed; decoded frames cost more
    @Volatile private var memoryBytes: Long = 0L

    private val memoryLock = Any()
    private val memory = object : LinkedHashMap<String, ByteArray>(MAX_MEMORY_ENTRIES + 1, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ByteArray>?): Boolean {
            if (size > MAX_MEMORY_ENTRIES) return true
            // Also evict if over byte budget (checked on put)
            return false
        }
    }
    private val loadLocks = mutableMapOf<String, Mutex>()
    private val loadLocksGuard = Mutex()

    private fun framesDir(): String = "${getCacheDirectory()}/animated-frames"

    private fun diskPath(url: String): String {
        val safe = url.hashCode().toUInt().toString(16)
        return "${framesDir()}/$safe.apng"
    }

    fun peekMemory(url: String): ByteArray? = synchronized(memoryLock) { memory[url] }

    private fun putMemory(url: String, bytes: ByteArray) {
        if (bytes.isEmpty()) return
        synchronized(memoryLock) {
            val existing = memory[url]
            if (existing != null) {
                memoryBytes -= existing.size
            }
            memory[url] = bytes
            memoryBytes += bytes.size
            evictWhileOverLimitLocked()
        }
    }

    private fun evictWhileOverLimitLocked() {
        // Access-order map: oldest are first in iteration
        val it = memory.entries.iterator()
        while ((memory.size > MAX_MEMORY_ENTRIES || memoryBytes > MAX_MEMORY_BYTES) && it.hasNext()) {
            val e = it.next()
            memoryBytes -= e.value.size
            it.remove()
        }
        if (memoryBytes < 0) memoryBytes = 0
    }

    fun clearMemory() {
        synchronized(memoryLock) {
            memory.clear()
            memoryBytes = 0
        }
    }

    private fun readFile(path: String): ByteArray? {
        if (!KmpFileSystem.exists(path)) return null
        return runCatching {
            KmpFileSystem.source(path).buffer().use { it.readByteArray() }
        }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    private fun readDisk(url: String): ByteArray? = readFile(diskPath(url))

    private fun writeDisk(url: String, bytes: ByteArray) {
        val base = framesDir()
        if (!KmpFileSystem.exists(base)) {
            KmpFileSystem.createDirectories(base, mustCreate = true)
        }
        KmpFileSystem.write(diskPath(url), bytes)
    }

    private suspend fun lockFor(url: String): Mutex =
        loadLocksGuard.withLock {
            loadLocks.getOrPut(url) { Mutex() }
        }

    suspend fun load(url: String, itemId: String? = null): ByteArray? {
        peekMemory(url)?.let { return it }

        itemId?.let { id ->
            ShopFrameCache.localPathIfExists(id)?.let { path ->
                readFile(path)?.let { bytes ->
                    putMemory(url, bytes)
                    return bytes
                }
            }
        }

        readDisk(url)?.let { bytes ->
            putMemory(url, bytes)
            return bytes
        }

        return lockFor(url).withLock {
            peekMemory(url)?.let { return@withLock it }
            readDisk(url)?.let { bytes ->
                putMemory(url, bytes)
                return@withLock bytes
            }

            val downloaded = withContext(Dispatchers.IO) {
                runCatching {
                    AppComponent.httpClient.get(url).body<ByteArray>()
                }.getOrNull()?.takeIf { it.isNotEmpty() }
            } ?: return@withLock null

            putMemory(url, downloaded)
            withContext(Dispatchers.IO) {
                writeDisk(url, downloaded)
                itemId?.let { ShopFrameCache.save(it, downloaded) }
            }
            downloaded
        }
    }

    suspend fun store(url: String, bytes: ByteArray, itemId: String? = null) {
        if (bytes.isEmpty()) return
        putMemory(url, bytes)
        withContext(Dispatchers.IO) {
            writeDisk(url, bytes)
            itemId?.let { ShopFrameCache.save(it, bytes) }
        }
    }

    suspend fun prefetch(urls: List<String>, concurrency: Int = 3) {
        prefetchEntries(
            urls.map { url -> url to null },
            concurrency = concurrency,
        )
    }

    /**
     * Prefetch for UI that will be shown soon (chat, comments, equipped). Warms memory LRU.
     */
    suspend fun prefetchEntries(
        entries: List<Pair<String, String?>>,
        concurrency: Int = 3,
    ) {
        val pending = entries
            .mapNotNull { (rawUrl, itemId) ->
                val url = rawUrl.trim()
                if (url.isEmpty()) null else url to itemId
            }
            .distinctBy { it.first }
            .filter { (url, _) -> peekMemory(url) == null && readDisk(url) == null }
        if (pending.isEmpty()) return

        coroutineScope {
            val gate = Semaphore(concurrency)
            pending.map { (url, itemId) ->
                async {
                    gate.withPermit { load(url, itemId = itemId) }
                }
            }.awaitAll()
        }
    }

    /**
     * Warm only disk cache for bulk catalogs (shop store grid). Does not bloat memory LRU.
     * Displayed items will promote to memory via normal load() when rendered.
     */
    suspend fun warmDiskOnly(entries: List<Pair<String, String?>>, concurrency: Int = 3) {
        val pending = entries
            .mapNotNull { (rawUrl, itemId) ->
                val url = rawUrl.trim()
                if (url.isEmpty()) null else url to itemId
            }
            .distinctBy { it.first }
            .filter { (url, _) -> readDisk(url) == null }
        if (pending.isEmpty()) return

        coroutineScope {
            val gate = Semaphore(concurrency)
            pending.map { (url, itemId) ->
                async {
                    gate.withPermit {
                        lockFor(url).withLock {
                            if (readDisk(url) != null) return@withLock
                            val downloaded = withContext(Dispatchers.IO) {
                                runCatching {
                                    AppComponent.httpClient.get(url).body<ByteArray>()
                                }.getOrNull()?.takeIf { it.isNotEmpty() }
                            } ?: return@withLock
                            withContext(Dispatchers.IO) {
                                writeDisk(url, downloaded)
                                itemId?.let { ShopFrameCache.save(it, downloaded) }
                            }
                            // Intentionally NOT putMemory — only promote on actual UI use
                        }
                    }
                }
            }.awaitAll()
        }
    }

    /** Called from low-memory callbacks to release decoded-frame byte buffers. */
    fun onLowMemory() {
        clearMemory()
    }
}

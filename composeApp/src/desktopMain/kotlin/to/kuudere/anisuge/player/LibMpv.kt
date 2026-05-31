package to.kuudere.anisuge.player

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer

@com.sun.jna.Structure.FieldOrder("type", "data")
open class mpv_render_param : com.sun.jna.Structure() {
    @JvmField var type: Int = 0
    @JvmField var data: Pointer? = null
    override fun toArray(size: Int): Array<mpv_render_param> {
        return super.toArray(size) as Array<mpv_render_param>
    }
}

/**
 * Raw JNA bindings to libmpv's C API (client.h).
 * Only the functions we actually need are mapped — the rest can be added later.
 *
 * libmpv license: ISC (client API) / LGPLv2+ (core, when built with -Dgpl=false)
 */
internal interface LibMpv : Library {

    // ── Lifecycle ────────────────────────────────────────────────────────────
    fun mpv_create(): Pointer?
    fun mpv_initialize(ctx: Pointer): Int
    fun mpv_terminate_destroy(ctx: Pointer)

    // ── Options / properties ─────────────────────────────────────────────────
    fun mpv_set_option_string(ctx: Pointer, name: String, data: String): Int
    fun mpv_set_property_string(ctx: Pointer, name: String, data: String): Int
    fun mpv_get_property_string(ctx: Pointer, name: String): Pointer?
    fun mpv_free(data: Pointer)

    // ── Commands ─────────────────────────────────────────────────────────────
    fun mpv_command(ctx: Pointer, args: Array<String?>): Int

    // ── Events ───────────────────────────────────────────────────────────────
    fun mpv_wait_event(ctx: Pointer, timeout: Double): Pointer

    // ── Render API ───────────────────────────────────────────────────────────
    fun mpv_render_context_create(res: com.sun.jna.ptr.PointerByReference, mpv: Pointer, params: mpv_render_param): Int
    fun mpv_render_context_free(ctx: Pointer)
    
    interface mpv_render_update_fn : com.sun.jna.Callback {
        fun invoke(cb_ctx: Pointer?)
    }
    fun mpv_render_context_set_update_callback(ctx: Pointer, callback: mpv_render_update_fn, cb_ctx: Pointer?)
    fun mpv_render_context_update(ctx: Pointer): Long
    fun mpv_render_context_render(ctx: Pointer, params: mpv_render_param): Int

    companion object {
        const val MPV_EVENT_NONE        = 0
        const val MPV_EVENT_SHUTDOWN    = 1
        const val MPV_EVENT_LOG_MESSAGE = 2
        const val MPV_EVENT_START_FILE  = 6
        const val MPV_EVENT_END_FILE    = 7
        const val MPV_EVENT_FILE_LOADED = 8
        const val MPV_EVENT_IDLE        = 11
        const val MPV_EVENT_TICK        = 14

        const val MPV_FORMAT_NONE   = 0
        const val MPV_FORMAT_STRING = 1
        const val MPV_FORMAT_OSD    = 2
        const val MPV_FORMAT_FLAG   = 3
        const val MPV_FORMAT_INT64  = 4
        const val MPV_FORMAT_DOUBLE = 5

        /**
         * Load libmpv. First sets LC_NUMERIC to "C" (required by mpv),
         * then tries system lib, then bundled fallback.
         */
        fun load(): LibMpv? {
            // mpv REQUIRES LC_NUMERIC=C. This is now set at app startup in main.kt
            // via forceLocaleC(), but we still try the system library first.

            // 1. Try system library (uses JNA's default search path)
            tryLoad("mpv")?.let { return it }

            val osName  = System.getProperty("os.name").lowercase()

            // 1b. macOS: JNA's default search path does NOT include Homebrew's
            // /opt/homebrew/lib (Apple Silicon) or /usr/local/lib (Intel), so a
            // `brew install mpv` is invisible to Native.load("mpv"). Try the known
            // install locations explicitly by absolute path. libmpv ships as
            // libmpv.2.dylib (versioned) with an unversioned libmpv.dylib symlink.
            if ("mac" in osName) {
                val macCandidates = listOf(
                    "/opt/homebrew/lib/libmpv.dylib",
                    "/opt/homebrew/lib/libmpv.2.dylib",
                    "/usr/local/lib/libmpv.dylib",
                    "/usr/local/lib/libmpv.2.dylib",
                )
                for (path in macCandidates) {
                    if (java.io.File(path).exists()) {
                        tryLoad(path)?.let {
                            println("[LibMpv] loaded macOS system libmpv from $path")
                            return it
                        }
                    }
                }
            }

            // 2. Try extracting bundled binary from resources
            val libName = when {
                "linux"  in osName -> "libmpv.so.2"
                "win"    in osName -> "mpv-2.dll"
                "mac"    in osName -> "libmpv.dylib"
                else               -> return null
            }
            return try {
                val loader = LibMpv::class.java.classLoader ?: return null
                val nativePrefix = "native/"
                val tmpDir = java.io.File(
                    System.getProperty("java.io.tmpdir"),
                    "anisurge-libmpv-${ProcessHandle.current().pid()}"
                ).also { it.mkdirs() }

                fun extractEntry(entryName: String) {
                    val fileName = entryName.removePrefix(nativePrefix)
                    if (!fileName.endsWith(".dll", ignoreCase = true)) return
                    val stream = loader.getResourceAsStream(entryName) ?: return
                    val outFile = java.io.File(tmpDir, fileName)
                    stream.use { input -> outFile.outputStream().use { input.copyTo(it) } }
                }

                val nativeRoot = loader.getResource(nativePrefix)
                if (nativeRoot?.protocol == "jar") {
                    val jarConn = nativeRoot.openConnection() as java.net.JarURLConnection
                    jarConn.jarFile.use { jar ->
                        jar.entries().asSequence()
                            .filter { !it.isDirectory && it.name.startsWith(nativePrefix) }
                            .forEach { extractEntry(it.name) }
                    }
                } else if (nativeRoot != null) {
                    java.io.File(nativeRoot.toURI()).listFiles()
                        ?.filter { it.isFile && it.name.endsWith(".dll", ignoreCase = true) }
                        ?.forEach { dll -> dll.copyTo(java.io.File(tmpDir, dll.name), overwrite = true) }
                } else {
                    extractEntry("$nativePrefix$libName")
                }

                val primary = java.io.File(tmpDir, libName)
                if (!primary.isFile) return null
                tryLoad(primary.absolutePath)
            } catch (e: Exception) {
                null
            }
        }

        private fun tryLoad(name: String): LibMpv? = try {
            Native.load(name, LibMpv::class.java) as LibMpv
        } catch (e: UnsatisfiedLinkError) {
            null
        }
    }
}

/** Offset within mpv_event struct to find the event_id (first field, int32). */
internal fun Pointer.mpvEventId(): Int = this.getInt(0)


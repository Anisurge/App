package to.kuudere.anisuge.platform

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import to.kuudere.anisuge.BuildConfig
import java.io.File
import java.io.RandomAccessFile
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.ByteChannel
import java.nio.channels.SocketChannel
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean

actual object DiscordRichPresenceManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val clientId = BuildConfig.DISCORD_CLIENT_ID.trim()
    private val largeImageKey = BuildConfig.DISCORD_LARGE_IMAGE_KEY.trim().ifBlank { "logo" }
    private val smallImageKey = BuildConfig.DISCORD_SMALL_IMAGE_KEY.trim().ifBlank { "play" }
    private val enabled = clientId.isNotBlank()

    private var connection: DiscordIpcConnection? = null
    private var lastPayload: String? = null
    private val connecting = AtomicBoolean(false)

    actual fun configureMobile(enabled: Boolean, token: String) = Unit

    actual fun update(activity: DiscordPresenceActivity) {
        if (!enabled) return
        scope.launch {
            runCatching {
                val payload = activityPayload(activity)
                if (payload == lastPayload) return@launch
                val ipc = ensureConnected() ?: return@launch
                ipc.write(opcode = 1, payload)
                lastPayload = payload
            }.onFailure { disconnect() }
        }
    }

    actual fun clear() {
        if (!enabled) return
        scope.launch {
            runCatching {
                val ipc = ensureConnected() ?: return@launch
                ipc.write(opcode = 1, clearPayload())
                lastPayload = null
            }.onFailure { disconnect() }
        }
    }

    actual fun shutdown() {
        runCatching { connection?.write(opcode = 2, payload = "{}") }
        disconnect()
    }

    private fun ensureConnected(): DiscordIpcConnection? {
        connection?.takeIf { it.isOpen }?.let { return it }
        if (!connecting.compareAndSet(false, true)) return null
        return try {
            val ipc = openDiscordConnection() ?: return null
            ipc.write(opcode = 0, payload = "{\"v\":1,\"client_id\":\"$clientId\"}")
            ipc.readFrame()
            connection = ipc
            ipc
        } catch (_: Exception) {
            disconnect()
            null
        } finally {
            connecting.set(false)
        }
    }

    private fun disconnect() {
        runCatching { connection?.close() }
        connection = null
        lastPayload = null
    }

    private fun activityPayload(activity: DiscordPresenceActivity): String {
        val activityJson = buildJsonObject {
            put("type", 3)
            put("details", activity.details.trimPresenceField(fallback = "Watching anime"))
            put("state", activity.state.trimPresenceField(fallback = "On Anisurge"))
            putJsonObject("assets") {
                put("large_image", largeImageKey)
                put("large_text", activity.largeImageText.trimPresenceField(fallback = "Anisurge"))
                if (smallImageKey.isNotBlank()) put("small_image", smallImageKey)
                activity.smallImageText?.trimPresenceField()?.let { put("small_text", it) }
            }
            val timestamps = buildJsonObject {
                activity.startTimestampMillis?.let { put("start", it) }
                activity.endTimestampMillis?.let { put("end", it) }
            }
            if (timestamps.isNotEmpty()) put("timestamps", timestamps)
        }

        return packetPayload(JsonObject(mapOf("activity" to activityJson)))
    }

    private fun clearPayload(): String = packetPayload(JsonObject(mapOf("activity" to JsonNull)))

    private fun packetPayload(args: JsonObject): String {
        val payload = buildJsonObject {
            put("cmd", "SET_ACTIVITY")
            putJsonObject("args") {
                put("pid", ProcessHandle.current().pid())
                args.forEach { (key, value) -> put(key, value) }
            }
            put("nonce", System.nanoTime().toString())
        }
        return payload.toString()
    }
}

private fun String.trimPresenceField(fallback: String? = null): String? {
    val value = trim().ifBlank { fallback ?: return null }
    return value.take(128).let { if (it.length == 1) "$it " else it }
}

private fun openDiscordConnection(): DiscordIpcConnection? {
    val os = System.getProperty("os.name").lowercase()
    return if (os.contains("win")) openWindowsConnection() else openUnixConnection()
}

private fun openUnixConnection(): DiscordIpcConnection? {
    val bases = buildList {
        listOf("XDG_RUNTIME_DIR", "TMPDIR", "TMP", "TEMP").mapNotNullTo(this) { System.getenv(it) }
        add("/tmp")
        add("/var/tmp")
        val userId = System.getProperty("user.name")
        add("/run/user/$userId")
    }.distinct()

    val candidates = bases.flatMap { base ->
        val dir = File(base)
        buildList {
            add(dir)
            dir.listFiles()?.filter { it.isDirectory && it.name.contains("discord", ignoreCase = true) }?.let(::addAll)
            File(dir, "app/com.discordapp.Discord").takeIf { it.exists() }?.let(::add)
        }
    }.distinctBy { it.absolutePath }

    for (dir in candidates) {
        for (i in 0..9) {
            val socket = File(dir, "discord-ipc-$i")
            if (!socket.exists()) continue
            runCatching {
                val channel = SocketChannel.open(StandardProtocolFamily.UNIX)
                channel.connect(UnixDomainSocketAddress.of(socket.toPath()))
                return DiscordIpcConnection(channel)
            }
        }
    }
    return null
}

private fun openWindowsConnection(): DiscordIpcConnection? {
    for (i in 0..9) {
        runCatching {
            val pipe = RandomAccessFile("\\\\.\\pipe\\discord-ipc-$i", "rw")
            return DiscordIpcConnection(pipe.channel)
        }
    }
    return null
}

private class DiscordIpcConnection(private val channel: ByteChannel) {
    val isOpen: Boolean get() = channel.isOpen

    fun write(opcode: Int, payload: String) {
        val bytes = payload.toByteArray(StandardCharsets.UTF_8)
        val header = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(opcode)
            .putInt(bytes.size)
            .flip() as ByteBuffer
        writeFully(header)
        writeFully(ByteBuffer.wrap(bytes))
    }

    fun readFrame(): String? {
        val header = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        if (!readFully(header)) return null
        header.flip()
        header.int
        val length = header.int
        if (length <= 0) return ""
        val body = ByteBuffer.allocate(length)
        if (!readFully(body)) return null
        body.flip()
        return StandardCharsets.UTF_8.decode(body).toString()
    }

    fun close() {
        channel.close()
    }

    private fun writeFully(buffer: ByteBuffer) {
        while (buffer.hasRemaining()) channel.write(buffer)
    }

    private fun readFully(buffer: ByteBuffer): Boolean {
        while (buffer.hasRemaining()) {
            if (channel.read(buffer) < 0) return false
        }
        return true
    }
}

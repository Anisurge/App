package to.kuudere.anisuge.player

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketException
import java.net.URI
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

internal class LocalStreamProxy {
    private companion object {
        const val DEFAULT_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }

    private data class Session(val headers: Map<String, String>)

    private val sessions = ConcurrentHashMap<String, Session>()
    private val urls = ConcurrentHashMap<String, String>()
    private val targets = ConcurrentHashMap<String, String>()

    @Volatile private var serverSocket: ServerSocket? = null
    @Volatile private var serverPort: Int = 0

    fun proxyUrl(url: String, headers: Map<String, String>?): String {
        if (headers.isNullOrEmpty()) return url

        val sessionId = UUID.randomUUID().toString()
        sessions[sessionId] = Session(headers)
        urls[url] = sessionId
        ensureStarted()
        return proxiedUrl(sessionId, url)
    }

    fun release(url: String) {
        urls.remove(url)?.let { sessionId ->
            sessions.remove(sessionId)
            targets.keys.removeAll { it.startsWith("$sessionId:") }
        }
        if (sessions.isEmpty()) stop()
    }

    private fun ensureStarted() {
        if (serverSocket != null) return
        synchronized(this) {
            if (serverSocket != null) return
            val socket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
            serverSocket = socket
            serverPort = socket.localPort
            thread(name = "anisurge-stream-proxy", isDaemon = true) {
                while (!socket.isClosed) {
                    try {
                        val client = socket.accept()
                        thread(name = "anisurge-stream-proxy-client", isDaemon = true) {
                            try {
                                handleClient(client)
                            } catch (_: SocketException) {
                            } catch (_: Exception) {
                            }
                        }
                    } catch (_: Exception) {
                        if (!socket.isClosed) stop()
                    }
                }
            }
        }
    }

    private fun stop() {
        synchronized(this) {
            try {
                serverSocket?.close()
            } catch (_: Exception) {
            }
            serverSocket = null
            serverPort = 0
        }
    }

    private fun handleClient(client: java.net.Socket) {
        client.use { socket ->
            val input = BufferedInputStream(socket.getInputStream())
            val output = BufferedOutputStream(socket.getOutputStream())
            val requestLine = readLine(input) ?: return
            val parts = requestLine.split(' ')
            if (parts.size < 2 || parts[0] != "GET") {
                writeStatus(output, 405, "Method Not Allowed")
                return
            }

            val requestHeaders = mutableMapOf<String, String>()
            while (true) {
                val line = readLine(input) ?: return
                if (line.isEmpty()) break
                val separator = line.indexOf(':')
                if (separator > 0) {
                    requestHeaders[line.substring(0, separator).trim().lowercase()] = line.substring(separator + 1).trim()
                }
            }

            val path = parts[1]
            val pathParts = path.substringBefore('?').substringAfter("/proxy/", "").split('/')
            val sessionId = pathParts.getOrNull(0).orEmpty()
            val targetId = pathParts.getOrNull(1).orEmpty()
            val targetUrl = targets["$sessionId:$targetId"]
            val session = sessions[sessionId]
            if (session == null || targetUrl == null) {
                writeStatus(output, 404, "Not Found")
                return
            }

            proxyRequest(output, targetUrl, sessionId, session, requestHeaders["range"])
        }
    }

    private fun proxyRequest(output: BufferedOutputStream, targetUrl: String, sessionId: String, session: Session, range: String?) {
        val connection = (URL(targetUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            session.headers.forEach { (name, value) -> setRequestProperty(name, value) }
            if (session.headers.keys.none { it.equals("User-Agent", ignoreCase = true) }) {
                setRequestProperty("User-Agent", DEFAULT_USER_AGENT)
            }
            if (session.headers.keys.none { it.equals("Accept", ignoreCase = true) }) {
                setRequestProperty("Accept", "*/*")
            }
            range?.let { setRequestProperty("Range", it) }
        }

        try {
            val status = connection.responseCode
            val contentType = connection.contentType.orEmpty()
            val source = if (status >= 400) connection.errorStream else connection.inputStream
            if (source == null) {
                writeStatus(output, status, connection.responseMessage ?: "OK")
                return
            }

            if (isPlaylist(targetUrl, contentType)) {
                val body = source.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
                val rewritten = rewritePlaylist(body, targetUrl, sessionId)
                val bytes = rewritten.toByteArray(StandardCharsets.UTF_8)
                writeHeaders(output, 200, "OK", "application/vnd.apple.mpegurl", bytes.size.toLong())
                output.write(bytes)
            } else {
                val responseHeaders = listOfNotNull(
                    connection.getHeaderField("Content-Range")?.let { "Content-Range" to it },
                    connection.getHeaderField("Accept-Ranges")?.let { "Accept-Ranges" to it }
                )
                writeHeaders(
                    output,
                    status,
                    connection.responseMessage ?: "OK",
                    proxiedContentType(targetUrl, contentType),
                    connection.contentLengthLong,
                    responseHeaders
                )
                source.use { it.copyTo(output) }
            }
            output.flush()
        } catch (_: SocketException) {
        } catch (_: Exception) {
            try {
                writeStatus(output, 502, "Bad Gateway")
            } catch (_: Exception) {
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun rewritePlaylist(body: String, baseUrl: String, sessionId: String): String {
        return body.lineSequence().joinToString("\n") { line ->
            val trimmed = line.trim()
            when {
                trimmed.isEmpty() -> line
                trimmed.startsWith("#") && trimmed.contains("URI=\"") -> rewriteUriAttribute(line, baseUrl, sessionId)
                trimmed.startsWith("#") -> line
                else -> proxiedUrl(sessionId, URI(baseUrl).resolve(trimmed).toString())
            }
        }
    }

    private fun rewriteUriAttribute(line: String, baseUrl: String, sessionId: String): String {
        val marker = "URI=\""
        val start = line.indexOf(marker)
        if (start < 0) return line
        val uriStart = start + marker.length
        val uriEnd = line.indexOf('"', uriStart)
        if (uriEnd < 0) return line
        val original = line.substring(uriStart, uriEnd)
        val rewritten = proxiedUrl(sessionId, URI(baseUrl).resolve(original).toString())
        return line.substring(0, uriStart) + rewritten + line.substring(uriEnd)
    }

    private fun proxiedUrl(sessionId: String, targetUrl: String): String {
        val targetId = UUID.randomUUID().toString()
        targets["$sessionId:$targetId"] = targetUrl
        return "http://127.0.0.1:$serverPort/proxy/$sessionId/$targetId${proxyPathSuffix(targetUrl)}"
    }

    private fun proxyPathSuffix(targetUrl: String): String {
        val lowerUrl = targetUrl.substringBefore('?').lowercase()
        return when {
            lowerUrl.endsWith(".m3u8") -> "/playlist.m3u8"
            lowerUrl.endsWith(".jpg") || lowerUrl.endsWith(".jpeg") -> "/segment.ts"
            else -> "/stream"
        }
    }

    private fun isPlaylist(url: String, contentType: String): Boolean {
        val lowerUrl = url.substringBefore('?').lowercase()
        val lowerType = contentType.lowercase()
        return lowerUrl.endsWith(".m3u8") || lowerType.contains("mpegurl") || lowerType.contains("application/vnd.apple")
    }

    private fun proxiedContentType(url: String, contentType: String): String {
        val lowerUrl = url.substringBefore('?').lowercase()
        return if (lowerUrl.endsWith(".jpg") || lowerUrl.endsWith(".jpeg")) {
            "video/mp2t"
        } else {
            contentType.ifBlank { "application/octet-stream" }
        }
    }

    private fun writeHeaders(
        output: BufferedOutputStream,
        status: Int,
        message: String,
        contentType: String,
        contentLength: Long,
        extraHeaders: List<Pair<String, String>> = emptyList()
    ) {
        output.write("HTTP/1.1 $status $message\r\n".toByteArray())
        output.write("Content-Type: $contentType\r\n".toByteArray())
        if (contentLength >= 0) output.write("Content-Length: $contentLength\r\n".toByteArray())
        extraHeaders.forEach { (name, value) -> output.write("$name: $value\r\n".toByteArray()) }
        output.write("Connection: close\r\n".toByteArray())
        output.write("Access-Control-Allow-Origin: *\r\n".toByteArray())
        output.write("\r\n".toByteArray())
    }

    private fun writeStatus(output: BufferedOutputStream, status: Int, message: String) {
        val bytes = message.toByteArray(StandardCharsets.UTF_8)
        writeHeaders(output, status, message, "text/plain; charset=utf-8", bytes.size.toLong())
        output.write(bytes)
        output.flush()
    }

    private fun readLine(input: BufferedInputStream): String? {
        val bytes = mutableListOf<Byte>()
        while (true) {
            val value = input.read()
            if (value == -1) return if (bytes.isEmpty()) null else bytes.toByteArray().toString(StandardCharsets.UTF_8)
            if (value == '\n'.code) break
            if (value != '\r'.code) bytes.add(value.toByte())
        }
        return bytes.toByteArray().toString(StandardCharsets.UTF_8)
    }

    private fun String.urlEncode(): String = URLEncoder.encode(this, StandardCharsets.UTF_8.name())
    private fun String.urlDecode(): String = URLDecoder.decode(this, StandardCharsets.UTF_8.name())
}

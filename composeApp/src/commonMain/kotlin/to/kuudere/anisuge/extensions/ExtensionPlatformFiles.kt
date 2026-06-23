package to.kuudere.anisuge.extensions

import io.ktor.client.HttpClient

expect object ExtensionPlatformFiles {
    val isAndroid: Boolean
    fun rootDir(): String
    suspend fun download(client: HttpClient, url: String, destination: String)
    fun delete(path: String): Boolean
}

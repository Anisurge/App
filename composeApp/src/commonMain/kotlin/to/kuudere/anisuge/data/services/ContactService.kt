package to.kuudere.anisuge.data.services

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import to.kuudere.anisuge.data.network.ApiConfig

@Serializable
data class ContactRequest(
    val title: String,
    val message: String,
    val email: String? = null,
)

class ContactService(
    private val httpClient: HttpClient,
) {
    suspend fun submitContact(title: String, message: String, email: String? = null): Boolean {
        return try {
            val response = httpClient.post("${ApiConfig.API_BASE}/contact") {
                contentType(ContentType.Application.Json)
                setBody(ContactRequest(title, message, email))
            }
            response.status.value in 200..299
        } catch (e: Exception) {
            println("[ContactService] submitContact error: ${e.message}")
            false
        }
    }
}

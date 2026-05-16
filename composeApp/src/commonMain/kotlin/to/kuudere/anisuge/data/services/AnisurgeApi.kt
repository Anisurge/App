package to.kuudere.anisuge.data.services

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import to.kuudere.anisuge.AppComponent
import to.kuudere.anisuge.data.models.SessionInfo

internal object AnisurgeApi {
    val v1Base: String = "${AppComponent.ANISURGE_API_URL}/v1"

    fun HttpRequestBuilder.applyAnisurgeAuth(session: SessionInfo) {
        val jwt = session.anisurgeToken?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Missing Anisurge session token")
        header(HttpHeaders.Authorization, "Bearer $jwt")
    }

    fun HttpRequestBuilder.applyProjectRAuth(session: SessionInfo) {
        header(HttpHeaders.Authorization, "Bearer ${session.token}")
    }
}

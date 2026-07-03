package to.kuudere.anisuge.data.services

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import to.kuudere.anisuge.AppComponent
import to.kuudere.anisuge.data.models.SessionInfo

internal object AnisurgeApi {
    private var _customBaseUrl: String? = null

    val v1Base: String
        get() = "${_customBaseUrl ?: AppComponent.ANISURGE_API_URL}/v1"

    fun setBaseUrl(url: String) { _customBaseUrl = url.trimEnd('/').ifBlank { null } }

    val resolvedBaseUrl: String
        get() = _customBaseUrl ?: AppComponent.ANISURGE_API_URL

    val isCustomDomain: Boolean
        get() = _customBaseUrl != null

    fun HttpRequestBuilder.applyAnisurgeAuth(session: SessionInfo) {
        val jwt = session.anisurgeToken?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Missing Anisurge session token")
        header(HttpHeaders.Authorization, "Bearer $jwt")
    }

    fun HttpRequestBuilder.applyProjectRAuth(session: SessionInfo) {
        header(HttpHeaders.Authorization, "Bearer ${session.token}")
    }
}

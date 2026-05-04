package to.kuudere.anisuge.data.network

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders

object ApiConfig {
    const val BASE_URL = "https://api.reanime.to"
    const val API_PREFIX = "/api/v1"
    const val API_BASE = "$BASE_URL$API_PREFIX"
}

fun HttpRequestBuilder.bearer(token: String?) {
    val t = token?.trim().orEmpty()
    if (t.isNotEmpty()) {
        header(HttpHeaders.Authorization, "Bearer $t")
    }
}

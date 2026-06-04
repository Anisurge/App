package to.kuudere.anisuge.data.services

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import to.kuudere.anisuge.data.models.BffErrorResponse
import to.kuudere.anisuge.data.models.BffShopMeResponse
import to.kuudere.anisuge.data.models.BffShopPurchaseRequest
import to.kuudere.anisuge.data.models.BffShopPurchaseResponse
import to.kuudere.anisuge.data.models.StickerMeResponse
import to.kuudere.anisuge.data.services.AnisurgeApi.applyAnisurgeAuth

class StickerService(
    private val sessionStore: SessionStore,
    private val httpClient: HttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchMine(): Result<StickerMeResponse> {
        val stored = sessionStore.get() ?: return Result.failure(IllegalStateException("Not signed in"))
        return try {
            val response = httpClient.get("${AnisurgeApi.v1Base}/stickers/me") {
                applyAnisurgeAuth(stored)
            }
            if (response.status.isSuccess()) {
                Result.success(response.body())
            } else {
                Result.failure(Exception(errorMessage(response.bodyAsText())))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchCatalog(
        catalogLimit: Int = 25,
        catalogOffset: Int = 0,
    ): Result<BffShopMeResponse> {
        val stored = sessionStore.get() ?: return Result.failure(IllegalStateException("Not signed in"))
        return try {
            val response = httpClient.get(
                "${AnisurgeApi.v1Base}/stickers/catalog?catalogLimit=$catalogLimit&catalogOffset=$catalogOffset",
            ) {
                applyAnisurgeAuth(stored)
            }
            if (response.status.isSuccess()) {
                Result.success(response.body())
            } else {
                Result.failure(Exception(errorMessage(response.bodyAsText())))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun purchase(itemId: String): Result<BffShopPurchaseResponse> {
        val stored = sessionStore.get() ?: return Result.failure(IllegalStateException("Not signed in"))
        return try {
            val response = httpClient.post("${AnisurgeApi.v1Base}/stickers/purchase") {
                applyAnisurgeAuth(stored)
                contentType(ContentType.Application.Json)
                setBody(BffShopPurchaseRequest(itemId))
            }
            if (response.status.isSuccess()) {
                Result.success(response.body())
            } else {
                Result.failure(Exception(errorMessage(response.bodyAsText())))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun errorMessage(raw: String): String = runCatching {
        json.decodeFromString<BffErrorResponse>(raw).displayMessage()
    }.getOrElse { "Request failed" }
}

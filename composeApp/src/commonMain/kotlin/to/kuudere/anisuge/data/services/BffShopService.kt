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
import to.kuudere.anisuge.data.models.BffShopDownloadAllResponse
import to.kuudere.anisuge.data.models.BffShopMeResponse
import to.kuudere.anisuge.data.models.BffShopPurchaseRequest
import to.kuudere.anisuge.data.models.BffShopPurchaseResponse
import to.kuudere.anisuge.data.models.BffShopRedeemRequest
import to.kuudere.anisuge.data.models.BffShopRedeemResponse
import to.kuudere.anisuge.data.models.UserProfile
import to.kuudere.anisuge.data.models.toUserProfile
import to.kuudere.anisuge.data.services.AnisurgeApi.applyAnisurgeAuth

class BffShopService(
    private val sessionStore: SessionStore,
    private val httpClient: HttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchShopMe(
        catalogLimit: Int = 25,
        catalogOffset: Int = 0,
    ): Result<BffShopMeResponse> = authedGet(
        "/shop/me?catalogLimit=$catalogLimit&catalogOffset=$catalogOffset",
    ) { it.body() }

    suspend fun redeem(code: String): Result<BffShopRedeemResponse> {
        val stored = sessionStore.get() ?: return Result.failure(IllegalStateException("Not signed in"))
        return try {
            val response = httpClient.post("${AnisurgeApi.v1Base}/shop/redeem") {
                applyAnisurgeAuth(stored)
                contentType(ContentType.Application.Json)
                setBody(BffShopRedeemRequest(code.trim()))
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
            val response = httpClient.post("${AnisurgeApi.v1Base}/shop/purchase") {
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

    suspend fun fetchDownloadManifest(): Result<BffShopDownloadAllResponse> =
        authedGet("/shop/owned/download-all") { it.body() }

    suspend fun downloadFrameBytes(url: String): Result<ByteArray> {
        return try {
            val response = httpClient.get(url)
            if (response.status.isSuccess()) {
                Result.success(response.body())
            } else {
                Result.failure(Exception("Download failed (${response.status.value})"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend inline fun <T> authedGet(
        path: String,
        parse: (io.ktor.client.statement.HttpResponse) -> T,
    ): Result<T> {
        val stored = sessionStore.get() ?: return Result.failure(IllegalStateException("Not signed in"))
        return try {
            val response = httpClient.get("${AnisurgeApi.v1Base}$path") {
                applyAnisurgeAuth(stored)
            }
            if (response.status.isSuccess()) {
                Result.success(parse(response))
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

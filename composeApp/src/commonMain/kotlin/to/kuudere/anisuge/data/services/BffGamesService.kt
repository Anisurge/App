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
import to.kuudere.anisuge.data.models.BffCoinFlipRequest
import to.kuudere.anisuge.data.models.BffCoinFlipResponse
import to.kuudere.anisuge.data.models.BffErrorResponse
import to.kuudere.anisuge.data.models.BffGameStatusResponse
import to.kuudere.anisuge.data.models.BffWheelSpinRequest
import to.kuudere.anisuge.data.models.BffWheelSpinResponse
import to.kuudere.anisuge.data.services.AnisurgeApi.applyAnisurgeAuth

class BffGamesService(
    private val sessionStore: SessionStore,
    private val httpClient: HttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchStatus(): Result<BffGameStatusResponse> {
        val stored = sessionStore.get() ?: return Result.failure(IllegalStateException("Not signed in"))
        return try {
            val response = httpClient.get("${AnisurgeApi.v1Base}/games/status") {
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

    suspend fun spinWheel(freeSpin: Boolean = true): Result<BffWheelSpinResponse> {
        val stored = sessionStore.get() ?: return Result.failure(IllegalStateException("Not signed in"))
        return try {
            val response = httpClient.post("${AnisurgeApi.v1Base}/games/wheel/spin") {
                applyAnisurgeAuth(stored)
                contentType(ContentType.Application.Json)
                setBody(BffWheelSpinRequest(freeSpin = freeSpin))
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

    suspend fun flipCoin(bet: Int, choice: String): Result<BffCoinFlipResponse> {
        val stored = sessionStore.get() ?: return Result.failure(IllegalStateException("Not signed in"))
        return try {
            val response = httpClient.post("${AnisurgeApi.v1Base}/games/coin-flip") {
                applyAnisurgeAuth(stored)
                contentType(ContentType.Application.Json)
                setBody(BffCoinFlipRequest(bet = bet, choice = choice))
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

    private fun errorMessage(body: String): String {
        return runCatching {
            json.decodeFromString<BffErrorResponse>(body).error
        }.getOrNull() ?: body.ifBlank { "Request failed" }
    }
}

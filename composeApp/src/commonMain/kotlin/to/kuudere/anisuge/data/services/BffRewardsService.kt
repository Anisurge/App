package to.kuudere.anisuge.data.services

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import to.kuudere.anisuge.data.models.BffDailyClaimResponse
import to.kuudere.anisuge.data.models.BffErrorResponse
import to.kuudere.anisuge.data.models.BffRewardsStatusResponse
import to.kuudere.anisuge.data.services.AnisurgeApi.applyAnisurgeAuth

class BffRewardsService(
    private val sessionStore: SessionStore,
    private val httpClient: HttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchStatus(): Result<BffRewardsStatusResponse> = authedGet("/rewards/status") {
        it.body()
    }

    suspend fun claimDaily(): Result<BffDailyClaimResponse> {
        val stored = sessionStore.get() ?: return Result.failure(IllegalStateException("Not signed in"))
        return try {
            val response = httpClient.post("${AnisurgeApi.v1Base}/rewards/daily-claim") {
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

    private fun errorMessage(body: String): String {
        return runCatching {
            json.decodeFromString<BffErrorResponse>(body).error
        }.getOrNull() ?: body.ifBlank { "Request failed" }
    }
}

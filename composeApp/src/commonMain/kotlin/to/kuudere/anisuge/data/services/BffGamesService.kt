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
import to.kuudere.anisuge.data.models.BffAnimeGuessAnswerRequest
import to.kuudere.anisuge.data.models.BffAnimeGuessAnswerResponse
import to.kuudere.anisuge.data.models.BffAnimeGuessHintResponse
import to.kuudere.anisuge.data.models.BffAnimeGuessStartRequest
import to.kuudere.anisuge.data.models.BffAnimeGuessStartResponse
import to.kuudere.anisuge.data.models.BffCoinFlipRequest
import to.kuudere.anisuge.data.models.BffCoinFlipResponse
import to.kuudere.anisuge.data.models.BffCrashCashoutResponse
import to.kuudere.anisuge.data.models.BffCrashStartRequest
import to.kuudere.anisuge.data.models.BffCrashStartResponse
import to.kuudere.anisuge.data.models.BffEmptyGameRequest
import to.kuudere.anisuge.data.models.BffErrorResponse
import to.kuudere.anisuge.data.models.BffGameIdRequest
import to.kuudere.anisuge.data.models.BffGameStatusResponse
import to.kuudere.anisuge.data.models.BffHigherLowerAnswerRequest
import to.kuudere.anisuge.data.models.BffHigherLowerAnswerResponse
import to.kuudere.anisuge.data.models.BffHigherLowerStartResponse
import to.kuudere.anisuge.data.models.BffMinesCashoutResponse
import to.kuudere.anisuge.data.models.BffMinesCreateRequest
import to.kuudere.anisuge.data.models.BffMinesCreateResponse
import to.kuudere.anisuge.data.models.BffMinesRevealRequest
import to.kuudere.anisuge.data.models.BffMinesRevealResponse
import to.kuudere.anisuge.data.models.BffTriviaAnswerRequest
import to.kuudere.anisuge.data.models.BffTriviaAnswerResponse
import to.kuudere.anisuge.data.models.BffTriviaStartResponse
import to.kuudere.anisuge.data.models.BffWheelSpinRequest
import to.kuudere.anisuge.data.models.BffWheelSpinResponse
import to.kuudere.anisuge.data.models.BffRetroAiRequest
import to.kuudere.anisuge.data.models.BffRetroAiResponse
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

    suspend fun spinWheel(freeSpin: Boolean = true): Result<BffWheelSpinResponse> =
        postJson("/games/wheel/spin", BffWheelSpinRequest(freeSpin = freeSpin))

    suspend fun flipCoin(bet: Int, choice: String): Result<BffCoinFlipResponse> =
        postJson("/games/coin-flip", BffCoinFlipRequest(bet = bet, choice = choice))

    suspend fun createMines(bet: Int): Result<BffMinesCreateResponse> =
        postJson("/games/mines/create", BffMinesCreateRequest(bet = bet))

    suspend fun revealMinesTile(gameId: String, tileIndex: Int): Result<BffMinesRevealResponse> =
        postJson("/games/mines/reveal", BffMinesRevealRequest(gameId = gameId, tileIndex = tileIndex))

    suspend fun cashoutMines(gameId: String): Result<BffMinesCashoutResponse> =
        postJson("/games/mines/cashout", BffGameIdRequest(gameId = gameId))

    suspend fun startCrash(bet: Int): Result<BffCrashStartResponse> =
        postJson("/games/crash/start", BffCrashStartRequest(bet = bet))

    suspend fun cashoutCrash(gameId: String): Result<BffCrashCashoutResponse> =
        postJson("/games/crash/cashout", BffGameIdRequest(gameId = gameId))

    suspend fun startHigherLower(): Result<BffHigherLowerStartResponse> =
        postJson("/games/higher-lower/start", BffEmptyGameRequest)

    suspend fun answerHigherLower(gameId: String, answer: String): Result<BffHigherLowerAnswerResponse> =
        postJson("/games/higher-lower/answer", BffHigherLowerAnswerRequest(gameId = gameId, answer = answer))

    suspend fun startAnimeGuess(mode: String): Result<BffAnimeGuessStartResponse> =
        postJson("/games/guess/start", BffAnimeGuessStartRequest(mode = mode))

    suspend fun revealAnimeGuessHint(gameId: String): Result<BffAnimeGuessHintResponse> =
        postJson("/games/guess/hint", BffGameIdRequest(gameId = gameId))

    suspend fun answerAnimeGuess(gameId: String, answer: String = "", animeId: String? = null): Result<BffAnimeGuessAnswerResponse> =
        postJson("/games/guess/answer", BffAnimeGuessAnswerRequest(gameId = gameId, answer = answer, animeId = animeId))

    suspend fun startTrivia(): Result<BffTriviaStartResponse> =
        postJson("/games/trivia/start", BffEmptyGameRequest)

    suspend fun answerTrivia(gameId: String, choiceIndex: Int): Result<BffTriviaAnswerResponse> =
        postJson("/games/trivia/answer", BffTriviaAnswerRequest(gameId = gameId, choiceIndex = choiceIndex))

    suspend fun queryRetroAi(message: String): Result<BffRetroAiResponse> =
        postJson("/games/retro-ai", to.kuudere.anisuge.data.models.BffRetroAiRequest(message = message))

    private suspend inline fun <reified Request : Any, reified Response : Any> postJson(
        path: String,
        request: Request,
    ): Result<Response> {
        val stored = sessionStore.get() ?: return Result.failure(IllegalStateException("Not signed in"))
        return try {
            val response = httpClient.post("${AnisurgeApi.v1Base}$path") {
                applyAnisurgeAuth(stored)
                contentType(ContentType.Application.Json)
                setBody(request)
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

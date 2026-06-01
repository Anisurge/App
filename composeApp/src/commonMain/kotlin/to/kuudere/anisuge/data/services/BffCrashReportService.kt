package to.kuudere.anisuge.data.services

import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import to.kuudere.anisuge.AppComponent

class BffCrashReportService(
    private val httpClient: HttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun sendReport(
        stackTrace: String,
        deviceModel: String,
        osVersion: String,
        appVersion: String,
    ): Result<Unit> {
        val body = CrashReportBody(
            body = "In-app crash report",
            stackTrace = stackTrace,
            deviceModel = deviceModel,
            osVersion = osVersion,
            appVersion = appVersion,
        )
        return try {
            val response = httpClient.post("${AnisurgeApi.v1Base}/crash-report") {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
            if (response.status.isSuccess()) {
                Result.success(Unit)
            } else {
                val msg = runCatching {
                    json.decodeFromString<CrashReportError>(response.bodyAsText()).error
                }.getOrElse { "Failed (${response.status.value})" }
                Result.failure(Exception(msg))
            }
        } catch (e: Exception) {
            println("[BffCrashReportService] sendReport error: ${e.message}")
            Result.failure(e)
        }
    }
}

@Serializable
private data class CrashReportBody(
    val body: String,
    val stackTrace: String,
    val deviceModel: String,
    val osVersion: String,
    val appVersion: String,
)

@Serializable
private data class CrashReportError(
    val error: String,
)

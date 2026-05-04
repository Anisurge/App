package to.kuudere.anisuge.data.services

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import to.kuudere.anisuge.data.models.ScheduleApiResponse
import to.kuudere.anisuge.data.network.ApiConfig
import to.kuudere.anisuge.data.network.bearer

class ScheduleService(
    private val sessionStore: SessionStore,
    private val httpClient: HttpClient,
) {
    suspend fun fetchSchedule(
        tz: String = "UTC",
        year: Int? = null,
        month: Int? = null,
    ): ScheduleApiResponse {
        val stored = sessionStore.get()
        return try {
            val response = httpClient.get("${ApiConfig.API_BASE}/schedule") {
                parameter("tz", tz)
                year?.let { parameter("year", it) }
                month?.let { parameter("month", it) }
                if (stored != null) bearer(stored.token)
            }
            response.body<ScheduleApiResponse>()
        } catch (e: Exception) {
            println("[ScheduleService] fetchSchedule error: ${e.message}")
            ScheduleApiResponse()
        }
    }
}

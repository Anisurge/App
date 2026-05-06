package to.kuudere.anisuge.data.services

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import to.kuudere.anisuge.AppComponent
import to.kuudere.anisuge.data.models.ScheduleApiResponse

class ScheduleService(
    private val sessionStore: SessionStore,
    private val httpClient: HttpClient,
) {
    suspend fun fetchSchedule(
        tz: String = "UTC",
        year: Int? = null,
        month: Int? = null,
    ): ScheduleApiResponse {
        return try {
            val response = httpClient.get("${AppComponent.BASE_URL}/schedule") {
                parameter("tz", tz)
                year?.let { parameter("year", it) }
                month?.let { parameter("month", it) }
            }
            response.body()
        } catch (e: Exception) {
            println("[ScheduleService] fetchSchedule error: ${e.message}")
            ScheduleApiResponse()
        }
    }
}

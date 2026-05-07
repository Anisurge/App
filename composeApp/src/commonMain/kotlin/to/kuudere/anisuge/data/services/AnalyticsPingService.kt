package to.kuudere.anisuge.data.services

import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.datetime.Clock
import to.kuudere.anisuge.data.models.AnalyticsPingRequest
import to.kuudere.anisuge.platform.analyticsPingOs
import to.kuudere.anisuge.platform.AppVersion
import to.kuudere.anisuge.platform.isDesktopPlatform

class AnalyticsPingService(
    private val httpClient: HttpClient,
    private val settingsStore: SettingsStore,
    private val pingUrl: String,
) {
    suspend fun sendPingIfDue() {
        val now = Clock.System.now().toEpochMilliseconds()
        if (now - settingsStore.getAnalyticsLastPingMs() < MIN_INTERVAL_MS) return

        val installId = settingsStore.getOrCreateAnalyticsInstallId()
        val platform = if (isDesktopPlatform) "desktop" else "android"
        val os = analyticsPingOs()

        try {
            val response = httpClient.post(pingUrl) {
                contentType(ContentType.Application.Json)
                setBody(AnalyticsPingRequest(installId, platform, AppVersion, os))
            }
            if (response.status == HttpStatusCode.OK) {
                settingsStore.setAnalyticsLastPingMs(now)
            }
        } catch (_: Exception) {
            // Non-blocking: failures should not affect app startup
        }
    }

    private companion object {
        private const val MIN_INTERVAL_MS = 24L * 60 * 60 * 1000
    }
}

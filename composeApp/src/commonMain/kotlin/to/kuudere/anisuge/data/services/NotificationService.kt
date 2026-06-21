package to.kuudere.anisuge.data.services

import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.encodeURLPathPart
import io.ktor.http.isSuccess
import kotlinx.datetime.TimeZone
import kotlinx.serialization.Serializable
import to.kuudere.anisuge.data.services.AnisurgeApi.applyAnisurgeAuth
import to.kuudere.anisuge.platform.AppVersion
import to.kuudere.anisuge.platform.getPushNotificationToken
import to.kuudere.anisuge.platform.isAndroidPlatform
import to.kuudere.anisuge.platform.isAndroidTvPlatform

class NotificationService(
    private val sessionStore: SessionStore,
    private val httpClient: HttpClient,
) {
    @Serializable
    private data class DeviceRequest(
        val token: String,
        val platform: String,
        val timezone: String,
        val appVersion: String,
    )

    @Serializable
    private data class PreferencesRequest(
        val enabled: Boolean,
        val newEpisodes: Boolean,
        val reminderMinutes: Int,
    )

    suspend fun sync(
        enabled: Boolean,
        newEpisodes: Boolean,
        reminderMinutes: Int,
        knownToken: String? = null,
    ): Boolean {
        if (!isAndroidPlatform || isAndroidTvPlatform) return false
        val session = sessionStore.get()
        if (session == null) {
            println("[NotificationService] sync skipped: no session")
            return false
        }
        val token = knownToken ?: getPushNotificationToken()
        if (token.isNullOrBlank()) {
            println("[NotificationService] sync skipped: no FCM token")
            return false
        }
        println("[NotificationService] registering Android device")
        return runCatching {
            val deviceResponse = httpClient.post("${AnisurgeApi.v1Base}/notifications/devices") {
                applyAnisurgeAuth(session)
                contentType(ContentType.Application.Json)
                setBody(
                    DeviceRequest(
                        token = token,
                        platform = "android",
                        timezone = TimeZone.currentSystemDefault().id,
                        appVersion = AppVersion,
                    )
                )
            }
            if (!deviceResponse.status.isSuccess()) {
                println(
                    "[NotificationService] device registration failed: ${deviceResponse.status.value} " +
                        deviceResponse.bodyAsText().take(500)
                )
                return@runCatching false
            }
            val preferencesResponse = httpClient.patch("${AnisurgeApi.v1Base}/notifications/preferences") {
                applyAnisurgeAuth(session)
                contentType(ContentType.Application.Json)
                setBody(
                    PreferencesRequest(
                        enabled = enabled,
                        newEpisodes = newEpisodes,
                        reminderMinutes = reminderMinutes.coerceIn(0, 60),
                    )
                )
            }
            preferencesResponse.status.isSuccess().also { success ->
                println(
                    "[NotificationService] preference sync ${if (success) "completed" else "failed"}: " +
                        preferencesResponse.status.value
                )
            }
        }.onFailure {
            println("[NotificationService] sync error: ${it.message}")
        }.getOrDefault(false)
    }

    suspend fun unregisterCurrentDevice(): Boolean {
        if (!isAndroidPlatform || isAndroidTvPlatform) return false
        val session = sessionStore.get() ?: return false
        val token = getPushNotificationToken() ?: return false
        return runCatching {
            httpClient.delete(
                "${AnisurgeApi.v1Base}/notifications/devices/${token.encodeURLPathPart()}"
            ) {
                applyAnisurgeAuth(session)
            }.status.isSuccess()
        }.getOrDefault(false)
    }
}

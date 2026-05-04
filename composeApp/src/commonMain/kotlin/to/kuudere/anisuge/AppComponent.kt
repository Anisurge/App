package to.kuudere.anisuge

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import to.kuudere.anisuge.data.repository.ServerRepository
import to.kuudere.anisuge.data.services.AuthService
import to.kuudere.anisuge.data.services.SessionStore
import to.kuudere.anisuge.platform.createDataStore

import to.kuudere.anisuge.data.services.HomeService
import to.kuudere.anisuge.data.services.SearchService

object AppComponent {
    val httpClient: HttpClient by lazy {
        HttpClient {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient         = true
                    coerceInputValues = true
                    explicitNulls     = false
                })
            }
            install(Logging) { level = LogLevel.ALL }
            install(HttpTimeout) {
                requestTimeoutMillis = 60000
                connectTimeoutMillis = 30000
                socketTimeoutMillis  = 60000
            }
        }
    }

    val dataStore: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences> by lazy {
        createDataStore()
    }

    val sessionStore: SessionStore by lazy {
        SessionStore(dataStore)
    }

    val authService: AuthService by lazy {
        AuthService(sessionStore, httpClient)
    }

    val homeService: HomeService by lazy {
        HomeService(sessionStore, httpClient)
    }

    val searchService: SearchService by lazy {
        SearchService(sessionStore, httpClient)
    }

    val infoService: to.kuudere.anisuge.data.services.InfoService by lazy {
        to.kuudere.anisuge.data.services.InfoService(sessionStore, httpClient)
    }

    val watchlistService: to.kuudere.anisuge.data.services.WatchlistService by lazy {
        to.kuudere.anisuge.data.services.WatchlistService(sessionStore, httpClient)
    }

    val scheduleService: to.kuudere.anisuge.data.services.ScheduleService by lazy {
        to.kuudere.anisuge.data.services.ScheduleService(sessionStore, httpClient)
    }

    val commentService: to.kuudere.anisuge.data.services.CommentService by lazy {
        to.kuudere.anisuge.data.services.CommentService(sessionStore, httpClient)
    }

    val settingsStore: to.kuudere.anisuge.data.services.SettingsStore by lazy {
        to.kuudere.anisuge.data.services.SettingsStore(dataStore)
    }

    val settingsService: to.kuudere.anisuge.data.services.SettingsService by lazy {
        to.kuudere.anisuge.data.services.SettingsService(sessionStore, httpClient)
    }

    val serverRepository: ServerRepository by lazy {
        ServerRepository(httpClient, dataStore, settingsStore)
    }

    val latestService: to.kuudere.anisuge.data.services.LatestService by lazy {
        to.kuudere.anisuge.data.services.LatestService(sessionStore, httpClient)
    }

    val watchService: to.kuudere.anisuge.data.services.WatchService by lazy {
        to.kuudere.anisuge.data.services.WatchService(sessionStore, httpClient)
    }

    val communityService: to.kuudere.anisuge.data.services.CommunityService by lazy {
        to.kuudere.anisuge.data.services.CommunityService(sessionStore, httpClient)
    }

    val notificationService: to.kuudere.anisuge.data.services.NotificationService by lazy {
        to.kuudere.anisuge.data.services.NotificationService(sessionStore, httpClient)
    }

    val requestService: to.kuudere.anisuge.data.services.RequestService by lazy {
        to.kuudere.anisuge.data.services.RequestService(sessionStore, httpClient)
    }

    val oAuthService: to.kuudere.anisuge.data.services.OAuthService by lazy {
        to.kuudere.anisuge.data.services.OAuthService(sessionStore, httpClient)
    }

    val publicService: to.kuudere.anisuge.data.services.PublicService by lazy {
        to.kuudere.anisuge.data.services.PublicService(sessionStore, httpClient)
    }

    val contactService: to.kuudere.anisuge.data.services.ContactService by lazy {
        to.kuudere.anisuge.data.services.ContactService(httpClient)
    }

    val topAnimeService: to.kuudere.anisuge.data.services.TopAnimeService by lazy {
        to.kuudere.anisuge.data.services.TopAnimeService(httpClient)
    }
}

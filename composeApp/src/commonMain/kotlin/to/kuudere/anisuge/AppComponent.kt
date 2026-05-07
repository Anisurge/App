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
    const val BASE_URL = "https://api.reanime.to/api/v1"
    /** Public catalog for batch_scrape source ids (Next site); not the Project-R API host. */
    const val STREAMING_SERVERS_CATALOG_URL = "https://www.anisurge.lol/api/v1/streaming/servers"
    const val STREAMING_URL = "https://fetch.anisurge.lol/api"

    val httpClient: HttpClient by lazy {
        HttpClient {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient         = true
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

    val updateService: to.kuudere.anisuge.data.services.UpdateService by lazy {
        to.kuudere.anisuge.data.services.UpdateService(httpClient)
    }
}

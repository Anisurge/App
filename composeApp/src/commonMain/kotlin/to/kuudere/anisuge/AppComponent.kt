package to.kuudere.anisuge

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import to.kuudere.anisuge.data.repository.ServerRepository
import to.kuudere.anisuge.data.services.AuthService
import to.kuudere.anisuge.data.services.SessionStore
import to.kuudere.anisuge.platform.createDataStore

import to.kuudere.anisuge.data.services.HomeService
import to.kuudere.anisuge.data.services.SearchService

object AppComponent {
    /** Project-R catalog, social, notifications, settings writes. */
    const val PROJECT_R_BASE_URL = "https://api.reanime.to/api/v1"
    @Deprecated("Use PROJECT_R_BASE_URL", ReplaceWith("PROJECT_R_BASE_URL"))
    const val BASE_URL = PROJECT_R_BASE_URL
    /** Anisurge BFF — auth, signup, profile mirror, watchlist, continue, progress. */
    const val ANISURGE_API_URL = "https://db.anisurge.qzz.io"
    /** Public catalog for batch_scrape source ids (Next site); not the Project-R API host. */
    const val STREAMING_SERVERS_CATALOG_URL = "https://www.anisurge.lol/api/v1/streaming/servers"
    /** Anonymous install heartbeat for admin dashboard metrics (no account or PII). */
    const val ANALYTICS_PING_URL = "https://www.anisurge.lol/api/v1/app/ping"
    const val STREAMING_URL = "https://fetch.n92dev.us.kg/api"

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
                requestTimeoutMillis = 90_000
                connectTimeoutMillis = 30_000
                socketTimeoutMillis  = 90_000
            }
            install(WebSockets)
        }
    }

    val chatService: to.kuudere.anisuge.data.services.ChatService by lazy {
        to.kuudere.anisuge.data.services.ChatService(sessionStore, httpClient)
    }

    val dataStore: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences> by lazy {
        createDataStore()
    }

    val sessionStore: SessionStore by lazy {
        SessionStore(dataStore)
    }

    val integrationsSyncService: to.kuudere.anisuge.data.services.IntegrationsSyncService by lazy {
        to.kuudere.anisuge.data.services.IntegrationsSyncService(httpClient, sessionStore, settingsStore)
    }

    val bffMeService: to.kuudere.anisuge.data.services.BffMeService by lazy {
        to.kuudere.anisuge.data.services.BffMeService(sessionStore, httpClient)
    }

    val bffShopService: to.kuudere.anisuge.data.services.BffShopService by lazy {
        to.kuudere.anisuge.data.services.BffShopService(sessionStore, httpClient)
    }

    val bffRewardsService: to.kuudere.anisuge.data.services.BffRewardsService by lazy {
        to.kuudere.anisuge.data.services.BffRewardsService(sessionStore, httpClient)
    }

    val authService: AuthService by lazy {
        AuthService(sessionStore, httpClient, integrationsSyncService)
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

    val analyticsPingService: to.kuudere.anisuge.data.services.AnalyticsPingService by lazy {
        to.kuudere.anisuge.data.services.AnalyticsPingService(httpClient, settingsStore, ANALYTICS_PING_URL)
    }

    val trackingService: to.kuudere.anisuge.data.services.TrackingService by lazy {
        to.kuudere.anisuge.data.services.TrackingService(httpClient, settingsStore, integrationsSyncService)
    }

    val malAnilistIdCache: to.kuudere.anisuge.data.services.MalAnilistIdCache by lazy {
        to.kuudere.anisuge.data.services.MalAnilistIdCache(dataStore)
    }

    val aniskipService: to.kuudere.anisuge.data.services.AniskipService by lazy {
        to.kuudere.anisuge.data.services.AniskipService(httpClient, malAnilistIdCache)
    }

    val watchHistorySyncService: to.kuudere.anisuge.data.services.WatchHistorySyncService by lazy {
        to.kuudere.anisuge.data.services.WatchHistorySyncService(
            httpClient,
            sessionStore,
            settingsStore,
            malAnilistIdCache,
            trackingService,
        )
    }

    val communityService: to.kuudere.anisuge.data.services.CommunityService by lazy {
        to.kuudere.anisuge.data.services.CommunityService(sessionStore, httpClient)
    }

    val syncManager: to.kuudere.anisuge.data.services.SyncManager by lazy {
        to.kuudere.anisuge.data.services.SyncManager(trackingService)
    }
}

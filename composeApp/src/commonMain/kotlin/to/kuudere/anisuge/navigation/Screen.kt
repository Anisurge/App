package to.kuudere.anisuge.navigation

import io.ktor.http.encodeURLParameter

/** All navigation destinations in the app */
sealed class Screen(val route: String) {
    data object Splash : Screen("splash")
    data object Auth   : Screen("auth")
    data class Home(
        val startTab: String? = null,
        val startOnDownloads: Boolean = false,
        val startSettingsTab: String? = null,
    ) : Screen(
        buildString {
            append("home?")
            val params = mutableListOf<String>()
            if (startTab != null) params.add("tab=${startTab.encodeURLParameter()}")
            if (startOnDownloads) params.add("downloads=true")
            if (startSettingsTab != null) params.add("settingsTab=${startSettingsTab.encodeURLParameter()}")
            append(params.joinToString("&"))
        }
    ) {
        companion object {
            const val route = "home?downloads={downloads}&tab={tab}&settingsTab={settingsTab}"
        }
    }
    data object Search : Screen("search")
    data class Info(val animeId: String) : Screen("info/$animeId") {
        companion object {
            const val route = "info/{animeId}"
        }
    }
    data class Watch(
        val animeId: String,
        val episodeNumber: Int,
        val server: String? = null,
        val lang: String? = null,
        val offlinePath: String? = null,
        val offlineTitle: String? = null,
        /** Seconds; from continue-watching feed when API watch info omits progress.current_time */
        val resumeAtSeconds: Double? = null,
    ) : Screen(
        "watch/$animeId/$episodeNumber" + buildString {
            val params = mutableListOf<String>()
            if (server != null) params.add("server=$server")
            if (lang != null) params.add("lang=$lang")
            if (offlinePath != null) params.add("offlinePath=${offlinePath.encodeURLParameter()}")
            if (offlineTitle != null) params.add("offlineTitle=${offlineTitle.encodeURLParameter()}")
            if (resumeAtSeconds != null && resumeAtSeconds >= 1.0) {
                params.add("resumeAt=$resumeAtSeconds")
            }
            if (params.isNotEmpty()) {
                append("?")
                append(params.joinToString("&"))
            }
        }
    ) {
        companion object {
            const val route =
                "watch/{animeId}/{episodeNumber}?server={server}&lang={lang}&offlinePath={offlinePath}&offlineTitle={offlineTitle}&resumeAt={resumeAt}"
        }
    }

    data object Settings : Screen("settings")
    data object HomeLayout : Screen("settings/home-layout")
    data object Latest : Screen("latest")
    data object NewOnApp : Screen("new-on-app")
    data object ContinueWatching : Screen("continue-watching")
    /** Global community chat (route kept as `live-chat` for deep links). */
    data object LiveChat : Screen("live-chat") {
        const val displayName = "Community Chat"
    }
    data object Games : Screen("games")
    data object Announcements : Screen("announcements")
    data object W2gRoomList : Screen("w2g")
    data class W2gRoom(val inviteCode: String) : Screen("w2g-room/$inviteCode") {
        companion object {
            const val ROUTE = "w2g-room/{inviteCode}"
            fun route(inviteCode: String) = "w2g-room/$inviteCode"
        }
    }
    data class Update(val nextRoute: String) : Screen("update?next=${nextRoute.replace("/", "_")}") {
        companion object {
            const val route = "update?next={next}"
        }
    }
}

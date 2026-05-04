package to.kuudere.anisuge.navigation

import io.ktor.http.encodeURLPathPart

/** All navigation destinations in the app */
sealed class Screen(val route: String) {
    data object Splash : Screen("splash")
    data object Auth   : Screen("auth")
    data class Home(val startTab: String? = null, val startOnDownloads: Boolean = false) : Screen(
        buildString {
            append("home?")
            val params = mutableListOf<String>()
            if (startTab != null) params.add("tab=$startTab")
            if (startOnDownloads) params.add("downloads=true")
            append(params.joinToString("&"))
        }
    ) {
        companion object {
            const val route = "home?downloads={downloads}&tab={tab}"
        }
    }
    data object Search : Screen("search")
    data class Info(val animeId: String) : Screen("info/${animeId.encodeURLPathPart()}") {
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
        val offlineTitle: String? = null
    ) : Screen(
        "watch/${animeId.encodeURLPathPart()}/$episodeNumber" + buildString {
            if (server != null || lang != null || offlinePath != null || offlineTitle != null) append("?")
            val params = mutableListOf<String>()
            if (server != null) params.add("server=$server")
            if (lang != null) params.add("lang=$lang")
            if (offlinePath != null) params.add("offlinePath=$offlinePath")
            if (offlineTitle != null) params.add("offlineTitle=$offlineTitle")
            append(params.joinToString("&"))
        }
    ) {
        companion object {
            const val route = "watch/{animeId}/{episodeNumber}?server={server}&lang={lang}&offlinePath={offlinePath}&offlineTitle={offlineTitle}"
        }
    }

    data object Settings : Screen("settings")
    data object Latest : Screen("latest")
    data object ContinueWatching : Screen("continue-watching")
    data class Update(val nextRoute: String) : Screen("update?next=${nextRoute.replace("/", "_")}") {
        companion object {
            const val route = "update?next={next}"
        }
    }
}

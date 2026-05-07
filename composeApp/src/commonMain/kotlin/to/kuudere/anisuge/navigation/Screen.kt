package to.kuudere.anisuge.navigation

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
            if (offlinePath != null) params.add("offlinePath=$offlinePath")
            if (offlineTitle != null) params.add("offlineTitle=$offlineTitle")
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
    data object Latest : Screen("latest")
    data object ContinueWatching : Screen("continue-watching")
    data class Update(val nextRoute: String) : Screen("update?next=${nextRoute.replace("/", "_")}") {
        companion object {
            const val route = "update?next={next}"
        }
    }
}

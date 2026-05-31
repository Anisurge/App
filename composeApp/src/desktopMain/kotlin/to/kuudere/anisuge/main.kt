package to.kuudere.anisuge

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import to.kuudere.anisuge.platform.LocalWindowScope
import to.kuudere.anisuge.platform.LocalWindowState
import anisurge.composeapp.generated.resources.Res
import anisurge.composeapp.generated.resources.logo
import org.jetbrains.compose.resources.painterResource
import to.kuudere.anisuge.platform.DiscordRichPresenceManager
import com.sun.jna.Native
import com.sun.jna.Library

private interface CLib : Library {
    fun setlocale(category: Int, locale: String?): String?
}

/**
 * Set LC_NUMERIC=C through every available mechanism so mpv_create()
 * doesn't fail with "Non-C locale detected". Must be called at app startup,
 * before any native libraries are loaded.
 */
private fun forceLocaleC() {
    // JVM-level
    try { System.setProperty("user.language", "en") } catch (_: Exception) {}
    try { System.setProperty("user.country", "US") } catch (_: Exception) {}

    // Native setlocale through system libc
    try {
        val libName = if (System.getProperty("os.name").lowercase().contains("win")) "msvcrt" else "c"
        val clib = Native.load(libName, CLib::class.java) as CLib
        clib.setlocale(6, "C")  // LC_ALL
        clib.setlocale(1, "C")  // LC_NUMERIC
    } catch (_: Exception) {}
}

fun main() = application {
    // CRITICAL: mpv requires LC_NUMERIC=C. Set it at the very start,
    // before any native libraries are loaded. If this isn't set before
    // LibMpv.load() or mpv_create(), mpv fails with "Non-C locale detected".
    forceLocaleC()

    System.setProperty("compose.interop.blending", "true")
    CrashReporter.init(
        backendUrl = BuildConfig.CRASH_REPORTER_URL,
        appName = BuildConfig.CRASH_REPORTER_APP_NAME,
        apiKey = BuildConfig.CRASH_REPORTER_API_KEY.ifBlank { null }
    )
    fun exitWithCleanup() {
        DiscordRichPresenceManager.shutdown()
        exitApplication()
    }

    val windowState = rememberWindowState(size = DpSize(1280.dp, 800.dp))
    Window(
        onCloseRequest = ::exitWithCleanup,
        title = "Anisurge",
        state = windowState,
        undecorated = true,
        icon = painterResource(Res.drawable.logo)
    ) {
        CompositionLocalProvider(
            LocalWindowScope provides this,
            LocalWindowState provides windowState
        ) {
            App(onAppExit = ::exitWithCleanup)
        }
    }
}

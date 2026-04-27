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


fun main() = application {
    System.setProperty("compose.interop.blending", "true")
    CrashReporter.init(
        backendUrl = BuildConfig.CRASH_REPORTER_URL,
        appName = BuildConfig.CRASH_REPORTER_APP_NAME,
        apiKey = BuildConfig.CRASH_REPORTER_API_KEY.ifBlank { null }
    )
    val windowState = rememberWindowState(size = DpSize(1280.dp, 800.dp))
    Window(
        onCloseRequest = ::exitApplication,
        title = "Anisurge",
        state = windowState,
        undecorated = true,
        icon = painterResource(Res.drawable.logo)
    ) {
        CompositionLocalProvider(
            LocalWindowScope provides this,
            LocalWindowState provides windowState
        ) {
            App(onAppExit = ::exitApplication)
        }
    }
}

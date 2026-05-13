package to.kuudere.anisuge.screens.splash

import androidx.compose.runtime.Composable

@Composable
actual fun SplashVideoBackground(onVideoFinished: () -> Unit) {
    // iOS: skip splash video, go straight to app
    onVideoFinished()
}

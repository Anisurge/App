package to.kuudere.anisuge.screens.auth

import android.media.MediaPlayer
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.VideoView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import to.kuudere.anisuge.R
import to.kuudere.anisuge.platform.androidAppContext

@Composable
actual fun VideoBackground(
    videoUrl: String,
    modifier: Modifier
) {
    val context = androidAppContext
    val videoView = remember { VideoView(context) }
    val videoUri = Uri.parse("android.resource://${context.packageName}/${R.raw.auth_bg}")

    DisposableEffect(Unit) {
        videoView.setVideoURI(videoUri)
        videoView.setOnPreparedListener { mp ->
            mp.isLooping = true
            mp.setVolume(0f, 0f)
            mp.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
            videoView.start()
        }
        videoView.setOnErrorListener { _, _, _ ->
            true // Suppress error dialogs
        }

        onDispose {
            videoView.stopPlayback()
        }
    }

    AndroidView(
        factory = { videoView },
        modifier = modifier
    ) { view ->
        view.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }
}

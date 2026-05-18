package to.kuudere.anisuge.ui

import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.widget.ImageView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import coil3.compose.AsyncImage
import coil3.gif.GifDecoder
import coil3.request.ImageRequest
import coil3.request.allowHardware
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

@Composable
actual fun PlatformAnimatedProfileOverlay(
    url: String,
    modifier: Modifier,
    contentDescription: String?,
) {
    if (isAnimatedFrameAssetUrl(url) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        ApngImageDecoderOverlay(url = url, modifier = modifier, contentDescription = contentDescription)
    } else if (isProfileApngUrl(url) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        ApngImageDecoderOverlay(url = url, modifier = modifier, contentDescription = contentDescription)
    } else {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(url)
                .decoderFactory(GifDecoder.Factory())
                .allowHardware(false)
                .build(),
            contentDescription = contentDescription,
            modifier = modifier,
        )
    }
}

@Composable
private fun ApngImageDecoderOverlay(
    url: String,
    modifier: Modifier,
    contentDescription: String?,
) {
    var drawable by remember(url) { mutableStateOf<Drawable?>(null) }

    androidx.compose.runtime.LaunchedEffect(url) {
        val decoded = withContext(Dispatchers.IO) {
            runCatching {
                val bytes = URL(url).openStream().use { it.readBytes() }
                ImageDecoder.decodeDrawable(ImageDecoder.createSource(bytes))
            }.getOrNull()
        }
        if (decoded is AnimatedImageDrawable) {
            decoded.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
            decoded.start()
        }
        drawable = decoded
    }

    DisposableEffect(drawable) {
        onDispose {
            (drawable as? AnimatedImageDrawable)?.stop()
        }
    }

    AndroidView(
        factory = { ctx ->
            ImageView(ctx).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                this.contentDescription = contentDescription
            }
        },
        update = { view -> view.setImageDrawable(drawable) },
        modifier = modifier,
    )
}

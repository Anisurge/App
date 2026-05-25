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
import to.kuudere.anisuge.data.services.AnimatedFrameBytesCache

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
    cacheKey: String? = null,
) {
    var drawable by remember(url, cacheKey) { mutableStateOf<Drawable?>(null) }

    androidx.compose.runtime.LaunchedEffect(url, cacheKey) {
        val decoded = withContext(Dispatchers.IO) {
            runCatching {
                val bytes = AnimatedFrameBytesCache.load(url, itemId = cacheKey)
                    ?: return@runCatching null
                ImageDecoder.decodeDrawable(ImageDecoder.createSource(bytes))
            }.getOrNull()
        }
        if (decoded is AnimatedImageDrawable) {
            decoded.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
            decoded.start()
        }
        drawable = decoded
    }

    var imageViewRef by remember { mutableStateOf<ImageView?>(null) }

    DisposableEffect(drawable, imageViewRef) {
        onDispose {
            (drawable as? AnimatedImageDrawable)?.stop()
            imageViewRef?.setImageDrawable(null)
        }
    }

    AndroidView(
        factory = { ctx ->
            ImageView(ctx).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                this.contentDescription = contentDescription
                imageViewRef = this
            }
        },
        update = { view ->
            imageViewRef = view
            view.setImageDrawable(drawable)
        },
        modifier = modifier,
    )
}

package to.kuudere.anisuge.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import to.kuudere.anisuge.data.services.AnimatedFrameBytesCache

/** Frame-only preview (shop catalog) — plays APNG from URL, no profile picture underneath. */
@Composable
fun ShopFramePreview(
    frameUrl: String?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    cacheKey: String? = null,
) {
    val resolved = remember(frameUrl) { resolveProfileMediaUrl(frameUrl) }
    if (resolved == null) return
    AnimatedFrameUrlPreview(
        url = resolved,
        cacheKey = cacheKey,
        modifier = modifier,
        contentDescription = contentDescription,
    )
}

@Composable
fun AnimatedFrameUrlPreview(
    url: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    cacheKey: String? = null,
) {
    var bytes by remember(url, cacheKey) {
        mutableStateOf(AnimatedFrameBytesCache.peekMemory(url))
    }

    LaunchedEffect(url, cacheKey) {
        AnimatedFrameBytesCache.peekMemory(url)?.let { cached ->
            bytes = cached
            return@LaunchedEffect
        }
        val loaded = AnimatedFrameBytesCache.load(url, itemId = cacheKey)
        if (loaded != null) {
            bytes = loaded
        }
    }

    Box(modifier = modifier) {
        AsyncImage(
            model = url,
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
        if (bytes != null) {
            ApngBytesOverlay(
                bytes = bytes!!,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

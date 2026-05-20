package to.kuudere.anisuge.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import to.kuudere.anisuge.data.services.AnimatedFrameBytesCache

/** Frame-only preview (shop catalog) — APNG bytes when cached, Coil fallback otherwise. */
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
    var loading by remember(url, cacheKey) { mutableStateOf(bytes == null) }

    LaunchedEffect(url, cacheKey) {
        AnimatedFrameBytesCache.peekMemory(url)?.let { cached ->
            bytes = cached
            loading = false
            return@LaunchedEffect
        }
        loading = true
        val loaded = AnimatedFrameBytesCache.load(url, itemId = cacheKey)
        loading = false
        if (loaded != null) {
            bytes = loaded
        }
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        if (bytes == null) {
            AsyncImage(
                model = url,
                contentDescription = contentDescription,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
            if (loading) {
                CircularProgressIndicator(
                    color = Color.White.copy(alpha = 0.45f),
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp),
                )
            }
        } else {
            ApngBytesOverlay(
                bytes = bytes!!,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

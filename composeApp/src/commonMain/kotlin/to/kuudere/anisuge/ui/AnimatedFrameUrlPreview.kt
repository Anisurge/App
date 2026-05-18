package to.kuudere.anisuge.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import to.kuudere.anisuge.AppComponent

/** Frame-only preview (shop catalog) — plays APNG from URL, no profile picture underneath. */
@Composable
fun ShopFramePreview(
    frameUrl: String?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val resolved = remember(frameUrl) { resolveProfileMediaUrl(frameUrl) }
    if (resolved == null) return
    AnimatedFrameUrlPreview(
        url = resolved,
        modifier = modifier,
        contentDescription = contentDescription,
    )
}

@Composable
fun AnimatedFrameUrlPreview(
    url: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    var bytes by remember(url) { mutableStateOf<ByteArray?>(null) }
    var failed by remember(url) { mutableStateOf(false) }

    LaunchedEffect(url) {
        bytes = null
        failed = false
        val loaded = withContext(Dispatchers.Default) {
            runCatching {
                AppComponent.httpClient.get(url).body<ByteArray>()
            }.getOrNull()
        }
        if (loaded != null && loaded.isNotEmpty()) {
            bytes = loaded
        } else {
            failed = true
        }
    }

    when {
        bytes != null -> ApngBytesOverlay(bytes = bytes!!, modifier = modifier)
        failed -> AsyncImage(
            model = url,
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = modifier,
        )
        else -> Box(modifier = modifier)
    }
}

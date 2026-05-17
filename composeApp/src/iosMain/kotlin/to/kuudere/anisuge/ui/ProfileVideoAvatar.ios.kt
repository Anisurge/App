package to.kuudere.anisuge.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage

/** iOS: fall back to static frame until AVPlayer profile loops exist. */
@Composable
actual fun ProfileVideoAvatar(
    url: String,
    modifier: Modifier,
    contentDescription: String?,
) {
    AsyncImage(
        model = url,
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        modifier = modifier.fillMaxSize(),
    )
}

package to.kuudere.anisuge.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage

@Composable
fun ProfileAvatar(
    url: String?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    placeholderTint: Color = Color.White.copy(alpha = 0.45f),
    backgroundColor: Color = Color(0xFF222222),
) {
    val resolved = remember(url) { resolveProfileMediaUrl(url) }
    val shapeModifier = modifier.clip(CircleShape).background(backgroundColor)

    Box(shapeModifier, contentAlignment = Alignment.Center) {
        when {
            resolved == null -> {
                Icon(
                    Icons.Default.Person,
                    contentDescription = contentDescription,
                    tint = placeholderTint,
                    modifier = Modifier.fillMaxSize(0.55f),
                )
            }
            isProfileVideoUrl(resolved) -> {
                ProfileVideoAvatar(
                    url = resolved,
                    modifier = Modifier.fillMaxSize(),
                    contentDescription = contentDescription,
                )
            }
            else -> {
                AsyncImage(
                    model = resolved,
                    contentDescription = contentDescription,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/** Looping muted profile video (mp4/webm). Platform actual uses the video player when available. */
@Composable
expect fun ProfileVideoAvatar(
    url: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
)

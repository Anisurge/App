package to.kuudere.anisuge.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import to.kuudere.anisuge.ui.ProfileImageCropSheet

/** Pick a gallery image, crop to a square, or pass premium MP4 profile videos through. */
@Composable
fun rememberProfileImagePicker(
    onResult: (ChatImagePick?) -> Unit,
): () -> Unit {
    var pendingCrop by remember { mutableStateOf<ChatImagePick?>(null) }
    val launchGallery = rememberChatImagePicker(allowVideo = true) { pick ->
        if (pick?.mimeType == "video/mp4") {
            onResult(pick)
        } else {
            pendingCrop = pick
        }
    }

    pendingCrop?.let { source ->
        ProfileImageCropSheet(
            sourcePick = source,
            onConfirm = { cropped ->
                pendingCrop = null
                onResult(cropped)
            },
            onCancel = { pendingCrop = null },
        )
    }

    return launchGallery
}

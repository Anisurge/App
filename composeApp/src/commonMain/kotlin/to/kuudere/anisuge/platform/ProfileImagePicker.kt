package to.kuudere.anisuge.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import to.kuudere.anisuge.ui.ProfileImageCropSheet
import to.kuudere.anisuge.ui.ProfileVideoCropSheet

/** Pick gallery media; images are manually cropped, MP4s are square-cropped and trimmed before upload. */
@Composable
fun rememberProfileImagePicker(
    onResult: (ChatImagePick?) -> Unit,
): () -> Unit {
    var pendingCrop by remember { mutableStateOf<ChatImagePick?>(null) }
    var pendingVideo by remember { mutableStateOf<ChatImagePick?>(null) }
    val launchGallery = rememberChatImagePicker(allowVideo = true) { pick ->
        if (pick?.mimeType == "video/mp4") {
            pendingVideo = pick
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

    pendingVideo?.let { source ->
        ProfileVideoCropSheet(
            sourcePick = source,
            onConfirm = { processed ->
                pendingVideo = null
                onResult(processed)
            },
            onCancel = { pendingVideo = null },
        )
    }

    return launchGallery
}

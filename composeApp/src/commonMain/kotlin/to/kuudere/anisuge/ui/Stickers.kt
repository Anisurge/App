package to.kuudere.anisuge.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import to.kuudere.anisuge.data.models.Sticker
import to.kuudere.anisuge.data.models.StickerMessage
import to.kuudere.anisuge.theme.AppColors

@Composable
fun StickerMedia(
    sticker: StickerMessage,
    modifier: Modifier = Modifier,
    size: Dp = 132.dp,
    playAnimation: Boolean = true,
) {
    val assetUrl = resolveProfileMediaUrl(sticker.assetUrl)
    val previewUrl = resolveProfileMediaUrl(sticker.thumbnailUrl) ?: assetUrl
    val resolved = if (sticker.mediaType == "webm" && playAnimation) assetUrl else previewUrl

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.Black.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        when {
            resolved.isNullOrBlank() -> {
                Text(
                    sticker.name.ifBlank { "Sticker" },
                    color = AppColors.textMuted,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(8.dp),
                )
            }
            sticker.mediaType == "webm" && playAnimation -> {
                ProfileVideoAvatar(
                    url = resolved,
                    modifier = Modifier.matchParentSize(),
                    contentDescription = sticker.name,
                )
            }
            else -> {
                AsyncImage(
                    model = resolved,
                    contentDescription = sticker.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.matchParentSize().padding(4.dp),
                )
            }
        }
    }
}

@Composable
fun StickerInline(
    sticker: StickerMessage,
    modifier: Modifier = Modifier,
    playAnimation: Boolean = true,
) {
    StickerMedia(
        sticker = sticker,
        modifier = modifier.widthIn(max = 180.dp),
        size = 150.dp,
        playAnimation = playAnimation,
    )
}

@Composable
fun StickerPickerDialog(
    stickers: List<Sticker>,
    isLoading: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
    onSelect: (Sticker) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Stickers", color = AppColors.text, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                when {
                    isLoading && stickers.isEmpty() -> {
                        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = AppColors.accent)
                        }
                    }
                    stickers.isEmpty() -> {
                        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                            Text(
                                error ?: "No stickers yet.",
                                color = AppColors.textMuted,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                    else -> {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(86.dp),
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            contentPadding = PaddingValues(2.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(stickers, key = { it.id }) { sticker ->
                                Column(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { onSelect(sticker) }
                                        .padding(4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    StickerMedia(sticker.toMessage(), size = 76.dp)
                                    Text(
                                        sticker.name,
                                        color = AppColors.text,
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onRefresh, enabled = !isLoading) {
                Text("Refresh")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
        containerColor = AppColors.surface,
    )
}

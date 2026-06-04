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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
    coins: Int = 0,
    isPremium: Boolean = false,
    purchasingStickerId: String? = null,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
    onPurchase: (Sticker) -> Unit = {},
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
                                val canUse = sticker.canUseSticker(isPremium)
                                val canBuy = sticker.accessMode == "sell" && !sticker.owned
                                val label = when {
                                    canUse -> null
                                    sticker.accessMode == "pro" -> "Needs Pro"
                                    canBuy -> "${sticker.priceCoins} Berries"
                                    else -> "Locked"
                                }
                                Column(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .then(if (canUse) Modifier.clickable { onSelect(sticker) } else Modifier)
                                        .background(
                                            if (canUse) Color.Transparent
                                            else Color.Black.copy(alpha = 0.22f),
                                        )
                                        .padding(4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Box(contentAlignment = Alignment.BottomCenter) {
                                        StickerMedia(sticker.toMessage(), size = 76.dp)
                                        label?.let {
                                            Text(
                                                it,
                                                color = Color.White,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp))
                                                    .background(Color.Black.copy(alpha = 0.72f))
                                                    .padding(horizontal = 3.dp, vertical = 2.dp),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                    Text(
                                        sticker.name,
                                        color = AppColors.text,
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                    )
                                    if (canBuy) {
                                        Button(
                                            onClick = { onPurchase(sticker) },
                                            enabled = !isLoading && purchasingStickerId == null && coins >= sticker.priceCoins,
                                            modifier = Modifier.fillMaxWidth().height(28.dp).padding(top = 4.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = AppColors.accent,
                                                disabledContainerColor = AppColors.surfaceVariant,
                                            ),
                                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                        ) {
                                            Text(
                                                when {
                                                    purchasingStickerId == sticker.id -> "Buying"
                                                    coins < sticker.priceCoins -> "Need"
                                                    else -> "Buy"
                                                },
                                                fontSize = 10.sp,
                                                maxLines = 1,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Text(
                    "Balance: $coins Berries",
                    color = AppColors.textMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
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

fun Sticker.canUseSticker(isPremium: Boolean): Boolean = when (accessMode) {
    "free" -> true
    "pro" -> isPremium
    "sell" -> owned
    else -> owned
}

package to.kuudere.anisuge.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import to.kuudere.anisuge.data.models.BffShopItem
import to.kuudere.anisuge.ui.ProfileAvatar

private val BG_CARD = Color(0xFF141414)
private val TEXT = Color.White
private val MUTED = Color(0xFF9E9E9E)
private val ACCENT = Color(0xFFE50914)
@Composable
fun ShopSettingsTab(
    uiState: SettingsUiState,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onPurchase: (String) -> Unit,
    onOpenRedeem: () -> Unit,
    onClaimDaily: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp),
    ) {
        Text(
            "Frame shop",
            color = TEXT,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "Animated profile frames — earn Berries by watching · buy packs coming soon.",
            color = MUTED,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
        )

        BerriesBalanceCard(
            balance = uiState.shopCoins,
            loginStreak = uiState.rewardsLoginStreak,
            canClaimDaily = uiState.rewardsCanClaimDaily,
            nextDailyReward = uiState.rewardsNextDaily,
            todayWatch = uiState.rewardsTodayWatch,
            todayWatchCap = uiState.rewardsTodayWatchCap,
            isClaimingDaily = uiState.isClaimingDailyReward,
            onClaimDaily = onClaimDaily,
        )

        Spacer(Modifier.height(10.dp))

        OutlinedButton(
            onClick = onOpenRedeem,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Redeem Berries", maxLines = 1)
        }

        Spacer(Modifier.height(14.dp))

        OutlinedButton(
            onClick = onRefresh,
            enabled = !uiState.isLoadingShop,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Refresh shop", maxLines = 1)
        }

        Spacer(Modifier.height(16.dp))

        when {
            uiState.isLoadingShop -> {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = Color.White)
                }
            }
            uiState.shopCatalog.isEmpty() -> {
                Text(
                    "No frames in the shop yet.",
                    color = MUTED,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            else -> {
                ShopItemGrid(
                    items = uiState.shopCatalog,
                    pfpUrl = uiState.userProfile?.effectiveAvatar,
                    purchasingId = uiState.shopPurchasingId,
                    balance = uiState.shopCoins,
                    onPurchase = onPurchase,
                )
                if (uiState.shopCatalogHasMore) {
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = onLoadMore,
                        enabled = !uiState.isLoadingMoreShop,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (uiState.isLoadingMoreShop) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text("Load more")
                        }
                    }
                    val shown = uiState.shopCatalog.size
                    val total = uiState.shopCatalogTotal
                    if (total > 0) {
                        Text(
                            "Showing $shown of $total",
                            color = MUTED,
                            fontSize = 11.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

private fun berryLabel(amount: Int): String {
    val word = if (amount == 1) "Berry" else "Berries"
    return "${formatBerries(amount)} $word"
}

@Composable
private fun ShopItemGrid(
    items: List<BffShopItem>,
    pfpUrl: String?,
    purchasingId: String?,
    balance: Int,
    onPurchase: (String) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val minCell = 168.dp
        val columns = maxOf(1, (maxWidth / minCell).toInt().coerceAtMost(4))
        val rows = items.chunked(columns)

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            rows.forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    rowItems.forEach { item ->
                        key(item.id) {
                            ShopItemCard(
                                item = item,
                                pfpUrl = pfpUrl,
                                balance = balance,
                                isPurchasing = purchasingId == item.id,
                                onPurchase = { onPurchase(item.id) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    repeat(columns - rowItems.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun ShopItemCard(
    item: BffShopItem,
    pfpUrl: String?,
    balance: Int,
    isPurchasing: Boolean,
    onPurchase: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val canAfford = balance >= item.priceCoins

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(BG_CARD)
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ProfileAvatar(
            url = pfpUrl,
            avatarSize = 52.dp,
            frameUrl = item.assetUrl,
            frameCacheKey = item.id,
            showBundledTestFrame = false,
            contentDescription = item.name,
            modifier = Modifier.padding(4.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            item.name,
            color = TEXT,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        if (item.description.isNotBlank()) {
            Text(
                item.description,
                color = MUTED,
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            berryLabel(item.priceCoins),
            color = ShopBerryGoldDim,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(8.dp))
        when {
            item.owned -> {
                Text(
                    "Owned",
                    color = Color(0xFF81C784),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                )
            }
            !canAfford -> {
                Text(
                    "Need more Berries",
                    color = MUTED,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                )
            }
            else -> {
                Button(
                    onClick = onPurchase,
                    enabled = !isPurchasing,
                    colors = ButtonDefaults.buttonColors(containerColor = ACCENT),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (isPurchasing) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text("Buy", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

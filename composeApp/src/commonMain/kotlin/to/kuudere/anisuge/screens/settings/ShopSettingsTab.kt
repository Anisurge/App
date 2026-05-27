package to.kuudere.anisuge.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.distinctUntilChanged
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
    modifier: Modifier = Modifier,
) {
    val gridState = rememberLazyGridState()

    LaunchedEffect(
        gridState,
        uiState.shopCatalog.size,
        uiState.shopCatalogHasMore,
        uiState.isLoadingShop,
        uiState.isLoadingMoreShop,
    ) {
        snapshotFlow {
            val info = gridState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            val total = info.totalItemsCount
            Triple(lastVisible, total, gridState.canScrollForward)
        }
            .distinctUntilChanged()
            .collect { (lastVisible, total, canScrollForward) ->
                if (
                    canScrollForward &&
                    total > 0 &&
                    lastVisible >= total - 3 &&
                    uiState.shopCatalogHasMore &&
                    !uiState.isLoadingShop &&
                    !uiState.isLoadingMoreShop
                ) {
                    onLoadMore()
                }
            }
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 168.dp),
        state = gridState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            ShopHeader(
                balance = uiState.shopCoins,
                onRefresh = onRefresh,
                isRefreshing = uiState.isLoadingShop,
            )
        }

        when {
            uiState.isLoadingShop && uiState.shopCatalog.isEmpty() -> {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = Color.White)
                    }
                }
            }
            uiState.shopCatalog.isEmpty() -> {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        "No frames in the store yet.",
                        color = MUTED,
                        modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
                    )
                }
            }
            else -> {
                items(
                    items = uiState.shopCatalog,
                    key = { it.id },
                ) { item ->
                    ShopItemCard(
                        item = item,
                        pfpUrl = uiState.userProfile?.effectiveAvatar,
                        balance = uiState.shopCoins,
                        isPurchasing = uiState.shopPurchasingId == item.id,
                        onPurchase = { onPurchase(item.id) },
                    )
                }

                if (uiState.shopCatalogHasMore || uiState.isLoadingMoreShop) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        ShopLoadMoreFooter(
                            isLoading = uiState.isLoadingMoreShop,
                            shown = uiState.shopCatalog.size,
                            total = uiState.shopCatalogTotal,
                            onLoadMore = onLoadMore,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ShopHeader(
    balance: Int,
    onRefresh: () -> Unit,
    isRefreshing: Boolean,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            "Store",
            color = TEXT,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "Animated profile frames — spend Berries from Settings → Berries tab.",
            color = MUTED,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )
        Row(
            modifier = Modifier.padding(bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            BerryIcon(size = 22.dp)
            Text(
                "Balance: ${formatBerries(balance)} Berries",
                color = ShopBerryGoldDim,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        OutlinedButton(
            onClick = onRefresh,
            enabled = !isRefreshing,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Refresh store", maxLines = 1)
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ShopLoadMoreFooter(
    isLoading: Boolean,
    shown: Int,
    total: Int,
    onLoadMore: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.size(28.dp),
                strokeWidth = 2.dp,
            )
        } else {
            OutlinedButton(
                onClick = onLoadMore,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Load more")
            }
        }
        if (total > 0) {
            Text(
                "Showing $shown of $total",
                color = MUTED,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 6.dp),
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun berryLabel(amount: Int): String {
    val word = if (amount == 1) "Berry" else "Berries"
    return "${formatBerries(amount)} $word"
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
    val originalPrice = item.priceCoinsOriginal?.takeIf { it > item.priceCoins }
    val discountPercent = item.premiumDiscountPercent?.takeIf { it > 0 }

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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth(),
        ) {
            BerryIcon(size = 16.dp)
            Text(
                " ${berryLabel(item.priceCoins)}",
                color = ShopBerryGoldDim,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (originalPrice != null) {
                Text(
                    " ${berryLabel(originalPrice)}",
                    color = MUTED,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = TextDecoration.LineThrough,
                )
            }
        }
        if (discountPercent != null) {
            Text(
                "$discountPercent% Premium discount",
                color = Color(0xFFFFD54F),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
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

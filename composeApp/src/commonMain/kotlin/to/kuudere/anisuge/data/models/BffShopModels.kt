package to.kuudere.anisuge.data.models

import kotlinx.serialization.Serializable

@Serializable
data class BffShopItem(
    val id: String,
    val slug: String,
    val kind: String = "avatar_frame",
    val accessMode: String = "sell",
    val name: String,
    val description: String = "",
    val priceCoins: Int,
    val priceCoinsOriginal: Int? = null,
    val premiumDiscountPercent: Int? = null,
    val mediaType: String = "image",
    val assetUrl: String,
    val thumbnailUrl: String? = null,
    val previewVideoUrl: String? = null,
    val owned: Boolean = false,
)

@Serializable
data class BffShopItemsResponse(
    val coins: Int = 0,
    val items: List<BffShopItem> = emptyList(),
)

@Serializable
data class BffShopMeResponse(
    val coins: Int = 0,
    val isPremium: Boolean = false,
    val owned: List<BffShopItem> = emptyList(),
    val catalog: List<BffShopItem> = emptyList(),
    val catalogTotal: Int = 0,
    val catalogHasMore: Boolean = false,
)

@Serializable
data class BffShopOwnedResponse(
    val owned: List<BffShopItem> = emptyList(),
)

@Serializable
data class BffShopPurchaseRequest(
    val itemId: String,
)

@Serializable
data class BffShopPurchaseResponse(
    val coins: Int,
    val item: BffShopItem,
    val user: BffPublicUser? = null,
)

@Serializable
data class BffShopDownloadAllResponse(
    val items: List<BffShopDownloadEntry> = emptyList(),
)

@Serializable
data class BffShopRedeemRequest(
    val code: String,
)

@Serializable
data class BffShopRedeemResponse(
    val coins: Int,
    val rewardCoins: Int,
    val code: String,
    val message: String? = null,
    val user: BffPublicUser? = null,
)

@Serializable
data class BffShopDownloadEntry(
    val id: String,
    val slug: String,
    val name: String,
    val assetUrl: String,
    val previewVideoUrl: String? = null,
)

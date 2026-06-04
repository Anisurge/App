package to.kuudere.anisuge.data.models

import kotlinx.serialization.Serializable

@Serializable
data class BffRewardsTodayEarned(
    val watch: Int = 0,
    val watchCap: Int = 12,
    val chat: Int = 0,
    val total: Int = 0,
)

@Serializable
data class BffRewardsStatusResponse(
    val coins: Int = 0,
    val loginStreak: Int = 0,
    val canClaimDaily: Boolean = false,
    val dailyClaimedToday: Boolean = false,
    val todayEarned: BffRewardsTodayEarned = BffRewardsTodayEarned(),
    val nextDailyReward: Int = 0,
)

@Serializable
data class BffBerryPackPrice(
    val INR: Int = 0,
)

@Serializable
data class BffBerryPack(
    val id: String = "",
    val label: String = "",
    val coins: Int = 0,
    val prices: BffBerryPackPrice = BffBerryPackPrice(),
)

@Serializable
data class BffBerryPacksResponse(
    val packs: List<BffBerryPack> = emptyList(),
)

@Serializable
data class BffBerryCheckoutSessionResponse(
    val id: String = "",
    val packId: String = "",
    val checkoutUrl: String = "",
    val expiresAt: String = "",
)

@Serializable
data class BffDailyClaimResponse(
    val granted: Boolean = false,
    val amount: Int = 0,
    val coins: Int = 0,
    val loginStreak: Int = 0,
    val message: String? = null,
)

@Serializable
data class BffWatchProgressReward(
    val granted: Boolean = false,
    val amount: Int = 0,
    val source: String = "",
    val reason: String? = null,
)

@Serializable
data class BffWatchProgressResponse(
    val ok: Boolean = false,
    val coins: Int = 0,
    val reward: BffWatchProgressReward? = null,
)

package to.kuudere.anisuge.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val ShopBerryMuted = Color(0xFF9E9E9E)
internal val ShopBerryGold = Color(0xFFFFD54F)
internal val ShopBerryGoldDim = Color(0xFFFFB300)
internal val ShopBerryPanel = Color(0xFF1E1808)

internal fun formatBerries(amount: Int): String {
    val negative = amount < 0
    val n = kotlin.math.abs(amount)
    val raw = n.toString()
    val grouped = raw.reversed().chunked(3).joinToString(",").reversed()
    return if (negative) "-$grouped" else grouped
}

@Composable
internal fun BerriesBalanceCard(
    balance: Int,
    modifier: Modifier = Modifier,
    loginStreak: Int = 0,
    canClaimDaily: Boolean = false,
    nextDailyReward: Int = 0,
    todayWatch: Int = 0,
    todayWatchCap: Int = 12,
    isClaimingDaily: Boolean = false,
    onClaimDaily: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(ShopBerryPanel, Color(0xFF2A2210)),
                ),
            )
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Your balance",
                    color = ShopBerryMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    formatBerries(balance),
                    color = ShopBerryGold,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Text(
                    if (balance == 1) "Berry" else "Berries",
                    color = ShopBerryGoldDim,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                "฿",
                color = ShopBerryGold.copy(alpha = 0.35f),
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Text(
            "Earn by watching · Today $todayWatch/$todayWatchCap from episodes",
            color = ShopBerryMuted,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            modifier = Modifier.padding(top = 10.dp),
        )

        if (loginStreak > 0) {
            Text(
                "${loginStreak}-day streak",
                color = ShopBerryGoldDim,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        if (onClaimDaily != null) {
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onClaimDaily,
                enabled = canClaimDaily && !isClaimingDaily,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFE50914),
                    disabledContainerColor = Color(0xFF3A3A3A),
                ),
            ) {
                if (isClaimingDaily) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                } else if (canClaimDaily) {
                    Text("Claim daily (+$nextDailyReward Berries)")
                } else {
                    Text("Daily claimed")
                }
            }
        }
    }
}

@Composable
internal fun EarnBerriesTipsCard(
    todayWatch: Int,
    todayWatchCap: Int,
    loginStreak: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF141414))
            .padding(16.dp),
    ) {
        Text(
            "How to earn Berries",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(12.dp))
        EarnBerryTip(
            title = "Watch anime",
            detail = "Finish episodes (~85% watched) for 2 Berries each · up to $todayWatchCap/day ($todayWatch today)",
        )
        Spacer(Modifier.height(10.dp))
        EarnBerryTip(
            title = "Daily check-in",
            detail = if (loginStreak > 0) {
                "Claim above — ${loginStreak}-day streak adds bonus Berries"
            } else {
                "Claim above — streaks add bonus Berries over time"
            },
        )
        Spacer(Modifier.height(10.dp))
        EarnBerryTip(
            title = "Community chat",
            detail = "Your first message each day earns 1 Berry",
        )
        Spacer(Modifier.height(10.dp))
        EarnBerryTip(
            title = "Promo codes",
            detail = "Redeem event or Discord codes below",
        )
    }
}

@Composable
private fun EarnBerryTip(title: String, detail: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            title,
            color = ShopBerryGoldDim,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            detail,
            color = ShopBerryMuted,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

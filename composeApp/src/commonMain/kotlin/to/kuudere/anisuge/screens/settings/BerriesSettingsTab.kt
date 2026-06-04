package to.kuudere.anisuge.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import to.kuudere.anisuge.theme.AppColors
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import to.kuudere.anisuge.data.models.BffBerryPack

private val BG_CARD: Color get() = AppColors.surfaceVariant
private val TEXT: Color get() = AppColors.text

@Composable
fun BerriesSettingsTab(
    uiState: SettingsUiState,
    onCodeChange: (String) -> Unit,
    onRedeem: () -> Unit,
    onClaimDaily: () -> Unit,
    onBuyBerryPack: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp),
    ) {
        Text(
            "Berries",
            color = TEXT,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "Anisurge currency for profile frames. Earn by watching and checking in, or redeem promo codes.",
            color = ShopBerryMuted,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            modifier = Modifier.padding(top = 4.dp, bottom = 20.dp),
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

        Spacer(Modifier.height(16.dp))

        BuyBerriesCard(
            packs = uiState.berryPacks,
            isLoading = uiState.isLoadingBerryPacks,
            checkoutPackId = uiState.berryCheckoutPackId,
            onBuy = onBuyBerryPack,
        )

        Spacer(Modifier.height(16.dp))

        EarnBerriesTipsCard(
            todayWatch = uiState.rewardsTodayWatch,
            todayWatchCap = uiState.rewardsTodayWatchCap,
            loginStreak = uiState.rewardsLoginStreak,
        )

        Spacer(Modifier.height(20.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(BG_CARD)
                .padding(16.dp),
        ) {
            Text(
                "Redeem code",
                color = TEXT,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Enter a promo code to add Berries. Each code works once per account.",
                color = ShopBerryMuted,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
            OutlinedTextField(
                value = uiState.redeemCodeDraft,
                onValueChange = onCodeChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("LAUNCH-2026", color = ShopBerryMuted) },
                singleLine = true,
                enabled = !uiState.isRedeemingCode,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TEXT,
                    unfocusedTextColor = TEXT,
                    focusedBorderColor = ShopBerryGoldDim,
                    unfocusedBorderColor = Color(0xFF333333),
                    cursorColor = ShopBerryGold,
                ),
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onRedeem,
                enabled = uiState.redeemCodeDraft.isNotBlank() && !uiState.isRedeemingCode,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ShopBerryGoldDim,
                    contentColor = Color.Black,
                    disabledContainerColor = Color(0xFF2A2A2A),
                    disabledContentColor = ShopBerryMuted,
                ),
            ) {
                if (uiState.isRedeemingCode) {
                    CircularProgressIndicator(
                        color = Color.Black,
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("Redeem", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun BuyBerriesCard(
    packs: List<BffBerryPack>,
    isLoading: Boolean,
    checkoutPackId: String?,
    onBuy: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(BG_CARD)
            .padding(16.dp),
    ) {
        Text(
            "Buy Berries",
            color = TEXT,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            "Top up for stickers and profile cosmetics.",
            color = ShopBerryMuted,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )
        if (isLoading && packs.isEmpty()) {
            CircularProgressIndicator(
                color = ShopBerryGold,
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                packs.forEach { pack ->
                    BerryPackRow(
                        pack = pack,
                        isCheckingOut = checkoutPackId == pack.id,
                        checkoutInProgress = checkoutPackId != null,
                        onBuy = { onBuy(pack.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun BerryPackRow(
    pack: BffBerryPack,
    isCheckingOut: Boolean,
    checkoutInProgress: Boolean,
    onBuy: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF121212))
            .border(1.dp, Color(0xFF2C2410), RoundedCornerShape(12.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BerryIcon(size = 34.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                pack.label.ifBlank { "Berry Pack" },
                color = TEXT,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            BerriesAmountLabel(
                amount = pack.coins,
                iconSize = 14.dp,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        Button(
            onClick = onBuy,
            enabled = !checkoutInProgress,
            colors = ButtonDefaults.buttonColors(
                containerColor = ShopBerryGoldDim,
                contentColor = Color.Black,
                disabledContainerColor = Color(0xFF2A2A2A),
                disabledContentColor = ShopBerryMuted,
            ),
        ) {
            if (isCheckingOut) {
                CircularProgressIndicator(
                    color = Color.Black,
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                Text("₹${pack.prices.INR}", fontWeight = FontWeight.Bold)
            }
        }
    }
}

package to.kuudere.anisuge.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val BG_CARD = Color(0xFF141414)
private val TEXT = Color.White

@Composable
fun BerriesSettingsTab(
    uiState: SettingsUiState,
    onCodeChange: (String) -> Unit,
    onRedeem: () -> Unit,
    onClaimDaily: () -> Unit,
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

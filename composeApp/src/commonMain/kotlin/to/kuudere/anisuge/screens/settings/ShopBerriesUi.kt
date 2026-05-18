package to.kuudere.anisuge.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
internal fun BerriesBalanceCard(balance: Int, modifier: Modifier = Modifier) {
    Box(
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
    }
}

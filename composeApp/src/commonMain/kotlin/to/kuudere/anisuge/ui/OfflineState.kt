package to.kuudere.anisuge.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import to.kuudere.anisuge.i18n.LocalAppStrings
import to.kuudere.anisuge.theme.AppColors

@Composable
fun OfflineState(
    onRetry: () -> Unit,
    isLoading: Boolean = false,
    modifier: Modifier = Modifier.fillMaxSize()
) {
    val strings = LocalAppStrings.current
    Box(
        modifier = modifier
            .background(AppColors.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(horizontal = 40.dp)
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(AppColors.text.copy(alpha = 0.05f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.WifiOff,
                    contentDescription = null,
                    tint = AppColors.textMuted,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(Modifier.height(4.dp))

            Text(
                text = strings.noInternetConnection,
                color = AppColors.text,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )

            Text(
                text = strings.offlineDescription,
                color = AppColors.textMuted,
                fontSize = 14.sp,
                lineHeight = 22.sp,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = onRetry,
                enabled = !isLoading,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.accent,
                    contentColor   = AppColors.onAccent,
                    disabledContainerColor = AppColors.accent.copy(alpha = 0.7f),
                    disabledContentColor   = AppColors.onAccent.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 32.dp, vertical = 14.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = AppColors.onAccent.copy(alpha = 0.6f),
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(strings.retrying, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                } else {
                    Text(strings.retry, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                }
            }
        }
    }
}

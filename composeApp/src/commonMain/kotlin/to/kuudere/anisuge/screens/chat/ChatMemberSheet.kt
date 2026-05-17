package to.kuudere.anisuge.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import to.kuudere.anisuge.data.models.ChatMemberProfile
import to.kuudere.anisuge.data.models.ChatMessage
import to.kuudere.anisuge.ui.ChatUsernameLabel
import to.kuudere.anisuge.ui.ProfileAvatar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatMemberSheet(
    member: ChatMemberProfile,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF141414),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ProfileAvatar(
                url = member.avatarUrl,
                modifier = Modifier.size(96.dp),
                contentDescription = member.username,
            )

            val labelMessage = ChatMessage(
                userId = member.userId,
                username = member.username,
                avatarUrl = member.avatarUrl,
                isPremium = member.isPremium,
                nameStyle = member.nameStyle,
            )
            ChatUsernameLabel(
                message = labelMessage,
                isMine = false,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )

            member.joinedAt?.let { joined ->
                Text(
                    text = "Joined ${formatJoinDate(joined)}",
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 13.sp,
                )
            }

            if (member.isPremium) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Premium",
                    color = Color(0xFFFFD54F),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .background(Color(0xFF2A2200), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun formatJoinDate(iso: String): String {
    val day = iso.substringBefore('T').ifBlank { iso }
    return day
}

package to.kuudere.anisuge.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import to.kuudere.anisuge.data.models.BffShopItem
import to.kuudere.anisuge.data.models.UserProfile
import to.kuudere.anisuge.ui.ProfileAvatar

@Composable
fun ProfileFramePickerSection(
    user: UserProfile,
    ownedFrames: List<BffShopItem>,
    selectedItemId: String?,
    isSaving: Boolean,
    isLoadingOwned: Boolean = false,
    onSelectFrame: (BffShopItem?) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1A1A1A))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Animated frames", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Text(
            "Tap a frame to equip — saves instantly for profile and chat.",
            color = Color(0xFF9E9E9E),
            fontSize = 11.sp,
            lineHeight = 16.sp,
        )

        if (isLoadingOwned && ownedFrames.isEmpty()) {
            CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier
                    .size(28.dp)
                    .align(Alignment.CenterHorizontally),
                strokeWidth = 2.dp,
            )
            return@Column
        }

        if (ownedFrames.isEmpty()) {
            Text(
                "No frames yet — buy some in the Store.",
                color = Color(0xFF9E9E9E),
                fontSize = 12.sp,
            )
            return@Column
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 4.dp),
        ) {
            item(key = "none") {
                FramePickChip(
                    label = "None",
                    selected = selectedItemId == null && user.equippedFrameUrl.isNullOrBlank(),
                    isSaving = isSaving,
                    onClick = { onSelectFrame(null) },
                ) {
                    Box(
                        Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF333333)),
                    )
                }
            }
            items(ownedFrames, key = { it.id }) { item ->
                val selected = selectedItemId == item.id ||
                    user.equippedFrameItemId == item.id
                FramePickChip(
                    label = item.name,
                    selected = selected,
                    isSaving = isSaving,
                    onClick = { onSelectFrame(item) },
                ) {
                    ProfileAvatar(
                        url = user.effectiveAvatar,
                        avatarSize = 40.dp,
                        frameUrl = item.assetUrl,
                        frameCacheKey = item.id,
                        showBundledTestFrame = false,
                    )
                }
            }
        }

        if (isSaving) {
            CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.size(20.dp).align(Alignment.CenterHorizontally),
                strokeWidth = 2.dp,
            )
        }
    }
}

@Composable
private fun FramePickChip(
    label: String,
    selected: Boolean,
    isSaving: Boolean,
    onClick: () -> Unit,
    preview: @Composable () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) Color(0xFFE50914) else Color(0xFF333333),
                shape = RoundedCornerShape(12.dp),
            )
            .background(Color(0xFF111111))
            .clickable(enabled = !isSaving, onClick = onClick)
            .padding(10.dp),
    ) {
        preview()
        Text(
            label,
            color = Color.White,
            fontSize = 10.sp,
            maxLines = 1,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

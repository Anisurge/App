package to.kuudere.anisuge.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import to.kuudere.anisuge.data.models.ChatMemberProfile
import to.kuudere.anisuge.data.models.ChatMessage
import to.kuudere.anisuge.data.models.ChatProfileLibraryItem
import to.kuudere.anisuge.theme.AppColors
import to.kuudere.anisuge.ui.ChatDecoratedAvatar
import to.kuudere.anisuge.ui.ChatUsernameLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatMemberSheet(
    member: ChatMemberProfile,
    onDismiss: () -> Unit,
    onAnimeClick: (String) -> Unit = {},
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var watchlistQuery by remember(member.userId) { mutableStateOf("") }
    val filteredWatchlist by remember(member.watchlist, watchlistQuery) {
        derivedStateOf {
            val query = watchlistQuery.trim()
            if (query.isBlank()) {
                member.watchlist
            } else {
                member.watchlist.filter {
                    it.title.contains(query, ignoreCase = true) ||
                        it.animeId.contains(query, ignoreCase = true) ||
                        (it.subtitle?.contains(query, ignoreCase = true) == true)
                }
            }
        }
    }
    val watchlistRows by remember(filteredWatchlist) {
        derivedStateOf { filteredWatchlist.chunked(2) }
    }

    LaunchedEffect(member.userId) {
        runCatching { sheetState.expand() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AppColors.surface,
        modifier = Modifier.fillMaxHeight(0.92f),
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 8.dp, bottom = 4.dp)
                    .width(42.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(Color.White.copy(alpha = 0.28f)),
            )
        },
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                ProfileHeader(member)
            }

            item { Spacer(Modifier.height(10.dp)) }

            when {
                member.isLoadingDetails -> item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(96.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                }

                member.detailError != null -> item {
                    Text(
                        member.detailError,
                        color = AppColors.error,
                        fontSize = 13.sp,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                member.hidden -> item {
                    EmptyProfileText("Watch activity hidden")
                }

                member.isBot -> item {
                    BotSupportSection()
                }

                !member.isBot -> {
                    item {
                        ProfileStatsRow(member)
                    }

                    item {
                        ProfileAboutBlock(member)
                    }

                    item {
                        ProfileSectionTitle("Watch History", "${member.watchHistory.size} recent")
                        if (member.watchHistory.isEmpty()) {
                            EmptyProfileText("No recent watch history")
                        } else {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                items(
                                    member.watchHistory,
                                    key = { "history-${it.animeId}-${it.subtitle}" },
                                ) { item ->
                                    ProfileAnimeTile(
                                        item = item,
                                        onClick = { onAnimeClick(item.animeId) },
                                    )
                                }
                            }
                        }
                    }

                    item {
                        ProfileSectionTitle(
                            "Watchlist",
                            if (watchlistQuery.isBlank()) {
                                "${member.watchlist.size} saved"
                            } else {
                                "${filteredWatchlist.size}/${member.watchlist.size}"
                            },
                        )
                        OutlinedTextField(
                            value = watchlistQuery,
                            onValueChange = { watchlistQuery = it.take(80) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = {
                                Text("Search ${member.watchlist.size} saved titles", color = Color.White.copy(alpha = 0.38f))
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = AppColors.text,
                                unfocusedTextColor = AppColors.text,
                                cursorColor = AppColors.accent,
                                focusedBorderColor = AppColors.border,
                                unfocusedBorderColor = AppColors.border,
                            ),
                            shape = RoundedCornerShape(10.dp),
                        )
                    }

                    if (filteredWatchlist.isEmpty()) {
                        item { EmptyProfileText("No watchlist matches") }
                    } else {
                        items(
                            watchlistRows,
                            key = { row -> row.joinToString("|") { "${it.animeId}-${it.updatedAt}" } },
                            contentType = { "watchlist-grid-row" },
                        ) { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                row.forEach { item ->
                                    ProfileWatchlistTile(
                                        item = item,
                                        onClick = { onAnimeClick(item.animeId) },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                if (row.size == 1) {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun ProfileHeader(member: ChatMemberProfile) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        ChatDecoratedAvatar(
            avatarUrl = member.effectiveAvatarUrl,
            frameUrl = member.avatarFrameUrl,
            outerFrameUrl = member.avatarOuterUrl,
            userId = member.userId,
            avatarSize = 80.dp,
            contentDescription = member.username,
        )

        val labelMessage = ChatMessage(
            userId = member.userId,
            username = member.username,
            avatarUrl = member.effectiveAvatarUrl,
            isPremium = member.isPremium,
            nameStyle = member.nameStyle,
        )
        ChatUsernameLabel(
            message = labelMessage,
            isMine = false,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )

        if (member.isBot) {
            ProfileBadge("Anisurge AI Bot", Color(0xFFBF80FF), Color(0xFF241035))
        }
        member.joinedAt?.let { joined ->
            Text(
                text = "Joined ${formatJoinDate(joined)}",
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 13.sp,
            )
        }
        if (!member.isBot) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProfileBadge(formatBerries(member.coins), Color(0xFFFFB300), Color(0xFF2A2000))
                ProfileBadge("${member.karmaPoints} karma", Color(0xFFFFD54F), Color(0xFF2A2200))
            }
        }
        if (member.isPremium) {
            ProfileBadge("Premium", Color(0xFFFFD54F), Color(0xFF2A2200))
        }
    }
}

@Composable
private fun ProfileBadge(text: String, color: Color, background: Color) {
    Text(
        text = text,
        color = color,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .background(background, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
private fun ProfileStatsRow(member: ChatMemberProfile) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.045f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ProfileMiniStat("Watchlist", member.watchlist.size.toString(), Modifier.weight(1f))
        ProfileMiniStat("Recent", member.watchHistory.size.toString(), Modifier.weight(1f))
        ProfileMiniStat("Karma", member.karmaPoints.toString(), Modifier.weight(1f))
    }
}

@Composable
private fun ProfileMiniStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(value, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(label, color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp, maxLines = 1)
    }
}

@Composable
private fun ProfileAboutBlock(member: ChatMemberProfile) {
    val uriHandler = LocalUriHandler.current
    val bio = member.bio?.takeIf { it.isNotBlank() }
    val website = member.website?.takeIf { it.isNotBlank() }
    if (bio == null && website == null) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.035f))
            .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("About", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        bio?.let {
            Text(it, color = Color.White.copy(alpha = 0.72f), fontSize = 13.sp, lineHeight = 18.sp)
        }
        website?.let {
            Text(
                it.removePrefix("https://").removePrefix("http://"),
                color = Color(0xFFBF80FF),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable {
                    val target = if (it.startsWith("http://") || it.startsWith("https://")) {
                        it
                    } else {
                        "https://$it"
                    }
                    runCatching { uriHandler.openUri(target) }
                },
            )
        }
    }
}

@Composable
private fun ProfileSectionTitle(text: String, meta: String? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
        )
        meta?.let {
            Text(
                it,
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun EmptyProfileText(text: String) {
    Text(
        text = text,
        color = Color.White.copy(alpha = 0.5f),
        fontSize = 13.sp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
    )
}

@Composable
private fun ProfileAnimeTile(
    item: ChatProfileLibraryItem,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(92.dp)
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        AsyncImage(
            model = item.imageUrl,
            contentDescription = item.title,
            modifier = Modifier
                .fillMaxWidth()
                .height(124.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.08f)),
            contentScale = ContentScale.Crop,
        )
        Text(
            item.title,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
        )
        item.subtitle?.takeIf { it.isNotBlank() }?.let {
            Text(it, color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp, maxLines = 1)
        }
    }
}

@Composable
private fun ProfileWatchlistTile(
    item: ChatProfileLibraryItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        AsyncImage(
            model = item.imageUrl,
            contentDescription = item.title,
            modifier = Modifier
                .fillMaxWidth()
                .height(166.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.08f)),
            contentScale = ContentScale.Crop,
        )
        Text(
            item.title,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 15.sp,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            item.subtitle?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it.replaceFirstChar { c -> c.uppercase() },
                    color = Color(0xFFBF80FF),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
            item.updatedAt?.takeIf { it.isNotBlank() }?.let {
                Text(
                    formatProfileDate(it),
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun BotSupportSection() {
    val uriHandler = LocalUriHandler.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.035f))
            .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(14.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Anisurge Support & Community",
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
        )

        Text(
            text = "Need help, want to suggest features, or support the project? Use the official links below to get in touch with our community and developer team.",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 12.sp,
            lineHeight = 16.sp,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Donate button
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFE91E63))
                    .clickable { runCatching { uriHandler.openUri("https://anisurge.lol/donate") } }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Donate", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }

            // Premium button
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFFFD54F))
                    .clickable { runCatching { uriHandler.openUri("https://anisurge.lol") } }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Get Premium", color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Discord button
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF5865F2))
                    .clickable { runCatching { uriHandler.openUri("https://discord.gg/yR4T2dbeCx") } }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Discord", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }

            // Telegram button
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF24A1DE))
                    .clickable { runCatching { uriHandler.openUri("https://t.me/anisurge") } }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Telegram", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun formatJoinDate(iso: String): String {
    val day = iso.substringBefore('T').ifBlank { iso }
    return day
}

private fun formatBerries(amount: Int): String {
    val n = kotlin.math.abs(amount)
    val grouped = n.toString().reversed().chunked(3).joinToString(",").reversed()
    val word = if (amount == 1) "Berry" else "Berries"
    return "$grouped $word"
}

private fun formatProfileDate(iso: String): String {
    return iso.substringBefore('T').takeIf { it.isNotBlank() } ?: iso
}

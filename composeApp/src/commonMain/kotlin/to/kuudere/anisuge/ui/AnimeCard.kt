package to.kuudere.anisuge.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import to.kuudere.anisuge.data.models.AnimeItem

/**
 * Reusable anime card — 1:1 replica of the SvelteKit AnimeCard.svelte.
 *
 * Layout (matches CSS exactly):
 *  ┌──────────────────────┐
 *  │ ★ 8.5                │  ← .rating-badge-top  (top:8, left:8)
 *  │                      │
 *  │  CC 12  🎤 3         │  ← .episode-badges    (bottom:8, left:8)
 *  └──────────────────────┘     .image-container   (aspect-ratio:3/4, border-radius:8px, margin-bottom:12px)
 *  ● Title                     .title-row          (gap:8px, margin-bottom:6px)
 *    TV • 24m                  .metadata-row        (gap:6px)
 */
@Composable
fun AnimeCard(
    item: AnimeItem,
    modifier: Modifier = Modifier,
    badgeText: String? = null,
    onClick: () -> Unit,
) {
    val inter   = remember { MutableInteractionSource() }
    val hovered by inter.collectIsHoveredAsState()

    // translateY(-4px) on hover
    val lift by animateDpAsState(if (hovered) 4.dp else 0.dp, tween(200))

    Column(
        modifier = modifier
            .hoverable(inter)
            .tvFocusableClick(onClick = onClick)
            .offset(y = -lift)
    ) {
        // ── .image-container ─────────────────────────────────────────────────
        // aspect-ratio: 3/4; border-radius: 8px; background-color: #1f2937;
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF0C0C0C))
        ) {
            // main-image
            val url = when {
                item.imageUrl.startsWith("http") -> item.imageUrl
                item.imageUrl.isNotBlank()        -> "https://api.reanime.to/img/poster/${item.imageUrl}"
                else                              -> ""
            }
            AsyncImage(
                model              = url,
                contentDescription = item.displayTitle,
                contentScale       = ContentScale.Crop,
                modifier           = Modifier.fillMaxSize()
            )

            // ── .rating-badge-top ────────────────────────────────────────────
            // top:8px; left:8px; padding:2px 4px; border-radius:3px;
            // bg:rgba(0,0,0,0.8); border:1px solid rgba(255,255,255,0.1);
            if (!badgeText.isNullOrBlank()) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.Black.copy(alpha = 0.8f))
                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(3.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = badgeText,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            } else if ((item.score ?: 0) > 0) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.Black.copy(alpha = 0.8f))
                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(3.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = null,
                        tint = Color(0xFFfbbf24),       // .star-icon { color: #fbbf24 }
                        modifier = Modifier.size(12.dp)  // .material-icons { font-size: 12px }
                    )
                    Text(
                        text = String.format("%.1f", item.score?.toDouble() ?: 0.0),
                        color = Color.White,
                        fontSize = 10.sp,                // font-size: 10px
                        fontWeight = FontWeight.SemiBold  // font-weight: 600
                    )
                }
            }

            // ── .episode-badges ──────────────────────────────────────────────
            // bottom:8px; left:8px; gap:4px;
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // .subtitle-badge → subtitles icon + epCount
                val subCount = item.subbed.let { if (it > 0) it else item.epCount ?: 0 }
                EpisodeBadge(
                    icon = Icons.Default.ClosedCaption,
                    count = subCount
                )
                // .dubbed-badge → mic icon + dubbedCount
                val dubCount = item.dubbed
                if (dubCount > 0) {
                    EpisodeBadge(
                        icon = Icons.Default.Mic,
                        count = dubCount
                    )
                }
            }

            // ── .hover-overlay ───────────────────────────────────────────────
            // opacity 0→1 on hover; .play-button scale(0.8)→scale(1)
            val overlayAlpha by animateFloatAsState(
                targetValue = if (hovered) 1f else 0f,
                animationSpec = tween(200)
            )
            val playScale by animateFloatAsState(
                targetValue = if (hovered) 1f else 0.8f,
                animationSpec = tween(200)
            )
            if (overlayAlpha > 0f) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.6f * overlayAlpha)),
                    contentAlignment = Alignment.Center
                ) {
                    // .play-button: 48px, bg rgba(255,255,255,0.9), border-radius 50%
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .scale(playScale)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.9f)),
                        contentAlignment = Alignment.Center
                    ) {
                        // .play-button .material-icons { font-size: 20px }
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "Play",
                            tint = Color.Black,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // ── .content-section { padding: 0 2px } ─────────────────────────────
        // margin-bottom: 12px on image-container → Spacer
        Spacer(Modifier.height(12.dp))

        Column(Modifier.padding(horizontal = 2.dp)) {
            // ── .title-row { gap:8px; margin-bottom:6px } ───────────────────
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // .status-dot: 6px, margin-top:6px
                Box(
                    Modifier
                        .padding(top = 6.dp)
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF3b82f6)) // default blue (Completed)
                )
                // .title: font-size:14px; font-weight:500; line-clamp:1
                Text(
                    text       = item.displayTitle,
                    color      = Color.White,
                    fontSize   = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis,
                    lineHeight = 17.sp,
                    modifier   = Modifier.weight(1f)
                )
            }

            // margin-bottom: 6px
            Spacer(Modifier.height(6.dp))

            // ── .metadata-row { gap:6px } ───────────────────────────────────
            // Svelte shows: {type} • {duration}m
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!item.type.isNullOrBlank()) {
                    // .metadata-item { color:#9ca3af; font-size:12px; font-weight:400 }
                    Text(item.type, color = Color(0xFF9ca3af), fontSize = 12.sp)
                }
                if (!item.type.isNullOrBlank()) {
                    // .metadata-separator { color:#6b7280; font-size:12px }
                    Text("•", color = Color(0xFF6b7280), fontSize = 12.sp)
                }
                Text("${item.duration ?: 24}m", color = Color(0xFF9ca3af), fontSize = 12.sp)
            }
        }
    }
}

// ── .episode-badge ───────────────────────────────────────────────────────────
// padding:2px 4px; border-radius:3px; font-size:10px; font-weight:600;
// bg:rgba(0,0,0,0.8); border:1px solid rgba(255,255,255,0.1);
@Composable
private fun EpisodeBadge(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    count: Int
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(Color.Black.copy(alpha = 0.8f))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(3.dp))
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // .material-icons { font-size: 12px }
        Icon(
            icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(12.dp)
        )
        Text(
            text = count.toString(),
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

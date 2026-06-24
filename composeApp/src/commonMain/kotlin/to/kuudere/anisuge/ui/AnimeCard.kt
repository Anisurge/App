package to.kuudere.anisuge.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import to.kuudere.anisuge.data.models.AnimeItem
import to.kuudere.anisuge.data.models.LatestEpisodeLang
import to.kuudere.anisuge.theme.AppColors
import to.kuudere.anisuge.data.models.latestEpisodeLang
import to.kuudere.anisuge.i18n.resolveDisplayTitle

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
    /** When true, shows a SUB / DUB / SUB·DUB badge for the latest aired episode (top-right). */
    showLatestLangBadge: Boolean = false,
    /** When true, wraps the title instead of clamping it to one line. */
    showFullTitle: Boolean = false,
    /** Dantotsu compact mode: smaller visual, corner score. */
    compact: Boolean = false,
    /** Control visibility of score/rating badges. */
    showScore: Boolean = true,
    onClick: () -> Unit,
) {
    val inter   = remember { MutableInteractionSource() }
    val hovered by inter.collectIsHoveredAsState()

    // translateY(-4px) on hover
    val lift by animateDpAsState(if (hovered) 4.dp else 0.dp, tween(200))

    // Dantotsu-inspired fast snappy scale pop on appear (overshoot-like via spring)
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val enterScale by animateFloatAsState(
        targetValue = if (entered) 1f else 0.88f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        )
    )

    Column(
        modifier = modifier
            .graphicsLayer {
                scaleX = enterScale
                scaleY = enterScale
            }
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
                .clip(RoundedCornerShape(12.dp))
                .background(AppColors.surfaceVariant)
        ) {
            // main-image
            val url = when {
                item.imageUrl.startsWith("http") -> item.imageUrl
                item.imageUrl.isNotBlank()        -> "https://api.reanime.to/img/poster/${item.imageUrl}"
                else                              -> ""
            }
            AsyncImage(
                model              = url,
                contentDescription = item.resolveDisplayTitle(),
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
            } else if (showScore && (item.score ?: 0) > 0) {
                val scoreModifier = if (compact) {
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                } else {
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                }
                Row(
                    modifier = scoreModifier
                        .clip(RoundedCornerShape(if (compact) 6.dp else 3.dp))
                        .background(Color.Black.copy(alpha = 0.75f))
                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(if (compact) 6.dp else 3.dp))
                        .padding(horizontal = if (compact) 5.dp else 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = null,
                        tint = Color(0xFFfbbf24),
                        modifier = Modifier.size(if (compact) 11.dp else 12.dp)
                    )
                    Text(
                        text = (item.score?.toDouble() ?: 0.0).let { d ->
                            val i = d.toInt()
                            if (d == i.toDouble()) "$i.0" else "${(d * 10).toInt() / 10.0}"
                        },
                        color = Color.White,
                        fontSize = if (compact) 9.sp else 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // ── Latest-episode language badge (top-right, opt-in) ────────────
            if (showLatestLangBadge) {
                item.latestEpisodeLang?.let { lang ->
                    LatestLangBadge(
                        lang = lang,
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                    )
                }
            }

            // Bottom gradient for badge readability
            Box(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.4f)
                    .align(Alignment.BottomCenter)
                    .drawWithCache {
                        val gradient = Brush.verticalGradient(
                            0f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.55f)
                        )
                        onDrawBehind { drawRect(gradient) }
                    }
            )

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
                    text       = item.resolveDisplayTitle(),
                    color      = AppColors.text,
                    fontSize   = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines   = if (showFullTitle) Int.MAX_VALUE else 1,
                    overflow   = if (showFullTitle) TextOverflow.Clip else TextOverflow.Ellipsis,
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
                    Text(item.type, color = AppColors.textMuted, fontSize = 12.sp)
                }
                if (!item.type.isNullOrBlank()) {
                    // .metadata-separator { color:#6b7280; font-size:12px }
                    Text("•", color = AppColors.textDim, fontSize = 12.sp)
                }
                Text("${item.duration}m", color = AppColors.textMuted, fontSize = 12.sp)
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

// ── Latest-episode language badge ─────────────────────────────────────────────
// Top-right pill showing whether the just-aired episode is SUB, DUB, or both.
@Composable
private fun LatestLangBadge(
    lang: LatestEpisodeLang,
    modifier: Modifier = Modifier,
) {
    val (label, bg) = when (lang) {
        LatestEpisodeLang.SUB -> "SUB" to Color(0xFF2563EB)        // blue
        LatestEpisodeLang.DUB -> "DUB" to Color(0xFF9333EA)        // purple
        LatestEpisodeLang.SUB_DUB -> "SUB·DUB" to Color(0xFF059669) // green
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(3.dp))
            .background(bg.copy(alpha = 0.92f))
            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(3.dp))
            .padding(horizontal = 5.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

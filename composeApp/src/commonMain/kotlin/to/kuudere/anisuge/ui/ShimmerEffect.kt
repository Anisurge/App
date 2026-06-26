package to.kuudere.anisuge.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import to.kuudere.anisuge.theme.AppColors

private const val SHIMMER_DURATION_MS = 1200

@Composable
fun rememberShimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateX by transition.animateFloat(
        initialValue = -300f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(SHIMMER_DURATION_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmerTranslate"
    )
    return Brush.linearGradient(
        colors = listOf(
            AppColors.surfaceVariant.copy(alpha = 0.6f),
            AppColors.surfaceVariant.copy(alpha = 0.2f),
            AppColors.surfaceVariant.copy(alpha = 0.6f),
        ),
        start = Offset(translateX, 0f),
        end = Offset(translateX + 300f, 0f)
    )
}

@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(8.dp),
) {
    val shimmerBrush = rememberShimmerBrush()
    Box(
        modifier = modifier
            .clip(shape)
            .background(shimmerBrush)
    )
}

@Composable
fun ShimmerCard(
    width: Dp = 155.dp,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.width(width)) {
        ShimmerBox(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f),
            shape = RoundedCornerShape(12.dp)
        )
        Spacer(Modifier.height(12.dp))
        ShimmerBox(
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .height(14.dp),
            shape = RoundedCornerShape(4.dp)
        )
        Spacer(Modifier.height(6.dp))
        ShimmerBox(
            modifier = Modifier
                .fillMaxWidth(0.5f)
                .height(10.dp),
            shape = RoundedCornerShape(4.dp)
        )
    }
}

@Composable
fun ShimmerRow(
    cardWidth: Dp = 155.dp,
    cardCount: Int = 6,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
    ) {
        items(cardCount) {
            ShimmerCard(width = cardWidth)
        }
    }
}

@Composable
fun ShimmerContinueWatchingRow(
    modifier: Modifier = Modifier,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
    ) {
        items(5) {
            Column(modifier = Modifier.width(190.dp)) {
                ShimmerBox(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f),
                    shape = RoundedCornerShape(8.dp)
                )
                Spacer(Modifier.height(8.dp))
                ShimmerBox(
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .height(12.dp),
                    shape = RoundedCornerShape(4.dp)
                )
            }
        }
    }
}

@Composable
fun ShimmerHomeContent(
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        ShimmerBox(
            modifier = Modifier
                .fillMaxWidth()
                .height(340.dp),
            shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
        )
        Spacer(Modifier.height(24.dp))
        ShimmerRowHeader()
        Spacer(Modifier.height(12.dp))
        ShimmerRow()
        Spacer(Modifier.height(24.dp))
        ShimmerRowHeader()
        Spacer(Modifier.height(12.dp))
        ShimmerContinueWatchingRow()
        Spacer(Modifier.height(24.dp))
        ShimmerRowHeader()
        Spacer(Modifier.height(12.dp))
        ShimmerRow(cardWidth = 140.dp)
    }
}

@Composable
private fun ShimmerRowHeader(
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ShimmerBox(
            modifier = Modifier
                .width(3.dp)
                .height(20.dp),
            shape = RoundedCornerShape(2.dp)
        )
        ShimmerBox(
            modifier = Modifier
                .width(140.dp)
                .height(18.dp),
            shape = RoundedCornerShape(4.dp)
        )
    }
}

@Composable
fun ShimmerGrid(
    columns: Int = 3,
    rows: Int = 3,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        repeat(rows) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                repeat(columns) {
                    ShimmerCard(
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

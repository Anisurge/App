package to.kuudere.anisuge.screens.games

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import to.kuudere.anisuge.theme.AppColors
import to.kuudere.anisuge.screens.settings.BELI_SYMBOL

private data class WheelSegment(
    val label: String,
    val prize: Int,
    val color: Color,
)

private val SEGMENTS = listOf(
    WheelSegment("Try Again", 0, Color(0xFFE74C3C)),
    WheelSegment("1 Berry", 1, Color(0xFF3498DB)),
    WheelSegment("3 Berries", 3, Color(0xFF2ECC71)),
    WheelSegment("5 Berries", 5, Color(0xFFF39C12)),
    WheelSegment("10 Berries", 10, Color(0xFF9B59B6)),
    WheelSegment("25 Berries", 25, Color(0xFF1ABC9C)),
    WheelSegment("50 Berries", 50, Color(0xFFE67E22)),
    WheelSegment("JACKPOT", 100, Color(0xFFE91E63)),
)

private val SEGMENT_ANGLE = 360f / SEGMENTS.size
private val SEGMENT_HALF = SEGMENT_ANGLE / 2f

enum class GamesTab { Wheel, CoinFlip }

@Composable
fun GamesScreen(
    viewModel: GamesViewModel,
    onBack: () -> Unit,
) {
    val state = viewModel.state
    var selectedTab by remember { mutableStateOf(GamesTab.Wheel) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.background),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
            }
            Text("Games", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            state.status?.let { s ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0x22FFFFFF))
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                ) {
                    Text(
                        "$BELI_SYMBOL${formatInt(s.coins)}",
                        color = Color(0xFFFFD700),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            GamesTab.values().forEach { tab ->
                val isSelected = selectedTab == tab
                val bg = if (isSelected) Color(0xFFFFD700) else Color(0x18FFFFFF)
                val tc = if (isSelected) Color.Black else Color.White.copy(alpha = 0.7f)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(bg)
                        .clickable { selectedTab = tab }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        when (tab) {
                            GamesTab.Wheel -> "Berry Wheel"
                            GamesTab.CoinFlip -> "Coin Flip"
                        },
                        color = tc,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                    )
                }
                if (tab != GamesTab.values().last()) Spacer(Modifier.width(8.dp))
            }
        }

        Spacer(Modifier.height(8.dp))
        when (selectedTab) {
            GamesTab.Wheel -> WheelContent(viewModel, state)
            GamesTab.CoinFlip -> CoinFlipContent(viewModel, state)
        }
    }
}

@Composable
private fun WheelContent(
    viewModel: GamesViewModel,
    state: GamesUiState,
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(8.dp))

        val winningIndex = if (state.wheelResult != null) {
            SEGMENTS.indexOfFirst { it.prize == state.wheelResult!!.prize && it.label == state.wheelResult!!.prizeLabel }
        } else -1

        val targetAngle = if (winningIndex >= 0) {
            val segCenter = winningIndex * SEGMENT_ANGLE + SEGMENT_HALF
            5 * 360f + (360f - segCenter % 360f) % 360f
        } else 0f

        val spinAngle by animateFloatAsState(
            targetValue = if (state.spinning) targetAngle else 0f,
            animationSpec = tween(durationMillis = 2500),
            label = "spin",
        )

        Box(
            modifier = Modifier.size(290.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val cx = w / 2f
                val cy = h / 2f
                val r = minOf(cx, cy) - 4f
                val segSweep = SEGMENT_ANGLE
                val arcSize = Size(r * 2f, r * 2f)
                val arcTopLeft = Offset(cx - r, cy - r)

                // rotation indicator (ring glow)
                drawCircle(
                    Color(0x33FFD700),
                    r + 6f,
                    Offset(cx, cy),
                    style = Stroke(3f),
                )

                rotate(spinAngle, Offset(cx, cy)) {
                    SEGMENTS.forEachIndexed { i, seg ->
                        val startAngle = i * segSweep - 90f
                        drawArc(
                            color = seg.color,
                            startAngle = startAngle,
                            sweepAngle = segSweep,
                            useCenter = true,
                            topLeft = arcTopLeft,
                            size = arcSize,
                            style = Fill,
                        )
                        drawArc(
                            color = Color.White.copy(alpha = 0.3f),
                            startAngle = startAngle,
                            sweepAngle = segSweep,
                            useCenter = true,
                            topLeft = arcTopLeft,
                            size = arcSize,
                            style = Stroke(1.5f),
                        )

                    }
                }

                // center hub
                drawCircle(Color(0xFFFFD700), r * 0.14f, Offset(cx, cy))
                drawCircle(Color.Black, r * 0.11f, Offset(cx, cy))

                // top pointer
                val pointerPath = androidx.compose.ui.graphics.Path().apply {
                    moveTo(cx - 14f, cy - r + 14f)
                    lineTo(cx + 14f, cy - r + 14f)
                    lineTo(cx, cy - r - 16f)
                    close()
                }
                drawPath(pointerPath, Color(0xFFFFD700))
                drawPath(
                    pointerPath,
                    Color.White.copy(alpha = 0.4f),
                    style = Stroke(2f),
                )
            }

            if (state.spinning) {
                CircularProgressIndicator(
                    color = Color(0xFFFFD700),
                    modifier = Modifier.size(36.dp),
                    strokeWidth = 3.dp,
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Result card
        state.wheelResult?.let { result ->
            val bgCard = if (result.prize > 0) Color(0x1A2ECC71) else Color(0x1AE74C3C)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(bgCard)
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        result.prizeLabel,
                        color = if (result.prize > 0) Color(0xFF2ECC71) else Color(0xFFE74C3C),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    if (result.prize > 0) {
                        Text(
                            "+$BELI_SYMBOL${result.prize}",
                            color = Color(0xFFFFD700),
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Text(
                        if (result.freeSpin) "Free spin!" else "Cost: ${BELI_SYMBOL}${result.cost}",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 13.sp,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        state.error?.let { err ->
            Text(err, color = Color(0xFFE74C3C), fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
        }

        // Spin buttons
        state.status?.let { s ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = { viewModel.spinWheel(true) },
                    enabled = !state.spinning && s.canFreeWheel,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFFD700),
                        disabledContainerColor = Color(0x18FFFFFF),
                    ),
                ) {
                    Text(
                        if (s.canFreeWheel) "FREE SPIN" else "USED TODAY",
                        color = if (s.canFreeWheel) Color.Black else Color.Gray,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                    )
                }
                Button(
                    onClick = { viewModel.spinWheel(false) },
                    enabled = !state.spinning && s.coins >= 5,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF9B59B6),
                        disabledContainerColor = Color(0x18FFFFFF),
                    ),
                ) {
                    Text(
                        "SPIN ${BELI_SYMBOL}5",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Stats row
        state.status?.let { s ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                StatBox("Earned", "${BELI_SYMBOL}${formatInt(s.totalEarned)}", Color(0xFF2ECC71))
                StatBox("Lost", "${BELI_SYMBOL}${formatInt(s.totalLost)}", Color(0xFFE74C3C))
                StatBox("Best", "${s.bestStreak}", Color(0xFFFFD700))
            }
            Spacer(Modifier.height(16.dp))
        }

        // Prize table
        Spacer(Modifier.height(16.dp))
        SEGMENTS.forEach { seg ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(seg.color),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(seg.label, color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                }
                Text(
                    if (seg.prize > 0) "${BELI_SYMBOL}${seg.prize}" else "-",
                    color = if (seg.prize > 0) Color(0xFFFFD700) else Color.Gray,
                    fontSize = 13.sp,
                    fontWeight = if (seg.prize > 0) FontWeight.Bold else FontWeight.Normal,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun CoinFlipContent(
    viewModel: GamesViewModel,
    state: GamesUiState,
) {
    var betText by remember { mutableStateOf("10") }
    var selectedChoice by remember { mutableStateOf<String?>(null) }

    val bet = betText.toIntOrNull() ?: 0
    val coins = state.status?.coins ?: 0
    val canAfford = bet in 1..coins

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(16.dp))

        // Heads selection
        val hsColor by animateFloatAsState(
            targetValue = if (selectedChoice == "heads") 1f else 0.3f,
            label = "heads",
        )
        val tsColor by animateFloatAsState(
            targetValue = if (selectedChoice == "tails") 1f else 0.3f,
            label = "tails",
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(100.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0x22FFFFFF))
                    .border(
                        width = if (selectedChoice == "heads") 2.dp else 0.dp,
                        color = Color(0xFFFFD700),
                        shape = RoundedCornerShape(16.dp),
                    )
                    .clickable { selectedChoice = "heads" },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(
                                if (selectedChoice == "heads") Color(0xFFFFD700)
                                else Color(0x44FFFFFF)
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "H",
                            color = if (selectedChoice == "heads") Color.Black else Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "HEADS",
                        color = if (selectedChoice == "heads") Color(0xFFFFD700) else Color.White.copy(alpha = 0.6f),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(100.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0x22FFFFFF))
                    .border(
                        width = if (selectedChoice == "tails") 2.dp else 0.dp,
                        color = Color(0xFFFFD700),
                        shape = RoundedCornerShape(16.dp),
                    )
                    .clickable { selectedChoice = "tails" },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(
                                if (selectedChoice == "tails") Color(0xFFFFD700)
                                else Color(0x44FFFFFF)
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "T",
                            color = if (selectedChoice == "tails") Color.Black else Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "TAILS",
                        color = if (selectedChoice == "tails") Color(0xFFFFD700) else Color.White.copy(alpha = 0.6f),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Result display
        state.coinFlipResult?.let { result ->
            val resultBg = if (result.won) Color(0x1A2ECC71) else Color(0x1AE74C3C)
            val resultTxt = if (result.won) Color(0xFF2ECC71) else Color(0xFFE74C3C)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(resultBg)
                    .padding(20.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("FLIP: ${result.result.uppercase()}", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(
                        if (result.won) "WON ${BELI_SYMBOL}${result.payout}" else "LOST ${BELI_SYMBOL}${result.bet}",
                        color = resultTxt,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "Balance: ${BELI_SYMBOL}${formatInt(result.coins)}",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 14.sp,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = {
                    viewModel.clearCoinFlipResult()
                    selectedChoice = null
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(44.dp),
            ) {
                Text("PLAY AGAIN", color = Color.White, fontWeight = FontWeight.Bold)
            }
            return
        }

        // Bet amount
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = { betText = (bet - 5).coerceAtLeast(1).toString() },
                enabled = bet > 1,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0x22FFFFFF)),
                    contentAlignment = Alignment.Center,
                ) { Text("-", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold) }
            }
            Spacer(Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .width(100.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x15FFFFFF))
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("${BELI_SYMBOL}$bet", color = Color(0xFFFFD700), fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            IconButton(
                onClick = { betText = (bet + 5).coerceAtMost(coins).toString() },
                enabled = bet + 5 <= coins,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0x22FFFFFF)),
                    contentAlignment = Alignment.Center,
                ) { Text("+", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold) }
            }
        }

        Spacer(Modifier.height(16.dp))

        // Bet multiplier info
        Text(
            "Win pays 1.9x (${BELI_SYMBOL}${(bet * 1.9).toInt()})",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 13.sp,
        )

        Spacer(Modifier.height(16.dp))

        state.error?.let { err ->
            Text(err, color = Color(0xFFE74C3C), fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
        }

        // Flip button
        Button(
            onClick = {
                viewModel.flipCoin(bet, selectedChoice ?: return@Button)
            },
            enabled = !state.flipping && selectedChoice != null && canAfford,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFE74C3C),
                disabledContainerColor = Color(0x18FFFFFF),
            ),
        ) {
            if (state.flipping) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            } else {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(
                if (state.flipping) "FLIPPING..." else "FLIP COIN",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun StatBox(label: String, value: String, color: Color) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x0FFFFFFF))
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Text(value, color = color, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(label, color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
    }
}

private fun formatInt(amount: Int): String {
    val s = amount.toString()
    val b = StringBuilder()
    var count = 0
    for (i in s.lastIndex downTo 0) {
        if (count > 0 && count % 3 == 0) b.insert(0, ',')
        b.insert(0, s[i])
        count++
    }
    return b.toString()
}

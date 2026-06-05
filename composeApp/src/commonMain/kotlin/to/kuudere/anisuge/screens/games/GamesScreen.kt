package to.kuudere.anisuge.screens.games

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import to.kuudere.anisuge.screens.settings.BELI_SYMBOL
import to.kuudere.anisuge.theme.AppColors
import to.kuudere.anisuge.utils.formatFloat
import kotlin.math.min

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

enum class GamesTab { Wheel, CoinFlip, Mines }

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
            GamesTab.entries.forEach { tab ->
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
                            GamesTab.Wheel -> "Wheel"
                            GamesTab.CoinFlip -> "Coin"
                            GamesTab.Mines -> "Mines"
                        },
                        color = tc,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                    )
                }
                if (tab != GamesTab.entries.last()) Spacer(Modifier.width(6.dp))
            }
        }

        Spacer(Modifier.height(8.dp))
        when (selectedTab) {
            GamesTab.Wheel -> WheelContent(viewModel, state)
            GamesTab.CoinFlip -> CoinFlipContent(viewModel, state)
            GamesTab.Mines -> MinesContent(viewModel, state)
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

                drawCircle(Color(0xFFFFD700), r * 0.14f, Offset(cx, cy))
                drawCircle(Color.Black, r * 0.11f, Offset(cx, cy))

                val pointerPath = Path().apply {
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
    var previousResult by remember { mutableStateOf<String?>(null) }

    val bet = betText.toIntOrNull() ?: 0
    val coins = state.status?.coins ?: 0
    val canAfford = bet in 1..coins

    val isFlipping = state.flipping
    val hasResult = state.coinFlipResult != null

    val flipRotation by animateFloatAsState(
        targetValue = if (isFlipping) 720f else if (hasResult && previousResult != null) 720f else 0f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 200f),
        label = "flip",
    )

    LaunchedEffect(state.coinFlipResult) {
        if (state.coinFlipResult != null) {
            previousResult = state.coinFlipResult!!.result
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            listOf("heads" to "H", "tails" to "T").forEach { (value, label) ->
                val isSelected = selectedChoice == value
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(100.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0x22FFFFFF))
                        .border(
                            width = if (isSelected) 2.dp else 0.dp,
                            color = Color(0xFFFFD700),
                            shape = RoundedCornerShape(16.dp),
                        )
                        .clickable(enabled = !hasResult) { selectedChoice = value },
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) Color(0xFFFFD700)
                                    else Color(0x44FFFFFF)
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                label,
                                color = if (isSelected) Color.Black else Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            value.uppercase(),
                            color = if (isSelected) Color(0xFFFFD700) else Color.White.copy(alpha = 0.6f),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // 3D coin
        val showResult = hasResult && !isFlipping
        val isWin = state.coinFlipResult?.won == true
        val resultText = previousResult?.takeIf { hasResult } ?: ""

        Box(
            modifier = Modifier
                .size(120.dp)
                .graphicsLayer { rotationY = flipRotation },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(
                        when {
                            showResult && isWin -> Color(0xFF2ECC71)
                            showResult && !isWin -> Color(0xFFE74C3C)
                            else -> Color(0xFFFFD700)
                        }
                    )
                    .border(3.dp, Color.White.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (showResult) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            if (resultText == "heads") "H" else "T",
                            color = Color.White,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            if (isWin) "WIN" else "LOSE",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                } else {
                    Text(
                        if (selectedChoice == "heads") "H" else if (selectedChoice == "tails") "T" else "?",
                        color = Color.Black,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        state.coinFlipResult?.let { result ->
            Text(
                if (result.won) "WON ${BELI_SYMBOL}${result.payout}" else "LOST ${BELI_SYMBOL}${result.bet}",
                color = if (result.won) Color(0xFF2ECC71) else Color(0xFFE74C3C),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    viewModel.clearCoinFlipResult()
                    selectedChoice = null
                    previousResult = null
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(44.dp),
            ) {
                Text("PLAY AGAIN", color = Color.White, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Balance: ${BELI_SYMBOL}${formatInt(result.coins)}",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 14.sp,
            )
            return
        }

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

        Spacer(Modifier.height(12.dp))

        Text(
            "Win pays 1.7x (${BELI_SYMBOL}${(bet * 1.7).toInt()})",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 13.sp,
        )

        Spacer(Modifier.height(12.dp))

        state.error?.let { err ->
            Text(err, color = Color(0xFFE74C3C), fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
        }

        Button(
            onClick = {
                viewModel.flipCoin(bet, selectedChoice ?: return@Button)
            },
            enabled = !isFlipping && selectedChoice != null && canAfford,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFE74C3C),
                disabledContainerColor = Color(0x18FFFFFF),
            ),
        ) {
            if (isFlipping) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text(
                if (isFlipping) "FLIPPING..." else "FLIP COIN",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun MinesContent(
    viewModel: GamesViewModel,
    state: GamesUiState,
) {
    val mines = state.minesState
    var betText by remember { mutableStateOf("10") }
    var mineCountText by remember { mutableStateOf("3") }

    val bet = betText.toIntOrNull() ?: 0
    val mineCount = mineCountText.toIntOrNull() ?: 3
    val coins = state.status?.coins ?: 0
    val canStart = bet in 1..min(coins, 5000) && mineCount in 1..10 && !state.creatingMines

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(12.dp))

        if (mines == null || mines.state == "won" || mines.state == "lost" || mines.state == "cashed_out") {
            if (mines != null) {
                val resultColor = when (mines.state) {
                    "won" -> Color(0xFF2ECC71)
                    "cashed_out" -> Color(0xFF2ECC71)
                    else -> Color(0xFFE74C3C)
                }
                val resultText = when (mines.state) {
                    "won" -> "HIT A MINE!"
                    "cashed_out" -> "CASHED OUT!"
                    "lost" -> "GAME OVER"
                    else -> ""
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (mines.state == "lost") Color(0x1AE74C3C) else Color(0x1A2ECC71))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(resultText, color = resultColor, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        if (mines.state == "cashed_out") {
                            Text(
                                "+${BELI_SYMBOL}${state.status?.coins?.minus(coins - bet) ?: 0}",
                                color = Color(0xFFFFD700),
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { viewModel.clearMines() },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                ) {
                    Text("PLAY AGAIN", color = Color.White, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(12.dp))
            }

            // Bet & mine count selectors
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Bet", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { betText = (bet - 5).coerceAtLeast(1).toString() },
                            enabled = bet > 1,
                        ) {
                            Box(
                                Modifier.size(36.dp).clip(CircleShape).background(Color(0x22FFFFFF)),
                                contentAlignment = Alignment.Center,
                            ) { Text("-", color = Color.White, fontSize = 18.sp) }
                        }
                        Spacer(Modifier.width(8.dp))
                        Box(
                            Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(Color(0x15FFFFFF)).padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("${BELI_SYMBOL}$bet", color = Color(0xFFFFD700), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(8.dp))
                        IconButton(
                            onClick = { betText = (bet + 5).coerceAtMost(min(coins, 5000)).toString() },
                            enabled = bet + 5 <= min(coins, 5000),
                        ) {
                            Box(
                                Modifier.size(36.dp).clip(CircleShape).background(Color(0x22FFFFFF)),
                                contentAlignment = Alignment.Center,
                            ) { Text("+", color = Color.White, fontSize = 18.sp) }
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text("Mines", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { mineCountText = (mineCount - 1).coerceAtLeast(1).toString() },
                            enabled = mineCount > 1,
                        ) {
                            Box(
                                Modifier.size(36.dp).clip(CircleShape).background(Color(0x22FFFFFF)),
                                contentAlignment = Alignment.Center,
                            ) { Text("-", color = Color.White, fontSize = 18.sp) }
                        }
                        Spacer(Modifier.width(8.dp))
                        Box(
                            Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(Color(0x15FFFFFF)).padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("$mineCount", color = Color(0xFF9B59B6), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(8.dp))
                        IconButton(
                            onClick = { mineCountText = (mineCount + 1).coerceAtMost(10).toString() },
                            enabled = mineCount < 10,
                        ) {
                            Box(
                                Modifier.size(36.dp).clip(CircleShape).background(Color(0x22FFFFFF)),
                                contentAlignment = Alignment.Center,
                            ) { Text("+", color = Color.White, fontSize = 18.sp) }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            state.error?.let { err ->
                Text(err, color = Color(0xFFE74C3C), fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
            }

            Button(
                onClick = {
                    viewModel.createMines(bet, mineCount)
                },
                enabled = canStart,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF9B59B6),
                    disabledContainerColor = Color(0x18FFFFFF),
                ),
            ) {
                if (state.creatingMines) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    if (state.creatingMines) "STARTING..." else "START GAME",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            return
        }

        // Playing state - show grid
        val gridSize = mines.gridSize
        val gridTiles = gridSize * gridSize

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0x0FFFFFFF))
                .padding(8.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                for (row in 0 until gridSize) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        for (col in 0 until gridSize) {
                            val index = row * gridSize + col
                            val isRevealed = index in mines.revealedTiles
                            val isMine = state.minesRevealResult?.let {
                                it.tileIndex == index && it.isMine
                            } ?: false

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        when {
                                            isRevealed && isMine -> Color(0xFFE74C3C)
                                            isRevealed && !isMine -> Color(0xFF2ECC71)
                                            else -> Color(0x22FFFFFF)
                                        }
                                    )
                                    .clickable(enabled = mines.state == "playing" && !isRevealed && !state.revealingMine) {
                                        viewModel.revealMinesTile(index)
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (isRevealed && isMine) {
                                    Text("💣", fontSize = 18.sp)
                                } else if (isRevealed && !isMine) {
                                    Icon(
                                        Icons.Filled.Star,
                                        contentDescription = null,
                                        tint = Color(0xFFFFD700),
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Bet: ${BELI_SYMBOL}${mines.bet}",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 14.sp,
            )
            Text(
                "Multiplier: ${formatFloat(mines.currentMultiplier, 2)}x",
                color = Color(0xFFFFD700),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(Modifier.height(12.dp))

        state.error?.let { err ->
            Text(err, color = Color(0xFFE74C3C), fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
        }

        Button(
            onClick = { viewModel.cashoutMines() },
            enabled = mines.state == "playing" && mines.revealedTiles.isNotEmpty() && !state.cashingOutMines,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF2ECC71),
                disabledContainerColor = Color(0x18FFFFFF),
            ),
        ) {
            if (state.cashingOutMines) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text(
                if (state.cashingOutMines) "CASHING OUT..." else "CASH OUT",
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

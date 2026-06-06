package to.kuudere.anisuge.screens.games

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import to.kuudere.anisuge.data.models.BffAnimeGameItem
import to.kuudere.anisuge.data.models.BffCoinFlipResponse
import to.kuudere.anisuge.platform.PlatformBackHandler
import to.kuudere.anisuge.screens.settings.BELI_SYMBOL
import to.kuudere.anisuge.theme.AppColors
import kotlin.math.min

private enum class GameKind(
    val title: String,
    val subtitle: String,
    val accent: Color,
) {
    Wheel("Berry Wheel", "Daily free spin and paid prize spins", Color(0xFFFFC107)),
    Coin("Coin Flip", "Pick a side, wager Berries", Color(0xFFE57373)),
    HigherLower("Anime Higher/Lower", "Compare catalog scores", Color(0xFF64B5F6)),
    Guess("Anime Guess", "Timed blur and pixel poster rounds", Color(0xFFBA68C8)),
    Mines("Mines", "Reveal safe tiles and cash out", Color(0xFF4DB6AC)),
    Crash("Crash", "Cash out before the multiplier breaks", Color(0xFFFF8A65)),
    Trivia("Anime Trivia", "Catalog questions with rewards", Color(0xFFAED581)),
}

private val visibleGames = listOf(
    GameKind.HigherLower,
    GameKind.Guess,
    GameKind.Mines,
    GameKind.Trivia,
)

private data class WheelSegmentUi(val label: String, val prize: Int, val color: Color)

private val fallbackWheelSegments = listOf(
    WheelSegmentUi("Try Again", 0, Color(0xFFE53935)),
    WheelSegmentUi("1 Berry", 1, Color(0xFF1E88E5)),
    WheelSegmentUi("3 Berries", 3, Color(0xFF43A047)),
    WheelSegmentUi("5 Berries", 5, Color(0xFFFB8C00)),
    WheelSegmentUi("10 Berries", 10, Color(0xFF8E24AA)),
    WheelSegmentUi("25 Berries", 25, Color(0xFF00ACC1)),
    WheelSegmentUi("50 Berries", 50, Color(0xFFF4511E)),
    WheelSegmentUi("Jackpot", 100, Color(0xFFD81B60)),
)

@Composable
fun GamesScreen(
    viewModel: GamesViewModel,
    onBack: () -> Unit,
    onBuyBerries: () -> Unit = {},
) {
    val state = viewModel.state
    var selectedGame by remember { mutableStateOf<GameKind?>(null) }
    PlatformBackHandler(enabled = selectedGame != null) {
        selectedGame = null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.background),
    ) {
        GamesHeader(
            state = state,
            selectedGame = selectedGame,
            onBack = {
                if (selectedGame == null) onBack() else selectedGame = null
            },
            onRefresh = { viewModel.loadStatus() },
            onBuyBerries = onBuyBerries,
        )

        state.error?.let { error ->
            MessageBand(
                text = error,
                color = Color(0xFFFF6B6B),
                onDismiss = viewModel::clearError,
            )
        }
        state.toast?.let { toast ->
            MessageBand(
                text = toast,
                color = Color(0xFFFFD166),
                onDismiss = viewModel::clearToast,
            )
        }

        if (state.loading && state.status == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFFFFC107))
            }
            return@Column
        }

        if (selectedGame == null) {
            GamesLobby(
                state = state,
                onSelect = { selectedGame = it },
                onBuyBerries = onBuyBerries,
            )
        } else {
            GameDetail(
                game = selectedGame!!,
                viewModel = viewModel,
                state = state,
            )
        }
    }
}

@Composable
private fun GamesHeader(
    state: GamesUiState,
    selectedGame: GameKind?,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onBuyBerries: () -> Unit,
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
        Column {
            Text(
                selectedGame?.title ?: "Games",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                selectedGame?.subtitle ?: "Win and wager cosmetic Berries",
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.weight(1f))
        BalancePill(state.status?.coins ?: 0)
        IconButton(onClick = onRefresh) {
            Icon(Icons.Default.PlayArrow, "Refresh", tint = Color.White.copy(alpha = 0.8f))
        }
    }
}

@Composable
private fun BalancePill(coins: Int) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0x22FFFFFF))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(
            "$BELI_SYMBOL${formatBerries(coins)}",
            color = Color(0xFFFFD166),
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
        )
    }
}

@Composable
private fun MessageBand(
    text: String,
    color: Color,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = color, fontSize = 13.sp, modifier = Modifier.weight(1f))
        TextButton(onClick = onDismiss) { Text("OK", color = color) }
    }
}

@Composable
private fun GamesLobby(
    state: GamesUiState,
    onSelect: (GameKind) -> Unit,
    onBuyBerries: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        StatsStrip(state)
        BuyBerriesCard(onBuyBerries)
        ActiveSessionsStrip(state)
        ResponsiveGameGrid(onSelect)
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun StatsStrip(state: GamesUiState) {
    val status = state.status
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatTile("Earned", "${BELI_SYMBOL}${formatBerries(status?.totalEarned ?: 0)}", Color(0xFF81C784), Modifier.weight(1f))
        StatTile("Lost", "${BELI_SYMBOL}${formatBerries(status?.totalLost ?: 0)}", Color(0xFFE57373), Modifier.weight(1f))
        StatTile("Best", "${status?.bestStreak ?: 0}", Color(0xFFFFD166), Modifier.weight(1f))
    }
}

@Composable
private fun BuyBerriesCard(onBuyBerries: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onBuyBerries),
        color = Color(0x22FFD166),
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier
                .border(1.dp, Color(0xFFFFD166).copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Need more Berries?", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("Open the official checkout tied to your Anisurge account.", color = Color.White.copy(alpha = 0.62f), fontSize = 12.sp)
            }
            Button(
                onClick = onBuyBerries,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD166)),
            ) {
                Text("Buy Berries", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.height(72.dp),
        color = Color(0x14FFFFFF),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(label, color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp)
            Text(value, color = color, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable
private fun ActiveSessionsStrip(state: GamesUiState) {
    val active = state.status?.activeSessions.orEmpty()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0x10FFFFFF),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                if (active.isEmpty()) "No active rounds" else "${active.size} active server round${if (active.size == 1) "" else "s"}",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
            )
            Text(
                "Skill rounds pay Berries; paused luck games are hidden while they are tuned.",
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun ResponsiveGameGrid(onSelect: (GameKind) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = if (maxWidth < 620.dp) 2 else 3
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            visibleGames.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    row.forEach { game ->
                        GameCard(game = game, modifier = Modifier.weight(1f), onClick = { onSelect(game) })
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun GameCard(game: GameKind, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier
            .height(142.dp)
            .clickable(onClick = onClick),
        color = Color(0x14FFFFFF),
        shape = RoundedCornerShape(8.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(1.dp, game.accent.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                .padding(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(game.accent.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(game.title.take(1), color = game.accent, fontWeight = FontWeight.Black)
            }
            Column(
                modifier = Modifier.align(Alignment.BottomStart),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(game.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 2)
                Text(game.subtitle, color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp, maxLines = 2)
            }
        }
    }
}

@Composable
private fun GameDetail(
    game: GameKind,
    viewModel: GamesViewModel,
    state: GamesUiState,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        when (game) {
            GameKind.Wheel -> WheelGame(viewModel, state)
            GameKind.Coin -> CoinFlipGame(viewModel, state)
            GameKind.HigherLower -> HigherLowerGame(viewModel, state)
            GameKind.Guess -> AnimeGuessGame(viewModel, state)
            GameKind.Mines -> MinesGame(viewModel, state)
            GameKind.Crash -> CrashGame(viewModel, state)
            GameKind.Trivia -> TriviaGame(viewModel, state)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun WheelGame(viewModel: GamesViewModel, state: GamesUiState) {
    val status = state.status
    val segments = status?.config?.wheel?.segments
        ?.takeIf { it.isNotEmpty() }
        ?.mapIndexed { index, segment ->
            fallbackWheelSegments[index % fallbackWheelSegments.size].copy(label = segment.label, prize = segment.prize)
        }
        ?: fallbackWheelSegments
    val busy = state.busyAction == "wheel"
    var spinRotation by remember { mutableStateOf(0f) }
    var lastSpinKey by remember { mutableStateOf<String?>(null) }
    var revealedSpinKey by remember { mutableStateOf<String?>(null) }
    val resultKey = state.wheelResult?.let { "${it.prize}:${it.prizeLabel}:${it.coins}" }
    LaunchedEffect(busy, resultKey) {
        if (busy) {
            revealedSpinKey = null
            spinRotation += 1260f
        } else if (resultKey != null && resultKey != lastSpinKey) {
            revealedSpinKey = null
            val segmentIndex = segments.indexOfFirst { it.prize == state.wheelResult.prize }
                .takeIf { it >= 0 }
                ?: 0
            val sweep = 360f / segments.size
            val targetInsideSegment = segmentIndex * sweep + sweep / 2f
            spinRotation += 1080f + (360f - targetInsideSegment)
            lastSpinKey = resultKey
        }
    }
    LaunchedEffect(resultKey) {
        if (resultKey == null) {
            revealedSpinKey = null
        } else {
            delay(2300)
            revealedSpinKey = resultKey
        }
    }
    val rotation by animateFloatAsState(
        targetValue = spinRotation,
        animationSpec = tween(if (busy) 1200 else 2200),
        label = "wheel",
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(286.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val r = min(size.width, size.height) / 2f - 8f
                val c = Offset(size.width / 2f, size.height / 2f)
                val sweep = 360f / segments.size
                rotate(rotation, c) {
                    segments.forEachIndexed { index, segment ->
                        drawArc(
                            color = segment.color,
                            startAngle = -90f + index * sweep,
                            sweepAngle = sweep,
                            useCenter = true,
                            topLeft = Offset(c.x - r, c.y - r),
                            size = Size(r * 2, r * 2),
                            style = Fill,
                        )
                        drawArc(
                            color = Color.White.copy(alpha = 0.2f),
                            startAngle = -90f + index * sweep,
                            sweepAngle = sweep,
                            useCenter = true,
                            topLeft = Offset(c.x - r, c.y - r),
                            size = Size(r * 2, r * 2),
                            style = Stroke(1.5f),
                        )
                    }
                }
                drawCircle(Color(0xFFFFD166), r * 0.14f, c)
                val pointer = Path().apply {
                    moveTo(c.x - 15f, c.y - r + 18f)
                    lineTo(c.x + 15f, c.y - r + 18f)
                    lineTo(c.x, c.y - r - 18f)
                    close()
                }
                drawPath(pointer, Color.White)
            }
            if (busy) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(38.dp), strokeWidth = 3.dp)
        }

        state.wheelResult?.takeIf { revealedSpinKey == resultKey }?.let {
            ResultPanel(
                title = it.prizeLabel,
                body = if (it.prize > 0) "+$BELI_SYMBOL${it.prize}" else "Spin again tomorrow or spend Berries.",
                positive = it.prize > 0,
            )
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = { viewModel.spinWheel(true) },
                enabled = !busy && status?.canFreeWheel == true,
                modifier = Modifier.weight(1f).height(50.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD166)),
            ) {
                Text(if (status?.canFreeWheel == true) "Free Spin" else "Used Today", color = Color.Black, fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = { viewModel.spinWheel(false) },
                enabled = !busy && (status?.coins ?: 0) >= (status?.config?.wheel?.paidSpinCost ?: 5),
                modifier = Modifier.weight(1f).height(50.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFBA68C8)),
            ) {
                Text("Spin ${BELI_SYMBOL}${status?.config?.wheel?.paidSpinCost ?: 5}", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }

        segments.forEach { segment ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(segment.color))
                    Spacer(Modifier.width(8.dp))
                    Text(segment.label, color = Color.White.copy(alpha = 0.72f), fontSize = 13.sp)
                }
                Text(if (segment.prize > 0) "$BELI_SYMBOL${segment.prize}" else "-", color = Color(0xFFFFD166), fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun CoinFlipGame(viewModel: GamesViewModel, state: GamesUiState) {
    var bet by remember { mutableStateOf(10) }
    var choice by remember { mutableStateOf("heads") }
    val coins = state.status?.coins ?: 0
    val maxBet = min(coins, state.status?.config?.coinFlip?.maxBet ?: 5000).coerceAtLeast(1)
    val busy = state.busyAction == "coin"
    var revealedCoinKey by remember { mutableStateOf<String?>(null) }
    val coinResultKey = state.coinFlipResult?.let { "${it.result}:${it.won}:${it.coins}" }
    LaunchedEffect(busy, coinResultKey) {
        if (busy || coinResultKey == null) {
            revealedCoinKey = null
        } else {
            delay(1400)
            revealedCoinKey = coinResultKey
        }
    }
    val visibleCoinResult = state.coinFlipResult?.takeIf { revealedCoinKey == coinResultKey }
    bet = bet.coerceIn(1, maxBet)

    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ChoiceButton("Heads", choice == "heads", Modifier.weight(1f)) { choice = "heads" }
            ChoiceButton("Tails", choice == "tails", Modifier.weight(1f)) { choice = "tails" }
        }

        CoinFace(result = visibleCoinResult, spinResult = state.coinFlipResult, choice = choice, busy = busy)
        BetStepper(bet = bet, maxBet = maxBet, onChange = { bet = it })

        Text(
            "Win pays x${state.status?.config?.coinFlip?.payoutMultiplier ?: 1.7} (${BELI_SYMBOL}${(bet * (state.status?.config?.coinFlip?.payoutMultiplier ?: 1.7)).toInt()})",
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 13.sp,
        )

        Button(
            onClick = { viewModel.flipCoin(bet, choice) },
            enabled = !busy && coins >= bet,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE57373)),
        ) {
            if (busy) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            else Text("Flip Coin", fontWeight = FontWeight.Bold)
        }

        visibleCoinResult?.let {
            ResultPanel(
                title = if (it.won) "You won" else "You lost",
                body = "Result: ${it.result.uppercase()}  Payout: ${BELI_SYMBOL}${it.payout}",
                positive = it.won,
            )
        }
    }
}

@Composable
private fun CoinFace(result: BffCoinFlipResponse?, spinResult: BffCoinFlipResponse?, choice: String, busy: Boolean) {
    var coinTurns by remember { mutableStateOf(0f) }
    var lastResultKey by remember { mutableStateOf<String?>(null) }
    val resultKey = spinResult?.let { "${it.result}:${it.won}:${it.coins}" }
    LaunchedEffect(busy, resultKey) {
        if (busy) {
            coinTurns += 900f
        } else if (resultKey != null && resultKey != lastResultKey) {
            val finalSide = if (spinResult.result == "tails") 180f else 0f
            coinTurns = (coinTurns + 1080f).let { base ->
                base - (base % 360f) + finalSide
            }
            lastResultKey = resultKey
        }
    }
    val rotation by animateFloatAsState(
        targetValue = coinTurns,
        animationSpec = tween(if (busy) 900 else 1300),
        label = "coin",
    )
    Box(
        modifier = Modifier
            .size(132.dp)
            .graphicsLayer { rotationY = rotation }
            .clip(CircleShape)
            .background(if (result?.won == true) Color(0xFF66BB6A) else if (result != null) Color(0xFFE57373) else Color(0xFFFFD166))
            .border(4.dp, Color.White.copy(alpha = 0.22f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                when (result?.result ?: choice) {
                    "heads" -> "H"
                    "tails" -> "T"
                    else -> "?"
                },
                color = if (result == null) Color.Black else Color.White,
                fontWeight = FontWeight.Black,
                fontSize = 42.sp,
            )
            Text(
                result?.let { if (it.won) "WIN" else "LOSE" } ?: choice.uppercase(),
                color = if (result == null) Color.Black.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.8f),
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun MinesGame(viewModel: GamesViewModel, state: GamesUiState) {
    var bet by remember { mutableStateOf(10) }
    val coins = state.status?.coins ?: 0
    val maxBet = min(coins, state.status?.config?.mines?.maxBet ?: 5000).coerceAtLeast(1)
    val game = state.minesState
    val busy = state.busyAction?.startsWith("mines") == true
    bet = bet.coerceIn(1, maxBet)

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (game == null || game.state != "playing") {
            if (game != null) {
                ResultPanel(
                    title = when (game.state) {
                        "lost" -> "Mine hit"
                        "won" -> "Board cleared"
                        "cashed_out" -> "Cashed out"
                        else -> "Round ended"
                    },
                    body = state.minesCashoutResult?.let { "Won ${BELI_SYMBOL}${it.winAmount} at x${formatMultiplier(it.multiplier)}" }
                        ?: state.minesRevealResult?.let { "Payout ${BELI_SYMBOL}${it.winAmount}" }
                        ?: "Start a fresh board.",
                    positive = game.state != "lost",
                )
                OutlinedButton(onClick = viewModel::resetMines, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
                    Text("New Mines Round", color = Color.White)
                }
            }
            BetStepper(bet = bet, maxBet = maxBet, onChange = { bet = it })
            RiskInfoRow("10 bombs", "40% of the board", "Fixed house odds")
            Button(
                onClick = { viewModel.createMines(bet) },
                enabled = !busy && coins >= bet,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GameKind.Mines.accent),
            ) {
                Text("Start Mines", fontWeight = FontWeight.Bold, color = Color.Black)
            }
        } else {
            RoundInfoRow(
                left = "Bet ${BELI_SYMBOL}${game.bet}",
                center = "x${formatMultiplier(game.currentMultiplier)}",
                right = "Next x${formatMultiplier(game.nextMultiplier)}",
            )
            MinesBoard(game = game, busy = busy, onReveal = viewModel::revealMinesTile)
            Button(
                onClick = viewModel::cashoutMines,
                enabled = !busy && game.revealedTiles.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD166)),
            ) {
                Text("Cash Out ${BELI_SYMBOL}${(game.bet * game.currentMultiplier).toInt()}", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun MinesBoard(game: MinesGameState, busy: Boolean, onReveal: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
        repeat(game.gridSize) { y ->
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
                repeat(game.gridSize) { x ->
                    val index = y * game.gridSize + x
                    val revealed = index in game.revealedTiles
                    val mine = index in game.mineTiles
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                when {
                                    mine -> Color(0xFFE57373)
                                    revealed -> Color(0xFF4DB6AC)
                                    else -> Color(0x18FFFFFF)
                                },
                            )
                            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                            .clickable(enabled = !busy && game.state == "playing" && !revealed) { onReveal(index) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            when {
                                mine -> "X"
                                revealed -> "+"
                                else -> ""
                            },
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CrashGame(viewModel: GamesViewModel, state: GamesUiState) {
    var bet by remember { mutableStateOf(10) }
    val coins = state.status?.coins ?: 0
    val maxBet = min(coins, state.status?.config?.crash?.maxBet ?: 5000).coerceAtLeast(1)
    val game = state.crashState
    val busy = state.busyAction?.startsWith("crash") == true
    bet = bet.coerceIn(1, maxBet)

    if (game == null || game.state != "playing") {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            state.crashResult?.let {
                ResultPanel(
                    title = if (it.crashed) "Crashed" else "Cashed out",
                    body = if (it.crashed) "Crash point x${formatMultiplier(it.crashPoint)}" else "Payout ${BELI_SYMBOL}${it.payout} at x${formatMultiplier(it.multiplier)}",
                    positive = !it.crashed,
                )
                OutlinedButton(onClick = viewModel::resetCrash, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
                    Text("New Crash Round", color = Color.White)
                }
            }
            BetStepper(bet = bet, maxBet = maxBet, onChange = { bet = it })
            Button(
                onClick = { viewModel.startCrash(bet) },
                enabled = !busy && coins >= bet,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GameKind.Crash.accent),
            ) {
                Text("Start Crash", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    } else {
        var now by remember(game.gameId) { mutableLongStateOf(Clock.System.now().toEpochMilliseconds()) }
        LaunchedEffect(game.gameId, game.state) {
            while (game.state == "playing") {
                now = Clock.System.now().toEpochMilliseconds()
                delay(80)
            }
        }
        val multiplier = crashMultiplier(game.startedAt, game.growthPerSecond, now)
        val progress = ((multiplier - 1.0) / 10.0).toFloat().coerceIn(0f, 1f)
        Column(verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x12FFFFFF))
                    .border(1.dp, GameKind.Crash.accent.copy(alpha = 0.35f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("x${formatMultiplier(multiplier)}", color = GameKind.Crash.accent, fontSize = 50.sp, fontWeight = FontWeight.Black)
                    Text("Server decides the crash point", color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp)
                }
            }
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(), color = GameKind.Crash.accent, trackColor = Color.White.copy(alpha = 0.12f))
            Button(
                onClick = viewModel::cashoutCrash,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD166)),
            ) {
                Text("Cash Out ${BELI_SYMBOL}${(game.bet * multiplier).toInt()}", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun HigherLowerGame(viewModel: GamesViewModel, state: GamesUiState) {
    val game = state.higherLowerState
    val busy = state.busyAction?.startsWith("higher") == true
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (game == null || game.state != "playing") {
            state.higherLowerResult?.let {
                ResultPanel(
                    title = if (it.correct) "Correct" else "Wrong",
                    body = "Streak ${it.streak}. Reward ${BELI_SYMBOL}${it.reward}.",
                    positive = it.correct,
                )
                OutlinedButton(onClick = viewModel::resetHigherLower, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
                    Text("New Higher/Lower Round", color = Color.White)
                }
            }
            Button(
                onClick = viewModel::startHigherLower,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GameKind.HigherLower.accent),
            ) {
                Text("Start Round (${BELI_SYMBOL}${state.status?.config?.skillGames?.higherLowerEntryCost ?: 2})", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AnimeScoreCard(game.current, showScore = true, modifier = Modifier.weight(1f))
                AnimeScoreCard(game.next, showScore = state.higherLowerResult?.correct == false, modifier = Modifier.weight(1f))
            }
            Text("Is the next anime rated higher or lower?", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            RoundInfoRow("Streak ${game.streak}", "Entry ${BELI_SYMBOL}${state.status?.config?.skillGames?.higherLowerEntryCost ?: 2}", "Reward scales")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { viewModel.answerHigherLower("higher") },
                    enabled = !busy,
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF81C784)),
                ) { Text("Higher", color = Color.Black, fontWeight = FontWeight.Bold) }
                Button(
                    onClick = { viewModel.answerHigherLower("lower") },
                    enabled = !busy,
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF8A65)),
                ) { Text("Lower", color = Color.Black, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun AnimeScoreCard(anime: BffAnimeGameItem, showScore: Boolean, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.height(258.dp), color = Color(0x14FFFFFF), shape = RoundedCornerShape(8.dp)) {
        Column {
            AsyncImage(
                model = anime.imageUrl,
                contentDescription = anime.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(180.dp),
            )
            Column(Modifier.padding(9.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(anime.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(if (showScore) "Score ${anime.score}%" else "Score ??", color = Color(0xFFFFD166), fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun AnimeGuessGame(viewModel: GamesViewModel, state: GamesUiState) {
    val game = state.animeGuessState
    val result = state.animeGuessResult
    val busy = state.busyAction?.startsWith("guess") == true
    var typedAnswer by remember(game?.gameId) { mutableStateOf("") }
    var mode by remember { mutableStateOf("blur") }
    var guessSecondsLeft by remember(game?.gameId) { mutableStateOf(6) }

    LaunchedEffect(game?.gameId, game?.state) {
        val activeGame = game ?: return@LaunchedEffect
        if (activeGame.state != "playing") return@LaunchedEffect
        guessSecondsLeft = 6
        while (guessSecondsLeft > 0 && activeGame.state == "playing") {
            delay(1000)
            guessSecondsLeft -= 1
        }
        if (guessSecondsLeft <= 0 && activeGame.state == "playing") {
            viewModel.answerAnimeGuess("")
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (game == null || game.state != "playing") {
            result?.let {
                ResultPanel(
                    title = if (it.correct) "Correct" else "Revealed",
                    body = "${it.answer.title}  Reward ${BELI_SYMBOL}${it.reward}",
                    positive = it.correct,
                )
                OutlinedButton(onClick = viewModel::resetAnimeGuess, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
                    Text("New Guess Round", color = Color.White)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("blur", "pixel").forEach { item ->
                    ChoiceButton(item.replaceFirstChar { it.uppercase() }, mode == item, Modifier.weight(1f)) { mode = item }
                }
            }
            Button(
                onClick = { viewModel.startAnimeGuess(mode) },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GameKind.Guess.accent),
            ) {
                Text("Start Guess (${BELI_SYMBOL}${state.status?.config?.skillGames?.animeGuessEntryCost ?: 3})", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        } else {
            RoundInfoRow("Timer ${guessSecondsLeft}s", "Entry ${BELI_SYMBOL}${state.status?.config?.skillGames?.animeGuessEntryCost ?: 3}", "No repeats")
            GuessPoster(imageUrl = game.imageUrl, mode = game.mode)
            if (game.hints.isNotEmpty()) {
                Surface(color = Color(0x14FFFFFF), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        game.hints.forEach { Text(it, color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp) }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = typedAnswer,
                    onValueChange = { typedAnswer = it },
                    label = { Text("Anime title") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                Button(
                    onClick = { viewModel.revealGuessHint() },
                    enabled = !busy && guessSecondsLeft > 0 && game.hints.size < 2,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF64B5F6)),
                ) { Text("Hint") }
            }
            Button(
                onClick = { viewModel.answerAnimeGuess(typedAnswer) },
                enabled = !busy && guessSecondsLeft > 0 && typedAnswer.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(8.dp),
            ) { Text("Submit Guess") }
            Text("Or choose one:", color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp)
            game.choices.forEach { choice ->
                OutlinedButton(
                    onClick = { viewModel.answerAnimeGuess(animeId = choice.animeId) },
                    enabled = !busy && guessSecondsLeft > 0,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(choice.title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun GuessPoster(imageUrl: String, mode: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(360.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0x14FFFFFF)),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = "Anime guess poster",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .then(if (mode == "blur") Modifier.blur(24.dp) else Modifier)
                .then(if (mode == "pixel") Modifier.blur(10.dp) else Modifier),
        )
        if (mode == "pixel") {
            Canvas(Modifier.fillMaxSize()) {
                val step = 28.dp.toPx()
                var x = 0f
                while (x < size.width) {
                    drawLine(Color.Black.copy(alpha = 0.22f), Offset(x, 0f), Offset(x, size.height), 3f)
                    x += step
                }
                var y = 0f
                while (y < size.height) {
                    drawLine(Color.Black.copy(alpha = 0.22f), Offset(0f, y), Offset(size.width, y), 3f)
                    y += step
                }
            }
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.18f)))
    }
}

@Composable
private fun TriviaGame(viewModel: GamesViewModel, state: GamesUiState) {
    val game = state.triviaState
    val result = state.triviaResult
    val busy = state.busyAction?.startsWith("trivia") == true
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (game == null || game.state != "playing") {
            result?.let {
                ResultPanel(
                    title = if (it.correct) "Correct" else "Wrong",
                    body = it.explanation,
                    positive = it.correct,
                )
                OutlinedButton(onClick = viewModel::resetTrivia, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) {
                    Text("New Trivia Round", color = Color.White)
                }
            }
            Button(
                onClick = viewModel::startTrivia,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GameKind.Trivia.accent),
            ) {
                Text("Start Trivia (${BELI_SYMBOL}${state.status?.config?.skillGames?.triviaEntryCost ?: 2})", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        } else {
            Surface(color = Color(0x14FFFFFF), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                Text(game.question, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
            }
            game.choices.forEachIndexed { index, choice ->
                val selected = game.selectedIndex == index
                val correct = result?.correctIndex == index
                val color = when {
                    correct -> Color(0xFF81C784)
                    selected && result != null -> Color(0xFFE57373)
                    else -> Color.White.copy(alpha = 0.08f)
                }
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !busy && result == null) { viewModel.answerTrivia(index) },
                    color = color,
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(choice, color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(14.dp))
                }
            }
        }
    }
}

@Composable
private fun BetStepper(bet: Int, maxBet: Int, onChange: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Text("Bet", color = Color.White.copy(alpha = 0.65f), fontSize = 13.sp, modifier = Modifier.width(52.dp))
            StepButton("-", enabled = bet > 1) { onChange((bet - 5).coerceAtLeast(1)) }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x14FFFFFF))
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("$BELI_SYMBOL${formatBerries(bet)}", color = Color(0xFFFFD166), fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
            StepButton("+", enabled = bet < maxBet) { onChange((bet + 5).coerceAtMost(maxBet)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            listOf(10, 25, 50, 100).forEach { value ->
                OutlinedButton(
                    onClick = { onChange(value.coerceAtMost(maxBet).coerceAtLeast(1)) },
                    enabled = maxBet >= value,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                ) { Text("$BELI_SYMBOL$value", color = Color.White, fontSize = 12.sp) }
            }
        }
    }
}

@Composable
private fun RiskInfoRow(left: String, center: String, right: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        listOf(left, center, right).forEach { label ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x14FFFFFF))
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = Color.White.copy(alpha = 0.74f), fontSize = 12.sp, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun StepButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(44.dp),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0x22FFFFFF)),
        contentPadding = ButtonDefaults.ContentPadding,
    ) {
        Text(text, fontWeight = FontWeight.Black, color = Color.White, fontSize = 18.sp)
    }
}

@Composable
private fun ChoiceButton(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier
            .height(52.dp)
            .clickable(onClick = onClick),
        color = if (selected) Color(0xFFFFD166) else Color(0x14FFFFFF),
        shape = RoundedCornerShape(8.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text, color = if (selected) Color.Black else Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun RoundInfoRow(left: String, center: String, right: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StatTile(left, "", Color.White, Modifier.weight(1f))
        StatTile(center, "", Color(0xFFFFD166), Modifier.weight(1f))
        StatTile(right, "", Color.White, Modifier.weight(1f))
    }
}

@Composable
private fun ResultPanel(title: String, body: String, positive: Boolean) {
    val color = if (positive) Color(0xFF81C784) else Color(0xFFE57373)
    Surface(color = color.copy(alpha = 0.16f), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, color = color, fontSize = 22.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
            Text(body, color = Color.White.copy(alpha = 0.78f), fontSize = 13.sp, textAlign = TextAlign.Center)
        }
    }
}

private fun crashMultiplier(startedAt: String, growthPerSecond: Double, nowMs: Long): Double {
    val startedMs = runCatching { Instant.parse(startedAt).toEpochMilliseconds() }
        .getOrDefault(nowMs)
    val elapsed = ((nowMs - startedMs).coerceAtLeast(0)).toDouble() / 1000.0
    return (1.0 + elapsed * growthPerSecond).coerceAtMost(50.0)
}

private fun formatMultiplier(value: Double): String =
    ((value * 100.0).toInt() / 100.0).toString()

private fun formatBerries(amount: Int): String =
    amount.toString().reversed().chunked(3).joinToString(",").reversed()

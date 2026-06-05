package to.kuudere.anisuge.screens.games

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import to.kuudere.anisuge.theme.AppColors
import to.kuudere.anisuge.screens.settings.BELI_SYMBOL

private val WHEEL_COLORS = listOf(
    Color(0xFFE74C3C), // Red
    Color(0xFF3498DB), // Blue
    Color(0xFF2ECC71), // Green
    Color(0xFFF39C12), // Orange
    Color(0xFF9B59B6), // Purple
    Color(0xFF1ABC9C), // Teal
    Color(0xFFE67E22), // Dark Orange
    Color(0xFFE91E63), // Pink
)

private val SEGMENTS = listOf(
    "Try Again" to 0,
    "1 Berry" to 1,
    "3 Berries" to 3,
    "5 Berries" to 5,
    "10 Berries" to 10,
    "25 Berries" to 25,
    "50 Berries" to 50,
    "JACKPOT" to 100,
)

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
            .background(AppColors.background)
    ) {
        // Top bar
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
            state.status?.let { status ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0x33FFFFFF))
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                ) {
                    Text(
                        "$BELI_SYMBOL${formatBerries(status.coins)}",
                        color = Color(0xFFFFD700),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        // Tab selector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            GamesTab.values().forEach { tab ->
                val isSelected = selectedTab == tab
                val bgColor by animateColorAsState(
                    if (isSelected) Color(0xFFFFD700) else Color(0x33FFFFFF),
                    label = "tabBg",
                )
                val textColor by animateColorAsState(
                    if (isSelected) Color.Black else Color.White.copy(alpha = 0.7f),
                    label = "tabText",
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(bgColor)
                        .clickable { selectedTab = tab }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        when (tab) {
                            GamesTab.Wheel -> "Berry Wheel"
                            GamesTab.CoinFlip -> "Coin Flip"
                        },
                        color = textColor,
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
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Wheel canvas
        Box(
            modifier = Modifier.size(280.dp),
            contentAlignment = Alignment.Center,
        ) {
            val spinAngle by animateFloatAsState(
                targetValue = if (state.spinning) 1080f else 0f,
                animationSpec = tween(durationMillis = 2000),
                label = "spinAngle",
            )
            Canvas(modifier = Modifier.fillMaxSize()) {
                val segAngle = 360f / SEGMENTS.size
                val outerRadius = size.minDimension / 2f
                val center = Offset(size.width / 2f, size.height / 2f)

                SEGMENTS.forEachIndexed { i, (label, _) ->
                    val startAngle = spinAngle + i * segAngle - 90f
                    rotate(startAngle, center) {
                        translate(
                            left = center.x - 20f,
                            top = center.y - outerRadius,
                        ) {
                            drawRoundRect(
                                color = WHEEL_COLORS[i % WHEEL_COLORS.size],
                                topLeft = Offset.Zero,
                                size = Size(40f, outerRadius),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f),
                            )
                        }
                    }
                }

                // Center circle
                drawCircle(Color(0xFFFFD700), outerRadius * 0.15f, center)
                drawCircle(Color.Black, outerRadius * 0.12f, center)

                // Pointer triangle at top
                val pointerPath = androidx.compose.ui.graphics.Path().apply {
                    moveTo(center.x - 12f, center.y - outerRadius + 12f)
                    lineTo(center.x + 12f, center.y - outerRadius + 12f)
                    lineTo(center.x, center.y - outerRadius - 12f)
                    close()
                }
                drawPath(pointerPath, Color(0xFFFFD700))
            }

            if (state.spinning) {
                CircularProgressIndicator(
                    color = Color(0xFFFFD700),
                    modifier = Modifier.size(32.dp),
                    strokeWidth = 3.dp,
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Result display
        state.wheelResult?.let { result ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (result.prize > 0) Color(0x1A2ECC71) else Color(0x1AE74C3C))
                    .padding(16.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (result.prize > 0) {
                        Icon(
                            Icons.Filled.Star,
                            contentDescription = null,
                            tint = Color(0xFFFFD700),
                            modifier = Modifier.size(32.dp),
                        )
                    }
                    Text(
                        result.prizeLabel,
                        color = if (result.prize > 0) Color(0xFF2ECC71) else Color(0xFFE74C3C),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        if (result.freeSpin) "Free spin!" else "Cost: $BELI_SYMBOL${result.cost}",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 13.sp,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // Win/loss stats
        state.status?.let { status ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                StatBox("Earned", "$BELI_SYMBOL${formatBerries(status.totalEarned)}", Color(0xFF2ECC71))
                StatBox("Lost", "$BELI_SYMBOL${formatBerries(status.totalLost)}", Color(0xFFE74C3C))
                StatBox("Best Streak", "${status.bestStreak}", Color(0xFFFFD700))
            }
            Spacer(Modifier.height(16.dp))
        }

        // Spin buttons
        state.status?.let { status ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Free spin
                Button(
                    onClick = { viewModel.spinWheel(true) },
                    enabled = !state.spinning && status.canFreeWheel,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFFD700),
                        disabledContainerColor = Color(0x33FFFFFF),
                    ),
                ) {
                    Text(
                        if (status.canFreeWheel) "Free Spin" else "Used Today",
                        color = if (status.canFreeWheel) Color.Black else Color.Gray,
                        fontWeight = FontWeight.Bold,
                    )
                }

                // Paid spin
                Button(
                    onClick = { viewModel.spinWheel(false) },
                    enabled = !state.spinning && status.coins >= 5,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF9B59B6),
                        disabledContainerColor = Color(0x33FFFFFF),
                    ),
                ) {
                    Text(
                        "Spin (${BELI_SYMBOL}5)",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // Prize table
        Text(
            "Prize Table",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(8.dp))
        SEGMENTS.forEachIndexed { i, (label, prize) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(WHEEL_COLORS[i % WHEEL_COLORS.size])
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(label, color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                }
                Text(
                    if (prize > 0) "$BELI_SYMBOL$prize" else "-",
                    color = if (prize > 0) Color(0xFFFFD700) else Color.Gray,
                    fontSize = 13.sp,
                )
            }
        }
    }
}

@Composable
private fun CoinFlipContent(
    viewModel: GamesViewModel,
    state: GamesUiState,
) {
    var betText by remember { mutableStateOf("10") }
    var selectedChoice by remember { mutableStateOf<String?>(null) }
    var showResult by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))

        // Coin visual
        val flipRotation by animateFloatAsState(
            targetValue = if (state.flipping) 720f else if (showResult && state.coinFlipResult != null) 360f else 0f,
            animationSpec = tween(durationMillis = 800),
            label = "flipRotation",
        )
        Box(
            modifier = Modifier.size(160.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            state.coinFlipResult?.won == true -> Color(0x1A2ECC71)
                            state.coinFlipResult?.won == false -> Color(0x1AE74C3C)
                            else -> Color(0x33FFFFFF)
                        }
                    )
                    .border(3.dp, Color(0xFFFFD700), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    when {
                        state.flipping -> "..."
                        state.coinFlipResult != null -> state.coinFlipResult.result.uppercase()
                        else -> "?"
                    },
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFFD700),
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Win/loss text
        state.coinFlipResult?.let { result ->
            Text(
                if (result.won) "You won $BELI_SYMBOL${result.payout}!" else "You lost $BELI_SYMBOL${result.bet}",
                color = if (result.won) Color(0xFF2ECC71) else Color(0xFFE74C3C),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "New balance: $BELI_SYMBOL${formatBerries(result.coins)}",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    showResult = false
                    viewModel.clearCoinFlipResult()
                    selectedChoice = null
                },
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("Play Again", color = Color.White)
            }
            Spacer(Modifier.height(16.dp))
        }

        // Bet input
        OutlinedTextField(
            value = betText,
            onValueChange = { betText = it.filter { c -> c.isDigit() } },
            label = { Text("Bet Amount", color = Color.White.copy(alpha = 0.5f)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(180.dp),
            singleLine = true,
                    textStyle = TextStyle(color = Color.White, textAlign = TextAlign.Center),
        )

        Spacer(Modifier.height(16.dp))

        // Choice buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            listOf("heads" to Icons.Filled.Star, "tails" to Icons.Default.Close).forEach { (choice, icon) ->
                val isSelected = selectedChoice == choice
                val bgColor by animateColorAsState(
                    if (isSelected) Color(0xFFFFD700) else Color(0x33FFFFFF),
                    label = "choiceBg",
                )
                Button(
                    onClick = { selectedChoice = choice },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = bgColor,
                        disabledContainerColor = Color(0x33FFFFFF),
                    ),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            choice.uppercase(),
                            color = if (isSelected) Color.Black else Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // Flip button
        Button(
            onClick = {
                val bet = betText.toIntOrNull() ?: return@Button
                val choice = selectedChoice ?: return@Button
                showResult = true
                viewModel.flipCoin(bet, choice)
            },
            enabled = !state.flipping && selectedChoice != null && (betText.toIntOrNull() ?: 0) >= 1 && (state.status?.coins ?: 0) >= (betText.toIntOrNull() ?: 0),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFE74C3C),
                disabledContainerColor = Color(0x33FFFFFF),
            ),
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                if (state.flipping) "Flipping..." else "Flip Coin",
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
            .background(Color(0x15FFFFFF))
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Text(value, color = color, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(label, color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
    }
}

private fun formatBerries(amount: Int): String {
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

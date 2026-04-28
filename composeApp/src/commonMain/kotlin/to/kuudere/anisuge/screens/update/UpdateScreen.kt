package to.kuudere.anisuge.screens.update
import to.kuudere.anisuge.screens.update.UpdateState

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import to.kuudere.anisuge.theme.Background
import to.kuudere.anisuge.theme.KuudereRed
import to.kuudere.anisuge.theme.Muted
import to.kuudere.anisuge.theme.Surface
import to.kuudere.anisuge.platform.openUrl
import to.kuudere.anisuge.i18n.LocalAppStrings

@Composable
fun UpdateScreen(
    state: UpdateState,
    onUpdateLater: () -> Unit,
    onUpdateNow: () -> Unit
) {
    val strings = LocalAppStrings.current
    var startAnimation by remember { mutableStateOf(false) }
    var isFinishing by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        startAnimation = true
    }

    val alpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(1000)
    )

    LaunchedEffect(state.isUpdateAvailable) {
        if (state.isUpdateAvailable == false) {
            onUpdateLater()
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Background),
        contentAlignment = Alignment.Center
    ) {
        val isWide = maxWidth > 800.dp

        // Subtle monochrome decorative blurs
        Box(
            modifier = Modifier
                .size(400.dp)
                .align(Alignment.TopEnd)
                .offset(x = 150.dp, y = (-150).dp)
                .blur(120.dp)
                .background(Color.White.copy(alpha = 0.03f), CircleShape)
        )

        Box(
            modifier = Modifier
                .size(300.dp)
                .align(Alignment.BottomStart)
                .offset(x = (-100).dp, y = 150.dp)
                .blur(100.dp)
                .background(Color.White.copy(alpha = 0.02f), CircleShape)
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp)
                .alpha(alpha),
            contentAlignment = Alignment.Center
        ) {
            if (state.isUpdateAvailable == null) {
                // Checking for updates
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(strings.checkingForUpdates, color = Muted)
                }
            } else if (isWide) {
                // Wide screen: Two-column layout
                Row(
                    modifier = Modifier
                        .widthIn(max = 1000.dp)
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(64.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        UpdateHeader(state = state, isWide = true)
                        
                        UpdateActions(
                            onUpdateNow = {
                                isDownloading = true
                                state.downloadUrl?.let { openUrl(it) }
                                onUpdateNow()
                            },
                            onUpdateLater = {
                                isFinishing = true
                                onUpdateLater()
                            },
                            isFinishing = isFinishing,
                            isDownloading = isDownloading,
                            isCritical = state.isCritical,
                            modifier = Modifier.padding(top = 40.dp)
                        )

                        if (isDownloading && !state.isCritical) {
                            TextButton(
                                onClick = onUpdateLater,
                                modifier = Modifier.padding(top = 8.dp)
                            ) {
                                Text(strings.backToApp, color = Muted, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }

                    // Right Column: Changelog
                    Column(modifier = Modifier.weight(1.2f)) {
                        ChangelogCard(state = state)
                    }
                }
            } else {
                // Narrow screen: Stacked layout
                Column(
                    modifier = Modifier
                        .widthIn(max = 520.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    UpdateHeader(
                        state = state, 
                        isWide = false,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(40.dp))
                    ChangelogCard(state = state)
                    Spacer(modifier = Modifier.height(48.dp))
                    UpdateActions(
                        onUpdateNow = {
                            isDownloading = true
                            state.downloadUrl?.let { openUrl(it) }
                            onUpdateNow()
                        },
                        onUpdateLater = {
                            isFinishing = true
                            onUpdateLater()
                        },
                        isFinishing = isFinishing,
                        isDownloading = isDownloading,
                        isCritical = state.isCritical,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (isDownloading && !state.isCritical) {
                        TextButton(
                            onClick = onUpdateLater,
                            modifier = Modifier.padding(top = 12.dp)
                        ) {
                            Text(strings.backToApp, color = Muted, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UpdateHeader(
    state: UpdateState, 
    isWide: Boolean,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(64.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Surface)
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(20.dp))
        ) {
            Icon(
                imageVector = Icons.Default.SystemUpdate,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = strings.newUpdateAvailable,
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.CenterHorizontally),
            maxLines = 1
        )

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.CenterHorizontally)
        ) {
            Text(
                text = state.currentVersion,
                style = MaterialTheme.typography.bodyMedium,
                color = Muted
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = Muted.copy(alpha = 0.5f),
                modifier = Modifier.padding(horizontal = 8.dp).size(14.dp)
            )
            Text(
                text = state.newVersion,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ChangelogCard(state: UpdateState) {
    val strings = LocalAppStrings.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 400.dp)
            .clip(RoundedCornerShape(24.dp)),
        color = Surface.copy(alpha = 0.4f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text = strings.whatsNew,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(20.dp))

            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                state.changelog.forEach { change ->
                    Row(verticalAlignment = Alignment.Top) {
                        Box(
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .size(4.dp)
                                .background(Color.White, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = change,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.7f),
                            lineHeight = 22.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UpdateActions(
    onUpdateNow: () -> Unit, 
    onUpdateLater: () -> Unit,
    isFinishing: Boolean,
    isDownloading: Boolean = false,
    isCritical: Boolean = false,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    Column(modifier = modifier) {
        Button(
            onClick = onUpdateNow,
            enabled = !isFinishing && !isDownloading,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White,
                contentColor = Color.Black
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                text = if (isDownloading) strings.openingBrowser else strings.updateNow,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        if (!isDownloading && !isCritical) {
            Spacer(modifier = Modifier.height(12.dp))

            TextButton(
                onClick = onUpdateLater,
                enabled = !isFinishing,
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                if (isFinishing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = Muted
                    )
                } else {
                    Text(
                        text = strings.maybeLater,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Muted
                    )
                }
            }
        }
    }
}

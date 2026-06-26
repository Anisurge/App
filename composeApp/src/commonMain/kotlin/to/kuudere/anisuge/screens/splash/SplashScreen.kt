package to.kuudere.anisuge.screens.splash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import kotlinx.coroutines.delay
import to.kuudere.anisuge.player.VideoPlayerSurface
import to.kuudere.anisuge.player.rememberVideoPlayerState
import to.kuudere.anisuge.platform.DraggableWindowArea

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height

@Composable
fun SplashScreen(
    viewModel: SplashViewModel,
    onNavigateToAuth: () -> Unit,
    onNavigateToHome: () -> Unit,
) {
    val destination by viewModel.destination.collectAsState()
    val status by viewModel.status.collectAsState()
    var videoFinished by remember { mutableStateOf(false) }

    // Timeout fallback for faster app opening (users report slow startup)
    LaunchedEffect(Unit) {
        delay(1200)
        videoFinished = true
    }

    // Navigate when destination resolved (video removed for instant open; status shows progress)
    LaunchedEffect(destination, videoFinished) {
        if (destination != SplashDestination.Waiting) {
            when (destination) {
                is SplashDestination.GoHome,
                is SplashDestination.GoHomeOffline -> onNavigateToHome()
                else -> onNavigateToAuth()
            }
        }
    }

    DraggableWindowArea(Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    videoFinished = true
                },
            contentAlignment = Alignment.Center,
        ) {
            // Lightweight static splash for fast perceived startup (no heavy video player init)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("ANISURGE", color = Color.White, fontSize = 36.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Text("Loading...", color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp)
            }

            // Status text at bottom center
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Status: $status",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    letterSpacing = 1.sp
                )
            }
        }
    }
}

@Composable
expect fun SplashVideoBackground(onVideoFinished: () -> Unit)

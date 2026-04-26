package to.kuudere.anisuge.screens.auth

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Platform-specific video background.
 * On Android, plays a looping video. On Desktop, shows nothing (use fallback).
 */
@Composable
expect fun VideoBackground(
    videoUrl: String,
    modifier: Modifier = Modifier
)

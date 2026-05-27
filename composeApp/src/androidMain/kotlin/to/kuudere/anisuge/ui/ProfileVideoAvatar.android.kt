package to.kuudere.anisuge.ui

import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import java.io.File

@Composable
@OptIn(UnstableApi::class)
actual fun ProfileVideoAvatar(
    url: String,
    modifier: Modifier,
    contentDescription: String?,
) {
    val context = LocalContext.current
    val mediaSourceFactory = remember(context) {
        DefaultMediaSourceFactory(ProfileVideoCache.dataSourceFactory(context))
    }
    val player = remember(url) {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .apply {
                repeatMode = Player.REPEAT_MODE_ONE
                playWhenReady = true
                volume = 0f
                setMediaItem(MediaItem.fromUri(url))
                prepare()
            }
    }

    DisposableEffect(player) {
        onDispose { player.release() }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                isClickable = false
                isFocusable = false
                contentDescription?.let { this.contentDescription = it }
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
            }
        },
        update = { view ->
            if (view.player !== player) view.player = player
            player.volume = 0f
            player.playWhenReady = true
        },
        modifier = modifier,
    )
}

@OptIn(UnstableApi::class)
private object ProfileVideoCache {
    private const val MAX_BYTES = 64L * 1024L * 1024L
    @Volatile private var cache: SimpleCache? = null

    fun dataSourceFactory(context: android.content.Context): CacheDataSource.Factory {
        val appContext = context.applicationContext
        val resolvedCache = cache ?: synchronized(this) {
            cache ?: SimpleCache(
                File(appContext.cacheDir, "profile-video-cache"),
                LeastRecentlyUsedCacheEvictor(MAX_BYTES),
                StandaloneDatabaseProvider(appContext),
            ).also { cache = it }
        }
        val upstream = DefaultHttpDataSource.Factory()
            .setUserAgent("AniSurge/ProfileVideo")
            .setConnectTimeoutMs(8_000)
            .setReadTimeoutMs(12_000)
        return CacheDataSource.Factory()
            .setCache(resolvedCache)
            .setUpstreamDataSourceFactory(upstream)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }
}

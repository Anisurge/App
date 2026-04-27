package to.kuudere.anisuge.player

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * Manages an Android MediaSession so that earphone/headphone media buttons
 * (play/pause, next, previous) can control the video player.
 *
 * Also handles AudioFocus so that playback pauses when another app takes focus
 * (e.g. incoming call, another music app).
 */
@UnstableApi
class MediaSessionManager(private val context: Context) {

    private var mediaSession: MediaSession? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var playerAdapter: PlayerAdapter? = null

    /**
     * Start the media session, wiring it to the given [state].
     * The [onPlayPauseToggle] callback is invoked when the user presses play/pause
     * on their earphones. Pass `true` to pause, `false` to resume.
     */
    fun start(
        state: VideoPlayerState,
        onPlayPauseToggle: (Boolean) -> Unit,
        onNext: (() -> Unit)? = null,
        onPrev: (() -> Unit)? = null
    ) {
        // Only request audio focus and start MediaSession if we are NOT muted.
        // Background videos (like splash or auth loops) are usually muted and should
        // NOT steal focus from background music apps or show up in the lock screen.
        if (!state.config.muted) {
            audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val focusReq = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                        .build()
                )
                .setOnAudioFocusChangeListener { focusChange ->
                    when (focusChange) {
                        AudioManager.AUDIOFOCUS_LOSS,
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                            // Pause playback when losing focus
                            onPlayPauseToggle(true)
                        }
                        AudioManager.AUDIOFOCUS_GAIN -> {
                            // For video we don't auto-resume on regain
                        }
                    }
                }
                .build()

            audioFocusRequest = focusReq
            audioManager?.requestAudioFocus(focusReq)

            // Create the player adapter and media session
            val adapter = PlayerAdapter(state, onPlayPauseToggle, onNext, onPrev)
            playerAdapter = adapter

            val session = MediaSession.Builder(context, adapter)
                .setId("AnisugVideoSession_${System.currentTimeMillis()}")
                .build()

            mediaSession = session
        }
    }

    /**
     * Call this whenever the player's playback state changes externally (e.g. mpv fires a
     * pause/resume event) so the MediaSession immediately reflects the new state.
     * Without this, Android caches the stale state and the earphone play button feels laggy.
     */
    fun notifyStateChanged() {
        playerAdapter?.notifyStateChanged()
    }

    /**
     * Release the media session and abandon audio focus. Call from DisposableEffect onDispose.
     */
    fun release() {
        mediaSession?.release()
        mediaSession = null
        audioFocusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
        audioFocusRequest = null
        audioManager = null
        playerAdapter = null
    }
}

/**
 * Minimal [SimpleBasePlayer] implementation that bridges our libmpv-based VideoPlayerState
 * to the MediaSession API. MediaSession requires a Player, but we only need to handle
 * play/pause commands — the actual video is rendered by libmpv, not ExoPlayer.
 */
@UnstableApi
private class PlayerAdapter(
    private val state: VideoPlayerState,
    private val onPlayPause: (Boolean) -> Unit,
    private val onNext: (() -> Unit)?,
    private val onPrev: (() -> Unit)?
) : SimpleBasePlayer(android.os.Looper.getMainLooper()) {

    override fun getState(): State {
        val isPlaying = state.isPlaying && !state.isPaused
        val positionMs = (state.position * 1000).toLong().coerceAtLeast(0)
        val durationMs = if (state.duration > 0) (state.duration * 1000).toLong() else androidx.media3.common.C.TIME_UNSET

        val commands = Player.Commands.Builder()
            .addAll(
                Player.COMMAND_PLAY_PAUSE,
                Player.COMMAND_STOP,
                Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
                Player.COMMAND_GET_MEDIA_ITEMS_METADATA,
            )

        if (onNext != null && state.hasNextEpisode) {
            commands.add(Player.COMMAND_SEEK_TO_NEXT)
            commands.add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
        }
        if (onPrev != null && state.hasPrevEpisode) {
            commands.add(Player.COMMAND_SEEK_TO_PREVIOUS)
            commands.add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
        }

        val mediaItemBuilder = MediaItemData.Builder(/* uid= */ "anisuge-video")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle("Anisuge")
                    .build()
            )

        if (durationMs != androidx.media3.common.C.TIME_UNSET) {
            mediaItemBuilder.setDurationUs(durationMs * 1000)
        }

        return State.Builder()
            .setAvailableCommands(commands.build())
            .setPlayWhenReady(isPlaying, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setPlaybackState(
                if (state.isBuffering) Player.STATE_BUFFERING
                else if (state.isPlaying) Player.STATE_READY
                else Player.STATE_IDLE
            )
            .setPlaylist(listOf(mediaItemBuilder.build()))
            .setContentPositionMs { positionMs }
            .build()
    }

    /**
     * Push the current mpv state to the MediaSession so Android always knows
     * whether we're paused or playing. Called externally when mpv fires state events.
     */
    fun notifyStateChanged() {
        invalidateState()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        // playWhenReady == true means "play", false means "pause"
        onPlayPause(!playWhenReady) // our callback takes true = should pause
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(
        mediaItemIndex: Int,
        positionMs: Long,
        seekCommand: @Player.Command Int
    ): ListenableFuture<*> {
        when (seekCommand) {
            Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> {
                onNext?.invoke()
            }
            Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> {
                onPrev?.invoke()
            }
        }
        return Futures.immediateVoidFuture()
    }

    override fun handleStop(): ListenableFuture<*> {
        onPlayPause(true)
        return Futures.immediateVoidFuture()
    }
}

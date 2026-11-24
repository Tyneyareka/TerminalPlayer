package dev.terminalplayer.playback

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import dev.terminalplayer.data.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Single source of truth for playback state in the UI layer.
 *
 * Connects a [MediaController] to [PlaybackService] once per process, mirrors the player state into
 * [state] and exposes intent-like commands (`togglePlayPause`, `seekBy`, ...). The controller is
 * intentionally process scoped: playback must survive activity re-creation and tab switches.
 */
class PlayerConnection(context: Context) {

    private val appContext: Context = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(PlaybackUiState())
    val state: StateFlow<PlaybackUiState> = _state.asStateFlow()

    private var controller: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var positionTicker: Job? = null

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            publish(player)
        }
    }

    fun connect() {
        if (controllerFuture != null) return
        val token = SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
        val future = MediaController.Builder(appContext, token).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                val mediaController = runCatching { future.get() }.getOrNull()
                if (mediaController == null) {
                    _state.update { it.copy(connected = false) }
                    return@addListener
                }
                controller = mediaController
                mediaController.addListener(playerListener)
                publish(mediaController)
                startPositionTicker()
            },
            ContextCompat.getMainExecutor(appContext),
        )
    }

    fun release() {
        positionTicker?.cancel()
        positionTicker = null
        controller?.removeListener(playerListener)
        controller = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        _state.value = PlaybackUiState()
    }

    // ------------------------------------------------------------------ queue

    /**
     * Runs [block] only when a controller is connected. `MediaController` calls are not allowed to
     * crash the UI thread if the service was killed in the background, so failures are swallowed
     * and surfaced as an unchanged state instead of an exception.
     */
    private inline fun withController(block: (MediaController) -> Unit) {
        val mediaController = controller ?: return
        runCatching { block(mediaController) }
    }

    fun playQueue(tracks: List<Track>, startIndex: Int) {
        if (tracks.isEmpty()) return
        val items: List<MediaItem> = tracks.map { it.toMediaItem() }
        val index = startIndex.coerceIn(0, items.lastIndex)
        withController { mediaController ->
            mediaController.setMediaItems(items, index, 0L)
            mediaController.prepare()
            mediaController.play()
        }
    }

    // --------------------------------------------------------------- commands

    fun togglePlayPause() = withController { mediaController ->
        if (mediaController.isPlaying) {
            mediaController.pause()
        } else if (mediaController.mediaItemCount > 0) {
            if (mediaController.playbackState == Player.STATE_IDLE) {
                mediaController.prepare()
            }
            mediaController.play()
        }
    }

    fun next() = withController { it.seekToNextMediaItem() }

    fun previous() = withController { it.seekToPreviousMediaItem() }

    fun seekToFraction(fraction: Float) = withController { mediaController ->
        val duration = mediaController.duration
        if (duration > 0L) {
            mediaController.seekTo((fraction.coerceIn(0f, 1f) * duration).toLong())
        }
    }

    fun seekBy(deltaMillis: Long) = withController { mediaController ->
        val duration = mediaController.duration
        val target = mediaController.currentPosition + deltaMillis
        mediaController.seekTo(
            if (duration > 0L) target.coerceIn(0L, duration) else target.coerceAtLeast(0L),
        )
    }

    fun toggleShuffle() = withController { mediaController ->
        mediaController.shuffleModeEnabled = !mediaController.shuffleModeEnabled
    }

    fun cycleRepeat() = withController { mediaController ->
        mediaController.repeatMode = when (mediaController.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    // ---------------------------------------------------------------- internals

    private fun startPositionTicker() {
        if (positionTicker?.isActive == true) return
        positionTicker = scope.launch {
            while (isActive) {
                val mediaController = controller
                if (mediaController != null) {
                    val isPlaying = runCatching { mediaController.isPlaying }.getOrDefault(false)
                    if (isPlaying) {
                        val position = runCatching { mediaController.currentPosition }
                            .getOrDefault(0L)
                            .coerceAtLeast(0L)
                        val duration = runCatching { mediaController.duration }.getOrDefault(0L)
                        _state.update {
                            it.copy(
                                positionMs = position,
                                durationMs = if (duration > 0L) duration else it.durationMs,
                            )
                        }
                    }
                }
                delay(POSITION_INTERVAL_MS)
            }
        }
    }

    private fun publish(player: Player) {
        val snapshot = runCatching {
            val item = player.currentMediaItem
            val metadata = item?.mediaMetadata
            val duration = player.duration
            val metadataDuration = metadata?.durationMs ?: 0L
            PlaybackUiState(
                connected = true,
                isPlaying = player.isPlaying,
                isBuffering = player.playbackState == Player.STATE_BUFFERING,
                mediaId = item?.mediaId.orEmpty(),
                title = metadata?.title?.toString().orEmpty(),
                artist = metadata?.artist?.toString().orEmpty(),
                album = metadata?.albumTitle?.toString().orEmpty(),
                positionMs = player.currentPosition.coerceAtLeast(0L),
                durationMs = if (duration > 0L) duration else metadataDuration.coerceAtLeast(0L),
                queueIndex = player.currentMediaItemIndex,
                queueSize = player.mediaItemCount,
                shuffle = player.shuffleModeEnabled,
                repeatMode = player.repeatMode,
            )
        }.getOrNull() ?: return

        _state.value = snapshot
    }

    private companion object {
        const val POSITION_INTERVAL_MS = 250L
    }
}

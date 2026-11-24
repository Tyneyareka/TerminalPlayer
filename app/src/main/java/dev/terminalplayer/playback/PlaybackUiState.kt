package dev.terminalplayer.playback

import androidx.media3.common.Player

/**
 * Immutable snapshot of everything the terminal UI needs to render.
 *
 * The state is produced by [PlayerConnection] and consumed by `PlayerViewModel`, so Compose never
 * talks to the `MediaController` directly.
 */
data class PlaybackUiState(
    val connected: Boolean = false,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val mediaId: String = "",
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val queueIndex: Int = -1,
    val queueSize: Int = 0,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
) {
    val hasTrack: Boolean get() = mediaId.isNotEmpty()

    val progress: Float
        get() = if (durationMs > 0L) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    val repeatLabel: String
        get() = when (repeatMode) {
            Player.REPEAT_MODE_ONE -> "ONE"
            Player.REPEAT_MODE_ALL -> "ALL"
            else -> "OFF"
        }

    val stateLabel: String
        get() = when {
            !connected -> "LINKING"
            isBuffering -> "BUFFERING"
            isPlaying -> "PLAYING"
            hasTrack -> "PAUSED"
            else -> "IDLE"
        }
}

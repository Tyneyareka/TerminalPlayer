package dev.terminalplayer.playback

import androidx.media3.common.C
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-process bridge between the playback service and the UI.
 *
 * `MediaController` does not expose the audio session id, but `android.media.audiofx.Visualizer`
 * needs exactly that value. The service owns the ExoPlayer instance and publishes the session id
 * here; the visualizer screen observes it. Both components live in the same process, so a plain
 * [MutableStateFlow] is the cheapest correct solution - no binding, no Binder, no IPC.
 */
object AudioSessionBus {

    private val _sessionId = MutableStateFlow(C.AUDIO_SESSION_ID_UNSET)
    val sessionId: StateFlow<Int> = _sessionId.asStateFlow()

    fun publish(audioSessionId: Int) {
        _sessionId.value = audioSessionId
    }
}

package dev.terminalplayer.ui.visualizer

import android.Manifest
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import dev.terminalplayer.playback.AudioSessionBus
import dev.terminalplayer.ui.components.AsciiButton
import dev.terminalplayer.ui.components.InfoPanel
import dev.terminalplayer.ui.components.PermissionState
import dev.terminalplayer.ui.components.SectionHeader
import dev.terminalplayer.ui.components.openAppSettings
import dev.terminalplayer.ui.components.rememberPermissionState
import dev.terminalplayer.ui.theme.TerminalAmber
import dev.terminalplayer.ui.theme.TerminalRed

/**
 * Screen 2: the ASCII visualizer.
 *
 * Access chain:
 * `PlaybackService` -> ExoPlayer audio session id -> [AudioSessionBus] -> [VisualizerEngine]
 * -> FFT bands -> Compose Canvas character grid.
 *
 * The screen degrades gracefully: missing RECORD_AUDIO, no audio session yet and OEMs that refuse
 * the Visualizer effect all render a terminal style explanation instead of crashing.
 */
@Composable
fun VisualizerScreen(onOpenPlayer: () -> Unit) {
    val sessionId by AudioSessionBus.sessionId.collectAsStateWithLifecycle()
    val permission = rememberPermissionState(Manifest.permission.RECORD_AUDIO)

    Column(Modifier.fillMaxSize()) {
        SectionHeader(title = "VISUALIZER", trailing = "FFT // WAVE")
        Spacer(Modifier.height(6.dp))

        when {
            !permission.granted -> VisualizerPermission(permission)
            sessionId == C.AUDIO_SESSION_ID_UNSET -> VisualizerWaiting(onOpenPlayer)
            else -> VisualizerStage(
                sessionId = sessionId,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        }
    }
}

@Composable
private fun VisualizerStage(sessionId: Int, modifier: Modifier) {
    val engine = remember(sessionId) { VisualizerEngine(sessionId) }
    var failure by remember(engine) { mutableStateOf<String?>(null) }

    DisposableEffect(engine) {
        failure = engine.start()
        onDispose { engine.release() }
    }

    KeepScreenOn()

    Column(modifier) {
        val message = failure
        if (message != null) {
            InfoPanel(
                code = "E_VISUALIZER",
                message = message,
                tone = TerminalRed,
                actions = { AsciiButton("[ RETRY ]", onClick = { failure = engine.start() }) },
            )
        } else {
            AsciiVisualizer(
                engine = engine,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        }
    }
}

@Composable
private fun VisualizerPermission(permission: PermissionState) {
    val context = LocalContext.current
    InfoPanel(
        code = "E_AUDIO_CAPTURE",
        message = "The visualizer taps the app's own playback session through " +
            "android.media.audiofx.Visualizer. Android requires RECORD_AUDIO for that. " +
            "No microphone audio is recorded, stored or sent anywhere.",
        actions = {
            AsciiButton("[ GRANT ]", onClick = permission.request)
            AsciiButton("[ SETTINGS ]", onClick = { context.openAppSettings() })
        },
    )
}

@Composable
private fun VisualizerWaiting(onOpenPlayer: () -> Unit) {
    InfoPanel(
        code = "E_NO_AUDIO_SESSION",
        message = "The playback service has not published an audio session yet. " +
            "Start a track, then return to this tab. The session id is shown on the player screen " +
            "as SES:0x........",
        tone = TerminalAmber,
        actions = { AsciiButton("[ OPEN PLAYER ]", onClick = onOpenPlayer) },
    )
}

@Composable
private fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

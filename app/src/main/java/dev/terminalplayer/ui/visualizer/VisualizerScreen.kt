package dev.terminalplayer.ui.visualizer

import android.Manifest
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import dev.terminalplayer.i18n.LocalStrings
import dev.terminalplayer.playback.AudioSessionBus
import dev.terminalplayer.ui.components.AsciiButton
import dev.terminalplayer.ui.components.InfoPanel
import dev.terminalplayer.ui.components.PermissionState
import dev.terminalplayer.ui.components.openAppSettings
import dev.terminalplayer.ui.components.rememberPermissionState
import dev.terminalplayer.ui.theme.Danger
import dev.terminalplayer.ui.theme.TextMuted
import dev.terminalplayer.ui.theme.TextPrimary
import dev.terminalplayer.ui.theme.TerminalType
import dev.terminalplayer.ui.theme.Warning

/**
 * Screen 2: the ASCII visualizer.
 *
 * Access chain:
 * `PlaybackService` -> ExoPlayer audio session id -> [AudioSessionBus] -> [VisualizerEngine]
 * -> FFT bands -> Compose Canvas character grid.
 *
 * The screen degrades gracefully: missing RECORD_AUDIO, no audio session yet and OEMs that refuse
 * the Visualizer effect all render a card-style explanation instead of crashing.
 */
@Composable
fun VisualizerScreen(onOpenPlayer: () -> Unit) {
    val sessionId by AudioSessionBus.sessionId.collectAsStateWithLifecycle()
    val permission = rememberPermissionState(Manifest.permission.RECORD_AUDIO)

    Column(Modifier.fillMaxSize()) {
        VisualizerHeader()
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                !permission.granted -> VisualizerPermission(permission)
                sessionId == C.AUDIO_SESSION_ID_UNSET -> VisualizerWaiting(onOpenPlayer)
                else -> VisualizerStage(
                    sessionId = sessionId,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun VisualizerHeader() {
    val strings = LocalStrings.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = strings.visualizerTitle,
                style = TerminalType.headline,
                color = TextPrimary,
                maxLines = 1,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = strings.visualizerSubtitle,
                style = TerminalType.label,
                color = TextMuted,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = strings.visualizerColumns,
            style = TerminalType.label,
            color = TextMuted,
            maxLines = 1,
        )
    }
}

@Composable
private fun VisualizerStage(sessionId: Int, modifier: Modifier) {
    val strings = LocalStrings.current
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
                tone = Danger,
                actions = { AsciiButton(strings.retryAction, onClick = { failure = engine.start() }) },
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
    val strings = LocalStrings.current
    val context = LocalContext.current
    InfoPanel(
        code = "E_AUDIO_CAPTURE",
        message = strings.visualizerPermissionMessage,
        actions = {
            AsciiButton(strings.grantAction, onClick = permission.request)
            AsciiButton(strings.settingsAction, onClick = { context.openAppSettings() })
        },
    )
}

@Composable
private fun VisualizerWaiting(onOpenPlayer: () -> Unit) {
    val strings = LocalStrings.current
    InfoPanel(
        code = "E_NO_AUDIO_SESSION",
        message = strings.visualizerNoSessionMessage,
        tone = Warning,
        actions = { AsciiButton(strings.openPlayerAction, onClick = onOpenPlayer) },
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

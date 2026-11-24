package dev.terminalplayer.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.Player
import dev.terminalplayer.core.Ascii
import dev.terminalplayer.data.Track
import dev.terminalplayer.playback.PlaybackUiState
import dev.terminalplayer.ui.components.AsciiButton
import dev.terminalplayer.ui.components.AsciiProgressBar
import dev.terminalplayer.ui.components.BlinkingCursor
import dev.terminalplayer.ui.components.InfoPanel
import dev.terminalplayer.ui.components.MarqueeText
import dev.terminalplayer.ui.components.PermissionState
import dev.terminalplayer.ui.components.SectionHeader
import dev.terminalplayer.ui.components.audioLibraryPermission
import dev.terminalplayer.ui.components.audioLibraryPermissions
import dev.terminalplayer.ui.components.openAppSettings
import dev.terminalplayer.ui.components.rememberPermissionStateFor
import dev.terminalplayer.ui.theme.TerminalAmber
import dev.terminalplayer.ui.theme.TerminalBlack
import dev.terminalplayer.ui.theme.TerminalBright
import dev.terminalplayer.ui.theme.TerminalGray
import dev.terminalplayer.ui.theme.TerminalGreen
import dev.terminalplayer.ui.theme.TerminalGreenDim
import dev.terminalplayer.ui.theme.TerminalGreenSoft
import dev.terminalplayer.ui.theme.TerminalRed
import dev.terminalplayer.ui.theme.TerminalType
import java.util.Locale

/**
 * Screen 1: the player.
 *
 * Fixed terminal header (title / progress / transport), then the scrollable MediaStore library.
 * Everything is text: no Icon, no Image, no Material button.
 */
@Composable
fun PlayerScreen(viewModel: PlayerViewModel = viewModel()) {
    val playback by viewModel.playback.collectAsStateWithLifecycle()
    val library by viewModel.library.collectAsStateWithLifecycle()
    val sessionId by viewModel.audioSessionId.collectAsStateWithLifecycle()

    val permission = rememberPermissionStateFor(
        requiredPermission = audioLibraryPermission(),
        requestedPermissions = audioLibraryPermissions(),
    )

    LaunchedEffect(permission.granted) {
        if (permission.granted) viewModel.refreshLibraryIfNeeded()
    }

    Column(Modifier.fillMaxSize()) {
        SectionHeader(
            title = "PLAYER",
            trailing = if (playback.connected) "ONLINE" else "LINKING",
        )
        Spacer(Modifier.height(6.dp))

        NowPlayingBlock(playback)
        Spacer(Modifier.height(8.dp))

        AsciiProgressBar(
            progress = playback.progress,
            positionMs = playback.positionMs,
            durationMs = playback.durationMs,
            onSeek = viewModel::seekTo,
        )
        Spacer(Modifier.height(6.dp))

        StatusLine(playback, sessionId)
        Spacer(Modifier.height(8.dp))

        ControlBlock(playback = playback, viewModel = viewModel)
        Spacer(Modifier.height(10.dp))

        SectionHeader(
            title = "LIBRARY",
            trailing = when {
                library.loading -> "SCANNING"
                else -> "${library.tracks.size} TRACKS"
            },
        )
        LibraryToolbar(library = library, permission = permission, viewModel = viewModel)

        Box(Modifier.weight(1f).fillMaxWidth()) {
            LibraryBody(
                library = library,
                playback = playback,
                permission = permission,
                viewModel = viewModel,
            )
        }
    }
}

@Composable
private fun NowPlayingBlock(playback: PlaybackUiState) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "NOW PLAYING",
                style = TerminalType.small.copy(color = TerminalGreenDim),
                maxLines = 1,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = if (playback.queueSize > 0) {
                    String.format(
                        Locale.US,
                        "%02d/%02d",
                        (playback.queueIndex + 1).coerceAtLeast(0),
                        playback.queueSize,
                    )
                } else {
                    "--/--"
                },
                style = TerminalType.small.copy(color = TerminalGreenDim),
                maxLines = 1,
            )
        }
        Spacer(Modifier.height(2.dp))
        MarqueeText(
            text = when {
                !playback.hasTrack -> "NO TRACK LOADED // SELECT A TRACK FROM THE LIBRARY BELOW"
                playback.artist.isBlank() -> playback.title
                else -> "${playback.artist.uppercase()} // ${playback.title}"
            },
            style = TerminalType.marquee.copy(
                color = if (playback.hasTrack) TerminalBright else TerminalGreenDim,
            ),
            active = playback.hasTrack,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = "ALBUM: " + playback.album.ifBlank { "UNKNOWN" },
            style = TerminalType.small.copy(color = TerminalGray),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun StatusLine(playback: PlaybackUiState, sessionId: Int) {
    Text(
        text = "ST:${playback.stateLabel} SHF:${if (playback.shuffle) "ON" else "OFF"} " +
            "RPT:${playback.repeatLabel} SES:${Ascii.session(sessionId)}",
        style = TerminalType.small.copy(color = TerminalGreenDim),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun ControlBlock(playback: PlaybackUiState, viewModel: PlayerViewModel) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsciiButton("[ << ]", onClick = viewModel::previous)
            AsciiButton(
                label = if (playback.isPlaying) "[ || ]" else "[ > ]",
                onClick = viewModel::togglePlayPause,
                active = playback.isPlaying,
            )
            AsciiButton("[ >> ]", onClick = viewModel::next)
            Spacer(Modifier.weight(1f))
            if (playback.isBuffering) {
                Text(
                    text = "BUFFERING...",
                    style = TerminalType.small.copy(color = TerminalAmber),
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsciiButton("[-10s]", onClick = { viewModel.seekBy(-10_000L) })
            AsciiButton("[+30s]", onClick = { viewModel.seekBy(30_000L) })
            Spacer(Modifier.width(2.dp))
            AsciiButton(
                label = "[ SHUF ]",
                onClick = viewModel::toggleShuffle,
                active = playback.shuffle,
                color = TerminalGreenSoft,
            )
            AsciiButton(
                label = "[ RPT ]",
                onClick = viewModel::cycleRepeat,
                active = playback.repeatMode != Player.REPEAT_MODE_OFF,
                color = TerminalGreenSoft,
            )
        }
    }
}

@Composable
private fun LibraryToolbar(
    library: LibraryUiState,
    permission: PermissionState,
    viewModel: PlayerViewModel,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsciiButton(
            label = "[ RESCAN ]",
            onClick = viewModel::refreshLibrary,
            enabled = permission.granted,
        )
        AsciiButton(
            label = "[ SORT:${library.sort.label} ]",
            onClick = viewModel::cycleSort,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = if (library.loading) "SCANNING" else Ascii.clock(library.tracks.sumOf { it.durationMs }),
            style = TerminalType.small.copy(color = TerminalGray),
            maxLines = 1,
        )
    }
}

@Composable
private fun LibraryBody(
    library: LibraryUiState,
    playback: PlaybackUiState,
    permission: PermissionState,
    viewModel: PlayerViewModel,
) {
    when {
        !permission.granted -> PermissionBlock(permission)
        !library.error.isNullOrBlank() -> StateText("! ${library.error}", TerminalRed)
        library.tracks.isEmpty() && (library.loading || !library.loaded) ->
            StateText("> SCANNING MEDIASTORE...")
        library.tracks.isEmpty() -> StateText(
            "> NO AUDIO FILES FOUND // ADD MUSIC TO THE DEVICE AND PRESS [ RESCAN ]",
        )
        else -> TrackList(
            tracks = library.tracks,
            currentMediaId = playback.mediaId,
            onPlay = viewModel::playTrack,
        )
    }
}

@Composable
private fun PermissionBlock(permission: PermissionState) {
    val context = LocalContext.current
    InfoPanel(
        code = "E_LIBRARY_ACCESS",
        message = "READ_MEDIA_AUDIO is required to index local audio files. " +
            "TerminalPlayer works fully offline and never uploads media.",
        actions = {
            AsciiButton("[ GRANT ]", onClick = permission.request)
            AsciiButton("[ SETTINGS ]", onClick = { context.openAppSettings() })
        },
    )
}

@Composable
private fun StateText(text: String, color: Color = TerminalGreenDim) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(text = text, style = TerminalType.small.copy(color = color))
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "> ",
                style = TerminalType.small.copy(color = TerminalGreenSoft),
            )
            BlinkingCursor()
        }
    }
}

@Composable
private fun TrackList(
    tracks: List<Track>,
    currentMediaId: String,
    onPlay: (Track) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize()) {
        itemsIndexed(items = tracks, key = { _, track -> track.id }) { index, track ->
            TrackRow(
                index = index + 1,
                track = track,
                selected = track.id.toString() == currentMediaId,
                onPlay = { onPlay(track) },
            )
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun TrackRow(
    index: Int,
    track: Track,
    selected: Boolean,
    onPlay: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val background = if (selected) TerminalGreen else Color.Transparent
    val primary = if (selected) TerminalBlack else TerminalGreen
    val secondary = if (selected) TerminalBlack else TerminalGreenDim

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onPlay,
            )
            .padding(vertical = 4.dp, horizontal = 2.dp),
    ) {
        Text(
            text = "${if (selected) '>' else ' '} ${index.toString().padStart(2, '0')} ${track.title}",
            style = TerminalType.body.copy(color = primary),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "     ${track.artistLabel.uppercase()} // ${Ascii.clock(track.durationMs)}",
            style = TerminalType.small.copy(color = secondary),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

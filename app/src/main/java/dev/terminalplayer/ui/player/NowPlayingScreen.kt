package dev.terminalplayer.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import dev.terminalplayer.core.Ascii
import dev.terminalplayer.playback.PlaybackUiState
import dev.terminalplayer.ui.components.AsciiArtwork
import dev.terminalplayer.ui.components.GlyphButton
import dev.terminalplayer.ui.components.GlyphPill
import dev.terminalplayer.ui.components.MarqueeText
import dev.terminalplayer.ui.components.TerminalCard
import dev.terminalplayer.ui.theme.Accent
import dev.terminalplayer.ui.theme.Ink
import dev.terminalplayer.ui.theme.Stroke
import dev.terminalplayer.ui.theme.TextMuted
import dev.terminalplayer.ui.theme.TextPrimary
import dev.terminalplayer.ui.theme.TextSecondary
import dev.terminalplayer.ui.theme.TerminalType

/**
 * Full-screen "now playing": large ASCII cover, marquee title, seek bar and transport.
 *
 * It shares the same `PlayerViewModel` as the library, so opening or closing it never touches the
 * playback session. The mini player and the bottom navigation are hidden by `MainScreen` while
 * this screen is on top.
 */
@Composable
fun NowPlayingScreen(
    viewModel: PlayerViewModel,
    onBack: () -> Unit,
) {
    val playback by viewModel.playback.collectAsStateWithLifecycle()
    val catalog by viewModel.catalog.collectAsStateWithLifecycle()
    val sessionId by viewModel.audioSessionId.collectAsStateWithLifecycle()

    val currentTrack = catalog.tracks.firstOrNull { it.id.toString() == playback.mediaId }
    val favorite = playback.mediaId.toLongOrNull()?.let { it in catalog.favorites } ?: false

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        NowPlayingHeader(
            queueLabel = if (playback.queueSize > 0) {
                "${playback.queueIndex + 1} / ${playback.queueSize}"
            } else {
                "-- / --"
            },
            favorite = favorite,
            onBack = onBack,
            onToggleFavorite = { currentTrack?.let(viewModel::toggleFavorite) },
        )

        Spacer(Modifier.height(16.dp))
        TerminalCard(
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
            shape = RoundedCornerShape(24.dp),
        ) {
            AsciiArtwork(
                seed = currentTrack?.id ?: playback.mediaId.toLongOrNull() ?: 0L,
                modifier = Modifier.fillMaxSize().padding(18.dp),
                columns = 14,
                rows = 14,
            )
            Text(
                text = "ASCII COVER // ${currentTrack?.let { Ascii.clock(it.durationMs) } ?: "--:--"}",
                style = TerminalType.label.copy(fontSize = 10.sp),
                color = TextMuted,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(14.dp),
                maxLines = 1,
            )
        }

        Spacer(Modifier.height(22.dp))
        MarqueeText(
            text = playback.title.ifBlank { "NOTHING PLAYING" },
            style = TerminalType.headline,
            modifier = Modifier.fillMaxWidth(),
            active = playback.hasTrack,
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = playback.artist.ifBlank { "UNKNOWN ARTIST" },
            style = TerminalType.body,
            color = TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = "ALBUM // ${playback.album.ifBlank { "UNKNOWN" }}",
            style = TerminalType.label,
            color = TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(Modifier.height(18.dp))
        SeekBar(
            progress = playback.progress,
            onSeek = viewModel::seekTo,
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = Ascii.clock(playback.positionMs),
                style = TerminalType.label,
                color = TextSecondary,
                maxLines = 1,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = Ascii.clock(playback.durationMs),
                style = TerminalType.label,
                color = TextMuted,
                maxLines = 1,
            )
        }

        Spacer(Modifier.height(16.dp))
        Transport(
            playback = playback,
            onShuffle = viewModel::toggleShuffle,
            onPrevious = viewModel::previous,
            onPlayPause = viewModel::togglePlayPause,
            onNext = viewModel::next,
            onRepeat = viewModel::cycleRepeat,
        )

        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            GlyphPill("[-10s]", onClick = { viewModel.seekBy(-10_000L) })
            GlyphPill("[+30s]", onClick = { viewModel.seekBy(30_000L) })
        }

        Spacer(Modifier.height(20.dp))
        Text(
            text = "ST:${playback.stateLabel}  SHF:${if (playback.shuffle) "ON" else "OFF"}  " +
                "RPT:${playback.repeatLabel}",
            style = TerminalType.label.copy(fontSize = 10.sp),
            color = TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "SES:${Ascii.session(sessionId)} // MEDIA3",
            style = TerminalType.label.copy(fontSize = 10.sp),
            color = TextMuted,
            maxLines = 1,
        )
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun NowPlayingHeader(
    queueLabel: String,
    favorite: Boolean,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    Box(Modifier.fillMaxWidth()) {
        GlyphButton(
            glyph = "<",
            onClick = onBack,
            modifier = Modifier.align(Alignment.CenterStart),
            size = 40.dp,
            shape = CircleShape,
        )
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "NOW PLAYING",
                style = TerminalType.label,
                color = TextMuted,
                maxLines = 1,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = queueLabel,
                style = TerminalType.label,
                color = TextSecondary,
                maxLines = 1,
            )
        }
        GlyphButton(
            glyph = "[*]",
            onClick = onToggleFavorite,
            modifier = Modifier.align(Alignment.CenterEnd),
            size = 40.dp,
            shape = CircleShape,
            active = favorite,
        )
    }
}

@Composable
private fun Transport(
    playback: PlaybackUiState,
    onShuffle: () -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onRepeat: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphButton(
            glyph = "S",
            onClick = onShuffle,
            size = 42.dp,
            shape = CircleShape,
            active = playback.shuffle,
        )
        GlyphButton(
            glyph = "<<",
            onClick = onPrevious,
            size = 52.dp,
            shape = CircleShape,
        )
        PlayPauseButton(
            playing = playback.isPlaying,
            onClick = onPlayPause,
        )
        GlyphButton(
            glyph = ">>",
            onClick = onNext,
            size = 52.dp,
            shape = CircleShape,
        )
        GlyphButton(
            glyph = when (playback.repeatMode) {
                Player.REPEAT_MODE_ONE -> "1"
                Player.REPEAT_MODE_ALL -> "A"
                else -> "R"
            },
            onClick = onRepeat,
            size = 42.dp,
            shape = CircleShape,
            active = playback.repeatMode != Player.REPEAT_MODE_OFF,
        )
    }
}

@Composable
private fun PlayPauseButton(playing: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(70.dp)
            .clip(CircleShape)
            .background(TextPrimary)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (playing) "||" else ">",
            style = TerminalType.glyphLarge.copy(
                fontSize = 22.sp,
                color = Ink,
            ),
            maxLines = 1,
        )
    }
}

/** Thin, rounded seek bar: tap anywhere or drag to scrub. */
@Composable
private fun SeekBar(progress: Float, onSeek: (Float) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    if (size.width > 0) {
                        onSeek((offset.x / size.width.toFloat()).coerceIn(0f, 1f))
                    }
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    if (size.width > 0) {
                        onSeek((change.position.x / size.width.toFloat()).coerceIn(0f, 1f))
                    }
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val centerY = size.height / 2f
            val trackHeight = 3.dp.toPx()
            val radius = trackHeight / 2f
            drawRoundRect(
                color = Stroke,
                topLeft = Offset(0f, centerY - radius),
                size = Size(size.width, trackHeight),
                cornerRadius = CornerRadius(radius, radius),
            )
            if (progress > 0f) {
                drawRoundRect(
                    color = Accent,
                    topLeft = Offset(0f, centerY - radius),
                    size = Size(size.width * progress, trackHeight),
                    cornerRadius = CornerRadius(radius, radius),
                )
            }
            val thumbRadius = 6.dp.toPx()
            val maxThumbX = (size.width - thumbRadius).coerceAtLeast(thumbRadius)
            val thumbX = (size.width * progress).coerceIn(thumbRadius, maxThumbX)
            drawCircle(
                color = TextPrimary,
                radius = thumbRadius,
                center = Offset(thumbX, centerY),
            )
        }
    }
}

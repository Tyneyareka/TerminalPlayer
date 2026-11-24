package dev.terminalplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.terminalplayer.playback.PlaybackUiState
import dev.terminalplayer.ui.theme.Accent
import dev.terminalplayer.ui.theme.StrokeSoft
import dev.terminalplayer.ui.theme.TextSecondary
import dev.terminalplayer.ui.theme.TerminalType

/**
 * The floating mini player above the bottom navigation.
 *
 * It is a real `TerminalCard` with its own progress hairline; tapping the body expands the full
 * player, while the two circular glyph buttons keep playback controllable without leaving the
 * library.
 */
@Composable
fun MiniPlayer(
    playback: PlaybackUiState,
    onOpen: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!playback.hasTrack) return

    val interactionSource = remember { MutableInteractionSource() }
    TerminalCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
    ) {
        Box(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        role = Role.Button,
                        onClick = onOpen,
                    )
                    .padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsciiThumbnail(
                    seed = playback.mediaId.toLongOrNull() ?: playback.title.hashCode().toLong(),
                    size = 42.dp,
                    columns = 7,
                    rows = 7,
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    MarqueeText(
                        text = playback.title.ifBlank { "UNKNOWN TRACK" },
                        style = TerminalType.marquee,
                        modifier = Modifier.fillMaxWidth(),
                        active = true,
                    )
                    Text(
                        text = playback.artist.ifBlank { "UNKNOWN ARTIST" },
                        style = TerminalType.small,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(8.dp))
                GlyphButton(
                    glyph = if (playback.isPlaying) "||" else ">",
                    onClick = onTogglePlayPause,
                    size = 38.dp,
                    shape = CircleShape,
                    active = playback.isPlaying,
                )
                Spacer(Modifier.width(6.dp))
                GlyphButton(
                    glyph = ">>",
                    onClick = onNext,
                    size = 38.dp,
                    shape = CircleShape,
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(StrokeSoft),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(playback.progress)
                        .height(2.dp)
                        .background(Accent),
                )
            }
        }
    }
}

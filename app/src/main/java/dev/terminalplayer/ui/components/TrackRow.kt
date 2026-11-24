package dev.terminalplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.terminalplayer.core.Ascii
import dev.terminalplayer.data.Track
import dev.terminalplayer.ui.theme.Accent
import dev.terminalplayer.ui.theme.AccentBright
import dev.terminalplayer.ui.theme.TextMuted
import dev.terminalplayer.ui.theme.TextPrimary
import dev.terminalplayer.ui.theme.TextSecondary
import dev.terminalplayer.ui.theme.TerminalType

/**
 * One library row: ASCII cover, title, artist, duration, chevron.
 *
 * Tap plays the track; a long press is the shortcut for adding it to favourites (the
 * `PLAYLISTS` category). Ripple is disabled so the row keeps the flat terminal look.
 */
@Composable
fun TrackRow(
    track: Track,
    selected: Boolean,
    favorite: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    thumbnailSize: Dp = 54.dp,
    onLongClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(if (selected) Accent.copy(alpha = 0.07f) else Color.Transparent)
            .pointerInput(track.id, onLongClick) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongClick?.invoke() },
                )
            }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsciiThumbnail(seed = track.id, size = thumbnailSize)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = TerminalType.bodyStrong,
                color = if (selected) AccentBright else TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = track.artistLabel,
                    style = TerminalType.small,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (favorite) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "[*]",
                        style = TerminalType.label.copy(fontSize = 10.sp),
                        color = Accent,
                        maxLines = 1,
                    )
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = Ascii.clock(track.durationMs),
            style = TerminalType.small,
            color = TextMuted,
            maxLines = 1,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = ">",
            style = TerminalType.glyph.copy(fontSize = 14.sp),
            color = if (selected) Accent else TextMuted,
            maxLines = 1,
        )
    }
}

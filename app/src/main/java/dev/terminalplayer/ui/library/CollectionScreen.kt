package dev.terminalplayer.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.terminalplayer.core.Ascii
import dev.terminalplayer.data.Track
import dev.terminalplayer.playback.PlaybackUiState
import dev.terminalplayer.ui.components.AsciiButton
import dev.terminalplayer.ui.components.GlyphButton
import dev.terminalplayer.ui.components.Hairline
import dev.terminalplayer.ui.components.InfoPanel
import dev.terminalplayer.ui.components.MetaLabel
import dev.terminalplayer.ui.components.PermissionState
import dev.terminalplayer.ui.components.TerminalCard
import dev.terminalplayer.ui.components.TrackRow
import dev.terminalplayer.ui.components.openAppSettings
import dev.terminalplayer.ui.player.CatalogUiState
import dev.terminalplayer.ui.player.PlayerViewModel
import dev.terminalplayer.ui.player.TrackGroup
import dev.terminalplayer.ui.theme.InkRaised
import dev.terminalplayer.ui.theme.StrokeSoft
import dev.terminalplayer.ui.theme.TextMuted
import dev.terminalplayer.ui.theme.TextPrimary
import dev.terminalplayer.ui.theme.TextSecondary
import dev.terminalplayer.ui.theme.TerminalType
import androidx.compose.ui.platform.LocalContext

/**
 * One collection screen, three modes:
 *
 *  * `kind = null` - every track in the library;
 *  * `kind` + no group selected - the groups (albums / artists / folders / favourites);
 *  * `kind` + group selected - the tracks of that group.
 *
 * The selected group lives in the navigation state of `MainScreen`, so system back behaves
 * predictably: first it leaves the group, then the collection.
 */
@Composable
fun CollectionScreen(
    kind: CollectionKind?,
    selectedGroupKey: String?,
    onSelectGroup: (String?) -> Unit,
    onBack: () -> Unit,
    onOpenPlayer: () -> Unit,
    viewModel: PlayerViewModel,
    permission: PermissionState,
) {
    val catalog by viewModel.catalog.collectAsStateWithLifecycle()
    val playback by viewModel.playback.collectAsStateWithLifecycle()

    val groups = remember(kind, catalog) { groupsFor(kind, catalog) }
    val activeGroup = groups.firstOrNull { it.key == selectedGroupKey }

    val tracks: List<Track> = when {
        kind == null -> catalog.tracks
        activeGroup != null -> activeGroup.tracks
        else -> emptyList()
    }

    val title = activeGroup?.title ?: kind?.title ?: "ALL TRACKS"
    val subtitle = when {
        kind == null -> "${tracks.size} TRACKS // ${Ascii.clock(tracks.sumOf { it.durationMs })}"
        activeGroup != null -> "${tracks.size} TRACKS // ${Ascii.clock(activeGroup.durationMs)}"
        else -> "${groups.size} GROUPS"
    }

    BackHandler(enabled = activeGroup != null) { onSelectGroup(null) }

    Column(Modifier.fillMaxSize()) {
        CollectionHeader(
            title = title,
            subtitle = subtitle,
            onBack = {
                if (activeGroup != null) onSelectGroup(null) else onBack()
            },
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                !permission.granted -> LibraryPermission(permission)
                kind == null || activeGroup != null -> TrackList(
                    tracks = tracks,
                    playback = playback,
                    favorites = catalog.favorites,
                    emptyTitle = if (kind == CollectionKind.PLAYLISTS) "NO FAVORITES YET" else "NO TRACKS",
                    emptyMessage = when (kind) {
                        CollectionKind.PLAYLISTS ->
                            "Long-press any track to save it here."
                        else -> "Scan the library again from the home screen."
                    },
                    onPlay = { track ->
                        viewModel.playTrack(track, tracks)
                        onOpenPlayer()
                    },
                    onToggleFavorite = viewModel::toggleFavorite,
                )
                else -> GroupList(
                    kind = kind,
                    groups = groups,
                    onOpenGroup = { onSelectGroup(it.key) },
                )
            }
        }
    }
}

@Composable
private fun CollectionHeader(title: String, subtitle: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphButton(
            glyph = "<",
            onClick = onBack,
            size = 40.dp,
            shape = CircleShape,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = TerminalType.title,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = TerminalType.label,
                color = TextMuted,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun GroupList(
    kind: CollectionKind,
    groups: List<TrackGroup>,
    onOpenGroup: (TrackGroup) -> Unit,
) {
    if (groups.isEmpty()) {
        EmptyState("NO ${kind.title} INDEXED", "Scan the library again from the home screen.")
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(items = groups, key = { _, group -> group.key }) { _, group ->
            GroupCard(
                glyph = kind.glyph,
                group = group,
                onClick = { onOpenGroup(group) },
            )
        }
    }
}

@Composable
private fun GroupCard(glyph: String, group: TrackGroup, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    TerminalCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .background(InkRaised, RoundedCornerShape(12.dp))
                    .border(1.dp, StrokeSoft, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = glyph,
                    style = TerminalType.glyph.copy(color = TextSecondary),
                    maxLines = 1,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = group.title,
                    style = TerminalType.bodyStrong,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = group.subtitle,
                    style = TerminalType.small,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = Ascii.clock(group.durationMs),
                style = TerminalType.small,
                color = TextMuted,
                maxLines = 1,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = ">",
                style = TerminalType.glyph.copy(fontSize = 14.sp),
                color = TextMuted,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun TrackList(
    tracks: List<Track>,
    playback: PlaybackUiState,
    favorites: Set<Long>,
    emptyTitle: String,
    emptyMessage: String,
    onPlay: (Track) -> Unit,
    onToggleFavorite: (Track) -> Unit,
) {
    if (tracks.isEmpty()) {
        EmptyState(emptyTitle, emptyMessage)
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
    ) {
        item(key = "hint") {
            MetaLabel(
                text = "LONG PRESS A TRACK TO SAVE IT TO PLAYLISTS",
                modifier = Modifier.padding(vertical = 8.dp),
                color = TextMuted,
            )
        }
        itemsIndexed(items = tracks, key = { _, track -> track.id }) { index, track ->
            Column(Modifier.fillMaxWidth()) {
                if (index > 0) {
                    Hairline(Modifier.padding(start = 66.dp))
                }
                TrackRow(
                    track = track,
                    selected = track.id.toString() == playback.mediaId,
                    favorite = track.id in favorites,
                    onClick = { onPlay(track) },
                    onLongClick = { onToggleFavorite(track) },
                )
            }
        }
        item(key = "tail") {
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun EmptyState(title: String, message: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 24.dp),
    ) {
        Text(
            text = title,
            style = TerminalType.bodyStrong,
            color = TextPrimary,
            maxLines = 1,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = message,
            style = TerminalType.small,
            color = TextSecondary,
        )
    }
}

@Composable
private fun LibraryPermission(permission: PermissionState) {
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

private fun groupsFor(kind: CollectionKind?, catalog: CatalogUiState): List<TrackGroup> =
    when (kind) {
        CollectionKind.PLAYLISTS -> listOf(
            TrackGroup(
                key = "FAVORITES",
                title = "FAVORITES",
                subtitle = "${catalog.favoriteTracks.size} TRACKS",
                tracks = catalog.favoriteTracks,
            ),
        )
        CollectionKind.ALBUMS -> catalog.albums
        CollectionKind.ARTISTS -> catalog.artists
        CollectionKind.FOLDERS -> catalog.folders
        null -> emptyList()
    }

package dev.terminalplayer.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.terminalplayer.core.Ascii
import dev.terminalplayer.data.Track
import dev.terminalplayer.i18n.AppStrings
import dev.terminalplayer.i18n.LocalStrings
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
import dev.terminalplayer.ui.motion.LocalMotionEnabled
import dev.terminalplayer.ui.motion.animatedPressScale
import dev.terminalplayer.ui.motion.scaleLayer
import dev.terminalplayer.ui.player.CatalogUiState
import dev.terminalplayer.ui.player.PlayerViewModel
import dev.terminalplayer.ui.player.TrackGroup
import dev.terminalplayer.ui.theme.InkRaised
import dev.terminalplayer.ui.theme.StrokeSoft
import dev.terminalplayer.ui.theme.TextMuted
import dev.terminalplayer.ui.theme.TextPrimary
import dev.terminalplayer.ui.theme.TextSecondary
import dev.terminalplayer.ui.theme.TerminalType

/**
 * One collection screen, three modes:
 *
 *  * `kind = null` - every track in the library;
 *  * `kind` + no group selected - the groups (albums / artists / folders / favourites);
 *  * `kind` + group selected - the tracks of that group.
 *
 * The selected group lives in the navigation state of `MainScreen`, so system back behaves
 * predictably: first it leaves the group, then the collection. Opening a group slides the new list
 * in from the side while the old one leaves, and the header title crossfades with it.
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
    val strings = LocalStrings.current
    val motion = LocalMotionEnabled.current
    val catalog by viewModel.catalog.collectAsStateWithLifecycle()
    val playback by viewModel.playback.collectAsStateWithLifecycle()

    val groups = remember(kind, catalog, strings) { groupsFor(kind, catalog, strings) }
    val activeGroup = groups.firstOrNull { it.key == selectedGroupKey }

    val title = activeGroup?.let { groupTitle(kind, it, strings) }
        ?: kind?.localizedTitle(strings)
        ?: strings.allTracks
    val subtitle = when {
        kind == null ->
            "${strings.tracksCount(catalog.tracks.size)} // " +
                Ascii.clock(catalog.tracks.sumOf { it.durationMs })
        activeGroup != null ->
            "${strings.tracksCount(activeGroup.tracks.size)} // ${Ascii.clock(activeGroup.durationMs)}"
        else -> strings.groupsCount(groups.size)
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
            AnimatedContent(
                targetState = selectedGroupKey,
                transitionSpec = {
                    if (!motion) {
                        fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                    } else {
                        val direction = if (targetState != null) 1 else -1
                        (
                            fadeIn(tween(200)) +
                                slideInHorizontally(tween(260)) { width -> direction * width / 8 }
                        ) togetherWith (
                            fadeOut(tween(140)) +
                                slideOutHorizontally(tween(220)) { width -> -direction * width / 12 }
                        )
                    }
                },
                label = "collectionBody",
                modifier = Modifier.fillMaxSize(),
            ) { groupKey ->
                val currentKind = kind
                val group = groups.firstOrNull { it.key == groupKey }
                val trackMode = currentKind == null || group != null
                val shownTracks: List<Track> = when {
                    currentKind == null -> catalog.tracks
                    group != null -> group.tracks
                    else -> emptyList()
                }
                when {
                    !permission.granted -> LibraryPermission(permission)
                    trackMode -> TrackList(
                        tracks = shownTracks,
                        playback = playback,
                        favorites = catalog.favorites,
                        emptyTitle = if (currentKind == CollectionKind.PLAYLISTS) {
                            strings.noFavoritesTitle
                        } else {
                            strings.noTracksTitle
                        },
                        emptyMessage = if (currentKind == CollectionKind.PLAYLISTS) {
                            strings.noFavoritesBody
                        } else {
                            strings.noTracksBody
                        },
                        onPlay = { track ->
                            viewModel.playTrack(track, shownTracks)
                            onOpenPlayer()
                        },
                        onToggleFavorite = viewModel::toggleFavorite,
                    )
                    currentKind != null -> GroupList(
                        kind = currentKind,
                        groups = groups,
                        onOpenGroup = { onSelectGroup(it.key) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CollectionHeader(title: String, subtitle: String, onBack: () -> Unit) {
    val motion = LocalMotionEnabled.current
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
            Crossfade(
                targetState = title,
                animationSpec = tween(if (motion) 200 else 0),
                label = "collectionTitle",
            ) { text ->
                Text(
                    text = text,
                    style = TerminalType.title,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
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
    val strings = LocalStrings.current
    if (groups.isEmpty()) {
        EmptyState(
            title = strings.emptyKind(kind.localizedTitle(strings)),
            message = strings.scanAgainBody,
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(items = groups, key = { _, group -> group.key }) { _, group ->
            GroupCard(
                kind = kind,
                group = group,
                onClick = { onOpenGroup(group) },
            )
        }
    }
}

@Composable
private fun GroupCard(kind: CollectionKind, group: TrackGroup, onClick: () -> Unit) {
    val strings = LocalStrings.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val press = animatedPressScale(pressed, pressedScale = 0.98f)

    TerminalCard(
        modifier = Modifier
            .fillMaxWidth()
            .scaleLayer(press)
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
                    text = kind.glyph,
                    style = TerminalType.glyph.copy(color = TextSecondary),
                    maxLines = 1,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = groupTitle(kind, group, strings),
                    style = TerminalType.bodyStrong,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = group.subtitle.ifBlank { strings.tracksCount(group.tracks.size) },
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
    val strings = LocalStrings.current
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
                text = strings.longPressHint,
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
    val strings = LocalStrings.current
    val context = LocalContext.current
    InfoPanel(
        code = "E_LIBRARY_ACCESS",
        message = strings.libraryPermissionMessage,
        actions = {
            AsciiButton(strings.grantAction, onClick = permission.request)
            AsciiButton(strings.settingsAction, onClick = { context.openAppSettings() })
        },
    )
}

private fun groupsFor(
    kind: CollectionKind?,
    catalog: CatalogUiState,
    strings: AppStrings,
): List<TrackGroup> = when (kind) {
    CollectionKind.PLAYLISTS -> listOf(
        TrackGroup(
            key = "FAVORITES",
            title = strings.favorites,
            subtitle = "",
            tracks = catalog.favoriteTracks,
        ),
    )
    CollectionKind.ALBUMS -> catalog.albums
    CollectionKind.ARTISTS -> catalog.artists
    CollectionKind.FOLDERS -> catalog.folders
    null -> emptyList()
}

private fun groupTitle(kind: CollectionKind?, group: TrackGroup, strings: AppStrings): String =
    when {
        !group.unknownTitle -> group.title
        kind == CollectionKind.ARTISTS -> strings.unknownArtist
        kind == CollectionKind.ALBUMS -> strings.unknownAlbum
        else -> group.title
    }

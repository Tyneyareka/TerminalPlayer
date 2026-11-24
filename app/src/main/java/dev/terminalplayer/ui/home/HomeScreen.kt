package dev.terminalplayer.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.terminalplayer.data.Track
import dev.terminalplayer.playback.PlaybackUiState
import dev.terminalplayer.ui.components.BlinkingCursor
import dev.terminalplayer.ui.components.DashedRingButton
import dev.terminalplayer.ui.components.DotMatrix
import dev.terminalplayer.ui.components.GlyphButton
import dev.terminalplayer.ui.components.GlyphPill
import dev.terminalplayer.ui.components.Hairline
import dev.terminalplayer.ui.components.MetaLabel
import dev.terminalplayer.ui.components.PermissionState
import dev.terminalplayer.ui.components.SectionTitle
import dev.terminalplayer.ui.components.TerminalCard
import dev.terminalplayer.ui.components.TrackRow
import dev.terminalplayer.ui.components.openAppSettings
import dev.terminalplayer.ui.library.CollectionKind
import dev.terminalplayer.ui.player.CatalogUiState
import dev.terminalplayer.ui.player.LibrarySort
import dev.terminalplayer.ui.player.PlayerViewModel
import dev.terminalplayer.ui.theme.Danger
import dev.terminalplayer.ui.theme.MixEnd
import dev.terminalplayer.ui.theme.MixStart
import dev.terminalplayer.ui.theme.TextMuted
import dev.terminalplayer.ui.theme.TextPrimary
import dev.terminalplayer.ui.theme.TextSecondary
import dev.terminalplayer.ui.theme.TerminalType

/**
 * Library home: search chrome, three-track preview, the LOCAL MIX card and the CATEGORIES grid.
 *
 * Mirrors the reference mock-up: rounded charcoal cards, a single blue accent, ASCII covers and
 * dot-matrix decorations instead of images or vector icons.
 */
@Composable
fun HomeScreen(
    viewModel: PlayerViewModel,
    permission: PermissionState,
    onOpenPlayer: () -> Unit,
    onOpenCollection: (CollectionKind?) -> Unit,
) {
    val catalog by viewModel.catalog.collectAsStateWithLifecycle()
    val playback by viewModel.playback.collectAsStateWithLifecycle()
    var sortOpen by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(10.dp))
        SearchRow(
            sortOpen = sortOpen,
            onToggleSort = { sortOpen = !sortOpen },
        )
        if (sortOpen) {
            Spacer(Modifier.height(10.dp))
            SortRow(
                selected = catalog.sort,
                onSelect = viewModel::selectSort,
            )
        }

        Spacer(Modifier.height(22.dp))
        SectionTitle(
            title = "MY TRACKS",
            trailing = {
                GlyphButton(
                    glyph = ">",
                    onClick = { onOpenCollection(null) },
                    size = 38.dp,
                    shape = RoundedCornerShape(12.dp),
                )
            },
        )
        Spacer(Modifier.height(10.dp))
        MyTracksCard(
            catalog = catalog,
            playback = playback,
            permission = permission,
            onPlay = { track ->
                viewModel.playTrack(track, catalog.tracks)
                onOpenPlayer()
            },
            onToggleFavorite = viewModel::toggleFavorite,
            onRescan = viewModel::refreshLibrary,
        )

        Spacer(Modifier.height(16.dp))
        LocalMixCard(
            enabled = catalog.tracks.isNotEmpty(),
            onClick = {
                viewModel.playMix()
                onOpenPlayer()
            },
        )

        Spacer(Modifier.height(24.dp))
        Text(
            text = "CATEGORIES",
            style = TerminalType.title,
            color = TextPrimary,
            maxLines = 1,
        )
        Spacer(Modifier.height(12.dp))
        CategoryGrid(onOpen = onOpenCollection)
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun SearchRow(sortOpen: Boolean, onToggleSort: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TerminalCard(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(26.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "[/]",
                    style = TerminalType.glyph.copy(color = TextMuted),
                    maxLines = 1,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Search local library",
                    style = TerminalType.body.copy(color = TextMuted),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        GlyphButton(
            glyph = "[=]",
            onClick = onToggleSort,
            size = 52.dp,
            shape = RoundedCornerShape(16.dp),
            active = sortOpen,
        )
    }
}

@Composable
private fun SortRow(selected: LibrarySort, onSelect: (LibrarySort) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MetaLabel(text = "SORT", color = TextMuted)
        LibrarySort.entries.forEach { sort ->
            GlyphPill(
                text = sort.label,
                onClick = { onSelect(sort) },
                selected = sort == selected,
            )
        }
    }
}

@Composable
private fun MyTracksCard(
    catalog: CatalogUiState,
    playback: PlaybackUiState,
    permission: PermissionState,
    onPlay: (Track) -> Unit,
    onToggleFavorite: (Track) -> Unit,
    onRescan: () -> Unit,
) {
    TerminalCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
            when {
                !permission.granted -> PermissionPrompt(permission)
                !catalog.error.isNullOrBlank() -> LibraryMessage(
                    title = "LIBRARY ERROR",
                    message = catalog.error.orEmpty(),
                    tone = Danger,
                    action = "[ RESCAN ]",
                    onAction = onRescan,
                )
                catalog.tracks.isEmpty() && (catalog.loading || !catalog.loaded) -> LibraryMessage(
                    title = "SCANNING MEDIASTORE...",
                    message = "This can take a few seconds on large libraries.",
                    showCursor = true,
                )
                catalog.tracks.isEmpty() -> LibraryMessage(
                    title = "NO AUDIO FILES FOUND",
                    message = "Add music to the device, then scan the library again.",
                    action = "[ RESCAN ]",
                    onAction = onRescan,
                )
                else -> {
                    val preview = catalog.tracks.take(3)
                    preview.forEachIndexed { index, track ->
                        TrackRow(
                            track = track,
                            selected = track.id.toString() == playback.mediaId,
                            favorite = track.id in catalog.favorites,
                            onClick = { onPlay(track) },
                            onLongClick = { onToggleFavorite(track) },
                        )
                        if (index != preview.lastIndex) {
                            Hairline(Modifier.padding(start = 78.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionPrompt(permission: PermissionState) {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Text(
            text = "LIBRARY ACCESS REQUIRED",
            style = TerminalType.bodyStrong,
            color = TextPrimary,
            maxLines = 1,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "TerminalPlayer indexes local audio through MediaStore. " +
                "Files never leave the device.",
            style = TerminalType.small,
            color = TextSecondary,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlyphPill(
                text = "[ GRANT ]",
                onClick = permission.request,
                selected = true,
            )
            GlyphPill(
                text = "[ SETTINGS ]",
                onClick = { context.openAppSettings() },
            )
        }
    }
}

@Composable
private fun LibraryMessage(
    title: String,
    message: String,
    tone: Color = TextSecondary,
    action: String? = null,
    onAction: (() -> Unit)? = null,
    showCursor: Boolean = false,
) {
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = TerminalType.bodyStrong,
                color = if (tone == TextSecondary) TextPrimary else tone,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (showCursor) {
                Spacer(Modifier.width(2.dp))
                BlinkingCursor(color = TextSecondary)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = message,
            style = TerminalType.small,
            color = TextSecondary,
        )
        if (action != null && onAction != null) {
            Spacer(Modifier.height(12.dp))
            GlyphPill(text = action, onClick = onAction, selected = true)
        }
    }
}

@Composable
private fun LocalMixCard(enabled: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.5f)
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(MixStart, MixEnd)))
            .border(1.dp, Color.White.copy(alpha = 0.06f), shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(horizontal = 18.dp, vertical = 18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "LOCAL MIX",
                    style = TerminalType.huge.copy(fontSize = 21.sp, color = TextPrimary),
                    maxLines = 1,
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    text = "Offline selection from your files",
                    style = TerminalType.small.copy(color = TextPrimary.copy(alpha = 0.68f)),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(12.dp))
            DashedRingButton(
                glyph = ">",
                onClick = { if (enabled) onClick() },
                size = 62.dp,
            )
        }
    }
}

@Composable
private fun CategoryGrid(onOpen: (CollectionKind) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CategoryTile(
                kind = CollectionKind.PLAYLISTS,
                onClick = { onOpen(CollectionKind.PLAYLISTS) },
                modifier = Modifier.weight(1f),
            )
            CategoryTile(
                kind = CollectionKind.ALBUMS,
                onClick = { onOpen(CollectionKind.ALBUMS) },
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CategoryTile(
                kind = CollectionKind.ARTISTS,
                onClick = { onOpen(CollectionKind.ARTISTS) },
                modifier = Modifier.weight(1f),
            )
            CategoryTile(
                kind = CollectionKind.FOLDERS,
                onClick = { onOpen(CollectionKind.FOLDERS) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun CategoryTile(
    kind: CollectionKind,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    TerminalCard(
        modifier = modifier
            .height(112.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
    ) {
        DotMatrix(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 10.dp, end = 10.dp)
                .size(58.dp),
            color = TextSecondary,
            seed = kind.ordinal.toLong(),
            maxAlpha = 0.42f,
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
        ) {
            Text(
                text = kind.glyph,
                style = TerminalType.glyphLarge.copy(color = TextSecondary),
                maxLines = 1,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = kind.title,
                style = TerminalType.bodyStrong.copy(fontSize = 14.sp),
                color = TextPrimary,
                maxLines = 1,
            )
        }
    }
}

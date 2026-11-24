package dev.terminalplayer.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import dev.terminalplayer.i18n.AppStrings
import dev.terminalplayer.i18n.LocalStrings
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
import dev.terminalplayer.ui.library.localizedTitle
import dev.terminalplayer.ui.motion.Reveal
import dev.terminalplayer.ui.motion.LocalMotionEnabled
import dev.terminalplayer.ui.motion.animatedPressScale
import dev.terminalplayer.ui.motion.revealDelay
import dev.terminalplayer.ui.motion.scaleLayer
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
 *
 * Every block fades in with a small upward drift and a 70 ms stagger ([Reveal]); the sort row and the
 * tiles animate their own open/press states.
 */
@Composable
fun HomeScreen(
    viewModel: PlayerViewModel,
    permission: PermissionState,
    onOpenPlayer: () -> Unit,
    onOpenCollection: (CollectionKind?) -> Unit,
) {
    val strings = LocalStrings.current
    val motion = LocalMotionEnabled.current
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
        Reveal(delayMillis = revealDelay(0)) {
            SearchRow(
                hint = strings.searchHint,
                sortOpen = sortOpen,
                onToggleSort = { sortOpen = !sortOpen },
            )
        }
        AnimatedVisibility(
            visible = sortOpen,
            enter = fadeIn(tween(if (motion) 160 else 0)) +
                expandVertically(
                    tween(if (motion) 220 else 0),
                    expandFrom = Alignment.Top,
                ),
            exit = fadeOut(tween(if (motion) 120 else 0)) +
                shrinkVertically(
                    tween(if (motion) 180 else 0),
                    shrinkTowards = Alignment.Top,
                ),
        ) {
            Column {
                Spacer(Modifier.height(10.dp))
                SortRow(
                    selected = catalog.sort,
                    onSelect = viewModel::selectSort,
                )
            }
        }

        Spacer(Modifier.height(22.dp))
        Reveal(delayMillis = revealDelay(1)) {
            Column {
                SectionTitle(
                    title = strings.myTracks,
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
            }
        }

        Spacer(Modifier.height(16.dp))
        Reveal(delayMillis = revealDelay(2)) {
            LocalMixCard(
                title = strings.localMixTitle,
                subtitle = strings.localMixSubtitle,
                enabled = catalog.tracks.isNotEmpty(),
                onClick = {
                    viewModel.playMix()
                    onOpenPlayer()
                },
            )
        }

        Spacer(Modifier.height(24.dp))
        Reveal(delayMillis = revealDelay(3)) {
            Column {
                Text(
                    text = strings.categories,
                    style = TerminalType.title,
                    color = TextPrimary,
                    maxLines = 1,
                )
                Spacer(Modifier.height(12.dp))
                CategoryGrid(onOpen = onOpenCollection)
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun SearchRow(hint: String, sortOpen: Boolean, onToggleSort: () -> Unit) {
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
                    text = hint,
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
    val strings = LocalStrings.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MetaLabel(text = strings.sortLabel, color = TextMuted)
        LibrarySort.entries.forEach { sort ->
            GlyphPill(
                text = sortLabel(sort, strings),
                onClick = { onSelect(sort) },
                selected = sort == selected,
            )
        }
    }
}

private fun sortLabel(sort: LibrarySort, strings: AppStrings): String = when (sort) {
    LibrarySort.TITLE -> strings.sortTitle
    LibrarySort.ARTIST -> strings.sortArtist
    LibrarySort.LENGTH -> strings.sortLength
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
    val strings = LocalStrings.current
    TerminalCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
            when {
                !permission.granted -> PermissionPrompt(permission)
                !catalog.error.isNullOrBlank() -> LibraryMessage(
                    title = strings.libraryErrorTitle,
                    message = catalog.error.orEmpty(),
                    tone = Danger,
                    action = strings.rescanAction,
                    onAction = onRescan,
                )
                catalog.tracks.isEmpty() && (catalog.loading || !catalog.loaded) -> LibraryMessage(
                    title = strings.scanningTitle,
                    message = strings.scanningBody,
                    showCursor = true,
                )
                catalog.tracks.isEmpty() -> LibraryMessage(
                    title = strings.emptyLibraryTitle,
                    message = strings.emptyLibraryBody,
                    action = strings.rescanAction,
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
    val strings = LocalStrings.current
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Text(
            text = strings.libraryAccessTitle,
            style = TerminalType.bodyStrong,
            color = TextPrimary,
            maxLines = 1,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = strings.libraryAccessBody,
            style = TerminalType.small,
            color = TextSecondary,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlyphPill(
                text = strings.grantAction,
                onClick = permission.request,
                selected = true,
            )
            GlyphPill(
                text = strings.settingsAction,
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
private fun LocalMixCard(
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val press = animatedPressScale(pressed && enabled, pressedScale = 0.98f)
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .scaleLayer(press)
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
                    text = title,
                    style = TerminalType.huge.copy(fontSize = 21.sp, color = TextPrimary),
                    maxLines = 1,
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    text = subtitle,
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
    val strings = LocalStrings.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val press = animatedPressScale(pressed, pressedScale = 0.96f)

    TerminalCard(
        modifier = modifier
            .height(112.dp)
            .scaleLayer(press)
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
                text = kind.localizedTitle(strings),
                style = TerminalType.bodyStrong.copy(fontSize = 14.sp),
                color = TextPrimary,
                maxLines = 1,
            )
        }
    }
}

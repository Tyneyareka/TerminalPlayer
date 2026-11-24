package dev.terminalplayer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.terminalplayer.ui.components.BottomNav
import dev.terminalplayer.ui.components.MiniPlayer
import dev.terminalplayer.ui.components.RootTab
import dev.terminalplayer.ui.components.audioLibraryPermission
import dev.terminalplayer.ui.components.audioLibraryPermissions
import dev.terminalplayer.ui.components.rememberPermissionStateFor
import dev.terminalplayer.ui.home.HomeScreen
import dev.terminalplayer.ui.library.CollectionKind
import dev.terminalplayer.ui.library.CollectionScreen
import dev.terminalplayer.ui.player.NowPlayingScreen
import dev.terminalplayer.ui.player.PlayerViewModel
import dev.terminalplayer.ui.theme.Ink
import dev.terminalplayer.ui.visualizer.VisualizerScreen

private enum class Route {
    HOME,
    COLLECTION,
    PLAYER,
    VISUALIZER,
}

/**
 * Root of the new shell: library home, collection browser, full player and visualizer.
 *
 * Navigation is still a couple of `rememberSaveable` strings - a navigation library would cost more
 * than the four destinations need, and keeping everything in one composition preserves the shared
 * `PlayerViewModel` and the playback connection across screens.
 *
 * The bottom segmented control and the mini player live here so every screen except the full player
 * gets them for free.
 */
@Composable
fun MainScreen(viewModel: PlayerViewModel = viewModel()) {
    val playback by viewModel.playback.collectAsStateWithLifecycle()

    var routeName by rememberSaveable { mutableStateOf(Route.HOME.name) }
    var collectionName by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedGroupKey by rememberSaveable { mutableStateOf<String?>(null) }
    var playerOriginName by rememberSaveable { mutableStateOf(Route.HOME.name) }

    val route = Route.entries.firstOrNull { it.name == routeName } ?: Route.HOME
    val collectionKind = collectionName?.let { name ->
        CollectionKind.entries.firstOrNull { it.name == name }
    }

    val permission = rememberPermissionStateFor(
        requiredPermission = audioLibraryPermission(),
        requestedPermissions = audioLibraryPermissions(),
    )

    LaunchedEffect(permission.granted) {
        if (permission.granted) viewModel.refreshLibraryIfNeeded()
    }

    fun openCollection(kind: CollectionKind?) {
        collectionName = kind?.name
        selectedGroupKey = null
        routeName = Route.COLLECTION.name
    }

    fun openPlayer() {
        playerOriginName = routeName
        routeName = Route.PLAYER.name
    }

    fun closePlayer() {
        routeName = playerOriginName
    }

    BackHandler(enabled = route != Route.HOME) {
        when (route) {
            Route.COLLECTION -> {
                if (selectedGroupKey != null) {
                    selectedGroupKey = null
                } else {
                    routeName = Route.HOME.name
                }
            }
            Route.PLAYER -> closePlayer()
            else -> routeName = Route.HOME.name
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (route) {
                Route.HOME -> HomeScreen(
                    viewModel = viewModel,
                    permission = permission,
                    onOpenPlayer = { openPlayer() },
                    onOpenCollection = { kind -> openCollection(kind) },
                )
                Route.COLLECTION -> CollectionScreen(
                    kind = collectionKind,
                    selectedGroupKey = selectedGroupKey,
                    onSelectGroup = { key -> selectedGroupKey = key },
                    onBack = { routeName = Route.HOME.name },
                    onOpenPlayer = { openPlayer() },
                    viewModel = viewModel,
                    permission = permission,
                )
                Route.PLAYER -> NowPlayingScreen(
                    viewModel = viewModel,
                    onBack = { closePlayer() },
                )
                Route.VISUALIZER -> VisualizerScreen(
                    onOpenPlayer = { openPlayer() },
                )
            }
        }

        if (route != Route.PLAYER) {
            MiniPlayer(
                playback = playback,
                onOpen = { openPlayer() },
                onTogglePlayPause = viewModel::togglePlayPause,
                onNext = viewModel::next,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
            BottomNav(
                selected = if (route == Route.VISUALIZER) RootTab.VISUALIZER else RootTab.LIBRARY,
                onSelect = { tab ->
                    when (tab) {
                        RootTab.LIBRARY -> {
                            selectedGroupKey = null
                            routeName = Route.HOME.name
                        }
                        RootTab.VISUALIZER -> routeName = Route.VISUALIZER.name
                    }
                },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
    }
}

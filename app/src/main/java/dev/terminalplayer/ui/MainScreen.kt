package dev.terminalplayer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.terminalplayer.TerminalPlayerApp
import dev.terminalplayer.data.SettingsRepository
import dev.terminalplayer.ui.components.BottomNav
import dev.terminalplayer.ui.components.GlyphButton
import dev.terminalplayer.ui.components.MiniPlayer
import dev.terminalplayer.ui.components.RootTab
import dev.terminalplayer.ui.components.audioLibraryPermission
import dev.terminalplayer.ui.components.audioLibraryPermissions
import dev.terminalplayer.ui.components.rememberPermissionStateFor
import dev.terminalplayer.ui.home.HomeScreen
import dev.terminalplayer.ui.library.CollectionKind
import dev.terminalplayer.ui.library.CollectionScreen
import dev.terminalplayer.ui.motion.LocalMotionEnabled
import dev.terminalplayer.ui.player.NowPlayingScreen
import dev.terminalplayer.ui.player.PlayerViewModel
import dev.terminalplayer.ui.settings.SettingsScreen
import dev.terminalplayer.ui.theme.Ink
import dev.terminalplayer.ui.visualizer.VisualizerScreen

private enum class Route {
    HOME,
    COLLECTION,
    PLAYER,
    VISUALIZER,
    SETTINGS,
}

/**
 * Root of the shell: library home, collection browser, full player, visualizer and settings.
 *
 * Navigation is still a couple of `rememberSaveable` strings - a navigation library would cost more
 * than the five destinations need, and keeping everything in one composition preserves the shared
 * `PlayerViewModel` and the playback connection across screens.
 *
 * Route changes run through `AnimatedContent` (fade + slight vertical drift), the mini player and the
 * bottom bar slide in and out instead of popping, and the whole motion layer honours the
 * ANIMATIONS switch from SETTINGS through [LocalMotionEnabled].
 */
@Composable
fun MainScreen(
    viewModel: PlayerViewModel = viewModel(),
    settingsRepository: SettingsRepository = rememberSettingsRepository(),
) {
    val playback by viewModel.playback.collectAsStateWithLifecycle()
    val settings by settingsRepository.settings.collectAsStateWithLifecycle()
    val motion = LocalMotionEnabled.current

    var routeName by rememberSaveable { mutableStateOf(Route.HOME.name) }
    var collectionName by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedGroupKey by rememberSaveable { mutableStateOf<String?>(null) }
    var playerOriginName by rememberSaveable { mutableStateOf(Route.HOME.name) }
    var settingsOriginName by rememberSaveable { mutableStateOf(Route.HOME.name) }

    val route = Route.entries.firstOrNull { it.name == routeName } ?: Route.HOME
    val collectionKind = collectionName?.let { name ->
        CollectionKind.entries.firstOrNull { it.name == name }
    }
    val selectedTab = when {
        route == Route.VISUALIZER -> RootTab.VISUALIZER
        route == Route.SETTINGS && settingsOriginName == Route.VISUALIZER.name ->
            RootTab.VISUALIZER
        else -> RootTab.LIBRARY
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

    fun openSettings() {
        if (route != Route.SETTINGS) {
            settingsOriginName = routeName
            routeName = Route.SETTINGS.name
        }
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
            Route.SETTINGS -> routeName = settingsOriginName
            Route.VISUALIZER -> routeName = Route.HOME.name
            Route.HOME -> Unit
        }
    }

    val enterMillis = if (motion) 240 else 0
    val exitMillis = if (motion) 160 else 0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AnimatedContent(
                targetState = route,
                transitionSpec = {
                    (
                        fadeIn(tween(enterMillis)) +
                            slideInVertically(tween(enterMillis)) { height -> height / 16 }
                    ) togetherWith (
                        fadeOut(tween(exitMillis)) +
                            slideOutVertically(tween(exitMillis)) { height -> -height / 24 }
                    )
                },
                label = "route",
                modifier = Modifier.fillMaxSize(),
            ) { screen ->
                when (screen) {
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
                    Route.SETTINGS -> SettingsScreen(
                        settings = settings,
                        onLanguageSelected = settingsRepository::setLanguage,
                        onAnimationsChanged = settingsRepository::setAnimationsEnabled,
                        onBack = { routeName = settingsOriginName },
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = route != Route.PLAYER,
            enter = fadeIn(tween(enterMillis)) +
                slideInVertically(tween(enterMillis)) { height -> height / 3 },
            exit = fadeOut(tween(exitMillis)) +
                slideOutVertically(tween(exitMillis)) { height -> height / 3 },
        ) {
            Column(Modifier.fillMaxWidth()) {
                AnimatedVisibility(
                    visible = playback.hasTrack,
                    enter = fadeIn(tween(if (motion) 180 else 0)) +
                        expandVertically(
                            tween(if (motion) 220 else 0),
                            expandFrom = Alignment.Bottom,
                        ) +
                        slideInVertically(tween(if (motion) 220 else 0)) { height -> height / 3 },
                    exit = fadeOut(tween(if (motion) 120 else 0)) +
                        shrinkVertically(
                            tween(if (motion) 160 else 0),
                            shrinkTowards = Alignment.Bottom,
                        ) +
                        slideOutVertically(tween(if (motion) 160 else 0)) { height -> height / 4 },
                ) {
                    MiniPlayer(
                        playback = playback,
                        onOpen = { openPlayer() },
                        onTogglePlayPause = viewModel::togglePlayPause,
                        onNext = viewModel::next,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BottomNav(
                        selected = selectedTab,
                        onSelect = { tab ->
                            when (tab) {
                                RootTab.LIBRARY -> {
                                    selectedGroupKey = null
                                    routeName = Route.HOME.name
                                }
                                RootTab.VISUALIZER -> routeName = Route.VISUALIZER.name
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                    GlyphButton(
                        glyph = "[S]",
                        onClick = { openSettings() },
                        size = 54.dp,
                        shape = RoundedCornerShape(18.dp),
                        active = route == Route.SETTINGS,
                    )
                }
            }
        }
    }
}

@Composable
private fun rememberSettingsRepository(): SettingsRepository {
    val context = LocalContext.current
    return remember(context) {
        (context.applicationContext as TerminalPlayerApp).settingsRepository
    }
}

package dev.terminalplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.terminalplayer.ui.components.AsciiButton
import dev.terminalplayer.ui.components.AsciiRule
import dev.terminalplayer.ui.components.BlinkingCursor
import dev.terminalplayer.ui.player.PlayerScreen
import dev.terminalplayer.ui.theme.TerminalBlack
import dev.terminalplayer.ui.theme.TerminalGray
import dev.terminalplayer.ui.theme.TerminalGreen
import dev.terminalplayer.ui.theme.TerminalGreenDim
import dev.terminalplayer.ui.theme.TerminalGreenSoft
import dev.terminalplayer.ui.theme.TerminalType
import dev.terminalplayer.ui.visualizer.VisualizerScreen

private enum class TerminalTab(val label: String) {
    PLAYER("PLAYER"),
    VISUALIZER("VISUALIZER"),
}

/**
 * Root scaffold of the terminal: window chrome, tab bar, footer prompt and the two screens.
 *
 * Navigation is a single `enum` state: two tabs do not justify a navigation library, and this way
 * both screens stay inside the same composition scope (the player ViewModel survives tab switches).
 */
@Composable
fun MainScreen() {
    var selectedTab by rememberSaveable { mutableStateOf(TerminalTab.PLAYER) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TerminalBlack)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 10.dp),
    ) {
        AppHeader()
        TabBar(
            selected = selectedTab,
            onSelect = { selectedTab = it },
        )

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (selectedTab) {
                TerminalTab.PLAYER -> PlayerScreen()
                TerminalTab.VISUALIZER -> VisualizerScreen(
                    onOpenPlayer = { selectedTab = TerminalTab.PLAYER },
                )
            }
        }

        AppFooter()
    }
}

@Composable
private fun AppHeader() {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "TERMINALPLAYER",
                style = TerminalType.title,
                color = TerminalGreen,
                maxLines = 1,
                softWrap = false,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "v1.0.0",
                style = TerminalType.small,
                color = TerminalGreenDim,
                maxLines = 1,
            )
        }
        Text(
            text = "LOCAL AUDIO TERMINAL // MEDIA3 EXOPLAYER",
            style = TerminalType.small,
            color = TerminalGray,
            maxLines = 1,
            softWrap = false,
        )
        AsciiRule(char = '=', color = TerminalGreenSoft)
    }
}

@Composable
private fun TabBar(selected: TerminalTab, onSelect: (TerminalTab) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TerminalTab.entries.forEach { tab ->
                val isSelected = tab == selected
                AsciiButton(
                    label = if (isSelected) "[ ${tab.label} ]" else "  ${tab.label}  ",
                    onClick = { onSelect(tab) },
                    active = isSelected,
                )
            }
        }
        AsciiRule(char = '-', color = TerminalGreenDim)
    }
}

@Composable
private fun AppFooter() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "> ", style = TerminalType.small, color = TerminalGreenDim)
        Text(text = "READY", style = TerminalType.small, color = TerminalGreenDim)
        BlinkingCursor()
        Spacer(Modifier.weight(1f))
        Text(text = "API 31+", style = TerminalType.small, color = TerminalGray)
    }
}

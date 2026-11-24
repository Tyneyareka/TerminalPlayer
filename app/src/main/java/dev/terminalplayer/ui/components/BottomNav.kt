package dev.terminalplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.terminalplayer.ui.theme.Accent
import dev.terminalplayer.ui.theme.TextMuted
import dev.terminalplayer.ui.theme.TextPrimary
import dev.terminalplayer.ui.theme.TerminalType

/** The two root destinations of the bottom segmented control. */
enum class RootTab(val label: String, val glyph: String) {
    LIBRARY("LIBRARY", "[#]"),
    VISUALIZER("VISUALIZER", "~^~"),
}

/**
 * Bottom navigation styled as a single pill with an accent-filled active segment, exactly like the
 * mock-up. Inactive segments are transparent and only their glyph + label stay visible.
 */
@Composable
fun BottomNav(
    selected: RootTab,
    onSelect: (RootTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    TerminalCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(5.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            RootTab.entries.forEach { tab ->
                NavSegment(
                    tab = tab,
                    selected = tab == selected,
                    onClick = { onSelect(tab) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun NavSegment(
    tab: RootTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(19.dp)
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(shape)
            .background(if (selected) Accent else Color.Transparent)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = tab.glyph,
                style = TerminalType.glyph.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (selected) TextPrimary else TextMuted,
                ),
                maxLines = 1,
                softWrap = false,
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text = tab.label,
                style = TerminalType.bodyStrong.copy(
                    fontSize = 12.sp,
                    letterSpacing = 0.2f.sp,
                    color = if (selected) TextPrimary else TextMuted,
                ),
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

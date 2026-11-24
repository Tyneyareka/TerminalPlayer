package dev.terminalplayer.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.terminalplayer.i18n.LocalStrings
import dev.terminalplayer.ui.motion.LocalMotionEnabled
import dev.terminalplayer.ui.motion.animatedPressScale
import dev.terminalplayer.ui.motion.scaleLayer
import dev.terminalplayer.ui.theme.Accent
import dev.terminalplayer.ui.theme.TextMuted
import dev.terminalplayer.ui.theme.TextPrimary
import dev.terminalplayer.ui.theme.TerminalType

/** The two root destinations of the bottom segmented control. */
enum class RootTab(val glyph: String) {
    LIBRARY("[#]"),
    VISUALIZER("~^~"),
}

/**
 * Bottom navigation styled as a single pill with an accent-filled active segment, exactly like the
 * mock-up. Inactive segments are transparent and only their glyph + label stay visible.
 *
 * The active segment crossfades its fill, the glyph pops with a soft spring and the whole segment
 * springs down while pressed. SETTINGS is a separate key next to the pill (see `MainScreen`), so the
 * two labels stay readable at any width and in any language.
 */
@Composable
fun BottomNav(
    selected: RootTab,
    onSelect: (RootTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
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
                    glyph = tab.glyph,
                    label = when (tab) {
                        RootTab.LIBRARY -> strings.tabLibrary
                        RootTab.VISUALIZER -> strings.tabVisualizer
                    },
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
    glyph: String,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val motion = LocalMotionEnabled.current
    val shape = RoundedCornerShape(19.dp)

    val fill by animateColorAsState(
        targetValue = if (selected) Accent else Color.Transparent,
        animationSpec = tween(220),
        label = "navFill",
    )
    val ink by animateColorAsState(
        targetValue = if (selected) TextPrimary else TextMuted,
        animationSpec = tween(180),
        label = "navInk",
    )
    val glyphScale by animateFloatAsState(
        targetValue = if (selected && motion) 1.14f else 1f,
        animationSpec = spring(
            dampingRatio = 0.40f,
            stiffness = Spring.StiffnessLow,
        ),
        label = "navGlyph",
    )
    val press = animatedPressScale(pressed, pressedScale = 0.96f)

    Box(
        modifier = modifier
            .height(44.dp)
            .scaleLayer(press)
            .clip(shape)
            .background(fill)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = glyph,
                style = TerminalType.glyph.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = ink,
                ),
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.scaleLayer(glyphScale),
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text = label,
                style = TerminalType.bodyStrong.copy(
                    fontSize = 11.sp,
                    letterSpacing = 0.2f.sp,
                    color = ink,
                ),
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

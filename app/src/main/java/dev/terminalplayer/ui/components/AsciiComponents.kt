package dev.terminalplayer.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.terminalplayer.core.Ascii
import dev.terminalplayer.ui.theme.TerminalAmber
import dev.terminalplayer.ui.theme.TerminalBlack
import dev.terminalplayer.ui.theme.TerminalBright
import dev.terminalplayer.ui.theme.TerminalGray
import dev.terminalplayer.ui.theme.TerminalGreen
import dev.terminalplayer.ui.theme.TerminalGreenDim
import dev.terminalplayer.ui.theme.TerminalGreenSoft
import dev.terminalplayer.ui.theme.TerminalType
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt

/**
 * A full width horizontal rule made of real ASCII characters.
 * The amount of characters is computed from the measured advance width of the monospace glyph.
 */
@Composable
fun AsciiRule(
    modifier: Modifier = Modifier,
    char: Char = '-',
    color: Color = TerminalGreenDim,
    style: TextStyle = TerminalType.small,
) {
    val metrics = rememberMonoMetrics(style)
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val cells = metrics.cellsForWidth(constraints.maxWidth.toFloat()).coerceIn(1, 400)
        Text(
            text = char.toString().repeat(cells),
            style = style.copy(color = color),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
        )
    }
}

/**
 * A tap target that looks like a CMD button: `[ > ]`, `[ || ]`, `[ >> ]`.
 *
 * Active/disabled states are expressed through inverse video and colour, never through icons,
 * shapes or elevation. The ripple is disabled on purpose.
 */
@Composable
fun AsciiButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    active: Boolean = false,
    color: Color = TerminalGreen,
    style: TextStyle = TerminalType.body,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val inverted = enabled && (active || pressed)
    val textColor = when {
        inverted -> TerminalBlack
        enabled -> color
        else -> TerminalGray
    }

    Box(
        modifier = modifier
            .background(if (inverted) color else Color.Transparent)
            .then(
                if (enabled) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        role = Role.Button,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 2.dp, vertical = 6.dp),
    ) {
        Text(
            text = label,
            style = style.copy(color = textColor),
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** `> TITLE ====...` section header, the terminal equivalent of an app bar. */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    accent: Color = TerminalGreen,
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "> $title",
                style = TerminalType.title,
                color = accent,
                maxLines = 1,
                softWrap = false,
            )
            Spacer(Modifier.width(8.dp))
            if (!trailing.isNullOrBlank()) {
                Row(Modifier.weight(1f, fill = true), horizontalArrangement = Arrangement.End) {
                    Text(
                        text = trailing,
                        style = TerminalType.small,
                        color = TerminalGreenDim,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            } else {
                Spacer(Modifier.weight(1f))
            }
        }
        AsciiRule(char = '=', color = accent.copy(alpha = 0.45f))
    }
}

/**
 * Scrolling title in the spirit of a terminal banner.
 *
 * When [active] is false or the text fits, the string is static; otherwise it starts outside the
 * right edge and moves left, then repeats with a short pause.
 */
@Composable
fun MarqueeText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    active: Boolean = true,
    pixelsPerSecond: Float = 38f,
) {
    val metrics = rememberMonoMetrics(style)
    val textWidth = remember(text, metrics) { metrics.widthOf(text) }

    BoxWithConstraints(modifier.clipToBounds()) {
        val containerWidth = constraints.maxWidth.toFloat()
        if (!active || textWidth <= containerWidth || containerWidth <= 0f) {
            Text(
                text = text,
                style = style,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
                modifier = Modifier.wrapContentWidth(unbounded = true),
            )
            return@BoxWithConstraints
        }

        val scroll = remember { Animatable(0f) }
        LaunchedEffect(text, textWidth, containerWidth, active) {
            val gap = 56f
            while (isActive) {
                scroll.snapTo(containerWidth)
                val distance = containerWidth + textWidth + gap
                val duration = ((distance / pixelsPerSecond) * 1000f).toInt().coerceIn(2500, 30_000)
                scroll.animateTo(
                    targetValue = -textWidth - gap,
                    animationSpec = tween(durationMillis = duration, easing = LinearEasing),
                )
                delay(700)
            }
        }

        Text(
            text = text,
            style = style,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            modifier = Modifier
                .wrapContentWidth(unbounded = true)
                .offset { IntOffset(scroll.value.roundToInt(), 0) },
        )
    }
}

/**
 * `[=====>........] 01:20 / 03:45`.
 *
 * The amount of cells adapts to the available width; tapping the bar seeks.
 */
@Composable
fun AsciiProgressBar(
    progress: Float,
    positionMs: Long,
    durationMs: Long,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val style = TerminalType.body
    val metrics = rememberMonoMetrics(style)
    val timeLabel = "${Ascii.clock(positionMs)} / ${Ascii.clock(durationMs)}"

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val totalWidth = constraints.maxWidth.toFloat()
        val labelWidth = remember(timeLabel, metrics) { metrics.widthOf("  $timeLabel") }
        val available = (totalWidth - labelWidth).coerceAtLeast(0f)
        // Two cells are reserved for the surrounding brackets.
        val cells = (available / metrics.advance - 2f).toInt().coerceIn(8, 64)
        val bar = remember(progress, cells) { Ascii.bar(progress, cells) }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.pointerInput(durationMs, cells) {
                    detectTapGestures { offset ->
                        val width = size.width.toFloat()
                        if (width > 0f && durationMs > 0L) {
                            onSeek((offset.x / width).coerceIn(0f, 1f))
                        }
                    }
                },
            ) {
                Text(text = bar, style = style, maxLines = 1, softWrap = false)
            }
            Text(
                text = "  $timeLabel",
                style = TerminalType.small,
                color = TerminalGreenDim,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

/** Terminal-styled explanation / error panel with an ASCII action row. */
@Composable
fun InfoPanel(
    code: String,
    message: String,
    modifier: Modifier = Modifier,
    tone: Color = TerminalAmber,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Column(modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            text = "! $code",
            style = TerminalType.title,
            color = tone,
            maxLines = 1,
            softWrap = false,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = message,
            style = TerminalType.small,
            color = TerminalGreenDim,
        )
        Spacer(Modifier.height(10.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = actions,
        )
    }
}

/** Blinking `_` cursor used for idle states. */
@Composable
fun BlinkingCursor(
    modifier: Modifier = Modifier,
    color: Color = TerminalGreen,
) {
    val transition = rememberInfiniteTransition(label = "cursor")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 530, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "cursorPhase",
    )
    Text(
        text = "_",
        style = TerminalType.body,
        color = color.copy(alpha = 0.2f + 0.8f * phase),
        modifier = modifier,
    )
}

/** `LABEL  VALUE` line used by the status blocks. */
@Composable
fun TerminalKeyValue(
    key: String,
    value: String,
    modifier: Modifier = Modifier,
    keyColor: Color = TerminalGray,
    valueColor: Color = TerminalBright,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(text = "$key ", style = TerminalType.small, color = keyColor, maxLines = 1)
        Text(text = value, style = TerminalType.small, color = valueColor, maxLines = 1)
    }
}

/** Convenience wrapper for a "waiting" / "empty" block inside a Column. */
@Composable
fun ColumnScope.StateBlock(
    lines: List<String>,
    color: Color = TerminalGreenDim,
) {
    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        lines.forEach { line ->
            Text(text = line, style = TerminalType.small, color = color, softWrap = true)
        }
        Spacer(Modifier.height(6.dp))
        Row {
            Text(text = "> ", style = TerminalType.small, color = TerminalGreenSoft)
            BlinkingCursor()
        }
    }
}

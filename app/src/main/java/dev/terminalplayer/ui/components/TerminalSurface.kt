package dev.terminalplayer.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.terminalplayer.ui.motion.LocalMotionEnabled
import dev.terminalplayer.ui.motion.animatedPressScale
import dev.terminalplayer.ui.motion.scaleLayer
import dev.terminalplayer.ui.theme.Accent
import dev.terminalplayer.ui.theme.Stroke
import dev.terminalplayer.ui.theme.StrokeSoft
import dev.terminalplayer.ui.theme.SurfaceCard
import dev.terminalplayer.ui.theme.TextPrimary
import dev.terminalplayer.ui.theme.TextSecondary
import dev.terminalplayer.ui.theme.TerminalType

/** Shared corner radius for every card in the app. */
val CardShape: Shape = RoundedCornerShape(18.dp)

private const val CONTROL_MILLIS = 170

/**
 * The basic dark surface: rounded card, flat fill, hairline border.
 *
 * No elevation and no Material `Card`: the mock-up's depth comes only from a one pixel lighter
 * outline, which keeps the ASCII layers perfectly flat.
 */
@Composable
fun TerminalCard(
    modifier: Modifier = Modifier,
    shape: Shape = CardShape,
    color: Color = SurfaceCard,
    borderColor: Color = StrokeSoft,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(color)
            .border(1.dp, borderColor, shape),
        contentAlignment = contentAlignment,
        content = content,
    )
}

/**
 * A tap target whose only content is a monospace glyph (`[=]`, `>`, `<<`, `||`).
 *
 * Two small animations make the terminal feel alive without breaking the flat look: the button
 * springs down while pressed and the glyph pops slightly when [active] turns on. Colours crossfade
 * between the two states instead of snapping.
 */
@Composable
fun GlyphButton(
    glyph: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    shape: Shape = RoundedCornerShape(16.dp),
    active: Boolean = false,
    bordered: Boolean = true,
    background: Color = if (active) Accent else SurfaceCard,
    contentColor: Color = if (active) TextPrimary else TextSecondary,
    style: TextStyle = TerminalType.glyph,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val motion = LocalMotionEnabled.current

    val fill by animateColorAsState(
        targetValue = background,
        animationSpec = tween(CONTROL_MILLIS),
        label = "glyphFill",
    )
    val outline by animateColorAsState(
        targetValue = if (active) Accent else Stroke,
        animationSpec = tween(CONTROL_MILLIS),
        label = "glyphOutline",
    )
    val ink by animateColorAsState(
        targetValue = contentColor,
        animationSpec = tween(CONTROL_MILLIS),
        label = "glyphInk",
    )
    val selectScale by animateFloatAsState(
        targetValue = if (active && motion) 1.10f else 1f,
        animationSpec = spring(
            dampingRatio = 0.40f,
            stiffness = Spring.StiffnessLow,
        ),
        label = "glyphSelect",
    )
    val press = animatedPressScale(pressed, pressedScale = 0.90f)

    Box(
        modifier = modifier
            .size(size)
            .scaleLayer(press * selectScale)
            .clip(shape)
            .background(fill)
            .then(
                if (bordered) {
                    Modifier.border(1.dp, outline, shape)
                } else {
                    Modifier
                },
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = glyph,
            style = style.copy(color = ink),
            maxLines = 1,
            softWrap = false,
        )
    }
}

/**
 * The circular dashed action used by the LOCAL MIX card: a dotted ring around a single `>`.
 *
 * The ring slowly breathes so the card reads as "press me" even before the user touches it.
 */
@Composable
fun DashedRingButton(
    glyph: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 66.dp,
    ringColor: Color = TextPrimary,
    contentColor: Color = TextPrimary,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val motion = LocalMotionEnabled.current

    val transition = rememberInfiniteTransition(label = "ringPulse")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "ringPulsePhase",
    )
    val ringAlpha = if (motion) 0.38f + 0.34f * pulse else 0.65f
    val press = animatedPressScale(pressed, pressedScale = 0.94f)

    Box(
        modifier = modifier
            .size(size)
            .scaleLayer(press)
            .clip(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 1.dp.toPx()
            drawCircle(
                color = ringColor.copy(alpha = ringAlpha),
                radius = this.size.minDimension / 2f - stroke,
                style = Stroke(
                    width = stroke,
                    pathEffect = PathEffect.dashPathEffect(
                        floatArrayOf(4.dp.toPx(), 5.dp.toPx()),
                        0f,
                    ),
                ),
            )
        }
        Text(
            text = glyph,
            style = TerminalType.glyphLarge.copy(color = contentColor),
            maxLines = 1,
        )
    }
}

/** `MY TRACKS` + optional trailing action on the same baseline. */
@Composable
fun SectionTitle(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = TerminalType.title,
            color = TextPrimary,
            maxLines = 1,
            softWrap = false,
        )
        Spacer(Modifier.weight(1f))
        if (trailing != null) {
            trailing()
        }
    }
}

/** One pixel separator inside cards. */
@Composable
fun Hairline(
    modifier: Modifier = Modifier,
    color: Color = StrokeSoft,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(color),
    )
}

/** All-caps meta text: counts, durations, captions. */
@Composable
fun MetaLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = TextSecondary,
) {
    Text(
        text = text,
        modifier = modifier,
        style = TerminalType.label.copy(color = color),
        maxLines = 1,
    )
}

/** A rounded pill of text, used for the sort options under the search bar. */
@Composable
fun GlyphPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val shape = RoundedCornerShape(12.dp)

    val fill by animateColorAsState(
        targetValue = if (selected) Accent else SurfaceCard,
        animationSpec = tween(CONTROL_MILLIS),
        label = "pillFill",
    )
    val outline by animateColorAsState(
        targetValue = if (selected) Accent else StrokeSoft,
        animationSpec = tween(CONTROL_MILLIS),
        label = "pillOutline",
    )
    val ink by animateColorAsState(
        targetValue = if (selected) TextPrimary else TextSecondary,
        animationSpec = tween(CONTROL_MILLIS),
        label = "pillInk",
    )
    val press = animatedPressScale(pressed, pressedScale = 0.94f)

    Box(
        modifier = modifier
            .height(30.dp)
            .scaleLayer(press)
            .clip(shape)
            .background(fill)
            .border(1.dp, outline, shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = TerminalType.label.copy(
                fontSize = 11.sp,
                color = ink,
            ),
            maxLines = 1,
        )
    }
}

package dev.terminalplayer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import dev.terminalplayer.ui.theme.Accent
import dev.terminalplayer.ui.theme.Stroke
import dev.terminalplayer.ui.theme.StrokeSoft
import dev.terminalplayer.ui.theme.SurfaceCard
import dev.terminalplayer.ui.theme.TextPrimary
import dev.terminalplayer.ui.theme.TextSecondary
import dev.terminalplayer.ui.theme.TerminalType

/** Shared corner radius for every card in the app. */
val CardShape: Shape = RoundedCornerShape(18.dp)

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
 * Active state is a filled accent pill; the ripple stays disabled to keep the flat terminal feel.
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
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(background)
            .then(
                if (bordered) {
                    Modifier.border(1.dp, if (active) Accent else Stroke, shape)
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
            style = style.copy(color = contentColor),
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** The circular dashed action used by the LOCAL MIX card: a dotted ring around a single `>`. */
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
    Box(
        modifier = modifier
            .size(size)
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
                color = ringColor.copy(alpha = 0.65f),
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
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(if (selected) Accent else SurfaceCard)
            .border(1.dp, if (selected) Accent else StrokeSoft, shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .height(30.dp)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = TerminalType.label.copy(
                fontSize = 11.sp,
                color = if (selected) TextPrimary else TextSecondary,
            ),
            maxLines = 1,
        )
    }
}

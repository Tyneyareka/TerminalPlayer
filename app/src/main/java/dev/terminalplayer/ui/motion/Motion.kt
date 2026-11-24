package dev.terminalplayer.ui.motion

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * Global animation switch, driven by SETTINGS.
 *
 * When it is off every helper below resolves to its final value, so the app becomes completely
 * static without removing the animation code from the tree.
 */
val LocalMotionEnabled = compositionLocalOf { true }

private const val REVEAL_MILLIS = 320
private const val STAGGER_MILLIS = 70

/** Staggered delay for the n-th section of a screen (capped so late sections do not lag). */
fun revealDelay(index: Int): Int = (index * STAGGER_MILLIS).coerceAtMost(420)

/**
 * The "section opens" animation: a short fade plus a small upward drift.
 *
 * Unlike `AnimatedVisibility` the layout is reserved from the first frame, so a staggered column
 * does not jump around while its sections appear.
 */
@Composable
fun Reveal(
    modifier: Modifier = Modifier,
    delayMillis: Int = 0,
    content: @Composable () -> Unit,
) {
    val motion = LocalMotionEnabled.current
    var started by remember { mutableStateOf(!motion) }

    LaunchedEffect(Unit) { started = true }

    val progress by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = if (motion) {
            tween(
                durationMillis = REVEAL_MILLIS,
                delayMillis = delayMillis,
                easing = FastOutSlowInEasing,
            )
        } else {
            tween(durationMillis = 0)
        },
        label = "reveal",
    )

    Box(
        modifier = modifier.graphicsLayer {
            alpha = progress
            translationY = (1f - progress) * 18.dp.toPx()
        },
    ) {
        content()
    }
}

/**
 * Spring scale for a pressed control.
 *
 * Returns `1f` when animations are disabled, so callers can multiply it with their own scale and
 * keep a single `graphicsLayer`.
 */
@Composable
fun animatedPressScale(pressed: Boolean, pressedScale: Float = 0.92f): Float {
    val motion = LocalMotionEnabled.current
    val scale by animateFloatAsState(
        targetValue = if (pressed && motion) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "pressScale",
    )
    return scale
}

/** Uniform scale in the draw phase; the layout bounds stay untouched. */
fun Modifier.scaleLayer(scale: Float): Modifier =
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }

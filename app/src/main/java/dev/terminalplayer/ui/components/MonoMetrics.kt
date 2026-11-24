package dev.terminalplayer.ui.components

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.isSpecified
import kotlin.math.max

/**
 * Metrics of the monospace grid.
 *
 * The UI is built on character cells, so knowing the exact advance width of one glyph is enough
 * to build ASCII rules, adaptive progress bars and the Canvas based visualizer. Measurement uses
 * a plain `android.graphics.Paint` with [Typeface.MONOSPACE] - the same typeface Compose resolves
 * from [androidx.compose.ui.text.font.FontFamily.Monospace] - which avoids the experimental
 * text measuring APIs entirely.
 */
class MonoMetrics internal constructor(
    private val paint: Paint,
    val advance: Float,
    val lineHeight: Float,
) {
    fun widthOf(text: String): Float = paint.measureText(text)

    fun cellsForWidth(widthPx: Float): Int =
        if (advance <= 0f) 0 else (widthPx / advance).toInt().coerceAtLeast(0)
}

@Composable
fun rememberMonoMetrics(style: TextStyle): MonoMetrics {
    val density = LocalDensity.current
    val fontSizePx = if (style.fontSize.isSpecified) {
        with(density) { style.fontSize.toPx() }
    } else {
        14f * density.density
    }
    val lineHeightPx = if (style.lineHeight.isSpecified) {
        with(density) { style.lineHeight.toPx() }
    } else {
        fontSizePx * 1.25f
    }

    return remember(
        style.fontFamily,
        style.fontWeight,
        style.fontSize,
        style.lineHeight,
        style.letterSpacing,
        density.density,
        density.fontScale,
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.MONOSPACE
            textSize = fontSizePx
            letterSpacing = 0f
        }
        MonoMetrics(
            paint = paint,
            advance = max(1f, paint.measureText("0")),
            lineHeight = lineHeightPx,
        )
    }
}

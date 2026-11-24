package dev.terminalplayer.ui.components

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.terminalplayer.ui.theme.InkRaised
import dev.terminalplayer.ui.theme.StrokeSoft
import dev.terminalplayer.ui.theme.TextPrimary
import dev.terminalplayer.ui.theme.TextSecondary
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Glyph ramp used by the generated album art, from void to full phosphor.
 * Ten levels are enough to fake a halftone gradient with a monospace font.
 */
private const val RAMP = " .:-=+*#%@"

/**
 * Builds the deterministic ASCII cover of one track.
 *
 * Three patterns rotate by track id - noise, concentric rings and a spiral - so the library feels
 * illustrated while every pixel is still a text glyph. The result is cached by the composable and
 * depends only on [seed], so scrolling never regenerates it.
 */
fun asciiArtLines(seed: Long, columns: Int = 10, rows: Int = 10): List<String> {
    val cols = columns.coerceAtLeast(4)
    val rws = rows.coerceAtLeast(4)
    val variant = ((seed % 3L) + 3L).toInt()
    return List(rws) { row ->
        buildString(cols) {
            for (col in 0 until cols) {
                val nx = ((col + 0.5f) / cols) * 2f - 1f
                val ny = ((row + 0.5f) / rws) * 2f - 1f
                val radius = sqrt(nx * nx + ny * ny)
                val angle = atan2(ny, nx)
                val grain = asciiNoise(col, row, seed)
                val raw = when (variant) {
                    0 -> 0.18f + 0.82f * grain
                    1 -> {
                        val rings = 0.5f + 0.5f * cos(radius * 11f)
                        rings * 0.84f + grain * 0.16f
                    }
                    else -> {
                        val spiral = 0.5f + 0.5f * sin(angle * 2f + radius * 9f)
                        spiral * 0.84f + grain * 0.16f
                    }
                }
                // Fade towards the corners so the cover reads as a rounded halftone patch.
                val mask = 1f - smoothStep(0.62f, 1.16f, radius)
                val value = (raw * mask).coerceIn(0f, 1f)
                append(RAMP[(value * (RAMP.length - 1)).roundToInt()])
            }
        }
    }
}

/** Deterministic value noise in `0f..1f`; the same track always gets the same grain. */
internal fun asciiNoise(x: Int, y: Int, seed: Long): Float {
    var hash = seed.toInt() * 374761393 + x * 668265263 + y * 1274126177
    hash = (hash xor (hash ushr 13)) * 1274126177
    hash = hash xor (hash ushr 16)
    return (hash and 0x7FFFFFFF) / 2147483647f
}

private fun smoothStep(edge0: Float, edge1: Float, value: Float): Float {
    val t = ((value - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/** Draws pre-generated ASCII art with one native `drawText` per glyph. */
@Composable
fun AsciiArtwork(
    seed: Long,
    modifier: Modifier = Modifier,
    columns: Int = 10,
    rows: Int = 10,
    color: Color = TextPrimary,
) {
    val lines = remember(seed, columns, rows) { asciiArtLines(seed, columns, rows) }
    val paint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.MONOSPACE
            textAlign = Paint.Align.CENTER
        }
    }
    Canvas(modifier) {
        if (lines.isEmpty()) return@Canvas
        val cellWidth = size.width / columns
        val cellHeight = size.height / rows
        paint.textSize = cellHeight * 1.04f
        val baselineOffset = -(paint.ascent() + paint.descent()) / 2f
        val nativeCanvas = drawContext.canvas.nativeCanvas
        lines.forEachIndexed { row, line ->
            line.forEachIndexed { col, glyph ->
                if (glyph == ' ') return@forEachIndexed
                val level = RAMP.indexOf(glyph).coerceAtLeast(0) / (RAMP.length - 1f)
                paint.color = color.copy(alpha = 0.22f + 0.78f * level).toArgb()
                nativeCanvas.drawText(
                    glyph.toString(),
                    (col + 0.5f) * cellWidth,
                    (row + 0.5f) * cellHeight + baselineOffset,
                    paint,
                )
            }
        }
    }
}

/**
 * The small square cover in track rows: ASCII art on a sunken dark tile.
 */
@Composable
fun AsciiThumbnail(
    seed: Long,
    modifier: Modifier = Modifier,
    size: Dp = 54.dp,
    columns: Int = 9,
    rows: Int = 9,
) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(InkRaised)
            .border(1.dp, StrokeSoft, shape),
        contentAlignment = Alignment.Center,
    ) {
        AsciiArtwork(
            seed = seed,
            modifier = Modifier.fillMaxSize().padding(4.dp),
            columns = columns,
            rows = rows,
        )
    }
}

/**
 * Halftone dot grid used as decoration in the category tiles.
 *
 * Dots are skipped by noise, so the grid looks printed rather than mechanical.
 */
@Composable
fun DotMatrix(
    modifier: Modifier = Modifier,
    columns: Int = 7,
    rows: Int = 7,
    color: Color = TextSecondary,
    seed: Long = 0L,
    maxAlpha: Float = 0.55f,
) {
    Canvas(modifier) {
        val cellWidth = size.width / columns
        val cellHeight = size.height / rows
        val radius = minOf(cellWidth, cellHeight) * 0.17f
        for (row in 0 until rows) {
            for (col in 0 until columns) {
                val grain = asciiNoise(col, row, seed)
                if (grain < 0.28f) continue
                val falloff = 1f - (row + col) / (rows + columns - 2f).coerceAtLeast(1f)
                val alpha = maxAlpha * (0.30f + 0.70f * falloff) * (0.55f + 0.45f * grain)
                drawCircle(
                    color = color.copy(alpha = alpha.coerceIn(0f, 1f)),
                    radius = radius,
                    center = Offset(
                        (col + 0.5f) * cellWidth,
                        (row + 0.5f) * cellHeight,
                    ),
                )
            }
        }
    }
}

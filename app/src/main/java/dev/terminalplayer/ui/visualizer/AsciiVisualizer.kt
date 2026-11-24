package dev.terminalplayer.ui.visualizer

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import dev.terminalplayer.core.Ascii
import dev.terminalplayer.ui.theme.TerminalBright
import dev.terminalplayer.ui.theme.TerminalGreenSoft
import kotlinx.coroutines.isActive
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Character grid renderer for the circular ASCII visualizer.
 *
 * Layout of the frame (in character rows):
 *
 * ```
 * row 0        TERMINALPLAYER // ASCII FFT VISUALIZER
 * row 1        BANDS 40 FFT 1024 SR 44100 FPS 60
 * row 2        ----------------------------------------
 * row 3..N     circle: dense glyph ring + 40 spectrum rays + center readout
 * rows N+1..   ASCII spectrum strip (one bar per two columns)
 * rows -3..-1  rules, RMS/peak/bass readout, session id
 * ```
 *
 * Why it looks like a terminal and not like a Canvas with letters:
 *  - every glyph is placed on a monospace character grid (44 columns);
 *  - the vertical axis is halved because a character cell is twice as tall as wide,
 *    which keeps the circle round;
 *  - the ring is sampled by arc length (~0.25 cell), so it is continuous instead of dotted;
 *  - the ring uses directional glyphs `- \ | /`, the rays use density glyphs `. + = #`,
 *    so both structures stay readable when they overlap;
 *  - two priority layers (base/highlight) are flushed with one `drawText` per row per layer,
 *    which is ~60 draw calls for a full 40x44 frame instead of ~1700.
 *
 * The redraw is driven by [withFrameNanos]. `frameTick` is read inside the draw lambda, so a new
 * frame only invalidates the draw phase: no recomposition, no layout, no allocation churn.
 */
@Composable
fun AsciiVisualizer(
    engine: VisualizerEngine,
    modifier: Modifier = Modifier,
) {
    val paint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.MONOSPACE
            isSubpixelText = false
        }
    }
    val buffer = remember { AsciiCellBuffer() }
    var frameTick by remember { mutableLongStateOf(0L) }

    LaunchedEffect(engine) {
        var previousFrame = 0L
        while (isActive) {
            withFrameNanos { now ->
                val delta = if (previousFrame == 0L) {
                    16_666_666L
                } else {
                    (now - previousFrame).coerceIn(1_000_000L, 100_000_000L)
                }
                previousFrame = now
                engine.advance(delta)
                frameTick = now
            }
        }
    }

    Canvas(modifier) {
        drawAsciiFrame(
            engine = engine,
            buffer = buffer,
            paint = paint,
            frameNanos = frameTick,
        )
    }
}

private fun DrawScope.drawAsciiFrame(
    engine: VisualizerEngine,
    buffer: AsciiCellBuffer,
    paint: Paint,
    frameNanos: Long,
) {
    val width = size.width
    val height = size.height
    if (width < 140f || height < 220f) return

    val cellWidth = width / TARGET_COLUMNS
    val cellHeight = cellWidth * 2f
    val columns = TARGET_COLUMNS
    val rows = (height / cellHeight).toInt().coerceAtLeast(20)
    val topInset = (height - rows * cellHeight) / 2f

    // One monospace glyph must be exactly one cell wide.
    paint.textSize = 100f
    val referenceAdvance = paint.measureText("M").coerceAtLeast(0.001f)
    paint.textSize = 100f * cellWidth / referenceAdvance
    val fontMetrics = paint.fontMetrics
    val baselineOffset = (cellHeight - (fontMetrics.descent - fontMetrics.ascent)) / 2f -
        fontMetrics.ascent

    buffer.reset(columns, rows)

    // ---------------------------------------------------------------- geometry
    val footerRuleRow = rows - 3
    val statsRow = rows - 2
    val sessionRow = rows - 1
    val stripBottom = footerRuleRow - 2
    val stripTop = stripBottom - SPECTRUM_ROWS + 1
    val circleTop = 3
    val circleBottom = stripTop - 2

    val centerX = (columns - 1) * 0.5f
    val bands = engine.bands
    val peaks = engine.peaks
    val bandCount = bands.size
    val rms = engine.rms

    val hasCircleSpace = circleBottom - circleTop >= 10
    val maxRadius = if (hasCircleSpace) {
        min(columns * 0.5f - 1.5f, (circleBottom - circleTop).toFloat())
    } else {
        0f
    }
    val centerY = (circleTop + circleBottom) * 0.5f
    val baseRadius = maxRadius * 0.34f
    val outerRadius = maxRadius * 0.94f

    if (maxRadius >= 6f) {
        // Smooth the spectrum around the ring so the outline stays organic instead of spiky.
        val ringLevels = FloatArray(bandCount)
        for (index in 0 until bandCount) {
            var sum = 0f
            for (tap in -RING_SMOOTHING..RING_SMOOTHING) {
                val wrapped = (index + tap + bandCount) % bandCount
                sum += bands[wrapped]
            }
            ringLevels[index] = sum / (RING_SMOOTHING * 2 + 1)
        }

        fun plot(angle: Float, radius: Float, char: Char, hot: Boolean = false) {
            buffer.put(
                x = centerX + cos(angle) * radius,
                y = centerY + sin(angle) * radius * 0.5f,
                char = char,
                hot = hot,
            )
        }

        // 1. Outer ring: continuous, sampled by arc length, wobbling with its local band.
        val ringSamples = max(240, (outerRadius * 22f).toInt())
        for (sample in 0 until ringSamples) {
            val angle = sample * TWO_PI / ringSamples
            val normalised = ((angle + HALF_PI) / TWO_PI) % 1f
            val bandIndex = (normalised * bandCount).toInt().coerceIn(0, bandCount - 1)
            val level = ringLevels[bandIndex]
            val radius = outerRadius * (0.95f + 0.05f * level)

            val sweepDistance = abs((angle - engine.sweep + PI_F) % TWO_PI - PI_F)
            plot(
                angle = angle,
                radius = radius,
                char = directionChar(angle + HALF_PI),
                hot = sweepDistance < 0.30f || level > 0.82f,
            )
        }

        // 2. Cardinal notches: turns the ring into a radar dial.
        for (notch in 0 until 4) {
            val angle = -HALF_PI + notch * HALF_PI
            plot(angle, outerRadius + 0.9f, '+')
            plot(angle, outerRadius + 2.0f, '+')
        }

        // 3. Spectrum rays. Length, glyph density and heat follow the band level.
        val bandStep = TWO_PI / bandCount
        for (index in 0 until bandCount) {
            val angle = -HALF_PI + index * bandStep
            val level = bands[index]
            val reach = (outerRadius - baseRadius) * 0.88f * level.pow(1.15f)
            val end = baseRadius + reach

            var travel = baseRadius
            while (travel <= end) {
                val depth = (travel - baseRadius) / max(0.01f, outerRadius - baseRadius)
                val glyph = when {
                    level > 0.60f && depth > 0.30f -> '#'
                    level > 0.30f -> '='
                    level > 0.14f -> '+'
                    else -> '.'
                }
                plot(
                    angle = angle,
                    radius = travel,
                    char = glyph,
                    hot = level > 0.60f && depth > 0.55f,
                )
                travel += 0.45f
            }

            val tip = when {
                level > 0.80f -> '#'
                level > 0.52f -> '*'
                level > 0.26f -> '+'
                else -> '.'
            }
            plot(angle, end + 0.5f, tip, hot = level > 0.45f)

            val heldPeak = peaks[index]
            if (heldPeak > 0.50f && heldPeak > level + 0.10f) {
                val peakRadius = baseRadius +
                    (outerRadius - baseRadius) * 0.88f * heldPeak.pow(1.15f) + 1.0f
                plot(angle, peakRadius, '*', hot = true)
            }
        }

        // 4. Core readout, drawn last so it punches through the rays.
        val coreRow = centerY.roundToInt()
        buffer.putCentered(coreRow - 1, "[ CORE ]", hot = true)
        buffer.putCentered(coreRow, "RMS ${Ascii.percent(rms)}")
        buffer.putCentered(coreRow + 1, Ascii.meter(rms, 9))
    }

    // ------------------------------------------------------- spectrum strip
    if (stripTop > circleTop && stripBottom < footerRuleRow) {
        val stripWidth = columns - 2
        buffer.putLine(stripBottom + 1, " " + "-".repeat(stripWidth))

        val bars = stripWidth / 2
        for (bar in 0 until bars) {
            val bandIndex = (bar * bandCount / bars).coerceIn(0, bandCount - 1)
            val level = bands[bandIndex]
            val barHeight = (level * SPECTRUM_ROWS).roundToInt().coerceIn(0, SPECTRUM_ROWS)
            val column = 1 + bar * 2
            for (step in 0 until barHeight) {
                val row = stripBottom - step
                if (row < stripTop) break
                val isTop = step == barHeight - 1
                buffer.putCell(
                    column = column,
                    row = row,
                    char = if (isTop) '*' else '#',
                    hot = isTop && level > 0.45f,
                )
            }
        }
    }

    // ------------------------------------------------------------------ HUD
    val cursor = if ((frameNanos / 480_000_000L) % 2L == 0L) "_" else " "
    buffer.putLine(0, Ascii.fit("TERMINALPLAYER // ASCII FFT VISUALIZER", columns), hot = true)
    buffer.putLine(
        1,
        Ascii.fit(
            "BANDS $bandCount FFT ${engine.captureSize} SR ${engine.sampleRate} " +
                "FPS ${engine.fps.roundToInt()}",
            columns,
        ),
    )
    buffer.putLine(2, "-".repeat(columns))
    buffer.putLine(footerRuleRow, "-".repeat(columns))
    buffer.putLine(
        statsRow,
        Ascii.fit(
            "RMS ${short(engine.rms)} PK ${short(engine.peak)} BASS ${short(engine.bass)} " +
                "SWP ${short(engine.sweep / TWO_PI)}",
            columns,
        ),
        hot = true,
    )
    buffer.putLine(
        sessionRow,
        Ascii.fit("> SESSION ${Ascii.session(engine.audioSessionId)} // LIVE $cursor", columns),
    )

    // ----------------------------------------------------------------- draw
    drawIntoCanvas { canvas ->
        val nativeCanvas = canvas.nativeCanvas

        paint.color = TerminalGreenSoft.toArgb()
        buffer.forEachRow(hot = false) { row, startColumn, text ->
            nativeCanvas.drawText(
                text,
                startColumn * cellWidth,
                topInset + row * cellHeight + baselineOffset,
                paint,
            )
        }

        paint.color = TerminalBright.toArgb()
        buffer.forEachRow(hot = true) { row, startColumn, text ->
            nativeCanvas.drawText(
                text,
                startColumn * cellWidth,
                topInset + row * cellHeight + baselineOffset,
                paint,
            )
        }
    }
}

private fun short(value: Float): String = String.format(Locale.US, "%.2f", value)

private val TWO_PI: Float = (PI * 2.0).toFloat()
private val HALF_PI: Float = (PI / 2.0).toFloat()
private val PI_F: Float = PI.toFloat()
private const val TARGET_COLUMNS = 44
private const val SPECTRUM_ROWS = 6
private const val RING_SMOOTHING = 2

/** One glyph per 45 degrees, so a circle reads as `| / - \` instead of a solid line. */
private val DIRECTION_CHARS = charArrayOf('-', '\\', '|', '/', '-', '\\', '|', '/')

private fun directionChar(angle: Float): Char {
    var normalised = angle % TWO_PI
    if (normalised < 0f) normalised += TWO_PI
    val sector = ((normalised / (PI_F / 4f)) + 0.5f).toInt() % 8
    return DIRECTION_CHARS[sector]
}

/**
 * Reusable character buffer with two priority layers.
 *
 * "hot" glyphs punch through the base layer, which gives the highlight without a second render
 * pass or per-cell draw calls.
 */
internal class AsciiCellBuffer {

    private var columns = 0
    private var rows = 0
    private var dim = CharArray(0)
    private var hot = CharArray(0)

    fun reset(columns: Int, rows: Int) {
        if (this.columns != columns || this.rows != rows) {
            this.columns = columns
            this.rows = rows
            dim = CharArray(columns * rows)
            hot = CharArray(columns * rows)
        }
        dim.fill(' ')
        hot.fill(' ')
    }

    fun put(x: Float, y: Float, char: Char, hot: Boolean = false) {
        putCell(
            column = x.roundToInt(),
            row = y.roundToInt(),
            char = char,
            hot = hot,
        )
    }

    fun putCell(column: Int, row: Int, char: Char, hot: Boolean = false) {
        if (column < 0 || column >= columns || row < 0 || row >= rows) return
        val index = row * columns + column
        if (hot) {
            this.hot[index] = char
            dim[index] = ' '
        } else if (this.hot[index] == ' ') {
            dim[index] = char
        }
    }

    fun putLine(row: Int, text: String, hot: Boolean = false, startColumn: Int = 0) {
        if (row < 0 || row >= rows) return
        var column = startColumn
        var index = 0
        while (index < text.length) {
            if (column >= columns) return
            if (column >= 0) putCell(column, row, text[index], hot)
            column++
            index++
        }
    }

    fun putCentered(row: Int, text: String, hot: Boolean = false) {
        putLine(row, text, hot, startColumn = ((columns - text.length) / 2).coerceAtLeast(0))
    }

    fun forEachRow(hot: Boolean, action: (row: Int, startColumn: Int, text: String) -> Unit) {
        val cells = if (hot) this.hot else dim
        val rowCount = rows
        val columnCount = columns
        val builder = StringBuilder(columnCount)
        for (row in 0 until rowCount) {
            val offset = row * columnCount
            var first = -1
            var last = -1
            for (column in 0 until columnCount) {
                if (cells[offset + column] != ' ') {
                    if (first < 0) first = column
                    last = column
                }
            }
            if (first < 0) continue
            builder.setLength(0)
            for (column in first..last) builder.append(cells[offset + column])
            action(row, first, builder.toString())
        }
    }
}

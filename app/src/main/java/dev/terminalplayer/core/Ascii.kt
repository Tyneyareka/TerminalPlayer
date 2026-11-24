package dev.terminalplayer.core

import java.util.Locale

/**
 * ASCII vocabulary of TerminalPlayer: the only "design system" widgets are made of this.
 *
 * No vector icons, no emoji, no box drawing characters - plain 7-bit ASCII so every
 * monospace font on every OEM ROM renders it identically.
 */
object Ascii {

    const val PLAY_SYMBOL = ">"
    const val PAUSE_SYMBOL = "||"
    const val PREVIOUS_SYMBOL = "<<"
    const val NEXT_SYMBOL = ">>"

    /**
     * Renders `[=====>....]` for the given [progress] in `0f..1f`.
     *
     * @param cells number of characters between the brackets (the brackets are added).
     */
    fun bar(
        progress: Float,
        cells: Int,
        filled: Char = '=',
        head: Char = '>',
        empty: Char = '.',
    ): String {
        val width = cells.coerceAtLeast(4)
        val filledCount = (progress.coerceIn(0f, 1f) * width).toInt().coerceIn(0, width)
        val builder = StringBuilder(width + 2)
        builder.append('[')
        for (index in 0 until width) {
            when {
                index < filledCount - 1 -> builder.append(filled)
                index == filledCount - 1 -> builder.append(head)
                else -> builder.append(empty)
            }
        }
        builder.append(']')
        return builder.toString()
    }

    /** Renders a plain `###....` level meter (no brackets). */
    fun meter(value: Float, cells: Int, on: Char = '#', off: Char = '.'): String {
        val width = cells.coerceAtLeast(1)
        val lit = (value.coerceIn(0f, 1f) * width).toInt().coerceIn(0, width)
        val builder = StringBuilder(width)
        repeat(lit) { builder.append(on) }
        repeat(width - lit) { builder.append(off) }
        return builder.toString()
    }

    /** `42%`, always three characters wide, so HUD lines do not jump around. */
    fun percent(value: Float): String =
        String.format(Locale.US, "%3d%%", (value.coerceIn(0f, 1f) * 100f).toInt())

    /** `01:24`, `1:04:09` or `--:--` when the value is unknown. */
    fun clock(millis: Long): String {
        if (millis <= 0L) return "--:--"
        val totalSeconds = millis / 1000L
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0L) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    /** Audio session id rendered as a hexadecimal address, terminal style. */
    fun session(audioSessionId: Int): String =
        if (audioSessionId == 0) "--------" else String.format(Locale.US, "0x%08X", audioSessionId)

    /** Truncates [text] to [width] characters (used for Canvas HUD lines). */
    fun fit(text: String, width: Int): String =
        if (width <= 0) "" else if (text.length <= width) text else text.substring(0, width)

    /** Left-pads [text] until it is at least [width] characters wide. */
    fun padStart(text: String, width: Int, pad: Char = ' '): String =
        if (text.length >= width) text else buildString(width) {
            repeat(width - text.length) { append(pad) }
            append(text)
        }

    /** Right-pads [text] until it is at least [width] characters wide. */
    fun padEnd(text: String, width: Int, pad: Char = ' '): String =
        if (text.length >= width) text else buildString(width) {
            append(text)
            repeat(width - text.length) { append(pad) }
        }
}

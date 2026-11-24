package dev.terminalplayer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/*
 * TerminalPlayer palette, second generation.
 *
 * The app is still a character terminal - monospace glyphs, ASCII rules, ASCII artwork - but the
 * chassis around it is a modern dark surface: near-black ink, soft charcoal cards, hairline
 * borders and one saturated blue accent. No shadows, no gradients except the LOCAL MIX card.
 */
val Ink: Color = Color(0xFF0B0C0F)
val InkRaised: Color = Color(0xFF111216)
val SurfaceCard: Color = Color(0xFF17181D)
val SurfaceElevated: Color = Color(0xFF1D1F25)
val Stroke: Color = Color(0xFF2A2C34)
val StrokeSoft: Color = Color(0xFF21232A)

val TextPrimary: Color = Color(0xFFF0F1F4)
val TextSecondary: Color = Color(0xFF9C9FA8)
val TextMuted: Color = Color(0xFF646871)

val Accent: Color = Color(0xFF4D7BD8)
val AccentSoft: Color = Color(0xFF2C4166)
val AccentBright: Color = Color(0xFFA9C4FF)

val MixStart: Color = Color(0xFF474368)
val MixEnd: Color = Color(0xFF232134)

val Warning: Color = Color(0xFFE2A64B)
val Danger: Color = Color(0xFFE06060)

private val TerminalColorScheme = darkColorScheme(
    primary = Accent,
    onPrimary = TextPrimary,
    secondary = AccentBright,
    onSecondary = Ink,
    background = Ink,
    onBackground = TextPrimary,
    surface = SurfaceCard,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceElevated,
    onSurfaceVariant = TextSecondary,
    outline = Stroke,
    outlineVariant = StrokeSoft,
    error = Danger,
    onError = Ink,
)

/**
 * Typography scale. The interface stays monospaced on purpose: the ASCII artwork, rules and
 * progress meters only line up when every glyph has the same advance width.
 */
object TerminalType {

    val mono: FontFamily = FontFamily.Monospace

    /** Big screen title, e.g. the track title on the now-playing screen. */
    val headline: TextStyle = TextStyle(
        fontFamily = mono,
        fontSize = 23.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.4f).sp,
        color = TextPrimary,
    )

    /** Section titles: `MY TRACKS`, `CATEGORIES`. */
    val title: TextStyle = TextStyle(
        fontFamily = mono,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Bold,
        color = TextPrimary,
    )

    val body: TextStyle = TextStyle(
        fontFamily = mono,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.sp,
        color = TextPrimary,
    )

    val bodyStrong: TextStyle = body.copy(fontWeight = FontWeight.Bold)

    /** Secondary text: artist names, captions. */
    val small: TextStyle = TextStyle(
        fontFamily = mono,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        color = TextSecondary,
    )

    /** All-caps meta labels: `NOW PLAYING`, `03/12`. */
    val label: TextStyle = TextStyle(
        fontFamily = mono,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.6f.sp,
        color = TextMuted,
    )

    /** Marquee / mini player title. */
    val marquee: TextStyle = body.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold)

    /** ASCII pictograms: `[=]`, `[@]`, `~^~`, `>`. */
    val glyph: TextStyle = TextStyle(
        fontFamily = mono,
        fontSize = 16.sp,
        lineHeight = 18.sp,
        color = TextSecondary,
    )

    val glyphLarge: TextStyle = glyph.copy(fontSize = 22.sp, lineHeight = 24.sp)

    val huge: TextStyle = headline.copy(fontSize = 30.sp, lineHeight = 34.sp)
}

@Composable
fun TerminalTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TerminalColorScheme,
        typography = Typography(),
        content = content,
    )
}

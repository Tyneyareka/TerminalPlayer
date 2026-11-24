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
 * Terminal palette: pure black, phosphor green, no gradients, no shadows, no rounded shapes.
 * Every colour is a flat constant on purpose.
 */
val TerminalBlack: Color = Color(0xFF000000)
val TerminalGreen: Color = Color(0xFF39FF14)
val TerminalGreenSoft: Color = Color(0xFF2FBF2F)
val TerminalGreenDim: Color = Color(0xFF1E8C1E)
val TerminalBright: Color = Color(0xFFD7FFD0)
val TerminalGray: Color = Color(0xFF4A5A4A)
val TerminalAmber: Color = Color(0xFFFFB000)
val TerminalRed: Color = Color(0xFFFF4D4D)

private val TerminalColorScheme = darkColorScheme(
    primary = TerminalGreen,
    onPrimary = TerminalBlack,
    secondary = TerminalGreenSoft,
    onSecondary = TerminalBlack,
    background = TerminalBlack,
    onBackground = TerminalGreen,
    surface = TerminalBlack,
    onSurface = TerminalGreen,
    surfaceVariant = TerminalBlack,
    onSurfaceVariant = TerminalGreenDim,
    outline = TerminalGreenDim,
    outlineVariant = TerminalGray,
    error = TerminalRed,
    onError = TerminalBlack,
)

/**
 * Typography scale. The whole app is monospaced; swapping [mono] for a bundled terminal font
 * (e.g. `FontFamily(Font(R.font.ibm_plex_mono))`) is the only change needed to re-skin the app.
 */
object TerminalType {

    val mono: FontFamily = FontFamily.Monospace

    val body: TextStyle = TextStyle(
        fontFamily = mono,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.sp,
        color = TerminalGreen,
    )

    val small: TextStyle = body.copy(fontSize = 12.sp, lineHeight = 15.sp, color = TerminalGreenDim)

    val label: TextStyle = body.copy(fontSize = 13.sp, lineHeight = 16.sp, color = TerminalGreenSoft)

    val title: TextStyle = body.copy(
        fontSize = 16.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Bold,
        color = TerminalGreen,
    )

    val marquee: TextStyle = body.copy(fontSize = 15.sp, lineHeight = 20.sp, color = TerminalGreen)

    val huge: TextStyle = body.copy(
        fontSize = 22.sp,
        lineHeight = 26.sp,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
fun TerminalTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TerminalColorScheme,
        typography = Typography(),
        content = content,
    )
}

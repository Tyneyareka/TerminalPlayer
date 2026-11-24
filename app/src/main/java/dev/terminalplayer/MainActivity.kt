package dev.terminalplayer

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.terminalplayer.ui.MainScreen
import dev.terminalplayer.ui.theme.TerminalTheme

/**
 * Single activity host. Everything else is Compose.
 *
 * Edge-to-edge is requested explicitly (and handled with `WindowInsets.safeDrawing`), which keeps
 * the black terminal background behind the status and navigation bars on Android 15+.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            TerminalTheme {
                MainScreen()
            }
        }
    }
}

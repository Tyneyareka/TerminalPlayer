package dev.terminalplayer

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.terminalplayer.i18n.LocalStrings
import dev.terminalplayer.i18n.stringsFor
import dev.terminalplayer.ui.MainScreen
import dev.terminalplayer.ui.motion.LocalMotionEnabled
import dev.terminalplayer.ui.theme.TerminalTheme

/**
 * Single activity host. Everything else is Compose.
 *
 * Edge-to-edge is requested explicitly (and handled with `WindowInsets.safeDrawing`), which keeps
 * the black terminal background behind the status and navigation bars on Android 15+.
 *
 * The settings flow is collected here, at the root: the language table and the motion switch are
 * provided as [LocalStrings] and [LocalMotionEnabled], so changing either one recomposes the whole
 * shell instantly while playback keeps running in the service.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            val repository = remember {
                (application as TerminalPlayerApp).settingsRepository
            }
            val settings by repository.settings.collectAsStateWithLifecycle()
            val strings = remember(settings.language) { stringsFor(settings.language) }

            CompositionLocalProvider(
                LocalStrings provides strings,
                LocalMotionEnabled provides settings.animationsEnabled,
            ) {
                TerminalTheme {
                    MainScreen(settingsRepository = repository)
                }
            }
        }
    }
}

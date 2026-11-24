package dev.terminalplayer

import android.app.Application
import dev.terminalplayer.data.FavoritesRepository
import dev.terminalplayer.data.MusicRepository
import dev.terminalplayer.data.SettingsRepository
import dev.terminalplayer.playback.PlayerConnection

/**
 * Process-wide singletons.
 *
 * TerminalPlayer is small enough to skip a DI framework: the repository and the playback
 * connection live exactly as long as the process, which is also what background playback needs.
 * ViewModels receive these through [TerminalPlayerApp] via `AndroidViewModel`.
 */
class TerminalPlayerApp : Application() {

    lateinit var musicRepository: MusicRepository
        private set

    lateinit var favoritesRepository: FavoritesRepository
        private set

    lateinit var settingsRepository: SettingsRepository
        private set

    lateinit var playerConnection: PlayerConnection
        private set

    override fun onCreate() {
        super.onCreate()
        musicRepository = MusicRepository(this)
        favoritesRepository = FavoritesRepository(this)
        settingsRepository = SettingsRepository(this)
        playerConnection = PlayerConnection(this)
        // Connect immediately: this also boots the MediaSessionService and publishes the
        // audio session id the visualizer needs.
        playerConnection.connect()
    }
}

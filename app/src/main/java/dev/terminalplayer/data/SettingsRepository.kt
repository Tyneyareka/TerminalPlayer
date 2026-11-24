package dev.terminalplayer.data

import android.content.Context
import dev.terminalplayer.i18n.AppLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Everything the SETTINGS screen can change. Persisted by [SettingsRepository]. */
data class AppSettings(
    val language: AppLanguage = AppLanguage.SYSTEM,
    val animationsEnabled: Boolean = true,
)

/**
 * Small preference store for the shell, mirroring the approach of [FavoritesRepository]:
 * `SharedPreferences` is the source of truth and a [StateFlow] exposes it to Compose.
 *
 * Reads happen once in the constructor; every write updates both the flow and the file, so the
 * language and the animations switch survive process death without a DataStore dependency.
 */
class SettingsRepository(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun setLanguage(language: AppLanguage) = update { it.copy(language = language) }

    fun setAnimationsEnabled(enabled: Boolean) = update { it.copy(animationsEnabled = enabled) }

    private fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(_settings.value)
        if (next == _settings.value) return
        _settings.value = next
        prefs.edit()
            .putString(KEY_LANGUAGE, next.language.name)
            .putBoolean(KEY_ANIMATIONS, next.animationsEnabled)
            .apply()
    }

    private fun load(): AppSettings = AppSettings(
        language = AppLanguage.fromStorage(prefs.getString(KEY_LANGUAGE, null)),
        animationsEnabled = prefs.getBoolean(KEY_ANIMATIONS, true),
    )

    private companion object {
        const val PREFS_NAME = "terminal_player_settings"
        const val KEY_LANGUAGE = "language"
        const val KEY_ANIMATIONS = "animations"
    }
}

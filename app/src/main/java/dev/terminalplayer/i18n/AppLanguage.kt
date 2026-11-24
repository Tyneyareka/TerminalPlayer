package dev.terminalplayer.i18n

import java.util.Locale

/**
 * Interface language selected in SETTINGS.
 *
 * The whole UI is a pure function of this value: switching it recomposes the app in place, without
 * an Activity restart and without touching playback. [SYSTEM] follows the device locale and is
 * resolved again on every configuration change.
 */
enum class AppLanguage {
    SYSTEM,
    RUSSIAN,
    ENGLISH;

    /** The concrete language that should be rendered right now. */
    fun resolve(locale: Locale = Locale.getDefault()): AppLanguage = when (this) {
        SYSTEM -> if (locale.language.equals("ru", ignoreCase = true)) RUSSIAN else ENGLISH
        RUSSIAN, ENGLISH -> this
    }

    companion object {
        /** Reads the value persisted by `SettingsRepository`; unknown values fall back to SYSTEM. */
        fun fromStorage(raw: String?): AppLanguage =
            entries.firstOrNull { it.name == raw } ?: SYSTEM
    }
}

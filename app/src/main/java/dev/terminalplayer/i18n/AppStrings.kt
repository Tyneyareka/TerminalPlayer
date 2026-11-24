package dev.terminalplayer.i18n

import androidx.compose.runtime.compositionLocalOf

/**
 * Every user-visible string of the shell.
 *
 * The app keeps its copy in Kotlin instead of Android string resources because the interface is
 * driven by [AppLanguage] at runtime: switching the language in SETTINGS must recompose the current
 * screen immediately, without recreating the Activity (and therefore without interrupting playback).
 * `res/values/strings.xml` still carries the few strings Android itself consumes (notification
 * channel, launcher label).
 *
 * Technical ASCII labels (`ST`, `SHF`, `RPT`, `SES`, `BANDS`, `FFT`, error codes such as
 * `E_LIBRARY_ACCESS`) stay untranslated on purpose: they are terminal output, not prose.
 */
interface AppStrings {

    // Navigation ---------------------------------------------------------------

    val tabLibrary: String
    val tabVisualizer: String

    // Home ---------------------------------------------------------------------

    val searchHint: String
    val sortLabel: String
    val sortTitle: String
    val sortArtist: String
    val sortLength: String

    val myTracks: String
    val categories: String
    val localMixTitle: String
    val localMixSubtitle: String

    val libraryErrorTitle: String
    val scanningTitle: String
    val scanningBody: String
    val emptyLibraryTitle: String
    val emptyLibraryBody: String
    val libraryAccessTitle: String
    val libraryAccessBody: String

    val grantAction: String
    val settingsAction: String
    val rescanAction: String

    // Collections --------------------------------------------------------------

    val playlists: String
    val albums: String
    val artists: String
    val folders: String

    val allTracks: String
    val favorites: String
    val longPressHint: String
    val noTracksTitle: String
    val noTracksBody: String
    val noFavoritesTitle: String
    val noFavoritesBody: String
    val scanAgainBody: String
    val libraryPermissionMessage: String

    // Now playing --------------------------------------------------------------

    val nowPlaying: String
    val nothingPlaying: String
    val unknownTrack: String
    val unknownArtist: String
    val unknownAlbum: String
    val asciiCoverPrefix: String
    val albumPrefix: String

    // Visualizer ---------------------------------------------------------------

    val visualizerTitle: String
    val visualizerSubtitle: String
    val visualizerColumns: String
    val visualizerPermissionMessage: String
    val visualizerNoSessionMessage: String
    val openPlayerAction: String
    val retryAction: String

    // Settings -----------------------------------------------------------------

    val settingsTitle: String
    val settingsSubtitle: String
    val languageSection: String
    val languageApplied: String
    val languageSystem: String
    val languageRussian: String
    val languageEnglish: String
    val animationsSection: String
    val animationsTitle: String
    val animationsBody: String
    val animationsOn: String
    val animationsOff: String
    val aboutSection: String
    val aboutBody: String
    val versionLabel: String

    // Dynamic fragments --------------------------------------------------------

    /** `NO ALBUMS INDEXED` / `АЛЬБОМЫ НЕ НАЙДЕНЫ`. */
    fun emptyKind(kind: String): String

    /** `1 TRACK` / `3 TRACKS`, with the Russian plural rules applied. */
    fun tracksCount(count: Int): String

    fun groupsCount(count: Int): String

    /** Human name of the language, always written in that language. */
    fun languageName(language: AppLanguage): String = when (language) {
        AppLanguage.SYSTEM -> languageSystem
        AppLanguage.RUSSIAN -> languageRussian
        AppLanguage.ENGLISH -> languageEnglish
    }

    /** `Now: Русский` hint under the SYSTEM option. */
    fun languageNow(language: AppLanguage): String =
        "$languageNowPrefix ${languageName(language)}"

    val languageNowPrefix: String
}

object AppStringsEn : AppStrings {

    override val tabLibrary = "LIBRARY"
    override val tabVisualizer = "VISUALIZER"

    override val searchHint = "Search local library"
    override val sortLabel = "SORT"
    override val sortTitle = "TITLE"
    override val sortArtist = "ARTIST"
    override val sortLength = "LENGTH"

    override val myTracks = "MY TRACKS"
    override val categories = "CATEGORIES"
    override val localMixTitle = "LOCAL MIX"
    override val localMixSubtitle = "Offline selection from your files"

    override val libraryErrorTitle = "LIBRARY ERROR"
    override val scanningTitle = "SCANNING MEDIASTORE..."
    override val scanningBody = "This can take a few seconds on large libraries."
    override val emptyLibraryTitle = "NO AUDIO FILES FOUND"
    override val emptyLibraryBody = "Add music to the device, then scan the library again."
    override val libraryAccessTitle = "LIBRARY ACCESS REQUIRED"
    override val libraryAccessBody =
        "TerminalPlayer indexes local audio through MediaStore. Files never leave the device."

    override val grantAction = "[ GRANT ]"
    override val settingsAction = "[ SETTINGS ]"
    override val rescanAction = "[ RESCAN ]"

    override val playlists = "PLAYLISTS"
    override val albums = "ALBUMS"
    override val artists = "ARTISTS"
    override val folders = "FOLDERS"

    override val allTracks = "ALL TRACKS"
    override val favorites = "FAVORITES"
    override val longPressHint = "LONG PRESS A TRACK TO SAVE IT TO PLAYLISTS"
    override val noTracksTitle = "NO TRACKS"
    override val noTracksBody = "Scan the library again from the home screen."
    override val noFavoritesTitle = "NO FAVORITES YET"
    override val noFavoritesBody = "Long-press any track to save it here."
    override val scanAgainBody = "Scan the library again from the home screen."
    override val libraryPermissionMessage =
        "READ_MEDIA_AUDIO is required to index local audio files. " +
            "TerminalPlayer works fully offline and never uploads media."

    override val nowPlaying = "NOW PLAYING"
    override val nothingPlaying = "NOTHING PLAYING"
    override val unknownTrack = "UNKNOWN TRACK"
    override val unknownArtist = "UNKNOWN ARTIST"
    override val unknownAlbum = "UNKNOWN ALBUM"
    override val asciiCoverPrefix = "ASCII COVER"
    override val albumPrefix = "ALBUM"

    override val visualizerTitle = "VISUALIZER"
    override val visualizerSubtitle = "LIVE FFT // WAVEFORM"
    override val visualizerColumns = "44 COL"
    override val visualizerPermissionMessage =
        "The visualizer taps the app's own playback session through " +
            "android.media.audiofx.Visualizer. Android requires RECORD_AUDIO for that. " +
            "No microphone audio is recorded, stored or sent anywhere."
    override val visualizerNoSessionMessage =
        "The playback service has not published an audio session yet. " +
            "Start a track, then return to this tab."
    override val openPlayerAction = "[ OPEN PLAYER ]"
    override val retryAction = "[ RETRY ]"

    override val settingsTitle = "SETTINGS"
    override val settingsSubtitle = "SHELL PREFERENCES"
    override val languageSection = "LANGUAGE"
    override val languageApplied = "APPLIED INSTANTLY"
    override val languageSystem = "System"
    override val languageRussian = "Русский"
    override val languageEnglish = "English"
    override val languageNowPrefix = "Now:"
    override val animationsSection = "ANIMATIONS"
    override val animationsTitle = "Interface animations"
    override val animationsBody = "Section reveals, button presses and icon switches."
    override val animationsOn = "[ ON ]"
    override val animationsOff = "[ OFF ]"
    override val aboutSection = "ABOUT"
    override val aboutBody =
        "TerminalPlayer is an offline terminal-inspired player. It never uploads media or analytics."
    override val versionLabel = "VERSION"

    override fun emptyKind(kind: String) = "NO $kind INDEXED"

    override fun tracksCount(count: Int) = if (count == 1) "1 TRACK" else "$count TRACKS"

    override fun groupsCount(count: Int) = if (count == 1) "1 GROUP" else "$count GROUPS"
}

object AppStringsRu : AppStrings {

    override val tabLibrary = "БИБЛИОТЕКА"
    override val tabVisualizer = "ВИЗУАЛ"

    override val searchHint = "Поиск в локальной медиатеке"
    override val sortLabel = "СОРТИРОВКА"
    override val sortTitle = "НАЗВАНИЕ"
    override val sortArtist = "ИСПОЛНИТЕЛЬ"
    override val sortLength = "ВРЕМЯ"

    override val myTracks = "МОИ ТРЕКИ"
    override val categories = "КАТЕГОРИИ"
    override val localMixTitle = "ЛОКАЛЬНЫЙ МИКС"
    override val localMixSubtitle = "Офлайн-подборка из ваших файлов"

    override val libraryErrorTitle = "ОШИБКА МЕДИАТЕКИ"
    override val scanningTitle = "СКАНИРОВАНИЕ MEDIASTORE..."
    override val scanningBody = "На большой медиатеке это может занять несколько секунд."
    override val emptyLibraryTitle = "АУДИОФАЙЛЫ НЕ НАЙДЕНЫ"
    override val emptyLibraryBody = "Добавьте музыку на устройство и запустите сканирование снова."
    override val libraryAccessTitle = "НУЖЕН ДОСТУП К МЕДИАТЕКЕ"
    override val libraryAccessBody =
        "TerminalPlayer индексирует локальное аудио через MediaStore. Файлы не покидают устройство."

    override val grantAction = "[ ДОСТУП ]"
    override val settingsAction = "[ НАСТРОЙКИ ]"
    override val rescanAction = "[ СКАН ]"

    override val playlists = "ПЛЕЙЛИСТЫ"
    override val albums = "АЛЬБОМЫ"
    override val artists = "ИСПОЛНИТЕЛИ"
    override val folders = "ПАПКИ"

    override val allTracks = "ВСЕ ТРЕКИ"
    override val favorites = "ИЗБРАННОЕ"
    override val longPressHint = "ДОЛГОЕ НАЖАТИЕ — В ИЗБРАННОЕ"
    override val noTracksTitle = "ТРЕКОВ ПОКА НЕТ"
    override val noTracksBody = "Запустите сканирование медиатеки на главном экране."
    override val noFavoritesTitle = "В ИЗБРАННОМ ПОКА ПУСТО"
    override val noFavoritesBody = "Долгое нажатие на трек сохранит его здесь."
    override val scanAgainBody = "Запустите сканирование медиатеки на главном экране."
    override val libraryPermissionMessage =
        "Для индексации локальных аудиофайлов нужен READ_MEDIA_AUDIO. " +
            "TerminalPlayer работает полностью офлайн и не загружает медиа."

    override val nowPlaying = "СЕЙЧАС ИГРАЕТ"
    override val nothingPlaying = "НИЧЕГО НЕ ИГРАЕТ"
    override val unknownTrack = "НЕИЗВЕСТНЫЙ ТРЕК"
    override val unknownArtist = "НЕИЗВЕСТНЫЙ ИСПОЛНИТЕЛЬ"
    override val unknownAlbum = "НЕИЗВЕСТНЫЙ АЛЬБОМ"
    override val asciiCoverPrefix = "ASCII-ОБЛОЖКА"
    override val albumPrefix = "АЛЬБОМ"

    override val visualizerTitle = "ВИЗУАЛИЗАТОР"
    override val visualizerSubtitle = "ЖИВОЙ FFT // ОСЦИЛЛОГРАММА"
    override val visualizerColumns = "44 КОЛ"
    override val visualizerPermissionMessage =
        "Визуализатор подключается к аудиосессии собственного воспроизведения через " +
            "android.media.audiofx.Visualizer. Android требует RECORD_AUDIO. " +
            "Микрофон не записывается, данные никуда не отправляются."
    override val visualizerNoSessionMessage =
        "Сервис воспроизведения ещё не выдал аудиосессию. " +
            "Запустите трек и вернитесь на эту вкладку."
    override val openPlayerAction = "[ ОТКРЫТЬ ПЛЕЕР ]"
    override val retryAction = "[ ПОВТОР ]"

    override val settingsTitle = "НАСТРОЙКИ"
    override val settingsSubtitle = "ПАРАМЕТРЫ ОБОЛОЧКИ"
    override val languageSection = "ЯЗЫК"
    override val languageApplied = "СРАЗУ"
    override val languageSystem = "Системный"
    override val languageRussian = "Русский"
    override val languageEnglish = "English"
    override val languageNowPrefix = "Сейчас:"
    override val animationsSection = "АНИМАЦИИ"
    override val animationsTitle = "Анимации интерфейса"
    override val animationsBody = "Появление разделов, нажатия кнопок и переключение иконок."
    override val animationsOn = "[ ВКЛ ]"
    override val animationsOff = "[ ВЫКЛ ]"
    override val aboutSection = "О ПРИЛОЖЕНИИ"
    override val aboutBody =
        "TerminalPlayer — офлайн-плеер в эстетике терминала. Ничего не загружает и не отправляет."
    override val versionLabel = "ВЕРСИЯ"

    override fun emptyKind(kind: String) = "$kind НЕ НАЙДЕНЫ"

    override fun tracksCount(count: Int): String {
        val word = when {
            count % 100 in 11..14 -> "ТРЕКОВ"
            count % 10 == 1 -> "ТРЕК"
            count % 10 in 2..4 -> "ТРЕКА"
            else -> "ТРЕКОВ"
        }
        return "$count $word"
    }

    override fun groupsCount(count: Int): String {
        val word = when {
            count % 100 in 11..14 -> "ГРУПП"
            count % 10 == 1 -> "ГРУППА"
            count % 10 in 2..4 -> "ГРУППЫ"
            else -> "ГРУПП"
        }
        return "$count $word"
    }
}

/** String table of the currently selected language, provided by `MainActivity`. */
val LocalStrings = compositionLocalOf<AppStrings> { AppStringsEn }

fun stringsFor(language: AppLanguage): AppStrings =
    if (language.resolve() == AppLanguage.RUSSIAN) AppStringsRu else AppStringsEn

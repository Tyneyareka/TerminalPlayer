package dev.terminalplayer.ui.library

import dev.terminalplayer.i18n.AppStrings

/**
 * The four tiles in the CATEGORIES grid.
 *
 * `PLAYLISTS` is the local favourites playlist (long-press a track to save it); the other three are
 * MediaStore groupings computed in `PlayerViewModel`. The enum keeps only the ASCII glyph and the
 * identity: the visible name comes from [AppStrings], so it follows the SETTINGS language.
 */
enum class CollectionKind(val glyph: String) {
    PLAYLISTS("[=]"),
    ALBUMS("[@]"),
    ARTISTS("[*]"),
    FOLDERS("[#]"),
}

fun CollectionKind.localizedTitle(strings: AppStrings): String = when (this) {
    CollectionKind.PLAYLISTS -> strings.playlists
    CollectionKind.ALBUMS -> strings.albums
    CollectionKind.ARTISTS -> strings.artists
    CollectionKind.FOLDERS -> strings.folders
}

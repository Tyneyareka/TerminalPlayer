package dev.terminalplayer.ui.library

/**
 * The four tiles in the CATEGORIES grid.
 *
 * `PLAYLISTS` is the local favourites playlist (long-press a track to save it); the other three are
 * MediaStore groupings computed in `PlayerViewModel`.
 */
enum class CollectionKind(val title: String, val glyph: String) {
    PLAYLISTS("PLAYLISTS", "[=]"),
    ALBUMS("ALBUMS", "[@]"),
    ARTISTS("ARTISTS", "[*]"),
    FOLDERS("FOLDERS", "[#]"),
}

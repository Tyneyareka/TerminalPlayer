package dev.terminalplayer.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The only "playlist" TerminalPlayer ships with: a local set of favourite MediaStore ids.
 *
 * Stored in `SharedPreferences` because it is a handful of longs, must survive process death and
 * is intentionally not tied to any account or network service. MediaStore ids are stable for the
 * lifetime of the file on the device, so a stale id simply resolves to nothing after a rescan and
 * is pruned from the `PLAYLISTS` view.
 */
class FavoritesRepository(context: Context) {

    private val preferences =
        context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private val _ids = MutableStateFlow(readIds())
    val ids: StateFlow<Set<Long>> = _ids.asStateFlow()

    fun toggle(trackId: Long) {
        _ids.update { current ->
            val next = if (trackId in current) current - trackId else current + trackId
            preferences.edit()
                .putStringSet(KEY_TRACK_IDS, next.mapTo(HashSet()) { it.toString() })
                .apply()
            next
        }
    }

    private fun readIds(): Set<Long> =
        preferences.getStringSet(KEY_TRACK_IDS, emptySet())
            .orEmpty()
            .mapNotNullTo(HashSet<Long>()) { it.toLongOrNull() }

    private companion object {
        const val PREFERENCES_NAME = "terminalplayer.favorites"
        const val KEY_TRACK_IDS = "track_ids"
    }
}

package dev.terminalplayer.data

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

data class LibraryState(
    val tracks: List<Track> = emptyList(),
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val error: String? = null,
)

/**
 * Reads the device audio collection through MediaStore.
 *
 * All access goes through [MediaStore.Audio.Media.getContentUri] with `VOLUME_EXTERNAL`,
 * so scoped storage rules of Android 11+ are respected and no file paths are guessed.
 */
class MusicRepository(private val context: Context) {

    private val _state = MutableStateFlow(LibraryState())
    val state: StateFlow<LibraryState> = _state.asStateFlow()

    /** Returns the amount of tracks found, or `-1` when the scan failed. */
    suspend fun refresh(): Int = withContext(Dispatchers.IO) {
        _state.update { it.copy(loading = true, error = null) }
        try {
            val tracks = queryTracks()
            _state.update {
                it.copy(tracks = tracks, loading = false, loaded = true, error = null)
            }
            tracks.size
        } catch (security: SecurityException) {
            _state.update {
                it.copy(
                    loading = false,
                    loaded = true,
                    error = "PERMISSION DENIED // ${audioPermissionName()}",
                )
            }
            -1
        } catch (throwable: Throwable) {
            _state.update {
                it.copy(
                    loading = false,
                    loaded = true,
                    error = "MEDIASTORE QUERY FAILED // ${throwable.javaClass.simpleName}",
                )
            }
            -1
        }
    }

    fun reportError(message: String) {
        _state.update { it.copy(error = message, loading = false) }
    }

    private fun audioPermissionName(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            "READ_MEDIA_AUDIO"
        } else {
            "READ_EXTERNAL_STORAGE"
        }

    private fun queryTracks(): List<Track> {
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DISPLAY_NAME,
        )
        val selection =
            "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > 10000"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        val tracks = ArrayList<Track>(256)
        context.contentResolver.query(collection, projection, selection, null, sortOrder)
            ?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val duration = cursor.getLong(durationColumn)
                    if (id <= 0L || duration <= 0L) continue

                    val displayName = cursor.getString(nameColumn).orEmpty()
                    val rawTitle = cursor.getString(titleColumn).orEmpty().trim()
                    val title = rawTitle
                        .ifBlank { displayName.substringBeforeLast('.').trim() }
                        .ifBlank { "UNTITLED" }

                    val rawArtist = cursor.getString(artistColumn).orEmpty().trim()
                    val artist = if (rawArtist.equals("<unknown>", ignoreCase = true)) "" else rawArtist
                    val album = cursor.getString(albumColumn).orEmpty().trim()

                    tracks += Track(
                        id = id,
                        title = title,
                        artist = artist,
                        album = album,
                        durationMs = duration,
                        uri = ContentUris.withAppendedId(collection, id).toString(),
                    )
                }
            }
        return tracks
    }
}

package dev.terminalplayer.data

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

/**
 * One local audio file discovered through MediaStore.
 *
 * The [uri] is an already resolved `content://media/...` Uri string, so the playback
 * layer never needs to know how the library was indexed.
 */
data class Track(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val uri: String,
    /** Folder name as reported by MediaStore (`RELATIVE_PATH`), empty when unknown. */
    val folder: String = "",
) {
    val artistLabel: String
        get() = artist.ifBlank { "UNKNOWN ARTIST" }

    fun toMediaItem(): MediaItem = MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artistLabel)
                .setAlbumTitle(album)
                .setDurationMs(durationMs)
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .build(),
        )
        .build()
}

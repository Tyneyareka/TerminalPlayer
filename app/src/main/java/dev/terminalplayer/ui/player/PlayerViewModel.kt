package dev.terminalplayer.ui.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.terminalplayer.TerminalPlayerApp
import dev.terminalplayer.data.Track
import dev.terminalplayer.playback.AudioSessionBus
import dev.terminalplayer.playback.PlaybackUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** How the library is ordered. Cycled from the `[=]` button on the library screen. */
enum class LibrarySort(val label: String) {
    TITLE("TITLE"),
    ARTIST("ARTIST"),
    LENGTH("LENGTH");

    fun next(): LibrarySort = entries[(ordinal + 1) % entries.size]

    fun comparator(): Comparator<Track> = when (this) {
        TITLE -> compareBy(String.CASE_INSENSITIVE_ORDER) { track: Track -> track.title }
        ARTIST -> compareBy(String.CASE_INSENSITIVE_ORDER) { track: Track -> track.artistLabel }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { track: Track -> track.title }
        LENGTH -> compareBy { track: Track -> track.durationMs }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { track: Track -> track.title }
    }
}

/** A named bucket of tracks: one album, one artist, one folder or the favourites playlist. */
data class TrackGroup(
    val key: String,
    val title: String,
    val subtitle: String,
    val tracks: List<Track>,
) {
    val durationMs: Long get() = tracks.sumOf { it.durationMs }
}

/**
 * Everything the library / collection screens need, precomputed once per emission.
 *
 * Grouping lives in the ViewModel rather than in composables so scrolling and recomposition do not
 * rebuild album/artist/folder maps on every frame.
 */
data class CatalogUiState(
    val tracks: List<Track> = emptyList(),
    val favoriteTracks: List<Track> = emptyList(),
    val albums: List<TrackGroup> = emptyList(),
    val artists: List<TrackGroup> = emptyList(),
    val folders: List<TrackGroup> = emptyList(),
    val favorites: Set<Long> = emptySet(),
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val error: String? = null,
    val sort: LibrarySort = LibrarySort.TITLE,
)

/**
 * Prepares library and playback state for the UI.
 *
 * The ViewModel owns no Android framework objects: it delegates to
 * [dev.terminalplayer.data.MusicRepository], [dev.terminalplayer.data.FavoritesRepository] and
 * [dev.terminalplayer.playback.PlayerConnection] that live in the Application, so configuration
 * changes and tab switches never interrupt playback.
 */
class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as TerminalPlayerApp
    private val player = app.playerConnection
    private val repository = app.musicRepository
    private val favorites = app.favoritesRepository

    val playback: StateFlow<PlaybackUiState> = player.state
    val audioSessionId: StateFlow<Int> = AudioSessionBus.sessionId

    private val sort = MutableStateFlow(LibrarySort.TITLE)

    val catalog: StateFlow<CatalogUiState> =
        combine(repository.state, sort, favorites.ids) { state, currentSort, favoriteIds ->
            val tracks = state.tracks.sortedWith(currentSort.comparator())
            CatalogUiState(
                tracks = tracks,
                favoriteTracks = tracks.filter { it.id in favoriteIds },
                albums = groupAlbums(tracks),
                artists = groupArtists(tracks),
                folders = groupFolders(tracks),
                favorites = favoriteIds,
                loading = state.loading,
                loaded = state.loaded,
                error = state.error,
                sort = currentSort,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = CatalogUiState(),
        )

    /** Called when the audio permission becomes granted; scans only once per process. */
    fun refreshLibraryIfNeeded() {
        val state = repository.state.value
        if (state.loaded || state.loading) return
        refreshLibrary()
    }

    fun refreshLibrary() {
        viewModelScope.launch { repository.refresh() }
    }

    fun cycleSort() {
        sort.update { it.next() }
    }

    fun selectSort(value: LibrarySort) {
        sort.value = value
    }

    // -------------------------------------------------------------- playback

    /**
     * Starts [track] inside [queue] (the visible collection) so "next" keeps walking the list the
     * user is looking at, not the whole library.
     */
    fun playTrack(track: Track, queue: List<Track>) {
        val ordered = queue.ifEmpty { catalog.value.tracks }
        val index = ordered.indexOfFirst { it.id == track.id }
        if (index >= 0) {
            player.playQueue(ordered, index)
        } else {
            player.playQueue(listOf(track), 0)
        }
    }

    /** The LOCAL MIX card: the whole library, shuffled. */
    fun playMix() {
        val tracks = catalog.value.tracks
        if (tracks.isNotEmpty()) player.playQueue(tracks, 0, shuffle = true)
    }

    fun togglePlayPause() {
        if (!playback.value.hasTrack) {
            val tracks = catalog.value.tracks
            if (tracks.isNotEmpty()) player.playQueue(tracks, 0)
            return
        }
        player.togglePlayPause()
    }

    fun next() = player.next()

    fun previous() = player.previous()

    fun seekTo(fraction: Float) = player.seekToFraction(fraction)

    fun seekBy(deltaMillis: Long) = player.seekBy(deltaMillis)

    fun toggleShuffle() = player.toggleShuffle()

    fun cycleRepeat() = player.cycleRepeat()

    fun toggleFavorite(track: Track) = favorites.toggle(track.id)

    // -------------------------------------------------------------- grouping

    private fun groupAlbums(tracks: List<Track>): List<TrackGroup> =
        tracks.groupBy { it.album.ifBlank { "UNKNOWN ALBUM" } }
            .map { (album, items) ->
                val artists = items.map { it.artistLabel }
                    .distinct()
                    .take(2)
                    .joinToString(" / ")
                TrackGroup(
                    key = album,
                    title = album,
                    subtitle = artists.ifBlank { "${items.size} TRACKS" },
                    tracks = items,
                )
            }
            .sortedWith(byTitle())

    private fun groupArtists(tracks: List<Track>): List<TrackGroup> =
        tracks.groupBy { it.artistLabel }
            .map { (artist, items) ->
                TrackGroup(
                    key = artist,
                    title = artist,
                    subtitle = "${items.size} TRACKS",
                    tracks = items,
                )
            }
            .sortedWith(byTitle())

    private fun groupFolders(tracks: List<Track>): List<TrackGroup> =
        tracks.groupBy { it.folder.ifBlank { "ROOT" } }
            .map { (folder, items) ->
                TrackGroup(
                    key = folder,
                    title = folder,
                    subtitle = "${items.size} TRACKS",
                    tracks = items,
                )
            }
            .sortedWith(byTitle())

    private fun byTitle(): Comparator<TrackGroup> =
        compareBy(String.CASE_INSENSITIVE_ORDER) { group: TrackGroup -> group.title }
}

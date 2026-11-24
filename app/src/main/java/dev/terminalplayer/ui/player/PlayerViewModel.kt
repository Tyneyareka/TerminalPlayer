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

/** How the library list is ordered. Cycled through with the `[ SORT ]` button. */
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
    }
}

data class LibraryUiState(
    val tracks: List<Track> = emptyList(),
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val error: String? = null,
    val sort: LibrarySort = LibrarySort.TITLE,
)

/**
 * Prepares library and playback state for [PlayerScreen].
 *
 * The ViewModel owns no Android framework objects: it delegates to [dev.terminalplayer.data.MusicRepository]
 * and [dev.terminalplayer.playback.PlayerConnection] that live in the Application, so configuration
 * changes and tab switches never interrupt playback.
 */
class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as TerminalPlayerApp
    private val player = app.playerConnection
    private val repository = app.musicRepository

    val playback: StateFlow<PlaybackUiState> = player.state
    val audioSessionId: StateFlow<Int> = AudioSessionBus.sessionId

    private val sort = MutableStateFlow(LibrarySort.TITLE)

    val library: StateFlow<LibraryUiState> = combine(repository.state, sort) { state, currentSort ->
        LibraryUiState(
            tracks = state.tracks.sortedWith(currentSort.comparator()),
            loading = state.loading,
            loaded = state.loaded,
            error = state.error,
            sort = currentSort,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LibraryUiState(),
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

    fun playTrack(track: Track) {
        val tracks = library.value.tracks
        val index = tracks.indexOfFirst { it.id == track.id }
        if (index >= 0) player.playQueue(tracks, index)
    }

    fun togglePlayPause() {
        if (!playback.value.hasTrack) {
            val tracks = library.value.tracks
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
}

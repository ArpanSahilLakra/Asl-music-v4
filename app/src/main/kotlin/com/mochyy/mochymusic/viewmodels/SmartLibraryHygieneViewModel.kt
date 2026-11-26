package com.mochyy.mochymusic.viewmodels

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mochyy.mochymusic.ai.DuplicateCandidate
import com.mochyy.mochymusic.ai.DuplicateGroup
import com.mochyy.mochymusic.ai.DuplicateTrackDetector
import com.mochyy.mochymusic.constants.PreventDuplicateTracksInQueueKey
import com.mochyy.mochymusic.db.MusicDatabase
import com.mochyy.mochymusic.db.entities.PlaylistSongMap
import com.mochyy.mochymusic.utils.dataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@Immutable
data class PlaylistDuplicateSummary(
    val playlistId: String,
    val playlistName: String,
    val duplicateCount: Int,
    val removableMaps: List<PlaylistSongMap>,
)

@Immutable
data class LibraryHygieneSnapshot(
    val isScanning: Boolean = false,
    val totalLibraryTracks: Int = 0,
    val libraryDuplicateGroups: List<DuplicateGroup> = emptyList(),
    val playlistDuplicates: List<PlaylistDuplicateSummary> = emptyList(),
    val cacheSizeMb: Double = 0.0,
    val preventQueueDuplicates: Boolean = true,
    val statusMessage: String? = null,
)

@HiltViewModel
class SmartLibraryHygieneViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val database: MusicDatabase,
) : ViewModel() {

    private val _state = MutableStateFlow(LibraryHygieneSnapshot(isScanning = true))
    val state: StateFlow<LibraryHygieneSnapshot> = _state.asStateFlow()

    init {
        scanLibrary()
    }

    fun scanLibrary() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = _state.value.copy(isScanning = true, statusMessage = null)
            val songs = runCatching { database.allSongs().first() }.getOrDefault(emptyList())
            val candidates = songs.map { song ->
                DuplicateCandidate(
                    id = song.id,
                    title = song.song.title,
                    artist = song.artists.firstOrNull()?.name.orEmpty(),
                )
            }
            val libraryGroups = DuplicateTrackDetector.findDuplicateGroups(candidates)

            val playlists = runCatching { database.playlistsByCreateDateAsc().first() }.getOrDefault(emptyList())
            val playlistSummaries = buildList {
                for (playlist in playlists) {
                    val entries = runCatching {
                        database.playlistSongs(playlist.id).first()
                    }.getOrDefault(emptyList())

                    val seenKeys = HashSet<String>()
                    val removable = mutableListOf<PlaylistSongMap>()
                    for (entry in entries) {
                        val key = DuplicateTrackDetector.normalizeTrackKey(
                            entry.song.song.title,
                            entry.song.artists.firstOrNull()?.name.orEmpty(),
                        )
                        if (!seenKeys.add(key)) {
                            removable.add(entry.map)
                        }
                    }
                    if (removable.isNotEmpty()) {
                        add(
                            PlaylistDuplicateSummary(
                                playlistId = playlist.id,
                                playlistName = playlist.playlist.name,
                                duplicateCount = removable.size,
                                removableMaps = removable,
                            )
                        )
                    }
                }
            }

            val cacheBytes = calculateDirBytes(appContext.cacheDir)
            val preventDupes = runCatching {
                appContext.dataStore.data.first()[PreventDuplicateTracksInQueueKey] ?: true
            }.getOrDefault(true)

            _state.value = LibraryHygieneSnapshot(
                isScanning = false,
                totalLibraryTracks = songs.size,
                libraryDuplicateGroups = libraryGroups,
                playlistDuplicates = playlistSummaries,
                cacheSizeMb = cacheBytes / (1024.0 * 1024.0),
                preventQueueDuplicates = preventDupes,
                statusMessage = null,
            )
        }
    }

    fun deduplicatePlaylist(summary: PlaylistDuplicateSummary) {
        viewModelScope.launch(Dispatchers.IO) {
            database.transaction {
                summary.removableMaps.forEach { map ->
                    delete(map)
                }
            }
            scanLibrary()
            _state.value = _state.value.copy(
                statusMessage = "Removed ${summary.duplicateCount} duplicate entries from \"${summary.playlistName}\"."
            )
        }
    }

    fun deduplicateAllPlaylists() {
        val current = _state.value.playlistDuplicates
        if (current.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            var removed = 0
            database.transaction {
                current.forEach { summary ->
                    summary.removableMaps.forEach { map ->
                        delete(map)
                        removed++
                    }
                }
            }
            scanLibrary()
            _state.value = _state.value.copy(
                statusMessage = "Cleaned $removed duplicate playlist entries across ${current.size} playlists."
            )
        }
    }

    fun togglePreventQueueDuplicates(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            appContext.dataStore.edit { prefs ->
                prefs[PreventDuplicateTracksInQueueKey] = enabled
            }
            _state.value = _state.value.copy(
                preventQueueDuplicates = enabled,
                statusMessage = if (enabled) {
                    "Smart queue duplicate guard enabled."
                } else {
                    "Smart queue duplicate guard disabled."
                }
            )
        }
    }

    fun clearTemporaryCache() {
        viewModelScope.launch(Dispatchers.IO) {
            val beforeBytes = calculateDirBytes(appContext.cacheDir)
            appContext.cacheDir.listFiles()?.forEach { file ->
                // Preserve ExoPlayer active database locks; clean image/http temp caches safely
                if (!file.name.contains("exo", ignoreCase = true)) {
                    file.deleteRecursively()
                }
            }
            val afterBytes = calculateDirBytes(appContext.cacheDir)
            val freedMb = ((beforeBytes - afterBytes).coerceAtLeast(0L)) / (1024.0 * 1024.0)
            _state.value = _state.value.copy(
                cacheSizeMb = afterBytes / (1024.0 * 1024.0),
                statusMessage = String.format("Cleared %.1f MB of temporary cache safely.", freedMb),
            )
        }
    }

    private fun calculateDirBytes(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        var total = 0L
        dir.walkTopDown().forEach { f ->
            if (f.isFile) total += f.length()
        }
        return total
    }
}

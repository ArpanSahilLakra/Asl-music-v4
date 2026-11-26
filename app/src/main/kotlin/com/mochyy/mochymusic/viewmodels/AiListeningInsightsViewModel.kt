package com.mochyy.mochymusic.viewmodels

import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mochyy.mochymusic.ai.AiUiState
import com.mochyy.mochymusic.ai.GeminiMusicAiRepository
import com.mochyy.mochymusic.ai.ListeningDnaReport
import com.mochyy.mochymusic.db.MusicDatabase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

private const val TAG = "AiListeningInsightsVM"

@Immutable
data class LibraryStatsSnapshot(
    val totalTracks: Int = 0,
    val likedTracks: Int = 0,
    val totalMinutesListened: Long = 0L,
    val topTrackTitles: List<String> = emptyList(),
    val topArtistNames: List<String> = emptyList(),
)

@HiltViewModel
class AiListeningInsightsViewModel @Inject constructor(
    private val database: MusicDatabase,
    private val aiRepository: GeminiMusicAiRepository,
) : ViewModel() {

    private val _stats = MutableStateFlow(LibraryStatsSnapshot())
    val stats: StateFlow<LibraryStatsSnapshot> = _stats.asStateFlow()

    private val _dnaState = MutableStateFlow<AiUiState<ListeningDnaReport>>(AiUiState.Idle)
    val dnaState: StateFlow<AiUiState<ListeningDnaReport>> = _dnaState.asStateFlow()

    init {
        refreshLibraryStats()
    }

    fun refreshLibraryStats() {
        viewModelScope.launch(Dispatchers.IO) {
            val songs = runCatching { database.allSongs().first() }.getOrDefault(emptyList())
            val totalMinutes = songs.sumOf { it.song.totalPlayTime } / 60_000L
            val liked = songs.count { it.song.liked }
            val topSongs = songs.sortedByDescending { it.song.totalPlayTime }.take(8)
            val topArtists = topSongs
                .flatMap { it.artists }
                .groupingBy { it.name }
                .eachCount()
                .entries
                .sortedByDescending { it.value }
                .take(6)
                .map { it.key }

            _stats.value = LibraryStatsSnapshot(
                totalTracks = songs.size,
                likedTracks = liked,
                totalMinutesListened = totalMinutes,
                topTrackTitles = topSongs.map { song ->
                    val artist = song.artists.firstOrNull()?.name ?: "Unknown"
                    "${song.song.title} — $artist"
                },
                topArtistNames = topArtists,
            )
        }
    }

    fun analyzeSonicDna() {
        if (_dnaState.value is AiUiState.Loading) return
        viewModelScope.launch {
            _dnaState.value = AiUiState.Loading
            try {
                val snapshot = withContext(Dispatchers.IO) {
                    val songs = runCatching { database.allSongs().first() }.getOrDefault(emptyList())
                    val totalMinutes = songs.sumOf { it.song.totalPlayTime } / 60_000L
                    val liked = songs.count { it.song.liked }
                    val topSongs = songs.sortedByDescending { it.song.totalPlayTime }.take(10)
                    val topArtists = topSongs
                        .flatMap { it.artists }
                        .groupingBy { it.name }
                        .eachCount()
                        .entries
                        .sortedByDescending { it.value }
                        .take(6)
                        .map { it.key }
                    LibraryStatsSnapshot(
                        totalTracks = songs.size,
                        likedTracks = liked,
                        totalMinutesListened = totalMinutes,
                        topTrackTitles = topSongs.map { s ->
                            "${s.song.title} by ${s.artists.firstOrNull()?.name ?: "Unknown"}"
                        },
                        topArtistNames = topArtists,
                    )
                }
                _stats.value = snapshot

                val report = aiRepository.generateListeningDna(
                    topSongsSummary = snapshot.topTrackTitles.joinToString("; "),
                    topArtistsSummary = snapshot.topArtistNames.joinToString(", "),
                    totalTracks = snapshot.totalTracks,
                    likedTracks = snapshot.likedTracks,
                    totalMinutes = snapshot.totalMinutesListened,
                )
                _dnaState.value = AiUiState.Success(report)
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Sonic DNA timed out", e)
                _dnaState.value = AiUiState.Error("Sonic DNA analysis timed out after 60 seconds. Please retry.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Sonic DNA failed", e)
                _dnaState.value = AiUiState.Error(e.localizedMessage ?: "Failed to generate Sonic DNA insights.")
            }
        }
    }
}

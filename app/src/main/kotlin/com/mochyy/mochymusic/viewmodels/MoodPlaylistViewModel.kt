package com.mochyy.mochymusic.viewmodels

import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mochyy.mochymusic.ai.AiActionExecutor
import com.mochyy.mochymusic.ai.AiUiState
import com.mochyy.mochymusic.ai.GeminiMusicAiRepository
import com.mochyy.mochymusic.ai.GeneratedPlaylistPlan
import com.mochyy.mochymusic.ai.MoodPreset
import com.mochyy.mochymusic.ai.MoodPresets
import com.mochyy.mochymusic.db.MusicDatabase
import com.music.innertube.models.SongItem
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

private const val TAG = "MoodPlaylistVM"

@Immutable
data class MoodPlaylistResult(
    val plan: GeneratedPlaylistPlan,
    val savedPlaylistId: String,
    val resolvedSongs: List<SongItem>,
)

@HiltViewModel
class MoodPlaylistViewModel @Inject constructor(
    private val database: MusicDatabase,
    private val aiRepository: GeminiMusicAiRepository,
    private val actionExecutor: AiActionExecutor,
) : ViewModel() {

    private val _selectedMood = MutableStateFlow(MoodPresets.All.first())
    val selectedMood: StateFlow<MoodPreset> = _selectedMood.asStateFlow()

    private val _customPrompt = MutableStateFlow("")
    val customPrompt: StateFlow<String> = _customPrompt.asStateFlow()

    private val _useListeningHistory = MutableStateFlow(true)
    val useListeningHistory: StateFlow<Boolean> = _useListeningHistory.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _uiState = MutableStateFlow<AiUiState<MoodPlaylistResult>>(AiUiState.Idle)
    val uiState: StateFlow<AiUiState<MoodPlaylistResult>> = _uiState.asStateFlow()

    fun selectMood(preset: MoodPreset) {
        _selectedMood.value = preset
    }

    fun applySeedQuery(seed: String?) {
        if (seed.isNullOrBlank()) return
        val match = MoodPresets.All.firstOrNull {
            it.id.equals(seed, ignoreCase = true) || it.title.contains(seed, ignoreCase = true)
        }
        if (match != null) {
            _selectedMood.value = match
        } else {
            _customPrompt.value = seed
        }
    }

    fun updateCustomPrompt(value: String) {
        _customPrompt.value = value
    }

    fun setUseListeningHistory(enabled: Boolean) {
        _useListeningHistory.value = enabled
    }

    fun generatePlaylist() {
        if (_uiState.value is AiUiState.Loading) return

        viewModelScope.launch {
            _uiState.value = AiUiState.Loading
            _statusMessage.value = "Composing your mood mix with Gemini..."

            try {
                val historySummary = if (_useListeningHistory.value) {
                    withContext(Dispatchers.IO) {
                        runCatching {
                            database.allSongs().first()
                                .sortedByDescending { it.song.totalPlayTime }
                                .take(10)
                                .joinToString("; ") { song ->
                                    val artist = song.artists.firstOrNull()?.name ?: "Unknown"
                                    "${song.song.title} by $artist"
                                }
                        }.getOrDefault("")
                    }
                } else {
                    ""
                }

                val mood = _selectedMood.value
                val combinedCustom = buildString {
                    append(mood.promptHint)
                    val extra = _customPrompt.value.trim()
                    if (extra.isNotEmpty()) {
                        append(". Extra listener note: ")
                        append(extra)
                    }
                }

                val plan = aiRepository.generateMoodPlaylist(
                    moodLabel = mood.title,
                    customPrompt = combinedCustom,
                    listeningHistorySummary = historySummary,
                    trackCount = 10,
                )

                _statusMessage.value = "Matching ${plan.tracks.size} tracks & saving to your library..."
                val (playlistId, resolvedSongs) = actionExecutor.resolveAndSaveMoodPlaylist(plan)

                _statusMessage.value = null
                _uiState.value = AiUiState.Success(
                    MoodPlaylistResult(
                        plan = plan,
                        savedPlaylistId = playlistId,
                        resolvedSongs = resolvedSongs,
                    )
                )
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Mood playlist timed out", e)
                _statusMessage.value = null
                _uiState.value = AiUiState.Error("Playlist generation timed out after 60 seconds. Please try again.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Mood playlist generation failed", e)
                _statusMessage.value = null
                _uiState.value = AiUiState.Error(e.localizedMessage ?: "Failed to generate mood playlist.")
            }
        }
    }
}

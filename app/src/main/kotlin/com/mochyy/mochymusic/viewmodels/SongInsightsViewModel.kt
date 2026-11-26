package com.mochyy.mochymusic.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mochyy.mochymusic.ai.AiActionExecutor
import com.mochyy.mochymusic.ai.AiUiState
import com.mochyy.mochymusic.ai.GeminiMusicAiRepository
import com.mochyy.mochymusic.ai.SongExplanation
import com.mochyy.mochymusic.db.MusicDatabase
import com.mochyy.mochymusic.db.entities.SavedLyricPassageEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "SongInsightsVM"

@HiltViewModel
class SongInsightsViewModel @Inject constructor(
    private val database: MusicDatabase,
    private val aiRepository: GeminiMusicAiRepository,
    private val actionExecutor: AiActionExecutor,
) : ViewModel() {

    val savedPassages: StateFlow<List<SavedLyricPassageEntity>> = database.aiStudioDao
        .observeSavedLyricPassages()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    private val _explanationState = MutableStateFlow<AiUiState<SongExplanation>>(AiUiState.Idle)
    val explanationState: StateFlow<AiUiState<SongExplanation>> = _explanationState.asStateFlow()

    private val _targetLanguage = MutableStateFlow("English")
    val targetLanguage: StateFlow<String> = _targetLanguage.asStateFlow()

    private val _karaokeEnabled = MutableStateFlow(actionExecutor.isKaraokeVocalCutEnabled)
    val karaokeEnabled: StateFlow<Boolean> = _karaokeEnabled.asStateFlow()

    private var lastExplainedKey: String? = null

    fun setTargetLanguage(language: String) {
        _targetLanguage.value = language
    }

    fun toggleKaraokeVocalCut() {
        val next = !_karaokeEnabled.value
        actionExecutor.setKaraokeVocalAttenuation(next)
        _karaokeEnabled.value = next
    }

    fun explainSong(
        songTitle: String,
        artistName: String,
        albumName: String?,
        snippetContext: String? = null,
        forceRefresh: Boolean = false,
    ) {
        val lang = _targetLanguage.value
        val cacheKey = "$songTitle|$artistName|$lang|${snippetContext.orEmpty()}"
        if (!forceRefresh && lastExplainedKey == cacheKey && _explanationState.value is AiUiState.Success) {
            return
        }

        viewModelScope.launch {
            _explanationState.value = AiUiState.Loading
            try {
                val explanation = aiRepository.explainSong(
                    songTitle = songTitle,
                    artistName = artistName,
                    albumName = albumName,
                    targetLanguage = lang,
                    userSnippetContext = snippetContext,
                )
                lastExplainedKey = cacheKey
                _explanationState.value = AiUiState.Success(explanation)
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Song explanation timed out", e)
                _explanationState.value = AiUiState.Error("Song insight timed out after 60 seconds. Please retry.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Song explanation failed", e)
                _explanationState.value = AiUiState.Error(e.localizedMessage ?: "Failed to generate song insights.")
            }
        }
    }

    fun savePassage(
        songId: String,
        songTitle: String,
        artistName: String,
        passageText: String,
        note: String? = null,
    ) {
        val cleanText = passageText.trim()
        if (cleanText.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            database.aiStudioDao.insertSavedLyricPassage(
                SavedLyricPassageEntity(
                    songId = songId,
                    songTitle = songTitle,
                    artistName = artistName,
                    passageText = cleanText,
                    note = note?.trim()?.takeIf { it.isNotEmpty() },
                )
            )
        }
    }

    fun deletePassage(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            database.aiStudioDao.deleteSavedLyricPassage(id)
        }
    }
}

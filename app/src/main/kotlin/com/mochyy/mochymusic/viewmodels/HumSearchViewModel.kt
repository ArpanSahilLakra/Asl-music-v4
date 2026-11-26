package com.mochyy.mochymusic.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mochyy.mochymusic.ai.AiActionExecutor
import com.mochyy.mochymusic.ai.AiUiState
import com.mochyy.mochymusic.ai.GeminiMusicAiRepository
import com.mochyy.mochymusic.ai.HumMatchCandidate
import com.mochyy.mochymusic.ai.HumSearchResult
import com.mochyy.mochymusic.ai.MelodyRecorder
import com.mochyy.mochymusic.extensions.toMediaItem
import com.mochyy.mochymusic.playback.PlayerConnection
import com.mochyy.mochymusic.playback.queues.ListQueue
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

private const val TAG = "HumSearchViewModel"

@HiltViewModel
class HumSearchViewModel @Inject constructor(
    private val aiRepository: GeminiMusicAiRepository,
    private val actionExecutor: AiActionExecutor,
) : ViewModel() {

    private val recordingFlag = AtomicBoolean(false)

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _micLevel = MutableStateFlow(0f)
    val micLevel: StateFlow<Float> = _micLevel.asStateFlow()

    private val _liveNote = MutableStateFlow<String?>(null)
    val liveNote: StateFlow<String?> = _liveNote.asStateFlow()

    private val _elapsedSeconds = MutableStateFlow(0f)
    val elapsedSeconds: StateFlow<Float> = _elapsedSeconds.asStateFlow()

    private val _optionalHint = MutableStateFlow("")
    val optionalHint: StateFlow<String> = _optionalHint.asStateFlow()

    private val _uiState = MutableStateFlow<AiUiState<HumSearchResult>>(AiUiState.Idle)
    val uiState: StateFlow<AiUiState<HumSearchResult>> = _uiState.asStateFlow()

    private val _playbackStatus = MutableStateFlow<String?>(null)
    val playbackStatus: StateFlow<String?> = _playbackStatus.asStateFlow()

    fun updateHint(text: String) {
        _optionalHint.value = text
    }

    /**
     * Starts live microphone capture (caller must have verified RECORD_AUDIO permission).
     */
    fun startHummingCapture() {
        if (recordingFlag.get() || _uiState.value is AiUiState.Loading) return
        recordingFlag.set(true)
        _isRecording.value = true
        _micLevel.value = 0f
        _liveNote.value = null
        _elapsedSeconds.value = 0f
        _playbackStatus.value = null

        viewModelScope.launch {
            try {
                val sample = withContext(Dispatchers.IO) {
                    MelodyRecorder.capture(
                        shouldContinue = { recordingFlag.get() },
                        onLevelUpdate = { level, note, elapsedMs ->
                            _micLevel.value = level
                            if (note != null) _liveNote.value = note
                            _elapsedSeconds.value = elapsedMs / 1000f
                        },
                    )
                }
                recordingFlag.set(false)
                _isRecording.value = false
                _micLevel.value = 0f

                if (sample.durationMs < 1_000L) {
                    _uiState.value = AiUiState.Error("Recording was too short. Please hum or sing for at least 3 seconds.")
                    return@launch
                }

                _uiState.value = AiUiState.Loading
                val result = aiRepository.identifySongFromHumming(
                    wavBytes = sample.wavBytes,
                    melodyContourNotes = sample.pitchContourSummary,
                    optionalHint = _optionalHint.value.trim(),
                )
                _uiState.value = AiUiState.Success(result)
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Hum identification timed out", e)
                _isRecording.value = false
                recordingFlag.set(false)
                _uiState.value = AiUiState.Error("Melody matching timed out after 60 seconds. Please try again.")
            } catch (e: CancellationException) {
                recordingFlag.set(false)
                _isRecording.value = false
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Hum identification failed", e)
                recordingFlag.set(false)
                _isRecording.value = false
                _uiState.value = AiUiState.Error(e.localizedMessage ?: "Failed to analyze hummed melody.")
            }
        }
    }

    fun stopHummingCapture() {
        recordingFlag.set(false)
    }

    fun playCandidate(
        candidate: HumMatchCandidate,
        playerConnection: PlayerConnection?,
    ) {
        if (playerConnection == null) {
            _playbackStatus.value = "Player service is connecting — try again shortly."
            return
        }
        viewModelScope.launch {
            _playbackStatus.value = "Finding \"${candidate.title}\"..."
            val matches = actionExecutor.resolveTrackQueries(listOf(candidate.title to candidate.artist))
            if (matches.isNotEmpty()) {
                withContext(Dispatchers.Main) {
                    playerConnection.playQueue(
                        ListQueue(
                            title = "Hum Match: ${candidate.title}",
                            items = matches.map { it.toMediaItem() },
                        )
                    )
                }
                _playbackStatus.value = "Playing \"${matches.first().title}\""
            } else {
                _playbackStatus.value = "No playable audio source found for \"${candidate.title}\"."
            }
        }
    }

    override fun onCleared() {
        recordingFlag.set(false)
        super.onCleared()
    }
}

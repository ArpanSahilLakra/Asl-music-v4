package com.mochyy.mochymusic.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mochyy.mochymusic.ai.AiMasteringRecipe
import com.mochyy.mochymusic.ai.AiUiState
import com.mochyy.mochymusic.ai.AudioOutputProfile
import com.mochyy.mochymusic.ai.GeminiMusicAiRepository
import com.mochyy.mochymusic.eq.AudioFxService
import com.mochyy.mochymusic.eq.EqualizerService
import com.mochyy.mochymusic.eq.audio.LoudnessParams
import com.mochyy.mochymusic.eq.audio.ReverbParams
import com.mochyy.mochymusic.eq.audio.SpatialMode
import com.mochyy.mochymusic.eq.audio.SpatialParams
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "AiMasteringVM"

@HiltViewModel
class AiMasteringViewModel @Inject constructor(
    private val aiRepository: GeminiMusicAiRepository,
    private val equalizerService: EqualizerService,
    private val audioFxService: AudioFxService,
) : ViewModel() {

    private val _selectedProfile = MutableStateFlow(AudioOutputProfile.REALME_SPEAKERS)
    val selectedProfile: StateFlow<AudioOutputProfile> = _selectedProfile.asStateFlow()

    private val _customGoal = MutableStateFlow("")
    val customGoal: StateFlow<String> = _customGoal.asStateFlow()

    private val _uiState = MutableStateFlow<AiUiState<AiMasteringRecipe>>(AiUiState.Idle)
    val uiState: StateFlow<AiUiState<AiMasteringRecipe>> = _uiState.asStateFlow()

    private val _isMasterApplied = MutableStateFlow(false)
    val isMasterApplied: StateFlow<Boolean> = _isMasterApplied.asStateFlow()

    private var lastRecipe: AiMasteringRecipe? = null

    fun selectProfile(profile: AudioOutputProfile) {
        _selectedProfile.value = profile
    }

    fun updateCustomGoal(goal: String) {
        _customGoal.value = goal
    }

    fun generateAndApplyMaster(songTitle: String?, artistName: String?) {
        if (_uiState.value is AiUiState.Loading) return
        viewModelScope.launch {
            _uiState.value = AiUiState.Loading
            try {
                val recipe = aiRepository.generateMasteringRecipe(
                    songTitle = songTitle,
                    artistName = artistName,
                    outputProfile = _selectedProfile.value,
                    customGoal = _customGoal.value.trim(),
                )
                lastRecipe = recipe
                applyRecipeToDsp(recipe)
                _uiState.value = AiUiState.Success(recipe)
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "AI mastering timed out", e)
                _uiState.value = AiUiState.Error("Mastering timed out after 60 seconds. Please retry.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "AI mastering failed", e)
                _uiState.value = AiUiState.Error(e.localizedMessage ?: "Failed to generate audio mastering.")
            }
        }
    }

    fun toggleBypass() {
        val recipe = lastRecipe ?: return
        if (_isMasterApplied.value) {
            resetFlat()
        } else {
            applyRecipeToDsp(recipe)
        }
    }

    fun resetFlat() {
        equalizerService.disable()
        audioFxService.spatial = SpatialParams(enabled = false)
        audioFxService.reverb = ReverbParams(enabled = false)
        audioFxService.loudness = LoudnessParams(enabled = false)
        _isMasterApplied.value = false
    }

    private fun applyRecipeToDsp(recipe: AiMasteringRecipe) {
        equalizerService.apply(recipe.toParametricEq())
        audioFxService.spatial = if (recipe.spatialEnabled) {
            SpatialParams(
                enabled = true,
                mode = SpatialMode.Speakers,
                speakerAngle = 32f,
                amount = recipe.spatialAmount,
            )
        } else {
            SpatialParams(enabled = false)
        }
        audioFxService.reverb = if (recipe.reverbEnabled) {
            ReverbParams(
                enabled = true,
                mix = recipe.reverbMix,
                roomSize = 0.48f,
                damping = 0.52f,
            )
        } else {
            ReverbParams(enabled = false)
        }
        audioFxService.loudness = if (recipe.loudnessBoostDb > 0.25f) {
            LoudnessParams(enabled = true, boostDb = recipe.loudnessBoostDb, compression = 0.25f)
        } else {
            LoudnessParams(enabled = false)
        }
        _isMasterApplied.value = true
    }
}

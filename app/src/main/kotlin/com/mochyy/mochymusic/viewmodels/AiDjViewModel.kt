package com.mochyy.mochymusic.viewmodels

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mochyy.mochymusic.ai.AiActionExecutor
import com.mochyy.mochymusic.ai.AiUiState
import com.mochyy.mochymusic.ai.DjPersona
import com.mochyy.mochymusic.ai.DjSetPlan
import com.mochyy.mochymusic.ai.GeminiMusicAiRepository
import com.mochyy.mochymusic.constants.CrossfadeDurationKey
import com.mochyy.mochymusic.constants.CrossfadeEnabledKey
import com.mochyy.mochymusic.constants.CrossmixMode
import com.mochyy.mochymusic.constants.CrossmixModeKey
import com.mochyy.mochymusic.db.MusicDatabase
import com.mochyy.mochymusic.extensions.toMediaItem
import com.mochyy.mochymusic.playback.PlayerConnection
import com.mochyy.mochymusic.playback.queues.ListQueue
import com.mochyy.mochymusic.utils.dataStore
import com.music.innertube.models.SongItem
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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

private const val TAG = "AiDjViewModel"

@Immutable
data class DjSetResult(
    val plan: DjSetPlan,
    val resolvedTracks: List<SongItem>,
)

@HiltViewModel
class AiDjViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val database: MusicDatabase,
    private val aiRepository: GeminiMusicAiRepository,
    private val actionExecutor: AiActionExecutor,
) : ViewModel() {

    private val _selectedPersona = MutableStateFlow(DjPersona.NIGHT_DRIVE)
    val selectedPersona: StateFlow<DjPersona> = _selectedPersona.asStateFlow()

    private val _customVibe = MutableStateFlow("")
    val customVibe: StateFlow<String> = _customVibe.asStateFlow()

    private val _crossfadeSeconds = MutableStateFlow(DjPersona.NIGHT_DRIVE.defaultCrossfadeSeconds)
    val crossfadeSeconds: StateFlow<Float> = _crossfadeSeconds.asStateFlow()

    private val _smartCrossmixEnabled = MutableStateFlow(true)
    val smartCrossmixEnabled: StateFlow<Boolean> = _smartCrossmixEnabled.asStateFlow()

    // Opt-in only per design rule: never force unwanted voice announcements
    private val _voiceIntrosEnabled = MutableStateFlow(false)
    val voiceIntrosEnabled: StateFlow<Boolean> = _voiceIntrosEnabled.asStateFlow()

    private val _uiState = MutableStateFlow<AiUiState<DjSetResult>>(AiUiState.Idle)
    val uiState: StateFlow<AiUiState<DjSetResult>> = _uiState.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    fun selectPersona(persona: DjPersona) {
        _selectedPersona.value = persona
        _crossfadeSeconds.value = persona.defaultCrossfadeSeconds
    }

    fun updateCustomVibe(text: String) {
        _customVibe.value = text
    }

    fun updateCrossfadeSeconds(seconds: Float) {
        _crossfadeSeconds.value = seconds.coerceIn(2f, 12f)
    }

    fun toggleSmartCrossmix(enabled: Boolean) {
        _smartCrossmixEnabled.value = enabled
        viewModelScope.launch {
            applyCrossmixSettings(enabled, _crossfadeSeconds.value)
        }
    }

    fun toggleVoiceIntros(enabled: Boolean) {
        _voiceIntrosEnabled.value = enabled
    }

    fun buildDjSet(
        currentTrackTitle: String?,
        currentArtist: String?,
        onOptionalVoiceIntro: (String) -> Unit,
    ) {
        if (_uiState.value is AiUiState.Loading) return
        val persona = _selectedPersona.value
        val vibe = _customVibe.value.trim()
        val fadeSecs = _crossfadeSeconds.value
        val useCrossmix = _smartCrossmixEnabled.value

        viewModelScope.launch {
            _uiState.value = AiUiState.Loading
            _statusMessage.value = null
            try {
                applyCrossmixSettings(useCrossmix, fadeSecs)

                val tasteContext = withContext(Dispatchers.IO) {
                    runCatching { database.allSongs().first() }
                        .getOrDefault(emptyList())
                        .sortedByDescending { it.song.totalPlayTime }
                        .take(8)
                        .joinToString(", ") { song ->
                            val artist = song.artists.firstOrNull()?.name ?: "Unknown"
                            "${song.song.title} - $artist"
                        }
                }

                val plan = aiRepository.generateDjSetPlan(
                    persona = persona,
                    currentTrackTitle = currentTrackTitle,
                    currentArtist = currentArtist,
                    libraryContext = tasteContext,
                    customVibe = vibe,
                )

                val resolved = actionExecutor.resolveTrackQueries(
                    plan.tracks.map { it.title to it.artist }
                )

                _uiState.value = AiUiState.Success(DjSetResult(plan = plan, resolvedTracks = resolved))
                _statusMessage.value = if (useCrossmix) {
                    "Smart CrossMix armed (${fadeSecs.toInt()}s blend) • ${resolved.size} playable tracks ready"
                } else {
                    "${resolved.size} playable tracks ready to launch"
                }

                if (_voiceIntrosEnabled.value && plan.optionalHostIntro.isNotBlank()) {
                    onOptionalVoiceIntro(plan.optionalHostIntro)
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "AI DJ set generation timed out", e)
                _uiState.value = AiUiState.Error("AI DJ timed out after 60 seconds. Please try again.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "AI DJ set generation failed", e)
                _uiState.value = AiUiState.Error(e.localizedMessage ?: "Failed to build AI DJ set.")
            }
        }
    }

    fun launchDjSetPlayback(
        playerConnection: PlayerConnection?,
        result: DjSetResult,
    ) {
        if (playerConnection == null) {
            _statusMessage.value = "Player service is connecting — try again in a moment."
            return
        }
        val items = result.resolvedTracks.map { it.toMediaItem() }
        if (items.isEmpty()) {
            _statusMessage.value = "No playable audio sources matched this set yet."
            return
        }
        viewModelScope.launch {
            applyCrossmixSettings(_smartCrossmixEnabled.value, _crossfadeSeconds.value)
            withContext(Dispatchers.Main) {
                playerConnection.playQueue(
                    ListQueue(
                        title = "AI DJ: ${result.plan.setTitle}",
                        items = items,
                    )
                )
            }
            _statusMessage.value = "Now mixing \"${result.plan.setTitle}\" (${items.size} tracks)"
        }
    }

    private suspend fun applyCrossmixSettings(enabled: Boolean, fadeSeconds: Float) {
        runCatching {
            appContext.dataStore.edit { prefs ->
                prefs[CrossfadeEnabledKey] = enabled
                prefs[CrossfadeDurationKey] = fadeSeconds.coerceIn(2f, 12f)
                prefs[CrossmixModeKey] = if (enabled) {
                    CrossmixMode.CROSSMIX.name
                } else {
                    CrossmixMode.OFF.name
                }
            }
        }
    }
}

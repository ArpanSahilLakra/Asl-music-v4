package com.mochyy.mochymusic.viewmodels

import androidx.lifecycle.ViewModel
import com.mochyy.mochymusic.ai.StemChannel
import com.mochyy.mochymusic.ai.StemMixState
import com.mochyy.mochymusic.eq.EqualizerService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class StemMixerViewModel @Inject constructor(
    private val equalizerService: EqualizerService,
) : ViewModel() {

    private val _mix = MutableStateFlow(StemMixState())
    val mix: StateFlow<StemMixState> = _mix.asStateFlow()

    fun setChannelGain(channel: StemChannel, gainDb: Float) {
        val clamped = gainDb.coerceIn(-12f, 8f)
        val updated = when (channel) {
            StemChannel.VOCALS -> _mix.value.copy(vocalsDb = clamped, activePresetName = "Custom Mix")
            StemChannel.BASS -> _mix.value.copy(bassDb = clamped, activePresetName = "Custom Mix")
            StemChannel.DRUMS -> _mix.value.copy(drumsDb = clamped, activePresetName = "Custom Mix")
            StemChannel.INSTRUMENTS -> _mix.value.copy(instrumentsDb = clamped, activePresetName = "Custom Mix")
        }
        applyMix(updated)
    }

    fun toggleMute(channel: StemChannel) {
        val current = _mix.value
        val updated = when (channel) {
            StemChannel.VOCALS -> current.copy(vocalsMuted = !current.vocalsMuted, activePresetName = "Custom Mix")
            StemChannel.BASS -> current.copy(bassMuted = !current.bassMuted, activePresetName = "Custom Mix")
            StemChannel.DRUMS -> current.copy(drumsMuted = !current.drumsMuted, activePresetName = "Custom Mix")
            StemChannel.INSTRUMENTS -> current.copy(instrumentsMuted = !current.instrumentsMuted, activePresetName = "Custom Mix")
        }
        applyMix(updated)
    }

    fun toggleSolo(channel: StemChannel) {
        val current = _mix.value
        val nextSolo = if (current.soloChannel == channel) null else channel
        applyMix(current.copy(soloChannel = nextSolo, activePresetName = if (nextSolo != null) "${nextSolo.name} Solo" else "Custom Mix"))
    }

    fun applyPreset(name: String) {
        val preset = when (name) {
            "Vocal Spotlight" -> StemMixState(
                vocalsDb = 5.0f,
                bassDb = -3.0f,
                drumsDb = -3.5f,
                instrumentsDb = -2.0f,
                activePresetName = "Vocal Spotlight",
            )
            "Karaoke Instrumental" -> StemMixState(
                vocalsDb = -12.0f,
                vocalsMuted = true,
                bassDb = 2.0f,
                drumsDb = 1.5f,
                instrumentsDb = 2.5f,
                activePresetName = "Karaoke Instrumental",
            )
            "Bass & Drums Groove" -> StemMixState(
                vocalsDb = -3.5f,
                bassDb = 6.0f,
                drumsDb = 4.5f,
                instrumentsDb = -1.5f,
                activePresetName = "Bass & Drums Groove",
            )
            "Acoustic Clarity" -> StemMixState(
                vocalsDb = 2.5f,
                bassDb = -1.0f,
                drumsDb = 0.0f,
                instrumentsDb = 4.0f,
                activePresetName = "Acoustic Clarity",
            )
            else -> StemMixState(activePresetName = "Balanced")
        }
        applyMix(preset)
    }

    fun resetFlat() {
        _mix.value = StemMixState(activePresetName = "Balanced")
        equalizerService.disable()
    }

    private fun applyMix(state: StemMixState) {
        _mix.value = state
        if (state.isNeutral()) {
            equalizerService.disable()
        } else {
            equalizerService.apply(state.toParametricEq())
        }
    }
}

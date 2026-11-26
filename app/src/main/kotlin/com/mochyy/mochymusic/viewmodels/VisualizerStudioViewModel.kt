package com.mochyy.mochymusic.viewmodels

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mochyy.mochymusic.db.MusicDatabase
import com.mochyy.mochymusic.db.entities.CustomVisualizerPresetEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class VisualizerStudioMode(val label: String, val subtitle: String) {
    PERSPECTIVE_3D("3D Horizon", "Depth-projected reactive grid"),
    CIRCULAR_RING("Orbital Ring", "360° radial harmonic halo"),
    NEON_WAVEFORM("Oscilloscope", "Ribbon waveform & phase glow"),
    FREQUENCY_BARS("Studio Bars", "40-band peak-hold equalizer"),
    FLUID_RIBBONS("Fluid Aura", "Layered harmonic silk waves"),
    BEAT_PARTICLES("Pulse Starfield", "Beat-reactive 3D particle burst"),
}

@Immutable
data class VisualizerStudioConfig(
    val mode: VisualizerStudioMode = VisualizerStudioMode.PERSPECTIVE_3D,
    val sensitivity: Float = 1.15f,
    val glowIntensity: Float = 0.85f,
    val albumReactiveLighting: Boolean = true,
    val batterySaverFps: Boolean = false,
    val gyroParallaxEnabled: Boolean = true,
    val accentArgb: Int = 0xFF6366F1.toInt(),
)

@HiltViewModel
class VisualizerStudioViewModel @Inject constructor(
    private val database: MusicDatabase,
) : ViewModel() {

    private val _config = MutableStateFlow(VisualizerStudioConfig())
    val config: StateFlow<VisualizerStudioConfig> = _config.asStateFlow()

    val savedPresets: StateFlow<List<CustomVisualizerPresetEntity>> = database.aiStudioDao
        .observeVisualizerPresets()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    fun selectMode(mode: VisualizerStudioMode) {
        _config.value = _config.value.copy(mode = mode)
    }

    fun setSensitivity(value: Float) {
        _config.value = _config.value.copy(sensitivity = value.coerceIn(0.4f, 2.2f))
    }

    fun setGlowIntensity(value: Float) {
        _config.value = _config.value.copy(glowIntensity = value.coerceIn(0.1f, 1.5f))
    }

    fun setAlbumReactiveLighting(enabled: Boolean) {
        _config.value = _config.value.copy(albumReactiveLighting = enabled)
    }

    fun setBatterySaverFps(enabled: Boolean) {
        _config.value = _config.value.copy(batterySaverFps = enabled)
    }

    fun setGyroParallaxEnabled(enabled: Boolean) {
        _config.value = _config.value.copy(gyroParallaxEnabled = enabled)
    }

    fun setAccentArgb(argb: Int) {
        _config.value = _config.value.copy(accentArgb = argb, albumReactiveLighting = false)
    }

    fun applyPreset(preset: CustomVisualizerPresetEntity) {
        val parsedMode = runCatching { VisualizerStudioMode.valueOf(preset.mode) }
            .getOrDefault(VisualizerStudioMode.PERSPECTIVE_3D)
        _config.value = _config.value.copy(
            mode = parsedMode,
            sensitivity = preset.sensitivity,
            glowIntensity = preset.glowIntensity,
            albumReactiveLighting = preset.albumReactiveLighting,
            batterySaverFps = preset.batterySaverFps,
            accentArgb = preset.accentArgb,
        )
    }

    fun saveCurrentPreset(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val current = _config.value
        viewModelScope.launch(Dispatchers.IO) {
            database.aiStudioDao.insertVisualizerPreset(
                CustomVisualizerPresetEntity(
                    name = trimmed,
                    mode = current.mode.name,
                    sensitivity = current.sensitivity,
                    glowIntensity = current.glowIntensity,
                    albumReactiveLighting = current.albumReactiveLighting,
                    batterySaverFps = current.batterySaverFps,
                    accentArgb = current.accentArgb,
                )
            )
        }
    }

    fun deletePreset(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            database.aiStudioDao.deleteVisualizerPreset(id)
        }
    }
}

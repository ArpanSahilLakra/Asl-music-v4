package com.mochyy.mochymusic.playback.pulse

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import com.mochyy.mochymusic.core.device.DeviceHardwareSnapshot
import com.mochyy.mochymusic.core.device.Realme15DeviceCapabilityManager
import com.mochyy.mochymusic.ui.player.LivingArtworkPalette
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * Custom lighting modes for ASL Pulse Sync.
 */
enum class AslPulseLightingMode(
    val title: String,
    val description: String,
    val requiresDirectRgbApi: Boolean,
) {
    BEAT_SYNC(
        title = "Beat Sync",
        description = "Pulses on musical beats and bass drops",
        requiresDirectRgbApi = false,
    ),
    BASS_PULSE(
        title = "Bass Pulse",
        description = "Reacts to low-frequency sub-bass and kick energy",
        requiresDirectRgbApi = false,
    ),
    ARTWORK_MATCH(
        title = "Artwork Match",
        description = "Smoothly transitions rear halo colors from album artwork",
        requiresDirectRgbApi = false,
    ),
    BREATHING(
        title = "Breathing",
        description = "Slow breathing illumination scaled by song energy",
        requiresDirectRgbApi = false,
    ),
    RAINBOW_FLOW(
        title = "Rainbow Flow",
        description = "Smoothly cycles through spectrum colors with tempo",
        requiresDirectRgbApi = false,
    ),
    PARTY_MODE(
        title = "Party Mode",
        description = "Energetic multi-tone rhythmic flashes on beat peaks",
        requiresDirectRgbApi = false,
    ),
    STATIC_COLOR(
        title = "Static Colour",
        description = "Steady user-selected accent illumination while playing",
        requiresDirectRgbApi = false,
    ),
    OFF(
        title = "Off",
        description = "Disable ASL Pulse Sync lighting updates",
        requiresDirectRgbApi = false,
    ),
}

/**
 * Honest hardware/OS integration status for the Realme 15 5G (RMX5106) rear Pulse Light.
 * Never claims direct unprivileged RGB hardware register writes when the OS restricts them.
 */
enum class PulseSyncIntegrationStatus(
    val badgeText: String,
    val technicalSummary: String,
) {
    HARDWARE_INTEGRATION_AVAILABLE(
        badgeText = "Hardware Integration Available",
        technicalSummary = "Compatible Android light controller detected and verified at runtime.",
    ),
    NATIVE_REALME_SYNC_REQUIRED(
        badgeText = "Native Realme Synchronization Required",
        technicalSummary = "realme UI 6.0 does not expose a public third-party SDK for raw RGB LED register writes. " +
            "ASL Beats registers an active USAGE_MEDIA + CONTENT_TYPE_MUSIC MediaSession so realme UI's native " +
            "Pulse Light 'Music Rhythm' synchronizes the physical rear light with playback.",
    ),
    SYSTEM_CONFIGURATION_REQUIRED(
        badgeText = "Permission or System Configuration Required",
        technicalSummary = "Battery Saver or background restriction is active. Enable Pulse Light Music Rhythm in " +
            "Realme Settings -> Special Features -> Pulse Light and allow ASL Beats background playback.",
    ),
    UNSUPPORTED_DEVICE(
        badgeText = "Unsupported Device or Software Version",
        technicalSummary = "Physical Rear Pulse Light hardware is exclusive to Realme 15 5G (RMX5106) and compatible " +
            "realme UI devices. In-player beat synchronization preview remains available.",
    ),
}

/**
 * Beat event classification for lighting behaviour.
 */
enum class PulseBeatEvent(val label: String) {
    IDLE("Paused / Idle"),
    BASS_DROP("Bass Drop — Strong Short Pulse"),
    NORMAL_BEAT("Normal Beat — Gentle Pulse"),
    HIGH_ENERGY("High Energy — Brighter Rhythmic Glow"),
    LOW_ENERGY("Low Energy — Subtle Breathing"),
    SONG_TRANSITION("Song Transition — Smooth Colour Shift"),
}

/**
 * Real-time synchronized lighting frame emitted by [AslPulseSyncEngine].
 */
@Immutable
data class PulseSyncFrameState(
    val enabled: Boolean = true,
    val status: PulseSyncIntegrationStatus = PulseSyncIntegrationStatus.NATIVE_REALME_SYNC_REQUIRED,
    val mode: AslPulseLightingMode = AslPulseLightingMode.BEAT_SYNC,
    val activeColor: Color = Color(0xFF6366F1),
    val currentIntensity: Float = 0f,
    val brightnessLimit: Float = 0.85f,
    val sensitivity: Float = 0.70f,
    val batterySaverEnabled: Boolean = true,
    val pauseWhenScreenOff: Boolean = false,
    val beatEvent: PulseBeatEvent = PulseBeatEvent.IDLE,
    val isPlaying: Boolean = false,
    val nativeSettingsIntentAvailable: Boolean = false,
    val mediaSessionExtrasBundle: Bundle = Bundle.EMPTY,
)

/**
 * Inspects the installed realme UI / Android OS to determine how the physical rear Pulse Light
 * can be integrated using documented Android APIs without root, reflection, or private services.
 */
object PulseLightCapabilityInspector {

    // Documented & standard OEM activity candidates for Realme / OPLUS Pulse Light / Breathing Light settings
    private val RealmePulseSettingsCandidates = listOf(
        ComponentName("com.oplus.breathinglight", "com.oplus.breathinglight.BreathingLightSettingsActivity"),
        ComponentName("com.coloros.breathinglight", "com.coloros.breathinglight.BreathingLightSettingsActivity"),
        ComponentName("com.android.settings", "com.android.settings.Settings\$BreathingLightSettingsActivity"),
        ComponentName("com.android.settings", "com.oplus.settings.feature.breathinglight.BreathingLightSettingsActivity"),
    )

    private val RealmePulseIntentActions = listOf(
        "oplus.intent.action.BREATHING_LIGHT_SETTINGS",
        "com.oplus.breathinglight.ACTION_SETTINGS",
        "coloros.intent.action.BREATHING_LIGHT_SETTINGS",
    )

    fun inspectStatus(
        context: Context,
        hardware: DeviceHardwareSnapshot,
    ): PulseSyncIntegrationStatus {
        if (!hardware.isRealmeDevice && !hardware.isRealme15Rmx5106) {
            return PulseSyncIntegrationStatus.UNSUPPORTED_DEVICE
        }
        if (hardware.isBatterySaverActive) {
            return PulseSyncIntegrationStatus.SYSTEM_CONFIGURATION_REQUIRED
        }
        // On Realme 15 5G (RMX5106 / realme UI 6.0), third-party apps synchronize physical rear RGB
        // through realme UI's native Pulse Light Music Rhythm + MediaSession pipeline.
        return PulseSyncIntegrationStatus.NATIVE_REALME_SYNC_REQUIRED
    }

    /**
     * Resolves an official system Settings [Intent] to open Realme Pulse Light settings if present,
     * falling back safely to standard Android Sound/Vibration or Application settings.
     */
    fun resolvePulseLightSettingsIntent(context: Context): Intent {
        val pm = context.packageManager

        for (component in RealmePulseSettingsCandidates) {
            val candidate = Intent().setComponent(component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (pm.resolveActivity(candidate, PackageManager.MATCH_DEFAULT_ONLY) != null) {
                return candidate
            }
        }

        for (action in RealmePulseIntentActions) {
            val candidate = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (pm.resolveActivity(candidate, PackageManager.MATCH_DEFAULT_ONLY) != null) {
                return candidate
            }
        }

        val soundSettings = Intent(Settings.ACTION_SOUND_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (pm.resolveActivity(soundSettings, PackageManager.MATCH_DEFAULT_ONLY) != null) {
            return soundSettings
        }

        return Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun hasDedicatedPulseSettingsActivity(context: Context): Boolean {
        val pm = context.packageManager
        return RealmePulseSettingsCandidates.any { comp ->
            pm.resolveActivity(Intent().setComponent(comp), PackageManager.MATCH_DEFAULT_ONLY) != null
        } || RealmePulseIntentActions.any { act ->
            pm.resolveActivity(Intent(act), PackageManager.MATCH_DEFAULT_ONLY) != null
        }
    }
}

/**
 * Lightweight, rate-limited ASL Pulse Sync Engine for Realme 15 5G (RMX5106).
 *
 * Reuses the Living Album Art playback timeline and [com.mochyy.mochymusic.playback.audio.AudioSpectrum]
 * energies (`bass`, `mid`, `treble`) so zero extra FFT or microphone work is performed.
 */
@Stable
object AslPulseSyncEngine {

    // Rate-limit updates to avoid rapid uncontrolled hardware/session updates
    private const val MIN_UPDATE_INTERVAL_NORMAL_MS = 50L       // Max 20 updates/sec
    private const val MIN_UPDATE_INTERVAL_BATTERY_SAVER_MS = 125L // Max 8 updates/sec
    private const val SONG_TRANSITION_DURATION_MS = 900L

    private val _state = MutableStateFlow(PulseSyncFrameState())
    val state: StateFlow<PulseSyncFrameState> = _state.asStateFlow()

    private var lastUpdateTimestampMs: Long = 0L
    private var lastSongId: String? = null
    private var songTransitionStartMs: Long = 0L
    private var previousArtworkColor: Color = Color(0xFF6366F1)
    private var smoothedIntensity: Float = 0f
    private var previousBass: Float = 0f

    val PresetColors: List<Color> = listOf(
        Color(0xFF6366F1), // Electric Indigo
        Color(0xFFEC4899), // Pulse Magenta
        Color(0xFF06B6D4), // Cyber Cyan
        Color(0xFF10B981), // Emerald Beat
        Color(0xFFF59E0B), // Realme Gold
        Color(0xFFEF4444), // Crimson Drop
        Color(0xFFA855F7), // Neon Violet
    )

    fun refreshDeviceStatus(context: Context) {
        val snapshot = Realme15DeviceCapabilityManager.refreshSnapshot(context)
        val status = PulseLightCapabilityInspector.inspectStatus(context, snapshot)
        val hasDedicated = PulseLightCapabilityInspector.hasDedicatedPulseSettingsActivity(context)
        _state.value = _state.value.copy(
            status = status,
            nativeSettingsIntentAvailable = hasDedicated || snapshot.isRealmeDevice,
        )
    }

    /**
     * Steps the synchronization state using the shared playback timeline and spectrum energies.
     */
    fun onPlaybackFrame(
        context: Context?,
        enabled: Boolean,
        mode: AslPulseLightingMode,
        userColorArgb: Int,
        brightness: Float,
        sensitivity: Float,
        batterySaverOption: Boolean,
        pauseScreenOff: Boolean,
        isScreenInteractive: Boolean,
        isPlaying: Boolean,
        songId: String?,
        positionMs: Long,
        bass: Float,
        mid: Float,
        treble: Float,
        artworkPalette: LivingArtworkPalette,
        nowElapsedMs: Long = android.os.SystemClock.elapsedRealtime(),
    ): PulseSyncFrameState {
        val hardware = Realme15DeviceCapabilityManager.hardwareSnapshot.value
        val status = if (context != null) {
            PulseLightCapabilityInspector.inspectStatus(context, hardware)
        } else if (hardware.isRealmeDevice || hardware.isRealme15Rmx5106) {
            if (hardware.isBatterySaverActive) PulseSyncIntegrationStatus.SYSTEM_CONFIGURATION_REQUIRED
            else PulseSyncIntegrationStatus.NATIVE_REALME_SYNC_REQUIRED
        } else {
            PulseSyncIntegrationStatus.UNSUPPORTED_DEVICE
        }

        // Stop lighting immediately when disabled, mode is OFF, playback stops/pauses, or screen-off pause is requested
        if (!enabled || mode == AslPulseLightingMode.OFF || !isPlaying || (pauseScreenOff && !isScreenInteractive)) {
            smoothedIntensity = 0f
            previousBass = 0f
            val idleState = _state.value.copy(
                enabled = enabled,
                status = status,
                mode = mode,
                activeColor = Color(userColorArgb),
                currentIntensity = 0f,
                brightnessLimit = brightness.coerceIn(0.1f, 1f),
                sensitivity = sensitivity.coerceIn(0.1f, 1f),
                batterySaverEnabled = batterySaverOption,
                pauseWhenScreenOff = pauseScreenOff,
                beatEvent = PulseBeatEvent.IDLE,
                isPlaying = isPlaying,
            )
            _state.value = idleState
            return idleState
        }

        val effectiveBatterySave = batterySaverOption || hardware.isBatterySaverActive ||
            hardware.thermalSeverity.level >= 2
        val minInterval = if (effectiveBatterySave) {
            MIN_UPDATE_INTERVAL_BATTERY_SAVER_MS
        } else {
            MIN_UPDATE_INTERVAL_NORMAL_MS
        }

        if (nowElapsedMs - lastUpdateTimestampMs < minInterval && songId == lastSongId) {
            return _state.value
        }
        lastUpdateTimestampMs = nowElapsedMs

        if (songId != null && songId != lastSongId) {
            previousArtworkColor = _state.value.activeColor
            lastSongId = songId
            songTransitionStartMs = nowElapsedMs
        }

        val inSongTransition = (nowElapsedMs - songTransitionStartMs) in 0L..SONG_TRANSITION_DURATION_MS
        val gain = (0.5f + sensitivity.coerceIn(0.1f, 1f) * 1.1f)
        val scaledBass = (bass * gain).coerceIn(0f, 1f)
        val scaledMid = (mid * gain).coerceIn(0f, 1f)
        val scaledTreble = (treble * gain).coerceIn(0f, 1f)
        val overallEnergy = (scaledBass * 0.55f + scaledMid * 0.30f + scaledTreble * 0.15f).coerceIn(0f, 1f)

        val bassDelta = (scaledBass - previousBass).coerceAtLeast(0f)
        previousBass = scaledBass

        val beatEvent = when {
            inSongTransition -> PulseBeatEvent.SONG_TRANSITION
            bassDelta >= 0.22f && scaledBass >= 0.62f -> PulseBeatEvent.BASS_DROP
            bassDelta >= 0.10f && scaledBass >= 0.38f -> PulseBeatEvent.NORMAL_BEAT
            overallEnergy >= 0.55f -> PulseBeatEvent.HIGH_ENERGY
            else -> PulseBeatEvent.LOW_ENERGY
        }

        val timeSec = (positionMs.coerceAtLeast(0L) / 1000f)
        val userColor = Color(userColorArgb)

        val rawTargetIntensity: Float
        val targetColor: Color

        when (mode) {
            AslPulseLightingMode.BEAT_SYNC -> {
                rawTargetIntensity = when (beatEvent) {
                    PulseBeatEvent.BASS_DROP -> 1.0f
                    PulseBeatEvent.NORMAL_BEAT -> (0.55f + scaledBass * 0.45f).coerceAtMost(0.95f)
                    PulseBeatEvent.HIGH_ENERGY -> (0.42f + overallEnergy * 0.45f).coerceAtMost(0.88f)
                    else -> (0.18f + 0.12f * sin(timeSec * 2.2f)).coerceIn(0.08f, 0.35f)
                }
                targetColor = if (beatEvent == PulseBeatEvent.BASS_DROP) {
                    lerp(artworkPalette.primary, artworkPalette.particleTint, 0.5f)
                } else {
                    artworkPalette.primary
                }
            }

            AslPulseLightingMode.BASS_PULSE -> {
                rawTargetIntensity = (scaledBass * scaledBass * 1.15f + bassDelta * 0.6f).coerceIn(0.05f, 1f)
                targetColor = lerp(artworkPalette.bassGlow, userColor, 0.35f)
            }

            AslPulseLightingMode.ARTWORK_MATCH -> {
                rawTargetIntensity = (0.30f + overallEnergy * 0.65f).coerceIn(0.15f, 0.95f)
                val transitionFrac = if (inSongTransition) {
                    ((nowElapsedMs - songTransitionStartMs).toFloat() / SONG_TRANSITION_DURATION_MS).coerceIn(0f, 1f)
                } else {
                    1f
                }
                val currentArtMix = lerp(
                    artworkPalette.primary,
                    artworkPalette.secondary,
                    0.5f + 0.5f * sin(timeSec * 0.8f),
                )
                targetColor = lerp(previousArtworkColor, currentArtMix, transitionFrac)
            }

            AslPulseLightingMode.BREATHING -> {
                val breathSpeed = if (overallEnergy > 0.5f) 2.6f else 1.3f
                val wave = 0.5f + 0.5f * sin(timeSec * breathSpeed)
                rawTargetIntensity = (0.15f + wave * (0.45f + overallEnergy * 0.40f)).coerceIn(0.10f, 0.92f)
                targetColor = userColor
            }

            AslPulseLightingMode.RAINBOW_FLOW -> {
                val hue = ((timeSec * (28f + overallEnergy * 40f)) % 360f + 360f) % 360f
                val hsl = floatArrayOf(hue, 0.85f, 0.56f)
                targetColor = Color(ColorUtils.HSLToColor(hsl))
                rawTargetIntensity = (0.35f + overallEnergy * 0.55f).coerceIn(0.20f, 0.95f)
            }

            AslPulseLightingMode.PARTY_MODE -> {
                val index = ((positionMs / 220L).toInt().let { abs(it) }) % PresetColors.size
                targetColor = PresetColors[index]
                rawTargetIntensity = (0.40f + scaledBass * 0.60f + bassDelta * 0.50f).coerceIn(0.25f, 1f)
            }

            AslPulseLightingMode.STATIC_COLOR -> {
                targetColor = userColor
                rawTargetIntensity = 0.75f
            }

            AslPulseLightingMode.OFF -> {
                targetColor = userColor
                rawTargetIntensity = 0f
            }
        }

        val cappedBrightness = brightness.coerceIn(0.1f, 1f) * if (effectiveBatterySave) 0.72f else 1f
        val desiredIntensity = (rawTargetIntensity * cappedBrightness).coerceIn(0f, 1f)
        smoothedIntensity += (desiredIntensity - smoothedIntensity) * 0.45f

        val nextState = PulseSyncFrameState(
            enabled = true,
            status = status,
            mode = mode,
            activeColor = targetColor,
            currentIntensity = smoothedIntensity.coerceIn(0f, 1f),
            brightnessLimit = brightness.coerceIn(0.1f, 1f),
            sensitivity = sensitivity.coerceIn(0.1f, 1f),
            batterySaverEnabled = batterySaverOption,
            pauseWhenScreenOff = pauseScreenOff,
            beatEvent = beatEvent,
            isPlaying = true,
            nativeSettingsIntentAvailable = _state.value.nativeSettingsIntentAvailable || hardware.isRealmeDevice,
        )
        _state.value = nextState
        return nextState
    }

    fun stopAndRelease() {
        smoothedIntensity = 0f
        previousBass = 0f
        _state.value = _state.value.copy(
            currentIntensity = 0f,
            beatEvent = PulseBeatEvent.IDLE,
            isPlaying = false,
        )
    }
}

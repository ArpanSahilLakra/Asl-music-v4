package com.mochyy.mochymusic.ui.player.pulse

import android.os.PowerManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.constants.AdaptivePerformanceModeKey
import com.mochyy.mochymusic.constants.AslPulseSyncBatterySaverKey
import com.mochyy.mochymusic.constants.AslPulseSyncBrightnessKey
import com.mochyy.mochymusic.constants.AslPulseSyncColorArgbKey
import com.mochyy.mochymusic.constants.AslPulseSyncEnabledKey
import com.mochyy.mochymusic.constants.AslPulseSyncModeKey
import com.mochyy.mochymusic.constants.AslPulseSyncPauseScreenOffKey
import com.mochyy.mochymusic.constants.AslPulseSyncSensitivityKey
import com.mochyy.mochymusic.constants.LivingAlbumArtEnabledKey
import com.mochyy.mochymusic.constants.LivingArt3dParallaxKey
import com.mochyy.mochymusic.constants.LivingArtBassReactionKey
import com.mochyy.mochymusic.constants.LivingArtBeatLightingKey
import com.mochyy.mochymusic.constants.LivingArtParticlesEnabledKey
import com.mochyy.mochymusic.core.device.AdaptivePerformanceMode
import com.mochyy.mochymusic.core.device.Realme15DeviceCapabilityManager
import com.mochyy.mochymusic.playback.pulse.AslPulseLightingMode
import com.mochyy.mochymusic.playback.pulse.AslPulseSyncEngine
import com.mochyy.mochymusic.playback.pulse.PulseLightCapabilityInspector
import com.mochyy.mochymusic.playback.pulse.PulseSyncIntegrationStatus
import com.mochyy.mochymusic.ui.player.LivingAlbumArtState
import com.mochyy.mochymusic.ui.player.LivingArtworkPalette
import com.mochyy.mochymusic.ui.player.menu.MenuSheetSurface
import com.mochyy.mochymusic.utils.rememberEnumPreference
import com.mochyy.mochymusic.utils.rememberPreference
import kotlin.math.roundToInt

/**
 * Runtime composable bridge that synchronizes [AslPulseSyncEngine] with the shared
 * [LivingAlbumArtState] timeline and Media3 playback state without creating a second
 * audio-analysis pipeline.
 */
@Composable
fun AslPulseSyncEffectsBridge(
    livingState: LivingAlbumArtState,
    palette: LivingArtworkPalette,
    songId: String?,
    isPlaying: Boolean,
) {
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current
    val (enabled) = rememberPreference(AslPulseSyncEnabledKey, defaultValue = true)
    val (mode) = rememberEnumPreference(AslPulseSyncModeKey, AslPulseLightingMode.BEAT_SYNC)
    val (colorArgb) = rememberPreference(AslPulseSyncColorArgbKey, defaultValue = 0xFF6366F1.toInt())
    val (brightness) = rememberPreference(AslPulseSyncBrightnessKey, defaultValue = 0.85f)
    val (sensitivity) = rememberPreference(AslPulseSyncSensitivityKey, defaultValue = 0.70f)
    val (batterySaver) = rememberPreference(AslPulseSyncBatterySaverKey, defaultValue = true)
    val (pauseScreenOff) = rememberPreference(AslPulseSyncPauseScreenOffKey, defaultValue = false)

    LaunchedEffect(Unit) {
        AslPulseSyncEngine.refreshDeviceStatus(context)
    }

    LaunchedEffect(
        livingState.frameTick,
        enabled,
        mode,
        colorArgb,
        brightness,
        sensitivity,
        batterySaver,
        pauseScreenOff,
        isPlaying,
        songId,
    ) {
        val pm = context.getSystemService(android.content.Context.POWER_SERVICE) as? PowerManager
        val interactive = pm?.isInteractive ?: true
        val posMs = playerConnection?.player?.currentPosition ?: 0L

        AslPulseSyncEngine.onPlaybackFrame(
            context = context,
            enabled = enabled,
            mode = mode,
            userColorArgb = colorArgb,
            brightness = brightness,
            sensitivity = sensitivity,
            batterySaverOption = batterySaver,
            pauseScreenOff = pauseScreenOff,
            isScreenInteractive = interactive,
            isPlaying = isPlaying,
            songId = songId,
            positionMs = posMs,
            bass = livingState.bassEnergy,
            mid = livingState.midEnergy,
            treble = livingState.trebleEnergy,
            artworkPalette = palette,
        )
    }
}

/**
 * Full-featured sheet inside the immersive player providing:
 * 1. ASL Pulse Sync (Realme 15 5G RMX5106 Rear RGB Music Synchronization) card & controls
 * 2. Realme 15 5G Adaptive Performance Mode & Living Album Art controls
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AslPulseSyncSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val pulseState by AslPulseSyncEngine.state.collectAsState()
    val hardware by Realme15DeviceCapabilityManager.hardwareSnapshot.collectAsState()
    val renderingProfile by Realme15DeviceCapabilityManager.renderingProfile.collectAsState()

    val (pulseEnabled, setPulseEnabled) = rememberPreference(AslPulseSyncEnabledKey, defaultValue = true)
    val (pulseMode, setPulseMode) = rememberEnumPreference(AslPulseSyncModeKey, AslPulseLightingMode.BEAT_SYNC)
    val (pulseColorArgb, setPulseColorArgb) = rememberPreference(
        AslPulseSyncColorArgbKey,
        defaultValue = 0xFF6366F1.toInt(),
    )
    val (brightness, setBrightness) = rememberPreference(AslPulseSyncBrightnessKey, defaultValue = 0.85f)
    val (sensitivity, setSensitivity) = rememberPreference(AslPulseSyncSensitivityKey, defaultValue = 0.70f)
    val (batterySaver, setBatterySaver) = rememberPreference(AslPulseSyncBatterySaverKey, defaultValue = true)
    val (pauseScreenOff, setPauseScreenOff) = rememberPreference(AslPulseSyncPauseScreenOffKey, defaultValue = false)

    val (perfMode, setPerfMode) = rememberEnumPreference(
        AdaptivePerformanceModeKey,
        AdaptivePerformanceMode.AUTO,
    )
    val (livingEnabled, setLivingEnabled) = rememberPreference(LivingAlbumArtEnabledKey, defaultValue = true)
    val (bassReaction, setBassReaction) = rememberPreference(LivingArtBassReactionKey, defaultValue = true)
    val (particlesEnabled, setParticlesEnabled) = rememberPreference(LivingArtParticlesEnabledKey, defaultValue = true)
    val (beatLighting, setBeatLighting) = rememberPreference(LivingArtBeatLightingKey, defaultValue = true)
    val (parallax3d, setParallax3d) = rememberPreference(LivingArt3dParallaxKey, defaultValue = true)

    LaunchedEffect(perfMode) {
        Realme15DeviceCapabilityManager.setUserPerformanceMode(context, perfMode)
    }

    LaunchedEffect(Unit) {
        Realme15DeviceCapabilityManager.refreshSnapshot(context)
        AslPulseSyncEngine.refreshDeviceStatus(context)
    }

    MenuSheetSurface(
        onDismiss = onDismiss,
        modifier = modifier.testTag("asl_pulse_sync_sheet"),
    ) { _ ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // 1. ASL Pulse Sync Header & Rear Ring Visualizer Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0xFF0B0B12))
                    .border(
                        width = 1.dp,
                        color = pulseState.activeColor.copy(alpha = 0.38f),
                        shape = RoundedCornerShape(22.dp),
                    )
                    .padding(16.dp)
                    .testTag("asl_pulse_sync_card"),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    PulseHaloPreview(
                        color = pulseState.activeColor,
                        intensity = if (pulseEnabled) pulseState.currentIntensity else 0f,
                        modifier = Modifier.size(52.dp),
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "ASL Pulse Sync",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Realme 15 5G (RMX5106) Rear RGB Music Sync",
                            color = Color(0xFFB4B6C8),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Switch(
                        checked = pulseEnabled,
                        onCheckedChange = setPulseEnabled,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = pulseState.activeColor,
                        ),
                        modifier = Modifier.testTag("asl_pulse_sync_enable_switch"),
                    )
                }

                // Connection / Integration Status Banner
                val statusColor = when (pulseState.status) {
                    PulseSyncIntegrationStatus.HARDWARE_INTEGRATION_AVAILABLE -> Color(0xFF10B981)
                    PulseSyncIntegrationStatus.NATIVE_REALME_SYNC_REQUIRED -> Color(0xFF6366F1)
                    PulseSyncIntegrationStatus.SYSTEM_CONFIGURATION_REQUIRED -> Color(0xFFF59E0B)
                    PulseSyncIntegrationStatus.UNSUPPORTED_DEVICE -> Color(0xFF94A3B8)
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(statusColor.copy(alpha = 0.14f))
                        .border(1.dp, statusColor.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(statusColor),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = pulseState.status.badgeText,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.testTag("asl_pulse_status_badge"),
                        )
                    }
                    Text(
                        text = pulseState.status.technicalSummary,
                        color = Color(0xFFD1D5DB),
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                    )
                    Text(
                        text = "Live Beat State: ${pulseState.beatEvent.label}",
                        color = pulseState.activeColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }

                // Button to open native Realme Pulse Light / Sound settings
                Button(
                    onClick = {
                        runCatching {
                            val intent = PulseLightCapabilityInspector.resolvePulseLightSettingsIntent(context)
                            context.startActivity(intent)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.12f),
                        contentColor = Color.White,
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("open_realme_pulse_settings_button"),
                ) {
                    Text(
                        text = "Open Realme Pulse Light System Settings",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                // Lighting Modes Selector
                Text(
                    text = "Lighting Mode",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    AslPulseLightingMode.entries.forEach { modeItem ->
                        val selected = pulseMode == modeItem
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (selected) pulseState.activeColor.copy(alpha = 0.28f)
                                    else Color.White.copy(alpha = 0.07f),
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (selected) pulseState.activeColor else Color.Transparent,
                                    shape = RoundedCornerShape(12.dp),
                                )
                                .clickable { setPulseMode(modeItem) }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                .testTag("pulse_mode_${modeItem.name.lowercase()}"),
                        ) {
                            Text(
                                text = modeItem.title,
                                color = if (selected) Color.White else Color(0xFFB4B6C8),
                                fontSize = 12.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            )
                        }
                    }
                }
                Text(
                    text = pulseMode.description,
                    color = Color(0xFF9CA3AF),
                    fontSize = 11.sp,
                )

                // Colour Picker Swatches
                Text(
                    text = "Pulse Accent Colour",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    AslPulseSyncEngine.PresetColors.forEach { swatch ->
                        val swatchArgb = swatch.toArgb()
                        val isSelected = pulseColorArgb == swatchArgb
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(swatch)
                                .border(
                                    width = if (isSelected) 2.5.dp else 1.dp,
                                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.25f),
                                    shape = CircleShape,
                                )
                                .clickable { setPulseColorArgb(swatchArgb) },
                        )
                    }
                }

                // Brightness & Sensitivity Sliders
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Brightness Cap", color = Color.White, fontSize = 12.sp)
                        Text("${(brightness * 100).roundToInt()}%", color = Color(0xFFB4B6C8), fontSize = 12.sp)
                    }
                    Slider(
                        value = brightness,
                        onValueChange = setBrightness,
                        valueRange = 0.15f..1f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = pulseState.activeColor,
                        ),
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Beat Sensitivity", color = Color.White, fontSize = 12.sp)
                        Text("${(sensitivity * 100).roundToInt()}%", color = Color(0xFFB4B6C8), fontSize = 12.sp)
                    }
                    Slider(
                        value = sensitivity,
                        onValueChange = setSensitivity,
                        valueRange = 0.15f..1f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = pulseState.activeColor,
                        ),
                    )
                }

                ToggleOptionRow(
                    title = "7,000mAh Battery-Saving Throttle",
                    subtitle = "Caps lighting updates to 8Hz and 72% peak intensity",
                    checked = batterySaver,
                    onCheckedChange = setBatterySaver,
                    testTag = "pulse_battery_saver_switch",
                )

                ToggleOptionRow(
                    title = "Pause Pulse Light When Screen Off",
                    subtitle = "Stops decorative RGB updates while screen is locked",
                    checked = pauseScreenOff,
                    onCheckedChange = setPauseScreenOff,
                    testTag = "pulse_screen_off_switch",
                )
            }

            // 2. Realme 15 5G (RMX5106) Hardware & Adaptive Performance Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0xFF0B0B12))
                    .border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(22.dp),
                    )
                    .padding(16.dp)
                    .testTag("rmx5106_performance_card"),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Realme 15 5G (RMX5106) Adaptive Engine",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "${hardware.model} • ${hardware.detectedRealmeUiLabel} • " +
                        "${hardware.ramTier.label} • ${hardware.currentRefreshRateHz.roundToInt()}Hz " +
                        "(Max ${hardware.maxSupportedRefreshRateHz.roundToInt()}Hz) • ${hardware.thermalSeverity.label}",
                    color = Color(0xFFA5B4FC),
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                )

                AdaptivePerformanceMode.entries.forEach { modeOption ->
                    val selected = perfMode == modeOption
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (selected) Color(0xFF6366F1).copy(alpha = 0.22f)
                                else Color.White.copy(alpha = 0.05f),
                            )
                            .border(
                                width = 1.dp,
                                color = if (selected) Color(0xFF6366F1) else Color.Transparent,
                                shape = RoundedCornerShape(14.dp),
                            )
                            .clickable { setPerfMode(modeOption) }
                            .padding(12.dp)
                            .testTag("perf_mode_${modeOption.name.lowercase()}"),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = modeOption.title,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            if (selected) {
                                Text(
                                    text = "Active (${renderingProfile.maxParticleCount} particles @ ${renderingProfile.visualizerTargetFps}fps)",
                                    color = Color(0xFFA5B4FC),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = modeOption.subtitle,
                            color = Color(0xFF9CA3AF),
                            fontSize = 11.sp,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Living Album Art (Mali-G615 GPU Pipeline)",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )

                ToggleOptionRow(
                    title = "Enable Living Album Art",
                    subtitle = "Zero-allocation beat halo and reactive artwork motion",
                    checked = livingEnabled,
                    onCheckedChange = setLivingEnabled,
                    testTag = "living_art_enable_switch",
                )
                ToggleOptionRow(
                    title = "Bass Reactions",
                    subtitle = "Subtle cover pulse synchronized with Media3 PCM bass",
                    checked = bassReaction,
                    onCheckedChange = setBassReaction,
                    testTag = "living_art_bass_switch",
                )
                ToggleOptionRow(
                    title = "Floating Particles",
                    subtitle = "Adaptive Mali-G615 2D particle field",
                    checked = particlesEnabled,
                    onCheckedChange = setParticlesEnabled,
                    testTag = "living_art_particles_switch",
                )
                ToggleOptionRow(
                    title = "Beat Lighting Halo",
                    subtitle = "Cached artwork-derived radial lighting",
                    checked = beatLighting,
                    onCheckedChange = setBeatLighting,
                    testTag = "living_art_lighting_switch",
                )
                ToggleOptionRow(
                    title = "3D Artwork Parallax",
                    subtitle = "Gentle perspective tilt in High Visual Quality mode",
                    checked = parallax3d,
                    onCheckedChange = setParallax3d,
                    testTag = "living_art_parallax_switch",
                )
            }
        }
    }
}

@Composable
private fun PulseHaloPreview(
    color: Color,
    intensity: Float,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension / 2f
        val glowAlpha = (0.20f + intensity * 0.70f).coerceIn(0.15f, 0.95f)

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    color.copy(alpha = glowAlpha),
                    color.copy(alpha = glowAlpha * 0.35f),
                    Color.Transparent,
                ),
                center = center,
                radius = radius,
            ),
            radius = radius,
            center = center,
        )
        drawCircle(
            color = color,
            radius = radius * (0.52f + intensity * 0.16f),
            center = center,
            style = Stroke(width = 3.5.dp.toPx()),
        )
    }
}

@Composable
private fun ToggleOptionRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = subtitle,
                color = Color(0xFF9CA3AF),
                fontSize = 11.sp,
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag),
        )
    }
}

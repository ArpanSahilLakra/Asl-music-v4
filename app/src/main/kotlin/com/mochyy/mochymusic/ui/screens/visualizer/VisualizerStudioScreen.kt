package com.mochyy.mochymusic.ui.screens.visualizer

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.core.device.Realme15DeviceCapabilityManager
import com.mochyy.mochymusic.playback.audio.AudioSpectrum
import com.mochyy.mochymusic.ui.player.LivingArtworkPalette
import com.mochyy.mochymusic.ui.player.LivingArtworkPaletteCache
import com.mochyy.mochymusic.viewmodels.VisualizerStudioConfig
import com.mochyy.mochymusic.viewmodels.VisualizerStudioMode
import com.mochyy.mochymusic.viewmodels.VisualizerStudioViewModel
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val AccentSwatches = listOf(
    0xFF6366F1.toInt(),
    0xFFEC4899.toInt(),
    0xFF06B6D4.toInt(),
    0xFF10B981.toInt(),
    0xFFF59E0B.toInt(),
    0xFFA855F7.toInt(),
)

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun VisualizerStudioScreen(
    navController: NavController,
    viewModel: VisualizerStudioViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current
    val config by viewModel.config.collectAsState()
    val savedPresets by viewModel.savedPresets.collectAsState()
    val renderingProfile by Realme15DeviceCapabilityManager.renderingProfile.collectAsState()

    val mediaMetadata by (playerConnection?.mediaMetadata?.collectAsState() ?: remember { mutableStateOf(null) })
    val isPlaying by (playerConnection?.isEffectivelyPlaying?.collectAsState() ?: remember { mutableStateOf(false) })

    var artworkPalette by remember { mutableStateOf(LivingArtworkPalette.Default) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var presetNameInput by remember { mutableStateOf("") }
    var showControlsPanel by remember { mutableStateOf(true) }

    LaunchedEffect(mediaMetadata?.thumbnailUrl) {
        artworkPalette = LivingArtworkPaletteCache.resolve(context, mediaMetadata?.thumbnailUrl)
    }

    // Optional accelerometer tilt parallax (zero cost when disabled)
    var tiltX by remember { mutableFloatStateOf(0f) }
    var tiltY by remember { mutableFloatStateOf(0f) }
    DisposableEffect(context, config.gyroParallaxEnabled) {
        if (!config.gyroParallaxEnabled) {
            tiltX = 0f
            tiltY = 0f
            onDispose {}
        } else {
            val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            val accel = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    val nx = (event.values[0] / 9.81f).coerceIn(-1f, 1f)
                    val ny = (event.values[1] / 9.81f).coerceIn(-1f, 1f)
                    tiltX += (nx - tiltX) * 0.14f
                    tiltY += (ny - tiltY) * 0.14f
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }
            if (accel != null) {
                sensorManager.registerListener(listener, accel, SensorManager.SENSOR_DELAY_UI)
            }
            onDispose {
                sensorManager?.unregisterListener(listener)
            }
        }
    }

    // Zero-allocation pre-allocated FFT & particle buffers
    val rawBands = remember { FloatArray(AudioSpectrum.Bands) }
    val smoothBands = remember { FloatArray(AudioSpectrum.Bands) }
    val peakHold = remember { FloatArray(AudioSpectrum.Bands) }
    val particleAngle = remember { FloatArray(72) { i -> (i * 2.39996f) } }
    val particleRadius = remember { FloatArray(72) { i -> (i % 12) / 12f } }

    var frameTick by remember { mutableLongStateOf(0L) }
    var elapsedSec by remember { mutableFloatStateOf(0f) }
    var bassEnergy by remember { mutableFloatStateOf(0f) }
    var midEnergy by remember { mutableFloatStateOf(0f) }
    var trebleEnergy by remember { mutableFloatStateOf(0f) }
    var beatImpulse by remember { mutableFloatStateOf(0f) }
    var liveSignalActive by remember { mutableStateOf(false) }

    val targetFps = if (config.batterySaverFps) 30 else renderingProfile.visualizerTargetFps.coerceIn(30, 90)

    LaunchedEffect(playerConnection, isPlaying, targetFps, config.sensitivity) {
        val minIntervalNs = 1_000_000_000L / targetFps
        var lastDrawNs = 0L
        var prevBass = 0f

        while (true) {
            withFrameNanos { nowNs ->
                if (nowNs - lastDrawNs < minIntervalNs) return@withFrameNanos
                val dt = if (lastDrawNs == 0L) 0.016f else ((nowNs - lastDrawNs) / 1_000_000_000f).coerceIn(0.004f, 0.05f)
                lastDrawNs = nowNs
                elapsedSec += dt

                val conn = playerConnection
                val spectrum = conn?.service?.spectrumOf(conn.player)
                val hasReal = isPlaying && conn != null && spectrum != null && spectrum.read(conn.player.currentPosition, rawBands)
                liveSignalActive = hasReal

                val sens = config.sensitivity
                for (i in 0 until AudioSpectrum.Bands) {
                    val target = if (hasReal) {
                        (rawBands[i] * sens).coerceIn(0f, 1f)
                    } else if (isPlaying) {
                        // Gentle harmonic fallback while decoder warms up
                        (0.22f + 0.16f * sin(elapsedSec * 3.2f + i * 0.28f)).coerceIn(0.05f, 0.7f)
                    } else {
                        (0.06f + 0.04f * sin(elapsedSec * 1.4f + i * 0.22f)).coerceIn(0.02f, 0.14f)
                    }
                    val attack = if (target > smoothBands[i]) 0.42f else 0.16f
                    smoothBands[i] += (target - smoothBands[i]) * attack
                    if (smoothBands[i] > peakHold[i]) {
                        peakHold[i] = smoothBands[i]
                    } else {
                        peakHold[i] = (peakHold[i] - dt * 0.45f).coerceAtLeast(smoothBands[i])
                    }
                }

                var bSum = 0f
                for (i in 0 until 8) bSum += smoothBands[i]
                var mSum = 0f
                for (i in 8 until 24) mSum += smoothBands[i]
                var tSum = 0f
                for (i in 24 until AudioSpectrum.Bands) tSum += smoothBands[i]

                bassEnergy = bSum / 8f
                midEnergy = mSum / 16f
                trebleEnergy = tSum / (AudioSpectrum.Bands - 24).toFloat()

                val deltaBass = (bassEnergy - prevBass).coerceAtLeast(0f)
                prevBass = bassEnergy
                beatImpulse = maxOf(beatImpulse * (1f - dt * 5.2f), (deltaBass * 3.8f).coerceIn(0f, 1f))

                // Advance starfield particles smoothly
                val speed = 0.12f + bassEnergy * 0.55f + beatImpulse * 0.45f
                for (p in particleRadius.indices) {
                    particleRadius[p] += dt * speed
                    if (particleRadius[p] > 1.15f) {
                        particleRadius[p] -= 1.15f
                    }
                }

                frameTick = nowNs
            }
        }
    }

    val primaryColor = if (config.albumReactiveLighting) artworkPalette.primary else Color(config.accentArgb)
    val secondaryColor = if (config.albumReactiveLighting) artworkPalette.secondary else Color(0xFFEC4899)
    val deepSurface = if (config.albumReactiveLighting) artworkPalette.deepOledSurface else Color(0xFF05060B)

    Scaffold(
        containerColor = deepSurface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { _ ->
        Box(modifier = Modifier.fillMaxSize()) {
            // Live GPU-friendly Canvas reacting to real PCM FFT bands
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        if (config.gyroParallaxEnabled) {
                            rotationY = tiltX * 6f
                            rotationX = -tiltY * 4f
                            cameraDistance = 16f * density
                        }
                    }
                    .clickable { showControlsPanel = !showControlsPanel }
                    .testTag("visualizer_studio_canvas"),
            ) {
                @Suppress("UNUSED_VARIABLE")
                val tick = frameTick // read inside DrawScope only to avoid recomposing parent tree
                drawVisualizerFrame(
                    config = config,
                    bands = smoothBands,
                    peaks = peakHold,
                    particleAngles = particleAngle,
                    particleRadii = particleRadius,
                    bass = bassEnergy,
                    mid = midEnergy,
                    treble = trebleEnergy,
                    beat = beatImpulse,
                    elapsedSec = elapsedSec,
                    primary = primaryColor,
                    secondary = secondaryColor,
                    surface = deepSurface,
                )
            }

            // Top bar overlay
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.72f), Color.Transparent)
                        )
                    )
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            ) {
                IconButton(
                    onClick = { navController.popBackStack() },
                    modifier = Modifier.testTag("visualizer_back_button"),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = mediaMetadata?.title ?: "3D Visualizer Studio",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = buildString {
                            append(if (liveSignalActive) "Live PCM FFT" else "Idle Harmonic")
                            append(" • ${targetFps}Hz")
                            append(" • ${config.mode.label}")
                        },
                        color = Color(0xFFB5BCDE),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(
                    onClick = { showSaveDialog = true },
                    modifier = Modifier.testTag("visualizer_save_preset_button"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = "Save visualizer preset",
                        tint = Color.White,
                    )
                }
            }

            // Bottom Studio Controls Panel (collapsible by tapping canvas)
            if (showControlsPanel) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                        .background(Color(0xD80A0C16))
                        .border(
                            width = 1.dp,
                            color = Color.White.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                        )
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    // Playback quick transport
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = config.mode.label,
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = config.mode.subtitle,
                                color = Color(0xFF9EA4C8),
                                fontSize = 11.sp,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            IconButton(
                                onClick = { playerConnection?.seekToPrevious() },
                                modifier = Modifier.testTag("visualizer_prev_button"),
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.skip_previous),
                                    contentDescription = "Previous track",
                                    tint = Color.White,
                                )
                            }
                            Surface(
                                color = primaryColor,
                                shape = CircleShape,
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("visualizer_play_pause_button")
                                    .clickable { playerConnection?.togglePlayPause() },
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        painter = painterResource(if (isPlaying) R.drawable.pause else R.drawable.play),
                                        contentDescription = if (isPlaying) "Pause" else "Play",
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp),
                                    )
                                }
                            }
                            IconButton(
                                onClick = { playerConnection?.seekToNext() },
                                modifier = Modifier.testTag("visualizer_next_button"),
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.skip_next),
                                    contentDescription = "Next track",
                                    tint = Color.White,
                                )
                            }
                        }
                    }

                    // 6 Visualizer Modes
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 2.dp),
                    ) {
                        items(VisualizerStudioMode.entries) { mode ->
                            val selected = mode == config.mode
                            Surface(
                                color = if (selected) primaryColor else Color(0xFF171B2E),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .border(
                                        1.dp,
                                        if (selected) Color.White else Color.White.copy(alpha = 0.12f),
                                        RoundedCornerShape(14.dp),
                                    )
                                    .clickable { viewModel.selectMode(mode) }
                                    .testTag("visualizer_mode_${mode.name.lowercase()}"),
                            ) {
                                Text(
                                    text = mode.label,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                )
                            }
                        }
                    }

                    // Sliders: Sensitivity + Glow
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Sensitivity (${(config.sensitivity * 100).toInt()}%)", color = Color(0xFFC5CBE8), fontSize = 11.sp)
                            Slider(
                                value = config.sensitivity,
                                onValueChange = viewModel::setSensitivity,
                                valueRange = 0.4f..2.2f,
                                colors = SliderDefaults.colors(thumbColor = primaryColor, activeTrackColor = primaryColor),
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Glow (${(config.glowIntensity * 100).toInt()}%)", color = Color(0xFFC5CBE8), fontSize = 11.sp)
                            Slider(
                                value = config.glowIntensity,
                                onValueChange = viewModel::setGlowIntensity,
                                valueRange = 0.1f..1.5f,
                                colors = SliderDefaults.colors(thumbColor = secondaryColor, activeTrackColor = secondaryColor),
                            )
                        }
                    }

                    // Color swatches + toggles
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        AccentSwatches.forEach { argb ->
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(Color(argb))
                                    .border(
                                        width = if (!config.albumReactiveLighting && config.accentArgb == argb) 2.dp else 1.dp,
                                        color = Color.White.copy(alpha = 0.65f),
                                        shape = CircleShape,
                                    )
                                    .clickable { viewModel.setAccentArgb(argb) }
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        Text("Art Light", color = Color(0xFFC5CBE8), fontSize = 11.sp)
                        Switch(
                            checked = config.albumReactiveLighting,
                            onCheckedChange = viewModel::setAlbumReactiveLighting,
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("3D Tilt Parallax", color = Color(0xFFC5CBE8), fontSize = 12.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Switch(
                                checked = config.gyroParallaxEnabled,
                                onCheckedChange = viewModel::setGyroParallaxEnabled,
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Battery 30Hz", color = Color(0xFFC5CBE8), fontSize = 12.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Switch(
                                checked = config.batterySaverFps,
                                onCheckedChange = viewModel::setBatterySaverFps,
                            )
                        }
                    }

                    // Saved Room presets row
                    if (savedPresets.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(savedPresets, key = { it.id }) { preset ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFF171C32))
                                        .border(1.dp, Color(0xFF2B3259), RoundedCornerShape(12.dp))
                                        .clickable { viewModel.applyPreset(preset) }
                                        .padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                ) {
                                    Text(
                                        text = preset.name,
                                        color = Color.White,
                                        fontSize = 12.sp,
                                    )
                                    IconButton(
                                        onClick = { viewModel.deletePreset(preset.id) },
                                        modifier = Modifier.size(28.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Delete,
                                            contentDescription = "Delete preset ${preset.name}",
                                            tint = Color(0xFF9EA4C8),
                                            modifier = Modifier.size(14.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            containerColor = Color(0xFF14182B),
            titleContentColor = Color.White,
            title = { Text("Save Visualizer Preset") },
            text = {
                OutlinedTextField(
                    value = presetNameInput,
                    onValueChange = { presetNameInput = it },
                    placeholder = { Text("e.g., Neon Night Drive") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.saveCurrentPreset(presetNameInput)
                        presetNameInput = ""
                        showSaveDialog = false
                    }
                ) {
                    Text("Save", color = Color(0xFF818CF8))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text("Cancel", color = Color(0xFF9EA4C8))
                }
            }
        )
    }
}

private fun DrawScope.drawVisualizerFrame(
    config: VisualizerStudioConfig,
    bands: FloatArray,
    peaks: FloatArray,
    particleAngles: FloatArray,
    particleRadii: FloatArray,
    bass: Float,
    mid: Float,
    treble: Float,
    beat: Float,
    elapsedSec: Float,
    primary: Color,
    secondary: Color,
    surface: Color,
) {
    val w = size.width
    val h = size.height
    val cx = w * 0.5f
    val cy = h * 0.38f

    // Reactive OLED backdrop glow
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(
                primary.copy(alpha = (0.24f + bass * 0.32f) * config.glowIntensity.coerceAtMost(1.2f)),
                secondary.copy(alpha = (0.12f + mid * 0.18f) * config.glowIntensity.coerceAtMost(1.2f)),
                surface,
            ),
            center = Offset(cx, cy),
            radius = maxOf(w, h) * (0.55f + beat * 0.12f),
        )
    )

    when (config.mode) {
        VisualizerStudioMode.PERSPECTIVE_3D -> drawPerspective3D(bands, primary, secondary, bass, elapsedSec, config.glowIntensity)
        VisualizerStudioMode.CIRCULAR_RING -> drawCircularRing(bands, primary, secondary, bass, beat, cx, cy, minOf(w, h) * 0.24f, config.glowIntensity)
        VisualizerStudioMode.NEON_WAVEFORM -> drawNeonWaveform(bands, primary, secondary, cy, elapsedSec, config.glowIntensity)
        VisualizerStudioMode.FREQUENCY_BARS -> drawFrequencyBars(bands, peaks, primary, secondary, cy)
        VisualizerStudioMode.FLUID_RIBBONS -> drawFluidRibbons(bands, primary, secondary, cy, bass, treble, elapsedSec)
        VisualizerStudioMode.BEAT_PARTICLES -> drawBeatParticles(particleAngles, particleRadii, primary, secondary, bass, beat, cx, cy, minOf(w, h) * 0.45f)
    }
}

private fun DrawScope.drawPerspective3D(
    bands: FloatArray,
    primary: Color,
    secondary: Color,
    bass: Float,
    elapsedSec: Float,
    glow: Float,
) {
    val w = size.width
    val h = size.height
    val horizonY = h * 0.28f
    val floorBottom = h * 0.62f

    // Perspective horizon grid lines receding toward vanishing point
    val rows = 10
    for (r in 1..rows) {
        val depth = (r.toFloat() / rows)
        val projectedY = horizonY + (floorBottom - horizonY) * (depth * depth)
        drawLine(
            color = primary.copy(alpha = 0.12f + depth * 0.28f),
            start = Offset(0f, projectedY),
            end = Offset(w, projectedY),
            strokeWidth = 1.5f + depth * 1.5f,
        )
    }

    val cols = 16
    val vanish = Offset(w * 0.5f, horizonY)
    for (c in 0..cols) {
        val frac = (c.toFloat() / cols) * 2f - 1f
        val endX = w * 0.5f + frac * w * 0.95f
        drawLine(
            color = secondary.copy(alpha = 0.22f),
            start = vanish,
            end = Offset(endX, floorBottom),
            strokeWidth = 1.6f,
        )
    }

    // 3 layered 3D frequency mountain ridges with parallax depth
    for (layer in 0 until 3) {
        val depthScale = 0.55f + layer * 0.24f
        val baseY = horizonY + (floorBottom - horizonY) * (0.28f + layer * 0.28f)
        val ampMax = h * (0.12f + layer * 0.055f)
        val path = Path()
        for (i in bands.indices) {
            val x = (i.toFloat() / (bands.size - 1)) * w
            val wave = sin(elapsedSec * 2.1f + i * 0.2f + layer) * 0.06f
            val y = baseY - (bands[i] + wave).coerceAtLeast(0f) * ampMax
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        val strokeColor = if (layer == 2) Color.White else if (layer == 1) primary else secondary
        drawPath(
            path = path,
            color = strokeColor.copy(alpha = (0.38f + layer * 0.25f) * glow.coerceAtMost(1.2f)),
            style = Stroke(width = 2.5f + layer * 2.2f * depthScale, cap = StrokeCap.Round),
        )
    }

    // Floating harmonic sun core on horizon
    drawCircle(
        brush = Brush.radialGradient(
            listOf(Color.White.copy(alpha = 0.7f), primary.copy(alpha = 0.35f), Color.Transparent),
            center = Offset(w * 0.5f, horizonY - 26f),
            radius = 52f + bass * 42f,
        ),
        radius = 52f + bass * 42f,
        center = Offset(w * 0.5f, horizonY - 26f),
    )
}

private fun DrawScope.drawCircularRing(
    bands: FloatArray,
    primary: Color,
    secondary: Color,
    bass: Float,
    beat: Float,
    cx: Float,
    cy: Float,
    baseRadius: Float,
    glow: Float,
) {
    val radius = baseRadius * (1f + bass * 0.22f + beat * 0.1f)
    drawCircle(
        brush = Brush.radialGradient(
            listOf(primary.copy(alpha = 0.45f * glow.coerceAtMost(1.2f)), Color.Transparent),
            center = Offset(cx, cy),
            radius = radius * 1.45f,
        ),
        radius = radius * 1.45f,
        center = Offset(cx, cy),
    )

    val count = bands.size
    for (i in 0 until count) {
        val angle = (i.toFloat() / count) * (2.0 * PI) - PI / 2.0
        val barLen = 18f + bands[i] * baseRadius * 0.95f
        val start = Offset(
            x = cx + (cos(angle) * radius).toFloat(),
            y = cy + (sin(angle) * radius).toFloat(),
        )
        val end = Offset(
            x = cx + (cos(angle) * (radius + barLen)).toFloat(),
            y = cy + (sin(angle) * (radius + barLen)).toFloat(),
        )
        val tint = if (i % 2 == 0) primary else secondary
        drawLine(
            color = tint.copy(alpha = 0.85f),
            start = start,
            end = end,
            strokeWidth = 6f,
            cap = StrokeCap.Round,
        )
    }

    drawCircle(
        color = Color.White.copy(alpha = 0.55f),
        radius = radius * 0.92f,
        center = Offset(cx, cy),
        style = Stroke(width = 3f),
    )
}

private fun DrawScope.drawNeonWaveform(
    bands: FloatArray,
    primary: Color,
    secondary: Color,
    cy: Float,
    elapsedSec: Float,
    glow: Float,
) {
    val w = size.width
    val amp = size.height * 0.16f
    for (ribbon in 0..2) {
        val path = Path()
        val phase = elapsedSec * (2.5f + ribbon * 0.9f)
        for (i in bands.indices) {
            val x = (i.toFloat() / (bands.size - 1)) * w
            val signed = sin(i * 0.48f + phase) * bands[i] * amp * (1f - ribbon * 0.2f)
            val y = cy + signed
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        val color = when (ribbon) {
            0 -> primary
            1 -> secondary
            else -> Color.White
        }
        drawPath(
            path = path,
            color = color.copy(alpha = (0.45f + ribbon * 0.2f) * glow.coerceAtMost(1.2f)),
            style = Stroke(width = 8f - ribbon * 2.2f, cap = StrokeCap.Round),
        )
    }
}

private fun DrawScope.drawFrequencyBars(
    bands: FloatArray,
    peaks: FloatArray,
    primary: Color,
    secondary: Color,
    cy: Float,
) {
    val padX = 28f
    val usableW = size.width - padX * 2f
    val barStep = usableW / bands.size
    val barWidth = (barStep * 0.64f).coerceAtLeast(3f)
    val baseY = cy + size.height * 0.17f
    val maxBarH = size.height * 0.34f

    for (i in bands.indices) {
        val x = padX + i * barStep
        val bh = (bands[i] * maxBarH).coerceAtLeast(6f)
        val frac = i.toFloat() / bands.size
        val color = Color(
            red = primary.red + (secondary.red - primary.red) * frac,
            green = primary.green + (secondary.green - primary.green) * frac,
            blue = primary.blue + (secondary.blue - primary.blue) * frac,
            alpha = 0.92f,
        )
        drawRoundRect(
            color = color,
            topLeft = Offset(x, baseY - bh),
            size = Size(barWidth, bh),
            cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
        )
        // Peak-hold cap
        val peakY = baseY - (peaks[i] * maxBarH).coerceAtLeast(10f) - 6f
        drawRoundRect(
            color = Color.White.copy(alpha = 0.85f),
            topLeft = Offset(x, peakY),
            size = Size(barWidth, 4f),
            cornerRadius = CornerRadius(2f, 2f),
        )
    }
}

private fun DrawScope.drawFluidRibbons(
    bands: FloatArray,
    primary: Color,
    secondary: Color,
    cy: Float,
    bass: Float,
    treble: Float,
    elapsedSec: Float,
) {
    val w = size.width
    val h = size.height
    for (layer in 0 until 4) {
        val path = Path()
        val yBase = cy + (layer - 1.5f) * 38f
        path.moveTo(0f, h)
        for (i in bands.indices) {
            val x = (i.toFloat() / (bands.size - 1)) * w
            val harmonic = sin(elapsedSec * (1.6f + layer * 0.4f) + i * 0.25f) * (22f + treble * 45f)
            val lift = bands[i] * (95f + bass * 85f)
            path.lineTo(x, yBase - lift + harmonic)
        }
        path.lineTo(w, h)
        path.close()
        val tint = if (layer % 2 == 0) primary else secondary
        drawPath(
            path = path,
            brush = Brush.verticalGradient(
                listOf(tint.copy(alpha = 0.34f - layer * 0.05f), Color.Transparent),
                startY = yBase - 140f,
                endY = yBase + 160f,
            )
        )
    }
}

private fun DrawScope.drawBeatParticles(
    angles: FloatArray,
    radii: FloatArray,
    primary: Color,
    secondary: Color,
    bass: Float,
    beat: Float,
    cx: Float,
    cy: Float,
    maxRadius: Float,
) {
    // Pulsing energy ring at center
    drawCircle(
        color = primary.copy(alpha = 0.35f + beat * 0.4f),
        radius = 36f + bass * 64f,
        center = Offset(cx, cy),
        style = Stroke(width = 4f + beat * 4f),
    )

    for (i in angles.indices) {
        val rNorm = radii[i]
        val dist = rNorm * rNorm * maxRadius * (1f + beat * 0.25f)
        val x = cx + cos(angles[i]) * dist
        val y = cy + sin(angles[i]) * dist
        val radiusPx = (2.5f + rNorm * 7.5f + bass * 4.5f)
        val color = if (i % 3 == 0) Color.White else if (i % 2 == 0) primary else secondary
        drawCircle(
            color = color.copy(alpha = (1f - rNorm * 0.55f).coerceIn(0.1f, 0.95f)),
            radius = radiusPx,
            center = Offset(x, y),
        )
    }
}

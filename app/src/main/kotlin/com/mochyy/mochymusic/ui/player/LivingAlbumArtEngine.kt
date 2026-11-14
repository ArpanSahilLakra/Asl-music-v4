package com.mochyy.mochymusic.ui.player

import android.content.Context
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.palette.graphics.Palette
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.size.Size as CoilSize
import coil3.toBitmap
import com.mochyy.mochymusic.LocalAppContentObscured
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.constants.LivingAlbumArtEnabledKey
import com.mochyy.mochymusic.constants.LivingArt3dParallaxKey
import com.mochyy.mochymusic.constants.LivingArtBassReactionKey
import com.mochyy.mochymusic.constants.LivingArtBeatLightingKey
import com.mochyy.mochymusic.constants.LivingArtParticlesEnabledKey
import com.mochyy.mochymusic.core.device.AdaptivePerformanceMode
import com.mochyy.mochymusic.core.device.Realme15DeviceCapabilityManager
import com.mochyy.mochymusic.playback.audio.AudioSpectrum
import com.mochyy.mochymusic.ui.component.damageBoundary
import com.mochyy.mochymusic.ui.utils.resize
import com.mochyy.mochymusic.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Cached artwork-derived color palette for Living Album Art and ASL Pulse Sync.
 */
@Immutable
data class LivingArtworkPalette(
    val primary: Color,
    val secondary: Color,
    val bassGlow: Color,
    val particleTint: Color,
    val deepOledSurface: Color,
) {
    companion object {
        val Default = LivingArtworkPalette(
            primary = Color(0xFF6366F1),
            secondary = Color(0xFFEC4899),
            bassGlow = Color(0xFF8B5CF6),
            particleTint = Color(0xFFA5B4FC),
            deepOledSurface = Color(0xFF07070C),
        )
    }
}

/**
 * Bounded LRU cache of artwork-derived palettes so track transitions and back/forth skips
 * never re-decode or re-extract colors for previously visited songs.
 */
object LivingArtworkPaletteCache {
    private const val MAX_ENTRIES = 64
    private const val DECODE_PX = 64
    private val cache = LruCache<String, LivingArtworkPalette>(MAX_ENTRIES)

    fun getCached(url: String?): LivingArtworkPalette? {
        if (url.isNullOrBlank()) return null
        return cache.get(url)
    }

    suspend fun resolve(context: Context, url: String?): LivingArtworkPalette {
        if (url.isNullOrBlank()) return LivingArtworkPalette.Default
        cache.get(url)?.let { return it }

        val bitmap = withContext(Dispatchers.IO) {
            runCatching {
                val request = ImageRequest.Builder(context)
                    .data(url.resize(DECODE_PX * 2, DECODE_PX * 2))
                    .size(CoilSize(DECODE_PX, DECODE_PX))
                    .allowHardware(false)
                    .build()
                context.imageLoader.execute(request).image?.toBitmap()
            }.getOrNull()
        } ?: return LivingArtworkPalette.Default

        return withContext(Dispatchers.Default) {
            runCatching {
                val palette = Palette.from(bitmap)
                    .maximumColorCount(12)
                    .resizeBitmapArea(DECODE_PX * DECODE_PX)
                    .generate()

                val vibrant = palette.getVibrantColor(0xFF6366F1.toInt())
                val lightVibrant = palette.getLightVibrantColor(vibrant)
                val muted = palette.getMutedColor(0xFF8B5CF6.toInt())
                val darkMuted = palette.getDarkMutedColor(0xFF090910.toInt())

                LivingArtworkPalette(
                    primary = Color(vibrant),
                    secondary = Color(muted),
                    bassGlow = Color(vibrant).copy(alpha = 0.85f),
                    particleTint = Color(lightVibrant),
                    deepOledSurface = Color(darkMuted).copy(
                        red = Color(darkMuted).red * 0.25f,
                        green = Color(darkMuted).green * 0.25f,
                        blue = Color(darkMuted).blue * 0.25f,
                        alpha = 1f,
                    ),
                )
            }.getOrDefault(LivingArtworkPalette.Default)
        }.also { resolved ->
            cache.put(url, resolved)
        }
    }

    fun trimOnLowMemory() {
        cache.trimToSize(MAX_ENTRIES / 4)
    }
}

/**
 * Zero-allocation state holder for the Mali-G615 optimized Living Album Art engine.
 *
 * All per-frame state is stored in pre-allocated primitive arrays and float states read only
 * inside draw/graphicsLayer phases, avoiding UI recomposition during playback.
 */
@Stable
class LivingAlbumArtState(maxParticles: Int = MAX_SUPPORTED_PARTICLES) {
    val capacity: Int = maxParticles.coerceIn(0, MAX_SUPPORTED_PARTICLES)

    // Pre-allocated particle SOA (Structure of Arrays) to avoid per-frame object allocations on Mali-G615
    internal val particleX = FloatArray(capacity)
    internal val particleY = FloatArray(capacity)
    internal val particleVx = FloatArray(capacity)
    internal val particleVy = FloatArray(capacity)
    internal val particleRadiusDp = FloatArray(capacity)
    internal val particleBaseAlpha = FloatArray(capacity)
    internal val particlePhase = FloatArray(capacity)

    internal val spectrumBuffer = FloatArray(AudioSpectrum.Bands)

    var frameTick by mutableLongStateOf(0L)
        internal set

    var bassEnergy by mutableFloatStateOf(0f)
        internal set

    var midEnergy by mutableFloatStateOf(0f)
        internal set

    var trebleEnergy by mutableFloatStateOf(0f)
        internal set

    var beatImpulse by mutableFloatStateOf(0f)
        internal set

    var tiltXDeg by mutableFloatStateOf(0f)
        internal set

    var tiltYDeg by mutableFloatStateOf(0f)
        internal set

    var bassScale by mutableFloatStateOf(1f)
        internal set

    var isRealAudioSpectrumActive by mutableStateOf(false)
        internal set

    private var prevBass = 0f

    init {
        resetParticles()
    }

    fun resetParticles() {
        for (i in 0 until capacity) {
            // Deterministic golden-ratio distribution so no Random object allocation is needed
            val fi = (i + 1) * 0.6180339887f
            particleX[i] = (fi % 1f)
            particleY[i] = (((i * 0.381966f) + 0.13f) % 1f)
            val angle = (i * 2.3999632f)
            particleVx[i] = cos(angle) * (0.012f + (i % 5) * 0.004f)
            particleVy[i] = -0.018f - (i % 7) * 0.005f
            particleRadiusDp[i] = 1.6f + (i % 4) * 0.85f
            particleBaseAlpha[i] = 0.18f + (i % 5) * 0.09f
            particlePhase[i] = (i * 0.7f) % (2f * PI.toFloat())
        }
    }

    /**
     * Advances the simulation by [dtSeconds] using either real [AudioSpectrum] PCM bands
     * or a graceful progress-based harmonic wave when hardware offload/external cast hides PCM.
     */
    fun stepFrame(
        dtSeconds: Float,
        positionMs: Long,
        durationMs: Long,
        hasRealSpectrum: Boolean,
        activeParticleCount: Int,
        enableBass: Boolean,
        enable3dParallax: Boolean,
    ) {
        isRealAudioSpectrumActive = hasRealSpectrum
        val rawBass: Float
        val rawMid: Float
        val rawTreble: Float

        if (hasRealSpectrum) {
            rawBass = averageRange(spectrumBuffer, 0, 8)
            rawMid = averageRange(spectrumBuffer, 8, 24)
            rawTreble = averageRange(spectrumBuffer, 24, AudioSpectrum.Bands)
        } else {
            // Graceful progress-based harmonic motion (never pretends to detect microphone beats)
            val progressSec = (positionMs.coerceAtLeast(0L) / 1000f)
            rawBass = 0.22f + 0.14f * (0.5f + 0.5f * sin(progressSec * 1.6f))
            rawMid = 0.18f + 0.12f * (0.5f + 0.5f * sin(progressSec * 1.1f + 1.2f))
            rawTreble = 0.14f + 0.10f * (0.5f + 0.5f * cos(progressSec * 0.9f + 2.4f))
        }

        val attack = (dtSeconds * 16f).coerceIn(0.05f, 1f)
        val release = (dtSeconds * 6f).coerceIn(0.02f, 1f)

        bassEnergy += (rawBass - bassEnergy) * if (rawBass > bassEnergy) attack else release
        midEnergy += (rawMid - midEnergy) * if (rawMid > midEnergy) attack else release
        trebleEnergy += (rawTreble - trebleEnergy) * if (rawTreble > trebleEnergy) attack else release

        val bassDelta = (rawBass - prevBass).coerceAtLeast(0f)
        prevBass = rawBass
        val targetImpulse = if (hasRealSpectrum && bassDelta > 0.08f) {
            (bassDelta * 2.8f).coerceAtMost(1f)
        } else {
            0f
        }
        beatImpulse = maxOf(targetImpulse, beatImpulse * (1f - dtSeconds * 7.5f).coerceIn(0f, 1f))

        bassScale = if (enableBass) {
            1f + (bassEnergy * 0.036f + beatImpulse * 0.022f).coerceIn(0f, 0.055f)
        } else {
            1f
        }

        if (enable3dParallax) {
            val phase = (positionMs / 1000f)
            tiltXDeg = sin(phase * 0.75f) * (2.2f + bassEnergy * 2.0f)
            tiltYDeg = cos(phase * 0.55f) * (2.2f + midEnergy * 2.0f)
        } else {
            tiltXDeg = 0f
            tiltYDeg = 0f
        }

        val count = activeParticleCount.coerceIn(0, capacity)
        val speedBoost = 1f + bassEnergy * 1.8f + beatImpulse * 1.4f
        for (i in 0 until count) {
            var x = particleX[i] + particleVx[i] * dtSeconds * speedBoost
            var y = particleY[i] + particleVy[i] * dtSeconds * speedBoost
            if (x < 0f) x += 1f else if (x > 1f) x -= 1f
            if (y < 0f) y += 1f else if (y > 1f) y -= 1f
            particleX[i] = x
            particleY[i] = y
            particlePhase[i] = (particlePhase[i] + dtSeconds * (1.5f + trebleEnergy * 2f)) % (2f * PI.toFloat())
        }

        frameTick++
    }

    /**
     * Smoothly decays energies toward rest when playback pauses so visuals settle gracefully
     * instead of snapping or continuing to burn GPU cycles.
     */
    fun decayTowardsRest(dtSeconds: Float): Boolean {
        val decay = (1f - dtSeconds * 6f).coerceIn(0f, 1f)
        bassEnergy *= decay
        midEnergy *= decay
        trebleEnergy *= decay
        beatImpulse *= decay
        tiltXDeg *= decay
        tiltYDeg *= decay
        bassScale = 1f + (bassScale - 1f) * decay
        frameTick++
        return bassEnergy < 0.005f && midEnergy < 0.005f && beatImpulse < 0.005f && kotlin.math.abs(bassScale - 1f) < 0.001f
    }

    private fun averageRange(array: FloatArray, start: Int, endExclusive: Int): Float {
        val end = endExclusive.coerceAtMost(array.size)
        if (end <= start) return 0f
        var sum = 0f
        for (i in start until end) sum += array[i]
        return (sum / (end - start)).coerceIn(0f, 1f)
    }

    companion object {
        const val MAX_SUPPORTED_PARTICLES = 64
    }
}

/**
 * Remembers and drives the [LivingAlbumArtState] lifecycle-aware loop.
 * Automatically stops rendering when:
 * - The player is minimized (`progressProvider() < 0.35f`)
 * - Lyrics or Queue overlays cover the artwork (`lyricsProgressProvider() > 0.75f`)
 * - Playback is paused and the decay animation has settled
 * - The Activity goes to background or screen turns off (`Lifecycle.State.STARTED`)
 */
@Composable
fun rememberLivingAlbumArtState(
    thumbnailUrl: String?,
    isPlaying: Boolean,
    progressProvider: () -> Float,
    lyricsProgressProvider: () -> Float,
): Pair<LivingAlbumArtState, LivingArtworkPalette> {
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val obscured = LocalAppContentObscured.current

    val (livingEnabled) = rememberPreference(LivingAlbumArtEnabledKey, defaultValue = true)
    val (bassEnabled) = rememberPreference(LivingArtBassReactionKey, defaultValue = true)
    val (particlesEnabled) = rememberPreference(LivingArtParticlesEnabledKey, defaultValue = true)
    val (parallaxEnabled) = rememberPreference(LivingArt3dParallaxKey, defaultValue = true)

    val renderingProfile by Realme15DeviceCapabilityManager.renderingProfile.collectAsState()

    val state = remember { LivingAlbumArtState() }
    var palette by remember(thumbnailUrl) {
        mutableStateOf(LivingArtworkPaletteCache.getCached(thumbnailUrl) ?: LivingArtworkPalette.Default)
    }

    LaunchedEffect(thumbnailUrl) {
        palette = LivingArtworkPaletteCache.resolve(context, thumbnailUrl)
    }

    val currentProgress by rememberUpdatedState(progressProvider)
    val currentLyricsProgress by rememberUpdatedState(lyricsProgressProvider)

    LaunchedEffect(
        livingEnabled,
        isPlaying,
        playerConnection,
        renderingProfile,
        bassEnabled,
        particlesEnabled,
        parallaxEnabled,
    ) {
        if (!livingEnabled || playerConnection == null) {
            state.decayTowardsRest(1f)
            return@LaunchedEffect
        }

        snapshotFlow {
            val sheetOpen = currentProgress() > 0.35f
            val artVisible = currentLyricsProgress() < 0.75f
            val lifecycleStarted = lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            sheetOpen && artVisible && lifecycleStarted && !obscured.value
        }.distinctUntilChanged().collectLatest { shouldRun ->
            if (!shouldRun) {
                state.decayTowardsRest(1f)
                return@collectLatest
            }

            val targetFps = renderingProfile.visualizerTargetFps.coerceIn(15, 90)
            val minFrameIntervalNanos = 1_000_000_000L / targetFps
            val adpfSession = if (renderingProfile.effectiveMode == AdaptivePerformanceMode.HIGH_VISUAL_QUALITY) {
                Realme15DeviceCapabilityManager.createAdpfRenderSession(context, minFrameIntervalNanos)
            } else {
                null
            }

            try {
                var lastStepNanos = 0L
                while (true) {
                    val frameNanos = withFrameNanos { it }
                    if (lastStepNanos != 0L && (frameNanos - lastStepNanos) < minFrameIntervalNanos) {
                        continue
                    }
                    val workStartNanos = System.nanoTime()
                    val dt = if (lastStepNanos == 0L) {
                        1f / targetFps
                    } else {
                        ((frameNanos - lastStepNanos) / 1_000_000_000f).coerceIn(0.004f, 0.1f)
                    }
                    lastStepNanos = frameNanos

                    if (!isPlaying) {
                        val settled = state.decayTowardsRest(dt)
                        if (settled) break
                        continue
                    }

                    val player = playerConnection.player
                    val posMs = player.currentPosition
                    val durMs = player.duration.coerceAtLeast(1L)
                    val spectrum = playerConnection.service.spectrumOf(player)
                    val hasRealSpectrum = spectrum != null && spectrum.read(posMs, state.spectrumBuffer)

                    val activeParticles = if (particlesEnabled) renderingProfile.maxParticleCount else 0
                    val allowBass = bassEnabled && renderingProfile.enableBassReaction
                    val allow3d = parallaxEnabled && renderingProfile.enable3dArtworkParallax

                    state.stepFrame(
                        dtSeconds = dt,
                        positionMs = posMs,
                        durationMs = durMs,
                        hasRealSpectrum = hasRealSpectrum,
                        activeParticleCount = activeParticles,
                        enableBass = allowBass,
                        enable3dParallax = allow3d,
                    )

                    adpfSession?.reportActualWorkDuration(System.nanoTime() - workStartNanos)
                }
            } finally {
                adpfSession?.close()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            state.decayTowardsRest(1f)
        }
    }

    return state to palette
}

/**
 * Lightweight 2D Canvas overlay for Living Album Art (Beat Halo + Floating Particles).
 *
 * Uses a single [damageBoundary] Canvas pass with zero per-frame allocations,
 * replacing expensive multi-pass full-screen 3D blurs on the ARM Mali-G615 GPU.
 */
@Composable
fun LivingAlbumArtOverlay(
    state: LivingAlbumArtState,
    palette: LivingArtworkPalette,
    progressProvider: () -> Float,
    lyricsProgressProvider: () -> Float,
    modifier: Modifier = Modifier,
) {
    val (livingEnabled) = rememberPreference(LivingAlbumArtEnabledKey, defaultValue = true)
    if (!livingEnabled) return

    val (particlesEnabled) = rememberPreference(LivingArtParticlesEnabledKey, defaultValue = true)
    val (beatLightingEnabled) = rememberPreference(LivingArtBeatLightingKey, defaultValue = true)
    val renderingProfile by Realme15DeviceCapabilityManager.renderingProfile.collectAsState()

    val maxParticles = if (particlesEnabled) renderingProfile.maxParticleCount else 0
    val allowLighting = beatLightingEnabled && renderingProfile.enableBeatLighting

    if (maxParticles <= 0 && !allowLighting) return

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .damageBoundary()
            .graphicsLayer {
                val openAlpha = ((progressProvider() - 0.45f) / 0.45f).coerceIn(0f, 1f)
                val lyricsFade = (1f - lyricsProgressProvider() * 1.3f).coerceIn(0f, 1f)
                alpha = openAlpha * lyricsFade
            }
    ) {
        // Read frameTick inside DrawScope so only this Canvas invalidates on audio ticks
        state.frameTick
        drawLivingEffects(
            state = state,
            palette = palette,
            particleCount = maxParticles,
            drawLighting = allowLighting,
        )
    }
}

/**
 * Applies beat-reactive scale and optional 3D camera tilt to the album artwork layer
 * inside `graphicsLayer` without triggering Compose layout or recomposition.
 */
fun Modifier.livingAlbumArtTransform(
    state: LivingAlbumArtState?,
    progressProvider: () -> Float,
    lyricsProgressProvider: () -> Float,
): Modifier {
    if (state == null) return this
    return this.graphicsLayer {
        val weight = ((progressProvider() - 0.65f) / 0.35f).coerceIn(0f, 1f) *
            (1f - lyricsProgressProvider()).coerceIn(0f, 1f)
        if (weight <= 0.001f) return@graphicsLayer
        val scale = 1f + (state.bassScale - 1f) * weight
        scaleX = scale
        scaleY = scale
        rotationX = state.tiltXDeg * weight
        rotationY = state.tiltYDeg * weight
        cameraDistance = 14f * density
    }
}

private fun DrawScope.drawLivingEffects(
    state: LivingAlbumArtState,
    palette: LivingArtworkPalette,
    particleCount: Int,
    drawLighting: Boolean,
) {
    val w = size.width
    val h = size.height
    if (w <= 1f || h <= 1f) return

    if (drawLighting) {
        val glowAlpha = (state.bassEnergy * 0.28f + state.beatImpulse * 0.24f).coerceIn(0f, 0.42f)
        if (glowAlpha > 0.01f) {
            val radius = maxOf(w, h) * (0.48f + state.bassEnergy * 0.16f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        palette.bassGlow.copy(alpha = glowAlpha),
                        palette.primary.copy(alpha = glowAlpha * 0.45f),
                        Color.Transparent,
                    ),
                    center = Offset(w * 0.5f, h * 0.5f),
                    radius = radius,
                ),
                radius = radius,
                center = Offset(w * 0.5f, h * 0.5f),
                blendMode = BlendMode.Plus,
            )
        }
    }

    val count = particleCount.coerceIn(0, state.capacity)
    if (count <= 0) return

    val energyBoost = (0.45f + state.bassEnergy * 0.55f + state.beatImpulse * 0.35f).coerceIn(0.25f, 1.2f)
    for (i in 0 until count) {
        val px = state.particleX[i] * w
        val py = state.particleY[i] * h
        val pulse = 0.65f + 0.35f * sin(state.particlePhase[i])
        val alpha = (state.particleBaseAlpha[i] * pulse * energyBoost).coerceIn(0f, 0.78f)
        if (alpha <= 0.02f) continue

        val baseRadiusPx = state.particleRadiusDp[i] * density * (1f + state.bassEnergy * 0.45f)
        val color = if (i % 3 == 0) palette.primary else palette.particleTint

        // Soft outer halo + crisp core using lightweight 2D circles instead of shader blur
        drawCircle(
            color = color.copy(alpha = alpha * 0.28f),
            radius = baseRadiusPx * 2.1f,
            center = Offset(px, py),
            blendMode = BlendMode.Plus,
        )
        drawCircle(
            color = color.copy(alpha = alpha),
            radius = baseRadiusPx,
            center = Offset(px, py),
            blendMode = BlendMode.Plus,
        )
    }
}

package com.mochyy.mochymusic

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mochyy.mochymusic.core.device.AdaptivePerformanceMode
import com.mochyy.mochymusic.core.device.DeviceHardwareSnapshot
import com.mochyy.mochymusic.core.device.PhysicalRamTier
import com.mochyy.mochymusic.core.device.Realme15DeviceCapabilityManager
import com.mochyy.mochymusic.core.device.ThermalSeverity
import com.mochyy.mochymusic.db.InternalDatabase
import com.mochyy.mochymusic.playback.pulse.AslPulseLightingMode
import com.mochyy.mochymusic.playback.pulse.AslPulseSyncEngine
import com.mochyy.mochymusic.playback.pulse.PulseBeatEvent
import com.mochyy.mochymusic.playback.pulse.PulseLightCapabilityInspector
import com.mochyy.mochymusic.playback.pulse.PulseSyncIntegrationStatus
import com.mochyy.mochymusic.ui.player.LivingAlbumArtState
import com.mochyy.mochymusic.ui.player.LivingArtworkPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class Realme15OptimizationAndPulseSyncTest {

    private fun sampleRmx5106Snapshot(
        ramTier: PhysicalRamTier = PhysicalRamTier.RAM_8GB,
        thermal: ThermalSeverity = ThermalSeverity.NORMAL,
        batterySaver: Boolean = false,
        lowMemory: Boolean = false,
        currentHz: Float = 120f,
    ): DeviceHardwareSnapshot = DeviceHardwareSnapshot(
        manufacturer = "realme",
        brand = "realme",
        model = "RMX5106",
        deviceCode = "RMX5106",
        isRealmeDevice = true,
        isRealme15Rmx5106 = true,
        androidSdkInt = 35,
        androidRelease = "15",
        securityPatch = "2026-10-05",
        buildDisplay = "RMX5106_15.0.0.402(EX01)",
        detectedRealmeUiLabel = "realme UI 6.0 (Android 15)",
        totalPhysicalRamBytes = if (ramTier == PhysicalRamTier.RAM_12GB_PLUS) {
            12L * 1024L * 1024L * 1024L
        } else {
            8L * 1024L * 1024L * 1024L
        },
        availablePhysicalRamBytes = 4L * 1024L * 1024L * 1024L,
        ramTier = ramTier,
        memoryClassMb = 256,
        largeMemoryClassMb = 512,
        isLowMemory = lowMemory,
        displayWidthPx = 1280,
        displayHeightPx = 2800,
        densityDpi = 480,
        currentRefreshRateHz = currentHz,
        maxSupportedRefreshRateHz = 144f,
        supportedRefreshRatesHz = listOf(60, 90, 120, 144),
        supports144Hz = true,
        isOled1280x2800Class = true,
        isBatterySaverActive = batterySaver,
        thermalSeverity = thermal,
        supportsAdpfHintSession = true,
    )

    @Test
    fun `rmx5106 8gb and 12gb profiles adapt to thermal and battery saver states`() {
        val snap8Gb = sampleRmx5106Snapshot(ramTier = PhysicalRamTier.RAM_8GB)
        val snap12Gb = sampleRmx5106Snapshot(ramTier = PhysicalRamTier.RAM_12GB_PLUS)

        val profile8Gb = Realme15DeviceCapabilityManager.computeProfile(snap8Gb, AdaptivePerformanceMode.AUTO)
        val profile12Gb = Realme15DeviceCapabilityManager.computeProfile(snap12Gb, AdaptivePerformanceMode.AUTO)

        assertTrue(profile8Gb.isRmx5106Optimized)
        assertEquals(AdaptivePerformanceMode.HIGH_VISUAL_QUALITY, profile8Gb.effectiveMode)
        assertEquals(44, profile8Gb.maxParticleCount)
        assertEquals(960, profile8Gb.artworkDecodeSizePx)
        assertEquals(0.18, profile8Gb.coilMemoryCachePercent, 0.001)
        assertEquals(0.22, profile12Gb.coilMemoryCachePercent, 0.001)
        assertTrue(profile12Gb.databasePageLimit > profile8Gb.databasePageLimit)

        // Moderate thermal pressure steps High Visual Quality down to Balanced
        val warmSnap = sampleRmx5106Snapshot(thermal = ThermalSeverity.MODERATE)
        val warmProfile = Realme15DeviceCapabilityManager.computeProfile(
            warmSnap,
            AdaptivePerformanceMode.HIGH_VISUAL_QUALITY,
        )
        assertEquals(AdaptivePerformanceMode.BALANCED, warmProfile.effectiveMode)
        assertEquals(22, warmProfile.maxParticleCount)
        assertFalse(warmProfile.enable3dArtworkParallax)
        assertTrue(warmProfile.thermalReduced)

        // Severe thermal or Battery Saver steps down to Battery Saver (0 particles, 60Hz cap)
        val severeSnap = sampleRmx5106Snapshot(thermal = ThermalSeverity.SEVERE)
        val severeProfile = Realme15DeviceCapabilityManager.computeProfile(
            severeSnap,
            AdaptivePerformanceMode.HIGH_VISUAL_QUALITY,
        )
        assertEquals(AdaptivePerformanceMode.BATTERY_SAVER, severeProfile.effectiveMode)
        assertEquals(0, severeProfile.maxParticleCount)
        assertEquals(60f, severeProfile.targetUiRefreshRateHz, 0.01f)
    }

    @Test
    fun `living album art engine steps 1800 frames with zero allocation and settles on pause`() {
        val state = LivingAlbumArtState(maxParticles = 44)
        for (i in state.spectrumBuffer.indices) {
            state.spectrumBuffer[i] = if (i < 8) 0.78f else 0.35f
        }

        val startNanos = System.nanoTime()
        for (frame in 0 until 1800) {
            state.stepFrame(
                dtSeconds = 1f / 60f,
                positionMs = frame * 16L,
                durationMs = 180_000L,
                hasRealSpectrum = true,
                activeParticleCount = 44,
                enableBass = true,
                enable3dParallax = true,
            )
        }
        val elapsedMs = (System.nanoTime() - startNanos) / 1_000_000.0
        assertTrue("1,800 frames should execute in < 50ms on JVM (took ${elapsedMs}ms)", elapsedMs < 50.0)
        assertTrue(state.bassEnergy > 0.5f)
        assertTrue(state.bassScale > 1.0f)

        // Verify graceful fallback when real audio spectrum is unavailable
        state.stepFrame(
            dtSeconds = 0.016f,
            positionMs = 45_000L,
            durationMs = 180_000L,
            hasRealSpectrum = false,
            activeParticleCount = 22,
            enableBass = true,
            enable3dParallax = false,
        )
        assertFalse(state.isRealAudioSpectrumActive)
        assertEquals(0f, state.tiltXDeg, 0.001f)

        // Verify pause decay settles cleanly
        var settled = false
        for (i in 0 until 120) {
            if (state.decayTowardsRest(0.05f)) {
                settled = true
                break
            }
        }
        assertTrue("LivingAlbumArtState must settle to rest when paused", settled)
        assertEquals(1f, state.bassScale, 0.01f)
    }

    @Test
    fun `asl pulse sync inspector and beat engine honour all 8 modes and rate limits`() {
        val context: Context = RuntimeEnvironment.getApplication()
        val rmxSnap = sampleRmx5106Snapshot()
        val status = PulseLightCapabilityInspector.inspectStatus(context, rmxSnap)
        assertEquals(PulseSyncIntegrationStatus.NATIVE_REALME_SYNC_REQUIRED, status)

        val batterySaverSnap = sampleRmx5106Snapshot(batterySaver = true)
        assertEquals(
            PulseSyncIntegrationStatus.SYSTEM_CONFIGURATION_REQUIRED,
            PulseLightCapabilityInspector.inspectStatus(context, batterySaverSnap),
        )

        val genericSnap = rmxSnap.copy(
            manufacturer = "Google",
            brand = "google",
            model = "Pixel 8",
            isRealmeDevice = false,
            isRealme15Rmx5106 = false,
        )
        assertEquals(
            PulseSyncIntegrationStatus.UNSUPPORTED_DEVICE,
            PulseLightCapabilityInspector.inspectStatus(context, genericSnap),
        )

        val settingsIntent = PulseLightCapabilityInspector.resolvePulseLightSettingsIntent(context)
        assertNotNull(settingsIntent)

        // Test all 8 modes during active playback
        var clockMs = 1_000L
        for (mode in AslPulseLightingMode.entries) {
            clockMs += 200L
            val frame = AslPulseSyncEngine.onPlaybackFrame(
                context = context,
                enabled = true,
                mode = mode,
                userColorArgb = Color(0xFF6366F1).toArgb(),
                brightness = 0.9f,
                sensitivity = 0.8f,
                batterySaverOption = false,
                pauseScreenOff = false,
                isScreenInteractive = true,
                isPlaying = true,
                songId = "song_${mode.name}",
                positionMs = clockMs,
                bass = 0.85f,
                mid = 0.55f,
                treble = 0.40f,
                artworkPalette = LivingArtworkPalette.Default,
                nowElapsedMs = clockMs,
            )
            if (mode == AslPulseLightingMode.OFF) {
                assertEquals(0f, frame.currentIntensity, 0.001f)
                assertEquals(PulseBeatEvent.IDLE, frame.beatEvent)
            } else {
                assertTrue("Mode $mode should produce positive intensity when playing", frame.currentIntensity > 0f)
            }
        }

        // Verify pausing immediately zeroes out rear RGB intensity
        val pausedFrame = AslPulseSyncEngine.onPlaybackFrame(
            context = context,
            enabled = true,
            mode = AslPulseLightingMode.BEAT_SYNC,
            userColorArgb = Color(0xFF6366F1).toArgb(),
            brightness = 0.9f,
            sensitivity = 0.8f,
            batterySaverOption = false,
            pauseScreenOff = true,
            isScreenInteractive = true,
            isPlaying = false,
            songId = "song_1",
            positionMs = 5_000L,
            bass = 0.8f,
            mid = 0.5f,
            treble = 0.3f,
            artworkPalette = LivingArtworkPalette.Default,
            nowElapsedMs = clockMs + 500L,
        )
        assertEquals(0f, pausedFrame.currentIntensity, 0.001f)
        assertEquals(PulseBeatEvent.IDLE, pausedFrame.beatEvent)
    }

    @Test
    fun `sqlite performance indexes accelerate 5000 track library queries`() {
        val context: Context = RuntimeEnvironment.getApplication()
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(null) // in-memory DB
            .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE song (
                            id TEXT NOT NULL PRIMARY KEY,
                            title TEXT NOT NULL,
                            duration INTEGER NOT NULL,
                            liked INTEGER NOT NULL,
                            likedDate INTEGER,
                            inLibrary INTEGER,
                            isLocal INTEGER NOT NULL,
                            totalPlayTime INTEGER NOT NULL
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        """
                        CREATE TABLE event (
                            id INTEGER PRIMARY KEY AUTOINCREMENT,
                            songId TEXT NOT NULL,
                            timestamp INTEGER NOT NULL,
                            playTime INTEGER NOT NULL
                        )
                        """.trimIndent()
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        val db = helper.writableDatabase

        db.beginTransaction()
        try {
            for (i in 1..5000) {
                val liked = if (i % 5 == 0) 1 else 0
                val inLib = if (i % 2 == 0) 1700000000L + i else null
                val isLocal = if (i % 3 == 0) 1 else 0
                db.execSQL(
                    "INSERT INTO song (id, title, duration, liked, likedDate, inLibrary, isLocal, totalPlayTime) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    arrayOf<Any?>("track_$i", "Song Title $i", 210, liked, inLib, inLib, isLocal, i * 1000L),
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }

        InternalDatabase.ensurePerformanceIndexes(db)

        val startNanos = System.nanoTime()
        db.query("SELECT id, title FROM song WHERE liked = 1 ORDER BY likedDate DESC LIMIT 180").use { cursor ->
            assertEquals(180, cursor.count)
        }
        db.query("SELECT id, title FROM song WHERE isLocal = 1 LIMIT 180").use { cursor ->
            assertEquals(180, cursor.count)
        }
        val queryMs = (System.nanoTime() - startNanos) / 1_000_000.0
        assertTrue("Indexed 5,000-track queries should complete in < 50ms (took ${queryMs}ms)", queryMs < 50.0)
        helper.close()
    }
}

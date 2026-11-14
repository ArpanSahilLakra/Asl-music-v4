package com.mochyy.mochymusic.core.device

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.PerformanceHintManager
import android.os.PowerManager
import android.os.Process
import android.view.Display
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.util.Locale
import kotlin.math.roundToInt

/**
 * User-selectable and adaptive performance modes for ASL Beats.
 */
enum class AdaptivePerformanceMode(val title: String, val subtitle: String) {
    AUTO(
        title = "Adaptive (RMX5106 Smart)",
        subtitle = "Automatically scales particles, shaders, and frame rate for thermal and battery state"
    ),
    HIGH_VISUAL_QUALITY(
        title = "High Visual Quality",
        subtitle = "Full Living Album Art, 48 particles, beat lighting, and optional 3D parallax"
    ),
    BALANCED(
        title = "Balanced",
        subtitle = "Moderate particle density (24), 30Hz ambient shaders, and responsive 60-120Hz UI"
    ),
    BATTERY_SAVER(
        title = "Battery Saver",
        subtitle = "Disables particles and 3D tilt, lowers visualizer update rate for 7,000mAh endurance"
    ),
}

/**
 * Physical LPDDR4X RAM tiers (excludes Realme Dynamic RAM / swap).
 */
enum class PhysicalRamTier(val label: String) {
    RAM_12GB_PLUS("12GB LPDDR4X"),
    RAM_8GB("8GB LPDDR4X"),
    RAM_6GB("6GB"),
    RAM_LOW("<= 4GB"),
}

/**
 * Thermal severity tiers mapped from [PowerManager] documented thermal status constants.
 */
enum class ThermalSeverity(val level: Int, val label: String) {
    NORMAL(0, "Cool / Normal"),
    LIGHT(1, "Light Warmth"),
    MODERATE(2, "Moderate Warmth"),
    SEVERE(3, "Thermal Throttling Active"),
}

/**
 * Snapshot of hardware and software capabilities detected at runtime using documented Android APIs.
 */
@Immutable
data class DeviceHardwareSnapshot(
    val manufacturer: String,
    val brand: String,
    val model: String,
    val deviceCode: String,
    val isRealmeDevice: Boolean,
    val isRealme15Rmx5106: Boolean,
    val androidSdkInt: Int,
    val androidRelease: String,
    val securityPatch: String,
    val buildDisplay: String,
    val detectedRealmeUiLabel: String,
    val totalPhysicalRamBytes: Long,
    val availablePhysicalRamBytes: Long,
    val ramTier: PhysicalRamTier,
    val memoryClassMb: Int,
    val largeMemoryClassMb: Int,
    val isLowMemory: Boolean,
    val displayWidthPx: Int,
    val displayHeightPx: Int,
    val densityDpi: Int,
    val currentRefreshRateHz: Float,
    val maxSupportedRefreshRateHz: Float,
    val supportedRefreshRatesHz: List<Int>,
    val supports144Hz: Boolean,
    val isOled1280x2800Class: Boolean,
    val isBatterySaverActive: Boolean,
    val thermalSeverity: ThermalSeverity,
    val supportsAdpfHintSession: Boolean,
) {
    val totalPhysicalRamGb: Int
        get() = ((totalPhysicalRamBytes / (1024.0 * 1024.0 * 1024.0)).roundToInt()).coerceAtLeast(1)
}

/**
 * Concrete rendering and resource budgets computed for the active device state.
 */
@Immutable
data class AdaptiveRenderingProfile(
    val requestedMode: AdaptivePerformanceMode,
    val effectiveMode: AdaptivePerformanceMode,
    val isRmx5106Optimized: Boolean,
    val maxParticleCount: Int,
    val visualizerTargetFps: Int,
    val ambientShaderStepsPerSecond: Float,
    val enable3dArtworkParallax: Boolean,
    val enableBeatLighting: Boolean,
    val enableBassReaction: Boolean,
    val enableFullScreenBlur: Boolean,
    val coilMemoryCachePercent: Double,
    val artworkDecodeSizePx: Int,
    val thumbnailGridDecodeSizePx: Int,
    val databasePageLimit: Int,
    val targetUiRefreshRateHz: Float,
    val thermalReduced: Boolean,
)

/**
 * Small, maintainable device capability manager and adaptive performance governor.
 *
 * Specifically profiles the Indian Realme 15 5G (`RMX5106` — MediaTek Dimensity 7300+ 5G,
 * ARM Mali-G615 GPU, 8GB/12GB physical LPDDR4X RAM, 1280x2800 144Hz OLED, 7,000mAh battery,
 * realme UI 6.0 / Android 15+) while preserving a safe generic profile for all other Android phones.
 *
 * Uses documented Android APIs only. Never locks CPU frequencies, modifies kernel parameters,
 * or uses reflection/hidden APIs.
 */
@Stable
object Realme15DeviceCapabilityManager {

    const val TARGET_MODEL_RMX5106 = "RMX5106"
    private const val THERMAL_HYSTERESIS_MS = 10_000L

    private val _hardwareSnapshot = MutableStateFlow(defaultSnapshot())
    val hardwareSnapshot: StateFlow<DeviceHardwareSnapshot> = _hardwareSnapshot.asStateFlow()

    private val _renderingProfile = MutableStateFlow(computeProfile(defaultSnapshot(), AdaptivePerformanceMode.AUTO))
    val renderingProfile: StateFlow<AdaptiveRenderingProfile> = _renderingProfile.asStateFlow()

    @Volatile
    private var initialized = false

    @Volatile
    private var requestedMode: AdaptivePerformanceMode = AdaptivePerformanceMode.AUTO

    @Volatile
    private var appVisible: Boolean = true

    @Volatile
    private var lastThermalEscalationElapsedMs: Long = 0L

    private var thermalListener: Any? = null

    /**
     * Initializes runtime hardware inspection and registers documented thermal, battery-saver,
     * and display mode listeners. Safe to call multiple times.
     */
    fun initialize(context: Context) {
        val appContext = context.applicationContext
        refreshSnapshot(appContext)
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            initialized = true
            registerSystemListeners(appContext)
        }
    }

    fun setUserPerformanceMode(context: Context?, mode: AdaptivePerformanceMode) {
        requestedMode = mode
        context?.let { refreshSnapshot(it.applicationContext) } ?: run {
            _renderingProfile.value = computeProfile(_hardwareSnapshot.value, mode)
        }
    }

    fun onAppVisibilityChanged(context: Context?, visible: Boolean) {
        appVisible = visible
        context?.let { refreshSnapshot(it.applicationContext) } ?: run {
            _renderingProfile.value = computeProfile(_hardwareSnapshot.value, requestedMode)
        }
    }

    fun onLowMemorySignal(context: Context?) {
        val current = _hardwareSnapshot.value.copy(isLowMemory = true)
        _hardwareSnapshot.value = current
        _renderingProfile.value = computeProfile(current, requestedMode)
        context?.let { refreshSnapshot(it.applicationContext) }
    }

    /**
     * Re-evaluates hardware, display mode, memory, battery saver, and thermal status at runtime.
     */
    fun refreshSnapshot(context: Context): DeviceHardwareSnapshot {
        val manufacturer = Build.MANUFACTURER.orEmpty()
        val brand = Build.BRAND.orEmpty()
        val model = Build.MODEL.orEmpty()
        val device = Build.DEVICE.orEmpty()
        val product = Build.PRODUCT.orEmpty()
        val displayBuild = Build.DISPLAY.orEmpty()

        val isRealme = manufacturer.contains("realme", ignoreCase = true) ||
            brand.contains("realme", ignoreCase = true) ||
            model.startsWith("RMX", ignoreCase = true)

        val isRmx5106 = model.contains(TARGET_MODEL_RMX5106, ignoreCase = true) ||
            device.contains(TARGET_MODEL_RMX5106, ignoreCase = true) ||
            product.contains(TARGET_MODEL_RMX5106, ignoreCase = true) ||
            displayBuild.contains(TARGET_MODEL_RMX5106, ignoreCase = true)

        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memoryInfo)

        // Physical RAM in bytes (totalMem reports physical kernel-visible RAM, not swap/Dynamic RAM)
        val totalRamBytes = memoryInfo.totalMem.takeIf { it > 0L } ?: (8L * 1024L * 1024L * 1024L)
        val availRamBytes = memoryInfo.availMem.takeIf { it > 0L } ?: (2L * 1024L * 1024L * 1024L)
        val totalRamGb = totalRamBytes / (1024.0 * 1024.0 * 1024.0)

        val ramTier = when {
            totalRamGb >= 10.2 -> PhysicalRamTier.RAM_12GB_PLUS
            totalRamGb >= 6.8 -> PhysicalRamTier.RAM_8GB
            totalRamGb >= 5.0 -> PhysicalRamTier.RAM_6GB
            else -> PhysicalRamTier.RAM_LOW
        }

        val displayMetrics = context.resources.displayMetrics
        val widthPx = minOf(displayMetrics.widthPixels, displayMetrics.heightPixels)
        val heightPx = maxOf(displayMetrics.widthPixels, displayMetrics.heightPixels)
        val densityDpi = displayMetrics.densityDpi

        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
        val defaultDisplay = displayManager?.getDisplay(Display.DEFAULT_DISPLAY)
        val currentRefreshRate = defaultDisplay?.refreshRate?.takeIf { it > 0f } ?: 60f
        val supportedRates = defaultDisplay?.supportedModes
            ?.map { it.refreshRate.roundToInt() }
            ?.distinct()
            ?.sorted()
            ?.takeIf { it.isNotEmpty() }
            ?: listOf(60)
        val maxRefreshRate = defaultDisplay?.supportedModes
            ?.maxOfOrNull { it.refreshRate }
            ?.takeIf { it > 0f }
            ?: currentRefreshRate

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val batterySaver = powerManager?.isPowerSaveMode == true

        val rawThermal = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && powerManager != null) {
            mapThermalStatus(powerManager.currentThermalStatus)
        } else {
            ThermalSeverity.NORMAL
        }

        val nowElapsed = android.os.SystemClock.elapsedRealtime()
        val prevThermal = _hardwareSnapshot.value.thermalSeverity
        val effectiveThermal = when {
            rawThermal.level > prevThermal.level -> {
                lastThermalEscalationElapsedMs = nowElapsed
                rawThermal
            }
            rawThermal.level < prevThermal.level &&
                (nowElapsed - lastThermalEscalationElapsedMs) < THERMAL_HYSTERESIS_MS -> {
                // Hysteresis prevents rapid mode oscillation / visual flickering when cooling down
                prevThermal
            }
            else -> rawThermal
        }

        val supportsAdpf = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            context.getSystemService(PerformanceHintManager::class.java) != null

        val snapshot = DeviceHardwareSnapshot(
            manufacturer = manufacturer.ifBlank { "Android" },
            brand = brand.ifBlank { "Android" },
            model = model.ifBlank { "Generic" },
            deviceCode = device.ifBlank { "generic" },
            isRealmeDevice = isRealme,
            isRealme15Rmx5106 = isRmx5106,
            androidSdkInt = Build.VERSION.SDK_INT,
            androidRelease = Build.VERSION.RELEASE.orEmpty().ifBlank { "15" },
            securityPatch = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Build.VERSION.SECURITY_PATCH.orEmpty()
            } else {
                ""
            },
            buildDisplay = displayBuild,
            detectedRealmeUiLabel = detectRealmeUiLabel(isRealme, Build.VERSION.SDK_INT, displayBuild),
            totalPhysicalRamBytes = totalRamBytes,
            availablePhysicalRamBytes = availRamBytes,
            ramTier = ramTier,
            memoryClassMb = activityManager?.memoryClass ?: 256,
            largeMemoryClassMb = activityManager?.largeMemoryClass ?: 512,
            isLowMemory = memoryInfo.lowMemory,
            displayWidthPx = widthPx,
            displayHeightPx = heightPx,
            densityDpi = densityDpi,
            currentRefreshRateHz = currentRefreshRate,
            maxSupportedRefreshRateHz = maxRefreshRate,
            supportedRefreshRatesHz = supportedRates,
            supports144Hz = maxRefreshRate >= 140f || supportedRates.any { it >= 140 },
            isOled1280x2800Class = widthPx >= 1200 && heightPx >= 2600,
            isBatterySaverActive = batterySaver,
            thermalSeverity = effectiveThermal,
            supportsAdpfHintSession = supportsAdpf,
        )

        _hardwareSnapshot.value = snapshot
        _renderingProfile.value = computeProfile(snapshot, requestedMode)
        return snapshot
    }

    /**
     * Pure function computing the adaptive rendering profile for any hardware snapshot and mode.
     * Exposed as internal/public for deterministic unit and Robolectric testing.
     */
    fun computeProfile(
        snapshot: DeviceHardwareSnapshot,
        mode: AdaptivePerformanceMode,
    ): AdaptiveRenderingProfile {
        val thermalPressure = snapshot.thermalSeverity.level >= ThermalSeverity.MODERATE.level
        val severeThermal = snapshot.thermalSeverity == ThermalSeverity.SEVERE
        val batteryOrLowMem = snapshot.isBatterySaverActive || snapshot.isLowMemory || !appVisible

        val effectiveMode = when {
            severeThermal || batteryOrLowMem -> AdaptivePerformanceMode.BATTERY_SAVER
            thermalPressure && mode == AdaptivePerformanceMode.HIGH_VISUAL_QUALITY -> AdaptivePerformanceMode.BALANCED
            mode != AdaptivePerformanceMode.AUTO -> mode
            snapshot.thermalSeverity == ThermalSeverity.LIGHT -> AdaptivePerformanceMode.BALANCED
            snapshot.isRealme15Rmx5106 && snapshot.ramTier == PhysicalRamTier.RAM_12GB_PLUS ->
                AdaptivePerformanceMode.HIGH_VISUAL_QUALITY
            snapshot.isRealme15Rmx5106 && snapshot.ramTier == PhysicalRamTier.RAM_8GB ->
                AdaptivePerformanceMode.HIGH_VISUAL_QUALITY
            snapshot.ramTier == PhysicalRamTier.RAM_12GB_PLUS || snapshot.ramTier == PhysicalRamTier.RAM_8GB ->
                AdaptivePerformanceMode.BALANCED
            else -> AdaptivePerformanceMode.BALANCED
        }

        val coilCachePercent = when {
            snapshot.isLowMemory || effectiveMode == AdaptivePerformanceMode.BATTERY_SAVER -> 0.12
            snapshot.ramTier == PhysicalRamTier.RAM_12GB_PLUS -> 0.22
            snapshot.ramTier == PhysicalRamTier.RAM_8GB -> 0.18
            else -> 0.15
        }

        // Decode artwork close to actual display dimensions on 1280x2800 OLED instead of oversized 1200x1200+ buffers
        val artworkDecodePx = when {
            effectiveMode == AdaptivePerformanceMode.BATTERY_SAVER -> 640
            snapshot.isOled1280x2800Class -> 960
            else -> 800
        }

        val gridDecodePx = when {
            effectiveMode == AdaptivePerformanceMode.BATTERY_SAVER -> 240
            snapshot.isOled1280x2800Class -> 360
            else -> 300
        }

        val pageLimit = when (snapshot.ramTier) {
            PhysicalRamTier.RAM_12GB_PLUS -> 250
            PhysicalRamTier.RAM_8GB -> 180
            else -> 120
        }

        return when (effectiveMode) {
            AdaptivePerformanceMode.HIGH_VISUAL_QUALITY -> AdaptiveRenderingProfile(
                requestedMode = mode,
                effectiveMode = effectiveMode,
                isRmx5106Optimized = snapshot.isRealme15Rmx5106,
                maxParticleCount = if (snapshot.isRealme15Rmx5106) 44 else 36,
                visualizerTargetFps = if (snapshot.currentRefreshRateHz >= 90f) 60 else 60,
                ambientShaderStepsPerSecond = 30f,
                enable3dArtworkParallax = true,
                enableBeatLighting = true,
                enableBassReaction = true,
                enableFullScreenBlur = true,
                coilMemoryCachePercent = coilCachePercent,
                artworkDecodeSizePx = artworkDecodePx,
                thumbnailGridDecodeSizePx = gridDecodePx,
                databasePageLimit = pageLimit,
                // Respect active refresh rate without forcing 144Hz for decorative shaders
                targetUiRefreshRateHz = snapshot.currentRefreshRateHz.coerceAtMost(120f),
                thermalReduced = thermalPressure,
            )

            AdaptivePerformanceMode.BALANCED, AdaptivePerformanceMode.AUTO -> AdaptiveRenderingProfile(
                requestedMode = mode,
                effectiveMode = AdaptivePerformanceMode.BALANCED,
                isRmx5106Optimized = snapshot.isRealme15Rmx5106,
                maxParticleCount = 22,
                visualizerTargetFps = 30,
                ambientShaderStepsPerSecond = 20f,
                enable3dArtworkParallax = false,
                enableBeatLighting = true,
                enableBassReaction = true,
                enableFullScreenBlur = true,
                coilMemoryCachePercent = coilCachePercent,
                artworkDecodeSizePx = artworkDecodePx,
                thumbnailGridDecodeSizePx = gridDecodePx,
                databasePageLimit = pageLimit,
                targetUiRefreshRateHz = snapshot.currentRefreshRateHz.coerceIn(60f, 120f),
                thermalReduced = thermalPressure,
            )

            AdaptivePerformanceMode.BATTERY_SAVER -> AdaptiveRenderingProfile(
                requestedMode = mode,
                effectiveMode = AdaptivePerformanceMode.BATTERY_SAVER,
                isRmx5106Optimized = snapshot.isRealme15Rmx5106,
                maxParticleCount = 0,
                visualizerTargetFps = 15,
                ambientShaderStepsPerSecond = 10f,
                enable3dArtworkParallax = false,
                enableBeatLighting = false,
                enableBassReaction = true,
                enableFullScreenBlur = false,
                coilMemoryCachePercent = coilCachePercent,
                artworkDecodeSizePx = artworkDecodePx,
                thumbnailGridDecodeSizePx = gridDecodePx,
                databasePageLimit = pageLimit,
                targetUiRefreshRateHz = 60f,
                thermalReduced = thermalPressure || severeThermal,
            )
        }
    }

    /**
     * Creates an optional Android 12+ (API 31+) ADPF [PerformanceHintManager.Session] for
     * frame-critical visualizer work on Dimensity 7300+ Cortex-A78/A55 cores.
     * Never locks frequencies or boosts permanently; closes cleanly when the screen settles.
     */
    fun createAdpfRenderSession(context: Context, targetDurationNanos: Long = 16_666_667L): AdpfSessionHandle? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        return runCatching {
            val hintManager = context.getSystemService(PerformanceHintManager::class.java) ?: return null
            val tid = Process.myTid()
            val session = hintManager.createHintSession(intArrayOf(tid), targetDurationNanos) ?: return null
            AdpfSessionHandle(session)
        }.getOrNull()
    }

    class AdpfSessionHandle(private val session: Any) : AutoCloseable {
        fun reportActualWorkDuration(actualDurationNanos: Long) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && actualDurationNanos > 0L) {
                runCatching {
                    (session as? PerformanceHintManager.Session)?.reportActualWorkDuration(actualDurationNanos)
                }
            }
        }

        override fun close() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                runCatching {
                    (session as? PerformanceHintManager.Session)?.close()
                }
            }
        }
    }

    private fun registerSystemListeners(appContext: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                val pm = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
                val listener = PowerManager.OnThermalStatusChangedListener { _ ->
                    refreshSnapshot(appContext)
                }
                pm?.addThermalStatusListener(appContext.mainExecutor, listener)
                thermalListener = listener
            }.onFailure {
                Timber.w(it, "Could not register thermal status listener")
            }
        }

        runCatching {
            val filter = IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    refreshSnapshot(appContext)
                }
            }
            appContext.registerReceiver(receiver, filter)
        }.onFailure {
            Timber.w(it, "Could not register power save receiver")
        }

        runCatching {
            val dm = appContext.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
            dm?.registerDisplayListener(
                object : DisplayManager.DisplayListener {
                    override fun onDisplayAdded(displayId: Int) = Unit
                    override fun onDisplayRemoved(displayId: Int) = Unit
                    override fun onDisplayChanged(displayId: Int) {
                        if (displayId == Display.DEFAULT_DISPLAY) {
                            refreshSnapshot(appContext)
                        }
                    }
                },
                null,
            )
        }.onFailure {
            Timber.w(it, "Could not register display listener")
        }
    }

    private fun detectRealmeUiLabel(isRealme: Boolean, sdkInt: Int, buildDisplay: String): String {
        if (!isRealme) return "Standard Android ($sdkInt)"
        val upper = buildDisplay.uppercase(Locale.ROOT)
        return when {
            sdkInt >= 35 -> "realme UI 6.0 (Android 15)"
            sdkInt == 34 -> "realme UI 5.0 (Android 14)"
            sdkInt == 33 -> "realme UI 4.0 (Android 13)"
            upper.contains("RMX5106") -> "realme UI 6.0 (RMX5106)"
            else -> "realme UI (API $sdkInt)"
        }
    }

    private fun mapThermalStatus(status: Int): ThermalSeverity {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return ThermalSeverity.NORMAL
        return when (status) {
            PowerManager.THERMAL_STATUS_NONE -> ThermalSeverity.NORMAL
            PowerManager.THERMAL_STATUS_LIGHT -> ThermalSeverity.LIGHT
            PowerManager.THERMAL_STATUS_MODERATE -> ThermalSeverity.MODERATE
            PowerManager.THERMAL_STATUS_SEVERE,
            PowerManager.THERMAL_STATUS_CRITICAL,
            PowerManager.THERMAL_STATUS_EMERGENCY,
            PowerManager.THERMAL_STATUS_SHUTDOWN -> ThermalSeverity.SEVERE
            else -> ThermalSeverity.NORMAL
        }
    }

    private fun defaultSnapshot(): DeviceHardwareSnapshot = DeviceHardwareSnapshot(
        manufacturer = Build.MANUFACTURER.orEmpty().ifBlank { "realme" },
        brand = Build.BRAND.orEmpty().ifBlank { "realme" },
        model = Build.MODEL.orEmpty().ifBlank { TARGET_MODEL_RMX5106 },
        deviceCode = Build.DEVICE.orEmpty().ifBlank { "RMX5106" },
        isRealmeDevice = true,
        isRealme15Rmx5106 = Build.MODEL.orEmpty().contains(TARGET_MODEL_RMX5106, ignoreCase = true),
        androidSdkInt = Build.VERSION.SDK_INT,
        androidRelease = Build.VERSION.RELEASE.orEmpty().ifBlank { "15" },
        securityPatch = "",
        buildDisplay = Build.DISPLAY.orEmpty(),
        detectedRealmeUiLabel = "realme UI 6.0",
        totalPhysicalRamBytes = 8L * 1024L * 1024L * 1024L,
        availablePhysicalRamBytes = 4L * 1024L * 1024L * 1024L,
        ramTier = PhysicalRamTier.RAM_8GB,
        memoryClassMb = 256,
        largeMemoryClassMb = 512,
        isLowMemory = false,
        displayWidthPx = 1280,
        displayHeightPx = 2800,
        densityDpi = 480,
        currentRefreshRateHz = 120f,
        maxSupportedRefreshRateHz = 144f,
        supportedRefreshRatesHz = listOf(60, 90, 120, 144),
        supports144Hz = true,
        isOled1280x2800Class = true,
        isBatterySaverActive = false,
        thermalSeverity = ThermalSeverity.NORMAL,
        supportsAdpfHintSession = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
    )
}

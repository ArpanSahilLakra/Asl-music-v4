package com.mochyy.mochymusic.ai

import androidx.compose.runtime.Immutable
import com.mochyy.mochymusic.eq.data.FilterType
import com.mochyy.mochymusic.eq.data.ParametricEQ
import com.mochyy.mochymusic.eq.data.ParametricEQBand
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min

@Immutable
data class HumMatchCandidate(
    val title: String,
    val artist: String,
    val confidencePercent: Int,
    val melodyMatchNote: String,
)

@Immutable
data class HumSearchResult(
    val detectedContourSummary: String,
    val estimatedTempoBpm: Int?,
    val candidates: List<HumMatchCandidate>,
)

enum class StemChannel {
    VOCALS,
    BASS,
    DRUMS,
    INSTRUMENTS,
}

@Immutable
data class StemMixState(
    val vocalsDb: Float = 0f,
    val bassDb: Float = 0f,
    val drumsDb: Float = 0f,
    val instrumentsDb: Float = 0f,
    val vocalsMuted: Boolean = false,
    val bassMuted: Boolean = false,
    val drumsMuted: Boolean = false,
    val instrumentsMuted: Boolean = false,
    val soloChannel: StemChannel? = null,
    val activePresetName: String = "Balanced",
) {
    fun effectiveGainDb(channel: StemChannel): Double {
        if (soloChannel != null && soloChannel != channel) return -12.0
        val muted = when (channel) {
            StemChannel.VOCALS -> vocalsMuted
            StemChannel.BASS -> bassMuted
            StemChannel.DRUMS -> drumsMuted
            StemChannel.INSTRUMENTS -> instrumentsMuted
        }
        if (muted) return -12.0
        val raw = when (channel) {
            StemChannel.VOCALS -> vocalsDb
            StemChannel.BASS -> bassDb
            StemChannel.DRUMS -> drumsDb
            StemChannel.INSTRUMENTS -> instrumentsDb
        }
        return raw.toDouble().coerceIn(-12.0, 8.0)
    }

    fun isNeutral(): Boolean =
        soloChannel == null &&
            !vocalsMuted && !bassMuted && !drumsMuted && !instrumentsMuted &&
            vocalsDb == 0f && bassDb == 0f && drumsDb == 0f && instrumentsDb == 0f

    /**
     * Converts the 4-stem console into a safe, headroom-compensated 6-band ParametricEQ curve
     * suitable for zero-latency real-time DSP processing in EqualizerService.
     */
    fun toParametricEq(): ParametricEQ {
        val bassGain = effectiveGainDb(StemChannel.BASS)
        val drumsGain = effectiveGainDb(StemChannel.DRUMS)
        val vocalsGain = effectiveGainDb(StemChannel.VOCALS)
        val instGain = effectiveGainDb(StemChannel.INSTRUMENTS)

        val maxPositive = max(0.0, max(max(bassGain, drumsGain), max(vocalsGain, instGain)))
        val safePreamp = (-maxPositive * 0.5).coerceIn(-6.0, 0.0)

        return ParametricEQ(
            preamp = safePreamp,
            bands = listOf(
                ParametricEQBand(frequency = 65.0, gain = bassGain, q = 0.85, filterType = FilterType.LSC),
                ParametricEQBand(frequency = 180.0, gain = (bassGain * 0.45 + drumsGain * 0.55).coerceIn(-12.0, 8.0), q = 1.1, filterType = FilterType.PK),
                ParametricEQBand(frequency = 950.0, gain = vocalsGain, q = 0.9, filterType = FilterType.PK),
                ParametricEQBand(frequency = 2600.0, gain = (vocalsGain * 0.75 + instGain * 0.25).coerceIn(-12.0, 8.0), q = 1.0, filterType = FilterType.PK),
                ParametricEQBand(frequency = 5600.0, gain = drumsGain, q = 1.2, filterType = FilterType.PK),
                ParametricEQBand(frequency = 10500.0, gain = instGain, q = 0.85, filterType = FilterType.HSC),
            ),
            metadata = mapOf("source" to "ASL Beats Stem Console", "preset" to activePresetName),
        )
    }
}

enum class ClippingStatus(val label: String) {
    IDLE("Waiting for Audio"),
    CLEAN("Clean Headroom"),
    HOT("Hot Peaks"),
    CLIPPING("Clipping Risk"),
}

@Immutable
data class AudioQualityTelemetry(
    val peakLevel: Float = 0f,
    val rmsLevel: Float = 0f,
    val estimatedDynamicRangeDb: Float = 0f,
    val bassBalance: Float = 0f,
    val midBalance: Float = 0f,
    val trebleBalance: Float = 0f,
    val clippingEvents: Int = 0,
    val status: ClippingStatus = ClippingStatus.IDLE,
)

object AudioQualityAnalyzer {

    fun analyzeSpectrumFrame(
        bands: FloatArray,
        previousClipCount: Int,
        isPlaying: Boolean,
    ): AudioQualityTelemetry {
        if (!isPlaying || bands.isEmpty()) {
            return AudioQualityTelemetry(
                clippingEvents = previousClipCount,
                status = ClippingStatus.IDLE,
            )
        }
        var peak = 0f
        var sqSum = 0f
        var lowSum = 0f
        var midSum = 0f
        var highSum = 0f

        val lowEnd = min(10, bands.size)
        val midEnd = min(26, bands.size)

        for (i in bands.indices) {
            val v = bands[i].coerceIn(0f, 1.2f)
            if (v > peak) peak = v
            sqSum += v * v
            when {
                i < lowEnd -> lowSum += v
                i < midEnd -> midSum += v
                else -> highSum += v
            }
        }

        val rms = kotlin.math.sqrt(sqSum / bands.size).coerceAtLeast(0.001f)
        val crestRatio = (peak.coerceAtLeast(rms) / rms).coerceIn(1f, 16f)
        val dynamicRangeDb = (20.0 * kotlin.math.log10(crestRatio.toDouble())).toFloat().coerceIn(0f, 24f)

        val lowAvg = if (lowEnd > 0) lowSum / lowEnd else 0f
        val midAvg = if (midEnd > lowEnd) midSum / (midEnd - lowEnd) else 0f
        val highAvg = if (bands.size > midEnd) highSum / (bands.size - midEnd) else 0f
        val totalBalance = (lowAvg + midAvg + highAvg).coerceAtLeast(0.001f)

        val isClipping = peak >= 0.96f
        val status = when {
            isClipping -> ClippingStatus.CLIPPING
            peak >= 0.85f -> ClippingStatus.HOT
            else -> ClippingStatus.CLEAN
        }

        return AudioQualityTelemetry(
            peakLevel = peak.coerceIn(0f, 1f),
            rmsLevel = rms.coerceIn(0f, 1f),
            estimatedDynamicRangeDb = dynamicRangeDb,
            bassBalance = (lowAvg / totalBalance).coerceIn(0f, 1f),
            midBalance = (midAvg / totalBalance).coerceIn(0f, 1f),
            trebleBalance = (highAvg / totalBalance).coerceIn(0f, 1f),
            clippingEvents = if (isClipping) previousClipCount + 1 else previousClipCount,
            status = status,
        )
    }
}

object AiPhase4JsonParsers {

    fun parseHumSearchJson(rawJson: String): HumSearchResult {
        val json = JSONObject(rawJson)
        val arr = json.optJSONArray("candidates")
        val candidates = buildList {
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val title = obj.optString("title", "").trim()
                    val artist = obj.optString("artist", "").trim()
                    if (title.isNotEmpty() && artist.isNotEmpty()) {
                        val reason = obj.optString("matchReason", "").trim()
                            .ifBlank { obj.optString("melodyMatchNote", "Melodic contour similarity").trim() }
                        add(
                            HumMatchCandidate(
                                title = title,
                                artist = artist,
                                confidencePercent = obj.optInt("confidencePercent", 70).coerceIn(1, 100),
                                melodyMatchNote = reason,
                            )
                        )
                    }
                }
            }
        }
        val rawBpm = json.optInt("estimatedTempoBpm", 0)
        val summary = json.optString("melodySummary", "").trim()
            .ifBlank { json.optString("detectedContourSummary", "Hummed melody analyzed").trim() }
        return HumSearchResult(
            detectedContourSummary = summary,
            estimatedTempoBpm = rawBpm.takeIf { it in 50..210 },
            candidates = candidates,
        )
    }
}

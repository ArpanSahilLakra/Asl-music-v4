package com.mochyy.mochymusic.ai

import androidx.compose.runtime.Immutable
import com.mochyy.mochymusic.eq.data.FilterType
import com.mochyy.mochymusic.eq.data.ParametricEQ
import com.mochyy.mochymusic.eq.data.ParametricEQBand
import org.json.JSONObject

enum class AudioOutputProfile(val label: String, val subtitle: String) {
    REALME_SPEAKERS("Dual Stereo Speakers", "Tuned for Realme 15 5G speakers"),
    EARBUDS_IEM("Earbuds / IEMs", "Balanced sub-bass & vocal intimacy"),
    STUDIO_OVER_EAR("Studio Headphones", "Wide soundstage & crisp detail"),
    BLUETOOTH_BASS("Bluetooth Party", "Deep punch & warm club lift"),
    NIGHT_QUIET("Late Night Quiet", "Smooth dynamics at low volume"),
}

@Immutable
data class AiMasteringBand(
    val frequencyHz: Double,
    val gainDb: Double,
    val q: Double,
    val label: String,
)

@Immutable
data class AiMasteringRecipe(
    val presetName: String,
    val summary: String,
    val preampDb: Double,
    val bands: List<AiMasteringBand>,
    val spatialEnabled: Boolean,
    val spatialAmount: Float,
    val reverbEnabled: Boolean,
    val reverbMix: Float,
    val loudnessBoostDb: Float,
) {
    fun toParametricEq(): ParametricEQ = ParametricEQ(
        preamp = preampDb.coerceIn(-8.0, 4.0),
        bands = bands.mapIndexed { idx, band ->
            val type = when (idx) {
                0 -> FilterType.LSC
                bands.lastIndex -> FilterType.HSC
                else -> FilterType.PK
            }
            ParametricEQBand(
                frequency = band.frequencyHz.coerceIn(30.0, 16000.0),
                gain = band.gainDb.coerceIn(-12.0, 12.0),
                q = band.q.coerceIn(0.5, 4.0),
                filterType = type,
            )
        },
        metadata = mapOf("source" to "ASL Beats AI Mastering", "preset" to presetName),
    )
}

@Immutable
data class ListeningDnaReport(
    val archetypeTitle: String,
    val sonicTagline: String,
    val vibeSummary: String,
    val topMoodTags: List<String>,
    val audioCoachingTip: String,
    val recommendedMoodSeed: String,
    val discoveryPicks: List<GeneratedTrackSuggestion>,
)

object AiPhase2JsonParsers {

    fun parseMasteringRecipeJson(rawJson: String): AiMasteringRecipe {
        val json = JSONObject(rawJson)
        val bandsArr = json.optJSONArray("bands")
        val bands = buildList {
            if (bandsArr != null) {
                for (i in 0 until bandsArr.length()) {
                    val obj = bandsArr.optJSONObject(i) ?: continue
                    add(
                        AiMasteringBand(
                            frequencyHz = obj.optDouble("frequencyHz", 1000.0).coerceIn(32.0, 16000.0),
                            gainDb = obj.optDouble("gainDb", 0.0).coerceIn(-12.0, 12.0),
                            q = obj.optDouble("q", 1.2).coerceIn(0.5, 4.0),
                            label = obj.optString("label", "Band ${i + 1}").trim(),
                        )
                    )
                }
            }
        }
        return AiMasteringRecipe(
            presetName = json.optString("presetName", "AI Studio Master").trim(),
            summary = json.optString("summary", "").trim(),
            preampDb = json.optDouble("preampDb", -1.5).coerceIn(-8.0, 4.0),
            bands = bands,
            spatialEnabled = json.optBoolean("spatialEnabled", false),
            spatialAmount = json.optDouble("spatialAmount", 0.7).toFloat().coerceIn(0.2f, 1.0f),
            reverbEnabled = json.optBoolean("reverbEnabled", false),
            reverbMix = json.optDouble("reverbMix", 0.25).toFloat().coerceIn(0.05f, 0.7f),
            loudnessBoostDb = json.optDouble("loudnessBoostDb", 2.0).toFloat().coerceIn(0f, 6f),
        )
    }

    fun parseListeningDnaJson(rawJson: String): ListeningDnaReport {
        val json = JSONObject(rawJson)
        val tagsArr = json.optJSONArray("topMoodTags")
        val tags = buildList {
            if (tagsArr != null) {
                for (i in 0 until tagsArr.length()) {
                    val tag = tagsArr.optString(i, "").trim()
                    if (tag.isNotEmpty()) add(tag)
                }
            }
        }
        val picksArr = json.optJSONArray("discoveryPicks")
        val picks = buildList {
            if (picksArr != null) {
                for (i in 0 until picksArr.length()) {
                    val obj = picksArr.optJSONObject(i) ?: continue
                    val title = obj.optString("title", "").trim()
                    val artist = obj.optString("artist", "").trim()
                    if (title.isNotEmpty() && artist.isNotEmpty()) {
                        add(
                            GeneratedTrackSuggestion(
                                title = title,
                                artist = artist,
                                reason = obj.optString("reason", "").trim(),
                            )
                        )
                    }
                }
            }
        }
        return ListeningDnaReport(
            archetypeTitle = json.optString("archetypeTitle", "Sonic Explorer").trim(),
            sonicTagline = json.optString("sonicTagline", "Curating atmosphere across genres").trim(),
            vibeSummary = json.optString("vibeSummary", "").trim(),
            topMoodTags = tags,
            audioCoachingTip = json.optString("audioCoachingTip", "").trim(),
            recommendedMoodSeed = json.optString("recommendedMoodSeed", "night_drive").trim(),
            discoveryPicks = picks,
        )
    }
}

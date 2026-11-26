package com.mochyy.mochymusic.ai

import androidx.compose.runtime.Immutable
import org.json.JSONObject

enum class DjPersona(
    val title: String,
    val subtitle: String,
    val defaultBpmRange: String,
    val preferredCrossmixStyle: String,
    val defaultCrossfadeSeconds: Float,
    val startColorHex: Long,
    val endColorHex: Long,
) {
    CHILL(
        title = "Chill DJ",
        subtitle = "Silky downtempo & lush velvet fades",
        defaultBpmRange = "78-96 BPM",
        preferredCrossmixStyle = "Silk Blend",
        defaultCrossfadeSeconds = 8f,
        startColorHex = 0xFF0D9488,
        endColorHex = 0xFF115E59,
    ),
    PARTY(
        title = "Party DJ",
        subtitle = "Beat-locked bass swaps & peak energy drops",
        defaultBpmRange = "118-130 BPM",
        preferredCrossmixStyle = "Bass Swap",
        defaultCrossfadeSeconds = 6f,
        startColorHex = 0xFFEC4899,
        endColorHex = 0xFF9D174D,
    ),
    WORKOUT(
        title = "Workout DJ",
        subtitle = "Relentless high-BPM momentum & drops",
        defaultBpmRange = "128-145 BPM",
        preferredCrossmixStyle = "Drop Cut",
        defaultCrossfadeSeconds = 4f,
        startColorHex = 0xFFEA580C,
        endColorHex = 0xFF9A3412,
    ),
    NIGHT_DRIVE(
        title = "Night Drive DJ",
        subtitle = "Harmonic glides & neon midnight synths",
        defaultBpmRange = "95-115 BPM",
        preferredCrossmixStyle = "Harmonic Glide",
        defaultCrossfadeSeconds = 9f,
        startColorHex = 0xFF4F46E5,
        endColorHex = 0xFF312E81,
    ),
    CLASSIC_RADIO(
        title = "Classic Radio DJ",
        subtitle = "Warm vocal hand-offs & timeless flow",
        defaultBpmRange = "90-118 BPM",
        preferredCrossmixStyle = "Vocal Hand-Off",
        defaultCrossfadeSeconds = 5f,
        startColorHex = 0xFFD97706,
        endColorHex = 0xFF78350F,
    ),
}

@Immutable
data class DjSetTrack(
    val title: String,
    val artist: String,
    val estimatedBpm: Int,
    val camelotKey: String,
    val energyPercent: Int,
    val transitionNote: String,
)

@Immutable
data class DjSetPlan(
    val setTitle: String,
    val personaName: String,
    val bpmArcSummary: String,
    val keyFlowSummary: String,
    val recommendedTransitionStyle: String,
    val optionalHostIntro: String,
    val tracks: List<DjSetTrack>,
)

@Immutable
data class ScreenshotIdentifiedTrack(
    val title: String,
    val artist: String,
    val album: String,
    val confidencePercent: Int,
    val detectedCue: String,
)

@Immutable
data class ScreenshotScanResult(
    val sourceSummary: String,
    val tracks: List<ScreenshotIdentifiedTrack>,
)

@Immutable
data class DuplicateCandidate(
    val id: String,
    val title: String,
    val artist: String,
)

@Immutable
data class DuplicateGroup(
    val normalizedKey: String,
    val displayTitle: String,
    val displayArtist: String,
    val itemIds: List<String>,
)

object DuplicateTrackDetector {

    private val parenSuffixRegex = Regex("""\s*[\(\[].*?[\)\]]""")
    private val nonAlnumRegex = Regex("""[^a-z0-9\s]""")
    private val whitespaceRegex = Regex("""\s+""")

    fun normalizeTrackKey(title: String, artist: String): String {
        val cleanTitle = title.lowercase()
            .replace(parenSuffixRegex, " ")
            .replace(nonAlnumRegex, " ")
            .replace(whitespaceRegex, " ")
            .trim()
        val cleanArtist = artist.lowercase()
            .substringBefore(",")
            .substringBefore("&")
            .substringBefore("feat")
            .replace(nonAlnumRegex, " ")
            .replace(whitespaceRegex, " ")
            .trim()
        return "$cleanTitle::$cleanArtist"
    }

    fun findDuplicateGroups(items: List<DuplicateCandidate>): List<DuplicateGroup> {
        return items
            .groupBy { normalizeTrackKey(it.title, it.artist) }
            .filter { (key, group) -> key.length > 3 && group.size > 1 }
            .map { (key, group) ->
                DuplicateGroup(
                    normalizedKey = key,
                    displayTitle = group.first().title,
                    displayArtist = group.first().artist,
                    itemIds = group.map { it.id },
                )
            }
            .sortedByDescending { it.itemIds.size }
    }
}

object AiPhase3JsonParsers {

    fun parseDjSetPlanJson(rawJson: String, persona: DjPersona): DjSetPlan {
        val json = JSONObject(rawJson)
        val arr = json.optJSONArray("tracks")
        val tracks = buildList {
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val title = obj.optString("title", "").trim()
                    val artist = obj.optString("artist", "").trim()
                    if (title.isNotEmpty() && artist.isNotEmpty()) {
                        add(
                            DjSetTrack(
                                title = title,
                                artist = artist,
                                estimatedBpm = obj.optInt("estimatedBpm", 110).coerceIn(55, 195),
                                camelotKey = obj.optString("camelotKey", "8A").trim().ifBlank { "8A" },
                                energyPercent = obj.optInt("energyPercent", 65).coerceIn(10, 100),
                                transitionNote = obj.optString("transitionNote", "Smooth phrase blend").trim(),
                            )
                        )
                    }
                }
            }
        }
        return DjSetPlan(
            setTitle = json.optString("setTitle", "${persona.title} Session").trim().ifBlank { "${persona.title} Session" },
            personaName = persona.title,
            bpmArcSummary = json.optString("bpmArcSummary", persona.defaultBpmRange).trim(),
            keyFlowSummary = json.optString("keyFlowSummary", "Harmonic Camelot Progression").trim(),
            recommendedTransitionStyle = json.optString("recommendedTransitionStyle", persona.preferredCrossmixStyle).trim(),
            optionalHostIntro = json.optString("optionalHostIntro", "").trim(),
            tracks = tracks,
        )
    }

    fun parseScreenshotScanJson(rawJson: String): ScreenshotScanResult {
        val json = JSONObject(rawJson)
        val arr = json.optJSONArray("tracks")
        val tracks = buildList {
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val title = obj.optString("title", "").trim()
                    val artist = obj.optString("artist", "").trim()
                    if (title.isNotEmpty()) {
                        add(
                            ScreenshotIdentifiedTrack(
                                title = title,
                                artist = artist.ifBlank { "Unknown Artist" },
                                album = obj.optString("album", "").trim(),
                                confidencePercent = obj.optInt("confidencePercent", 80).coerceIn(1, 100),
                                detectedCue = obj.optString("detectedCue", "Visual text & artwork match").trim(),
                            )
                        )
                    }
                }
            }
        }
        return ScreenshotScanResult(
            sourceSummary = json.optString("sourceSummary", "Music image analyzed").trim(),
            tracks = tracks,
        )
    }
}

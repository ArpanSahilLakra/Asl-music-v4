package com.mochyy.mochymusic.ai

import androidx.compose.runtime.Immutable
import org.json.JSONObject

sealed interface AiUiState<out T> {
    data object Idle : AiUiState<Nothing>
    data object Loading : AiUiState<Nothing>
    data class Streaming(val partialText: String) : AiUiState<Nothing>
    data class Success<T>(val data: T) : AiUiState<T>
    data class Error(val message: String) : AiUiState<Nothing>
}

enum class AiActionType {
    NONE,
    PLAY_QUERY,
    NEXT_TRACK,
    PREVIOUS_TRACK,
    PAUSE,
    RESUME,
    BOOST_BASS,
    RESET_EQ,
    ENABLE_SPATIAL,
    DISABLE_SPATIAL,
    ENABLE_KARAOKE,
    DISABLE_KARAOKE,
    TOGGLE_FAVORITE,
    OPEN_MOOD_PLAYLIST,
    EXPLAIN_CURRENT_SONG,
}

@Immutable
data class AiAssistantAction(
    val type: AiActionType = AiActionType.NONE,
    val query: String = "",
    val intensity: String = "medium",
    val note: String = "",
)

@Immutable
data class MoodPreset(
    val id: String,
    val title: String,
    val subtitle: String,
    val promptHint: String,
    val startColorHex: Long,
    val endColorHex: Long,
)

@Immutable
data class GeneratedTrackSuggestion(
    val title: String,
    val artist: String,
    val reason: String,
)

@Immutable
data class GeneratedPlaylistPlan(
    val title: String,
    val description: String,
    val moodLabel: String,
    val targetBpmRange: String,
    val tracks: List<GeneratedTrackSuggestion>,
)

@Immutable
data class SongExplanation(
    val songTitle: String,
    val artistName: String,
    val summary: String,
    val moodAndVibe: String,
    val themes: List<String>,
    val storyInterpretation: String,
    val artistAndEraContext: String,
    val languageNote: String,
    val listeningTip: String,
)

object MoodPresets {
    val All: List<MoodPreset> = listOf(
        MoodPreset("happy", "Happy", "Sunlit & uplifting", "Joyful, bright, feel-good melodies with upbeat bounce", 0xFFF59E0B, 0xFFEF4444),
        MoodPreset("sad", "Sad", "Cathartic & tender", "Emotional, introspective ballads and gentle acoustics", 0xFF3B82F6, 0xFF1E3A8A),
        MoodPreset("romantic", "Romantic", "Warm & intimate", "Lush, heartfelt love songs and velvety R&B grooves", 0xFFEC4899, 0xFF9D174D),
        MoodPreset("relaxing", "Relaxing", "Slow breath & calm", "Mellow chillout, soft indie, and soothing lo-fi textures", 0xFF14B8A6, 0xFF0F766E),
        MoodPreset("energetic", "Energetic", "High-voltage spark", "Punchy pop, dance anthems, and crisp driving percussion", 0xFFF97316, 0xFFDC2626),
        MoodPreset("motivational", "Motivational", "Rise & conquer", "Inspiring hooks, triumphant crescendos, and bold lyrics", 0xFF8B5CF6, 0xFF4F46E5),
        MoodPreset("focus", "Focus", "Deep flow state", "Minimal vocal distraction, steady rhythms, ambient electronic and neo-classical", 0xFF06B6D4, 0xFF1D4ED8),
        MoodPreset("workout", "Workout", "Peak adrenaline", "High-BPM bangers, heavy bass drops, and relentless momentum", 0xFFE11D48, 0xFF7C2D12),
        MoodPreset("night_drive", "Night Drive", "Neon highways", "Synthwave, atmospheric R&B, nocturnal basslines, and shimmering pads", 0xFF6366F1, 0xFF1E1B4B),
        MoodPreset("rainy", "Rainy Weather", "Cozy windowpane", "Warm acoustic folk, soft jazz, gentle piano, and wistful vocals", 0xFF64748B, 0xFF1E293B),
        MoodPreset("travel", "Travel", "Open road horizon", "Expansive indie anthems, breezily paced road-trip classics", 0xFF10B981, 0xFF047857),
        MoodPreset("meditation", "Meditation", "Stillness & clarity", "Ethereal soundscapes, healing drones, soft instrumental serenity", 0xFFA855F7, 0xFF581C87),
    )
}

object AiJsonParsers {
    fun parseActionJson(rawJson: String): AiAssistantAction {
        val json = JSONObject(rawJson)
        val rawAction = json.optString("action", "NONE").uppercase()
        val type = runCatching { AiActionType.valueOf(rawAction) }.getOrDefault(AiActionType.NONE)
        return AiAssistantAction(
            type = type,
            query = json.optString("query", "").trim(),
            intensity = json.optString("intensity", "medium").trim(),
            note = json.optString("note", "").trim(),
        )
    }

    fun parsePlaylistPlanJson(rawJson: String, fallbackMood: String): GeneratedPlaylistPlan {
        val json = JSONObject(rawJson)
        val tracksArray = json.optJSONArray("tracks")
        val tracks = buildList {
            if (tracksArray != null) {
                for (i in 0 until tracksArray.length()) {
                    val item = tracksArray.optJSONObject(i) ?: continue
                    val title = item.optString("title", "").trim()
                    val artist = item.optString("artist", "").trim()
                    if (title.isNotEmpty() && artist.isNotEmpty()) {
                        add(
                            GeneratedTrackSuggestion(
                                title = title,
                                artist = artist,
                                reason = item.optString("reason", "").trim(),
                            )
                        )
                    }
                }
            }
        }
        return GeneratedPlaylistPlan(
            title = json.optString("title", "$fallbackMood Mix").trim().ifBlank { "$fallbackMood Mix" },
            description = json.optString("description", "Curated by ASL Beats AI").trim(),
            moodLabel = json.optString("moodLabel", fallbackMood).trim().ifBlank { fallbackMood },
            targetBpmRange = json.optString("targetBpmRange", "95-120 BPM").trim(),
            tracks = tracks,
        )
    }

    fun parseSongExplanationJson(
        rawJson: String,
        songTitle: String,
        artistName: String,
    ): SongExplanation {
        val json = JSONObject(rawJson)
        val themesArr = json.optJSONArray("themes")
        val themes = buildList {
            if (themesArr != null) {
                for (i in 0 until themesArr.length()) {
                    val t = themesArr.optString(i, "").trim()
                    if (t.isNotEmpty()) add(t)
                }
            }
        }
        return SongExplanation(
            songTitle = songTitle,
            artistName = artistName,
            summary = json.optString("summary", "").trim(),
            moodAndVibe = json.optString("moodAndVibe", "").trim(),
            themes = themes,
            storyInterpretation = json.optString("storyInterpretation", "").trim(),
            artistAndEraContext = json.optString("artistAndEraContext", "").trim(),
            languageNote = json.optString("languageNote", "").trim(),
            listeningTip = json.optString("listeningTip", "").trim(),
        )
    }
}

package com.mochyy.mochymusic.ai

import com.mochyy.mochymusic.db.AllMigrations
import com.mochyy.mochymusic.db.MIGRATION_42_43
import com.mochyy.mochymusic.db.MusicDatabaseVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiPhase1UnitTest {

    @Test
    fun parseActionJson_parsesPlayQueryAndIntensity() {
        val raw = """
            {
              "action": "PLAY_QUERY",
              "query": "night drive synthwave",
              "intensity": "strong",
              "note": "Queueing synthwave mix"
            }
        """.trimIndent()
        val action = AiJsonParsers.parseActionJson(raw)
        assertEquals(AiActionType.PLAY_QUERY, action.type)
        assertEquals("night drive synthwave", action.query)
        assertEquals("strong", action.intensity)
        assertEquals("Queueing synthwave mix", action.note)
    }

    @Test
    fun parseActionJson_unknownActionFallsBackToNone() {
        val action = AiJsonParsers.parseActionJson("""{"action": "UNKNOWN_CMD"}""")
        assertEquals(AiActionType.NONE, action.type)
    }

    @Test
    fun parsePlaylistPlanJson_parsesCuratedTracks() {
        val raw = """
            {
              "title": "Midnight Neon Drive",
              "description": "Atmospheric synths for quiet highways",
              "moodLabel": "Night Drive",
              "targetBpmRange": "95-115 BPM",
              "tracks": [
                {"title": "Midnight City", "artist": "M83", "reason": "Shimmering nocturnal synth hook"},
                {"title": "Nightcall", "artist": "Kavinsky", "reason": "Iconic analog bassline"}
              ]
            }
        """.trimIndent()
        val plan = AiJsonParsers.parsePlaylistPlanJson(raw, "Night Drive")
        assertEquals("Midnight Neon Drive", plan.title)
        assertEquals("95-115 BPM", plan.targetBpmRange)
        assertEquals(2, plan.tracks.size)
        assertEquals("M83", plan.tracks[0].artist)
    }

    @Test
    fun parseSongExplanationJson_parsesAllInsightSections() {
        val raw = """
            {
              "summary": "A reflective anthem about resilience.",
              "moodAndVibe": "Warm and hopeful",
              "themes": ["Hope", "Growth", "Nostalgia"],
              "storyInterpretation": "Follows a journey from doubt to clarity.",
              "artistAndEraContext": "Blends modern indie pop with warm analog keys.",
              "languageNote": "Uses concise metaphorical phrasing.",
              "listeningTip": "Try light spatial audio with balanced EQ."
            }
        """.trimIndent()
        val explanation = AiJsonParsers.parseSongExplanationJson(raw, "Horizon", "Nova")
        assertEquals("Horizon", explanation.songTitle)
        assertEquals("Nova", explanation.artistName)
        assertEquals(3, explanation.themes.size)
        assertEquals("Warm and hopeful", explanation.moodAndVibe)
    }

    @Test
    fun moodPresets_containsAll12DistinctMoods() {
        assertEquals(12, MoodPresets.All.size)
        assertEquals(12, MoodPresets.All.map { it.id }.toSet().size)
    }

    @Test
    fun roomMigration42To43_registeredInAllMigrations() {
        assertEquals(43, MusicDatabaseVersion)
        assertTrue(AllMigrations.any { it === MIGRATION_42_43 && it.startVersion == 42 && it.endVersion == 43 })
    }
}

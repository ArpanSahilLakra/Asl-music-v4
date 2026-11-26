package com.mochyy.mochymusic.ai

import com.mochyy.mochymusic.alarm.MusicAlarmScheduler
import com.mochyy.mochymusic.eq.data.FilterType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class AiPhase2UnitTest {

    @Test
    fun parseMasteringRecipeJson_clampsBoundsAndMapsParametricFilterTypes() {
        val raw = """
            {
              "presetName": "Velvet Sub Master",
              "summary": "Adds deep sub-bass punch with crisp vocal presence.",
              "preampDb": -2.5,
              "bands": [
                {"frequencyHz": 55, "gainDb": 4.5, "q": 0.9, "label": "Sub Bass"},
                {"frequencyHz": 160, "gainDb": 1.5, "q": 1.1, "label": "Warmth"},
                {"frequencyHz": 1500, "gainDb": 2.0, "q": 1.4, "label": "Vocals"},
                {"frequencyHz": 11000, "gainDb": 3.0, "q": 0.8, "label": "Air"}
              ],
              "spatialEnabled": true,
              "spatialAmount": 0.8,
              "reverbEnabled": true,
              "reverbMix": 0.22,
              "loudnessBoostDb": 2.5
            }
        """.trimIndent()

        val recipe = AiPhase2JsonParsers.parseMasteringRecipeJson(raw)
        assertEquals("Velvet Sub Master", recipe.presetName)
        assertEquals(-2.5, recipe.preampDb, 0.001)
        assertEquals(4, recipe.bands.size)
        assertTrue(recipe.spatialEnabled)
        assertTrue(recipe.reverbEnabled)
        assertEquals(2.5f, recipe.loudnessBoostDb, 0.001f)

        val eq = recipe.toParametricEq()
        assertEquals(4, eq.bands.size)
        assertEquals(FilterType.LSC, eq.bands.first().filterType)
        assertEquals(FilterType.PK, eq.bands[1].filterType)
        assertEquals(FilterType.PK, eq.bands[2].filterType)
        assertEquals(FilterType.HSC, eq.bands.last().filterType)
    }

    @Test
    fun parseListeningDnaJson_extractsArchetypeTagsAndValidPicks() {
        val raw = """
            {
              "archetypeTitle": "Nocturnal Synth Architect",
              "sonicTagline": "Chasing warm analog horizons after midnight",
              "vibeSummary": "You gravitate toward lush textures and driving basslines.",
              "topMoodTags": ["Dreamy", "Atmospheric", "Bass-Forward", "Late Night"],
              "audioCoachingTip": "Try a subtle +2 dB 11kHz air shelf with 65% spatial width.",
              "recommendedMoodSeed": "night_drive",
              "discoveryPicks": [
                {"title": "Resonance", "artist": "HOME", "reason": "Nostalgic analog synth arpeggios"},
                {"title": "", "artist": "Invalid", "reason": "Should be skipped"},
                {"title": "Sunset", "artist": "The Midnight", "reason": "Widescreen retro chorus"}
              ]
            }
        """.trimIndent()

        val report = AiPhase2JsonParsers.parseListeningDnaJson(raw)
        assertEquals("Nocturnal Synth Architect", report.archetypeTitle)
        assertEquals(4, report.topMoodTags.size)
        assertEquals("night_drive", report.recommendedMoodSeed)
        assertEquals(2, report.discoveryPicks.size)
        assertEquals("HOME", report.discoveryPicks[0].artist)
        assertEquals("The Midnight", report.discoveryPicks[1].artist)
    }

    @Test
    fun computeNextTriggerMillis_oneShotMovesToNextDayWhenTimePassed() {
        val nowCal = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 25, 9, 30, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val trigger = MusicAlarmScheduler.computeNextTriggerMillis(
            hour = 7,
            minute = 15,
            repeatDaysMask = 0,
            nowMillis = nowCal.timeInMillis,
        )
        val triggerCal = Calendar.getInstance().apply { timeInMillis = trigger }
        assertTrue(trigger > nowCal.timeInMillis)
        assertEquals(26, triggerCal.get(Calendar.DAY_OF_MONTH))
        assertEquals(7, triggerCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(15, triggerCal.get(Calendar.MINUTE))
    }

    @Test
    fun computeNextTriggerMillis_repeatingMaskSelectsNextMatchingWeekday() {
        // 2026-11-25 is a Wednesday
        val wednesdayMorning = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 25, 10, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }
        // Friday = bit 4 (1 shl 4)
        val fridayBit = 1 shl 4
        val trigger = MusicAlarmScheduler.computeNextTriggerMillis(
            hour = 8,
            minute = 0,
            repeatDaysMask = fridayBit,
            nowMillis = wednesdayMorning.timeInMillis,
        )
        val triggerCal = Calendar.getInstance().apply { timeInMillis = trigger }
        assertEquals(Calendar.FRIDAY, triggerCal.get(Calendar.DAY_OF_WEEK))
        assertEquals(8, triggerCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, triggerCal.get(Calendar.MINUTE))
    }
}

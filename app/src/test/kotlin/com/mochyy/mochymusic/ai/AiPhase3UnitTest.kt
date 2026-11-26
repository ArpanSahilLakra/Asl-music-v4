package com.mochyy.mochymusic.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiPhase3UnitTest {

    @Test
    fun djPersonas_containsAllFiveExpectedPersonas() {
        assertEquals(5, DjPersona.entries.size)
        assertTrue(DjPersona.entries.contains(DjPersona.CHILL))
        assertTrue(DjPersona.entries.contains(DjPersona.PARTY))
        assertTrue(DjPersona.entries.contains(DjPersona.WORKOUT))
        assertTrue(DjPersona.entries.contains(DjPersona.NIGHT_DRIVE))
        assertTrue(DjPersona.entries.contains(DjPersona.CLASSIC_RADIO))
    }

    @Test
    fun parseDjSetPlanJson_parsesAndClampsBpmAndEnergy() {
        val raw = """
            {
              "setTitle": "Midnight Autobahn Mix",
              "bpmArcSummary": "102 -> 118 BPM",
              "keyFlowSummary": "8A -> 9A -> 9B",
              "recommendedTransitionStyle": "Harmonic Glide",
              "optionalHostIntro": "Lock in for a nocturnal synth voyage.",
              "tracks": [
                {
                  "title": "Nightcall",
                  "artist": "Kavinsky",
                  "estimatedBpm": 91,
                  "camelotKey": "8A",
                  "energyPercent": 58,
                  "transitionNote": "Warm pad intro into analog bass"
                },
                {
                  "title": "Blinding Lights",
                  "artist": "The Weeknd",
                  "estimatedBpm": 250,
                  "camelotKey": "9A",
                  "energyPercent": 120,
                  "transitionNote": "High-energy synth lift"
                }
              ]
            }
        """.trimIndent()

        val plan = AiPhase3JsonParsers.parseDjSetPlanJson(raw, DjPersona.NIGHT_DRIVE)
        assertEquals("Midnight Autobahn Mix", plan.setTitle)
        assertEquals(2, plan.tracks.size)
        assertEquals(91, plan.tracks[0].estimatedBpm)
        assertEquals("8A", plan.tracks[0].camelotKey)
        assertEquals(195, plan.tracks[1].estimatedBpm) // clamped to 195
        assertEquals(100, plan.tracks[1].energyPercent) // clamped to 100
    }

    @Test
    fun parseScreenshotScanJson_extractsTracksAndSkipsBlankTitles() {
        val raw = """
            {
              "sourceSummary": "Streaming app playlist screenshot with 2 tracks",
              "tracks": [
                {
                  "title": "Starboy",
                  "artist": "The Weeknd",
                  "album": "Starboy",
                  "confidencePercent": 96,
                  "detectedCue": "Title text and red neon album cover"
                },
                {
                  "title": "",
                  "artist": "Noise",
                  "album": "",
                  "confidencePercent": 10,
                  "detectedCue": "Artifact"
                },
                {
                  "title": "Instant Crush",
                  "artist": "Daft Punk",
                  "album": "Random Access Memories",
                  "confidencePercent": 91,
                  "detectedCue": "Tracklist row text"
                }
              ]
            }
        """.trimIndent()

        val scan = AiPhase3JsonParsers.parseScreenshotScanJson(raw)
        assertEquals(2, scan.tracks.size)
        assertEquals("Starboy", scan.tracks[0].title)
        assertEquals(96, scan.tracks[0].confidencePercent)
        assertEquals("Daft Punk", scan.tracks[1].artist)
    }

    @Test
    fun duplicateTrackDetector_normalizesRemasterAndFeatSuffixes() {
        val candidates = listOf(
            DuplicateCandidate("id_1", "Bohemian Rhapsody (Remastered 2011)", "Queen"),
            DuplicateCandidate("id_2", "Bohemian Rhapsody", "Queen"),
            DuplicateCandidate("id_3", "Midnight City (Radio Edit)", "M83, Feat. Guest"),
            DuplicateCandidate("id_4", "Midnight City", "M83"),
            DuplicateCandidate("id_5", "Unique Track", "Solo Artist"),
        )

        val groups = DuplicateTrackDetector.findDuplicateGroups(candidates)
        assertEquals(2, groups.size)
        assertTrue(groups.all { it.itemIds.size == 2 })
    }
}

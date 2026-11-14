package com.mochyy.mochymusic

import com.mochyy.mochymusic.api.ytmusic.YtAlbumRefDto
import com.mochyy.mochymusic.api.ytmusic.YtArtistRefDto
import com.mochyy.mochymusic.api.ytmusic.YtLyricLineDto
import com.mochyy.mochymusic.api.ytmusic.YtLyricsResponseDto
import com.mochyy.mochymusic.api.ytmusic.YtPlaylistDetailDto
import com.mochyy.mochymusic.api.ytmusic.YtSearchResponseDto
import com.mochyy.mochymusic.api.ytmusic.YtSongDetailDto
import com.mochyy.mochymusic.api.ytmusic.YtTrackSummaryDto
import com.mochyy.mochymusic.models.MediaMetadata
import com.mochyy.mochymusic.playback.audio.AudiusTrackDto
import com.mochyy.mochymusic.playback.audio.AudiusUserDto
import com.mochyy.mochymusic.playback.audio.LicensedAudioProvider
import com.mochyy.mochymusic.playback.audio.LocalAudioProvider
import com.mochyy.mochymusic.utils.YTPlayerUtils
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoveryAndAudioResolverTest {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    @Test
    fun `test song search normalization and SongItem mapping`() {
        val rawJson = """
            {
              "query": "Midnight City",
              "filter": "songs",
              "continuation": "20",
              "items": [
                {
                  "videoId": "dX3k_QDnzHE",
                  "title": "Midnight City",
                  "artist": "M83",
                  "artists": [{"id": "UC12345", "name": "M83"}],
                  "album": {"id": "MPREb_12345", "name": "Hurry Up, We're Dreaming"},
                  "durationSeconds": 243,
                  "durationText": "4:03",
                  "thumbnailUrl": "https://lh3.googleusercontent.com/thumb1.jpg",
                  "isExplicit": false
                }
              ],
              "suggestions": ["Midnight City M83", "Midnight City live"]
            }
        """.trimIndent()

        val decoded = json.decodeFromString(YtSearchResponseDto.serializer(), rawJson)
        assertEquals("Midnight City", decoded.query)
        assertEquals("20", decoded.continuation)
        assertEquals(1, decoded.items.size)
        assertEquals(2, decoded.suggestions.size)

        val songItem = decoded.items.first().toSongItem()
        assertEquals("dX3k_QDnzHE", songItem.id)
        assertEquals("Midnight City", songItem.title)
        assertEquals("M83", songItem.artists.first().name)
        assertEquals("UC12345", songItem.artists.first().id)
        assertEquals("Hurry Up, We're Dreaming", songItem.album?.name)
        assertEquals("MPREb_12345", songItem.album?.id)
        assertEquals(243, songItem.duration)
        assertEquals("https://lh3.googleusercontent.com/thumb1.jpg", songItem.thumbnail)
    }

    @Test
    fun `test song metadata and missing thumbnail fallback`() {
        val dtoWithMissingThumb = YtSongDetailDto(
            videoId = "abc123xyz00",
            title = "Unreleased Instrumental",
            artist = "ASL Beats Studio",
            artists = listOf(YtArtistRefDto(id = "UC_ASL", name = "ASL Beats Studio")),
            album = YtAlbumRefDto(id = "MPREb_ASL", name = "Studio Sessions"),
            durationSeconds = 195,
            thumbnailUrl = null,
            recommendedTracks = listOf(
                YtTrackSummaryDto(
                    videoId = "rec999",
                    title = "Follow Up Track",
                    artist = "ASL Beats Studio",
                    durationSeconds = 210,
                    thumbnailUrl = "",
                )
            ),
        )

        val songItem = dtoWithMissingThumb.toSongItem()
        assertEquals("abc123xyz00", songItem.id)
        assertEquals("Unreleased Instrumental", songItem.title)
        assertEquals("https://i.ytimg.com/vi/abc123xyz00/hqdefault.jpg", songItem.thumbnail)

        val recItem = dtoWithMissingThumb.recommendedTracks.first().toSongItem()
        assertEquals("https://i.ytimg.com/vi/rec999/hqdefault.jpg", recItem.thumbnail)
    }

    @Test
    fun `test missing lyrics, plain lyrics, and synchronized timestamped lyrics`() {
        val missingLyrics = YtLyricsResponseDto(
            videoId = "vid_no_lyrics",
            available = false,
            hasTimestamps = false,
            lyricsText = "",
            lines = emptyList(),
        )
        assertNull(missingLyrics.toFormattedLyricsString())

        val plainLyrics = YtLyricsResponseDto(
            videoId = "vid_plain",
            available = true,
            hasTimestamps = false,
            source = "YouTube Music",
            lyricsText = "First line of song\nSecond line of song",
            lines = emptyList(),
        )
        assertEquals(
            "First line of song\nSecond line of song",
            plainLyrics.toFormattedLyricsString(),
        )

        val syncedLyrics = YtLyricsResponseDto(
            videoId = "vid_synced",
            available = true,
            hasTimestamps = true,
            source = "LRCLIB / YouTube Music",
            lyricsText = "First line\nSecond line",
            lines = listOf(
                YtLyricLineDto(startTimeMs = 12_500L, endTimeMs = 16_000L, text = "First line"),
                YtLyricLineDto(startTimeMs = 65_040L, endTimeMs = 70_000L, text = "Second line"),
            ),
        )
        val formattedSynced = syncedLyrics.toFormattedLyricsString()
        assertNotNull(formattedSynced)
        assertEquals("[00:12.50]First line\n[01:05.04]Second line", formattedSynced)
    }

    @Test
    fun `test playlist pagination decoding`() {
        val page1Json = """
            {
              "playlistId": "PL_TEST_100",
              "title": "ASL Beats Synthwave",
              "author": {"id": "UC_CUR", "name": "ASL Curator"},
              "trackCount": 75,
              "continuation": "50",
              "tracks": [
                {
                  "videoId": "track_1",
                  "title": "Neon Horizon",
                  "artist": "Synth Runner",
                  "durationSeconds": 200
                }
              ]
            }
        """.trimIndent()

        val page1 = json.decodeFromString(YtPlaylistDetailDto.serializer(), page1Json)
        assertEquals("PL_TEST_100", page1.playlistId)
        assertEquals("50", page1.continuation)
        assertEquals(1, page1.tracks.size)
        assertEquals("Neon Horizon", page1.tracks.first().title)
    }

    @Test
    fun `test audio source matching scores local and licensed candidates accurately`() {
        val exactScore = LocalAudioProvider.scoreCandidate(
            targetTitle = LocalAudioProvider.normalizeToken("Blinding Lights"),
            targetArtists = listOf(LocalAudioProvider.normalizeToken("The Weeknd")),
            targetDurationSec = 200,
            candidateTitle = LocalAudioProvider.normalizeToken("Blinding Lights (Official Audio)"),
            candidateArtists = listOf(LocalAudioProvider.normalizeToken("The Weeknd")),
            candidateDurationSec = 201,
        )
        assertTrue("Expected high score for matching track, got $exactScore", exactScore >= 75)

        val mismatchScore = LocalAudioProvider.scoreCandidate(
            targetTitle = LocalAudioProvider.normalizeToken("Blinding Lights"),
            targetArtists = listOf(LocalAudioProvider.normalizeToken("The Weeknd")),
            targetDurationSec = 200,
            candidateTitle = LocalAudioProvider.normalizeToken("Classical Piano Sonata No. 14"),
            candidateArtists = listOf(LocalAudioProvider.normalizeToken("Beethoven")),
            candidateDurationSec = 420,
        )
        assertTrue("Expected low score for unrelated track, got $mismatchScore", mismatchScore < 40)

        val provider = LicensedAudioProvider()
        val audiusCandidates = listOf(
            AudiusTrackDto(
                id = "audius_999",
                title = "Unrelated Song",
                duration = 320,
                isStreamable = true,
                user = AudiusUserDto(name = "Other Artist"),
            ),
            AudiusTrackDto(
                id = "audius_101",
                title = "Night Owl",
                duration = 194,
                isStreamable = true,
                user = AudiusUserDto(name = "Broke For Free", handle = "brokeforfree"),
            ),
        )

        val matched = provider.findBestAuthorizedTrack(
            tracks = audiusCandidates,
            targetTitle = LocalAudioProvider.normalizeToken("Night Owl"),
            targetArtists = listOf(LocalAudioProvider.normalizeToken("Broke For Free")),
            targetDurationSec = 194,
            baseUrl = "https://api.audius.co",
        )
        assertNotNull(matched)
        assertEquals(
            "https://api.audius.co/v1/tracks/audius_101/stream?app_name=asl_beats_android",
            matched?.uri,
        )
    }

    @Test
    fun `test strict compliance disables YouTube audio extraction`() = runBlocking {
        val result = YTPlayerUtils.playerResponseForPlayback(videoId = "dQw4w9WgXcQ")
        assertTrue("Expected YouTube audio extraction to be disabled", result.isFailure)
        val exception = result.exceptionOrNull()
        assertNotNull(exception)
        assertTrue(
            "Expected 'Audio source unavailable' message, got: ${exception?.message}",
            exception?.message?.contains("Audio source unavailable") == true,
        )
    }

    @Test
    fun `test AudioResolutionQuery and SQL compliance triggers`() {
        val metadata = MediaMetadata(
            id = "track_sql_1",
            title = "Solar Flare",
            artists = listOf(MediaMetadata.Artist(id = "art_1", name = "Starlight")),
            duration = 215,
            thumbnailUrl = "https://img.example/solar.jpg",
        )
        val query = com.mochyy.mochymusic.playback.audio.AudioResolutionQuery.fromMediaMetadata(metadata)
        assertEquals("track_sql_1", query.songId)
        assertEquals("Solar Flare", query.title)
        assertEquals(listOf("Starlight"), query.artists)
        assertEquals(215, query.durationSeconds)

        val rules = com.mochyy.mochymusic.db.AuthorizedAudioDatabase.SQL_COMPLIANCE_RULES
        assertEquals(2, rules.size)
        assertTrue(rules.all { it.contains("googlevideo.com") && it.contains("RAISE(ABORT") })

        val domainTrack = com.mochyy.mochymusic.domain.audio.TrackMetadataDescriptor.fromAppMediaMetadata(metadata)
        assertEquals("track_sql_1", domainTrack.trackId)
        assertEquals("Solar Flare", domainTrack.title)
        assertEquals("Starlight", domainTrack.artist)
        assertEquals(215, domainTrack.durationSeconds)

        val license = com.mochyy.mochymusic.domain.audio.AudioAuthorizationLicense(
            sourceType = com.mochyy.mochymusic.playback.audio.AuthorizedSourceType.LICENSED_CREATIVE_COMMONS,
            providerId = "audius_licensed_cc",
            providerName = "Audius Authorized Catalog",
            licenseName = "Creative Commons BY 4.0",
            isOfflinePlayable = false,
            verifiedAtEpochMs = 1700000000000L,
        )
        val authorizedStream = com.mochyy.mochymusic.domain.audio.AuthorizedMediaStream(
            trackId = domainTrack.trackId,
            streamUri = "https://api.audius.co/v1/tracks/solar1/stream",
            title = domainTrack.title,
            artist = domainTrack.artist,
            durationSeconds = domainTrack.durationSeconds,
            mimeType = "audio/mpeg",
            artworkUrl = domainTrack.thumbnailUrl,
            authorization = license,
        )
        val resultDto = authorizedStream.toAuthorizedStreamResult()
        assertEquals("https://api.audius.co/v1/tracks/solar1/stream", resultDto.uri)
        assertEquals("Audius Authorized Catalog", resultDto.providerLabel)
        assertEquals("Creative Commons BY 4.0", resultDto.licenseLabel)
    }
}

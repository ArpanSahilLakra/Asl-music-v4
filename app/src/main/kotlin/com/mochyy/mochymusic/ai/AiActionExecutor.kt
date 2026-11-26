package com.mochyy.mochymusic.ai

import android.util.Log
import com.mochyy.mochymusic.db.MusicDatabase
import com.mochyy.mochymusic.db.entities.PlaylistEntity
import com.mochyy.mochymusic.db.entities.PlaylistSongMap
import com.mochyy.mochymusic.eq.AudioFxService
import com.mochyy.mochymusic.eq.EqualizerService
import com.mochyy.mochymusic.eq.audio.LoudnessParams
import com.mochyy.mochymusic.eq.audio.SpatialMode
import com.mochyy.mochymusic.eq.audio.SpatialParams
import com.mochyy.mochymusic.eq.data.FilterType
import com.mochyy.mochymusic.eq.data.ParametricEQ
import com.mochyy.mochymusic.eq.data.ParametricEQBand
import com.mochyy.mochymusic.extensions.toMediaItem
import com.mochyy.mochymusic.models.toMediaMetadata
import com.mochyy.mochymusic.playback.PlayerConnection
import com.mochyy.mochymusic.playback.queues.ListQueue
import com.music.innertube.YouTube
import com.music.innertube.models.SongItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AiActionExecutor"

data class AiActionExecutionResult(
    val executed: Boolean,
    val summary: String,
    val navigateRoute: String? = null,
)

/**
 * Executes real in-app music actions (Media3 playback/queue, Parametric EQ, Spatial Audio,
 * Karaoke vocal attenuation, library favorites, and saved playlists) requested by the AI Assistant
 * or Lyrics Studio.
 */
@Singleton
class AiActionExecutor @Inject constructor(
    private val database: MusicDatabase,
    private val equalizerService: EqualizerService,
    private val audioFxService: AudioFxService,
) {
    @Volatile
    var isKaraokeVocalCutEnabled: Boolean = false
        private set

    fun applyBassBoost(intensity: String = "medium"): String {
        val boost = when (intensity.lowercase()) {
            "subtle" -> 3.0
            "strong" -> 7.0
            else -> 5.0
        }
        val eq = ParametricEQ(
            preamp = -(boost * 0.45),
            bands = listOf(
                ParametricEQBand(frequency = 55.0, gain = boost, q = 0.9, filterType = FilterType.LSC),
                ParametricEQBand(frequency = 110.0, gain = boost * 0.72, q = 1.1, filterType = FilterType.PK),
                ParametricEQBand(frequency = 220.0, gain = boost * 0.35, q = 1.2, filterType = FilterType.PK),
                ParametricEQBand(frequency = 3500.0, gain = 1.0, q = 1.2, filterType = FilterType.PK),
            ),
            metadata = mapOf("source" to "ASL Beats AI", "preset" to "Bass Boost ($intensity)")
        )
        equalizerService.apply(eq)
        isKaraokeVocalCutEnabled = false
        audioFxService.loudness = LoudnessParams(enabled = true, boostDb = 2.5f, compression = 0.25f)
        return "Bass Boost ($intensity) applied live"
    }

    fun setKaraokeVocalAttenuation(enabled: Boolean): String {
        isKaraokeVocalCutEnabled = enabled
        if (enabled) {
            // Attenuates the core human vocal formant region (320 Hz - 3.6 kHz) while keeping
            // sub-bass warmth and hi-hat shimmer intact for sing-along karaoke practice.
            val karaokeEq = ParametricEQ(
                preamp = 1.5,
                bands = listOf(
                    ParametricEQBand(frequency = 80.0, gain = 2.5, q = 0.9, filterType = FilterType.LSC),
                    ParametricEQBand(frequency = 450.0, gain = -7.5, q = 1.0, filterType = FilterType.PK),
                    ParametricEQBand(frequency = 1200.0, gain = -11.0, q = 0.85, filterType = FilterType.PK),
                    ParametricEQBand(frequency = 2800.0, gain = -9.5, q = 1.0, filterType = FilterType.PK),
                    ParametricEQBand(frequency = 9500.0, gain = 2.5, q = 0.9, filterType = FilterType.HSC),
                ),
                metadata = mapOf("source" to "ASL Beats Lyrics Studio", "preset" to "Karaoke Vocal Cut")
            )
            equalizerService.apply(karaokeEq)
            return "Karaoke vocal attenuation enabled"
        } else {
            equalizerService.disable()
            return "Karaoke vocal attenuation disabled"
        }
    }

    fun setSpatialAudio(enabled: Boolean, intensity: String = "medium"): String {
        val amount = when (intensity.lowercase()) {
            "subtle" -> 0.55f
            "strong" -> 1.0f
            else -> 0.85f
        }
        audioFxService.spatial = if (enabled) {
            SpatialParams(
                enabled = true,
                mode = SpatialMode.Speakers,
                speakerAngle = 35f,
                amount = amount,
            )
        } else {
            SpatialParams(enabled = false)
        }
        return if (enabled) "3D Spatial Audio enabled ($intensity)" else "Spatial Audio disabled"
    }

    suspend fun execute(
        action: AiAssistantAction,
        playerConnection: PlayerConnection?,
    ): AiActionExecutionResult {
        return when (action.type) {
            AiActionType.NONE -> AiActionExecutionResult(false, "")

            AiActionType.PAUSE -> {
                if (playerConnection != null) {
                    withContext(Dispatchers.Main) { playerConnection.pause() }
                    AiActionExecutionResult(true, "Playback paused")
                } else {
                    AiActionExecutionResult(false, "Player is not ready")
                }
            }

            AiActionType.RESUME -> {
                if (playerConnection != null) {
                    withContext(Dispatchers.Main) { playerConnection.play() }
                    AiActionExecutionResult(true, "Playback resumed")
                } else {
                    AiActionExecutionResult(false, "Player is not ready")
                }
            }

            AiActionType.NEXT_TRACK -> {
                if (playerConnection != null) {
                    withContext(Dispatchers.Main) { playerConnection.seekToNext() }
                    AiActionExecutionResult(true, "Skipped to next track")
                } else {
                    AiActionExecutionResult(false, "Player is not ready")
                }
            }

            AiActionType.PREVIOUS_TRACK -> {
                if (playerConnection != null) {
                    withContext(Dispatchers.Main) { playerConnection.seekToPrevious() }
                    AiActionExecutionResult(true, "Returned to previous track")
                } else {
                    AiActionExecutionResult(false, "Player is not ready")
                }
            }

            AiActionType.BOOST_BASS -> {
                AiActionExecutionResult(true, applyBassBoost(action.intensity))
            }

            AiActionType.RESET_EQ -> {
                equalizerService.disable()
                isKaraokeVocalCutEnabled = false
                audioFxService.loudness = LoudnessParams(enabled = false)
                AiActionExecutionResult(true, "Equalizer reset to flat")
            }

            AiActionType.ENABLE_SPATIAL -> {
                AiActionExecutionResult(true, setSpatialAudio(true, action.intensity))
            }

            AiActionType.DISABLE_SPATIAL -> {
                AiActionExecutionResult(true, setSpatialAudio(false))
            }

            AiActionType.ENABLE_KARAOKE -> {
                AiActionExecutionResult(true, setKaraokeVocalAttenuation(true))
            }

            AiActionType.DISABLE_KARAOKE -> {
                AiActionExecutionResult(true, setKaraokeVocalAttenuation(false))
            }

            AiActionType.TOGGLE_FAVORITE -> {
                if (playerConnection != null) {
                    withContext(Dispatchers.Main) { playerConnection.toggleLike() }
                    AiActionExecutionResult(true, "Updated favorite status")
                } else {
                    AiActionExecutionResult(false, "No active track to favorite")
                }
            }

            AiActionType.PLAY_QUERY -> {
                playByQuery(action.query, playerConnection)
            }

            AiActionType.OPEN_MOOD_PLAYLIST -> {
                val encoded = java.net.URLEncoder.encode(action.query.ifBlank { "relaxing" }, "UTF-8")
                AiActionExecutionResult(
                    executed = true,
                    summary = "Ready to generate mood playlist: ${action.query.ifBlank { "Custom Mood" }}",
                    navigateRoute = "ai_mood_playlist?seed=$encoded",
                )
            }

            AiActionType.EXPLAIN_CURRENT_SONG -> {
                val current = playerConnection?.mediaMetadata?.value
                if (current != null) {
                    AiActionExecutionResult(
                        executed = true,
                        summary = "Analyzing \"${current.title}\"",
                    )
                } else {
                    AiActionExecutionResult(false, "Play a song first so I can analyze it")
                }
            }
        }
    }

    private suspend fun playByQuery(
        query: String,
        playerConnection: PlayerConnection?,
    ): AiActionExecutionResult {
        if (query.isBlank() || playerConnection == null) {
            return AiActionExecutionResult(false, "Couldn't start playback without a query")
        }
        return try {
            // 1. Check user's local/Room library first for instant offline-capable matching
            val localMatches = withContext(Dispatchers.IO) {
                val all = database.allSongs().first()
                val tokens = query.lowercase().split(" ").filter { it.length >= 2 }
                all.filter { song ->
                    val haystack = "${song.song.title} ${song.artists.joinToString(" ") { it.name }}".lowercase()
                    tokens.isNotEmpty() && tokens.all { haystack.contains(it) }
                }.take(8)
            }
            if (localMatches.isNotEmpty()) {
                val mediaItems = localMatches.map { it.toMediaItem() }
                withContext(Dispatchers.Main) {
                    playerConnection.playQueue(ListQueue(title = "AI Mix: $query", items = mediaItems))
                }
                return AiActionExecutionResult(
                    executed = true,
                    summary = "Playing \"${localMatches.first().song.title}\" from your library"
                )
            }

            // 2. Fall back to real YouTube Music search discovery
            val remoteMatches = withContext(Dispatchers.IO) {
                YouTube.search(query, YouTube.SearchFilter.FILTER_SONG)
                    .getOrNull()
                    ?.items
                    ?.filterIsInstance<SongItem>()
                    ?.take(8)
                    .orEmpty()
            }
            if (remoteMatches.isNotEmpty()) {
                val mediaItems = remoteMatches.map { it.toMediaItem() }
                withContext(Dispatchers.Main) {
                    playerConnection.playQueue(ListQueue(title = "AI Mix: $query", items = mediaItems))
                }
                return AiActionExecutionResult(
                    executed = true,
                    summary = "Queued ${remoteMatches.size} tracks starting with \"${remoteMatches.first().title}\""
                )
            }

            AiActionExecutionResult(false, "No matching songs found for \"$query\"")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play query '$query'", e)
            AiActionExecutionResult(false, "Playback search error: ${e.localizedMessage ?: "try again"}")
        }
    }

    /**
     * Resolves Gemini track suggestions against the local library and YouTube search, then saves
     * a real playable Room playlist and returns the saved playlist ID + matched MediaItems.
     */
    suspend fun resolveAndSaveMoodPlaylist(
        plan: GeneratedPlaylistPlan,
    ): Pair<String, List<SongItem>> = withContext(Dispatchers.IO) {
        val resolvedSongs = mutableListOf<SongItem>()
        val seenIds = HashSet<String>()
        val localSongs = runCatching { database.allSongs().first() }.getOrDefault(emptyList())

        for (suggestion in plan.tracks) {
            val normalizedTitle = suggestion.title.lowercase()
            val normalizedArtist = suggestion.artist.lowercase()

            // Try matching an existing song in user's library first
            val localHit = localSongs.firstOrNull { song ->
                song.song.title.lowercase().contains(normalizedTitle) &&
                    song.artists.any { it.name.lowercase().contains(normalizedArtist) }
            }
            if (localHit != null && seenIds.add(localHit.id)) {
                resolvedSongs.add(
                    SongItem(
                        id = localHit.id,
                        title = localHit.song.title,
                        artists = localHit.artists.map {
                            com.music.innertube.models.Artist(name = it.name, id = it.id)
                        },
                        album = localHit.album?.let {
                            com.music.innertube.models.Album(name = it.title, id = it.id)
                        },
                        duration = localHit.song.duration,
                        thumbnail = localHit.song.thumbnailUrl.orEmpty(),
                        explicit = localHit.song.explicit,
                    )
                )
                continue
            }

            // Otherwise resolve via YouTube Music search
            val remoteHit = runCatching {
                YouTube.search("${suggestion.title} ${suggestion.artist}", YouTube.SearchFilter.FILTER_SONG)
                    .getOrNull()
                    ?.items
                    ?.filterIsInstance<SongItem>()
                    ?.firstOrNull()
            }.getOrNull()

            if (remoteHit != null && seenIds.add(remoteHit.id)) {
                resolvedSongs.add(remoteHit)
            }
        }

        val playlistEntity = PlaylistEntity(
            name = plan.title,
            description = "${plan.description} (${plan.targetBpmRange})",
            isEditable = true,
            isLocal = true,
            thumbnailUrl = resolvedSongs.firstOrNull()?.thumbnail,
        )

        database.withTransaction {
            insert(playlistEntity)
            resolvedSongs.forEachIndexed { index, songItem ->
                insert(songItem.toMediaMetadata())
                insert(
                    PlaylistSongMap(
                        songId = songItem.id,
                        playlistId = playlistEntity.id,
                        position = index,
                    )
                )
            }
        }

        playlistEntity.id to resolvedSongs
    }

    /**
     * Resolves arbitrary (title, artist) pairs against the user's local Room library first and
     * YouTube Music search second, returning playable SongItems in order without duplicates.
     */
    suspend fun resolveTrackQueries(
        queries: List<Pair<String, String>>,
    ): List<SongItem> = withContext(Dispatchers.IO) {
        val resolved = mutableListOf<SongItem>()
        val seenIds = HashSet<String>()
        val localSongs = runCatching { database.allSongs().first() }.getOrDefault(emptyList())

        for ((title, artist) in queries) {
            val normTitle = title.lowercase().trim()
            val normArtist = artist.lowercase().trim()
            val localHit = localSongs.firstOrNull { song ->
                song.song.title.lowercase().contains(normTitle) &&
                    (normArtist.isEmpty() || normArtist == "unknown artist" ||
                        song.artists.any { it.name.lowercase().contains(normArtist) })
            }
            if (localHit != null && seenIds.add(localHit.id)) {
                resolved.add(
                    SongItem(
                        id = localHit.id,
                        title = localHit.song.title,
                        artists = localHit.artists.map {
                            com.music.innertube.models.Artist(name = it.name, id = it.id)
                        },
                        album = localHit.album?.let {
                            com.music.innertube.models.Album(name = it.title, id = it.id)
                        },
                        duration = localHit.song.duration,
                        thumbnail = localHit.song.thumbnailUrl.orEmpty(),
                        explicit = localHit.song.explicit,
                    )
                )
                continue
            }

            val queryString = if (normArtist.isEmpty() || normArtist == "unknown artist") {
                title
            } else {
                "$title $artist"
            }
            val remoteHit = runCatching {
                YouTube.search(queryString, YouTube.SearchFilter.FILTER_SONG)
                    .getOrNull()
                    ?.items
                    ?.filterIsInstance<SongItem>()
                    ?.firstOrNull()
            }.getOrNull()

            if (remoteHit != null && seenIds.add(remoteHit.id)) {
                resolved.add(remoteHit)
            }
        }
        resolved
    }
}

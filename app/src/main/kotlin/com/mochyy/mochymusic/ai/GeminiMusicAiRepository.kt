package com.mochyy.mochymusic.ai

import android.graphics.Bitmap
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "GeminiMusicAiRepo"
private const val DEFAULT_MODEL = "gemini-3.8-flash"
private const val FAST_MODEL = "gemini-3.5-flash-lite"
private const val AI_TIMEOUT_MS = 60_000L

@Singleton
class GeminiMusicAiRepository @Inject constructor() {

    private val chatModel by lazy {
        Firebase.ai.generativeModel(
            modelName = DEFAULT_MODEL,
            systemInstruction = content {
                text(
                    """
                    You are ASL Beats AI, an expert music companion, audio engineer, and DJ built directly into the ASL Beats Android music player.
                    Keep answers concise, warm, musically insightful, and directly helpful.
                    When the user asks to play a song, adjust bass/EQ, toggle spatial audio, enable karaoke vocal cut, like a song, or create a playlist, acknowledge the action naturally because the app executes it alongside your reply.
                    Copyright policy: NEVER reproduce full copyrighted song lyrics. You may discuss themes, meanings, short brief phrases for critique, musical structure, and cultural context.
                    """.trimIndent()
                )
            }
        )
    }

    private val actionExtractionSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "action" to Schema.enumeration(
                    values = AiActionType.entries.map { it.name },
                    description = "The single concrete playback, EQ, or playlist command requested by the user, or NONE if purely conversational"
                ),
                "query" to Schema.string(
                    description = "Song title, artist, genre, or mood query when action is PLAY_QUERY or OPEN_MOOD_PLAYLIST"
                ),
                "intensity" to Schema.enumeration(
                    values = listOf("subtle", "medium", "strong"),
                    description = "Strength for audio adjustments like BOOST_BASS or ENABLE_SPATIAL"
                ),
                "note" to Schema.string(
                    description = "Short 4-8 word UI label describing the executed command"
                ),
            ),
            optionalProperties = listOf("query", "intensity", "note")
        )
    }

    private val actionModel by lazy {
        Firebase.ai.generativeModel(
            modelName = FAST_MODEL,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = actionExtractionSchema
                temperature = 0.1f
            }
        )
    }

    private val playlistSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "title" to Schema.string(description = "Evocative short playlist title (2-5 words)"),
                "description" to Schema.string(description = "One-sentence mood and sonic vibe description"),
                "moodLabel" to Schema.string(description = "Primary mood label"),
                "targetBpmRange" to Schema.string(description = "Estimated BPM range, e.g. '92-110 BPM'"),
                "tracks" to Schema.array(
                    description = "8 to 12 real, well-known songs matching the mood and listening context",
                    items = Schema.obj(
                        properties = mapOf(
                            "title" to Schema.string(description = "Exact song title"),
                            "artist" to Schema.string(description = "Primary artist name"),
                            "reason" to Schema.string(description = "Short 5-10 word musical reason it fits")
                        )
                    )
                )
            )
        )
    }

    private val playlistModel by lazy {
        Firebase.ai.generativeModel(
            modelName = DEFAULT_MODEL,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = playlistSchema
                temperature = 0.7f
            }
        )
    }

    private val songExplanationSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "summary" to Schema.string(description = "2-3 sentence overview of the song's core message and sound"),
                "moodAndVibe" to Schema.string(description = "Emotional tone, sonic texture, and energy profile"),
                "themes" to Schema.array(
                    items = Schema.string(),
                    description = "3 to 5 concise thematic tags"
                ),
                "storyInterpretation" to Schema.string(description = "Rich interpretation of the story/imagery without quoting full copyrighted lyrics"),
                "artistAndEraContext" to Schema.string(description = "Artist background, musical style, and release/cultural context"),
                "languageNote" to Schema.string(description = "Translation or cultural nuance note if requested or non-English, otherwise a brief poetic style note"),
                "listeningTip" to Schema.string(description = "Recommended EQ, spatial audio, or moment to enjoy this track")
            )
        )
    }

    private val explanationModel by lazy {
        Firebase.ai.generativeModel(
            modelName = DEFAULT_MODEL,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = songExplanationSchema
                temperature = 0.45f
            }
        )
    }

    /**
     * Streams an AI Music Assistant response token-by-token using Firebase AI Logic.
     */
    fun streamAssistantReply(
        userMessage: String,
        history: List<Pair<String, String>>,
        nowPlayingContext: String,
        topLibraryContext: String,
        executedActionSummary: String?,
    ): Flow<String> = flow {
        val chatHistory: List<Content> = history.takeLast(12).map { (role, text) ->
            val modelRole = if (role == "assistant") "model" else "user"
            content(modelRole) { text(text) }
        }
        val contextualPrompt = buildString {
            appendLine("[Player Context]")
            appendLine("Now playing: $nowPlayingContext")
            if (topLibraryContext.isNotBlank()) {
                appendLine("User favorite/recent tracks: $topLibraryContext")
            }
            if (!executedActionSummary.isNullOrBlank()) {
                appendLine("App action just executed for this request: $executedActionSummary")
            }
            appendLine()
            append("User request: ")
            append(userMessage)
        }

        try {
            withTimeout(AI_TIMEOUT_MS) {
                val chat = chatModel.startChat(history = chatHistory)
                val accumulated = StringBuilder()
                chat.sendMessageStream(contextualPrompt).collect { chunk ->
                    val delta = chunk.text.orEmpty()
                    if (delta.isNotEmpty()) {
                        accumulated.append(delta)
                        emit(accumulated.toString())
                    }
                }
                if (accumulated.isBlank()) {
                    throw IllegalStateException("AI returned an empty response.")
                }
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Assistant streaming timed out", e)
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Assistant streaming failed", e)
            throw e
        }
    }

    /**
     * Extracts a structured player/DSP/playlist command from a natural-language user prompt.
     */
    suspend fun extractAssistantAction(
        userMessage: String,
        nowPlayingTitle: String?,
    ): AiAssistantAction {
        val prompt = buildString {
            appendLine("Classify whether this music app command should execute an immediate in-app action.")
            appendLine("Currently playing track: ${nowPlayingTitle ?: "Nothing playing"}")
            appendLine("Examples:")
            appendLine("- 'Play something relaxing for night driving' -> PLAY_QUERY, query='relaxing night drive music'")
            appendLine("- 'Increase bass' / 'boost bass' -> BOOST_BASS")
            appendLine("- 'Turn on 3D spatial audio' -> ENABLE_SPATIAL")
            appendLine("- 'Enable karaoke mode' / 'reduce vocals' -> ENABLE_KARAOKE")
            appendLine("- 'Like this song' / 'favorite this' -> TOGGLE_FAVORITE")
            appendLine("- 'Explain this song' / 'what does this track mean' -> EXPLAIN_CURRENT_SONG")
            appendLine("- 'Skip to next song' -> NEXT_TRACK")
            appendLine("- 'Make a workout playlist' -> OPEN_MOOD_PLAYLIST, query='workout'")
            appendLine("- General music questions -> NONE")
            append("User message: ")
            append(userMessage)
        }
        return try {
            val response = withTimeout(AI_TIMEOUT_MS) {
                actionModel.generateContent(prompt)
            }
            val raw = response.text ?: return AiAssistantAction()
            AiJsonParsers.parseActionJson(raw)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Action extraction failed; continuing with conversational reply", e)
            AiAssistantAction()
        }
    }

    /**
     * Generates a structured mood-based playlist plan grounded in the user's mood and listening taste.
     */
    suspend fun generateMoodPlaylist(
        moodLabel: String,
        customPrompt: String,
        listeningHistorySummary: String,
        trackCount: Int = 10,
    ): GeneratedPlaylistPlan {
        val prompt = buildString {
            appendLine("Create a curated music playlist for the ASL Beats player.")
            appendLine("Mood: $moodLabel")
            if (customPrompt.isNotBlank()) {
                appendLine("User specific vibe/request: $customPrompt")
            }
            if (listeningHistorySummary.isNotBlank()) {
                appendLine("User's recent & favorite tracks for taste alignment (blend familiar style with fresh discoveries): $listeningHistorySummary")
            }
            appendLine("Return exactly $trackCount real, widely available songs with accurate artist names and crisp sonic flow.")
        }
        return try {
            val response = withTimeout(AI_TIMEOUT_MS) {
                playlistModel.generateContent(prompt)
            }
            val raw = response.text ?: throw IllegalStateException("AI returned an empty playlist.")
            val plan = AiJsonParsers.parsePlaylistPlanJson(raw, moodLabel)
            if (plan.tracks.isEmpty()) {
                throw IllegalStateException("No tracks were returned for this mood.")
            }
            plan
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Mood playlist generation timed out", e)
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Mood playlist generation failed", e)
            throw e
        }
    }

    /**
     * Produces a copyright-safe musical & thematic analysis of a song (never dumps full lyrics).
     */
    suspend fun explainSong(
        songTitle: String,
        artistName: String,
        albumName: String?,
        targetLanguage: String = "English",
        userSnippetContext: String? = null,
    ): SongExplanation {
        val prompt = buildString {
            appendLine("Analyze this song for a listener in the ASL Beats music app.")
            appendLine("Song: $songTitle")
            appendLine("Artist: $artistName")
            if (!albumName.isNullOrBlank()) {
                appendLine("Album: $albumName")
            }
            appendLine("Respond in language: $targetLanguage")
            if (!userSnippetContext.isNullOrBlank()) {
                appendLine("Listener highlighted short snippet for interpretation/translation: \"$userSnippetContext\"")
            }
            appendLine("IMPORTANT: Do NOT reproduce full copyrighted lyrics. Focus on original analysis: meaning, themes, mood, story interpretation, artist/era context, cultural/translation nuance, and an audio/listening tip.")
        }
        return try {
            val response = withTimeout(AI_TIMEOUT_MS) {
                explanationModel.generateContent(prompt)
            }
            val raw = response.text ?: throw IllegalStateException("AI returned an empty explanation.")
            AiJsonParsers.parseSongExplanationJson(raw, songTitle, artistName)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Song explanation timed out", e)
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Song explanation failed", e)
            throw e
        }
    }

    private val masteringSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "presetName" to Schema.string(description = "Short expressive mastering profile title (2-4 words)"),
                "summary" to Schema.string(description = "One-sentence explanation of how this curve elevates the sound"),
                "preampDb" to Schema.double(description = "Safe headroom preamp gain between -5.0 and 0.0 dB"),
                "bands" to Schema.array(
                    description = "Exactly 6 parametric EQ bands spanning 55Hz, 160Hz, 450Hz, 1500Hz, 4500Hz, and 11000Hz",
                    items = Schema.obj(
                        properties = mapOf(
                            "frequencyHz" to Schema.double(description = "Center frequency in Hz"),
                            "gainDb" to Schema.double(description = "Gain in dB between -8.0 and +8.0"),
                            "q" to Schema.double(description = "Filter Q factor between 0.7 and 2.5"),
                            "label" to Schema.string(description = "Short band label such as Sub Bass, Warmth, Vocals, Air")
                        )
                    )
                ),
                "spatialEnabled" to Schema.boolean(description = "Whether virtual stereo spatialization enhances this track"),
                "spatialAmount" to Schema.double(description = "Spatial depth between 0.3 and 0.95"),
                "reverbEnabled" to Schema.boolean(description = "Whether subtle room ambience improves immersion"),
                "reverbMix" to Schema.double(description = "Reverb wet/dry mix between 0.1 and 0.45"),
                "loudnessBoostDb" to Schema.double(description = "Dynamic loudness boost between 0.0 and 4.5 dB"),
            )
        )
    }

    private val masteringModel by lazy {
        Firebase.ai.generativeModel(
            modelName = DEFAULT_MODEL,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = masteringSchema
                temperature = 0.4f
            }
        )
    }

    private val listeningDnaSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "archetypeTitle" to Schema.string(description = "Creative 2-4 word listener persona title"),
                "sonicTagline" to Schema.string(description = "Punchy one-line musical identity summary"),
                "vibeSummary" to Schema.string(description = "2-3 sentence personalized analysis of the listener's taste and emotional range"),
                "topMoodTags" to Schema.array(
                    items = Schema.string(),
                    description = "4 to 6 short mood/texture tags reflecting their library"
                ),
                "audioCoachingTip" to Schema.string(description = "Actionable EQ, spatial audio, or visualizer tip tailored to their favorite music"),
                "recommendedMoodSeed" to Schema.string(description = "One mood ID or keyword to seed a fresh discovery playlist"),
                "discoveryPicks" to Schema.array(
                    description = "5 fresh track recommendations matching their Sonic DNA",
                    items = Schema.obj(
                        properties = mapOf(
                            "title" to Schema.string(description = "Song title"),
                            "artist" to Schema.string(description = "Artist name"),
                            "reason" to Schema.string(description = "Short 6-12 word reason it complements their taste")
                        )
                    )
                )
            )
        )
    }

    private val listeningDnaModel by lazy {
        Firebase.ai.generativeModel(
            modelName = DEFAULT_MODEL,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = listeningDnaSchema
                temperature = 0.65f
            }
        )
    }

    suspend fun generateMasteringRecipe(
        songTitle: String?,
        artistName: String?,
        outputProfile: AudioOutputProfile,
        customGoal: String,
    ): AiMasteringRecipe {
        val prompt = buildString {
            appendLine("Design a studio-grade 6-band Parametric EQ + DSP mastering chain for ASL Beats.")
            appendLine("Target output hardware: ${outputProfile.label} (${outputProfile.subtitle})")
            if (!songTitle.isNullOrBlank()) {
                appendLine("Current song: \"$songTitle\" by ${artistName ?: "Unknown Artist"}")
            }
            if (customGoal.isNotBlank()) {
                appendLine("Listener custom sound goal: $customGoal")
            }
            appendLine("Keep preamp headroom safe so peak limiting stays transparent.")
        }
        return try {
            val response = withTimeout(AI_TIMEOUT_MS) {
                masteringModel.generateContent(prompt)
            }
            val raw = response.text ?: throw IllegalStateException("AI returned an empty mastering profile.")
            val recipe = AiPhase2JsonParsers.parseMasteringRecipeJson(raw)
            if (recipe.bands.isEmpty()) {
                throw IllegalStateException("No EQ bands were generated.")
            }
            recipe
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "AI mastering timed out", e)
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "AI mastering failed", e)
            throw e
        }
    }

    suspend fun generateListeningDna(
        topSongsSummary: String,
        topArtistsSummary: String,
        totalTracks: Int,
        likedTracks: Int,
        totalMinutes: Long,
    ): ListeningDnaReport {
        val prompt = buildString {
            appendLine("Create a personalized Sonic DNA & Listening Coach report for an ASL Beats listener.")
            appendLine("Library stats: $totalTracks songs, $likedTracks liked, $totalMinutes minutes listened.")
            appendLine("Top played tracks: ${topSongsSummary.ifBlank { "Fresh listener exploring new genres" }}")
            appendLine("Top artists: ${topArtistsSummary.ifBlank { "Open-minded multi-genre taste" }}")
        }
        return try {
            val response = withTimeout(AI_TIMEOUT_MS) {
                listeningDnaModel.generateContent(prompt)
            }
            val raw = response.text ?: throw IllegalStateException("AI returned an empty Sonic DNA report.")
            AiPhase2JsonParsers.parseListeningDnaJson(raw)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Listening DNA timed out", e)
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Listening DNA failed", e)
            throw e
        }
    }

    private val djSetSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "setTitle" to Schema.string(description = "Creative 2-5 word DJ set title"),
                "bpmArcSummary" to Schema.string(description = "BPM progression summary, e.g. '118 -> 126 BPM'"),
                "keyFlowSummary" to Schema.string(description = "Harmonic Camelot key progression, e.g. '8A -> 9A -> 9B -> 10B'"),
                "recommendedTransitionStyle" to Schema.string(description = "Recommended CrossMix style such as Silk Blend, Bass Swap, Harmonic Glide, or Drop Cut"),
                "optionalHostIntro" to Schema.string(description = "Short 1-sentence natural radio/DJ intro (under 20 words)"),
                "tracks" to Schema.array(
                    description = "8 seamlessly ordered real songs with matching BPM and harmonic key progression",
                    items = Schema.obj(
                        properties = mapOf(
                            "title" to Schema.string(description = "Exact song title"),
                            "artist" to Schema.string(description = "Primary artist name"),
                            "estimatedBpm" to Schema.integer(description = "Estimated tempo in BPM (60-185)"),
                            "camelotKey" to Schema.string(description = "Camelot harmonic key notation like 8A, 9A, 10B"),
                            "energyPercent" to Schema.integer(description = "Track energy score from 15 to 100"),
                            "transitionNote" to Schema.string(description = "Short 4-8 word DJ mixing cue into this track")
                        )
                    )
                )
            )
        )
    }

    private val djSetModel by lazy {
        Firebase.ai.generativeModel(
            modelName = DEFAULT_MODEL,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = djSetSchema
                temperature = 0.65f
            }
        )
    }

    private val screenshotScanSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "sourceSummary" to Schema.string(description = "Brief 4-10 word description of what music info was seen in the image"),
                "tracks" to Schema.array(
                    description = "All identified songs visible or recognizable from titles, album covers, lyrics snippets, or music player UI",
                    items = Schema.obj(
                        properties = mapOf(
                            "title" to Schema.string(description = "Identified song title"),
                            "artist" to Schema.string(description = "Identified primary artist"),
                            "album" to Schema.string(description = "Album name if known or visible, otherwise empty string"),
                            "confidencePercent" to Schema.integer(description = "Match confidence 1-100"),
                            "detectedCue" to Schema.string(description = "What visual clue matched, e.g. 'Player UI title', 'Album cover art', 'Lyric quote'")
                        )
                    )
                )
            )
        )
    }

    private val screenshotVisionModel by lazy {
        Firebase.ai.generativeModel(
            modelName = DEFAULT_MODEL,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = screenshotScanSchema
                temperature = 0.25f
            }
        )
    }

    suspend fun generateDjSetPlan(
        persona: DjPersona,
        currentTrackTitle: String?,
        currentArtist: String?,
        libraryContext: String,
        customVibe: String,
    ): DjSetPlan {
        val prompt = buildString {
            appendLine("Build an 8-track harmonic, beat-matched DJ mix set for ASL Beats.")
            appendLine("DJ Persona: ${persona.title} — ${persona.subtitle}")
            appendLine("Target BPM window: ${persona.defaultBpmRange}; preferred CrossMix transition: ${persona.preferredCrossmixStyle}")
            if (!currentTrackTitle.isNullOrBlank()) {
                appendLine("Anchor / currently playing song to blend from: \"$currentTrackTitle\" by ${currentArtist ?: "Unknown"}")
            }
            if (libraryContext.isNotBlank()) {
                appendLine("Listener's taste cues: $libraryContext")
            }
            if (customVibe.isNotBlank()) {
                appendLine("Extra user request: $customVibe")
            }
            appendLine("Order the 8 tracks so BPM and Camelot keys flow smoothly with realistic energy progression.")
        }
        return try {
            val response = withTimeout(AI_TIMEOUT_MS) {
                djSetModel.generateContent(prompt)
            }
            val raw = response.text ?: throw IllegalStateException("AI DJ returned an empty set.")
            val plan = AiPhase3JsonParsers.parseDjSetPlanJson(raw, persona)
            if (plan.tracks.isEmpty()) {
                throw IllegalStateException("AI DJ could not assemble tracks for this session.")
            }
            plan
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "AI DJ set generation timed out", e)
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "AI DJ set generation failed", e)
            throw e
        }
    }

    suspend fun identifyMusicFromScreenshot(bitmap: Bitmap): ScreenshotScanResult {
        val prompt = """
            Inspect this screenshot or photo for music information: song titles, artist names, album covers, playlist lists, music app now-playing screens, or lyric cards.
            Identify every real song you can recognize with high accuracy. Do NOT invent tracks if none are visible; return an empty tracks array if no music information is present.
            Copyright rule: never transcribe full copyrighted song lyrics in your output.
        """.trimIndent()
        return try {
            val response = withTimeout(AI_TIMEOUT_MS) {
                screenshotVisionModel.generateContent(
                    content {
                        image(bitmap)
                        text(prompt)
                    }
                )
            }
            val raw = response.text ?: throw IllegalStateException("Vision scanner returned an empty response.")
            AiPhase3JsonParsers.parseScreenshotScanJson(raw)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Screenshot music scan timed out", e)
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Screenshot music scan failed", e)
            throw e
        }
    }

    private val humSearchSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "melodySummary" to Schema.string(description = "Short 6-14 word description of the hummed/sung melodic phrase and rhythm"),
                "estimatedTempoBpm" to Schema.integer(description = "Estimated tempo of the hummed melody in BPM"),
                "candidates" to Schema.array(
                    description = "Up to 5 most likely matching real songs ordered by melodic & rhythmic similarity",
                    items = Schema.obj(
                        properties = mapOf(
                            "title" to Schema.string(description = "Song title"),
                            "artist" to Schema.string(description = "Primary artist name"),
                            "album" to Schema.string(description = "Album title if known, else empty string"),
                            "confidencePercent" to Schema.integer(description = "Estimated confidence 1-100"),
                            "matchReason" to Schema.string(description = "Short explanation of why the melody/rhythm matches")
                        )
                    )
                )
            )
        )
    }

    private val humSearchModel by lazy {
        Firebase.ai.generativeModel(
            modelName = DEFAULT_MODEL,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = humSearchSchema
                temperature = 0.35f
            }
        )
    }

    suspend fun identifySongFromHumming(
        wavBytes: ByteArray,
        melodyContourNotes: String,
        optionalHint: String,
    ): HumSearchResult {
        val prompt = buildString {
            appendLine("Listen carefully to this short hummed or sung audio clip from a listener in ASL Beats.")
            if (melodyContourNotes.isNotBlank()) {
                appendLine("Extracted pitch contour sequence: $melodyContourNotes")
            }
            if (optionalHint.isNotBlank()) {
                appendLine("Listener optional hint (genre/era/mood/language): $optionalHint")
            }
            appendLine("Identify the top 3 to 5 most likely matching real songs based on the melody intervals, cadence, and any audible sung words.")
            appendLine("Be honest about confidencePercent if the recording is faint or ambiguous.")
        }
        return try {
            val response = withTimeout(AI_TIMEOUT_MS) {
                humSearchModel.generateContent(
                    content {
                        inlineData(wavBytes, "audio/wav")
                        text(prompt)
                    }
                )
            }
            val raw = response.text ?: throw IllegalStateException("Hum search returned an empty response.")
            AiPhase4JsonParsers.parseHumSearchJson(raw)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Hum search timed out", e)
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Hum search failed", e)
            throw e
        }
    }
}

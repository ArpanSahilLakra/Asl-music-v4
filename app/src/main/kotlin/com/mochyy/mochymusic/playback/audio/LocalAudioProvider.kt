package com.mochyy.mochymusic.playback.audio

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import com.mochyy.mochymusic.db.MusicDatabase
import com.mochyy.mochymusic.domain.audio.LocalAudioProviderContract
import com.mochyy.mochymusic.models.MediaMetadata
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class LocalAudioProvider @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
) : LocalAudioProviderContract {
    override val providerId: String = "local_audio"
    override val providerName: String = "Local Device Music"

    override suspend fun resolve(metadata: MediaMetadata): AudioSourceResult.AuthorizedStream? =
        withContext(Dispatchers.IO) {
            // 1. Direct local URI already attached to the track
            val directUri = metadata.localUri?.takeIf { it.isNotBlank() }
            if (directUri != null && isUriReadable(directUri)) {
                return@withContext AudioSourceResult.AuthorizedStream(
                    uri = directUri,
                    title = metadata.title,
                    artist = metadata.artists.joinToString(", ") { it.name }.ifBlank { "Local Artist" },
                    sourceType = AuthorizedSourceType.LOCAL_DEVICE,
                    providerLabel = providerName,
                    licenseLabel = "User Device Local Audio",
                    durationSeconds = metadata.duration.takeIf { it > 0 },
                )
            }

            // 2. Match against local songs stored in Room database by ID or normalized Title + Artist
            val localSongs = runCatching {
                database.localSongsByRowIdAsc().firstOrNull().orEmpty()
            }.getOrDefault(emptyList())

            val exactIdMatch = localSongs.firstOrNull {
                it.song.id == metadata.id && !it.song.localUri.isNullOrBlank()
            }
            if (exactIdMatch != null && isUriReadable(exactIdMatch.song.localUri!!)) {
                return@withContext AudioSourceResult.AuthorizedStream(
                    uri = exactIdMatch.song.localUri,
                    title = exactIdMatch.song.title,
                    artist = exactIdMatch.artists.joinToString(", ") { it.name }.ifBlank { "Local Artist" },
                    sourceType = AuthorizedSourceType.LOCAL_DEVICE,
                    providerLabel = providerName,
                    licenseLabel = "User Device Local Audio",
                    durationSeconds = exactIdMatch.song.duration.takeIf { it > 0 },
                )
            }

            val targetTitle = normalizeToken(metadata.title)
            val targetArtists = metadata.artists.map { normalizeToken(it.name) }.filter { it.isNotEmpty() }
            if (targetTitle.isEmpty()) return@withContext null

            val bestRoomMatch = localSongs
                .filter { !it.song.localUri.isNullOrBlank() && isUriReadable(it.song.localUri!!) }
                .mapNotNull { candidate ->
                    val candTitle = normalizeToken(candidate.song.title)
                    val candArtists = candidate.artists.map { normalizeToken(it.name) }.filter { it.isNotEmpty() }
                    val score = scoreCandidate(
                        targetTitle = targetTitle,
                        targetArtists = targetArtists,
                        targetDurationSec = metadata.duration.takeIf { it > 0 },
                        candidateTitle = candTitle,
                        candidateArtists = candArtists,
                        candidateDurationSec = candidate.song.duration.takeIf { it > 0 },
                    )
                    // Require strong artist + title match for local files
                    if (score >= 75) candidate to score else null
                }
                .maxByOrNull { it.second }
                ?.first

            if (bestRoomMatch != null) {
                return@withContext AudioSourceResult.AuthorizedStream(
                    uri = bestRoomMatch.song.localUri!!,
                    title = bestRoomMatch.song.title,
                    artist = bestRoomMatch.artists.joinToString(", ") { it.name }.ifBlank { "Local Artist" },
                    sourceType = AuthorizedSourceType.LOCAL_DEVICE,
                    providerLabel = providerName,
                    licenseLabel = "User Device Local Audio",
                    durationSeconds = bestRoomMatch.song.duration.takeIf { it > 0 },
                )
            }

            // 3. Query Android MediaStore if READ_MEDIA_AUDIO / READ_EXTERNAL_STORAGE is already granted
            resolveFromMediaStore(targetTitle, targetArtists, metadata.duration.takeIf { it > 0 })
        }

    override suspend fun getAvailableLocalSuggestions(limit: Int): List<LocalAudioSuggestion> =
        withContext(Dispatchers.IO) {
            val localSongs = runCatching {
                database.localSongsByRowIdAsc().firstOrNull().orEmpty()
            }.getOrDefault(emptyList())

            localSongs
                .filter { !it.song.localUri.isNullOrBlank() }
                .take(limit)
                .map {
                    LocalAudioSuggestion(
                        songId = it.song.id,
                        title = it.song.title,
                        artist = it.artists.joinToString(", ") { a -> a.name }.ifBlank { "Local Artist" },
                        localUri = it.song.localUri!!,
                    )
                }
        }

    private fun resolveFromMediaStore(
        targetTitle: String,
        targetArtists: List<String>,
        targetDurationSec: Int?,
    ): AudioSourceResult.AuthorizedStream? {
        return runCatching {
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.MIME_TYPE,
            )
            val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
            var bestMatch: AudioSourceResult.AuthorizedStream? = null
            var bestScore = 0

            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                null,
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val title = cursor.getString(titleCol).orEmpty()
                    val artist = cursor.getString(artistCol).orEmpty()
                    val durationMs = cursor.getLong(durationCol)
                    val mime = cursor.getString(mimeCol)

                    val candTitle = normalizeToken(title)
                    val candArtists = listOf(normalizeToken(artist)).filter { it.isNotEmpty() }
                    val durationSec = (durationMs / 1000L).toInt().takeIf { it > 0 }

                    val score = scoreCandidate(
                        targetTitle = targetTitle,
                        targetArtists = targetArtists,
                        targetDurationSec = targetDurationSec,
                        candidateTitle = candTitle,
                        candidateArtists = candArtists,
                        candidateDurationSec = durationSec,
                    )
                    if (score >= 75 && score > bestScore) {
                        bestScore = score
                        val contentUri = ContentUris.withAppendedId(
                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                            id,
                        ).toString()
                        bestMatch = AudioSourceResult.AuthorizedStream(
                            uri = contentUri,
                            title = title,
                            artist = artist.ifBlank { "Local Artist" },
                            sourceType = AuthorizedSourceType.LOCAL_DEVICE,
                            providerLabel = providerName,
                            licenseLabel = "User Device Local Audio",
                            mimeType = mime,
                            durationSeconds = durationSec,
                        )
                    }
                }
            }
            bestMatch
        }.getOrNull()
    }

    private fun isUriReadable(uriString: String): Boolean {
        if (uriString.startsWith("file://")) {
            val path = uriString.removePrefix("file://")
            return File(path).exists()
        }
        return uriString.startsWith("content://") || uriString.startsWith("file:")
    }

    internal companion object {
        private val TOPIC_SUFFIX_REGEX = Regex("""(?i)\s*-\s*topic$""")
        private val SOUNDTRACK_PARENS_REGEX = Regex(
            """(?i)[\(\[\{]\s*(?:from|original\s+motion\s+picture|full\s+(?:song|video|audio)|feat\.?|ft\.?)[^\)\]\}]*[\)\]\}]"""
        )
        private val NOISE_PARENS_REGEX = Regex(
            """(?i)[\(\[\{][^\)\]\}]*\b(official\s*(music\s*)?(video|audio|lyric\s*video)?|lyrics?|lyrical|hd|4k|8k|remastered|visualizer|audio|video\s*song|full\s*video)\b[^\)\]\}]*[\)\]\}]"""
        )
        private val NON_ALNUM_REGEX = Regex("""[^\p{L}\p{N}\s]+""")
        private val MULTI_SPACE_REGEX = Regex("""\s+""")

        fun cleanCoreTitle(raw: String): String {
            val beforePipe = raw
                .substringBefore(" | ")
                .substringBefore(" // ")
                .substringBefore(" - Full ")
            return beforePipe
                .replace(SOUNDTRACK_PARENS_REGEX, " ")
                .replace(NOISE_PARENS_REGEX, " ")
                .replace(MULTI_SPACE_REGEX, " ")
                .trim()
                .ifBlank { raw.trim() }
        }

        fun normalizeToken(raw: String): String {
            return cleanCoreTitle(raw)
                .replace(TOPIC_SUFFIX_REGEX, " ")
                .replace(NOISE_PARENS_REGEX, " ")
                .replace(NON_ALNUM_REGEX, " ")
                .replace(MULTI_SPACE_REGEX, " ")
                .trim()
                .lowercase()
        }

        fun scoreCandidate(
            targetTitle: String,
            targetArtists: List<String>,
            targetDurationSec: Int?,
            candidateTitle: String,
            candidateArtists: List<String>,
            candidateDurationSec: Int?,
        ): Int {
            if (targetTitle.isEmpty() || candidateTitle.isEmpty()) return 0

            val targetWords = targetTitle.split(" ").filter { it.length >= 2 }
            val candWords = candidateTitle.split(" ").filter { it.length >= 2 }.toSet()
            val wordOverlapRatio = if (targetWords.isNotEmpty()) {
                targetWords.count { it in candWords }.toFloat() / targetWords.size.toFloat()
            } else {
                0f
            }

            val titleScore = when {
                candidateTitle == targetTitle -> 60
                candidateTitle.contains(targetTitle) || targetTitle.contains(candidateTitle) -> 52
                wordOverlapRatio >= 0.75f -> 45
                wordOverlapRatio >= 0.5f && targetWords.size >= 2 -> 35
                else -> 0
            }
            if (titleScore == 0) return 0

            val cleanTargetArtists = targetArtists.filter { it.isNotEmpty() && it != "unknown artist" }
            val cleanCandArtists = candidateArtists.filter { it.isNotEmpty() && it != "unknown artist" }

            val artistScore = if (cleanTargetArtists.isEmpty()) {
                20
            } else {
                val matched = cleanTargetArtists.any { t ->
                    cleanCandArtists.any { c ->
                        c == t || c.contains(t) || t.contains(c) ||
                            t.split(" ").any { word -> word.length >= 3 && c.contains(word) }
                    }
                }
                if (matched) 25 else 5
            }

            val durationScore = if (targetDurationSec != null && candidateDurationSec != null && targetDurationSec > 0 && candidateDurationSec > 0) {
                val diff = abs(targetDurationSec - candidateDurationSec)
                when {
                    diff <= 6 -> 15
                    diff <= 20 -> 10
                    diff <= 45 -> 5
                    diff > 90 -> -15
                    else -> 0
                }
            } else {
                10
            }

            return titleScore + artistScore + durationScore
        }
    }
}

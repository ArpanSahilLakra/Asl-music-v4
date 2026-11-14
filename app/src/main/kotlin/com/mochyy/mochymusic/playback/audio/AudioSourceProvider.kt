package com.mochyy.mochymusic.playback.audio

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata as Media3MediaMetadata
import com.mochyy.mochymusic.extensions.metadata
import com.mochyy.mochymusic.models.MediaMetadata
import kotlinx.serialization.Serializable

/**
 * Classifies the legal authorization basis of a resolved audio stream.
 * YouTube audio extraction is strictly prohibited and has no enum entry here.
 */
@Serializable
enum class AuthorizedSourceType {
    LOCAL_DEVICE,
    LICENSED_CREATIVE_COMMONS,
}

/**
 * Normalized track lookup query used by [AudioSourceResolver], [LocalAudioProvider],
 * and [LicensedAudioProvider] to match catalogue metadata to an authorized audio stream.
 */
@Serializable
data class AudioResolutionQuery(
    val songId: String,
    val title: String,
    val artists: List<String>,
    val albumTitle: String? = null,
    val durationSeconds: Int? = null,
    val isrc: String? = null,
    val localUri: String? = null,
    val thumbnailUrl: String? = null,
) {
    companion object {
        fun fromMediaMetadata(metadata: MediaMetadata): AudioResolutionQuery =
            AudioResolutionQuery(
                songId = metadata.id,
                title = metadata.title,
                artists = metadata.artists.map { it.name },
                albumTitle = metadata.album?.title,
                durationSeconds = metadata.duration.takeIf { it > 0 },
                localUri = metadata.localUri,
                thumbnailUrl = metadata.thumbnailUrl,
            )

        fun fromMedia3Item(mediaItem: MediaItem): AudioResolutionQuery {
            val tagMeta = mediaItem.metadata
            if (tagMeta != null) return fromMediaMetadata(tagMeta)

            val m3 = mediaItem.mediaMetadata
            val artistStr = m3.artist?.toString().orEmpty()
            return AudioResolutionQuery(
                songId = mediaItem.mediaId,
                title = m3.title?.toString().orEmpty(),
                artists = if (artistStr.isNotBlank()) listOf(artistStr) else emptyList(),
                albumTitle = m3.albumTitle?.toString(),
                localUri = mediaItem.localConfiguration?.uri?.toString(),
                thumbnailUrl = m3.artworkUri?.toString(),
            )
        }
    }
}

@Serializable
data class LocalAudioSuggestion(
    val songId: String,
    val title: String,
    val artist: String,
    val localUri: String,
)

sealed interface AudioSourceResult {
    @Serializable
    data class AuthorizedStream(
        val uri: String,
        val title: String,
        val artist: String,
        val sourceType: AuthorizedSourceType,
        val providerLabel: String,
        val licenseLabel: String,
        val mimeType: String? = null,
        val durationSeconds: Int? = null,
        val matchedTrackId: String? = null,
        val artworkUrl: String? = null,
    ) : AudioSourceResult {

        /**
         * Builds a Media3 [MediaItem] configured with the authorized stream URI, MIME type,
         * artwork URI, and authorization metadata extras for `ExoPlayer` / `MediaSession`.
         */
        fun toMedia3MediaItem(
            mediaId: String = matchedTrackId ?: uri,
            fallbackMetadata: MediaMetadata? = null,
        ): MediaItem {
            val extras = Bundle().apply {
                putString(EXTRA_AUTHORIZED_URI, uri)
                putString(EXTRA_AUTHORIZED_SOURCE_TYPE, sourceType.name)
                putString(EXTRA_AUTHORIZED_PROVIDER, providerLabel)
                putString(EXTRA_AUTHORIZED_LICENSE, licenseLabel)
                durationSeconds?.let { putInt(EXTRA_AUTHORIZED_DURATION_SEC, it) }
            }

            val resolvedThumb = artworkUrl ?: fallbackMetadata?.thumbnailUrl
            val media3Metadata = Media3MediaMetadata.Builder()
                .setTitle(title.ifBlank { fallbackMetadata?.title ?: mediaId })
                .setArtist(
                    artist.ifBlank {
                        fallbackMetadata?.artists?.joinToString(", ") { it.name }.orEmpty()
                    }
                )
                .setAlbumTitle(fallbackMetadata?.album?.title)
                .apply {
                    if (!resolvedThumb.isNullOrBlank()) {
                        setArtworkUri(Uri.parse(resolvedThumb))
                    }
                }
                .setIsPlayable(true)
                .setIsBrowsable(false)
                .setExtras(extras)
                .build()

            return MediaItem.Builder()
                .setMediaId(mediaId)
                .setUri(Uri.parse(uri))
                .apply {
                    if (!mimeType.isNullOrBlank()) {
                        setMimeType(mimeType)
                    }
                    setCustomCacheKey("authorized|$providerLabel|$mediaId")
                    if (fallbackMetadata != null) {
                        setTag(fallbackMetadata)
                    }
                }
                .setMediaMetadata(media3Metadata)
                .build()
        }

        companion object {
            const val EXTRA_AUTHORIZED_URI = "com.mochyy.mochymusic.extra.AUTHORIZED_URI"
            const val EXTRA_AUTHORIZED_SOURCE_TYPE = "com.mochyy.mochymusic.extra.AUTHORIZED_SOURCE_TYPE"
            const val EXTRA_AUTHORIZED_PROVIDER = "com.mochyy.mochymusic.extra.AUTHORIZED_PROVIDER"
            const val EXTRA_AUTHORIZED_LICENSE = "com.mochyy.mochymusic.extra.AUTHORIZED_LICENSE"
            const val EXTRA_AUTHORIZED_DURATION_SEC = "com.mochyy.mochymusic.extra.AUTHORIZED_DURATION_SEC"
        }
    }

    data class Unavailable(
        val reason: String = "Audio source unavailable",
        val externalUrl: String? = null,
        val localSuggestions: List<LocalAudioSuggestion> = emptyList(),
    ) : AudioSourceResult
}

/**
 * Base contract for providers that resolve catalogue metadata into an explicitly
 * authorized playable audio stream.
 */
interface AudioSourceProvider {
    val providerId: String
    val providerName: String

    suspend fun resolve(metadata: MediaMetadata): AudioSourceResult.AuthorizedStream?

    suspend fun resolveMediaItem(mediaItem: MediaItem): AudioSourceResult.AuthorizedStream? {
        val meta = mediaItem.metadata ?: return null
        return resolve(meta)
    }
}

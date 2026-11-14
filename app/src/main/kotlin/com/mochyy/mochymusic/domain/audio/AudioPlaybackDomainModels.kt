package com.mochyy.mochymusic.domain.audio

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata as Media3MediaMetadata
import com.mochyy.mochymusic.extensions.metadata
import com.mochyy.mochymusic.models.MediaMetadata
import com.mochyy.mochymusic.playback.audio.AudioSourceResult
import com.mochyy.mochymusic.playback.audio.AuthorizedSourceType
import com.mochyy.mochymusic.playback.audio.LocalAudioSuggestion
import kotlinx.serialization.Serializable

/**
 * Domain representation of a discovered music track's metadata (e.g., from `sigma67/ytmusicapi`).
 * Contains strictly catalogue metadata, artwork, and identifiers — never unauthorized stream URLs.
 */
@Serializable
data class TrackMetadataDescriptor(
    val trackId: String,
    val title: String,
    val artist: String,
    val artists: List<String> = emptyList(),
    val albumTitle: String? = null,
    val albumBrowseId: String? = null,
    val durationSeconds: Int? = null,
    val thumbnailUrl: String? = null,
    val isrc: String? = null,
    val localUri: String? = null,
    val isExplicit: Boolean = false,
) {
    fun toAppMediaMetadata(): MediaMetadata {
        val artistEntities = if (artists.isNotEmpty()) {
            artists.map { MediaMetadata.Artist(id = null, name = it) }
        } else if (artist.isNotBlank()) {
            listOf(MediaMetadata.Artist(id = null, name = artist))
        } else {
            emptyList()
        }
        val albumEntity = if (!albumTitle.isNullOrBlank()) {
            MediaMetadata.Album(id = albumBrowseId.orEmpty(), title = albumTitle)
        } else {
            null
        }
        return MediaMetadata(
            id = trackId,
            title = title,
            artists = artistEntities,
            duration = durationSeconds ?: -1,
            thumbnailUrl = thumbnailUrl,
            album = albumEntity,
            explicit = isExplicit,
            localUri = localUri,
        )
    }

    companion object {
        fun fromAppMediaMetadata(metadata: MediaMetadata): TrackMetadataDescriptor {
            val artistNames = metadata.artists.map { it.name }.filter { it.isNotBlank() }
            return TrackMetadataDescriptor(
                trackId = metadata.id,
                title = metadata.title,
                artist = artistNames.joinToString(", ").ifBlank { "Unknown Artist" },
                artists = artistNames,
                albumTitle = metadata.album?.title,
                albumBrowseId = metadata.album?.id,
                durationSeconds = metadata.duration.takeIf { it > 0 },
                thumbnailUrl = metadata.thumbnailUrl,
                localUri = metadata.localUri,
                isExplicit = metadata.explicit,
            )
        }

        fun fromMedia3MediaItem(mediaItem: MediaItem): TrackMetadataDescriptor {
            mediaItem.metadata?.let { return fromAppMediaMetadata(it) }

            val m3 = mediaItem.mediaMetadata
            val artistText = m3.artist?.toString().orEmpty().trim()
            val durationSec = m3.extras?.getInt(
                AudioSourceResult.AuthorizedStream.EXTRA_AUTHORIZED_DURATION_SEC,
                -1,
            )?.takeIf { it > 0 }

            return TrackMetadataDescriptor(
                trackId = mediaItem.mediaId,
                title = m3.title?.toString().orEmpty().ifBlank { mediaItem.mediaId },
                artist = artistText.ifBlank { "Unknown Artist" },
                artists = if (artistText.isNotBlank()) {
                    artistText.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                } else {
                    emptyList()
                },
                albumTitle = m3.albumTitle?.toString(),
                durationSeconds = durationSec,
                thumbnailUrl = m3.artworkUri?.toString(),
                localUri = mediaItem.localConfiguration?.uri?.toString(),
            )
        }
    }
}

/**
 * Domain model recording the explicit legal authorization for streaming an audio recording.
 */
@Serializable
data class AudioAuthorizationLicense(
    val sourceType: AuthorizedSourceType,
    val providerId: String,
    val providerName: String,
    val licenseName: String,
    val licenseUrl: String? = null,
    val isOfflinePlayable: Boolean = sourceType == AuthorizedSourceType.LOCAL_DEVICE,
    val verifiedAtEpochMs: Long = System.currentTimeMillis(),
)

/**
 * Domain data class representing a verified, playable audio stream and its Media3 [MediaItem] mapping.
 */
@Serializable
data class AuthorizedMediaStream(
    val trackId: String,
    val streamUri: String,
    val title: String,
    val artist: String,
    val albumTitle: String? = null,
    val durationSeconds: Int? = null,
    val mimeType: String? = null,
    val artworkUrl: String? = null,
    val bitrateKbps: Int? = null,
    val authorization: AudioAuthorizationLicense,
) {
    /**
     * Converts this domain stream into a Media3 [MediaItem] ready for `ExoPlayer` and `MediaSession`.
     */
    fun toMedia3MediaItem(fallbackMetadata: MediaMetadata? = null): MediaItem {
        val extras = Bundle().apply {
            putString(AudioSourceResult.AuthorizedStream.EXTRA_AUTHORIZED_URI, streamUri)
            putString(
                AudioSourceResult.AuthorizedStream.EXTRA_AUTHORIZED_SOURCE_TYPE,
                authorization.sourceType.name,
            )
            putString(EXTRA_AUTHORIZED_PROVIDER_ID, authorization.providerId)
            putString(
                AudioSourceResult.AuthorizedStream.EXTRA_AUTHORIZED_PROVIDER,
                authorization.providerName,
            )
            putString(
                AudioSourceResult.AuthorizedStream.EXTRA_AUTHORIZED_LICENSE,
                authorization.licenseName,
            )
            durationSeconds?.let {
                putInt(AudioSourceResult.AuthorizedStream.EXTRA_AUTHORIZED_DURATION_SEC, it)
            }
            bitrateKbps?.let { putInt(EXTRA_AUTHORIZED_BITRATE_KBPS, it) }
        }

        val resolvedArtwork = artworkUrl ?: fallbackMetadata?.thumbnailUrl
        val media3Meta = Media3MediaMetadata.Builder()
            .setTitle(title.ifBlank { fallbackMetadata?.title ?: trackId })
            .setArtist(
                artist.ifBlank {
                    fallbackMetadata?.artists?.joinToString(", ") { it.name }.orEmpty()
                }
            )
            .setAlbumTitle(albumTitle ?: fallbackMetadata?.album?.title)
            .apply {
                if (!resolvedArtwork.isNullOrBlank()) {
                    setArtworkUri(Uri.parse(resolvedArtwork))
                }
            }
            .setIsPlayable(true)
            .setIsBrowsable(false)
            .setExtras(extras)
            .build()

        val requestMetadata = MediaItem.RequestMetadata.Builder()
            .setMediaUri(Uri.parse(streamUri))
            .setExtras(extras)
            .build()

        return MediaItem.Builder()
            .setMediaId(trackId)
            .setUri(Uri.parse(streamUri))
            .setRequestMetadata(requestMetadata)
            .apply {
                if (!mimeType.isNullOrBlank()) {
                    setMimeType(mimeType)
                }
                setCustomCacheKey("authorized|${authorization.providerId}|$trackId")
                if (fallbackMetadata != null) {
                    setTag(fallbackMetadata)
                }
            }
            .setMediaMetadata(media3Meta)
            .build()
    }

    fun toAuthorizedStreamResult(): AudioSourceResult.AuthorizedStream =
        AudioSourceResult.AuthorizedStream(
            uri = streamUri,
            title = title,
            artist = artist,
            sourceType = authorization.sourceType,
            providerLabel = authorization.providerName,
            licenseLabel = authorization.licenseName,
            mimeType = mimeType,
            durationSeconds = durationSeconds,
            matchedTrackId = trackId,
            artworkUrl = artworkUrl,
        )

    companion object {
        const val EXTRA_AUTHORIZED_PROVIDER_ID = "com.mochyy.mochymusic.extra.AUTHORIZED_PROVIDER_ID"
        const val EXTRA_AUTHORIZED_BITRATE_KBPS = "com.mochyy.mochymusic.extra.AUTHORIZED_BITRATE_KBPS"

        fun fromAuthorizedStreamResult(
            trackId: String,
            providerId: String,
            result: AudioSourceResult.AuthorizedStream,
            albumTitle: String? = null,
        ): AuthorizedMediaStream = AuthorizedMediaStream(
            trackId = result.matchedTrackId ?: trackId,
            streamUri = result.uri,
            title = result.title,
            artist = result.artist,
            albumTitle = albumTitle,
            durationSeconds = result.durationSeconds,
            mimeType = result.mimeType,
            artworkUrl = result.artworkUrl,
            authorization = AudioAuthorizationLicense(
                sourceType = result.sourceType,
                providerId = providerId,
                providerName = result.providerLabel,
                licenseName = result.licenseLabel,
            ),
        )

        fun fromMedia3MediaItem(mediaItem: MediaItem): AuthorizedMediaStream? {
            val extras = mediaItem.mediaMetadata.extras ?: mediaItem.requestMetadata.extras ?: return null
            val uri = extras.getString(AudioSourceResult.AuthorizedStream.EXTRA_AUTHORIZED_URI)
                ?: mediaItem.localConfiguration?.uri?.toString()
                ?: return null
            val sourceTypeName = extras.getString(
                AudioSourceResult.AuthorizedStream.EXTRA_AUTHORIZED_SOURCE_TYPE
            ) ?: return null
            val sourceType = runCatching {
                AuthorizedSourceType.valueOf(sourceTypeName)
            }.getOrNull() ?: return null

            val providerId = extras.getString(EXTRA_AUTHORIZED_PROVIDER_ID) ?: "authorized_provider"
            val providerName = extras.getString(
                AudioSourceResult.AuthorizedStream.EXTRA_AUTHORIZED_PROVIDER
            ) ?: providerId
            val licenseName = extras.getString(
                AudioSourceResult.AuthorizedStream.EXTRA_AUTHORIZED_LICENSE
            ) ?: "Authorized Audio Stream"
            val durationSec = extras.getInt(
                AudioSourceResult.AuthorizedStream.EXTRA_AUTHORIZED_DURATION_SEC,
                -1,
            ).takeIf { it > 0 }

            return AuthorizedMediaStream(
                trackId = mediaItem.mediaId,
                streamUri = uri,
                title = mediaItem.mediaMetadata.title?.toString().orEmpty(),
                artist = mediaItem.mediaMetadata.artist?.toString().orEmpty(),
                albumTitle = mediaItem.mediaMetadata.albumTitle?.toString(),
                durationSeconds = durationSec,
                mimeType = mediaItem.localConfiguration?.mimeType,
                artworkUrl = mediaItem.mediaMetadata.artworkUri?.toString(),
                authorization = AudioAuthorizationLicense(
                    sourceType = sourceType,
                    providerId = providerId,
                    providerName = providerName,
                    licenseName = licenseName,
                ),
            )
        }
    }
}

/**
 * Domain model returned when no authorized audio source matches a discovered track.
 */
@Serializable
data class UnavailableAudioSource(
    val trackId: String,
    val title: String,
    val artist: String,
    val reason: String = "Audio source unavailable",
    val officialExternalPlaybackUrl: String,
    val localAlternatives: List<LocalAudioSuggestion> = emptyList(),
)

/**
 * Sealed domain outcome of resolving a track through [AudioSourceResolverContract].
 */
sealed interface DomainAudioResolution {
    data class Authorized(
        val stream: AuthorizedMediaStream,
        val mediaItem: MediaItem,
    ) : DomainAudioResolution

    data class Unavailable(
        val details: UnavailableAudioSource,
    ) : DomainAudioResolution
}

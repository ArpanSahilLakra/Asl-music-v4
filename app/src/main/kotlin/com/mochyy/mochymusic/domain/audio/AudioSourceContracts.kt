package com.mochyy.mochymusic.domain.audio

import androidx.media3.common.MediaItem
import com.mochyy.mochymusic.models.MediaMetadata
import com.mochyy.mochymusic.playback.audio.AudioSourceProvider
import com.mochyy.mochymusic.playback.audio.AudioSourceResult
import com.mochyy.mochymusic.playback.audio.LocalAudioSuggestion
import com.mochyy.mochymusic.playback.audio.UnavailablePlaybackNotice
import kotlinx.coroutines.flow.StateFlow

/**
 * Domain-layer base interface for authorized audio stream providers.
 * Implementations must return a stream only when they hold explicit authorization
 * to play that recording (e.g., user-owned local files or licensed Creative Commons catalogs).
 */
interface AudioSourceProviderContract : AudioSourceProvider {
    suspend fun resolveDomainTrack(track: TrackMetadataDescriptor): AuthorizedMediaStream? {
        val result = resolve(track.toAppMediaMetadata()) ?: return null
        return AuthorizedMediaStream.fromAuthorizedStreamResult(
            trackId = track.trackId,
            providerId = providerId,
            result = result,
            albumTitle = track.albumTitle,
        )
    }

    suspend fun resolveToMedia3Item(track: TrackMetadataDescriptor): MediaItem? {
        val stream = resolveDomainTrack(track) ?: return null
        return stream.toMedia3MediaItem(fallbackMetadata = track.toAppMediaMetadata())
    }
}

/**
 * Domain-layer contract for resolving user-owned local device audio files
 * (via Room local library and Android `MediaStore.Audio.Media`).
 */
interface LocalAudioProviderContract : AudioSourceProviderContract {
    suspend fun getAvailableLocalSuggestions(limit: Int = 5): List<LocalAudioSuggestion>
}

/**
 * Domain-layer contract for resolving independently licensed / Creative Commons
 * audio streams (e.g., public stream-authorized Audius tracks).
 */
interface LicensedAudioProviderContract : AudioSourceProviderContract {
    fun setBaseUrlForTesting(url: String?)
}

/**
 * Domain-layer contract for the central audio source resolver.
 *
 * Matches discovered track metadata against [LocalAudioProviderContract] and
 * [LicensedAudioProviderContract], returning a Media3-compatible [DomainAudioResolution].
 * Never extracts YouTube audio streams.
 */
interface AudioSourceResolverContract {
    val unavailableNotice: StateFlow<UnavailablePlaybackNotice?>
    val activeAuthorizedSource: StateFlow<AudioSourceResult.AuthorizedStream?>

    fun dismissUnavailableNotice()

    fun getCachedAuthorizedStream(songId: String): AudioSourceResult.AuthorizedStream?

    suspend fun resolve(
        metadata: MediaMetadata,
        emitUnavailableUiNotice: Boolean = true,
    ): AudioSourceResult

    suspend fun resolveToMedia3Item(
        mediaItem: MediaItem,
        emitUnavailableUiNotice: Boolean = true,
    ): MediaItem?

    suspend fun resolveDomainTrack(
        track: TrackMetadataDescriptor,
        emitUnavailableUiNotice: Boolean = true,
    ): DomainAudioResolution
}

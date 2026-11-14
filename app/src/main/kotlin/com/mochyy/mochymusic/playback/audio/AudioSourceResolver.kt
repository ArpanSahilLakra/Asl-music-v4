package com.mochyy.mochymusic.playback.audio

import androidx.media3.common.MediaItem
import com.mochyy.mochymusic.db.AuthorizedAudioRepository
import com.mochyy.mochymusic.domain.audio.AudioSourceResolverContract
import com.mochyy.mochymusic.domain.audio.AuthorizedMediaStream
import com.mochyy.mochymusic.domain.audio.DomainAudioResolution
import com.mochyy.mochymusic.domain.audio.TrackMetadataDescriptor
import com.mochyy.mochymusic.domain.audio.UnavailableAudioSource
import com.mochyy.mochymusic.extensions.metadata
import com.mochyy.mochymusic.models.MediaMetadata
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

data class UnavailablePlaybackNotice(
    val songId: String,
    val title: String,
    val artist: String,
    val thumbnailUrl: String?,
    val reason: String,
    val externalYoutubeMusicUrl: String,
    val localSuggestions: List<LocalAudioSuggestion>,
)

@Singleton
class AudioSourceResolver @Inject constructor(
    private val localAudioProvider: LocalAudioProvider,
    private val licensedAudioProvider: LicensedAudioProvider,
    private val authorizedAudioRepository: AuthorizedAudioRepository? = null,
) : AudioSourceResolverContract {
    private val resolvedCache = ConcurrentHashMap<String, AudioSourceResult.AuthorizedStream>()

    private val _unavailableNotice = MutableStateFlow<UnavailablePlaybackNotice?>(null)
    override val unavailableNotice: StateFlow<UnavailablePlaybackNotice?> = _unavailableNotice.asStateFlow()

    private val _activeAuthorizedSource = MutableStateFlow<AudioSourceResult.AuthorizedStream?>(null)
    override val activeAuthorizedSource: StateFlow<AudioSourceResult.AuthorizedStream?> =
        _activeAuthorizedSource.asStateFlow()

    override fun dismissUnavailableNotice() {
        _unavailableNotice.value = null
    }

    override fun getCachedAuthorizedStream(songId: String): AudioSourceResult.AuthorizedStream? =
        resolvedCache[songId]

    override suspend fun resolveToMedia3Item(
        mediaItem: MediaItem,
        emitUnavailableUiNotice: Boolean,
    ): MediaItem? {
        val metadata = mediaItem.metadata ?: MediaMetadata(
            id = mediaItem.mediaId,
            title = mediaItem.mediaMetadata.title?.toString().orEmpty(),
            artists = listOfNotNull(
                mediaItem.mediaMetadata.artist?.toString()?.takeIf { it.isNotBlank() }?.let {
                    MediaMetadata.Artist(id = null, name = it)
                }
            ),
            duration = -1,
            thumbnailUrl = mediaItem.mediaMetadata.artworkUri?.toString(),
            localUri = mediaItem.localConfiguration?.uri?.toString(),
        )
        return when (val result = resolve(metadata, emitUnavailableUiNotice)) {
            is AudioSourceResult.AuthorizedStream ->
                result.toMedia3MediaItem(mediaId = metadata.id, fallbackMetadata = metadata)
            is AudioSourceResult.Unavailable -> null
        }
    }

    override suspend fun resolveDomainTrack(
        track: TrackMetadataDescriptor,
        emitUnavailableUiNotice: Boolean,
    ): DomainAudioResolution {
        val appMetadata = track.toAppMediaMetadata()
        return when (val result = resolve(appMetadata, emitUnavailableUiNotice)) {
            is AudioSourceResult.AuthorizedStream -> {
                val providerId = if (result.sourceType == AuthorizedSourceType.LOCAL_DEVICE) {
                    localAudioProvider.providerId
                } else {
                    licensedAudioProvider.providerId
                }
                val domainStream = AuthorizedMediaStream.fromAuthorizedStreamResult(
                    trackId = track.trackId,
                    providerId = providerId,
                    result = result,
                    albumTitle = track.albumTitle,
                )
                DomainAudioResolution.Authorized(
                    stream = domainStream,
                    mediaItem = domainStream.toMedia3MediaItem(fallbackMetadata = appMetadata),
                )
            }
            is AudioSourceResult.Unavailable -> {
                DomainAudioResolution.Unavailable(
                    details = UnavailableAudioSource(
                        trackId = track.trackId,
                        title = track.title,
                        artist = track.artist,
                        reason = result.reason,
                        officialExternalPlaybackUrl = result.externalUrl
                            ?: "https://music.youtube.com/watch?v=${track.trackId}",
                        localAlternatives = result.localSuggestions,
                    ),
                )
            }
        }
    }

    override suspend fun resolve(
        metadata: MediaMetadata,
        emitUnavailableUiNotice: Boolean,
    ): AudioSourceResult {
        resolvedCache[metadata.id]?.let { cached ->
            _activeAuthorizedSource.value = cached
            _unavailableNotice.value = null
            return cached
        }

        val normTitle = LocalAudioProvider.normalizeToken(metadata.title)
        val normArtist = metadata.artists.firstOrNull()?.name
            ?.let { LocalAudioProvider.normalizeToken(it) }
            .orEmpty()

        // 1. Check user's local device library first
        localAudioProvider.resolve(metadata)?.let { localStream ->
            val enriched = localStream.copy(
                matchedTrackId = metadata.id,
                artworkUrl = localStream.artworkUrl ?: metadata.thumbnailUrl,
            )
            resolvedCache[metadata.id] = enriched
            runCatching {
                authorizedAudioRepository?.saveAuthorizedStream(
                    songId = metadata.id,
                    normalizedTitle = normTitle,
                    normalizedArtist = normArtist,
                    stream = enriched,
                )
            }
            _activeAuthorizedSource.value = enriched
            _unavailableNotice.value = null
            return enriched
        }

        // 2. Check persisted SQLite authorized audio cache
        val sqlCached = runCatching {
            authorizedAudioRepository?.getCachedStream(
                songId = metadata.id,
                normalizedTitle = normTitle,
                normalizedArtist = normArtist,
            )
        }.getOrNull()
        if (sqlCached != null) {
            resolvedCache[metadata.id] = sqlCached
            _activeAuthorizedSource.value = sqlCached
            _unavailableNotice.value = null
            return sqlCached
        }

        // 3. Check independently authorized Creative Commons / public stream provider
        licensedAudioProvider.resolve(metadata)?.let { licensedStream ->
            val enriched = licensedStream.copy(
                matchedTrackId = metadata.id,
                artworkUrl = licensedStream.artworkUrl ?: metadata.thumbnailUrl,
            )
            resolvedCache[metadata.id] = enriched
            runCatching {
                authorizedAudioRepository?.saveAuthorizedStream(
                    songId = metadata.id,
                    normalizedTitle = normTitle,
                    normalizedArtist = normArtist,
                    stream = enriched,
                )
            }
            _activeAuthorizedSource.value = enriched
            _unavailableNotice.value = null
            return enriched
        }

        // 4. No authorized stream exists — never extract YouTube audio
        val externalUrl = "https://music.youtube.com/watch?v=${metadata.id}"
        val suggestions = localAudioProvider.getAvailableLocalSuggestions(limit = 5)
        val unavailable = AudioSourceResult.Unavailable(
            reason = "Audio source unavailable",
            externalUrl = externalUrl,
            localSuggestions = suggestions,
        )

        _activeAuthorizedSource.value = null
        if (emitUnavailableUiNotice) {
            _unavailableNotice.value = UnavailablePlaybackNotice(
                songId = metadata.id,
                title = metadata.title,
                artist = metadata.artists.joinToString(", ") { it.name }.ifBlank { "Unknown Artist" },
                thumbnailUrl = metadata.thumbnailUrl,
                reason = unavailable.reason,
                externalYoutubeMusicUrl = externalUrl,
                localSuggestions = suggestions,
            )
        }
        return unavailable
    }
}

package com.mochyy.mochymusic.api.ytmusic

import com.music.innertube.models.Album
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.Artist
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint
import kotlinx.serialization.Serializable

@Serializable
data class YtArtistRefDto(
    val id: String? = null,
    val name: String = "",
)

@Serializable
data class YtAlbumRefDto(
    val id: String? = null,
    val name: String = "",
)

@Serializable
data class YtTrackSummaryDto(
    val videoId: String = "",
    val title: String = "",
    val artist: String = "Unknown Artist",
    val artists: List<YtArtistRefDto> = emptyList(),
    val album: YtAlbumRefDto? = null,
    val durationSeconds: Int? = null,
    val durationText: String? = null,
    val thumbnailUrl: String? = null,
    val isExplicit: Boolean = false,
    val resultType: String = "song",
) {
    fun toSongItem(): SongItem {
        val mappedArtists = if (artists.isNotEmpty()) {
            artists.map { Artist(name = it.name, id = it.id) }
        } else {
            listOf(Artist(name = artist.ifBlank { "Unknown Artist" }, id = null))
        }
        val mappedAlbum = album?.takeIf { it.name.isNotBlank() && !it.id.isNullOrBlank() }?.let {
            Album(name = it.name, id = it.id!!)
        }
        val thumb = thumbnailUrl?.takeIf { it.isNotBlank() }
            ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
        return SongItem(
            id = videoId,
            title = title.ifBlank { videoId },
            artists = mappedArtists,
            album = mappedAlbum,
            duration = durationSeconds,
            thumbnail = thumb,
            explicit = isExplicit,
            endpoint = WatchEndpoint(videoId = videoId),
        )
    }
}

@Serializable
data class YtAlbumSummaryDto(
    val browseId: String = "",
    val playlistId: String? = null,
    val title: String = "",
    val artist: String = "",
    val artists: List<YtArtistRefDto> = emptyList(),
    val year: Int? = null,
    val thumbnailUrl: String? = null,
    val isExplicit: Boolean = false,
) {
    fun toAlbumItem(): AlbumItem {
        val mappedArtists = if (artists.isNotEmpty()) {
            artists.map { Artist(name = it.name, id = it.id) }
        } else if (artist.isNotBlank()) {
            listOf(Artist(name = artist, id = null))
        } else {
            emptyList()
        }
        return AlbumItem(
            browseId = browseId,
            playlistId = playlistId ?: browseId,
            title = title,
            artists = mappedArtists,
            year = year,
            thumbnail = thumbnailUrl.orEmpty(),
            explicit = isExplicit,
        )
    }
}

@Serializable
data class YtArtistSummaryDto(
    val browseId: String = "",
    val name: String = "",
    val subscribers: String? = null,
    val thumbnailUrl: String? = null,
) {
    fun toArtistItem(): ArtistItem = ArtistItem(
        id = browseId,
        title = name,
        thumbnail = thumbnailUrl,
        shuffleEndpoint = null,
        radioEndpoint = null,
    )
}

@Serializable
data class YtPlaylistSummaryDto(
    val playlistId: String = "",
    val title: String = "",
    val author: String? = null,
    val itemCount: String? = null,
    val thumbnailUrl: String? = null,
) {
    fun toPlaylistItem(): PlaylistItem = PlaylistItem(
        id = playlistId,
        title = title,
        author = author?.takeIf { it.isNotBlank() }?.let { Artist(name = it, id = null) },
        songCountText = itemCount,
        thumbnail = thumbnailUrl,
        playEndpoint = WatchEndpoint(playlistId = playlistId),
        shuffleEndpoint = null,
        radioEndpoint = null,
    )
}

@Serializable
data class YtSearchResponseDto(
    val query: String = "",
    val filter: String? = null,
    val items: List<YtTrackSummaryDto> = emptyList(),
    val albums: List<YtAlbumSummaryDto> = emptyList(),
    val artists: List<YtArtistSummaryDto> = emptyList(),
    val playlists: List<YtPlaylistSummaryDto> = emptyList(),
    val suggestions: List<String> = emptyList(),
    val continuation: String? = null,
)

@Serializable
data class YtSuggestionsResponseDto(
    val query: String = "",
    val suggestions: List<String> = emptyList(),
)

@Serializable
data class YtSongDetailDto(
    val videoId: String = "",
    val title: String = "",
    val artist: String = "Unknown Artist",
    val artists: List<YtArtistRefDto> = emptyList(),
    val album: YtAlbumRefDto? = null,
    val durationSeconds: Int? = null,
    val thumbnailUrl: String? = null,
    val isExplicit: Boolean = false,
    val lyricsBrowseId: String? = null,
    val relatedBrowseId: String? = null,
    val recommendedTracks: List<YtTrackSummaryDto> = emptyList(),
    val streamingAuthorized: Boolean = false,
) {
    fun toSongItem(): SongItem = YtTrackSummaryDto(
        videoId = videoId,
        title = title,
        artist = artist,
        artists = artists,
        album = album,
        durationSeconds = durationSeconds,
        thumbnailUrl = thumbnailUrl,
        isExplicit = isExplicit,
    ).toSongItem()
}

@Serializable
data class YtLyricLineDto(
    val startTimeMs: Long? = null,
    val endTimeMs: Long? = null,
    val text: String = "",
)

@Serializable
data class YtLyricsResponseDto(
    val videoId: String = "",
    val available: Boolean = false,
    val hasTimestamps: Boolean = false,
    val lyricsText: String = "",
    val lines: List<YtLyricLineDto> = emptyList(),
    val source: String? = null,
) {
    fun toFormattedLyricsString(): String? {
        if (!available) return null
        if (hasTimestamps && lines.any { it.startTimeMs != null }) {
            return lines.joinToString("\n") { line ->
                val ms = line.startTimeMs
                if (ms != null && ms >= 0) {
                    val mins = ms / 60_000
                    val secs = (ms % 60_000) / 1000
                    val centis = (ms % 1000) / 10
                    "[%02d:%02d.%02d]%s".format(mins, secs, centis, line.text)
                } else {
                    line.text
                }
            }.takeIf { it.isNotBlank() }
        }
        return lyricsText.trim().takeIf { it.isNotBlank() }
            ?: lines.joinToString("\n") { it.text }.trim().takeIf { it.isNotBlank() }
    }
}

@Serializable
data class YtAlbumDetailDto(
    val browseId: String = "",
    val playlistId: String? = null,
    val title: String = "",
    val artist: String = "",
    val artists: List<YtArtistRefDto> = emptyList(),
    val year: Int? = null,
    val description: String? = null,
    val thumbnailUrl: String? = null,
    val trackCount: Int = 0,
    val durationSeconds: Int? = null,
    val tracks: List<YtTrackSummaryDto> = emptyList(),
) {
    fun toAlbumPage(): com.music.innertube.pages.AlbumPage {
        val mappedArtists = if (artists.isNotEmpty()) {
            artists.map { Artist(name = it.name, id = it.id) }
        } else if (artist.isNotBlank()) {
            listOf(Artist(name = artist, id = null))
        } else {
            emptyList()
        }
        val albumItem = AlbumItem(
            browseId = browseId,
            playlistId = playlistId?.takeIf { it.isNotBlank() } ?: browseId,
            title = title,
            artists = mappedArtists,
            year = year,
            thumbnail = thumbnailUrl.orEmpty(),
            description = description,
        )
        return com.music.innertube.pages.AlbumPage(
            album = albumItem,
            songs = tracks.map { it.toSongItem() },
            otherVersions = emptyList(),
            releasesForYou = emptyList(),
            description = description,
        )
    }
}

@Serializable
data class YtArtistDetailDto(
    val browseId: String = "",
    val name: String = "",
    val description: String? = null,
    val subscribers: String? = null,
    val views: String? = null,
    val thumbnailUrl: String? = null,
    val shufflePlaylistId: String? = null,
    val radioPlaylistId: String? = null,
    val topSongs: List<YtTrackSummaryDto> = emptyList(),
    val albums: List<YtAlbumSummaryDto> = emptyList(),
    val singles: List<YtAlbumSummaryDto> = emptyList(),
    val relatedArtists: List<YtArtistSummaryDto> = emptyList(),
) {
    fun toArtistPage(): com.music.innertube.pages.ArtistPage {
        val sections = buildList {
            if (topSongs.isNotEmpty()) {
                add(
                    com.music.innertube.pages.ArtistSection(
                        title = "Songs",
                        items = topSongs.map { it.toSongItem() },
                        moreEndpoint = null,
                    ),
                )
            }
            if (albums.isNotEmpty()) {
                add(
                    com.music.innertube.pages.ArtistSection(
                        title = "Albums",
                        items = albums.map { it.toAlbumItem() },
                        moreEndpoint = null,
                    ),
                )
            }
            if (singles.isNotEmpty()) {
                add(
                    com.music.innertube.pages.ArtistSection(
                        title = "Singles",
                        items = singles.map { it.toAlbumItem() },
                        moreEndpoint = null,
                    ),
                )
            }
            if (relatedArtists.isNotEmpty()) {
                add(
                    com.music.innertube.pages.ArtistSection(
                        title = "Fans might also like",
                        items = relatedArtists.map { it.toArtistItem() },
                        moreEndpoint = null,
                    ),
                )
            }
        }
        return com.music.innertube.pages.ArtistPage(
            artist = ArtistItem(
                id = browseId,
                title = name,
                thumbnail = thumbnailUrl,
                channelId = browseId,
                shuffleEndpoint = shufflePlaylistId?.let { WatchEndpoint(playlistId = it) },
                radioEndpoint = radioPlaylistId?.let { WatchEndpoint(playlistId = it) },
            ),
            sections = sections,
            description = description,
            subscriberCountText = subscribers,
            monthlyListenerCount = views,
        )
    }
}

@Serializable
data class YtPlaylistDetailDto(
    val playlistId: String = "",
    val title: String = "",
    val author: YtArtistRefDto? = null,
    val description: String? = null,
    val trackCount: Int? = null,
    val durationText: String? = null,
    val thumbnailUrl: String? = null,
    val tracks: List<YtTrackSummaryDto> = emptyList(),
    val continuation: String? = null,
) {
    fun toPlaylistItem(): PlaylistItem = PlaylistItem(
        id = playlistId,
        title = title.ifBlank { "Playlist" },
        author = author?.takeIf { it.name.isNotBlank() }?.let { Artist(name = it.name, id = it.id) },
        songCountText = trackCount?.let { "$it songs" },
        thumbnail = thumbnailUrl ?: tracks.firstOrNull()?.thumbnailUrl,
        playEndpoint = WatchEndpoint(playlistId = playlistId),
        shuffleEndpoint = null,
        radioEndpoint = null,
    )
}

@Serializable
data class YtRadioResponseDto(
    val videoId: String = "",
    val playlistId: String? = null,
    val lyricsBrowseId: String? = null,
    val tracks: List<YtTrackSummaryDto> = emptyList(),
)

@Serializable
data class FastApiErrorEnvelope(
    val detail: String? = null,
)

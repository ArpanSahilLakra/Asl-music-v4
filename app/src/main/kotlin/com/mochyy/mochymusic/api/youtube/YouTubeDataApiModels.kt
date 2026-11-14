package com.mochyy.mochymusic.api.youtube

import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import kotlinx.serialization.Serializable

@Serializable
data class ThumbnailInfo(
    val url: String = "",
    val width: Int? = null,
    val height: Int? = null,
)

@Serializable
data class ThumbnailSet(
    val maxres: ThumbnailInfo? = null,
    val standard: ThumbnailInfo? = null,
    val high: ThumbnailInfo? = null,
    val medium: ThumbnailInfo? = null,
    val default: ThumbnailInfo? = null,
) {
    fun bestUrl(): String? =
        listOfNotNull(maxres, standard, high, medium, default)
            .firstOrNull { it.url.isNotBlank() }
            ?.url
}

@Serializable
data class ResourceId(
    val kind: String? = null,
    val videoId: String? = null,
    val playlistId: String? = null,
    val channelId: String? = null,
)

@Serializable
data class ResourceSnippet(
    val publishedAt: String? = null,
    val channelId: String? = null,
    val title: String = "",
    val description: String = "",
    val channelTitle: String? = null,
    val videoOwnerChannelTitle: String? = null,
    val videoOwnerChannelId: String? = null,
    val thumbnails: ThumbnailSet? = null,
    val resourceId: ResourceId? = null,
)

@Serializable
data class SearchListResponse(
    val nextPageToken: String? = null,
    val items: List<SearchItem> = emptyList(),
)

@Serializable
data class SearchItem(
    val id: ResourceId = ResourceId(),
    val snippet: ResourceSnippet = ResourceSnippet(),
)

@Serializable
data class VideoListResponse(
    val items: List<VideoItem> = emptyList(),
)

@Serializable
data class VideoItem(
    val id: String = "",
    val snippet: ResourceSnippet = ResourceSnippet(),
    val contentDetails: VideoContentDetails? = null,
)

@Serializable
data class VideoContentDetails(
    val duration: String? = null,
)

@Serializable
data class PlaylistListResponse(
    val items: List<PlaylistApiItem> = emptyList(),
)

@Serializable
data class PlaylistApiItem(
    val id: String = "",
    val snippet: ResourceSnippet = ResourceSnippet(),
    val contentDetails: PlaylistContentDetails? = null,
)

@Serializable
data class PlaylistContentDetails(
    val itemCount: Int? = null,
)

@Serializable
data class PlaylistItemListResponse(
    val nextPageToken: String? = null,
    val items: List<PlaylistItemResource> = emptyList(),
)

@Serializable
data class PlaylistItemResource(
    val id: String = "",
    val snippet: ResourceSnippet = ResourceSnippet(),
    val contentDetails: PlaylistItemContentDetails? = null,
)

@Serializable
data class PlaylistItemContentDetails(
    val videoId: String? = null,
)

@Serializable
data class ChannelListResponse(
    val items: List<ChannelItem> = emptyList(),
)

@Serializable
data class ChannelItem(
    val id: String = "",
    val snippet: ResourceSnippet = ResourceSnippet(),
    val contentDetails: ChannelContentDetails? = null,
)

@Serializable
data class ChannelContentDetails(
    val relatedPlaylists: RelatedPlaylists? = null,
)

@Serializable
data class RelatedPlaylists(
    val uploads: String? = null,
    val likes: String? = null,
)

@Serializable
data class YouTubeApiErrorEnvelope(
    val error: YouTubeApiErrorBody? = null,
)

@Serializable
data class YouTubeApiErrorBody(
    val code: Int = 0,
    val message: String = "",
    val errors: List<YouTubeApiErrorDetail> = emptyList(),
)

@Serializable
data class YouTubeApiErrorDetail(
    val domain: String? = null,
    val reason: String? = null,
    val message: String? = null,
)

sealed interface YouTubeDataApiResult<out T> {
    data class Success<T>(val data: T) : YouTubeDataApiResult<T>

    sealed interface Failure : YouTubeDataApiResult<Nothing> {
        val message: String
    }

    data class MissingKey(
        override val message: String = "YouTube Data API key is not configured. Add YOUTUBE_API_KEY in the Secrets panel in AI Studio.",
    ) : Failure

    data class QuotaExceeded(
        override val message: String = "YouTube Data API daily quota exceeded. Try again after quota resets.",
    ) : Failure

    data class KeyRestricted(
        override val message: String,
    ) : Failure

    data class NotFound(
        override val message: String,
    ) : Failure

    data class NetworkError(
        override val message: String,
    ) : Failure

    data class ApiError(
        val httpCode: Int,
        val reason: String?,
        override val message: String,
    ) : Failure
}

data class DataApiSearchPage(
    val songs: List<SongItem>,
    val nextPageToken: String? = null,
)

data class DataApiPlaylistPage(
    val playlist: PlaylistItem?,
    val songs: List<SongItem>,
    val nextPageToken: String? = null,
    val resolvedPlaylistId: String,
)

private val ISO_DURATION_REGEX =
    Regex("""^P(?:(\d+)D)?(?:T(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?)?$""")

fun parseIso8601DurationSeconds(iso: String?): Int? {
    if (iso.isNullOrBlank()) return null
    val match = ISO_DURATION_REGEX.matchEntire(iso.trim()) ?: return null
    val days = match.groupValues[1].toIntOrNull() ?: 0
    val hours = match.groupValues[2].toIntOrNull() ?: 0
    val minutes = match.groupValues[3].toIntOrNull() ?: 0
    val seconds = match.groupValues[4].toIntOrNull() ?: 0
    val total = days * 86_400 + hours * 3_600 + minutes * 60 + seconds
    return total.takeIf { it > 0 }
}

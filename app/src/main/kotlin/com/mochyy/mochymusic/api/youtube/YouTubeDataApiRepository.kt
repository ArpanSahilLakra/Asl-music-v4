package com.mochyy.mochymusic.api.youtube

import android.net.Uri
import com.music.innertube.models.Artist
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YouTubeDataApiRepository @Inject constructor(
    private val client: YouTubeDataApiClient,
) {
    fun isKeyConfigured(): Boolean = client.isKeyConfigured()

    fun packageName(): String = client.packageName()

    fun signingSha1Formatted(): String? = client.signingSha1Formatted()

    suspend fun searchMusicVideos(
        query: String,
        pageToken: String? = null,
        maxResults: Int = 25,
    ): YouTubeDataApiResult<DataApiSearchPage> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            return YouTubeDataApiResult.Success(DataApiSearchPage(emptyList(), null))
        }

        val capped = maxResults.coerceIn(1, 50)
        var parsedResult = executeSearch(
            query = trimmed,
            pageToken = pageToken,
            maxResults = capped,
            musicCategoryOnly = true,
        )

        if (parsedResult is YouTubeDataApiResult.Success &&
            parsedResult.data.items.isEmpty() &&
            pageToken == null
        ) {
            parsedResult = executeSearch(
                query = trimmed,
                pageToken = null,
                maxResults = capped,
                musicCategoryOnly = false,
            )
        }

        return when (parsedResult) {
            is YouTubeDataApiResult.Failure -> parsedResult
            is YouTubeDataApiResult.Success -> {
                val searchResponse = parsedResult.data
                val baseSongs = searchResponse.items.mapNotNull { item ->
                    val videoId = item.id.videoId?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    snippetToSongItem(videoId = videoId, snippet = item.snippet, duration = null)
                }
                val enriched = hydrateDurations(baseSongs)
                YouTubeDataApiResult.Success(
                    DataApiSearchPage(
                        songs = enriched,
                        nextPageToken = searchResponse.nextPageToken,
                    ),
                )
            }
        }
    }

    suspend fun fetchVideoDetails(
        videoIds: List<String>,
    ): YouTubeDataApiResult<List<SongItem>> {
        val cleanIds = videoIds.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (cleanIds.isEmpty()) {
            return YouTubeDataApiResult.Success(emptyList())
        }

        val detailedById = LinkedHashMap<String, SongItem>(cleanIds.size)
        for (chunk in cleanIds.chunked(50)) {
            val rawResult = client.get(
                endpoint = "videos",
                params = mapOf(
                    "part" to "snippet,contentDetails",
                    "id" to chunk.joinToString(","),
                ),
            )
            when (rawResult) {
                is YouTubeDataApiResult.Failure -> return rawResult
                is YouTubeDataApiResult.Success -> {
                    val decoded = runCatching {
                        client.json.decodeFromString(VideoListResponse.serializer(), rawResult.data)
                    }.getOrElse { err ->
                        return YouTubeDataApiResult.ApiError(
                            httpCode = 200,
                            reason = "parseError",
                            message = err.localizedMessage ?: "Failed to parse YouTube videos response.",
                        )
                    }
                    decoded.items.forEach { video ->
                        if (video.id.isNotBlank()) {
                            val durationSec = parseIso8601DurationSeconds(video.contentDetails?.duration)
                            detailedById[video.id] = snippetToSongItem(
                                videoId = video.id,
                                snippet = video.snippet,
                                duration = durationSec,
                            )
                        }
                    }
                }
            }
        }

        return YouTubeDataApiResult.Success(
            cleanIds.mapNotNull { detailedById[it] },
        )
    }

    suspend fun fetchPlaylistDetails(
        playlistInput: String,
    ): YouTubeDataApiResult<PlaylistItem> {
        val playlistId = extractPlaylistId(playlistInput)
            ?: return YouTubeDataApiResult.NotFound("Enter a valid YouTube playlist ID or URL.")

        val rawResult = client.get(
            endpoint = "playlists",
            params = mapOf(
                "part" to "snippet,contentDetails",
                "id" to playlistId,
            ),
        )

        return when (rawResult) {
            is YouTubeDataApiResult.Failure -> rawResult
            is YouTubeDataApiResult.Success -> {
                val decoded = runCatching {
                    client.json.decodeFromString(PlaylistListResponse.serializer(), rawResult.data)
                }.getOrElse { err ->
                    return YouTubeDataApiResult.ApiError(
                        httpCode = 200,
                        reason = "parseError",
                        message = err.localizedMessage ?: "Failed to parse YouTube playlist response.",
                    )
                }
                val item = decoded.items.firstOrNull()
                    ?: return YouTubeDataApiResult.NotFound("Playlist \"$playlistId\" was not found.")

                val count = item.contentDetails?.itemCount
                YouTubeDataApiResult.Success(
                    PlaylistItem(
                        id = item.id.ifBlank { playlistId },
                        title = decodeHtmlEntities(item.snippet.title).ifBlank { "YouTube Playlist" },
                        author = item.snippet.channelTitle?.takeIf { it.isNotBlank() }?.let { authorName ->
                            Artist(
                                name = decodeHtmlEntities(authorName),
                                id = item.snippet.channelId,
                            )
                        },
                        songCountText = count?.let { "$it tracks" },
                        thumbnail = item.snippet.thumbnails?.bestUrl(),
                        playEndpoint = null,
                        shuffleEndpoint = null,
                        radioEndpoint = null,
                    ),
                )
            }
        }
    }

    suspend fun fetchPlaylistItems(
        playlistInput: String,
        pageToken: String? = null,
        maxResults: Int = 50,
    ): YouTubeDataApiResult<DataApiPlaylistPage> {
        val playlistId = extractPlaylistId(playlistInput)
            ?: return YouTubeDataApiResult.NotFound("Enter a valid YouTube playlist ID or URL.")

        val params = linkedMapOf(
            "part" to "snippet,contentDetails",
            "playlistId" to playlistId,
            "maxResults" to maxResults.coerceIn(1, 50).toString(),
        )
        if (!pageToken.isNullOrBlank()) {
            params["pageToken"] = pageToken
        }

        val itemsResult = client.get("playlistItems", params)
        return when (itemsResult) {
            is YouTubeDataApiResult.Failure -> itemsResult
            is YouTubeDataApiResult.Success -> {
                val decoded = runCatching {
                    client.json.decodeFromString(PlaylistItemListResponse.serializer(), itemsResult.data)
                }.getOrElse { err ->
                    return YouTubeDataApiResult.ApiError(
                        httpCode = 200,
                        reason = "parseError",
                        message = err.localizedMessage ?: "Failed to parse playlist items.",
                    )
                }

                val baseSongs = decoded.items.mapNotNull { entry ->
                    val videoId = entry.contentDetails?.videoId
                        ?: entry.snippet.resourceId?.videoId
                        ?: return@mapNotNull null
                    val rawTitle = entry.snippet.title.trim()
                    if (videoId.isBlank() ||
                        rawTitle.equals("Deleted video", ignoreCase = true) ||
                        rawTitle.equals("Private video", ignoreCase = true)
                    ) {
                        return@mapNotNull null
                    }
                    snippetToSongItem(
                        videoId = videoId,
                        snippet = entry.snippet,
                        duration = null,
                    )
                }

                val enriched = hydrateDurations(baseSongs)
                val playlistMeta = if (pageToken.isNullOrBlank()) {
                    (fetchPlaylistDetails(playlistId) as? YouTubeDataApiResult.Success)?.data
                } else {
                    null
                }

                YouTubeDataApiResult.Success(
                    DataApiPlaylistPage(
                        playlist = playlistMeta,
                        songs = enriched,
                        nextPageToken = decoded.nextPageToken,
                        resolvedPlaylistId = playlistId,
                    ),
                )
            }
        }
    }

    suspend fun fetchChannelUploads(
        channelInput: String,
        pageToken: String? = null,
        maxResults: Int = 50,
    ): YouTubeDataApiResult<DataApiPlaylistPage> {
        val resolved = resolveChannelQuery(channelInput)
            ?: return YouTubeDataApiResult.NotFound("Enter a YouTube channel ID (UC...), @handle, or URL.")

        val channelParams = linkedMapOf("part" to "snippet,contentDetails")
        when (resolved) {
            is ChannelLookup.ById -> channelParams["id"] = resolved.channelId
            is ChannelLookup.ByHandle -> channelParams["forHandle"] = resolved.handle
        }

        var channelResult = queryChannel(channelParams)
        if (channelResult is YouTubeDataApiResult.NotFound && resolved is ChannelLookup.ByHandle) {
            channelResult = queryChannel(
                mapOf(
                    "part" to "snippet,contentDetails",
                    "forUsername" to resolved.handle.removePrefix("@"),
                ),
            )
        }

        return when (channelResult) {
            is YouTubeDataApiResult.Failure -> channelResult
            is YouTubeDataApiResult.Success -> {
                val channel = channelResult.data
                val uploadsPlaylistId = channel.contentDetails?.relatedPlaylists?.uploads
                    ?.takeIf { it.isNotBlank() }
                    ?: return YouTubeDataApiResult.NotFound(
                        "No public uploads playlist was found for this channel.",
                    )

                when (val page = fetchPlaylistItems(uploadsPlaylistId, pageToken, maxResults)) {
                    is YouTubeDataApiResult.Failure -> page
                    is YouTubeDataApiResult.Success -> {
                        val channelTitle = decodeHtmlEntities(channel.snippet.title)
                        val enrichedHeader = page.data.playlist?.copy(
                            title = if (channelTitle.isNotBlank()) "$channelTitle · Uploads" else page.data.playlist.title,
                            author = Artist(
                                name = channelTitle.ifBlank { page.data.playlist.author?.name ?: "Channel" },
                                id = channel.id,
                            ),
                            thumbnail = channel.snippet.thumbnails?.bestUrl() ?: page.data.playlist.thumbnail,
                        ) ?: PlaylistItem(
                            id = uploadsPlaylistId,
                            title = if (channelTitle.isNotBlank()) "$channelTitle · Uploads" else "Channel Uploads",
                            author = Artist(name = channelTitle.ifBlank { "YouTube Channel" }, id = channel.id),
                            songCountText = "${page.data.songs.size} tracks",
                            thumbnail = channel.snippet.thumbnails?.bestUrl(),
                            playEndpoint = null,
                            shuffleEndpoint = null,
                            radioEndpoint = null,
                        )
                        YouTubeDataApiResult.Success(
                            page.data.copy(playlist = enrichedHeader),
                        )
                    }
                }
            }
        }
    }

    private suspend fun executeSearch(
        query: String,
        pageToken: String?,
        maxResults: Int,
        musicCategoryOnly: Boolean,
    ): YouTubeDataApiResult<SearchListResponse> {
        val params = linkedMapOf(
            "part" to "snippet",
            "type" to "video",
            "q" to query,
            "maxResults" to maxResults.toString(),
        )
        if (musicCategoryOnly) {
            params["videoCategoryId"] = "10"
        }
        if (!pageToken.isNullOrBlank()) {
            params["pageToken"] = pageToken
        }

        return when (val raw = client.get("search", params)) {
            is YouTubeDataApiResult.Failure -> raw
            is YouTubeDataApiResult.Success -> {
                val decoded = runCatching {
                    client.json.decodeFromString(SearchListResponse.serializer(), raw.data)
                }.getOrElse { err ->
                    return YouTubeDataApiResult.ApiError(
                        httpCode = 200,
                        reason = "parseError",
                        message = err.localizedMessage ?: "Failed to parse YouTube search response.",
                    )
                }
                YouTubeDataApiResult.Success(decoded)
            }
        }
    }

    private suspend fun queryChannel(
        params: Map<String, String>,
    ): YouTubeDataApiResult<ChannelItem> {
        return when (val raw = client.get("channels", params)) {
            is YouTubeDataApiResult.Failure -> raw
            is YouTubeDataApiResult.Success -> {
                val decoded = runCatching {
                    client.json.decodeFromString(ChannelListResponse.serializer(), raw.data)
                }.getOrElse { err ->
                    return YouTubeDataApiResult.ApiError(
                        httpCode = 200,
                        reason = "parseError",
                        message = err.localizedMessage ?: "Failed to parse channel response.",
                    )
                }
                val item = decoded.items.firstOrNull()
                    ?: return YouTubeDataApiResult.NotFound("YouTube channel was not found.")
                YouTubeDataApiResult.Success(item)
            }
        }
    }

    private suspend fun hydrateDurations(baseSongs: List<SongItem>): List<SongItem> {
        if (baseSongs.isEmpty()) return emptyList()
        val detailsResult = fetchVideoDetails(baseSongs.map { it.id })
        val byId = (detailsResult as? YouTubeDataApiResult.Success)
            ?.data
            ?.associateBy { it.id }
            ?: return baseSongs

        return baseSongs.map { song ->
            val detailed = byId[song.id] ?: return@map song
            song.copy(
                duration = detailed.duration ?: song.duration,
                thumbnail = detailed.thumbnail.ifBlank { song.thumbnail },
                artists = if (detailed.artists.isNotEmpty()) detailed.artists else song.artists,
                title = detailed.title.ifBlank { song.title },
            )
        }
    }

    private fun snippetToSongItem(
        videoId: String,
        snippet: ResourceSnippet,
        duration: Int?,
    ): SongItem {
        val rawArtist = snippet.videoOwnerChannelTitle
            ?.takeIf { it.isNotBlank() }
            ?: snippet.channelTitle?.takeIf { it.isNotBlank() }
            ?: "YouTube"
        val cleanArtist = decodeHtmlEntities(rawArtist)
            .removeSuffix(" - Topic")
            .trim()
            .ifBlank { "YouTube" }
        val artistId = snippet.videoOwnerChannelId ?: snippet.channelId
        val thumb = snippet.thumbnails?.bestUrl()
            ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

        return SongItem(
            id = videoId,
            title = decodeHtmlEntities(snippet.title).ifBlank { videoId },
            artists = listOf(Artist(name = cleanArtist, id = artistId)),
            duration = duration,
            thumbnail = thumb,
        )
    }

    fun extractPlaylistId(rawInput: String): String? {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return null

        if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            val uri = runCatching { Uri.parse(trimmed) }.getOrNull()
            val listParam = uri?.getQueryParameter("list")?.trim()
            if (!listParam.isNullOrEmpty()) {
                return normalizePlaylistId(listParam)
            }
            return null
        }

        return normalizePlaylistId(trimmed)
    }

    private fun normalizePlaylistId(candidate: String): String? {
        val clean = candidate.trim()
        if (clean.isEmpty()) return null
        // YouTube Music browse IDs prefix real playlist IDs with "VL"
        return if (clean.startsWith("VL") && clean.length > 4) {
            clean.removePrefix("VL")
        } else {
            clean
        }
    }

    private sealed interface ChannelLookup {
        data class ById(val channelId: String) : ChannelLookup
        data class ByHandle(val handle: String) : ChannelLookup
    }

    private fun resolveChannelQuery(rawInput: String): ChannelLookup? {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return null

        if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            val uri = runCatching { Uri.parse(trimmed) }.getOrNull() ?: return null
            val segments = uri.pathSegments.orEmpty()
            val channelIdx = segments.indexOfFirst { it.equals("channel", ignoreCase = true) }
            if (channelIdx >= 0 && channelIdx + 1 < segments.size) {
                return ChannelLookup.ById(segments[channelIdx + 1])
            }
            val handleSegment = segments.firstOrNull { it.startsWith("@") }
            if (handleSegment != null) {
                return ChannelLookup.ByHandle(handleSegment)
            }
            val last = segments.lastOrNull()?.takeIf { it.isNotBlank() } ?: return null
            return if (last.startsWith("UC") && last.length >= 20) {
                ChannelLookup.ById(last)
            } else {
                ChannelLookup.ByHandle("@${last.removePrefix("@")}")
            }
        }

        return if (trimmed.startsWith("UC") && trimmed.length >= 20) {
            ChannelLookup.ById(trimmed)
        } else {
            ChannelLookup.ByHandle("@${trimmed.removePrefix("@")}")
        }
    }

    private fun decodeHtmlEntities(input: String): String {
        if (!input.contains('&')) return input
        return input
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&#x27;", "'")
            .replace("&#x2F;", "/")
    }
}

// OnlineSearchViewModel.kt

package com.mochyy.mochymusic.viewmodels

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.innertube.YouTube
import com.music.innertube.YouTube.SearchFilter.Companion.FILTER_ALBUM
import com.music.innertube.YouTube.SearchFilter.Companion.FILTER_ARTIST
import com.music.innertube.YouTube.SearchFilter.Companion.FILTER_COMMUNITY_PLAYLIST
import com.music.innertube.YouTube.SearchFilter.Companion.FILTER_FEATURED_PLAYLIST
import com.music.innertube.YouTube.SearchFilter.Companion.FILTER_SONG
import com.music.innertube.YouTube.SearchFilter.Companion.FILTER_VIDEO
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem
import com.music.innertube.models.filterExplicit
import com.music.innertube.models.filterVideoSongs
import com.music.innertube.models.filterYoutubeShorts
import com.music.innertube.pages.SearchSummary
import com.music.innertube.pages.SearchSummaryPage
import com.mochyy.mochymusic.api.youtube.YouTubeDataApiRepository
import com.mochyy.mochymusic.api.youtube.YouTubeDataApiResult
import com.mochyy.mochymusic.api.ytmusic.DiscoveryResult
import com.mochyy.mochymusic.api.ytmusic.YtMusicBackendClient
import com.mochyy.mochymusic.constants.HideExplicitKey
import com.mochyy.mochymusic.constants.HideVideoSongsKey
import com.mochyy.mochymusic.constants.HideYoutubeShortsKey
import com.mochyy.mochymusic.constants.PreferYouTubeDataApiSearchKey
import com.mochyy.mochymusic.constants.UseYouTubeDataApiFallbackKey
import com.mochyy.mochymusic.models.ItemsPage
import com.mochyy.mochymusic.utils.dataStore
import com.mochyy.mochymusic.utils.get
import com.mochyy.mochymusic.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.net.URLDecoder
import javax.inject.Inject

private const val DATA_API_CONTINUATION_PREFIX = "dataapi:"
private const val FAST_API_CONTINUATION_PREFIX = "fastapi:"

@HiltViewModel
class OnlineSearchViewModel
@Inject
constructor(
    @ApplicationContext val context: Context,
    savedStateHandle: SavedStateHandle,
    private val dataApiRepository: YouTubeDataApiRepository,
    private val ytMusicBackendClient: YtMusicBackendClient,
) : ViewModel() {
    val query = try {
        URLDecoder.decode(savedStateHandle.get<String>("query")!!, "UTF-8")
    } catch (e: IllegalArgumentException) {
        savedStateHandle.get<String>("query")!!
    }
    val filter = MutableStateFlow<YouTube.SearchFilter?>(null)
    var summaryPage by mutableStateOf<SearchSummaryPage?>(null)
    val viewStateMap = mutableStateMapOf<String, ItemsPage?>()

    init {
        viewModelScope.launch {
            filter.collect { activeFilter ->
                val useFallback = context.dataStore.get(UseYouTubeDataApiFallbackKey, true)
                val preferDataApi = context.dataStore.get(PreferYouTubeDataApiSearchKey, false)

                if (activeFilter == null) {
                    if (summaryPage == null) {
                        var loaded = loadSummaryFromFastApi()
                        if (!loaded && preferDataApi && dataApiRepository.isKeyConfigured()) {
                            loaded = loadSummaryFromDataApi()
                        }
                        if (!loaded) {
                            YouTube
                                .searchSummary(query)
                                .onSuccess { page ->
                                    val filtered = applySummaryFilters(page)
                                    val hasItems = filtered.summaries.any { it.items.isNotEmpty() }
                                    if (hasItems) {
                                        summaryPage = filtered
                                        loaded = true
                                    }
                                }.onFailure {
                                    reportException(it)
                                }
                        }
                        if (!loaded && useFallback && dataApiRepository.isKeyConfigured()) {
                            loaded = loadSummaryFromDataApi()
                        }
                        if (!loaded && summaryPage == null) {
                            summaryPage = SearchSummaryPage(emptyList())
                        }
                    }
                } else {
                    if (viewStateMap[activeFilter.value] == null) {
                        val supportsDataApi =
                            activeFilter.value == FILTER_SONG.value ||
                                activeFilter.value == FILTER_VIDEO.value

                        var loaded = loadFilterFromFastApi(activeFilter)
                        if (!loaded && preferDataApi && supportsDataApi && dataApiRepository.isKeyConfigured()) {
                            loaded = loadFilterFromDataApi(activeFilter.value)
                        }
                        if (!loaded) {
                            YouTube
                                .search(query, activeFilter)
                                .onSuccess { result ->
                                    val filteredItems = applyItemFilters(result.items.distinctBy { it.id })
                                    if (filteredItems.isNotEmpty() || !supportsDataApi || !useFallback) {
                                        viewStateMap[activeFilter.value] = ItemsPage(
                                            items = filteredItems,
                                            continuation = result.continuation,
                                        )
                                        loaded = true
                                    }
                                }.onFailure {
                                    reportException(it)
                                }
                        }
                        if (!loaded && supportsDataApi && useFallback && dataApiRepository.isKeyConfigured()) {
                            loaded = loadFilterFromDataApi(activeFilter.value)
                        }
                        if (!loaded && viewStateMap[activeFilter.value] == null) {
                            viewStateMap[activeFilter.value] = ItemsPage(emptyList(), null)
                        }
                    }
                }
            }
        }
    }

    fun loadMore() {
        val activeFilterObj = filter.value ?: return
        val activeFilter = activeFilterObj.value
        viewModelScope.launch {
            val viewState = viewStateMap[activeFilter] ?: return@launch
            val continuation = viewState.continuation ?: return@launch
            if (continuation.startsWith(FAST_API_CONTINUATION_PREFIX)) {
                val offset = continuation.removePrefix(FAST_API_CONTINUATION_PREFIX).toIntOrNull() ?: return@launch
                val backendFilter = mapFilterToFastApiName(activeFilterObj)
                val result = ytMusicBackendClient.search(query, filter = backendFilter, limit = 20, offset = offset)
                if (result is DiscoveryResult.Success) {
                    val mapped = mapFastApiItems(result.data, backendFilter)
                    val newItems = applyItemFilters(mapped)
                    viewStateMap[activeFilter] = ItemsPage(
                        items = (viewState.items + newItems).distinctBy { it.id },
                        continuation = result.data.continuation?.let { "$FAST_API_CONTINUATION_PREFIX$it" },
                    )
                }
                return@launch
            }

            if (continuation.startsWith(DATA_API_CONTINUATION_PREFIX)) {
                val token = continuation.removePrefix(DATA_API_CONTINUATION_PREFIX)
                val result = dataApiRepository.searchMusicVideos(query, pageToken = token)
                if (result is YouTubeDataApiResult.Success) {
                    val newItems = applyItemFilters(result.data.songs)
                    viewStateMap[activeFilter] = ItemsPage(
                        items = (viewState.items + newItems).distinctBy { it.id },
                        continuation = result.data.nextPageToken?.let { "$DATA_API_CONTINUATION_PREFIX$it" },
                    )
                }
                return@launch
            }

            val searchResult =
                YouTube.searchContinuation(continuation).getOrNull() ?: return@launch
            val newItems = applyItemFilters(searchResult.items)
            viewStateMap[activeFilter] = ItemsPage(
                (viewState.items + newItems).distinctBy { it.id },
                searchResult.continuation,
            )
        }
    }

    private suspend fun loadSummaryFromFastApi(): Boolean {
        return when (val res = ytMusicBackendClient.search(query, filter = null, limit = 25, offset = 0)) {
            is DiscoveryResult.Success -> {
                val data = res.data
                val sections = buildList {
                    val songs = applySongFilters(data.items.map { it.toSongItem() })
                    if (songs.isNotEmpty()) {
                        add(SearchSummary("Songs", songs))
                    }
                    val albums = applyItemFilters(data.albums.map { it.toAlbumItem() })
                    if (albums.isNotEmpty()) {
                        add(SearchSummary("Albums", albums))
                    }
                    val artists = data.artists.map { it.toArtistItem() }
                    if (artists.isNotEmpty()) {
                        add(SearchSummary("Artists", artists))
                    }
                    val playlists = data.playlists.map { it.toPlaylistItem() }
                    if (playlists.isNotEmpty()) {
                        add(SearchSummary("Playlists", playlists))
                    }
                }
                if (sections.isNotEmpty()) {
                    summaryPage = SearchSummaryPage(sections)
                    true
                } else {
                    false
                }
            }
            is DiscoveryResult.Error -> false
        }
    }

    private suspend fun loadFilterFromFastApi(searchFilter: YouTube.SearchFilter): Boolean {
        val backendFilter = mapFilterToFastApiName(searchFilter)
        return when (val res = ytMusicBackendClient.search(query, filter = backendFilter, limit = 20, offset = 0)) {
            is DiscoveryResult.Success -> {
                val items = applyItemFilters(mapFastApiItems(res.data, backendFilter).distinctBy { it.id })
                if (items.isNotEmpty()) {
                    viewStateMap[searchFilter.value] = ItemsPage(
                        items = items,
                        continuation = res.data.continuation?.let { "$FAST_API_CONTINUATION_PREFIX$it" },
                    )
                    true
                } else {
                    false
                }
            }
            is DiscoveryResult.Error -> false
        }
    }

    private fun mapFilterToFastApiName(searchFilter: YouTube.SearchFilter): String = when (searchFilter.value) {
        FILTER_SONG.value -> "songs"
        FILTER_VIDEO.value -> "videos"
        FILTER_ALBUM.value -> "albums"
        FILTER_ARTIST.value -> "artists"
        FILTER_COMMUNITY_PLAYLIST.value -> "community_playlists"
        FILTER_FEATURED_PLAYLIST.value -> "featured_playlists"
        else -> "songs"
    }

    private fun mapFastApiItems(
        data: com.mochyy.mochymusic.api.ytmusic.YtSearchResponseDto,
        backendFilter: String?,
    ): List<YTItem> = when (backendFilter) {
        "albums" -> data.albums.map { it.toAlbumItem() }
        "artists" -> data.artists.map { it.toArtistItem() }
        "community_playlists", "featured_playlists", "playlists" -> data.playlists.map { it.toPlaylistItem() }
        else -> data.items.map { it.toSongItem() }
    }

    private suspend fun loadSummaryFromDataApi(): Boolean {
        return when (val result = dataApiRepository.searchMusicVideos(query)) {
            is YouTubeDataApiResult.Success -> {
                val filteredSongs = applySongFilters(result.data.songs)
                if (filteredSongs.isNotEmpty()) {
                    summaryPage = SearchSummaryPage(
                        listOf(SearchSummary("Music videos", filteredSongs)),
                    )
                    true
                } else {
                    false
                }
            }
            is YouTubeDataApiResult.Failure -> false
        }
    }

    private suspend fun loadFilterFromDataApi(filterKey: String): Boolean {
        return when (val result = dataApiRepository.searchMusicVideos(query)) {
            is YouTubeDataApiResult.Success -> {
                val filteredSongs = applySongFilters(result.data.songs)
                viewStateMap[filterKey] = ItemsPage(
                    items = filteredSongs,
                    continuation = result.data.nextPageToken?.let { "$DATA_API_CONTINUATION_PREFIX$it" },
                )
                filteredSongs.isNotEmpty()
            }
            is YouTubeDataApiResult.Failure -> false
        }
    }

    private fun applySummaryFilters(page: SearchSummaryPage): SearchSummaryPage {
        val hideExplicit = context.dataStore.get(HideExplicitKey, false)
        val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)
        val hideYoutubeShorts = context.dataStore.get(HideYoutubeShortsKey, true)
        return page
            .filterExplicit(hideExplicit)
            .filterVideoSongs(hideVideoSongs)
            .filterYoutubeShorts(hideYoutubeShorts)
    }

    private fun <T : YTItem> applyItemFilters(items: List<T>): List<T> {
        val hideExplicit = context.dataStore.get(HideExplicitKey, false)
        val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)
        val hideYoutubeShorts = context.dataStore.get(HideYoutubeShortsKey, true)
        return items
            .filterExplicit(hideExplicit)
            .filterVideoSongs(hideVideoSongs)
            .filterYoutubeShorts(hideYoutubeShorts)
    }

    private fun applySongFilters(songs: List<SongItem>): List<SongItem> =
        applyItemFilters(songs.distinctBy { it.id })
}

// OnlineSearchSuggestionViewModel.kt

package com.mochyy.mochymusic.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.innertube.YouTube
import com.music.innertube.models.WatchEndpoint
import com.music.innertube.models.YTItem
import com.music.innertube.models.filterExplicit
import com.music.innertube.models.filterVideoSongs
import com.music.innertube.utils.YouTubeUrlParser
import com.mochyy.mochymusic.api.ytmusic.DiscoveryResult
import com.mochyy.mochymusic.api.ytmusic.YtMusicBackendClient
import com.mochyy.mochymusic.constants.HideExplicitKey
import com.mochyy.mochymusic.constants.HideVideoSongsKey
import com.mochyy.mochymusic.db.MusicDatabase
import com.mochyy.mochymusic.db.entities.SearchHistory
import com.mochyy.mochymusic.utils.dataStore
import com.mochyy.mochymusic.utils.get
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class OnlineSearchSuggestionViewModel
@Inject
constructor(
    @ApplicationContext val context: Context,
    database: MusicDatabase,
    private val ytMusicBackendClient: YtMusicBackendClient,
) : ViewModel() {
    val query = MutableStateFlow("")
    private val _viewState = MutableStateFlow(SearchSuggestionViewState())
    val viewState = _viewState.asStateFlow()

    init {
        viewModelScope.launch {
            query
                .flatMapLatest { query ->
                    if (query.isEmpty()) {
                        database.searchHistory().map { history ->
                            SearchSuggestionViewState(
                                history = history,
                            )
                        }
                    } else if (com.mochyy.mochymusic.utils.OfflineMode.isOn) {
                        combine(
                            database.searchHistory(query).map { it.take(3) },
                            database.searchSongs(query, OfflinePreviewSize),
                            database.searchAlbums(query, OfflinePreviewSize),
                            database.searchArtists(query, OfflinePreviewSize),
                            database.searchPlaylists(query, OfflinePreviewSize),
                        ) { history, songs, albums, artists, playlists ->
                            SearchSuggestionViewState(
                                history = history,
                                items = com.mochyy.mochymusic.ui.screens.library.searchLibrary(
                                    query, songs, artists, albums, playlists,
                                ),
                            )
                        }
                    } else {
                        val parsedUrl = YouTubeUrlParser.parse(query)
                        val parsedItem = if (parsedUrl != null) fetchParsedUrlItem(parsedUrl) else null

                        val fastApiSearch = if (parsedUrl == null) {
                            (ytMusicBackendClient.search(query, limit = 6) as? DiscoveryResult.Success)?.data
                        } else null

                        val innerTubeResult = if (parsedUrl == null && fastApiSearch == null) {
                            YouTube.searchSuggestions(query).getOrNull()
                        } else null

                        val hideExplicit = context.dataStore.get(HideExplicitKey, false)
                        val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)

                        val suggestionQueries = fastApiSearch?.suggestions?.takeIf { it.isNotEmpty() }
                            ?: innerTubeResult?.queries.orEmpty()
                        val recommendedItems: List<YTItem> = if (fastApiSearch != null) {
                            fastApiSearch.items.take(4).map { it.toSongItem() } +
                                fastApiSearch.albums.take(2).map { it.toAlbumItem() } +
                                fastApiSearch.artists.take(2).map { it.toArtistItem() }
                        } else {
                            innerTubeResult?.recommendedItems.orEmpty()
                        }

                        database
                            .searchHistory(query)
                            .map { it.take(3) }
                            .map { history ->
                                SearchSuggestionViewState(
                                    history = history,
                                    suggestions = suggestionQueries.filter { suggestionQuery ->
                                        history.none { it.query == suggestionQuery }
                                    },
                                    items = listOfNotNull(parsedItem) +
                                        recommendedItems
                                            .distinctBy { it.id }
                                            .filter { it.id != parsedItem?.id }
                                            .filterExplicit(hideExplicit)
                                            .filterVideoSongs(hideVideoSongs),
                                    isFromLink = parsedUrl != null,
                                )
                            }
                    }
                }.collect {
                    _viewState.value = it
                }
        }
    }

    private suspend fun fetchParsedUrlItem(parsedUrl: YouTubeUrlParser.ParsedUrl): YTItem? {
        return try {
            when (parsedUrl) {
                is YouTubeUrlParser.ParsedUrl.Video -> {
                    (ytMusicBackendClient.getSong(parsedUrl.id) as? DiscoveryResult.Success)?.data?.toSongItem()
                        ?: YouTube.queue(listOf(parsedUrl.id)).getOrNull()?.firstOrNull()
                }

                is YouTubeUrlParser.ParsedUrl.Artist -> {
                    (ytMusicBackendClient.getArtist(parsedUrl.id) as? DiscoveryResult.Success)?.data?.toArtistPage()?.artist
                        ?: YouTube.artist(parsedUrl.id).getOrNull()?.artist
                }
            }
        } catch (e: Exception) {
            null
        }
    }
}

data class SearchSuggestionViewState(
    val history: List<SearchHistory> = emptyList(),
    val suggestions: List<String> = emptyList(),
    val items: List<YTItem> = emptyList(),
    val isFromLink: Boolean = false,
)

/** how many of each kind offline search shows */
private const val OfflinePreviewSize = 8

// OnlinePlaylistViewModel.kt

package com.mochyy.mochymusic.viewmodels

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.innertube.YouTube
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem
import com.music.innertube.models.filterVideoSongs
import com.mochyy.mochymusic.api.youtube.YouTubeDataApiRepository
import com.mochyy.mochymusic.api.youtube.YouTubeDataApiResult
import com.mochyy.mochymusic.api.ytmusic.DiscoveryResult
import com.mochyy.mochymusic.api.ytmusic.YtMusicBackendClient
import com.mochyy.mochymusic.constants.HideVideoSongsKey
import com.mochyy.mochymusic.constants.UseYouTubeDataApiFallbackKey
import com.mochyy.mochymusic.db.MusicDatabase
import com.mochyy.mochymusic.utils.dataStore
import com.mochyy.mochymusic.utils.get
import com.mochyy.mochymusic.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val PLAYLIST_DATA_API_PREFIX = "dataapi:"
private const val PLAYLIST_FAST_API_PREFIX = "fastapi:"

@HiltViewModel
class OnlinePlaylistViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
    database: MusicDatabase,
    private val dataApiRepository: YouTubeDataApiRepository,
    private val ytMusicBackendClient: YtMusicBackendClient,
) : ViewModel() {
    val playlistId = savedStateHandle.get<String>("playlistId")!!

    val playlist = MutableStateFlow<PlaylistItem?>(null)
    val playlistSongs = MutableStateFlow<List<SongItem>>(emptyList())
    val relatedItems = MutableStateFlow<List<YTItem>>(emptyList())

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore = _isLoadingMore.asStateFlow()

    val dbPlaylist = database.playlistByBrowseId(playlistId)
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    var continuation: String? = null
        private set

    private var proactiveLoadJob: Job? = null

    init {
        fetchInitialPlaylistData()
    }

    private fun fetchInitialPlaylistData() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _error.value = null
            continuation = null
            proactiveLoadJob?.cancel()

            var innerTubeFailure: Throwable? = null
            var loaded = false

            when (val fastApiRes = ytMusicBackendClient.getPlaylist(playlistId, limit = 50, offset = 0)) {
                is DiscoveryResult.Success -> {
                    val data = fastApiRes.data
                    playlist.value = data.toPlaylistItem()
                    playlistSongs.value = applySongFilters(data.tracks.map { it.toSongItem() })
                    continuation = data.continuation?.let { "$PLAYLIST_FAST_API_PREFIX$it" }
                    loaded = playlistSongs.value.isNotEmpty()
                }
                is DiscoveryResult.Error -> Unit
            }

            if (!loaded) {
                YouTube.playlist(playlistId)
                    .onSuccess { playlistPage ->
                        val filtered = applySongFilters(playlistPage.songs)
                        playlist.value = playlistPage.playlist
                        playlistSongs.value = filtered
                        relatedItems.value = playlistPage.related ?: emptyList()
                        continuation = playlistPage.songsContinuation
                        if (filtered.isNotEmpty() || playlistPage.songsContinuation != null) {
                            loaded = true
                        }
                    }.onFailure { throwable ->
                        innerTubeFailure = throwable
                        reportException(throwable)
                    }
            }

            val useFallback = context.dataStore.get(UseYouTubeDataApiFallbackKey, true)
            if (!loaded && useFallback && dataApiRepository.isKeyConfigured()) {
                when (val dataApiResult = dataApiRepository.fetchPlaylistItems(playlistId)) {
                    is YouTubeDataApiResult.Success -> {
                        val page = dataApiResult.data
                        if (page.playlist != null) {
                            playlist.value = page.playlist
                        } else if (playlist.value == null) {
                            playlist.value = PlaylistItem(
                                id = page.resolvedPlaylistId,
                                title = "YouTube Playlist",
                                author = null,
                                songCountText = "${page.songs.size} tracks",
                                thumbnail = page.songs.firstOrNull()?.thumbnail,
                                playEndpoint = null,
                                shuffleEndpoint = null,
                                radioEndpoint = null,
                            )
                        }
                        playlistSongs.value = applySongFilters(page.songs)
                        continuation = page.nextPageToken?.let { "$PLAYLIST_DATA_API_PREFIX$it" }
                        loaded = true
                    }
                    is YouTubeDataApiResult.Failure -> {
                        if (innerTubeFailure != null) {
                            _error.value = dataApiResult.message
                        }
                    }
                }
            }

            if (!loaded && _error.value == null && innerTubeFailure != null) {
                _error.value = innerTubeFailure?.message ?: "Failed to load playlist"
            }

            _isLoading.value = false
            if (continuation != null) {
                startProactiveBackgroundLoading()
            }
        }
    }

    private fun startProactiveBackgroundLoading() {
        proactiveLoadJob?.cancel()
        proactiveLoadJob = viewModelScope.launch(Dispatchers.IO) {
            var currentProactiveToken = continuation
            while (currentProactiveToken != null && isActive) {
                if (_isLoadingMore.value) {
                    break
                }

                if (currentProactiveToken.startsWith(PLAYLIST_FAST_API_PREFIX)) {
                    val offset = currentProactiveToken.removePrefix(PLAYLIST_FAST_API_PREFIX).toIntOrNull()
                    if (offset == null) {
                        currentProactiveToken = null
                        break
                    }
                    when (val result = ytMusicBackendClient.getPlaylist(playlistId, limit = 50, offset = offset)) {
                        is DiscoveryResult.Success -> {
                            val currentSongs = playlistSongs.value.toMutableList()
                            currentSongs.addAll(result.data.tracks.map { it.toSongItem() })
                            playlistSongs.value = applySongFilters(currentSongs)
                            currentProactiveToken = result.data.continuation?.let { "$PLAYLIST_FAST_API_PREFIX$it" }
                            this@OnlinePlaylistViewModel.continuation = currentProactiveToken
                        }
                        is DiscoveryResult.Error -> {
                            currentProactiveToken = null
                        }
                    }
                } else if (currentProactiveToken.startsWith(PLAYLIST_DATA_API_PREFIX)) {
                    val token = currentProactiveToken.removePrefix(PLAYLIST_DATA_API_PREFIX)
                    when (val result = dataApiRepository.fetchPlaylistItems(playlistId, pageToken = token)) {
                        is YouTubeDataApiResult.Success -> {
                            val currentSongs = playlistSongs.value.toMutableList()
                            currentSongs.addAll(result.data.songs)
                            playlistSongs.value = applySongFilters(currentSongs)
                            currentProactiveToken = result.data.nextPageToken?.let { "$PLAYLIST_DATA_API_PREFIX$it" }
                            this@OnlinePlaylistViewModel.continuation = currentProactiveToken
                        }
                        is YouTubeDataApiResult.Failure -> {
                            currentProactiveToken = null
                        }
                    }
                } else {
                    YouTube.playlistContinuation(currentProactiveToken)
                        .onSuccess { playlistContinuationPage ->
                            val currentSongs = playlistSongs.value.toMutableList()
                            currentSongs.addAll(playlistContinuationPage.songs)
                            playlistSongs.value = applySongFilters(currentSongs)
                            currentProactiveToken = playlistContinuationPage.continuation

                            this@OnlinePlaylistViewModel.continuation = currentProactiveToken
                        }.onFailure { throwable ->
                            reportException(throwable)
                            currentProactiveToken = null
                        }
                }
            }
        }
    }

    fun loadMoreSongs() {
        if (_isLoadingMore.value) return

        val tokenForManualLoad = continuation ?: return

        proactiveLoadJob?.cancel()
        _isLoadingMore.value = true

        viewModelScope.launch(Dispatchers.IO) {
            if (tokenForManualLoad.startsWith(PLAYLIST_FAST_API_PREFIX)) {
                val offset = tokenForManualLoad.removePrefix(PLAYLIST_FAST_API_PREFIX).toIntOrNull()
                if (offset != null) {
                    when (val result = ytMusicBackendClient.getPlaylist(playlistId, limit = 50, offset = offset)) {
                        is DiscoveryResult.Success -> {
                            val currentSongs = playlistSongs.value.toMutableList()
                            currentSongs.addAll(result.data.tracks.map { it.toSongItem() })
                            playlistSongs.value = applySongFilters(currentSongs)
                            continuation = result.data.continuation?.let { "$PLAYLIST_FAST_API_PREFIX$it" }
                        }
                        is DiscoveryResult.Error -> {
                            _error.value = result.message
                        }
                    }
                }
                _isLoadingMore.value = false
                if (continuation != null && isActive) {
                    startProactiveBackgroundLoading()
                }
                return@launch
            }

            if (tokenForManualLoad.startsWith(PLAYLIST_DATA_API_PREFIX)) {
                val token = tokenForManualLoad.removePrefix(PLAYLIST_DATA_API_PREFIX)
                when (val result = dataApiRepository.fetchPlaylistItems(playlistId, pageToken = token)) {
                    is YouTubeDataApiResult.Success -> {
                        val currentSongs = playlistSongs.value.toMutableList()
                        currentSongs.addAll(result.data.songs)
                        playlistSongs.value = applySongFilters(currentSongs)
                        continuation = result.data.nextPageToken?.let { "$PLAYLIST_DATA_API_PREFIX$it" }
                    }
                    is YouTubeDataApiResult.Failure -> {
                        _error.value = result.message
                    }
                }
                _isLoadingMore.value = false
                if (continuation != null && isActive) {
                    startProactiveBackgroundLoading()
                }
                return@launch
            }

            YouTube.playlistContinuation(tokenForManualLoad)
                .onSuccess { playlistContinuationPage ->
                    val currentSongs = playlistSongs.value.toMutableList()
                    currentSongs.addAll(playlistContinuationPage.songs)
                    playlistSongs.value = applySongFilters(currentSongs)
                    continuation = playlistContinuationPage.continuation
                }.onFailure { throwable ->
                    reportException(throwable)
                }.also {
                    _isLoadingMore.value = false

                    if (continuation != null && isActive) {
                        startProactiveBackgroundLoading()
                    }
                }
        }
    }

    fun retry() {
        proactiveLoadJob?.cancel()
        fetchInitialPlaylistData()
    }

    private fun applySongFilters(songs: List<SongItem>): List<SongItem> {
        val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)
        return songs
            .distinctBy { it.id }
            .filterVideoSongs(hideVideoSongs)
    }

    override fun onCleared() {
        super.onCleared()
        proactiveLoadJob?.cancel()
    }
}

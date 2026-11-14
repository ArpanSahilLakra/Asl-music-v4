package com.mochyy.mochymusic.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mochyy.mochymusic.api.youtube.YouTubeDataApiRepository
import com.mochyy.mochymusic.api.youtube.YouTubeDataApiResult
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class DataApiExplorerMode {
    SEARCH,
    PLAYLIST,
    CHANNEL,
}

data class YouTubeDataApiUiState(
    val isKeyConfigured: Boolean = false,
    val packageName: String = "",
    val sha1Fingerprint: String = "",
    val mode: DataApiExplorerMode = DataApiExplorerMode.SEARCH,
    val inputText: String = "",
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val playlistHeader: PlaylistItem? = null,
    val tracks: List<SongItem> = emptyList(),
    val nextPageToken: String? = null,
    val activeTargetId: String = "",
    val errorMessage: String? = null,
    val hasExecuted: Boolean = false,
)

@HiltViewModel
class YouTubeDataApiViewModel @Inject constructor(
    private val repository: YouTubeDataApiRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        YouTubeDataApiUiState(
            isKeyConfigured = repository.isKeyConfigured(),
            packageName = repository.packageName(),
            sha1Fingerprint = repository.signingSha1Formatted() ?: "Unavailable",
        ),
    )
    val uiState = _uiState.asStateFlow()

    fun setMode(mode: DataApiExplorerMode) {
        _uiState.update { state ->
            if (state.mode == mode) state
            else state.copy(
                mode = mode,
                playlistHeader = null,
                tracks = emptyList(),
                nextPageToken = null,
                activeTargetId = "",
                errorMessage = null,
                hasExecuted = false,
            )
        }
    }

    fun setInputText(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun execute() {
        val current = _uiState.value
        val trimmed = current.inputText.trim()
        if (trimmed.isEmpty() || current.isLoading) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    isLoadingMore = false,
                    errorMessage = null,
                    playlistHeader = null,
                    tracks = emptyList(),
                    nextPageToken = null,
                    hasExecuted = true,
                )
            }

            when (current.mode) {
                DataApiExplorerMode.SEARCH -> {
                    when (val result = repository.searchMusicVideos(trimmed)) {
                        is YouTubeDataApiResult.Success -> {
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    tracks = result.data.songs,
                                    nextPageToken = result.data.nextPageToken,
                                    activeTargetId = trimmed,
                                )
                            }
                        }
                        is YouTubeDataApiResult.Failure -> {
                            _uiState.update {
                                it.copy(isLoading = false, errorMessage = result.message)
                            }
                        }
                    }
                }

                DataApiExplorerMode.PLAYLIST -> {
                    when (val result = repository.fetchPlaylistItems(trimmed)) {
                        is YouTubeDataApiResult.Success -> {
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    playlistHeader = result.data.playlist,
                                    tracks = result.data.songs,
                                    nextPageToken = result.data.nextPageToken,
                                    activeTargetId = result.data.resolvedPlaylistId,
                                )
                            }
                        }
                        is YouTubeDataApiResult.Failure -> {
                            _uiState.update {
                                it.copy(isLoading = false, errorMessage = result.message)
                            }
                        }
                    }
                }

                DataApiExplorerMode.CHANNEL -> {
                    when (val result = repository.fetchChannelUploads(trimmed)) {
                        is YouTubeDataApiResult.Success -> {
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    playlistHeader = result.data.playlist,
                                    tracks = result.data.songs,
                                    nextPageToken = result.data.nextPageToken,
                                    activeTargetId = result.data.resolvedPlaylistId,
                                )
                            }
                        }
                        is YouTubeDataApiResult.Failure -> {
                            _uiState.update {
                                it.copy(isLoading = false, errorMessage = result.message)
                            }
                        }
                    }
                }
            }
        }
    }

    fun loadMore() {
        val current = _uiState.value
        val token = current.nextPageToken ?: return
        val target = current.activeTargetId.takeIf { it.isNotBlank() } ?: return
        if (current.isLoading || current.isLoadingMore) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true, errorMessage = null) }

            when (current.mode) {
                DataApiExplorerMode.SEARCH -> {
                    when (val result = repository.searchMusicVideos(target, pageToken = token)) {
                        is YouTubeDataApiResult.Success -> {
                            _uiState.update { state ->
                                state.copy(
                                    isLoadingMore = false,
                                    tracks = (state.tracks + result.data.songs).distinctBy { it.id },
                                    nextPageToken = result.data.nextPageToken,
                                )
                            }
                        }
                        is YouTubeDataApiResult.Failure -> {
                            _uiState.update {
                                it.copy(isLoadingMore = false, errorMessage = result.message)
                            }
                        }
                    }
                }

                DataApiExplorerMode.PLAYLIST,
                DataApiExplorerMode.CHANNEL -> {
                    when (val result = repository.fetchPlaylistItems(target, pageToken = token)) {
                        is YouTubeDataApiResult.Success -> {
                            _uiState.update { state ->
                                state.copy(
                                    isLoadingMore = false,
                                    tracks = (state.tracks + result.data.songs).distinctBy { it.id },
                                    nextPageToken = result.data.nextPageToken,
                                )
                            }
                        }
                        is YouTubeDataApiResult.Failure -> {
                            _uiState.update {
                                it.copy(isLoadingMore = false, errorMessage = result.message)
                            }
                        }
                    }
                }
            }
        }
    }
}

package com.mochyy.mochymusic.viewmodels

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mochyy.mochymusic.ai.AiActionExecutor
import com.mochyy.mochymusic.ai.AiUiState
import com.mochyy.mochymusic.ai.GeminiMusicAiRepository
import com.mochyy.mochymusic.ai.ScreenshotIdentifiedTrack
import com.mochyy.mochymusic.ai.ScreenshotScanResult
import com.mochyy.mochymusic.extensions.toMediaItem
import com.mochyy.mochymusic.playback.PlayerConnection
import com.mochyy.mochymusic.playback.queues.ListQueue
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.math.max

private const val TAG = "ScreenshotToMusicVM"
private const val MAX_IMAGE_DIM_PX = 1024

@HiltViewModel
class ScreenshotToMusicViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val aiRepository: GeminiMusicAiRepository,
    private val actionExecutor: AiActionExecutor,
) : ViewModel() {

    private val _selectedImageUri = MutableStateFlow<Uri?>(null)
    val selectedImageUri: StateFlow<Uri?> = _selectedImageUri.asStateFlow()

    private val _uiState = MutableStateFlow<AiUiState<ScreenshotScanResult>>(AiUiState.Idle)
    val uiState: StateFlow<AiUiState<ScreenshotScanResult>> = _uiState.asStateFlow()

    private val _playbackStatus = MutableStateFlow<String?>(null)
    val playbackStatus: StateFlow<String?> = _playbackStatus.asStateFlow()

    private val _isQueueing = MutableStateFlow(false)
    val isQueueing: StateFlow<Boolean> = _isQueueing.asStateFlow()

    fun scanScreenshotUri(uri: Uri) {
        if (_uiState.value is AiUiState.Loading) return
        _selectedImageUri.value = uri
        _playbackStatus.value = null
        viewModelScope.launch {
            _uiState.value = AiUiState.Loading
            try {
                val bitmap = withContext(Dispatchers.IO) { decodeScaledBitmap(uri) }
                    ?: throw IllegalStateException("Could not decode the selected image.")
                val result = aiRepository.identifyMusicFromScreenshot(bitmap)
                _uiState.value = AiUiState.Success(result)
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Screenshot scan timed out", e)
                _uiState.value = AiUiState.Error("Image scan timed out after 60 seconds. Please retry.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Screenshot scan failed", e)
                _uiState.value = AiUiState.Error(e.localizedMessage ?: "Failed to identify music from image.")
            }
        }
    }

    fun playIdentifiedTrack(
        track: ScreenshotIdentifiedTrack,
        playerConnection: PlayerConnection?,
    ) {
        if (playerConnection == null) {
            _playbackStatus.value = "Player service is connecting — try again shortly."
            return
        }
        viewModelScope.launch {
            _isQueueing.value = true
            try {
                val matched = actionExecutor.resolveTrackQueries(listOf(track.title to track.artist))
                if (matched.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        playerConnection.playQueue(
                            ListQueue(
                                title = "Scanned: ${track.title}",
                                items = matched.map { it.toMediaItem() },
                            )
                        )
                    }
                    _playbackStatus.value = "Playing \"${matched.first().title}\""
                } else {
                    _playbackStatus.value = "No playable audio found for \"${track.title}\"."
                }
            } finally {
                _isQueueing.value = false
            }
        }
    }

    fun queueAllIdentifiedTracks(
        tracks: List<ScreenshotIdentifiedTrack>,
        playerConnection: PlayerConnection?,
    ) {
        if (playerConnection == null || tracks.isEmpty()) return
        viewModelScope.launch {
            _isQueueing.value = true
            try {
                val matched = actionExecutor.resolveTrackQueries(tracks.map { it.title to it.artist })
                if (matched.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        playerConnection.playQueue(
                            ListQueue(
                                title = "Screenshot Mix (${matched.size} tracks)",
                                items = matched.map { it.toMediaItem() },
                            )
                        )
                    }
                    _playbackStatus.value = "Queued ${matched.size} identified tracks."
                } else {
                    _playbackStatus.value = "Could not match playable tracks from this screenshot."
                }
            } finally {
                _isQueueing.value = false
            }
        }
    }

    private fun decodeScaledBitmap(uri: Uri): Bitmap? {
        val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        appContext.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, boundsOptions)
        }
        val maxSide = max(boundsOptions.outWidth, boundsOptions.outHeight)
        if (maxSide <= 0) return null

        var sampleSize = 1
        while (maxSide / sampleSize > MAX_IMAGE_DIM_PX) {
            sampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return appContext.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        }
    }
}

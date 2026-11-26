package com.mochyy.mochymusic.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mochyy.mochymusic.ai.AiActionExecutor
import com.mochyy.mochymusic.ai.AiUiState
import com.mochyy.mochymusic.ai.GeminiMusicAiRepository
import com.mochyy.mochymusic.db.MusicDatabase
import com.mochyy.mochymusic.db.entities.AiChatMessageEntity
import com.mochyy.mochymusic.playback.PlayerConnection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

private const val TAG = "AiAssistantVM"

@HiltViewModel
class AiAssistantViewModel @Inject constructor(
    private val database: MusicDatabase,
    private val aiRepository: GeminiMusicAiRepository,
    private val actionExecutor: AiActionExecutor,
) : ViewModel() {

    val messages: StateFlow<List<AiChatMessageEntity>> = database.aiStudioDao
        .observeChatMessages()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    private val _replyState = MutableStateFlow<AiUiState<String>>(AiUiState.Idle)
    val replyState: StateFlow<AiUiState<String>> = _replyState.asStateFlow()

    private val _lastActionBanner = MutableStateFlow<String?>(null)
    val lastActionBanner: StateFlow<String?> = _lastActionBanner.asStateFlow()

    private val _pendingNavigationRoute = MutableStateFlow<String?>(null)
    val pendingNavigationRoute: StateFlow<String?> = _pendingNavigationRoute.asStateFlow()

    private val _voiceReadoutEnabled = MutableStateFlow(false)
    val voiceReadoutEnabled: StateFlow<Boolean> = _voiceReadoutEnabled.asStateFlow()

    private val _latestSpokenReply = MutableStateFlow<String?>(null)
    val latestSpokenReply: StateFlow<String?> = _latestSpokenReply.asStateFlow()

    fun toggleVoiceReadout() {
        _voiceReadoutEnabled.value = !_voiceReadoutEnabled.value
    }

    fun consumeSpokenReply() {
        _latestSpokenReply.value = null
    }

    fun consumeNavigationRoute() {
        _pendingNavigationRoute.value = null
    }

    fun dismissError() {
        if (_replyState.value is AiUiState.Error) {
            _replyState.value = AiUiState.Idle
        }
    }

    fun clearHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            database.aiStudioDao.clearChatHistory()
            _lastActionBanner.value = null
            _replyState.value = AiUiState.Idle
        }
    }

    fun sendMessage(
        rawPrompt: String,
        playerConnection: PlayerConnection?,
    ) {
        val prompt = rawPrompt.trim()
        if (prompt.isEmpty() || _replyState.value is AiUiState.Loading || _replyState.value is AiUiState.Streaming) {
            return
        }

        viewModelScope.launch {
            _replyState.value = AiUiState.Loading
            _lastActionBanner.value = null

            try {
                // Capture recent history before inserting the new prompt
                val priorHistory = withContext(Dispatchers.IO) {
                    database.aiStudioDao.recentChatMessages(12)
                        .reversed()
                        .map { it.role to it.content }
                }

                withContext(Dispatchers.IO) {
                    database.aiStudioDao.insertChatMessage(
                        AiChatMessageEntity(role = "user", content = prompt)
                    )
                }

                val currentTrack = playerConnection?.mediaMetadata?.value
                val nowPlayingContext = if (currentTrack != null) {
                    val artists = currentTrack.artists.joinToString(", ") { it.name }.ifBlank { "Unknown Artist" }
                    "\"${currentTrack.title}\" by $artists"
                } else {
                    "Nothing currently playing"
                }

                val topLibraryContext = withContext(Dispatchers.IO) {
                    runCatching {
                        database.allSongs().first()
                            .sortedByDescending { it.song.totalPlayTime }
                            .take(6)
                            .joinToString("; ") { song ->
                                val artist = song.artists.firstOrNull()?.name ?: "Unknown"
                                "${song.song.title} - $artist"
                            }
                    }.getOrDefault("")
                }

                // Step 1: Extract & execute real in-app music action if requested
                val parsedAction = aiRepository.extractAssistantAction(
                    userMessage = prompt,
                    nowPlayingTitle = currentTrack?.title,
                )
                val execResult = actionExecutor.execute(parsedAction, playerConnection)
                val actionSummary = execResult.summary.takeIf { it.isNotBlank() }
                if (execResult.executed && actionSummary != null) {
                    _lastActionBanner.value = actionSummary
                }
                if (!execResult.navigateRoute.isNullOrBlank()) {
                    _pendingNavigationRoute.value = execResult.navigateRoute
                }

                // Step 2: Stream conversational response from Gemini
                var finalReply = ""
                aiRepository.streamAssistantReply(
                    userMessage = prompt,
                    history = priorHistory,
                    nowPlayingContext = nowPlayingContext,
                    topLibraryContext = topLibraryContext,
                    executedActionSummary = actionSummary,
                ).collect { accumulated ->
                    finalReply = accumulated
                    _replyState.value = AiUiState.Streaming(accumulated)
                }

                if (finalReply.isBlank()) {
                    _replyState.value = AiUiState.Error("AI returned an empty response. Please try again.")
                    return@launch
                }

                withContext(Dispatchers.IO) {
                    database.aiStudioDao.insertChatMessage(
                        AiChatMessageEntity(
                            role = "assistant",
                            content = finalReply,
                            actionSummary = actionSummary,
                        )
                    )
                }

                _replyState.value = AiUiState.Success(finalReply)
                if (_voiceReadoutEnabled.value) {
                    _latestSpokenReply.value = finalReply
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "AI Assistant timed out", e)
                _replyState.value = AiUiState.Error("Request timed out after 60 seconds. Check your connection and try again.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "AI Assistant request failed", e)
                _replyState.value = AiUiState.Error(e.localizedMessage ?: "AI Assistant failed to respond.")
            }
        }
    }
}

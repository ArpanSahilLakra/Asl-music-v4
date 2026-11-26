package com.mochyy.mochymusic.ui.screens.ai

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.ai.AiUiState
import com.mochyy.mochymusic.db.entities.AiChatMessageEntity
import com.mochyy.mochymusic.viewmodels.AiAssistantViewModel
import java.util.Locale

private val QuickAssistantPrompts = listOf(
    "Play relaxing night drive music",
    "Increase bass for this track",
    "Turn on 3D spatial audio",
    "Explain the song playing now",
    "Create a workout mood playlist",
    "Enable karaoke vocal mode",
)

@Composable
fun AiAssistantScreen(
    navController: NavController,
    viewModel: AiAssistantViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current
    val messages by viewModel.messages.collectAsState()
    val replyState by viewModel.replyState.collectAsState()
    val actionBanner by viewModel.lastActionBanner.collectAsState()
    val pendingRoute by viewModel.pendingNavigationRoute.collectAsState()
    val voiceReadoutEnabled by viewModel.voiceReadoutEnabled.collectAsState()
    val latestSpokenReply by viewModel.latestSpokenReply.collectAsState()
    val nowPlaying by (playerConnection?.mediaMetadata?.collectAsState() ?: remember { mutableStateOf(null) })

    var inputText by remember { mutableStateOf("") }
    var isListeningVoice by remember { mutableStateOf(false) }
    var voiceStatusHint by remember { mutableStateOf<String?>(null) }
    var showClearDialog by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    // Navigate when assistant triggers a dedicated studio destination (e.g. Mood Playlist)
    LaunchedEffect(pendingRoute) {
        val route = pendingRoute ?: return@LaunchedEffect
        viewModel.consumeNavigationRoute()
        navController.navigate(route)
    }

    // Android TextToSpeech for optional spoken assistant replies
    val ttsHolder = remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(context) {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                engine?.language = Locale.getDefault()
            }
        }
        ttsHolder.value = engine
        onDispose {
            engine.stop()
            engine.shutdown()
            ttsHolder.value = null
        }
    }

    LaunchedEffect(latestSpokenReply, voiceReadoutEnabled) {
        val text = latestSpokenReply ?: return@LaunchedEffect
        viewModel.consumeSpokenReply()
        if (voiceReadoutEnabled) {
            ttsHolder.value?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "asl_ai_reply")
        }
    }

    // Android SpeechRecognizer for hands-free voice commands
    val speechRecognizer = remember(context) {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else {
            null
        }
    }

    DisposableEffect(speechRecognizer) {
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isListeningVoice = true
                voiceStatusHint = "Listening..."
            }

            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                isListeningVoice = false
            }

            override fun onError(error: Int) {
                isListeningVoice = false
                voiceStatusHint = "Voice capture unavailable (code $error). Try typing or tap mic again."
            }

            override fun onResults(results: Bundle?) {
                isListeningVoice = false
                voiceStatusHint = null
                val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!spoken.isNullOrBlank()) {
                    inputText = ""
                    viewModel.sendMessage(spoken, playerConnection)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!partial.isNullOrBlank()) {
                    inputText = partial
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        onDispose {
            speechRecognizer?.destroy()
        }
    }

    val startVoiceCapture: () -> Unit = {
        if (speechRecognizer == null) {
            voiceStatusHint = "Speech recognition is not available on this device."
        } else {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            }
            voiceStatusHint = "Listening..."
            speechRecognizer.startListening(intent)
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startVoiceCapture()
        } else {
            voiceStatusHint = "Microphone permission is required for voice commands."
        }
    }

    val streamingText = (replyState as? AiUiState.Streaming)?.partialText
    val isBusy = replyState is AiUiState.Loading || replyState is AiUiState.Streaming

    LaunchedEffect(messages.size, streamingText?.length) {
        val total = messages.size + if (streamingText != null) 1 else 0
        if (total > 0) {
            listState.animateScrollToItem(total - 1)
        }
    }

    Scaffold(
        containerColor = Color(0xFF07080E),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF13152A), Color(0xFF07080E))
                        )
                    )
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 8.dp, vertical = 10.dp),
            ) {
                IconButton(
                    onClick = { navController.popBackStack() },
                    modifier = Modifier.testTag("ai_assistant_back_button"),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "ASL Beats AI",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = nowPlaying?.let { "Now playing: ${it.title}" } ?: "Conversational DJ & Sound Engineer",
                        color = Color(0xFF9EA4C8),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(
                    onClick = { viewModel.toggleVoiceReadout() },
                    modifier = Modifier.testTag("ai_assistant_tts_toggle"),
                ) {
                    Icon(
                        painter = painterResource(
                            if (voiceReadoutEnabled) R.drawable.volume_up else R.drawable.volume_off
                        ),
                        contentDescription = if (voiceReadoutEnabled) "Disable spoken replies" else "Enable spoken replies",
                        tint = if (voiceReadoutEnabled) Color(0xFF818CF8) else Color(0xFF8A8FA8),
                        modifier = Modifier.size(22.dp),
                    )
                }
                IconButton(
                    onClick = { showClearDialog = true },
                    modifier = Modifier.testTag("ai_assistant_clear_button"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Clear chat history",
                        tint = Color(0xFF8A8FA8),
                    )
                }
            }
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0B0D17))
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                AnimatedVisibility(visible = voiceStatusHint != null) {
                    Text(
                        text = voiceStatusHint.orEmpty(),
                        color = Color(0xFFA5B4FC),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 6.dp, bottom = 6.dp),
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Surface(
                        color = if (isListeningVoice) Color(0xFFEC4899) else Color(0xFF1A1D33),
                        shape = CircleShape,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("ai_assistant_voice_button")
                            .clickable {
                                if (isListeningVoice) {
                                    speechRecognizer?.stopListening()
                                    isListeningVoice = false
                                    voiceStatusHint = null
                                } else {
                                    val hasPerm = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.RECORD_AUDIO
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (hasPerm) {
                                        startVoiceCapture()
                                    } else {
                                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                }
                            },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(R.drawable.graphic_eq),
                                contentDescription = if (isListeningVoice) "Stop voice input" else "Speak to AI assistant",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }

                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = { Text("Ask to play, tune bass, explain a track...", color = Color(0xFF7B809E), fontSize = 14.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF6366F1),
                            unfocusedBorderColor = Color(0xFF232742),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = Color(0xFF121526),
                            unfocusedContainerColor = Color(0xFF121526),
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("ai_assistant_input"),
                    )

                    Surface(
                        color = if (!isBusy && inputText.isNotBlank()) Color(0xFF6366F1) else Color(0xFF252942),
                        shape = CircleShape,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("ai_assistant_send_button")
                            .clickable(enabled = !isBusy && inputText.isNotBlank()) {
                                val toSend = inputText
                                inputText = ""
                                voiceStatusHint = null
                                viewModel.sendMessage(toSend, playerConnection)
                            },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (isBusy) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(20.dp),
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Send message",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            AnimatedVisibility(visible = actionBanner != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF1A2838))
                        .border(1.dp, Color(0xFF38BDF8).copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.equalizer),
                        contentDescription = null,
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = actionBanner.orEmpty(),
                        color = Color(0xFFE0F2FE),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            if (replyState is AiUiState.Error) {
                val err = (replyState as AiUiState.Error).message
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF3B1520))
                        .border(1.dp, Color(0xFFF43F5E).copy(alpha = 0.55f), RoundedCornerShape(14.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = err,
                        color = Color(0xFFFFE4E6),
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { viewModel.dismissError() }) {
                        Text("Dismiss", color = Color(0xFFFDA4AF), fontSize = 12.sp)
                    }
                }
            }

            if (messages.isEmpty() && streamingText == null) {
                Column(
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF6366F1), Color(0xFFEC4899))
                                )
                            ),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(34.dp),
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Your Personal Music Companion",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Ask by text or voice to queue tracks, tune live EQ & bass, switch on 3D spatial sound, or unpack a song's story.",
                        color = Color(0xFF9EA4C8),
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp),
                    ) {
                        items(QuickAssistantPrompts) { suggestion ->
                            Surface(
                                color = Color(0xFF15182C),
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier
                                    .border(1.dp, Color(0xFF2B3052), RoundedCornerShape(18.dp))
                                    .clickable { viewModel.sendMessage(suggestion, playerConnection) },
                            ) {
                                Text(
                                    text = suggestion,
                                    color = Color(0xFFDCE1FF),
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                )
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    items(messages, key = { it.id }) { msg ->
                        ChatBubble(message = msg)
                    }
                    if (streamingText != null) {
                        item(key = "streaming_reply") {
                            ChatBubble(
                                message = AiChatMessageEntity(
                                    role = "assistant",
                                    content = streamingText,
                                    actionSummary = actionBanner,
                                )
                            )
                        }
                    } else if (replyState is AiUiState.Loading) {
                        item(key = "loading_reply") {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.padding(vertical = 6.dp),
                            ) {
                                CircularProgressIndicator(
                                    color = Color(0xFF818CF8),
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text("Thinking...", color = Color(0xFF9EA4C8), fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            containerColor = Color(0xFF141729),
            titleContentColor = Color.White,
            textContentColor = Color(0xFFC2C7E0),
            title = { Text("Clear conversation?") },
            text = { Text("This removes your saved AI Music Assistant chat history from this device.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearHistory()
                        showClearDialog = false
                    }
                ) {
                    Text("Clear", color = Color(0xFFF43F5E))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel", color = Color(0xFFA5B4FC))
                }
            }
        )
    }
}

@Composable
private fun ChatBubble(message: AiChatMessageEntity) {
    val isUser = message.role == "user"
    Column(
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 310.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 18.dp,
                        topEnd = 18.dp,
                        bottomStart = if (isUser) 18.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 18.dp,
                    )
                )
                .background(
                    if (isUser) {
                        Brush.linearGradient(listOf(Color(0xFF6366F1), Color(0xFF4F46E5)))
                    } else {
                        Brush.linearGradient(listOf(Color(0xFF15192E), Color(0xFF111424)))
                    }
                )
                .border(
                    width = 1.dp,
                    color = if (isUser) Color.Transparent else Color(0xFF262B4A),
                    shape = RoundedCornerShape(
                        topStart = 18.dp,
                        topEnd = 18.dp,
                        bottomStart = if (isUser) 18.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 18.dp,
                    )
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!message.actionSummary.isNullOrBlank() && !isUser) {
                    Text(
                        text = message.actionSummary,
                        color = Color(0xFF38BDF8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Text(
                    text = message.content,
                    color = Color.White,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                )
            }
        }
    }
}

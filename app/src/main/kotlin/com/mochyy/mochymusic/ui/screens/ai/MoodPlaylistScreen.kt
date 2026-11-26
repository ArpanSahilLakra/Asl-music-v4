package com.mochyy.mochymusic.ui.screens.ai

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.ai.AiUiState
import com.mochyy.mochymusic.ai.MoodPreset
import com.mochyy.mochymusic.ai.MoodPresets
import com.mochyy.mochymusic.extensions.toMediaItem
import com.mochyy.mochymusic.playback.queues.ListQueue
import com.mochyy.mochymusic.viewmodels.MoodPlaylistResult
import com.mochyy.mochymusic.viewmodels.MoodPlaylistViewModel

@Composable
fun MoodPlaylistScreen(
    navController: NavController,
    seedQuery: String? = null,
    viewModel: MoodPlaylistViewModel = hiltViewModel(),
) {
    val playerConnection = LocalPlayerConnection.current
    val selectedMood by viewModel.selectedMood.collectAsState()
    val customPrompt by viewModel.customPrompt.collectAsState()
    val useListeningHistory by viewModel.useListeningHistory.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(seedQuery) {
        viewModel.applySeedQuery(seedQuery)
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
                            listOf(Color(0xFF15172E), Color(0xFF07080E))
                        )
                    )
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 8.dp, vertical = 10.dp),
            ) {
                IconButton(
                    onClick = { navController.popBackStack() },
                    modifier = Modifier.testTag("mood_playlist_back_button"),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "AI Mood Playlist Studio",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "12 mood presets + custom prompts saved directly to your library",
                        color = Color(0xFF9EA4C8),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding(),
        ) {
            item(key = "mood_grid_header") {
                Text(
                    text = "Choose your sonic mood",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            // 12 mood presets rendered in clean 2-column rows
            val rows = MoodPresets.All.chunked(2)
            itemsIndexed(rows, key = { idx, _ -> "mood_row_$idx" }) { _, rowItems ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    rowItems.forEach { preset ->
                        MoodPresetCard(
                            preset = preset,
                            selected = preset.id == selectedMood.id,
                            onClick = { viewModel.selectMood(preset) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            item(key = "custom_prompt_box") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = customPrompt,
                        onValueChange = viewModel::updateCustomPrompt,
                        label = { Text("Describe a custom vibe (optional)", color = Color(0xFF9EA4C8)) },
                        placeholder = {
                            Text("e.g., late-night synth pop with Hindi indie chill", color = Color(0xFF6B708E))
                        },
                        maxLines = 3,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF6366F1),
                            unfocusedBorderColor = Color(0xFF252945),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = Color(0xFF101324),
                            unfocusedContainerColor = Color(0xFF101324),
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("mood_custom_prompt_input"),
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF111426))
                            .border(1.dp, Color(0xFF232742), RoundedCornerShape(14.dp))
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Blend with my listening history",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                text = "Uses your top played tracks to personalize recommendations",
                                color = Color(0xFF8C92B5),
                                fontSize = 11.sp,
                            )
                        }
                        Switch(
                            checked = useListeningHistory,
                            onCheckedChange = viewModel::setUseListeningHistory,
                            modifier = Modifier.testTag("mood_history_switch"),
                        )
                    }

                    Button(
                        onClick = { viewModel.generatePlaylist() },
                        enabled = uiState !is AiUiState.Loading,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("mood_generate_button"),
                    ) {
                        if (uiState is AiUiState.Loading) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(statusMessage ?: "Generating...", color = Color.White, fontWeight = FontWeight.SemiBold)
                        } else {
                            Icon(imageVector = Icons.Filled.Star, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Generate ${selectedMood.title} Playlist", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            if (uiState is AiUiState.Error) {
                item(key = "mood_error") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF3B1520))
                            .border(1.dp, Color(0xFFF43F5E).copy(alpha = 0.55f), RoundedCornerShape(14.dp))
                            .padding(14.dp),
                    ) {
                        Text(
                            text = (uiState as AiUiState.Error).message,
                            color = Color(0xFFFFE4E6),
                            fontSize = 13.sp,
                        )
                    }
                }
            }

            if (uiState is AiUiState.Success) {
                val result = (uiState as AiUiState.Success<MoodPlaylistResult>).data
                item(key = "generated_result_card") {
                    GeneratedPlaylistResultCard(
                        result = result,
                        onPlayNow = {
                            val items = result.resolvedSongs.map { it.toMediaItem() }
                            if (items.isNotEmpty()) {
                                playerConnection?.playQueue(
                                    ListQueue(title = result.plan.title, items = items)
                                )
                            }
                        },
                        onOpenPlaylist = {
                            navController.navigate("local_playlist/${result.savedPlaylistId}")
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun MoodPresetCard(
    preset: MoodPreset,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val brush = Brush.linearGradient(
        listOf(
            Color(preset.startColorHex).copy(alpha = if (selected) 0.92f else 0.42f),
            Color(preset.endColorHex).copy(alpha = if (selected) 0.92f else 0.34f),
        )
    )
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(brush)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) Color.White else Color.White.copy(alpha = 0.14f),
                shape = RoundedCornerShape(16.dp),
            )
            .clickable(onClick = onClick)
            .testTag("mood_card_${preset.id}")
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Text(
            text = preset.title,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = preset.subtitle,
            color = Color.White.copy(alpha = 0.82f),
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun GeneratedPlaylistResultCard(
    result: MoodPlaylistResult,
    onPlayNow: () -> Unit,
    onOpenPlaylist: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF11152A))
            .border(1.dp, Color(0xFF6366F1).copy(alpha = 0.45f), RoundedCornerShape(20.dp))
            .padding(16.dp),
    ) {
        Text(
            text = result.plan.title,
            color = Color.White,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "${result.plan.description} • ${result.plan.targetBpmRange}",
            color = Color(0xFFB4BAE0),
            fontSize = 13.sp,
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Button(
                onClick = onPlayNow,
                enabled = result.resolvedSongs.isNotEmpty(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("mood_play_now_button"),
            ) {
                Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Play Now (${result.resolvedSongs.size})", color = Color.White, fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick = onOpenPlaylist,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .height(48.dp)
                    .testTag("mood_open_saved_playlist_button"),
            ) {
                Text("Open Saved", color = Color(0xFFA5B4FC))
            }
        }

        AnimatedVisibility(visible = result.plan.tracks.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                result.plan.tracks.forEachIndexed { index, track ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF171C36))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Text(
                            text = "${index + 1}",
                            color = Color(0xFF818CF8),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(24.dp),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "${track.artist} • ${track.reason}",
                                color = Color(0xFF9EA4C8),
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

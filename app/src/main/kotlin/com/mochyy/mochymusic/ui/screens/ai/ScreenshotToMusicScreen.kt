package com.mochyy.mochymusic.ui.screens.ai

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.ai.AiUiState
import com.mochyy.mochymusic.ai.ScreenshotScanResult
import com.mochyy.mochymusic.viewmodels.ScreenshotToMusicViewModel

@Composable
fun ScreenshotToMusicScreen(
    navController: NavController,
    viewModel: ScreenshotToMusicViewModel = hiltViewModel(),
) {
    val playerConnection = LocalPlayerConnection.current
    val selectedUri by viewModel.selectedImageUri.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val playbackStatus by viewModel.playbackStatus.collectAsState()
    val isQueueing by viewModel.isQueueing.collectAsState()

    // Zero-permission Android Photo Picker per Google Play policy
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.scanScreenshotUri(uri)
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
                            listOf(Color(0xFF0F2430), Color(0xFF07080E))
                        )
                    )
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 8.dp, vertical = 10.dp),
            ) {
                IconButton(
                    onClick = { navController.popBackStack() },
                    modifier = Modifier.testTag("screenshot_music_back_button"),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Screenshot-to-Music",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Identify songs from album art, playlists, or lyric cards",
                        color = Color(0xFF8CC5D9),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
        ) {
            // Upload / Photo Picker Hero
            item(key = "picker_card") {
                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF0E1D2B), Color(0xFF101426))
                            )
                        )
                        .border(1.dp, Color(0xFF06B6D4).copy(alpha = 0.45f), RoundedCornerShape(22.dp))
                        .padding(16.dp),
                ) {
                    if (selectedUri != null) {
                        AsyncImage(
                            model = selectedUri,
                            contentDescription = "Selected music screenshot",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .border(1.dp, Color(0xFF284259), RoundedCornerShape(16.dp)),
                        )
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFF0A121E))
                                .border(1.dp, Color(0xFF1E3247), RoundedCornerShape(16.dp))
                                .padding(20.dp),
                        ) {
                            Text(
                                text = "Drop in any music screenshot",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "Supports playlist screenshots, album artwork, social stories, and now-playing screens using Android's private Photo Picker (no storage permission required).",
                                color = Color(0xFF9AB8C9),
                                fontSize = 13.sp,
                                lineHeight = 19.sp,
                            )
                        }
                    }

                    Button(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        enabled = uiState !is AiUiState.Loading,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0891B2)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("screenshot_music_pick_button"),
                    ) {
                        if (uiState is AiUiState.Loading) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Scanning Artwork & Titles...",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Search,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (selectedUri == null) "Pick Screenshot to Scan" else "Scan Another Screenshot",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                            )
                        }
                    }
                }
            }

            if (!playbackStatus.isNullOrBlank()) {
                item(key = "playback_status") {
                    Text(
                        text = playbackStatus.orEmpty(),
                        color = Color(0xFF67E8F9),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            if (uiState is AiUiState.Error) {
                item(key = "scan_error") {
                    val msg = (uiState as AiUiState.Error).message
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF2B1218))
                            .border(1.dp, Color(0xFFF87171).copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                            .padding(14.dp),
                    ) {
                        Text("Scan Failed", color = Color(0xFFFCA5A5), fontWeight = FontWeight.Bold)
                        Text(msg, color = Color(0xFFFEE2E2), fontSize = 13.sp)
                    }
                }
            }

            item(key = "scan_results") {
                AnimatedVisibility(visible = uiState is AiUiState.Success) {
                    val scan = (uiState as? AiUiState.Success<ScreenshotScanResult>)?.data
                    if (scan != null) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(22.dp))
                                .background(Color(0xFF101526))
                                .border(1.dp, Color(0xFF283A57), RoundedCornerShape(22.dp))
                                .padding(16.dp),
                        ) {
                            Text(
                                text = scan.sourceSummary,
                                color = Color(0xFF67E8F9),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                            )

                            if (scan.tracks.isEmpty()) {
                                Text(
                                    text = "No song titles, artists, or recognizable album covers were detected in this image. Try a clearer screenshot of a player or playlist.",
                                    color = Color(0xFF9EA4C8),
                                    fontSize = 13.sp,
                                    lineHeight = 19.sp,
                                )
                            } else {
                                if (scan.tracks.size > 1) {
                                    OutlinedButton(
                                        onClick = { viewModel.queueAllIdentifiedTracks(scan.tracks, playerConnection) },
                                        enabled = !isQueueing,
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .testTag("screenshot_music_queue_all_button"),
                                    ) {
                                        Text(
                                            text = "Queue All ${scan.tracks.size} Identified Tracks",
                                            color = Color(0xFF67E8F9),
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }

                                scan.tracks.forEachIndexed { idx, track ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(Color(0xFF161D33))
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = track.title,
                                                color = Color.White,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                text = buildString {
                                                    append(track.artist)
                                                    if (track.album.isNotBlank()) append(" • ${track.album}")
                                                },
                                                color = Color(0xFFA5B4FC),
                                                fontSize = 12.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                text = "${track.confidencePercent}% match • ${track.detectedCue}",
                                                color = Color(0xFF6EE7B7),
                                                fontSize = 11.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        IconButton(
                                            onClick = { viewModel.playIdentifiedTrack(track, playerConnection) },
                                            enabled = !isQueueing,
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(Color(0xFF0891B2))
                                                .testTag("screenshot_music_play_$idx"),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.PlayArrow,
                                                contentDescription = "Play ${track.title}",
                                                tint = Color.White,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

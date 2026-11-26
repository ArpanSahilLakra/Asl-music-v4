package com.mochyy.mochymusic.ui.screens.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.mochyy.mochymusic.viewmodels.SmartLibraryHygieneViewModel
import java.util.Locale

@Composable
fun SmartLibraryHygieneScreen(
    navController: NavController,
    viewModel: SmartLibraryHygieneViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val totalPlaylistDupes = state.playlistDuplicates.sumOf { it.duplicateCount }

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
                            listOf(Color(0xFF0F2922), Color(0xFF07080E))
                        )
                    )
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 8.dp, vertical = 10.dp),
            ) {
                IconButton(
                    onClick = { navController.popBackStack() },
                    modifier = Modifier.testTag("library_hygiene_back_button"),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Smart Offline & Duplicate Cleaner",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Playlist deduplication, queue guard & safe cache control",
                        color = Color(0xFF86EFAC),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(
                    onClick = viewModel::scanLibrary,
                    modifier = Modifier.testTag("library_hygiene_rescan_button"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "Rescan library",
                        tint = Color(0xFF6EE7B7),
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
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
        ) {
            if (state.isScanning) {
                item(key = "scanning") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF101626))
                            .padding(16.dp),
                    ) {
                        CircularProgressIndicator(
                            color = Color(0xFF34D399),
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = "Scanning playlists, library metadata, and cache...",
                            color = Color.White,
                            fontSize = 13.sp,
                        )
                    }
                }
            }

            // Metrics Summary
            item(key = "hygiene_metrics") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    HygieneMetricTile(
                        value = state.totalLibraryTracks.toString(),
                        label = "Tracks",
                        accent = Color(0xFF60A5FA),
                        modifier = Modifier.weight(1f),
                    )
                    HygieneMetricTile(
                        value = totalPlaylistDupes.toString(),
                        label = "Playlist Dupes",
                        accent = if (totalPlaylistDupes > 0) Color(0xFFFBBF24) else Color(0xFF34D399),
                        modifier = Modifier.weight(1f),
                    )
                    HygieneMetricTile(
                        value = String.format(Locale.US, "%.1f MB", state.cacheSizeMb),
                        label = "Temp Cache",
                        accent = Color(0xFFA78BFA),
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            if (!state.statusMessage.isNullOrBlank()) {
                item(key = "hygiene_status") {
                    Text(
                        text = state.statusMessage.orEmpty(),
                        color = Color(0xFF6EE7B7),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            // Smart Queue Guard & Cache Cleanup Card
            item(key = "queue_and_cache") {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFF101526))
                        .border(1.dp, Color(0xFF23304A), RoundedCornerShape(18.dp))
                        .padding(14.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Prevent Duplicate Tracks in Queue",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "Automatically skips repeat entries when adding to Up Next",
                                color = Color(0xFF9EA4C8),
                                fontSize = 12.sp,
                            )
                        }
                        Switch(
                            checked = state.preventQueueDuplicates,
                            onCheckedChange = viewModel::togglePreventQueueDuplicates,
                            modifier = Modifier.testTag("library_hygiene_queue_guard_switch"),
                        )
                    }

                    OutlinedButton(
                        onClick = viewModel::clearTemporaryCache,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("library_hygiene_clear_cache_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = null,
                            tint = Color(0xFFA78BFA),
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Clear Temporary Image & HTTP Cache",
                            color = Color(0xFFD8B4FE),
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            // Playlist Duplicate Cleaner
            item(key = "playlist_dupes_header") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "Playlist Duplicate Entries",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    if (state.playlistDuplicates.isNotEmpty()) {
                        Button(
                            onClick = viewModel::deduplicateAllPlaylists,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("library_hygiene_clean_all_button"),
                        ) {
                            Text("Clean All", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            if (state.playlistDuplicates.isEmpty()) {
                item(key = "no_playlist_dupes") {
                    Text(
                        text = "Your playlists are spotless — zero duplicate tracks found.",
                        color = Color(0xFF86EFAC),
                        fontSize = 13.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF0E1F1A))
                            .border(1.dp, Color(0xFF10B981).copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                            .padding(14.dp),
                    )
                }
            } else {
                items(state.playlistDuplicates, key = { it.playlistId }) { summary ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF12182B))
                            .border(1.dp, Color(0xFFF59E0B).copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = summary.playlistName,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "${summary.duplicateCount} duplicate entries detected",
                                color = Color(0xFFFCD34D),
                                fontSize = 12.sp,
                            )
                        }
                        OutlinedButton(
                            onClick = { viewModel.deduplicatePlaylist(summary) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("clean_playlist_${summary.playlistId}"),
                        ) {
                            Text("Clean", color = Color(0xFF6EE7B7), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Library Duplicate Groups Overview
            if (state.libraryDuplicateGroups.isNotEmpty()) {
                item(key = "lib_dupes_header") {
                    Text(
                        text = "Similar / Duplicate Songs in Library (${state.libraryDuplicateGroups.size})",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                items(state.libraryDuplicateGroups, key = { it.normalizedKey }) { group ->
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF121629))
                            .border(1.dp, Color(0xFF283052), RoundedCornerShape(14.dp))
                            .padding(12.dp),
                    ) {
                        Text(
                            text = group.displayTitle,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "${group.displayArtist.ifBlank { "Unknown Artist" }} • ${group.itemIds.size} copies",
                            color = Color(0xFF9EA4C8),
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HygieneMetricTile(
    value: String,
    label: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF111526))
            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
            .padding(12.dp),
    ) {
        Text(
            text = value,
            color = accent,
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
        )
        Text(
            text = label,
            color = Color(0xFFA7B0D1),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

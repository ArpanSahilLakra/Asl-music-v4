package com.mochyy.mochymusic.ui.screens.ai

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.ai.AiUiState
import com.mochyy.mochymusic.ai.ListeningDnaReport
import com.mochyy.mochymusic.viewmodels.AiListeningInsightsViewModel
import java.net.URLEncoder

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiListeningInsightsScreen(
    navController: NavController,
    viewModel: AiListeningInsightsViewModel = hiltViewModel(),
) {
    val stats by viewModel.stats.collectAsState()
    val dnaState by viewModel.dnaState.collectAsState()

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
                            listOf(Color(0xFF201330), Color(0xFF07080E))
                        )
                    )
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 8.dp, vertical = 10.dp),
            ) {
                IconButton(
                    onClick = { navController.popBackStack() },
                    modifier = Modifier.testTag("ai_insights_back_button"),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "AI Music Personality & DNA",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Listening habits, sonic archetype & personalized discovery",
                        color = Color(0xFFB7A5D6),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(
                    onClick = viewModel::refreshLibraryStats,
                    modifier = Modifier.testTag("ai_insights_refresh_button"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "Refresh library stats",
                        tint = Color(0xFFD8B4FE),
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
            // Library metrics row
            item(key = "library_metrics") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    InsightStatCard(
                        value = stats.totalTracks.toString(),
                        label = "Library Tracks",
                        accent = Color(0xFF818CF8),
                        modifier = Modifier.weight(1f),
                    )
                    InsightStatCard(
                        value = stats.likedTracks.toString(),
                        label = "Favorites",
                        accent = Color(0xFFF472B6),
                        modifier = Modifier.weight(1f),
                    )
                    InsightStatCard(
                        value = "${stats.totalMinutesListened}m",
                        label = "Listened",
                        accent = Color(0xFF34D399),
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // Analyze Sonic DNA CTA
            item(key = "analyze_button") {
                Button(
                    onClick = viewModel::analyzeSonicDna,
                    enabled = dnaState !is AiUiState.Loading,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("ai_insights_analyze_button"),
                ) {
                    if (dnaState is AiUiState.Loading) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Decoding Your Sonic DNA...",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                        )
                    } else {
                        Icon(
                            painter = painterResource(R.drawable.sparks),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (dnaState is AiUiState.Success) {
                                "Refresh AI Sonic DNA Report"
                            } else {
                                "Generate My AI Sonic DNA"
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                        )
                    }
                }
            }

            // Error surface
            if (dnaState is AiUiState.Error) {
                item(key = "dna_error") {
                    val message = (dnaState as AiUiState.Error).message
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF2B1218))
                            .border(1.dp, Color(0xFFF87171).copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                            .padding(14.dp),
                    ) {
                        Text(
                            text = "Sonic DNA Analysis Failed",
                            color = Color(0xFFFCA5A5),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                        )
                        Text(
                            text = message,
                            color = Color(0xFFFEE2E2),
                            fontSize = 13.sp,
                        )
                    }
                }
            }

            // AI Sonic DNA Report
            item(key = "dna_report") {
                AnimatedVisibility(visible = dnaState is AiUiState.Success) {
                    val report = (dnaState as? AiUiState.Success<ListeningDnaReport>)?.data
                    if (report != null) {
                        SonicDnaReportSection(
                            report = report,
                            onOpenMoodSeed = { seed ->
                                val encoded = URLEncoder.encode(seed, "UTF-8")
                                navController.navigate("ai_mood_playlist?seed=$encoded")
                            },
                            onOpenMastering = {
                                navController.navigate("ai_mastering")
                            },
                        )
                    }
                }
            }

            // Top Artists from local library
            if (stats.topArtistNames.isNotEmpty()) {
                item(key = "top_artists") {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color(0xFF101322))
                            .border(1.dp, Color(0xFF232842), RoundedCornerShape(18.dp))
                            .padding(14.dp),
                    ) {
                        Text(
                            text = "Your Top Artists",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            stats.topArtistNames.forEach { artist ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(Color(0xFF1C2038))
                                        .border(1.dp, Color(0xFF323961), RoundedCornerShape(50))
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                ) {
                                    Text(
                                        text = artist,
                                        color = Color(0xFFE2E6FF),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Most Played Tracks from local library
            item(key = "top_tracks") {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFF101322))
                        .border(1.dp, Color(0xFF232842), RoundedCornerShape(18.dp))
                        .padding(14.dp),
                ) {
                    Text(
                        text = "Most Played in Your Library",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    if (stats.topTrackTitles.isEmpty()) {
                        Text(
                            text = "Play songs in ASL Beats to build your listening profile, or tap Generate My AI Sonic DNA for a starter music persona.",
                            color = Color(0xFF9EA4C8),
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                        )
                    } else {
                        stats.topTrackTitles.forEachIndexed { index, trackLine ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF15192D))
                                    .padding(horizontal = 12.dp, vertical = 9.dp),
                            ) {
                                Text(
                                    text = "#${index + 1}",
                                    color = Color(0xFF818CF8),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.width(24.dp),
                                )
                                Text(
                                    text = trackLine,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InsightStatCard(
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
            .padding(14.dp),
    ) {
        Text(
            text = value,
            color = accent,
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold,
        )
        Text(
            text = label,
            color = Color(0xFFA7B0D1),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SonicDnaReportSection(
    report: ListeningDnaReport,
    onOpenMoodSeed: (String) -> Unit,
    onOpenMastering: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Archetype Hero Card
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF2E1065), Color(0xFF1E1B4B), Color(0xFF131629))
                    )
                )
                .border(1.dp, Color(0xFFA855F7).copy(alpha = 0.55f), RoundedCornerShape(22.dp))
                .padding(18.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = null,
                    tint = Color(0xFFE879F9),
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = "YOUR SONIC ARCHETYPE",
                    color = Color(0xFFE879F9),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                )
            }
            Text(
                text = report.archetypeTitle,
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                text = report.sonicTagline,
                color = Color(0xFFE9D5FF),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (report.vibeSummary.isNotBlank()) {
                Text(
                    text = report.vibeSummary,
                    color = Color(0xFFD5D2EC),
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                )
            }

            if (report.topMoodTags.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    report.topMoodTags.forEach { tag ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Color.White.copy(alpha = 0.12f))
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Text(
                                text = tag,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        }

        // Audio Coaching Card
        if (report.audioCoachingTip.isNotBlank()) {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF10192D))
                    .border(1.dp, Color(0xFF38BDF8).copy(alpha = 0.4f), RoundedCornerShape(18.dp))
                    .padding(14.dp),
            ) {
                Text(
                    text = "AI Audio Coaching Tip",
                    color = Color(0xFF7DD3FC),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = report.audioCoachingTip,
                    color = Color(0xFFE0F2FE),
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                )
                OutlinedButton(
                    onClick = onOpenMastering,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("ai_insights_open_mastering"),
                ) {
                    Text(
                        text = "Open AI Mastering Studio",
                        color = Color(0xFF7DD3FC),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        // Discovery Picks
        if (report.discoveryPicks.isNotEmpty()) {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF101322))
                    .border(1.dp, Color(0xFF283052), RoundedCornerShape(18.dp))
                    .padding(14.dp),
            ) {
                Text(
                    text = "Fresh Discovery Picks For You",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
                report.discoveryPicks.forEachIndexed { idx, pick ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF171C33))
                            .clickable { onOpenMoodSeed("${pick.title} ${pick.artist}") }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${idx + 1}. ${pick.title}",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = pick.artist,
                                color = Color(0xFFA5B4FC),
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (pick.reason.isNotBlank()) {
                                Text(
                                    text = pick.reason,
                                    color = Color(0xFF9EA4C8),
                                    fontSize = 11.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = "Explore mix",
                            tint = Color(0xFFC084FC),
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }

                Button(
                    onClick = { onOpenMoodSeed(report.recommendedMoodSeed) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEC4899)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("ai_insights_launch_mood_mix"),
                ) {
                    Text(
                        text = "Build '${report.recommendedMoodSeed}' Mood Mix",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
            }
        }
    }
}

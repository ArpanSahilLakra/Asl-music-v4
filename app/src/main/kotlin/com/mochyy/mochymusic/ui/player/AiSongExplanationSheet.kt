package com.mochyy.mochymusic.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.ai.AiUiState
import com.mochyy.mochymusic.ai.SongExplanation
import com.mochyy.mochymusic.ui.player.menu.MenuSheetSurface
import com.mochyy.mochymusic.viewmodels.SongInsightsViewModel

private val InsightLanguages = listOf("English", "Hindi", "Spanish", "Japanese", "Korean", "French")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiSongExplanationSheet(
    onDismiss: () -> Unit,
    onOpenSavedPassages: (() -> Unit)? = null,
    viewModel: SongInsightsViewModel = hiltViewModel(),
) {
    val playerConnection = LocalPlayerConnection.current
    val currentMetadata by (playerConnection?.mediaMetadata?.collectAsState() ?: remember { mutableStateOf(null) })
    val explanationState by viewModel.explanationState.collectAsState()
    val targetLanguage by viewModel.targetLanguage.collectAsState()
    val karaokeEnabled by viewModel.karaokeEnabled.collectAsState()

    var passageDraft by remember { mutableStateOf("") }
    var passageSavedNotice by remember { mutableStateOf(false) }

    val songTitle = currentMetadata?.title.orEmpty()
    val artistName = currentMetadata?.artists?.joinToString(", ") { it.name }.orEmpty().ifBlank { "Unknown Artist" }
    val albumName = currentMetadata?.album?.title

    LaunchedEffect(songTitle, artistName, targetLanguage) {
        if (songTitle.isNotBlank()) {
            viewModel.explainSong(
                songTitle = songTitle,
                artistName = artistName,
                albumName = albumName,
            )
        }
    }

    MenuSheetSurface(onDismiss = onDismiss) { _ ->
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = null,
                    tint = Color(0xFF818CF8),
                    modifier = Modifier.size(22.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "AI Song Insights & Lyrics Studio",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = if (songTitle.isNotBlank()) "$songTitle • $artistName" else "Play any track to analyze",
                        color = Color(0xFFA0A6C4),
                        fontSize = 12.sp,
                    )
                }
                if (songTitle.isNotBlank()) {
                    IconButton(
                        onClick = {
                            viewModel.explainSong(
                                songTitle = songTitle,
                                artistName = artistName,
                                albumName = albumName,
                                snippetContext = passageDraft.takeIf { it.isNotBlank() },
                                forceRefresh = true,
                            )
                        },
                        modifier = Modifier.testTag("ai_song_insights_refresh"),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Refresh AI insights",
                            tint = Color(0xFFA5B4FC),
                        )
                    }
                }
            }

            // Karaoke Vocal Attenuation DSP Toggle
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF15192E))
                    .border(1.dp, Color(0xFF282F54), RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Karaoke Vocal Attenuation",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Cuts center vocal frequencies live for sing-along practice",
                        color = Color(0xFF9EA4C8),
                        fontSize = 11.sp,
                    )
                }
                Switch(
                    checked = karaokeEnabled,
                    onCheckedChange = { viewModel.toggleKaraokeVocalCut() },
                    modifier = Modifier.testTag("karaoke_vocal_cut_switch"),
                )
            }

            // Target explanation / translation language selector
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(InsightLanguages) { lang ->
                    val selected = lang == targetLanguage
                    Surface(
                        color = if (selected) Color(0xFF6366F1) else Color(0xFF171B2E),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .clickable { viewModel.setTargetLanguage(lang) }
                            .testTag("insight_lang_${lang.lowercase()}"),
                    ) {
                        Text(
                            text = lang,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        )
                    }
                }
            }

            // AI Song Explanation Content
            when (val state = explanationState) {
                is AiUiState.Idle -> {
                    Text(
                        text = "Play a song to view its themes, meaning, story interpretation, and artist context.",
                        color = Color(0xFF9EA4C8),
                        fontSize = 13.sp,
                    )
                }

                is AiUiState.Loading, is AiUiState.Streaming -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(vertical = 16.dp),
                    ) {
                        CircularProgressIndicator(
                            color = Color(0xFF818CF8),
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp),
                        )
                        Text("Analyzing song themes & story...", color = Color(0xFFC5CBE8), fontSize = 13.sp)
                    }
                }

                is AiUiState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF3B1520))
                            .padding(12.dp),
                    ) {
                        Text(state.message, color = Color(0xFFFFE4E6), fontSize = 13.sp)
                    }
                }

                is AiUiState.Success -> {
                    SongExplanationBody(explanation = state.data)
                }
            }

            // Save Favorite Lyric Passage into Room v43
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF121629))
                    .border(1.dp, Color(0xFF262D4F), RoundedCornerShape(16.dp))
                    .padding(12.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "Save Favorite Lyric Passage",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    if (onOpenSavedPassages != null) {
                        Text(
                            text = "View All",
                            color = Color(0xFF818CF8),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clickable {
                                    onDismiss()
                                    onOpenSavedPassages()
                                }
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                        )
                    }
                }

                OutlinedTextField(
                    value = passageDraft,
                    onValueChange = {
                        passageDraft = it
                        passageSavedNotice = false
                    },
                    placeholder = { Text("Paste or type a favorite line to bookmark...", color = Color(0xFF6F7594), fontSize = 12.sp) },
                    maxLines = 3,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF6366F1),
                        unfocusedBorderColor = Color(0xFF252A47),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("save_lyric_passage_input"),
                )

                Button(
                    onClick = {
                        val id = currentMetadata?.id ?: "manual_${System.currentTimeMillis()}"
                        viewModel.savePassage(
                            songId = id,
                            songTitle = songTitle.ifBlank { "Untitled Track" },
                            artistName = artistName,
                            passageText = passageDraft,
                        )
                        passageDraft = ""
                        passageSavedNotice = true
                    },
                    enabled = passageDraft.isNotBlank(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("save_lyric_passage_button"),
                ) {
                    if (passageSavedNotice) {
                        Icon(imageVector = Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Saved to Lyric Vault", color = Color.White)
                    } else {
                        Text("Bookmark Passage", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SongExplanationBody(explanation: SongExplanation) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (explanation.themes.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                explanation.themes.forEach { theme ->
                    Surface(
                        color = Color(0xFF1F2442),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text(
                            text = theme,
                            color = Color(0xFFC7D2FE),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                }
            }
        }

        InsightCard(title = "Meaning & Core Message", body = explanation.summary)
        InsightCard(title = "Mood & Sonic Vibe", body = explanation.moodAndVibe)
        InsightCard(title = "Story Interpretation", body = explanation.storyInterpretation)
        InsightCard(title = "Artist & Era Context", body = explanation.artistAndEraContext)
        if (explanation.languageNote.isNotBlank()) {
            InsightCard(title = "Language & Poetic Note", body = explanation.languageNote)
        }
        if (explanation.listeningTip.isNotBlank()) {
            InsightCard(title = "ASL Beats Listening Tip", body = explanation.listeningTip)
        }
    }
}

@Composable
private fun InsightCard(title: String, body: String) {
    if (body.isBlank()) return
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF121629))
            .padding(12.dp),
    ) {
        Text(
            text = title,
            color = Color(0xFF818CF8),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = body,
            color = Color(0xFFE6E9F8),
            fontSize = 13.sp,
            lineHeight = 19.sp,
        )
    }
}

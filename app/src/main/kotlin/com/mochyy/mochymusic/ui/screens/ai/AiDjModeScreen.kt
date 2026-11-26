package com.mochyy.mochymusic.ui.screens.ai

import android.speech.tts.TextToSpeech
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.ai.AiUiState
import com.mochyy.mochymusic.ai.DjPersona
import com.mochyy.mochymusic.ai.DjSetTrack
import com.mochyy.mochymusic.viewmodels.AiDjViewModel
import com.mochyy.mochymusic.viewmodels.DjSetResult
import java.util.Locale

@Composable
fun AiDjModeScreen(
    navController: NavController,
    viewModel: AiDjViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current
    val currentMeta = playerConnection?.mediaMetadata?.collectAsState()?.value
    val selectedPersona by viewModel.selectedPersona.collectAsState()
    val customVibe by viewModel.customVibe.collectAsState()
    val crossfadeSeconds by viewModel.crossfadeSeconds.collectAsState()
    val smartCrossmixEnabled by viewModel.smartCrossmixEnabled.collectAsState()
    val voiceIntrosEnabled by viewModel.voiceIntrosEnabled.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()

    val ttsReady = remember { mutableStateOf(false) }
    val tts = remember {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                engine?.language = Locale.US
                ttsReady.value = true
            }
        }
        engine
    }
    DisposableEffect(Unit) {
        onDispose {
            tts.stop()
            tts.shutdown()
        }
    }

    val currentTitle = currentMeta?.title
    val currentArtist = currentMeta?.artists?.joinToString(", ") { it.name }?.takeIf { it.isNotBlank() }

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
                            listOf(Color(0xFF21122E), Color(0xFF07080E))
                        )
                    )
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 8.dp, vertical = 10.dp),
            ) {
                IconButton(
                    onClick = { navController.popBackStack() },
                    modifier = Modifier.testTag("ai_dj_back_button"),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "AI DJ Deck & CrossMix",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Harmonic key matching, BPM arcs & seamless crossfading",
                        color = Color(0xFFB7A5D6),
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
            // 5 DJ Personas Carousel
            item(key = "dj_personas") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Select DJ Persona",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(DjPersona.entries, key = { it.name }) { persona ->
                            val selected = persona == selectedPersona
                            Column(
                                verticalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier
                                    .width(172.dp)
                                    .height(114.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(
                                        Brush.linearGradient(
                                            listOf(
                                                Color(persona.startColorHex),
                                                Color(persona.endColorHex),
                                            )
                                        )
                                    )
                                    .border(
                                        width = if (selected) 2.5.dp else 1.dp,
                                        color = if (selected) Color.White else Color.White.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(20.dp),
                                    )
                                    .clickable { viewModel.selectPersona(persona) }
                                    .testTag("dj_persona_${persona.name.lowercase()}")
                                    .padding(14.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        text = persona.defaultBpmRange,
                                        color = Color.White.copy(alpha = 0.9f),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    if (selected) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(50))
                                                .background(Color.White.copy(alpha = 0.25f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp),
                                        ) {
                                            Text(
                                                text = "ACTIVE",
                                                color = Color.White,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                            )
                                        }
                                    }
                                }
                                Column {
                                    Text(
                                        text = persona.title,
                                        color = Color.White,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        maxLines = 1,
                                    )
                                    Text(
                                        text = persona.subtitle,
                                        color = Color.White.copy(alpha = 0.85f),
                                        fontSize = 11.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // CrossMix & Optional Voice Intros Panel
            item(key = "dj_controls") {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFF101323))
                        .border(1.dp, Color(0xFF272D4B), RoundedCornerShape(18.dp))
                        .padding(14.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Smart CrossMix Transitions",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "Beat-aligned ${selectedPersona.preferredCrossmixStyle} on Media3",
                                color = Color(0xFF9EA4C8),
                                fontSize = 12.sp,
                            )
                        }
                        Switch(
                            checked = smartCrossmixEnabled,
                            onCheckedChange = viewModel::toggleSmartCrossmix,
                            modifier = Modifier.testTag("ai_dj_crossmix_switch"),
                        )
                    }

                    Column {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = "Crossfade Blend Window",
                                color = Color(0xFFD1D6F0),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                text = "${crossfadeSeconds.toInt()}s",
                                color = Color(0xFFF472B6),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Slider(
                            value = crossfadeSeconds,
                            onValueChange = viewModel::updateCrossfadeSeconds,
                            valueRange = 2f..12f,
                            steps = 9,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFFF472B6),
                                activeTrackColor = Color(0xFFEC4899),
                            ),
                            modifier = Modifier.testTag("ai_dj_crossfade_slider"),
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Optional DJ Voice Intros",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "Off by default — enable for brief spoken set intros",
                                color = Color(0xFF9EA4C8),
                                fontSize = 12.sp,
                            )
                        }
                        Switch(
                            checked = voiceIntrosEnabled,
                            onCheckedChange = viewModel::toggleVoiceIntros,
                            modifier = Modifier.testTag("ai_dj_voice_intro_switch"),
                        )
                    }

                    OutlinedTextField(
                        value = customVibe,
                        onValueChange = viewModel::updateCustomVibe,
                        singleLine = true,
                        placeholder = {
                            Text(
                                text = "Optional twist: e.g. 'blend Hindi house with Afro beats'",
                                color = Color(0xFF7B83A8),
                                fontSize = 13.sp,
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFFEC4899),
                            unfocusedBorderColor = Color(0xFF2B3252),
                            focusedContainerColor = Color(0xFF0A0D18),
                            unfocusedContainerColor = Color(0xFF0A0D18),
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ai_dj_vibe_input"),
                    )

                    Button(
                        onClick = {
                            viewModel.buildDjSet(
                                currentTrackTitle = currentTitle,
                                currentArtist = currentArtist,
                                onOptionalVoiceIntro = { introText ->
                                    if (ttsReady.value) {
                                        tts.speak(introText, TextToSpeech.QUEUE_FLUSH, null, "asl_dj_intro")
                                    }
                                },
                            )
                        },
                        enabled = uiState !is AiUiState.Loading,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEC4899)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("ai_dj_build_set_button"),
                    ) {
                        if (uiState is AiUiState.Loading) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Matching BPM & Harmonic Keys...",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                            )
                        } else {
                            Icon(
                                painter = painterResource(R.drawable.graphic_eq),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Build ${selectedPersona.title} Set",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                            )
                        }
                    }
                }
            }

            if (!statusMessage.isNullOrBlank()) {
                item(key = "dj_status") {
                    Text(
                        text = statusMessage.orEmpty(),
                        color = Color(0xFF6EE7B7),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }

            if (uiState is AiUiState.Error) {
                item(key = "dj_error") {
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
                        Text("AI DJ Session Error", color = Color(0xFFFCA5A5), fontWeight = FontWeight.Bold)
                        Text(msg, color = Color(0xFFFEE2E2), fontSize = 13.sp)
                    }
                }
            }

            item(key = "dj_set_result") {
                AnimatedVisibility(visible = uiState is AiUiState.Success) {
                    val result = (uiState as? AiUiState.Success<DjSetResult>)?.data
                    if (result != null) {
                        DjSetResultDeck(
                            result = result,
                            onLaunchMix = { viewModel.launchDjSetPlayback(playerConnection, result) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DjSetResultDeck(
    result: DjSetResult,
    onLaunchMix: () -> Unit,
) {
    val plan = result.plan
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Color(0xFF111526))
            .border(1.dp, Color(0xFFEC4899).copy(alpha = 0.45f), RoundedCornerShape(22.dp))
            .padding(16.dp),
    ) {
        Text(
            text = plan.setTitle,
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
        )
        Text(
            text = "${plan.bpmArcSummary}  •  ${plan.recommendedTransitionStyle}",
            color = Color(0xFFF9A8D4),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "Key Flow: ${plan.keyFlowSummary}",
            color = Color(0xFFA5B4FC),
            fontSize = 12.sp,
        )

        // Energy & BPM Arc Visualization
        DjEnergyArcCanvas(
            tracks = plan.tracks,
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF090C17))
                .border(1.dp, Color(0xFF252D4D), RoundedCornerShape(14.dp)),
        )

        Button(
            onClick = onLaunchMix,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("ai_dj_launch_playback_button"),
        ) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = Color.White,
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Launch Live DJ Mix (${result.resolvedTracks.size} Tracks)",
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            plan.tracks.forEachIndexed { idx, track ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF171C32))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = String.format(Locale.US, "%02d", idx + 1),
                        color = Color(0xFFF472B6),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
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
                            text = "${track.artist} • ${track.transitionNote}",
                            color = Color(0xFF9EA4C8),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "${track.estimatedBpm} BPM",
                            color = Color(0xFF93C5FD),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "${track.camelotKey} • ${track.energyPercent}%",
                            color = Color(0xFF6EE7B7),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DjEnergyArcCanvas(
    tracks: List<DjSetTrack>,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.padding(horizontal = 14.dp, vertical = 14.dp)) {
        if (tracks.isEmpty()) return@Canvas
        val w = size.width
        val h = size.height

        val pts = tracks.mapIndexed { idx, track ->
            val x = if (tracks.size == 1) w / 2f else (idx.toFloat() / (tracks.size - 1)) * w
            val norm = (track.energyPercent / 100f).coerceIn(0.1f, 1f)
            val y = h - norm * (h * 0.85f)
            Offset(x, y)
        }

        val line = Path().apply {
            moveTo(pts.first().x, pts.first().y)
            for (i in 0 until pts.lastIndex) {
                val p0 = pts[i]
                val p1 = pts[i + 1]
                val cx = (p0.x + p1.x) / 2f
                cubicTo(cx, p0.y, cx, p1.y, p1.x, p1.y)
            }
        }
        val fill = Path().apply {
            addPath(line)
            lineTo(pts.last().x, h)
            lineTo(pts.first().x, h)
            close()
        }
        drawPath(
            path = fill,
            brush = Brush.verticalGradient(
                listOf(Color(0xFFEC4899).copy(alpha = 0.32f), Color.Transparent)
            ),
        )
        drawPath(
            path = line,
            brush = Brush.horizontalGradient(
                listOf(Color(0xFF38BDF8), Color(0xFFEC4899), Color(0xFFF59E0B))
            ),
            style = Stroke(width = 4.5f, cap = StrokeCap.Round),
        )
        pts.forEach { pt ->
            drawCircle(color = Color(0xFF090C17), radius = 8f, center = pt)
            drawCircle(color = Color(0xFFF472B6), radius = 5f, center = pt)
        }
    }
}

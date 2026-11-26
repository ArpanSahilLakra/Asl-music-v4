package com.mochyy.mochymusic.ui.screens.ai

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.mochyy.mochymusic.ai.AiMasteringBand
import com.mochyy.mochymusic.ai.AiMasteringRecipe
import com.mochyy.mochymusic.ai.AiUiState
import com.mochyy.mochymusic.ai.AudioOutputProfile
import com.mochyy.mochymusic.viewmodels.AiMasteringViewModel
import java.util.Locale

private val QUICK_MASTERING_GOALS = listOf(
    "Punchy Sub-Bass",
    "Crystal Lead Vocals",
    "Wide Concert Stage",
    "Warm Analog Vinyl",
    "Late-Night Smooth",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiMasteringScreen(
    navController: NavController,
    viewModel: AiMasteringViewModel = hiltViewModel(),
) {
    val playerConnection = LocalPlayerConnection.current
    val currentMeta = playerConnection?.mediaMetadata?.collectAsState()?.value
    val selectedProfile by viewModel.selectedProfile.collectAsState()
    val customGoal by viewModel.customGoal.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val isMasterApplied by viewModel.isMasterApplied.collectAsState()

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
                            listOf(Color(0xFF141B2D), Color(0xFF07080E))
                        )
                    )
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 8.dp, vertical = 10.dp),
            ) {
                IconButton(
                    onClick = { navController.popBackStack() },
                    modifier = Modifier.testTag("ai_mastering_back_button"),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "AI Audio Mastering Studio",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "6-band Parametric EQ + Spatial, Reverb & Loudness DSP",
                        color = Color(0xFF93A5C9),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (isMasterApplied) Color(0xFF065F46) else Color(0xFF1C2035))
                        .border(
                            1.dp,
                            if (isMasterApplied) Color(0xFF34D399) else Color(0xFF31395C),
                            RoundedCornerShape(50),
                        )
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                ) {
                    Text(
                        text = if (isMasterApplied) "DSP LIVE" else "FLAT",
                        color = if (isMasterApplied) Color(0xFFA7F3D0) else Color(0xFF9EA4C8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
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
            // Active track context banner
            item(key = "now_playing_context") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF12182B), Color(0xFF191D35))
                            )
                        )
                        .border(1.dp, Color(0xFF283254), RoundedCornerShape(18.dp))
                        .padding(14.dp),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1D4ED8).copy(alpha = 0.28f)),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.tune),
                            contentDescription = null,
                            tint = Color(0xFF60A5FA),
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (currentTitle != null) "MASTERING TARGET TRACK" else "GLOBAL DSP PROFILE",
                            color = Color(0xFF60A5FA),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                        )
                        Text(
                            text = currentTitle ?: "No track playing — tuning for output hardware",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (currentArtist != null) {
                            Text(
                                text = currentArtist,
                                color = Color(0xFF9EA4C8),
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            // Output Hardware Profile selector
            item(key = "output_profiles") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Output Hardware Profile",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    AudioOutputProfile.entries.forEach { profile ->
                        val isSelected = profile == selectedProfile
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isSelected) Color(0xFF1A2547) else Color(0xFF111423))
                                .border(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) Color(0xFF60A5FA) else Color(0xFF232842),
                                    shape = RoundedCornerShape(16.dp),
                                )
                                .clickable { viewModel.selectProfile(profile) }
                                .testTag("mastering_profile_${profile.name.lowercase()}")
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = profile.label,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = profile.subtitle,
                                    color = Color(0xFF9EA4C8),
                                    fontSize = 12.sp,
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = "Selected",
                                    tint = Color(0xFF60A5FA),
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
            }

            // Custom sound goal + chips
            item(key = "custom_goal") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Sonic Tuning Goal (Optional)",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        QUICK_MASTERING_GOALS.forEach { goal ->
                            val active = customGoal.equals(goal, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(if (active) Color(0xFF2563EB) else Color(0xFF15192B))
                                    .border(
                                        1.dp,
                                        if (active) Color(0xFF93C5FD) else Color(0xFF28304F),
                                        RoundedCornerShape(50),
                                    )
                                    .clickable { viewModel.updateCustomGoal(goal) }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                            ) {
                                Text(
                                    text = goal,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = customGoal,
                        onValueChange = viewModel::updateCustomGoal,
                        placeholder = {
                            Text(
                                text = "e.g. Deeper 55Hz kick, clear vocals, subtle concert air",
                                color = Color(0xFF7B83A8),
                                fontSize = 13.sp,
                            )
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFF60A5FA),
                            unfocusedBorderColor = Color(0xFF2B3252),
                            focusedContainerColor = Color(0xFF101322),
                            unfocusedContainerColor = Color(0xFF101322),
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ai_mastering_goal_input"),
                    )
                }
            }

            // Primary Generate + Bypass/Flat controls
            item(key = "action_buttons") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { viewModel.generateAndApplyMaster(currentTitle, currentArtist) },
                        enabled = uiState !is AiUiState.Loading,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("ai_mastering_generate_button"),
                    ) {
                        if (uiState is AiUiState.Loading) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Designing 6-Band Master...",
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
                                text = "Generate & Apply AI Master",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        OutlinedButton(
                            onClick = viewModel::toggleBypass,
                            enabled = uiState is AiUiState.Success,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("ai_mastering_bypass_button"),
                        ) {
                            Text(
                                text = if (isMasterApplied) "Bypass DSP" else "Re-Apply Master",
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        OutlinedButton(
                            onClick = viewModel::resetFlat,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("ai_mastering_reset_button"),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = null,
                                tint = Color(0xFF93C5FD),
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Reset Flat",
                                color = Color(0xFF93C5FD),
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }

            // Error surface
            if (uiState is AiUiState.Error) {
                item(key = "error_card") {
                    val message = (uiState as AiUiState.Error).message
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
                            text = "Mastering Request Failed",
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

            // Generated Mastering Recipe Card + 6-Band EQ Curve Canvas
            item(key = "recipe_result") {
                AnimatedVisibility(visible = uiState is AiUiState.Success) {
                    val recipe = (uiState as? AiUiState.Success<AiMasteringRecipe>)?.data
                    if (recipe != null) {
                        MasteringRecipeCard(recipe = recipe, isLive = isMasterApplied)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MasteringRecipeCard(
    recipe: AiMasteringRecipe,
    isLive: Boolean,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF131B33), Color(0xFF0E1222))
                )
            )
            .border(1.dp, Color(0xFF3B82F6).copy(alpha = 0.45f), RoundedCornerShape(22.dp))
            .padding(16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = recipe.presetName,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = String.format(
                        Locale.US,
                        "Preamp %+.1f dB • %s",
                        recipe.preampDb,
                        if (isLive) "Applied to Audio Pipeline" else "Bypassed"
                    ),
                    color = Color(0xFF93C5FD),
                    fontSize = 12.sp,
                )
            }
        }

        if (recipe.summary.isNotBlank()) {
            Text(
                text = recipe.summary,
                color = Color(0xFFD1D8F0),
                fontSize = 13.sp,
                lineHeight = 19.sp,
            )
        }

        // Custom 6-Band EQ Curve Canvas
        EqResponseCurveCanvas(
            bands = recipe.bands,
            modifier = Modifier
                .fillMaxWidth()
                .height(144.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF090C18))
                .border(1.dp, Color(0xFF232C4A), RoundedCornerShape(16.dp)),
        )

        // DSP Chain Badges
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DspStatusBadge(
                label = "Spatial",
                value = if (recipe.spatialEnabled) "${(recipe.spatialAmount * 100).toInt()}%" else "Off",
                active = recipe.spatialEnabled,
            )
            DspStatusBadge(
                label = "Reverb",
                value = if (recipe.reverbEnabled) "${(recipe.reverbMix * 100).toInt()}% wet" else "Off",
                active = recipe.reverbEnabled,
            )
            DspStatusBadge(
                label = "Loudness",
                value = if (recipe.loudnessBoostDb > 0.25f) {
                    String.format(Locale.US, "+%.1f dB", recipe.loudnessBoostDb)
                } else {
                    "Off"
                },
                active = recipe.loudnessBoostDb > 0.25f,
            )
        }

        // Individual Band Breakdown
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            recipe.bands.forEach { band ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF151A2E))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = band.label,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = formatFrequency(band.frequencyHz),
                        color = Color(0xFF9EA4C8),
                        fontSize = 12.sp,
                        modifier = Modifier.width(68.dp),
                    )
                    Text(
                        text = String.format(Locale.US, "%+.1f dB", band.gainDb),
                        color = when {
                            band.gainDb > 0.4 -> Color(0xFF34D399)
                            band.gainDb < -0.4 -> Color(0xFFFBBF24)
                            else -> Color(0xFFD1D8F0)
                        },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun EqResponseCurveCanvas(
    bands: List<AiMasteringBand>,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.padding(horizontal = 14.dp, vertical = 14.dp)) {
        val w = size.width
        val h = size.height
        val midY = h / 2f

        // Zero-dB center line and +/- 6 dB guides
        drawLine(
            color = Color(0xFF283254),
            start = Offset(0f, midY),
            end = Offset(w, midY),
            strokeWidth = 1.5f,
        )
        drawLine(
            color = Color(0xFF1A2038),
            start = Offset(0f, h * 0.25f),
            end = Offset(w, h * 0.25f),
            strokeWidth = 1f,
        )
        drawLine(
            color = Color(0xFF1A2038),
            start = Offset(0f, h * 0.75f),
            end = Offset(w, h * 0.75f),
            strokeWidth = 1f,
        )

        if (bands.isEmpty()) return@Canvas

        val points = bands.mapIndexed { idx, band ->
            val x = if (bands.size == 1) w / 2f else (idx.toFloat() / (bands.size - 1)) * w
            val normalized = (band.gainDb.toFloat() / 12f).coerceIn(-1f, 1f)
            val y = midY - normalized * (h * 0.42f)
            Offset(x, y)
        }

        val curvePath = Path().apply {
            moveTo(points.first().x, points.first().y)
            for (i in 0 until points.lastIndex) {
                val p0 = points[i]
                val p1 = points[i + 1]
                val controlX = (p0.x + p1.x) / 2f
                cubicTo(controlX, p0.y, controlX, p1.y, p1.x, p1.y)
            }
        }

        val fillPath = Path().apply {
            addPath(curvePath)
            lineTo(points.last().x, midY)
            lineTo(points.first().x, midY)
            close()
        }

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                listOf(
                    Color(0xFF38BDF8).copy(alpha = 0.35f),
                    Color(0xFF6366F1).copy(alpha = 0.08f),
                )
            ),
        )
        drawPath(
            path = curvePath,
            brush = Brush.horizontalGradient(
                listOf(Color(0xFF38BDF8), Color(0xFF818CF8), Color(0xFFF472B6))
            ),
            style = Stroke(width = 5f, cap = StrokeCap.Round),
        )

        points.forEach { pt ->
            drawCircle(color = Color(0xFF07080E), radius = 9f, center = pt)
            drawCircle(color = Color(0xFF60A5FA), radius = 6f, center = pt)
        }
    }
}

@Composable
private fun DspStatusBadge(
    label: String,
    value: String,
    active: Boolean,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (active) Color(0xFF1D3461) else Color(0xFF141829))
            .border(
                1.dp,
                if (active) Color(0xFF60A5FA) else Color(0xFF282F4D),
                RoundedCornerShape(50),
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = "$label:",
            color = Color(0xFF9EA4C8),
            fontSize = 12.sp,
        )
        Text(
            text = value,
            color = if (active) Color(0xFFBFDBFE) else Color(0xFF7D85A8),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun formatFrequency(hz: Double): String {
    return if (hz >= 1000.0) {
        String.format(Locale.US, "%.1f kHz", hz / 1000.0)
    } else {
        String.format(Locale.US, "%.0f Hz", hz)
    }
}

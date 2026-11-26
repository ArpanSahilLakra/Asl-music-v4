package com.mochyy.mochymusic.ui.screens.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.navigation.NavController
import com.mochyy.mochymusic.R

/**
 * Compact, high-craft entry shelf on HomeScreen providing 1-tap access to ASL Beats AI Studio
 * tools without displacing existing Home sections.
 */
@Composable
fun AiStudioHomeShelf(
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = null,
                tint = Color(0xFF818CF8),
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "ASL AI Studio",
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "studio_assistant") {
                StudioShortcutTile(
                    title = "AI Music DJ",
                    subtitle = "Voice & chat control",
                    iconRes = R.drawable.graphic_eq,
                    colors = listOf(Color(0xFF4F46E5), Color(0xFF7C3AED)),
                    tag = "home_open_ai_assistant",
                    onClick = { navController.navigate("ai_assistant") },
                )
            }
            item(key = "studio_mood") {
                StudioShortcutTile(
                    title = "Mood Mixes",
                    subtitle = "12 AI vibe presets",
                    iconRes = R.drawable.queue_music,
                    colors = listOf(Color(0xFFEC4899), Color(0xFFBE185D)),
                    tag = "home_open_mood_playlists",
                    onClick = { navController.navigate("ai_mood_playlist") },
                )
            }
            item(key = "studio_visualizer") {
                StudioShortcutTile(
                    title = "3D Visualizer",
                    subtitle = "Live PCM FFT studio",
                    iconRes = R.drawable.equalizer,
                    colors = listOf(Color(0xFF0891B2), Color(0xFF1D4ED8)),
                    tag = "home_open_visualizer_studio",
                    onClick = { navController.navigate("visualizer_studio") },
                )
            }
            item(key = "studio_mastering") {
                StudioShortcutTile(
                    title = "AI Mastering",
                    subtitle = "6-band EQ & spatial",
                    iconRes = R.drawable.tune,
                    colors = listOf(Color(0xFF2563EB), Color(0xFF1D4ED8)),
                    tag = "home_open_ai_mastering",
                    onClick = { navController.navigate("ai_mastering") },
                )
            }
            item(key = "studio_insights") {
                StudioShortcutTile(
                    title = "Sonic DNA",
                    subtitle = "AI music personality",
                    iconRes = R.drawable.sparks,
                    colors = listOf(Color(0xFF9333EA), Color(0xFF6D28D9)),
                    tag = "home_open_ai_insights",
                    onClick = { navController.navigate("ai_insights") },
                )
            }
            item(key = "studio_alarms") {
                StudioShortcutTile(
                    title = "Music Alarm",
                    subtitle = "Fade-in wake mixes",
                    iconRes = R.drawable.bedtime,
                    colors = listOf(Color(0xFFD97706), Color(0xFFB45309)),
                    tag = "home_open_music_alarms",
                    onClick = { navController.navigate("music_alarms") },
                )
            }
            item(key = "studio_dj_mode") {
                StudioShortcutTile(
                    title = "AI DJ Deck",
                    subtitle = "5 personas & CrossMix",
                    iconRes = R.drawable.discover_tune,
                    colors = listOf(Color(0xFFDB2777), Color(0xFF9D174D)),
                    tag = "home_open_ai_dj_mode",
                    onClick = { navController.navigate("ai_dj_mode") },
                )
            }
            item(key = "studio_screenshot_scan") {
                StudioShortcutTile(
                    title = "Photo to Song",
                    subtitle = "Scan covers & playlists",
                    iconRes = R.drawable.sparks,
                    colors = listOf(Color(0xFF0E7490), Color(0xFF155E75)),
                    tag = "home_open_screenshot_to_music",
                    onClick = { navController.navigate("screenshot_to_music") },
                )
            }
            item(key = "studio_hygiene") {
                StudioShortcutTile(
                    title = "Smart Cleaner",
                    subtitle = "Dupes & temp cache",
                    iconRes = R.drawable.history,
                    colors = listOf(Color(0xFF047857), Color(0xFF065F46)),
                    tag = "home_open_smart_hygiene",
                    onClick = { navController.navigate("smart_library_hygiene") },
                )
            }
            item(key = "studio_saved_lyrics") {
                StudioShortcutTile(
                    title = "Lyric Vault",
                    subtitle = "Saved passages",
                    iconRes = R.drawable.lyrics,
                    colors = listOf(Color(0xFF059669), Color(0xFF047857)),
                    tag = "home_open_saved_lyrics",
                    onClick = { navController.navigate("saved_lyrics") },
                )
            }
        }
    }
}

@Composable
private fun StudioShortcutTile(
    title: String,
    subtitle: String,
    iconRes: Int,
    colors: List<Color>,
    tag: String,
    onClick: () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .width(152.dp)
            .height(92.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.linearGradient(colors))
            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .testTag(tag)
            .padding(12.dp),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = title,
            tint = Color.White,
            modifier = Modifier.size(22.dp),
        )
        Column {
            Text(
                text = title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                color = Color.White.copy(alpha = 0.84f),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

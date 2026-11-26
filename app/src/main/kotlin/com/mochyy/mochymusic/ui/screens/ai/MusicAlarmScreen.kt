package com.mochyy.mochymusic.ui.screens.ai

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.db.entities.MusicAlarmEntity
import com.mochyy.mochymusic.viewmodels.MusicAlarmViewModel
import java.util.Locale

private val DAY_LABELS = listOf("M", "T", "W", "T", "F", "S", "S")
private val FADE_PRESETS = listOf(10, 20, 30, 45)

@Composable
fun MusicAlarmScreen(
    navController: NavController,
    viewModel: MusicAlarmViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val alarms by viewModel.alarms.collectAsState()

    var hour by rememberSaveable { mutableIntStateOf(7) }
    var minute by rememberSaveable { mutableIntStateOf(0) }
    var label by rememberSaveable { mutableStateOf("Morning Mix") }
    var repeatDaysMask by rememberSaveable { mutableIntStateOf(0b0011111) } // Mon-Fri default
    var fadeInSeconds by rememberSaveable { mutableIntStateOf(20) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        viewModel.addAlarm(
            label = label,
            hour = hour,
            minute = minute,
            repeatDaysMask = repeatDaysMask,
            fadeInSeconds = fadeInSeconds,
        )
    }

    fun scheduleAlarmWithPermissionCheck() {
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.addAlarm(
                label = label,
                hour = hour,
                minute = minute,
                repeatDaysMask = repeatDaysMask,
                fadeInSeconds = fadeInSeconds,
            )
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
                            listOf(Color(0xFF241A12), Color(0xFF07080E))
                        )
                    )
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 8.dp, vertical = 10.dp),
            ) {
                IconButton(
                    onClick = { navController.popBackStack() },
                    modifier = Modifier.testTag("music_alarm_back_button"),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Smart Music Alarms",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Wake up gently with scheduled fade-in mixes",
                        color = Color(0xFFD6B99A),
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
            // Alarm Composer Card
            item(key = "alarm_composer") {
                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF1A1525), Color(0xFF101220))
                            )
                        )
                        .border(1.dp, Color(0xFFF59E0B).copy(alpha = 0.4f), RoundedCornerShape(22.dp))
                        .padding(16.dp),
                ) {
                    Text(
                        text = "Create Wake-Up Mix",
                        color = Color(0xFFFCD34D),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                    )

                    // Digital Time Stepper
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        TimeUnitStepper(
                            value = String.format(Locale.US, "%02d", hour),
                            label = "HOUR",
                            onIncrement = { hour = (hour + 1) % 24 },
                            onDecrement = { hour = (hour + 23) % 24 },
                            tagPrefix = "alarm_hour",
                        )
                        Text(
                            text = ":",
                            color = Color(0xFFF59E0B),
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 14.dp),
                        )
                        TimeUnitStepper(
                            value = String.format(Locale.US, "%02d", minute),
                            label = "MIN",
                            onIncrement = { minute = (minute + 5) % 60 },
                            onDecrement = { minute = (minute + 55) % 60 },
                            tagPrefix = "alarm_minute",
                        )
                    }

                    // Label Input
                    OutlinedTextField(
                        value = label,
                        onValueChange = { label = it },
                        singleLine = true,
                        label = { Text("Alarm label", color = Color(0xFF9EA4C8)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFFF59E0B),
                            unfocusedBorderColor = Color(0xFF2B3252),
                            focusedContainerColor = Color(0xFF0C0F1C),
                            unfocusedContainerColor = Color(0xFF0C0F1C),
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("music_alarm_label_input"),
                    )

                    // Repeat Day Pills
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Repeat Days",
                            color = Color(0xFFD1D5EC),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            DAY_LABELS.forEachIndexed { bitIndex, dayLetter ->
                                val bit = 1 shl bitIndex
                                val active = (repeatDaysMask and bit) != 0
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(if (active) Color(0xFFD97706) else Color(0xFF171B2E))
                                        .border(
                                            1.dp,
                                            if (active) Color(0xFFFCD34D) else Color(0xFF2A3152),
                                            CircleShape,
                                        )
                                        .clickable { repeatDaysMask = repeatDaysMask xor bit }
                                        .testTag("alarm_day_$bitIndex"),
                                ) {
                                    Text(
                                        text = dayLetter,
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }
                    }

                    // Gentle Fade-In Selector
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Gentle Fade-In Duration",
                            color = Color(0xFFD1D5EC),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            FADE_PRESETS.forEach { seconds ->
                                val selected = fadeInSeconds == seconds
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(42.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (selected) Color(0xFF78350F) else Color(0xFF15192B))
                                        .border(
                                            1.dp,
                                            if (selected) Color(0xFFF59E0B) else Color(0xFF28304F),
                                            RoundedCornerShape(12.dp),
                                        )
                                        .clickable { fadeInSeconds = seconds },
                                ) {
                                    Text(
                                        text = "${seconds}s",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                        }
                    }

                    Button(
                        onClick = { scheduleAlarmWithPermissionCheck() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("music_alarm_add_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Schedule Music Alarm",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            // Saved Alarms Header
            item(key = "saved_header") {
                Text(
                    text = "Scheduled Alarms (${alarms.size})",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            if (alarms.isEmpty()) {
                item(key = "empty_alarms") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color(0xFF101322))
                            .border(1.dp, Color(0xFF232842), RoundedCornerShape(18.dp))
                            .padding(16.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.bedtime),
                            contentDescription = null,
                            tint = Color(0xFFF59E0B),
                            modifier = Modifier.size(28.dp),
                        )
                        Column {
                            Text(
                                text = "No music alarms scheduled",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "Pick a wake-up time above to start your day with a gentle fade-in mix.",
                                color = Color(0xFF9EA4C8),
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
            } else {
                items(alarms, key = { it.id }) { alarm ->
                    MusicAlarmCard(
                        alarm = alarm,
                        onToggle = { enabled -> viewModel.toggleAlarm(alarm, enabled) },
                        onDelete = { viewModel.deleteAlarm(alarm) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TimeUnitStepper(
    value: String,
    label: String,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    tagPrefix: String,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF0C0F1C))
            .border(1.dp, Color(0xFF283052), RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        IconButton(
            onClick = onIncrement,
            modifier = Modifier.testTag("${tagPrefix}_inc"),
        ) {
            Icon(
                imageVector = Icons.Filled.KeyboardArrowUp,
                contentDescription = "Increase $label",
                tint = Color(0xFFFCD34D),
            )
        }
        Text(
            text = value,
            color = Color.White,
            fontSize = 34.sp,
            fontWeight = FontWeight.ExtraBold,
        )
        Text(
            text = label,
            color = Color(0xFF8B93B8),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
        )
        IconButton(
            onClick = onDecrement,
            modifier = Modifier.testTag("${tagPrefix}_dec"),
        ) {
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = "Decrease $label",
                tint = Color(0xFFFCD34D),
            )
        }
    }
}

@Composable
private fun MusicAlarmCard(
    alarm: MusicAlarmEntity,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF111526))
            .border(
                1.dp,
                if (alarm.enabled) Color(0xFFF59E0B).copy(alpha = 0.5f) else Color(0xFF232842),
                RoundedCornerShape(18.dp),
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Notifications,
            contentDescription = null,
            tint = if (alarm.enabled) Color(0xFFFCD34D) else Color(0xFF6B7280),
            modifier = Modifier.size(22.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = String.format(Locale.US, "%02d:%02d", alarm.hour, alarm.minute),
                color = if (alarm.enabled) Color.White else Color(0xFF8B93B8),
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                text = alarm.label,
                color = Color(0xFFD5DCF5),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${formatRepeatSummary(alarm.repeatDaysMask)} • ${alarm.fadeInSeconds}s fade-in",
                color = Color(0xFF9EA4C8),
                fontSize = 11.sp,
            )
        }
        Switch(
            checked = alarm.enabled,
            onCheckedChange = onToggle,
            modifier = Modifier.testTag("music_alarm_toggle_${alarm.id}"),
        )
        IconButton(
            onClick = onDelete,
            modifier = Modifier.testTag("music_alarm_delete_${alarm.id}"),
        ) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = "Delete alarm",
                tint = Color(0xFFF87171),
            )
        }
    }
}

private fun formatRepeatSummary(mask: Int): String {
    if (mask == 0) return "One-time"
    if (mask == 0b1111111) return "Every day"
    if (mask == 0b0011111) return "Weekdays"
    if (mask == 0b1100000) return "Weekends"
    val names = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    return names.filterIndexed { idx, _ -> (mask and (1 shl idx)) != 0 }.joinToString(", ")
}

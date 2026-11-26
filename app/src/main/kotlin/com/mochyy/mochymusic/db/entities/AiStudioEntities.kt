package com.mochyy.mochymusic.db.entities

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Immutable
@Entity(
    tableName = "ai_chat_message",
    indices = [Index(value = ["createdAt"])]
)
data class AiChatMessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val role: String, // "user" or "assistant"
    val content: String,
    val actionSummary: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Immutable
@Entity(
    tableName = "saved_lyric_passage",
    indices = [Index(value = ["songId"]), Index(value = ["savedAt"])]
)
data class SavedLyricPassageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val songId: String,
    val songTitle: String,
    val artistName: String,
    val passageText: String,
    val note: String? = null,
    val savedAt: Long = System.currentTimeMillis(),
)

@Immutable
@Entity(
    tableName = "music_alarm",
    indices = [Index(value = ["enabled"])]
)
data class MusicAlarmEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val label: String,
    val hour: Int,
    val minute: Int,
    val enabled: Boolean = true,
    val repeatDaysMask: Int = 0,
    val targetSongId: String? = null,
    val targetPlaylistId: String? = null,
    val fadeInSeconds: Int = 20,
)

@Immutable
@Entity(
    tableName = "custom_visualizer_preset",
    indices = [Index(value = ["createdAt"])]
)
data class CustomVisualizerPresetEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val mode: String,
    val sensitivity: Float = 1.0f,
    val glowIntensity: Float = 0.8f,
    val albumReactiveLighting: Boolean = true,
    val batterySaverFps: Boolean = false,
    val accentArgb: Int = 0xFF6366F1.toInt(),
    val createdAt: Long = System.currentTimeMillis(),
)

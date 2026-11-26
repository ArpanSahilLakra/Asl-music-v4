package com.mochyy.mochymusic.db.daos

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.mochyy.mochymusic.db.entities.AiChatMessageEntity
import com.mochyy.mochymusic.db.entities.CustomVisualizerPresetEntity
import com.mochyy.mochymusic.db.entities.MusicAlarmEntity
import com.mochyy.mochymusic.db.entities.SavedLyricPassageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AiStudioDao {
    // AI Assistant Chat History
    @Query("SELECT * FROM ai_chat_message ORDER BY createdAt ASC, id ASC")
    fun observeChatMessages(): Flow<List<AiChatMessageEntity>>

    @Query("SELECT * FROM ai_chat_message ORDER BY createdAt DESC, id DESC LIMIT :limit")
    suspend fun recentChatMessages(limit: Int = 20): List<AiChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChatMessage(message: AiChatMessageEntity): Long

    @Query("UPDATE ai_chat_message SET content = :content, actionSummary = :actionSummary WHERE id = :id")
    suspend fun updateChatMessage(id: Long, content: String, actionSummary: String?)

    @Query("DELETE FROM ai_chat_message")
    suspend fun clearChatHistory()

    // Saved Favorite Lyric Passages
    @Query("SELECT * FROM saved_lyric_passage ORDER BY savedAt DESC, id DESC")
    fun observeSavedLyricPassages(): Flow<List<SavedLyricPassageEntity>>

    @Query("SELECT * FROM saved_lyric_passage WHERE songId = :songId ORDER BY savedAt DESC")
    fun observeSavedLyricPassagesForSong(songId: String): Flow<List<SavedLyricPassageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSavedLyricPassage(passage: SavedLyricPassageEntity): Long

    @Query("DELETE FROM saved_lyric_passage WHERE id = :id")
    suspend fun deleteSavedLyricPassage(id: Long)

    // Music Alarms (schema ready for Phase 2 & instant persistence)
    @Query("SELECT * FROM music_alarm ORDER BY hour ASC, minute ASC")
    fun observeMusicAlarms(): Flow<List<MusicAlarmEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMusicAlarm(alarm: MusicAlarmEntity): Long

    @Update
    suspend fun updateMusicAlarm(alarm: MusicAlarmEntity)

    @Query("DELETE FROM music_alarm WHERE id = :id")
    suspend fun deleteMusicAlarm(id: Long)

    // Custom Visualizer Presets
    @Query("SELECT * FROM custom_visualizer_preset ORDER BY createdAt DESC, id DESC")
    fun observeVisualizerPresets(): Flow<List<CustomVisualizerPresetEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVisualizerPreset(preset: CustomVisualizerPresetEntity): Long

    @Query("DELETE FROM custom_visualizer_preset WHERE id = :id")
    suspend fun deleteVisualizerPreset(id: Long)
}

package com.mochyy.mochymusic.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mochyy.mochymusic.alarm.MusicAlarmScheduler
import com.mochyy.mochymusic.db.MusicDatabase
import com.mochyy.mochymusic.db.entities.MusicAlarmEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MusicAlarmViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val database: MusicDatabase,
) : ViewModel() {

    val alarms: StateFlow<List<MusicAlarmEntity>> = database.aiStudioDao
        .observeMusicAlarms()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    fun addAlarm(
        label: String,
        hour: Int,
        minute: Int,
        repeatDaysMask: Int,
        fadeInSeconds: Int,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val entity = MusicAlarmEntity(
                label = label.trim().ifBlank { "Morning Mix" },
                hour = hour.coerceIn(0, 23),
                minute = minute.coerceIn(0, 59),
                enabled = true,
                repeatDaysMask = repeatDaysMask,
                fadeInSeconds = fadeInSeconds.coerceIn(5, 60),
            )
            val insertedId = database.aiStudioDao.insertMusicAlarm(entity)
            MusicAlarmScheduler.schedule(appContext, entity.copy(id = insertedId))
        }
    }

    fun toggleAlarm(alarm: MusicAlarmEntity, enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = alarm.copy(enabled = enabled)
            database.aiStudioDao.updateMusicAlarm(updated)
            if (enabled) {
                MusicAlarmScheduler.schedule(appContext, updated)
            } else {
                MusicAlarmScheduler.cancel(appContext, updated.id)
            }
        }
    }

    fun deleteAlarm(alarm: MusicAlarmEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            MusicAlarmScheduler.cancel(appContext, alarm.id)
            database.aiStudioDao.deleteMusicAlarm(alarm.id)
        }
    }
}

package com.mochyy.mochymusic.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.mochyy.mochymusic.db.entities.MusicAlarmEntity
import java.util.Calendar

object MusicAlarmScheduler {

    const val EXTRA_ALARM_ID = "asl_alarm_id"
    const val EXTRA_ALARM_LABEL = "asl_alarm_label"
    const val EXTRA_FADE_IN_SECONDS = "asl_alarm_fade_in"

    fun computeNextTriggerMillis(
        hour: Int,
        minute: Int,
        repeatDaysMask: Int,
        nowMillis: Long = System.currentTimeMillis(),
    ): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
            set(Calendar.MINUTE, minute.coerceIn(0, 59))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (repeatDaysMask == 0) {
            if (cal.timeInMillis <= nowMillis) {
                cal.add(Calendar.DAY_OF_YEAR, 1)
            }
            return cal.timeInMillis
        }

        // Bit 0 = Monday .. Bit 6 = Sunday
        for (offset in 0..7) {
            val candidate = (cal.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, offset)
            }
            if (candidate.timeInMillis <= nowMillis) continue
            val dayBit = calendarDayToBit(candidate.get(Calendar.DAY_OF_WEEK))
            if ((repeatDaysMask and dayBit) != 0) {
                return candidate.timeInMillis
            }
        }
        cal.add(Calendar.DAY_OF_YEAR, 1)
        return cal.timeInMillis
    }

    private fun calendarDayToBit(dayOfWeek: Int): Int = when (dayOfWeek) {
        Calendar.MONDAY -> 1 shl 0
        Calendar.TUESDAY -> 1 shl 1
        Calendar.WEDNESDAY -> 1 shl 2
        Calendar.THURSDAY -> 1 shl 3
        Calendar.FRIDAY -> 1 shl 4
        Calendar.SATURDAY -> 1 shl 5
        Calendar.SUNDAY -> 1 shl 6
        else -> 1 shl 0
    }

    fun schedule(context: Context, alarm: MusicAlarmEntity) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pendingIntent = buildPendingIntent(context, alarm)
        if (!alarm.enabled) {
            alarmManager.cancel(pendingIntent)
            return
        }
        val triggerAt = computeNextTriggerMillis(alarm.hour, alarm.minute, alarm.repeatDaysMask)
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
    }

    fun cancel(context: Context, alarmId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, MusicAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarmId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager.cancel(pendingIntent)
    }

    private fun buildPendingIntent(context: Context, alarm: MusicAlarmEntity): PendingIntent {
        val intent = Intent(context, MusicAlarmReceiver::class.java).apply {
            putExtra(EXTRA_ALARM_ID, alarm.id)
            putExtra(EXTRA_ALARM_LABEL, alarm.label)
            putExtra(EXTRA_FADE_IN_SECONDS, alarm.fadeInSeconds)
        }
        return PendingIntent.getBroadcast(
            context,
            alarm.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

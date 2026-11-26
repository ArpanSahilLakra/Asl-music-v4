package com.mochyy.mochymusic.alarm

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.mochyy.mochymusic.MainActivity
import com.mochyy.mochymusic.R

class MusicAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getLongExtra(MusicAlarmScheduler.EXTRA_ALARM_ID, 0L)
        val label = intent.getStringExtra(MusicAlarmScheduler.EXTRA_ALARM_LABEL)
            ?.takeIf { it.isNotBlank() } ?: "ASL Beats Wake-Up Mix"
        val fadeSeconds = intent.getIntExtra(MusicAlarmScheduler.EXTRA_FADE_IN_SECONDS, 20)

        ensureChannel(context)

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MusicAlarmScheduler.EXTRA_ALARM_ID, alarmId)
            putExtra(MusicAlarmScheduler.EXTRA_FADE_IN_SECONDS, fadeSeconds)
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            alarmId.toInt(),
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.music_note)
            .setContentTitle(label)
            .setContentText("Tap to start your morning mix (${fadeSeconds}s gentle fade-in)")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        val canNotify = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (canNotify) {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_BASE_ID + alarmId.toInt(), notification)
        }
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "ASL Beats Music Alarms",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = "Scheduled musical wake-up notifications"
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    companion object {
        private const val CHANNEL_ID = "asl_beats_music_alarms"
        private const val NOTIFICATION_BASE_ID = 42_100
    }
}

package com.kap.record.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.kap.record.Constants
import com.kap.record.MainActivity
import com.kap.record.R
import com.kap.record.service.ScreenRecordService

object RecordingNotification {

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(Constants.NOTIFICATION_CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    Constants.NOTIFICATION_CHANNEL_ID,
                    context.getString(R.string.notification_channel_name),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = context.getString(R.string.notification_channel_description)
                    setSound(null, null)
                    enableVibration(false)
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    fun buildArmed(context: Context): Notification =
        baseBuilder(context)
            .setContentTitle(context.getString(R.string.notification_armed_title))
            .setContentText(context.getString(R.string.notification_armed_text))
            .build()

    fun buildRecording(context: Context, elapsedMs: Long, isPaused: Boolean): Notification {
        val builder = baseBuilder(context)
            .setContentTitle(
                context.getString(
                    if (isPaused) R.string.notification_paused_title else R.string.notification_recording_title
                )
            )
            .setContentText(formatElapsed(elapsedMs))

        builder.addAction(if (isPaused) actionResume(context) else actionPause(context))
        builder.addAction(actionStop(context))
        return builder.build()
    }

    private fun baseBuilder(context: Context): NotificationCompat.Builder {
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(context, Constants.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tile_record)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
    }

    private fun actionPause(context: Context): NotificationCompat.Action =
        NotificationCompat.Action.Builder(
            0,
            context.getString(R.string.action_pause),
            servicePendingIntent(context, Constants.ACTION_PAUSE, 1)
        ).build()

    private fun actionResume(context: Context): NotificationCompat.Action =
        NotificationCompat.Action.Builder(
            0,
            context.getString(R.string.action_resume),
            servicePendingIntent(context, Constants.ACTION_RESUME, 2)
        ).build()

    private fun actionStop(context: Context): NotificationCompat.Action =
        NotificationCompat.Action.Builder(
            0,
            context.getString(R.string.action_stop),
            servicePendingIntent(context, Constants.ACTION_STOP, 3)
        ).build()

    private fun servicePendingIntent(context: Context, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, ScreenRecordService::class.java).setAction(action)
        return PendingIntent.getService(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun formatElapsed(elapsedMs: Long): String {
        val totalSeconds = elapsedMs / 1000
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return if (h > 0) String.format("%02d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
    }
}

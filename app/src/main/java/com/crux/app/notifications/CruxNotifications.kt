package com.crux.app.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.crux.app.MainActivity
import com.crux.app.R

/**
 * The three notification channels (data-model / user-choices): a morning digest, due-now nudges, and
 * an evening wrap. Created once at app start; the user tunes each in settings. minSdk is 26, so
 * channels always exist.
 */
object CruxNotifications {
    const val CHANNEL_MORNING = "morning"
    const val CHANNEL_DUE = "due"
    const val CHANNEL_WRAP = "wrap"

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_MORNING, "morning", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "the day's climb, each morning"
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_DUE, "due", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "a task's time has arrived"
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_WRAP, "wrap", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "the evening wrap-up"
            },
        )
    }

    /** Post a notification that opens the app. Silently no-ops if permission is not granted (33+). */
    fun post(context: Context, channel: String, title: String, text: String, notificationId: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        val launch = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context, 0, launch,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }
}

package com.crux.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

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
}

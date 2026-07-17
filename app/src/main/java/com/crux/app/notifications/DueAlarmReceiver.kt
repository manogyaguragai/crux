package com.crux.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Fires when a timed task reaches its moment; posts a "due" notification on that channel. */
class DueAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra(DueAlarms.EXTRA_TASK_ID, -1L)
        val title = intent.getStringExtra(DueAlarms.EXTRA_TITLE).orEmpty()
        if (taskId < 0 || title.isBlank()) return
        CruxNotifications.post(
            context = context,
            channel = CruxNotifications.CHANNEL_DUE,
            title = title,
            text = "due now",
            notificationId = ("due_$taskId").hashCode(),
        )
    }
}

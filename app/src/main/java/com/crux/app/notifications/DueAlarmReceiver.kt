package com.crux.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Fires when a timed task reaches its moment (or its earlier reminder offset); posts on the "due"
 * channel. A reminder alarm carries [DueAlarms.ACTION_REMIND] and reads "due in Nm"; the due alarm
 * itself reads "due now". The two use distinct notification ids so a reminder never replaces the due.
 */
class DueAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra(DueAlarms.EXTRA_TASK_ID, -1L)
        val title = intent.getStringExtra(DueAlarms.EXTRA_TITLE).orEmpty()
        if (taskId < 0 || title.isBlank()) return
        val remind = intent.action == DueAlarms.ACTION_REMIND
        val offset = intent.getIntExtra(DueAlarms.EXTRA_OFFSET, 0)
        CruxNotifications.post(
            context = context,
            channel = CruxNotifications.CHANNEL_DUE,
            title = title,
            text = if (remind) "due in ${offset}m" else "due now",
            notificationId = ((if (remind) "remind_" else "due_") + taskId).hashCode(),
        )
    }
}

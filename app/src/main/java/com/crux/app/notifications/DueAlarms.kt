package com.crux.app.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.crux.app.domain.model.Task

/**
 * Per-task "due now" reminders via exact AlarmManager alarms (only for tasks that carry a time).
 * Alarms persist across process death but not reboot, so [BootReceiver] re-arms them; while the app
 * runs, CruxApplication re-arms the whole set whenever the open tasks or the due toggle change.
 *
 * In-process it tracks which task ids are armed so it can cancel a task that has been completed,
 * edited to no time, or moved past due. Only future-dated tasks are armed (a past time would fire at
 * once); a task that has slipped past its time simply gets cancelled on the next re-arm.
 */
object DueAlarms {
    const val EXTRA_TASK_ID = "task_id"
    const val EXTRA_TITLE = "title"
    const val EXTRA_OFFSET = "offset"
    // the reminder alarm carries its own action so its PendingIntent never collides with the due one
    // (PendingIntent equality ignores extras but honours the action), even at the same request code.
    const val ACTION_REMIND = "com.crux.app.REMIND"

    private val armedDue = mutableSetOf<Long>()
    private val armedRemind = mutableSetOf<Long>()

    @Synchronized
    fun rescheduleAll(context: Context, openTasks: List<Task>, dueEnabled: Boolean, now: Long) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return

        // due alarms: the task's own time.
        val wantedDue = if (!dueEnabled) emptyList() else openTasks.filter {
            it.hasTime && it.dueAt != null && it.dueAt > now
        }
        val wantedDueIds = wantedDue.map { it.id }.toSet()
        (armedDue - wantedDueIds).forEach { cancel(context, duePendingIntent(context, it, "")) }
        armedDue.clear()
        wantedDue.forEach { task ->
            val at = task.dueAt ?: return@forEach
            arm(manager, at, duePendingIntent(context, task.id, task.title))
            armedDue += task.id
        }

        // reminder alarms: an offset before a timed due (shares the due toggle).
        val wantedRemind = if (!dueEnabled) emptyList() else openTasks.filter {
            it.hasTime && it.dueAt != null && it.remindOffsetMinutes != null &&
                it.dueAt - it.remindOffsetMinutes * 60_000L > now
        }
        val wantedRemindIds = wantedRemind.map { it.id }.toSet()
        (armedRemind - wantedRemindIds).forEach { cancel(context, remindPendingIntent(context, it, "", 0)) }
        armedRemind.clear()
        wantedRemind.forEach { task ->
            val offset = task.remindOffsetMinutes ?: return@forEach
            val at = (task.dueAt ?: return@forEach) - offset * 60_000L
            arm(manager, at, remindPendingIntent(context, task.id, task.title, offset))
            armedRemind += task.id
        }
    }

    private fun arm(manager: AlarmManager, at: Long, pending: PendingIntent) {
        if (canScheduleExact(manager)) {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        } else {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        }
    }

    private fun cancel(context: Context, pending: PendingIntent) {
        context.getSystemService(AlarmManager::class.java)?.cancel(pending)
    }

    private fun canScheduleExact(manager: AlarmManager): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()

    private fun duePendingIntent(context: Context, taskId: Long, title: String): PendingIntent {
        val intent = Intent(context, DueAlarmReceiver::class.java).apply {
            putExtra(EXTRA_TASK_ID, taskId)
            putExtra(EXTRA_TITLE, title)
        }
        return PendingIntent.getBroadcast(
            context,
            taskId.toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun remindPendingIntent(context: Context, taskId: Long, title: String, offset: Int): PendingIntent {
        val intent = Intent(context, DueAlarmReceiver::class.java).apply {
            action = ACTION_REMIND
            putExtra(EXTRA_TASK_ID, taskId)
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_OFFSET, offset)
        }
        return PendingIntent.getBroadcast(
            context,
            taskId.toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}

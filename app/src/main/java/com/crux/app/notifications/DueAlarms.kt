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

    private val armed = mutableSetOf<Long>()

    @Synchronized
    fun rescheduleAll(context: Context, openTasks: List<Task>, dueEnabled: Boolean, now: Long) {
        val wanted = if (!dueEnabled) emptyList() else openTasks.filter {
            it.hasTime && it.dueAt != null && it.dueAt > now
        }
        val wantedIds = wanted.map { it.id }.toSet()

        // cancel alarms we no longer want (completed, un-timed, slipped past, or toggle off)
        (armed - wantedIds).forEach { cancel(context, it) }
        armed.clear()

        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        wanted.forEach { task ->
            val at = task.dueAt ?: return@forEach
            val pending = pendingIntent(context, task.id, task.title)
            if (canScheduleExact(manager)) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            } else {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            }
            armed += task.id
        }
    }

    private fun cancel(context: Context, taskId: Long) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        manager.cancel(pendingIntent(context, taskId, title = ""))
    }

    private fun canScheduleExact(manager: AlarmManager): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()

    private fun pendingIntent(context: Context, taskId: Long, title: String): PendingIntent {
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
}

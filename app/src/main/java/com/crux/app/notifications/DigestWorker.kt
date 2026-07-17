package com.crux.app.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.crux.app.CruxApplication
import com.crux.app.domain.isOverdue
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.ZoneId

/**
 * Posts the morning or wrap digest, then reschedules itself for the next day. Content is a calm
 * count, never a list of nags. Re-reads settings on each run so a toggle flipped off between firings
 * is honoured, and silently does nothing if notification permission is not granted.
 */
class DigestWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as CruxApplication).container
        val prefs = container.settingsRepository.notifications.first()
        val kind = inputData.getString(KEY_KIND) ?: return Result.success()

        val enabled = kind == NotificationScheduler.MORNING && prefs.morningEnabled ||
            kind == NotificationScheduler.WRAP && prefs.wrapEnabled
        // always reschedule the next firing so the daily cadence survives even a disabled day
        val minutes = if (kind == NotificationScheduler.MORNING) prefs.morningMinutes else prefs.wrapMinutes
        NotificationScheduler.scheduleNext(applicationContext, kind, minutes)
        if (!enabled) return Result.success()

        val zone = ZoneId.systemDefault()
        val now = Instant.now()
        val open = container.taskRepository.observeOpen().first()

        val (channel, title, text) = if (kind == NotificationScheduler.MORNING) {
            val overdue = open.count { isOverdue(it, now, zone) }
            val text = when {
                open.isEmpty() -> "clear trail. nothing due."
                overdue > 0 -> "${open.size} open · $overdue overdue"
                else -> "${open.size} open today"
            }
            Triple(CruxNotifications.CHANNEL_MORNING, "today", text)
        } else {
            val startOfToday = now.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
            val doneToday = container.database.completionLogDao().observeAll().first()
                .count { it.completedAt >= startOfToday }
            val text = when {
                open.isEmpty() -> "$doneToday done today. trail clear."
                else -> "$doneToday done today · ${open.size} still open"
            }
            Triple(CruxNotifications.CHANNEL_WRAP, "wrap", text)
        }

        CruxNotifications.post(applicationContext, channel, title, text, notificationId = kind.hashCode())
        return Result.success()
    }

    companion object {
        const val KEY_KIND = "kind"
    }
}

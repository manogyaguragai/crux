package com.crux.app.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.crux.app.CruxApplication
import com.crux.app.MainActivity
import com.crux.app.R
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

        post(channel, title, text, notificationId = kind.hashCode())
        return Result.success()
    }

    private fun post(channel: String, title: String, text: String, notificationId: Int) {
        // POST_NOTIFICATIONS is a runtime permission only on API 33+; below that, posting is free.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        val launch = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            applicationContext, 0, launch,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(applicationContext, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(applicationContext).notify(notificationId, notification)
    }

    companion object {
        const val KEY_KIND = "kind"
    }
}

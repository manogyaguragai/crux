package com.crux.app.notifications

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.crux.app.data.NotificationPrefs
import java.time.Duration
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * Schedules the daily morning and wrap digests with WorkManager. WorkManager persists its queue
 * across reboots, so no boot receiver is needed. Each firing reschedules itself for the next day;
 * flipping a toggle off cancels that digest. Per-task due-now exact alarms are a later slice.
 */
object NotificationScheduler {
    const val MORNING = "digest_morning"
    const val WRAP = "digest_wrap"

    fun reschedule(context: Context, prefs: NotificationPrefs) {
        scheduleOrCancel(context, MORNING, prefs.morningEnabled, prefs.morningMinutes)
        scheduleOrCancel(context, WRAP, prefs.wrapEnabled, prefs.wrapMinutes)
    }

    /** Enqueue the next firing of [kind] at the given time-of-day (minutes since midnight). */
    fun scheduleNext(context: Context, kind: String, minutes: Int) {
        val request = OneTimeWorkRequestBuilder<DigestWorker>()
            .setInitialDelay(delayMillisUntil(minutes), TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(DigestWorker.KEY_KIND to kind))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(kind, ExistingWorkPolicy.REPLACE, request)
    }

    private fun scheduleOrCancel(context: Context, kind: String, enabled: Boolean, minutes: Int) {
        if (enabled) scheduleNext(context, kind, minutes)
        else WorkManager.getInstance(context).cancelUniqueWork(kind)
    }

    private fun delayMillisUntil(minutes: Int): Long {
        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.now(zone)
        var next = now.toLocalDate().atTime(LocalTime.of(minutes / 60, minutes % 60)).atZone(zone)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return Duration.between(now, next).toMillis().coerceAtLeast(0)
    }
}

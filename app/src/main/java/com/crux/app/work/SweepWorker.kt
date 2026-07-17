package com.crux.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.crux.app.CruxApplication
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * The scheduled midnight sweep: clears yesterday's DONE rows even if the app is never opened. The
 * on-foreground sweep (MainActivity.onResume) still covers the case the user actually sees; this is
 * the background backstop. Self-reschedules for the next day; WorkManager persists it across reboot.
 */
class SweepWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as CruxApplication).container
        val zone = ZoneId.systemDefault()
        container.taskRepository.sweepDoneBeforeToday(zone, Instant.now())
        SweepScheduler.scheduleNext(applicationContext)
        return Result.success()
    }
}

object SweepScheduler {
    private const val NAME = "midnight_sweep"
    private const val SWEEP_MINUTE = 10 // 00:10, just after midnight

    fun scheduleNext(context: Context) {
        val request = OneTimeWorkRequestBuilder<SweepWorker>()
            .setInitialDelay(delayMillisUntilMidnight(), TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(NAME, ExistingWorkPolicy.REPLACE, request)
    }

    private fun delayMillisUntilMidnight(): Long {
        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.now(zone)
        var next = now.toLocalDate().plusDays(1).atTime(LocalTime.of(0, SWEEP_MINUTE)).atZone(zone)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return Duration.between(now, next).toMillis().coerceAtLeast(0)
    }
}

package com.crux.app

import android.app.Application
import com.crux.app.data.AppContainer
import com.crux.app.notifications.CruxNotifications
import com.crux.app.notifications.DueAlarms
import com.crux.app.notifications.NotificationScheduler
import com.crux.app.work.SweepScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Owns the manual dependency container for the whole process. */
class CruxApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        CruxNotifications.createChannels(this)
        val scope = CoroutineScope(Dispatchers.Default)
        // (re)schedule the daily digests + the midnight sweep to the current settings on every start.
        scope.launch {
            val prefs = container.settingsRepository.notifications.first()
            NotificationScheduler.reschedule(this@CruxApplication, prefs)
            SweepScheduler.scheduleNext(this@CruxApplication)
        }
        // keep per-task due alarms armed to the live open-task set + the due toggle, while alive.
        scope.launch {
            combine(
                container.taskRepository.observeOpen(),
                container.settingsRepository.notifications,
            ) { tasks, prefs -> tasks to prefs.dueEnabled }.collect { (tasks, dueEnabled) ->
                DueAlarms.rescheduleAll(this@CruxApplication, tasks, dueEnabled, System.currentTimeMillis())
            }
        }
    }
}

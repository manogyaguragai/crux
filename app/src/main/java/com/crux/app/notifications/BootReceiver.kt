package com.crux.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.crux.app.CruxApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Re-arms the per-task due alarms after a reboot (AlarmManager forgets them across boot; WorkManager
 * digests and the sweep re-arm themselves, so they need no handling here).
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        val container = (context.applicationContext as CruxApplication).container
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val tasks = container.taskRepository.observeOpen().first()
                val prefs = container.settingsRepository.notifications.first()
                DueAlarms.rescheduleAll(context, tasks, prefs.dueEnabled, System.currentTimeMillis())
            } finally {
                pending.finish()
            }
        }
    }
}

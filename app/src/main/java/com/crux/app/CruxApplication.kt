package com.crux.app

import android.app.Application
import com.crux.app.data.AppContainer
import com.crux.app.notifications.CruxNotifications
import com.crux.app.notifications.NotificationScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
        // (re)schedule the daily digests to the user's current settings on every start.
        CoroutineScope(Dispatchers.Default).launch {
            val prefs = container.settingsRepository.notifications.first()
            NotificationScheduler.reschedule(this@CruxApplication, prefs)
        }
    }
}

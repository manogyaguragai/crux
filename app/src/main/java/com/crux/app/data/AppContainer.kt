package com.crux.app.data

import android.content.Context
import com.crux.app.notifications.NotificationScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Manual dependency container (architecture.md: Hilt is overkill for one module).
 * Holds the database and, from phase 1, the repositories built on its DAOs.
 * Constructed once in CruxApplication; the database opens lazily on first use.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val database: CruxDatabase by lazy { CruxDatabase.build(appContext) }

    val taskRepository: TaskRepository by lazy {
        TaskRepository(database.taskDao(), database.completionLogDao(), database.projectDao())
    }

    val projectRepository: ProjectRepository by lazy {
        ProjectRepository(database.projectDao(), database.taskDao())
    }

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(appContext) }

    val backupRepository: BackupRepository by lazy { BackupRepository(database) }

    /** Re-apply the daily digest schedule after a notification setting changes. */
    fun rescheduleNotifications(prefs: NotificationPrefs) {
        NotificationScheduler.reschedule(appContext, prefs)
    }

    /**
     * The hard reset (owner request): wipe every table back to empty and drop all preferences, so
     * the app is brand new. Irreversible; the settings screen guards it behind a destructive confirm.
     */
    suspend fun hardReset() {
        withContext(Dispatchers.IO) { database.clearAllTables() }
        settingsRepository.clear()
    }
}

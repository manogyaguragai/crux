package com.crux.app.data

import android.content.Context
import com.crux.app.data.queue.CaptureProcessor
import com.crux.app.data.queue.CaptureQueue
import com.crux.app.intelligence.Intelligence
import com.crux.app.intelligence.LlmClient
import com.crux.app.notifications.NotificationScheduler
import com.crux.app.voice.VoiceController
import com.crux.app.voice.VoiceModel
import com.crux.app.voice.VoiceModelStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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

    /** Encrypted at-rest storage for the BYOK api key (phase 3). Opens its Keystore file lazily. */
    val secureKeyStore: SecureKeyStore by lazy { SecureKeyStore(appContext) }

    /**
     * The optional AI layer (phase 3): rules run first, then — only when AI is on and a key is set —
     * the chosen provider fills what the rules missed. Off by default; the app is fully useful without it.
     */
    val intelligence: Intelligence by lazy {
        Intelligence(settingsRepository, secureKeyStore, LlmClient())
    }

    /** The autonomous capture pipeline (rules + optional LLM), run by the queue worker off the UI. */
    val captureProcessor: CaptureProcessor by lazy {
        CaptureProcessor(taskRepository, projectRepository, intelligence)
    }

    /** The persisted capture queue: omnibar submissions land here and drain one by one in the background. */
    val captureQueue: CaptureQueue by lazy { CaptureQueue(appContext) }

    /**
     * On-device voice capture (phase 4). Off until the user downloads a Whisper model. App-scoped with
     * its own coroutine scope so a large model download survives navigation between home and settings;
     * both screens observe the one controller. Models live under filesDir/voice/.
     */
    val voiceModelStore: VoiceModelStore by lazy { VoiceModelStore(appContext.filesDir) }
    private val voiceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val voiceController: VoiceController by lazy { VoiceController(voiceModelStore, voiceScope) }

    /** Re-apply the daily digest schedule after a notification setting changes. */
    fun rescheduleNotifications(prefs: NotificationPrefs) {
        NotificationScheduler.reschedule(appContext, prefs)
    }

    /**
     * The hard reset (owner request): wipe every table back to empty and drop all preferences, so
     * the app is brand new. Irreversible; the settings screen guards it behind a destructive confirm.
     */
    suspend fun hardReset() {
        withContext(Dispatchers.IO) {
            database.clearAllTables()
            secureKeyStore.clear() // the stored api key is data too; the reset must wipe it
            captureQueue.clear()   // drop any pending/finished queued captures
            // downloaded voice models are on-device data too; "brand new" must reclaim their ~100 MB+.
            VoiceModel.entries.forEach { voiceController.remove(it) }
        }
        settingsRepository.clear()
    }
}

package com.crux.app.ui

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.crux.app.data.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.crux.app.data.NotificationPrefs
import com.crux.app.data.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * State for the settings screen: the appearance preferences (OLED deep mode, text-size scale) and
 * the hard reset. Preferences persist through the SettingsRepository (DataStore); the reset wipes
 * the database and the preferences via the container.
 */
class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    private val settings = container.settingsRepository

    val deepMode: StateFlow<Boolean> =
        settings.deepMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val fontScale: StateFlow<Float> =
        settings.fontScale.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 1f)

    val homeCount: StateFlow<Int> =
        settings.homeCount.stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsRepository.DEFAULT_HOME_COUNT,
        )

    val notifications: StateFlow<NotificationPrefs> =
        settings.notifications.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            NotificationPrefs(true, SettingsRepository.DEFAULT_MORNING_MIN, true, true, SettingsRepository.DEFAULT_WRAP_MIN),
        )

    fun setDeepMode(on: Boolean) = launch { settings.setDeepMode(on) }

    fun setFontScale(scale: Float) = launch { settings.setFontScale(scale) }

    fun setHomeCount(n: Int) = launch { settings.setHomeCount(n) }

    fun setMorning(on: Boolean, minutes: Int) = launch {
        settings.setMorning(on, minutes)
        container.rescheduleNotifications(settings.notifications.first())
    }

    fun setDue(on: Boolean) = launch { settings.setDue(on) }

    fun setWrap(on: Boolean, minutes: Int) = launch {
        settings.setWrap(on, minutes)
        container.rescheduleNotifications(settings.notifications.first())
    }

    val archivedCount: StateFlow<Int> =
        container.projectRepository.observeArchivedCount()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun clearArchived() = launch { container.projectRepository.clearArchived() }

    fun hardReset() = launch { container.hardReset() }

    /** Write the full backup JSON to the SAF-picked file. */
    fun export(resolver: ContentResolver, uri: Uri) = launch {
        val json = container.backupRepository.exportJson(System.currentTimeMillis())
        withContext(Dispatchers.IO) {
            resolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
        }
    }

    /** Replace all data from the SAF-picked backup file. */
    fun import(resolver: ContentResolver, uri: Uri) = launch {
        val json = withContext(Dispatchers.IO) {
            resolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }
        if (!json.isNullOrBlank()) container.backupRepository.importJson(json)
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    companion object {
        /** The four text-size steps offered, as (label, scale). 1.0 is the shipped size. */
        val FONT_STEPS = listOf(
            "small" to 0.9f,
            "default" to 1.0f,
            "large" to 1.15f,
            "larger" to 1.3f,
        )

        fun factory(container: AppContainer) = viewModelFactory {
            initializer { SettingsViewModel(container) }
        }
    }
}

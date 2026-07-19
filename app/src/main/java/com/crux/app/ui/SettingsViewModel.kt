package com.crux.app.ui

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.crux.app.data.AppContainer
import com.crux.app.intelligence.LlmPrompt
import com.crux.app.intelligence.LlmProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.crux.app.data.NotificationPrefs
import com.crux.app.data.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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

    // --- AI assist (phase 3, BYOK) ---
    private val keyStore = container.secureKeyStore
    // Bumped after any key write so [hasKey] re-reads the (non-observable) encrypted store.
    private val keyRefresh = MutableStateFlow(0)

    val aiEnabled: StateFlow<Boolean> =
        settings.aiEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val aiProvider: StateFlow<LlmProvider?> =
        settings.aiProvider.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Whether the chosen provider has a key stored. Re-checked when the provider or a key changes. */
    val hasKey: StateFlow<Boolean> =
        combine(settings.aiProvider, keyRefresh) { provider, _ ->
            provider != null && withContext(Dispatchers.IO) { keyStore.hasKey(provider.id) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Masked previews of every stored key, keyed by provider id — the BYOK sheet shows these so the
     *  owner can see which providers are keyed (multiple at once) without the raw secret leaking. */
    val savedKeys: StateFlow<Map<String, String>> =
        keyRefresh.map { readMaskedKeys() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private suspend fun readMaskedKeys(): Map<String, String> = withContext(Dispatchers.IO) {
        LlmProvider.entries.mapNotNull { p -> keyStore.keyFor(p.id)?.let { p.id to maskKey(it) } }.toMap()
    }

    // --- the editable capture prompt + command trigger words (phase 4) ---

    val aiSystemPrompt: StateFlow<String> =
        settings.aiSystemPrompt.stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5_000), LlmPrompt.DEFAULT_SYSTEM_PROMPT,
        )

    val aiCommandWords: StateFlow<String> =
        settings.aiCommandWords.stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5_000), LlmPrompt.DEFAULT_COMMAND_WORDS,
        )

    fun setAiSystemPrompt(prompt: String) = launch { settings.setAiSystemPrompt(prompt) }
    fun resetAiSystemPrompt() = launch { settings.resetAiSystemPrompt() }
    fun setAiCommandWords(words: String) = launch { settings.setAiCommandWords(words) }
    fun resetAiCommandWords() = launch { settings.resetAiCommandWords() }

    /** Flip the master switch. Enabling with no key is harmless — the chain stays gated on the key too. */
    fun setAiEnabled(on: Boolean) = launch { settings.setAiEnabled(on) }

    /** Save a key for [provider], make it the active provider, and turn AI on in one step. */
    fun saveKey(provider: LlmProvider, key: String) = launch {
        if (key.isBlank()) return@launch
        withContext(Dispatchers.IO) { keyStore.setKey(provider.id, key) }
        settings.setAiProvider(provider)
        settings.setAiEnabled(true)
        keyRefresh.value += 1
    }

    /** Make [provider] the active one (it must already be keyed) and turn AI on. Lets the owner keep
     *  keys for several providers and switch which one parses. */
    fun useProvider(provider: LlmProvider) = launch {
        settings.setAiProvider(provider)
        settings.setAiEnabled(true)
    }

    /** Forget just [provider]'s key. If it was the active one, switch AI off (nothing left to call). */
    fun removeKey(provider: LlmProvider) = launch {
        withContext(Dispatchers.IO) { keyStore.removeKey(provider.id) }
        if (settings.aiProvider.first() == provider) settings.setAiEnabled(false)
        keyRefresh.value += 1
    }

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

    /** A recognisable-but-not-leaked preview: keep the head (identifies the provider prefix) and last 4. */
    private fun maskKey(key: String): String =
        if (key.length <= 10) "•".repeat(key.length) else "${key.take(6)}…${key.takeLast(4)}"

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

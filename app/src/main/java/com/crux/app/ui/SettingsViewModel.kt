package com.crux.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.crux.app.data.AppContainer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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

    fun setDeepMode(on: Boolean) = launch { settings.setDeepMode(on) }

    fun setFontScale(scale: Float) = launch { settings.setFontScale(scale) }

    fun hardReset() = launch { container.hardReset() }

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

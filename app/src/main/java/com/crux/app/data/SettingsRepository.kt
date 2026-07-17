package com.crux.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "crux_settings")

/**
 * App preferences (DataStore), per architecture.md. Phase 1 holds the two appearance settings the
 * owner asked for: OLED "deep" mode and a text-size scale. Notification toggles and BYOK keys land
 * with their own slices. Defaults keep the shipped look (Void background, 1.0 text scale).
 */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val DEEP = booleanPreferencesKey("deep_mode")
        val FONT_SCALE = floatPreferencesKey("font_scale")
    }

    val deepMode: Flow<Boolean> = context.settingsDataStore.data.map { it[Keys.DEEP] ?: false }

    /** Text-size multiplier applied on top of the system font scale. 1.0 = the shipped size. */
    val fontScale: Flow<Float> = context.settingsDataStore.data.map { it[Keys.FONT_SCALE] ?: 1f }

    suspend fun setDeepMode(on: Boolean) {
        context.settingsDataStore.edit { it[Keys.DEEP] = on }
    }

    suspend fun setFontScale(scale: Float) {
        context.settingsDataStore.edit { it[Keys.FONT_SCALE] = scale }
    }

    /** Reset every preference to its default (part of the hard reset). */
    suspend fun clear() {
        context.settingsDataStore.edit { it.clear() }
    }
}

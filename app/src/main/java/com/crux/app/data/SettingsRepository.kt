package com.crux.app.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
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
        val MORNING_ON = booleanPreferencesKey("notif_morning_on")
        val MORNING_MIN = intPreferencesKey("notif_morning_minutes")
        val DUE_ON = booleanPreferencesKey("notif_due_on")
        val WRAP_ON = booleanPreferencesKey("notif_wrap_on")
        val WRAP_MIN = intPreferencesKey("notif_wrap_minutes")
        val HOME_COUNT = intPreferencesKey("home_count")
    }

    val deepMode: Flow<Boolean> = context.settingsDataStore.data.map { it[Keys.DEEP] ?: false }

    /** How many open tasks home shows (owner-configurable, 1..10; default 3). Clamped on read + write. */
    val homeCount: Flow<Int> = context.settingsDataStore.data.map {
        (it[Keys.HOME_COUNT] ?: DEFAULT_HOME_COUNT).coerceIn(HOME_COUNT_MIN, HOME_COUNT_MAX)
    }

    /** Text-size multiplier applied on top of the system font scale. 1.0 = the shipped size. */
    val fontScale: Flow<Float> = context.settingsDataStore.data.map { it[Keys.FONT_SCALE] ?: 1f }

    /** The three notification settings, with their two configurable times (minutes since midnight). */
    val notifications: Flow<NotificationPrefs> =
        context.settingsDataStore.data.map { it.toNotificationPrefs() }

    suspend fun setDeepMode(on: Boolean) {
        context.settingsDataStore.edit { it[Keys.DEEP] = on }
    }

    suspend fun setHomeCount(n: Int) {
        context.settingsDataStore.edit { it[Keys.HOME_COUNT] = n.coerceIn(HOME_COUNT_MIN, HOME_COUNT_MAX) }
    }

    suspend fun setFontScale(scale: Float) {
        context.settingsDataStore.edit { it[Keys.FONT_SCALE] = scale }
    }

    suspend fun setMorning(on: Boolean, minutes: Int) {
        context.settingsDataStore.edit { it[Keys.MORNING_ON] = on; it[Keys.MORNING_MIN] = minutes }
    }

    suspend fun setDue(on: Boolean) {
        context.settingsDataStore.edit { it[Keys.DUE_ON] = on }
    }

    suspend fun setWrap(on: Boolean, minutes: Int) {
        context.settingsDataStore.edit { it[Keys.WRAP_ON] = on; it[Keys.WRAP_MIN] = minutes }
    }

    private fun Preferences.toNotificationPrefs() = NotificationPrefs(
        morningEnabled = this[Keys.MORNING_ON] ?: true,
        morningMinutes = this[Keys.MORNING_MIN] ?: DEFAULT_MORNING_MIN,
        dueEnabled = this[Keys.DUE_ON] ?: true,
        wrapEnabled = this[Keys.WRAP_ON] ?: true,
        wrapMinutes = this[Keys.WRAP_MIN] ?: DEFAULT_WRAP_MIN,
    )

    /** Reset every preference to its default (part of the hard reset). */
    suspend fun clear() {
        context.settingsDataStore.edit { it.clear() }
    }

    companion object {
        const val DEFAULT_MORNING_MIN = 8 * 60   // 08:00
        const val DEFAULT_WRAP_MIN = 21 * 60      // 21:00
        const val DEFAULT_HOME_COUNT = 3          // the original hardcoded top-3
        const val HOME_COUNT_MIN = 1
        const val HOME_COUNT_MAX = 10
    }
}

/** The notification preferences as one value. Times are minutes since local midnight. */
data class NotificationPrefs(
    val morningEnabled: Boolean,
    val morningMinutes: Int,
    val dueEnabled: Boolean,
    val wrapEnabled: Boolean,
    val wrapMinutes: Int,
)

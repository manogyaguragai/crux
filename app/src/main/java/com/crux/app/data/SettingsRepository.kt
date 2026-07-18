package com.crux.app.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.crux.app.intelligence.LlmProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate

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
        // phase 3 (the AI layer). The api key itself lives in SecureKeyStore, never here; DataStore
        // only holds the non-secret switches and a per-day call counter for the budget guard.
        val AI_ENABLED = booleanPreferencesKey("ai_enabled")
        val AI_PROVIDER = stringPreferencesKey("ai_provider")
        val AI_CALLS_DATE = stringPreferencesKey("ai_calls_date")   // ISO local date the counter is for
        val AI_CALLS_COUNT = intPreferencesKey("ai_calls_count")
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

    /** Master switch for the optional AI layer. Off (the default) means the app is 100% deterministic. */
    val aiEnabled: Flow<Boolean> = context.settingsDataStore.data.map { it[Keys.AI_ENABLED] ?: false }

    /** The provider the owner picked, or null if none chosen yet. The key lives in SecureKeyStore. */
    val aiProvider: Flow<LlmProvider?> =
        context.settingsDataStore.data.map { LlmProvider.fromId(it[Keys.AI_PROVIDER]) }

    suspend fun setAiEnabled(on: Boolean) {
        context.settingsDataStore.edit { it[Keys.AI_ENABLED] = on }
    }

    suspend fun setAiProvider(provider: LlmProvider) {
        context.settingsDataStore.edit { it[Keys.AI_PROVIDER] = provider.id }
    }

    /** How many LLM calls have gone out [today] (0 after a date rollover). The budget guard reads this. */
    suspend fun aiCallsToday(today: LocalDate): Int {
        val prefs = context.settingsDataStore.data.first()
        return if (prefs[Keys.AI_CALLS_DATE] == today.toString()) prefs[Keys.AI_CALLS_COUNT] ?: 0 else 0
    }

    /** Count one LLM call against [today]'s budget, rolling the counter over at a new date. */
    suspend fun recordAiCall(today: LocalDate) {
        context.settingsDataStore.edit { prefs ->
            val sameDay = prefs[Keys.AI_CALLS_DATE] == today.toString()
            prefs[Keys.AI_CALLS_DATE] = today.toString()
            prefs[Keys.AI_CALLS_COUNT] = (if (sameDay) prefs[Keys.AI_CALLS_COUNT] ?: 0 else 0) + 1
        }
    }

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
        // Budget guard (intelligence.md): expected volume is 10-30 calls/day, far under any free tier.
        // The cap is a runaway backstop, not a quota — past it the chain quietly reverts to rules-only.
        const val AI_DAILY_CAP = 100
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

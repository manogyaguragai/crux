package com.crux.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * At-rest storage for the BYOK api key (owner decision 2026-07-18: store it encrypted, not in plain
 * DataStore). Jetpack Security's [EncryptedSharedPreferences] wraps the value with an AES key held in
 * the Android Keystore — hardware-backed where the device supports it, so the key is not readable
 * from a raw filesystem dump the way a DataStore value would be. Everything else (the AI on/off flag,
 * the chosen provider) stays in [SettingsRepository]; only the secret lives here.
 *
 * Keys are stored per provider id, so switching provider and back does not force a re-paste. All
 * access touches disk + the Keystore, so callers use it off the main thread (Dispatchers.IO).
 */
class SecureKeyStore(context: Context) {

    private val appContext = context.applicationContext

    // Built lazily: opening the Keystore-backed file is the expensive part, and a phone with AI off
    // should never pay it. `by lazy` also means the first opener wins if two threads race.
    private val prefs: SharedPreferences by lazy {
        val master = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            appContext,
            FILE,
            master,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    /** The stored key for [providerId], or null when none is set (blank counts as none). */
    fun keyFor(providerId: String): String? =
        prefs.getString(providerId, null)?.takeIf { it.isNotBlank() }

    /** True when a non-blank key exists for [providerId] (the UI shows a masked preview, never the key). */
    fun hasKey(providerId: String): Boolean = keyFor(providerId) != null

    fun setKey(providerId: String, key: String) {
        prefs.edit().putString(providerId, key.trim()).apply()
    }

    fun removeKey(providerId: String) {
        prefs.edit().remove(providerId).apply()
    }

    /** Wipe every stored key (part of the hard reset). */
    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val FILE = "crux_secure_keys"
    }
}

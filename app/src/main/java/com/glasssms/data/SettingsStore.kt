package com.glasssms.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class Settings(
    val detectCodes: Boolean = true,
    val codeNotifications: Boolean = true,
    val autoCopy: Boolean = false,
    val deleteAfterCopy: Boolean = false,
    val deleteDelayMinutes: Int = 5,
)

private val Context.dataStore by preferencesDataStore("settings")

class SettingsStore(private val context: Context) {
    private object Keys {
        val detect = booleanPreferencesKey("detect_codes")
        val notify = booleanPreferencesKey("code_notifications")
        val autoCopy = booleanPreferencesKey("auto_copy")
        val deleteAfterCopy = booleanPreferencesKey("delete_after_copy")
        val deleteDelay = intPreferencesKey("delete_delay_minutes")
    }

    val flow: Flow<Settings> = context.dataStore.data.map(::fromPrefs)

    private fun fromPrefs(p: Preferences): Settings {
        val d = Settings()
        return Settings(
            detectCodes = p[Keys.detect] ?: d.detectCodes,
            codeNotifications = p[Keys.notify] ?: d.codeNotifications,
            autoCopy = p[Keys.autoCopy] ?: d.autoCopy,
            deleteAfterCopy = p[Keys.deleteAfterCopy] ?: d.deleteAfterCopy,
            deleteDelayMinutes = p[Keys.deleteDelay] ?: d.deleteDelayMinutes,
        )
    }

    suspend fun current(): Settings = flow.first()

    suspend fun update(transform: (Settings) -> Settings) {
        context.dataStore.edit { p ->
            val s = transform(fromPrefs(p))
            p[Keys.detect] = s.detectCodes
            p[Keys.notify] = s.codeNotifications
            p[Keys.autoCopy] = s.autoCopy
            p[Keys.deleteAfterCopy] = s.deleteAfterCopy
            p[Keys.deleteDelay] = s.deleteDelayMinutes
        }
    }
}

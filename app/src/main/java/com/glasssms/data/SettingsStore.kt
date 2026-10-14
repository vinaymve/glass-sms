package com.glasssms.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class SwipeAction { None, ToggleRead, Delete }

data class Settings(
    val detectCodes: Boolean = true,
    val codeNotifications: Boolean = true,
    val autoCopy: Boolean = false,
    val deleteAfterCopy: Boolean = false,
    val deleteDelayMinutes: Int = 5,
    /** Delete every code message this long after it arrives. 0 = off, [AUTO_DELETE_AT_EXPIRY] = when the code expires. */
    val autoDeleteCodesMinutes: Int = 0,
    val confirmDelete: Boolean = true,
    val swipeRight: SwipeAction = SwipeAction.ToggleRead,
    val swipeLeft: SwipeAction = SwipeAction.Delete,
    val deleteExplosion: Boolean = true,
) {
    companion object {
        const val AUTO_DELETE_AT_EXPIRY = -1
    }
}

private val Context.dataStore by preferencesDataStore("settings")

class SettingsStore(private val context: Context) {
    private object Keys {
        val detect = booleanPreferencesKey("detect_codes")
        val notify = booleanPreferencesKey("code_notifications")
        val autoCopy = booleanPreferencesKey("auto_copy")
        val deleteAfterCopy = booleanPreferencesKey("delete_after_copy")
        val deleteDelay = intPreferencesKey("delete_delay_minutes")
        val autoDelete = intPreferencesKey("auto_delete_codes_minutes")
        val confirmDelete = booleanPreferencesKey("confirm_delete")
        val swipeRight = stringPreferencesKey("swipe_right")
        val swipeLeft = stringPreferencesKey("swipe_left")
        val deleteExplosion = booleanPreferencesKey("delete_explosion")
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
            autoDeleteCodesMinutes = p[Keys.autoDelete] ?: d.autoDeleteCodesMinutes,
            confirmDelete = p[Keys.confirmDelete] ?: d.confirmDelete,
            swipeRight = p[Keys.swipeRight].toSwipeAction() ?: d.swipeRight,
            swipeLeft = p[Keys.swipeLeft].toSwipeAction() ?: d.swipeLeft,
            deleteExplosion = p[Keys.deleteExplosion] ?: d.deleteExplosion,
        )
    }

    private fun String?.toSwipeAction() = SwipeAction.entries.firstOrNull { it.name == this }

    suspend fun current(): Settings = flow.first()

    suspend fun update(transform: (Settings) -> Settings) {
        context.dataStore.edit { p ->
            val s = transform(fromPrefs(p))
            p[Keys.detect] = s.detectCodes
            p[Keys.notify] = s.codeNotifications
            p[Keys.autoCopy] = s.autoCopy
            p[Keys.deleteAfterCopy] = s.deleteAfterCopy
            p[Keys.deleteDelay] = s.deleteDelayMinutes
            p[Keys.autoDelete] = s.autoDeleteCodesMinutes
            p[Keys.confirmDelete] = s.confirmDelete
            p[Keys.swipeRight] = s.swipeRight.name
            p[Keys.swipeLeft] = s.swipeLeft.name
            p[Keys.deleteExplosion] = s.deleteExplosion
        }
    }
}

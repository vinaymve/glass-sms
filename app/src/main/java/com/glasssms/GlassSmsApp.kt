package com.glasssms

import android.app.Application
import android.content.Context
import com.glasssms.data.SettingsStore
import com.glasssms.data.SmsRepository
import com.glasssms.otp.Notifications

class GlassSmsApp : Application() {
    val repo by lazy { SmsRepository(this) }
    val settings by lazy { SettingsStore(this) }

    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannels(this)
    }
}

val Context.app: GlassSmsApp get() = applicationContext as GlassSmsApp

/** Process-wide UI state that background receivers need to see. */
object AppState {
    /** Thread currently open and resumed on screen; incoming messages for it skip the notification. */
    @Volatile var visibleThreadId: Long? = null
}

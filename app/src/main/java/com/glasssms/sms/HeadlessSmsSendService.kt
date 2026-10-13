package com.glasssms.sms

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.telephony.TelephonyManager
import com.glasssms.app

/** "Respond via message" from the incoming-call screen. */
class HeadlessSmsSendService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == TelephonyManager.ACTION_RESPOND_VIA_MESSAGE) {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)
            val recipients = intent.data?.schemeSpecificPart?.substringBefore('?')
                ?.split(',', ';')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
            if (!text.isNullOrBlank()) {
                Thread { recipients.forEach { app.repo.send(it, text) } }.start()
            }
        }
        stopSelf(startId)
        return START_NOT_STICKY
    }
}

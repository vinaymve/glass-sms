package com.glasssms.sms

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony.Sms
import com.glasssms.app

/** Moves an outgoing message from Outbox to Sent or Failed once the radio reports back. */
class SmsSentReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val uri = intent.data ?: return
        val type = if (resultCode == Activity.RESULT_OK) Sms.MESSAGE_TYPE_SENT else Sms.MESSAGE_TYPE_FAILED
        runCatching { context.app.repo.setType(uri, type) }
    }
}

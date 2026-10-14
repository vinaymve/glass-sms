package com.glasssms.sms

import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.glasssms.AppState
import com.glasssms.app
import com.glasssms.otp.CodeActions
import com.glasssms.otp.Notifications
import com.glasssms.otp.OtpParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * As the default SMS app we receive SMS_DELIVER and are responsible for storing the message
 * in the provider ourselves.
 */
class SmsDeliverReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (parts.isNullOrEmpty()) return

        val first = parts[0]
        val address = first.displayOriginatingAddress ?: first.originatingAddress.orEmpty()
        val body = parts.joinToString("") { it.displayMessageBody.orEmpty() }
        val sentAt = first.timestampMillis
        val subId = intent.getIntExtra("subscription", -1)

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                handle(context.applicationContext, address, body, sentAt, subId)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun handle(context: Context, address: String, body: String, sentAt: Long, subId: Int) {
        val repo = context.app.repo
        val uri = repo.insertIncoming(address, body, sentAt, subId) ?: return
        val messageId = ContentUris.parseId(uri)
        val threadId = repo.threadIdOf(uri) ?: return
        val settings = context.app.settings.current()
        val otp = if (settings.detectCodes) OtpParser.parse(body, address) else null

        if (otp != null) CodeActions.scheduleAutoDelete(context, messageId, otp, settings)
        if (otp != null && settings.autoCopy) {
            CodeActions.copyAndMaybeCleanUp(context, otp.code, messageId)
        }
        if (AppState.visibleThreadId == threadId) return

        val title = repo.contacts.displayName(address)
        if (otp != null && settings.codeNotifications) {
            Notifications.showCode(context, threadId, messageId, address, title, otp, body)
        } else {
            Notifications.showMessage(context, threadId, address, title, body)
        }
    }
}

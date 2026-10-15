package com.glasssms.sms

import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.glasssms.AppState
import com.glasssms.app
import com.glasssms.data.Categorizer
import com.glasssms.data.Category
import com.glasssms.data.Settings
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
        val store = context.app.settings
        val settings = store.current()

        blockReason(address, body, settings)?.let { reason ->
            repo.storeSpam(address, body, System.currentTimeMillis(), reason)
            return
        }

        val uri = repo.insertIncoming(address, body, sentAt, subId) ?: return
        val messageId = ContentUris.parseId(uri)
        val threadId = repo.threadIdOf(uri) ?: return
        val otp = if (settings.detectCodes) OtpParser.parse(body, address) else null

        // A new message brings an archived conversation back, like Google Messages and Gmail.
        if (threadId in settings.archivedThreads) store.update { it.copy(archivedThreads = it.archivedThreads - threadId) }

        if (otp != null) CodeActions.scheduleAutoDelete(context, messageId, otp, settings)
        if (otp != null && settings.autoCopy) {
            CodeActions.copyAndMaybeCleanUp(context, otp.code, messageId)
        }
        if (AppState.visibleThreadId == threadId) return
        if (threadId in settings.mutedThreads) return
        if (settings.silenceOffers && Categorizer.categorize(address, body, otp) == Category.Offers) return

        val title = repo.contacts.displayName(address)
        if (otp != null && settings.codeNotifications) {
            Notifications.showCode(context, threadId, messageId, address, title, otp, body)
        } else {
            Notifications.showMessage(context, threadId, address, title, body)
        }
    }

    private fun blockReason(address: String, body: String, settings: Settings): String? {
        if (Categorizer.senderKey(address) in settings.blockedSenders) return "Blocked sender"
        if (settings.blockPromotional && Categorizer.isPromotionalHeader(address)) return "Promotional"
        val word = settings.blockedKeywords.firstOrNull { it.isNotBlank() && body.contains(it, ignoreCase = true) }
        return word?.let { "Contains \u201c$it\u201d" }
    }
}

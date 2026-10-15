package com.glasssms.sms

import android.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.glasssms.app
import com.glasssms.otp.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Reply and Mark as Read buttons on message notifications. */
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val threadId = intent.getLongExtra(EXTRA_THREAD_ID, -1)
        if (threadId < 0) return
        val reply = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(KEY_REPLY)?.toString()
        val address = intent.getStringExtra(EXTRA_ADDRESS)

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = context.app.repo
                when (intent.action) {
                    ACTION_REPLY -> if (!reply.isNullOrBlank() && address != null) repo.send(address, reply.trim())
                }
                // Replying counts as reading, as in every other messaging app.
                repo.markRead(threadId)
                Notifications.cancel(context, threadId)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_REPLY = "com.glasssms.REPLY"
        const val ACTION_MARK_READ = "com.glasssms.MARK_READ"
        const val KEY_REPLY = "reply_text"
        const val EXTRA_THREAD_ID = "thread_id"
        const val EXTRA_ADDRESS = "address"
    }
}

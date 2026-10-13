package com.glasssms.data

import android.app.PendingIntent
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.provider.Telephony.Sms
import android.telephony.SmsManager
import com.glasssms.otp.OtpParser
import com.glasssms.sms.SmsSentReceiver
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

/**
 * Reads and writes the system SMS provider. Writes only take effect while this app is the
 * default SMS app; that is an Android rule, not something we can work around.
 */
class SmsRepository(private val context: Context) {
    private val resolver = context.contentResolver
    val contacts = ContactResolver(context)

    /** Emits once immediately, then whenever the SMS provider changes. */
    fun changes(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        resolver.registerContentObserver(Sms.CONTENT_URI, true, observer)
        resolver.registerContentObserver(MMS_SMS_URI, true, observer)
        trySend(Unit)
        awaitClose { resolver.unregisterContentObserver(observer) }
    }.conflate()

    fun loadConversations(): List<Conversation> {
        class Acc(val latest: Message, var unread: Int)
        val byThread = LinkedHashMap<Long, Acc>()
        query(Sms.CONTENT_URI, null, null, "${Sms.DATE} DESC") { c ->
            val m = c.toMessage()
            if (m.type == Sms.MESSAGE_TYPE_DRAFT) return@query
            val acc = byThread.getOrPut(m.threadId) { Acc(m, 0) }
            if (!m.read && !m.isOutgoing) acc.unread++
        }
        return byThread.values.map { acc ->
            val m = acc.latest
            val name = contacts.name(m.address)
            Conversation(
                threadId = m.threadId,
                address = m.address,
                displayName = name ?: ContactResolver.formatAddress(m.address),
                isKnown = name != null,
                latest = m,
                unreadCount = acc.unread,
                otp = if (m.isOutgoing) null else OtpParser.parse(m.body, m.address),
            )
        }
    }

    fun loadMessages(threadId: Long): List<Message> {
        val out = ArrayList<Message>()
        query(Sms.CONTENT_URI, "${Sms.THREAD_ID} = ?", arrayOf(threadId.toString()), "${Sms.DATE} ASC") { c ->
            val m = c.toMessage()
            if (m.type != Sms.MESSAGE_TYPE_DRAFT) out += m
        }
        return out
    }

    /** Incoming messages since [since] that contain a one-time code, newest first. */
    fun recentCodes(since: Long): List<OtpEntry> {
        val out = ArrayList<OtpEntry>()
        query(
            Sms.CONTENT_URI,
            "${Sms.DATE} > ? AND ${Sms.TYPE} = ?",
            arrayOf(since.toString(), Sms.MESSAGE_TYPE_INBOX.toString()),
            "${Sms.DATE} DESC",
        ) { c ->
            val m = c.toMessage()
            val otp = OtpParser.parse(m.body, m.address) ?: return@query
            out += OtpEntry(m.id, m.threadId, m.address, contacts.displayName(m.address), m.date, otp)
        }
        return out
    }

    fun threadIdFor(address: String): Long = Telephony.Threads.getOrCreateThreadId(context, address)

    fun threadIdOf(messageUri: Uri): Long? = try {
        resolver.query(messageUri, arrayOf(Sms.THREAD_ID), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getLong(0) else null
        }
    } catch (_: Exception) {
        null
    }

    fun insertIncoming(address: String, body: String, sentAt: Long, subscriptionId: Int): Uri? {
        val values = ContentValues().apply {
            put(Sms.ADDRESS, address)
            put(Sms.BODY, body)
            put(Sms.DATE, System.currentTimeMillis())
            put(Sms.DATE_SENT, sentAt)
            put(Sms.READ, 0)
            put(Sms.SEEN, 0)
            put(Sms.TYPE, Sms.MESSAGE_TYPE_INBOX)
            if (subscriptionId >= 0) put(Sms.SUBSCRIPTION_ID, subscriptionId)
        }
        return resolver.insert(Sms.Inbox.CONTENT_URI, values)
    }

    fun send(address: String, body: String) {
        val values = ContentValues().apply {
            put(Sms.ADDRESS, address)
            put(Sms.BODY, body)
            put(Sms.DATE, System.currentTimeMillis())
            put(Sms.READ, 1)
            put(Sms.SEEN, 1)
            put(Sms.TYPE, Sms.MESSAGE_TYPE_OUTBOX)
        }
        val uri = resolver.insert(Sms.Outbox.CONTENT_URI, values)

        val sms = smsManager()
        val parts = sms.divideMessage(body)
        val sentIntent = PendingIntent.getBroadcast(
            context,
            uri?.hashCode() ?: body.hashCode(),
            Intent(context, SmsSentReceiver::class.java).setData(uri),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val sentIntents = ArrayList<PendingIntent>(parts.size).apply { repeat(parts.size) { add(sentIntent) } }
        sms.sendMultipartTextMessage(address, null, parts, sentIntents, null)
    }

    fun setType(messageUri: Uri, type: Int) {
        resolver.update(messageUri, ContentValues().apply { put(Sms.TYPE, type) }, null, null)
    }

    fun markRead(threadId: Long) {
        val values = ContentValues().apply {
            put(Sms.READ, 1)
            put(Sms.SEEN, 1)
        }
        runCatching {
            resolver.update(Sms.CONTENT_URI, values, "${Sms.THREAD_ID} = ? AND ${Sms.READ} = 0", arrayOf(threadId.toString()))
        }
    }

    fun deleteThread(threadId: Long) {
        runCatching { resolver.delete(Sms.CONTENT_URI, "${Sms.THREAD_ID} = ?", arrayOf(threadId.toString())) }
    }

    fun deleteMessages(ids: Collection<Long>) {
        ids.forEach { id -> runCatching { resolver.delete(ContentUris.withAppendedId(Sms.CONTENT_URI, id), null, null) } }
    }

    @Suppress("DEPRECATION")
    private fun smsManager(): SmsManager =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) context.getSystemService(SmsManager::class.java)
        else SmsManager.getDefault()

    private inline fun query(uri: Uri, selection: String?, args: Array<String>?, sort: String, row: (Cursor) -> Unit) {
        try {
            resolver.query(uri, PROJECTION, selection, args, sort)?.use { c -> while (c.moveToNext()) row(c) }
        } catch (_: SecurityException) {
            // No READ_SMS yet; callers see an empty list until the user grants it.
        }
    }

    private fun Cursor.toMessage() = Message(
        id = getLong(0),
        threadId = getLong(1),
        address = getString(2).orEmpty(),
        body = getString(3).orEmpty(),
        date = getLong(4),
        type = getInt(5),
        read = getInt(6) == 1,
    )

    companion object {
        private val MMS_SMS_URI: Uri = Uri.parse("content://mms-sms/")
        private val PROJECTION = arrayOf(Sms._ID, Sms.THREAD_ID, Sms.ADDRESS, Sms.BODY, Sms.DATE, Sms.TYPE, Sms.READ)

        fun isDefaultSmsApp(context: Context) = Telephony.Sms.getDefaultSmsPackage(context) == context.packageName
    }
}

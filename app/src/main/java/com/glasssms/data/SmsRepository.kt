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
    val local = LocalStore(context)

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
                category = Category.Personal,
                unreadCount = acc.unread,
                otp = if (m.isOutgoing) null else OtpParser.parse(m.body, m.address),
            ).let { c -> c.copy(category = Categorizer.categorize(m.address, m.body, c.otp)) }
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

    fun markAllRead() {
        val values = ContentValues().apply {
            put(Sms.READ, 1)
            put(Sms.SEEN, 1)
        }
        runCatching { resolver.update(Sms.CONTENT_URI, values, "${Sms.READ} = 0", null) }
    }

    /** Marks the newest incoming message unread, which is how the thread shows as unread. */
    fun markUnread(threadId: Long) {
        var latestId: Long? = null
        query(
            Sms.CONTENT_URI,
            "${Sms.THREAD_ID} = ? AND ${Sms.TYPE} = ?",
            arrayOf(threadId.toString(), Sms.MESSAGE_TYPE_INBOX.toString()),
            "${Sms.DATE} DESC",
        ) { c -> if (latestId == null) latestId = c.getLong(0) }
        val id = latestId ?: return
        runCatching {
            resolver.update(ContentUris.withAppendedId(Sms.CONTENT_URI, id), ContentValues().apply { put(Sms.READ, 0) }, null, null)
        }
    }

    // --- Recently Deleted -----------------------------------------------------------------

    /** Moves a whole conversation to the bin. Returns the batch id for Undo, or null if nothing moved. */
    fun trashThread(threadId: Long): Long? = trash(loadMessages(threadId))

    /** Moves single messages to the bin. */
    fun trashMessages(ids: Collection<Long>): Long? = trash(loadByIds(ids))

    private fun trash(messages: List<Message>): Long? {
        if (messages.isEmpty()) return null
        val batch = System.nanoTime()
        local.insert(LocalStore.TRASH, messages, batch)
        deleteMessages(messages.map { it.id })
        return batch
    }

    fun restoreBatch(batch: Long) = restore(LocalStore.TRASH, local.byBatch(LocalStore.TRASH, batch))

    fun restoreTrash(items: List<StoredMessage>) = restore(LocalStore.TRASH, items)

    fun deleteFromTrash(ids: Collection<Long>) = local.delete(LocalStore.TRASH, ids)

    fun emptyTrash() = local.clear(LocalStore.TRASH)

    fun trashed(): List<StoredMessage> = local.all(LocalStore.TRASH)

    fun purgeTrash(maxAgeMillis: Long) = local.deleteOlderThan(LocalStore.TRASH, System.currentTimeMillis() - maxAgeMillis)

    // --- Spam --------------------------------------------------------------------------------

    /** A blocked incoming SMS never reaches the system inbox; it waits here instead. */
    fun storeSpam(address: String, body: String, date: Long, reason: String) {
        local.insert(
            LocalStore.SPAM,
            listOf(Message(0, 0, address, body, date, Sms.MESSAGE_TYPE_INBOX, read = false)),
            batch = date,
            reason = reason,
        )
    }

    fun moveThreadToSpam(threadId: Long, reason: String) {
        val messages = loadMessages(threadId)
        if (messages.isEmpty()) return
        local.insert(LocalStore.SPAM, messages, System.nanoTime(), reason)
        deleteMessages(messages.map { it.id })
    }

    fun spam(): List<StoredMessage> = local.all(LocalStore.SPAM)

    fun restoreSpam(items: List<StoredMessage>) = restore(LocalStore.SPAM, items)

    fun deleteSpam(ids: Collection<Long>) = local.delete(LocalStore.SPAM, ids)

    private fun restore(table: String, items: List<StoredMessage>) {
        items.forEach { m ->
            val values = ContentValues().apply {
                put(Sms.ADDRESS, m.address)
                put(Sms.BODY, m.body)
                put(Sms.DATE, m.date)
                put(Sms.TYPE, m.type)
                put(Sms.READ, if (m.read) 1 else 0)
                put(Sms.SEEN, 1)
                if (m.subId >= 0) put(Sms.SUBSCRIPTION_ID, m.subId)
            }
            runCatching { resolver.insert(Sms.CONTENT_URI, values) }
        }
        local.delete(table, items.map { it.id })
    }

    // --- Inbox cleaner -----------------------------------------------------------------------

    /** Incoming code and offer messages older than the given ages (null = skip that kind). */
    fun findCleanable(otpOlderThan: Long?, offersOlderThan: Long?): List<Message> {
        val now = System.currentTimeMillis()
        val out = ArrayList<Message>()
        query(Sms.CONTENT_URI, "${Sms.TYPE} = ?", arrayOf(Sms.MESSAGE_TYPE_INBOX.toString()), "${Sms.DATE} DESC") { c ->
            val m = c.toMessage()
            val age = now - m.date
            val otp = OtpParser.parse(m.body, m.address)
            val category = Categorizer.categorize(m.address, m.body, otp)
            val match = (category == Category.Otps && otpOlderThan != null && age > otpOlderThan) ||
                (category == Category.Offers && offersOlderThan != null && age > offersOlderThan)
            if (match) out += m
        }
        return out
    }

    fun trashAll(messages: List<Message>): Long? = trash(messages)

    private fun loadByIds(ids: Collection<Long>): List<Message> {
        if (ids.isEmpty()) return emptyList()
        val out = ArrayList<Message>()
        ids.chunked(200).forEach { chunk ->
            val where = "${Sms._ID} IN (${chunk.joinToString(",") { "?" }})"
            query(Sms.CONTENT_URI, where, chunk.map { it.toString() }.toTypedArray(), "${Sms.DATE} ASC") { c -> out += c.toMessage() }
        }
        return out
    }

    /** Permanent delete, for auto-cleanup of codes and items already copied to the bin or spam. */
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

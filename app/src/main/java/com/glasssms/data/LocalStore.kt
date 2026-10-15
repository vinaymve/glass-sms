package com.glasssms.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** A message kept by the app outside the system SMS store: in the bin, or caught as spam. */
data class StoredMessage(
    val id: Long,
    val address: String,
    val body: String,
    val date: Long,
    val type: Int,
    val read: Boolean,
    val subId: Int,
    /** Bin: the delete batch it belongs to (for Undo). Spam: the reason it was caught. */
    val batch: Long,
    val reason: String,
    val storedAt: Long,
)

/**
 * App-private SQLite for the Recently Deleted bin and the Spam folder. Android's SMS provider has
 * no trash, so deleted messages are copied here first and can be written back on restore.
 */
class LocalStore(context: Context) : SQLiteOpenHelper(context, "glass.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        for (table in listOf(TRASH, SPAM)) {
            db.execSQL(
                """CREATE TABLE $table (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    address TEXT NOT NULL,
                    body TEXT NOT NULL,
                    date INTEGER NOT NULL,
                    type INTEGER NOT NULL,
                    read INTEGER NOT NULL,
                    sub_id INTEGER NOT NULL,
                    batch INTEGER NOT NULL,
                    reason TEXT NOT NULL,
                    stored_at INTEGER NOT NULL
                )""",
            )
            db.execSQL("CREATE INDEX ${table}_batch ON $table(batch)")
            db.execSQL("CREATE INDEX ${table}_address ON $table(address)")
        }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun insert(table: String, messages: List<Message>, batch: Long, reason: String = "") {
        val now = System.currentTimeMillis()
        writableDatabase.transaction {
            messages.forEach { m ->
                insert(
                    table,
                    null,
                    ContentValues().apply {
                        put("address", m.address)
                        put("body", m.body)
                        put("date", m.date)
                        put("type", m.type)
                        put("read", if (m.read) 1 else 0)
                        put("sub_id", -1)
                        put("batch", batch)
                        put("reason", reason)
                        put("stored_at", now)
                    },
                )
            }
        }
    }

    fun all(table: String): List<StoredMessage> = query(table, null, null)

    fun byBatch(table: String, batch: Long) = query(table, "batch = ?", arrayOf(batch.toString()))

    fun byAddress(table: String, address: String) = query(table, "address = ?", arrayOf(address))

    fun delete(table: String, ids: Collection<Long>) {
        if (ids.isEmpty()) return
        writableDatabase.transaction { ids.forEach { delete(table, "id = ?", arrayOf(it.toString())) } }
    }

    fun deleteOlderThan(table: String, cutoff: Long) {
        writableDatabase.delete(table, "stored_at < ?", arrayOf(cutoff.toString()))
    }

    fun clear(table: String) {
        writableDatabase.delete(table, null, null)
    }

    private fun query(table: String, where: String?, args: Array<String>?): List<StoredMessage> {
        val out = ArrayList<StoredMessage>()
        readableDatabase.query(table, null, where, args, null, null, "date DESC").use { c ->
            while (c.moveToNext()) {
                out += StoredMessage(
                    id = c.getLong(c.getColumnIndexOrThrow("id")),
                    address = c.getString(c.getColumnIndexOrThrow("address")),
                    body = c.getString(c.getColumnIndexOrThrow("body")),
                    date = c.getLong(c.getColumnIndexOrThrow("date")),
                    type = c.getInt(c.getColumnIndexOrThrow("type")),
                    read = c.getInt(c.getColumnIndexOrThrow("read")) == 1,
                    subId = c.getInt(c.getColumnIndexOrThrow("sub_id")),
                    batch = c.getLong(c.getColumnIndexOrThrow("batch")),
                    reason = c.getString(c.getColumnIndexOrThrow("reason")),
                    storedAt = c.getLong(c.getColumnIndexOrThrow("stored_at")),
                )
            }
        }
        return out
    }

    private inline fun SQLiteDatabase.transaction(block: SQLiteDatabase.() -> Unit) {
        beginTransaction()
        try {
            block()
            setTransactionSuccessful()
        } finally {
            endTransaction()
        }
    }

    companion object {
        const val TRASH = "trash"
        const val SPAM = "spam"
    }
}

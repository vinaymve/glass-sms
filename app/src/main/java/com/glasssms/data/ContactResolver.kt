package com.glasssms.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.PhoneLookup
import android.telephony.PhoneNumberUtils
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

data class ContactPhone(val name: String, val number: String, val label: String)

class ContactResolver(private val context: Context) {
    // "" marks a looked-up address with no matching contact.
    private val cache = ConcurrentHashMap<String, String>()

    fun name(address: String): String? {
        if (address.isBlank()) return null
        cache[address]?.let { return it.ifEmpty { null } }
        if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return null
        val name = try {
            val uri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(address))
            context.contentResolver.query(uri, arrayOf(PhoneLookup.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        } catch (_: Exception) {
            null
        }
        cache[address] = name.orEmpty()
        return name
    }

    /** Contacts whose name or number matches [query], one row per phone number. */
    fun search(query: String, limit: Int = 40): List<ContactPhone> {
        if (query.isBlank()) return emptyList()
        if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return emptyList()
        val out = ArrayList<ContactPhone>()
        val seen = HashSet<String>()
        try {
            val uri = Uri.withAppendedPath(Phone.CONTENT_FILTER_URI, Uri.encode(query.trim()))
            val projection = arrayOf(Phone.DISPLAY_NAME, Phone.NUMBER, Phone.TYPE, Phone.LABEL)
            context.contentResolver.query(uri, projection, null, null, null)?.use { c ->
                while (c.moveToNext() && out.size < limit) {
                    val name = c.getString(0) ?: continue
                    val number = c.getString(1) ?: continue
                    // The same number is often stored by several synced accounts.
                    if (!seen.add(name + "|" + number.filter { it.isDigit() || it == '+' })) continue
                    val label = Phone.getTypeLabel(context.resources, c.getInt(2), c.getString(3)).toString()
                    out += ContactPhone(name, number, label.lowercase())
                }
            }
        } catch (_: Exception) {
            // Malformed query or provider hiccup: no suggestions rather than a crash.
        }
        return out
    }

    fun displayName(address: String): String = name(address) ?: formatAddress(address)

    fun clear() = cache.clear()

    companion object {
        // Indian-style business sender IDs: "AD-AIRTEL-S", "JX-HDFCBK" → "AIRTEL", "HDFCBK".
        private val senderId = Regex("""^[A-Za-z]{2}-([A-Za-z0-9]{3,})(?:-[A-Za-z])?$""")

        fun formatAddress(address: String): String {
            senderId.matchEntire(address)?.let { return it.groupValues[1] }
            if (address.any { it.isLetter() }) return address
            return PhoneNumberUtils.formatNumber(address, Locale.getDefault().country) ?: address
        }
    }
}

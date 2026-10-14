package com.glasssms.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract.PhoneLookup
import android.telephony.PhoneNumberUtils
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

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

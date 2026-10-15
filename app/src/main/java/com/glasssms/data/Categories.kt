package com.glasssms.data

import com.glasssms.otp.Otp

enum class Category(val label: String) {
    Personal("Personal"),
    Transactions("Transactions"),
    Otps("OTPs"),
    Offers("Offers"),
}

/**
 * Sorts a conversation the way Indian inboxes need it.
 *
 * Business senders in India carry a TRAI suffix added by the operator since May 2025:
 * -P promotional, -S service, -T transactional, -G government (e.g. "JX-HDFCBK-S").
 * That label is authoritative, so it wins; older or foreign messages fall back to keywords.
 */
object Categorizer {
    private val header = Regex("""^[A-Za-z]{2}-([A-Za-z0-9]{3,})(?:-([PSTGpstg]))?$""")
    private val promo = Regex(
        """(?i)(%\s*off|\boff\b|\bsale\b|discount|offer|cashback|coupon|voucher|\bfree\b|\bwin\b|limited time|shop now|use code|\bdeal)""",
    )

    fun categorize(address: String, body: String, otp: Otp?): Category {
        if (otp != null) return Category.Otps
        val suffix = header.matchEntire(address.trim())?.groupValues?.get(2)?.uppercase()
        return when {
            suffix == "P" -> Category.Offers
            suffix == "S" || suffix == "T" || suffix == "G" -> Category.Transactions
            !isBusinessSender(address) -> Category.Personal
            // Older headers without a suffix, or foreign senders: judge by the text.
            promo.containsMatchIn(body) -> Category.Offers
            else -> Category.Transactions
        }
    }

    /** Alphanumeric sender IDs and short codes are businesses; full phone numbers are people. */
    fun isBusinessSender(address: String): Boolean {
        val a = address.trim()
        if (a.any { it.isLetter() }) return true
        val digits = a.filter(Char::isDigit)
        return digits.length in 3..8
    }

    /** Stable identity for blocking: "JX-HDFCBK-S" → "HDFCBK", "+91 98765 43210" → "9876543210". */
    fun senderKey(address: String): String {
        val a = address.trim()
        header.matchEntire(a)?.let { return it.groupValues[1].uppercase() }
        if (a.any { it.isLetter() }) return a.uppercase()
        val digits = a.filter(Char::isDigit)
        return if (digits.length > 10) digits.takeLast(10) else digits
    }

    fun isPromotionalHeader(address: String) =
        header.matchEntire(address.trim())?.groupValues?.get(2)?.equals("P", ignoreCase = true) == true
}

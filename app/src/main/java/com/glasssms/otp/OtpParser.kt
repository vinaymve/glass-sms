package com.glasssms.otp

import java.util.concurrent.TimeUnit

data class Otp(
    val code: String,
    /** Best guess at who sent it, e.g. "Google" or "HDFCBK". */
    val service: String?,
    val validForMillis: Long,
    /** Where the code sits in the message body, for highlighting. */
    val start: Int,
    val end: Int,
) {
    /** "482913" → "482 913", "12345678" → "1234 5678". */
    val pretty: String
        get() = when {
            code.length == 6 && code.all(Char::isDigit) -> "${code.take(3)} ${code.drop(3)}"
            code.length == 8 && code.all(Char::isDigit) -> "${code.take(4)} ${code.drop(4)}"
            else -> code
        }
}

/**
 * On-device one-time-code detection. Pure Kotlin so it is easy to unit test.
 *
 * Strategy: first look for a code right after a keyword ("code is 1234", "OTP: 1234"); if that
 * fails and the message mentions a keyword at all, take the digit run closest to it that does
 * not look like money or part of an account number.
 */
object OtpParser {
    private val DEFAULT_VALIDITY = TimeUnit.MINUTES.toMillis(10)

    private val keyword = Regex(
        """(?i)\b(otp|one[\s-]?time|verification|verify|passcode|security\s+code|auth(?:entication)?|log\s?-?in|sign[\s-]?in|2fa|code|pin|password|token|confirm(?:ation)?)\b""",
    )

    private val strongKeyword = Regex(
        """(?i)\b(otp|one[\s-]?time|verification|verify|passcode|security\s+code|2fa|log\s?-?in|sign[\s-]?in|authenticat\w*)\b""",
    )

    private val promo = Regex("""(?i)(%\s*off|\boff\b|\bsale\b|discount|promo|coupon|offer|cashback|voucher)""")

    // "code is 482913", "OTP: 4821", "PIN - AB12CD", "code: 482-913"
    private val direct = Regex(
        """(?i)\b(?:code|otp|pin|passcode|password|token)\b\s*(?:is|:|-|=)?\s*[:#-]?\s*([A-Z0-9]{4,8}|\d{3}[\s-]\d{3})\b""",
    )

    private val digits = Regex("""(?<![\w₹$€£.,/])(\d{3}[\s-]\d{3}|\d{4,8})(?![\w/]|[.,]\d)""")

    private val validity = Regex(
        """(?i)(?:valid|expires?|expiring|expiry)\s*(?:for|in|within|after)?\s*(?:the\s+next\s+)?(\d{1,3})\s*(seconds?|secs?|minutes?|mins?|hours?|hrs?)\b""",
    )

    private val servicePatterns = listOf(
        Regex("""^\s*<#>\s*([A-Z][\w&.]{1,24})"""),
        Regex("""^\s*\[([^\]]{2,24})]"""),
        Regex("""^\s*([A-Z][\w&.]{1,24})\s*:"""),
        Regex("""(?i:your|for)\s+([A-Z][\w&.]{1,24})\s+(?i:verification|login|log-in|security|one-time|otp|code|account|pin|sign-in)"""),
        Regex("""(?i:from|by|on)\s+([A-Z][\w&.]{1,24})\b"""),
    )

    private val moneyPrefix = Regex("""(?i)(rs\.?|inr|usd|eur|gbp|₹|\$|€|£|a/c|acct|amt|amount|bal)\s*$""")

    private val notService = setOf("The", "This", "Your", "You", "Use", "Do", "Dear", "Please", "Never", "OTP", "Code", "Verification")

    fun parse(body: String, sender: String? = null): Otp? {
        if (body.isBlank() || !keyword.containsMatchIn(body)) return null
        // "Use code SAVE20 for 20% off" is marketing, not a login code.
        if (promo.containsMatchIn(body) && !strongKeyword.containsMatchIn(body)) return null
        val (code, range) = findCode(body) ?: return null
        return Otp(
            code = code,
            service = findService(body, sender),
            validForMillis = findValidity(body),
            start = range.first,
            end = range.last + 1,
        )
    }

    private fun findCode(body: String): Pair<String, IntRange>? {
        direct.findAll(body).forEach { m ->
            val g = m.groups[1] ?: return@forEach
            val raw = g.value
            if (raw.any(Char::isDigit) && !looksLikeMoney(body, g.range.first)) {
                return normalize(raw) to g.range
            }
        }

        val keywordPositions = keyword.findAll(body).map { it.range.first }.toList()
        return digits.findAll(body)
            .filter { !looksLikeMoney(body, it.range.first) }
            .filter { !looksLikeYear(it.value) }
            .minByOrNull { m -> keywordPositions.minOf { kotlin.math.abs(it - m.range.first) } }
            ?.let { normalize(it.value) to it.range }
    }

    private fun normalize(raw: String) = raw.filter(Char::isLetterOrDigit).uppercase()

    private fun looksLikeMoney(body: String, start: Int) =
        moneyPrefix.containsMatchIn(body.substring((start - 6).coerceAtLeast(0), start))

    private fun looksLikeYear(value: String) =
        value.length == 4 && value.toIntOrNull()?.let { it in 1990..2099 } == true

    private fun findValidity(body: String): Long {
        val m = validity.find(body) ?: return DEFAULT_VALIDITY
        val n = m.groupValues[1].toLongOrNull() ?: return DEFAULT_VALIDITY
        val unit = m.groupValues[2].lowercase()
        val millis = when {
            unit.startsWith("s") -> TimeUnit.SECONDS.toMillis(n)
            unit.startsWith("h") -> TimeUnit.HOURS.toMillis(n)
            else -> TimeUnit.MINUTES.toMillis(n)
        }
        return millis.coerceIn(TimeUnit.SECONDS.toMillis(30), TimeUnit.HOURS.toMillis(24))
    }

    private fun findService(body: String, sender: String?): String? {
        for (p in servicePatterns) {
            val name = p.find(body)?.groupValues?.get(1)?.trim() ?: continue
            if (name !in notService && name.any(Char::isLetter)) return name
        }
        // Alphanumeric sender IDs like "VM-HDFCBK-S" or "AX-AMAZON".
        if (sender != null && sender.any(Char::isLetter)) {
            return sender.substringAfter('-').substringBefore('-').ifBlank { sender }
        }
        return null
    }
}

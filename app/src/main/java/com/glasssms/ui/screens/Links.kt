package com.glasssms.ui.screens

import android.net.Uri
import android.util.Patterns

/** Something tappable inside a message. */
sealed interface LinkTarget {
    data class Url(val url: String) : LinkTarget
    data class Phone(val number: String) : LinkTarget
    data class Email(val address: String) : LinkTarget
    data class Upi(val id: String) : LinkTarget
}

data class FoundLink(val start: Int, val end: Int, val target: LinkTarget)

object Links {
    // UPI handles end in a bank/PSP handle, not a real domain, so they're checked before e-mail.
    private val upi = Regex(
        """(?<![\w.@-])[\w.-]{2,}@(ok\w+|ybl|ibl|axl|paytm|upi|apl|yapl|pt\w+|axisbank|icici|sbi|hdfcbank|kotak|freecharge|airtel|jio|wa\w+|abfspay|fam|slc|naviaxis|superyes|tapicici|pinelabs)(?![\w.@])""",
        RegexOption.IGNORE_CASE,
    )

    // Indian mobiles (+91 optional), 1800 toll-free, and other international numbers with a +.
    private val phone = Regex(
        """(?<![\w+])(?:\+91[\s-]?)?[6-9]\d{4}[\s-]?\d{5}(?!\d)|(?<!\d)1800[\s-]?\d{3}[\s-]?\d{3,4}(?!\d)|(?<![\w+])\+\d{10,13}(?!\d)""",
    )

    fun find(text: String): List<FoundLink> {
        val found = ArrayList<FoundLink>()
        fun free(start: Int, end: Int) = found.none { start < it.end && end > it.start }

        upi.findAll(text).forEach { m -> found += FoundLink(m.range.first, m.range.last + 1, LinkTarget.Upi(m.value)) }
        Patterns.EMAIL_ADDRESS.matcher(text).let { m ->
            while (m.find()) if (free(m.start(), m.end())) found += FoundLink(m.start(), m.end(), LinkTarget.Email(m.group()))
        }
        Patterns.WEB_URL.matcher(text).let { m ->
            while (m.find()) {
                val raw = m.group().trimEnd('.', ',', ')', '!', '?')
                if (looksLikeUrl(raw) && free(m.start(), m.start() + raw.length)) {
                    found += FoundLink(m.start(), m.start() + raw.length, LinkTarget.Url(raw))
                }
            }
        }
        phone.findAll(text).forEach { m ->
            if (free(m.range.first, m.range.last + 1)) found += FoundLink(m.range.first, m.range.last + 1, LinkTarget.Phone(m.value))
        }
        return found.sortedBy { it.start }
    }

    private val knownTlds = setOf(
        "com", "in", "net", "org", "co", "io", "ly", "me", "app", "gov", "edu", "info", "biz", "xyz", "ai", "dev",
        "cc", "to", "gl", "gd", "shop", "store", "online", "site", "live", "link", "page", "tech", "club", "world",
        "bank", "money", "pay", "news", "tv", "uk", "us", "ae", "sg", "au", "ca",
    )

    // WEB_URL also matches things like "Rs.1357.26" and "13:31.Not You?"; demand a scheme, "www.",
    // or a real-looking host: letters before the TLD and a known (or lowercase two-letter) TLD.
    private fun looksLikeUrl(raw: String): Boolean {
        if (raw.startsWith("http://", true) || raw.startsWith("https://", true) || raw.startsWith("www.", true)) return true
        val host = raw.substringBefore('/').substringBefore('?')
        val labels = host.split('.')
        if (labels.size < 2) return false
        val tld = labels.last()
        val name = labels[labels.size - 2]
        if (name.none { it.isLetter() }) return false
        return tld.lowercase() in knownTlds && tld == tld.lowercase() ||
            (tld.length == 2 && tld.all { it.isLowerCase() })
    }

    fun uriFor(url: String): Uri = Uri.parse(if (url.contains("://")) url else "https://$url")

    private val shorteners = setOf(
        "bit.ly", "tinyurl.com", "t.co", "goo.gl", "cutt.ly", "rb.gy", "is.gd", "shorturl.at", "tiny.cc",
        "s.id", "t.ly", "ow.ly", "rebrand.ly", "bl.ink", "short.io", "v.gd", "qr.ae", "lnkd.in",
    )

    /** Reasons to be careful before opening, shown in the link sheet. Empty means nothing stood out. */
    fun warnings(url: String): List<String> {
        val uri = uriFor(url)
        val host = uri.host.orEmpty().lowercase().removePrefix("www.")
        val path = uri.path.orEmpty().lowercase()
        return buildList {
            if (host in shorteners) add("This is a shortened link, so the real destination is hidden.")
            if (path.endsWith(".apk")) add("This link downloads an Android app (.apk). Banks and couriers never send apps by SMS.")
            if (host.matches(Regex("""\d{1,3}(\.\d{1,3}){3}"""))) add("The link points to a raw IP address instead of a website name.")
            if (host.startsWith("xn--") || host.contains(".xn--")) add("The address uses look-alike characters.")
            if (uri.scheme.equals("http", ignoreCase = true)) add("The connection isn't encrypted (http).")
        }
    }

    fun host(url: String) = uriFor(url).host.orEmpty().removePrefix("www.")
}

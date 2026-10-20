package com.randwise.app.domain

/** Normalises noisy bank descriptions so the same merchant groups together. */
object MerchantText {

    private val noisePrefixes = listOf(
        "POS PURCHASE", "POS", "CARD PURCHASE", "PURCHASE", "DEBIT ORDER", "DEBIT ORD", "DEBIT",
        "ACB DEBIT", "ACB", "PAYMENT TO", "PAYMENT", "EFT", "MAGTAPE", "SCHEDULED PYMT", "IB PAYMENT",
        "FNB APP PAYMENT TO", "SEND MONEY", "RECURRING", "CHEQUE CARD PURCHASE",
    ).sortedByDescending { it.length }

    private val nonLetters = Regex("[^A-Z ]")
    private val spaces = Regex("\\s+")

    /** Upper-case, strip digits/punctuation/card refs and common bank prefixes, keep up to 3 words. */
    fun normalize(raw: String): String {
        var s = raw.uppercase().replace(nonLetters, " ").replace(spaces, " ").trim()
        var changed = true
        while (changed) {
            changed = false
            for (p in noisePrefixes) {
                if (s.startsWith("$p ") || s == p) {
                    s = s.removePrefix(p).trim()
                    changed = true
                }
            }
        }
        val words = s.split(' ').filter { it.length > 1 }
        return words.take(3).joinToString(" ").ifEmpty { raw.uppercase().trim().take(24) }
    }

    fun matches(merchant: String, pattern: String): Boolean {
        if (pattern.isBlank()) return false
        return normalize(merchant).contains(pattern.uppercase().trim())
    }

    /** Short label safe to share with the AI gateway: first word only, never reference numbers. */
    fun anonymizedLabel(raw: String): String = normalize(raw).split(' ').firstOrNull().orEmpty()

    private val feeRegex = Regex(
        "(OVERDRAFT|UNPAID|INSUFFICIENT|DISHONOU?R|RETURNED DEBIT|RD FEE|PENALTY|LATE (PAYMENT )?FEE|" +
            "SERVICE FEE|ADMIN FEE|TRANSACTION FEE|INTEREST ON OVERDRAFT|BOUNCED)",
    )

    fun isFeeTrap(raw: String): Boolean = feeRegex.containsMatchIn(raw.uppercase())
}

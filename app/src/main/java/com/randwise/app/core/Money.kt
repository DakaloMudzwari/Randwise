package com.randwise.app.core

import kotlin.math.abs

/**
 * All monetary values in RandWise are Long minor units (cents). No floating point is ever
 * used for money (FDS §9 "Precision"). These helpers format and parse at the UI boundary only.
 */
object Money {

    /** Formats cents as "R 1 234,56" (South African convention: space grouping, comma decimal). */
    fun format(minor: Long, showSign: Boolean = false, symbol: String = "R"): String {
        val negative = minor < 0
        val absMinor = if (minor == Long.MIN_VALUE) Long.MAX_VALUE else abs(minor)
        val rands = absMinor / 100
        val cents = absMinor % 100
        val grouped = groupThousands(rands.toString())
        // Non-breaking space keeps "R" and the number on one line.
        val body = "$symbol $grouped,${cents.toString().padStart(2, '0')}"
        return when {
            negative -> "-$body"
            showSign && minor > 0 -> "+$body"
            else -> body
        }
    }

    /** Compact format without cents, e.g. "R 12 400". Rounds half away from zero. */
    fun formatWhole(minor: Long, symbol: String = "R"): String {
        val rounded = divRound(minor, 100)
        val grouped = groupThousands(abs(rounded).toString())
        return (if (rounded < 0) "-" else "") + "$symbol $grouped"
    }

    private fun groupThousands(digits: String): String {
        val sb = StringBuilder()
        digits.reversed().forEachIndexed { i, c ->
            if (i > 0 && i % 3 == 0) sb.append(' ')
            sb.append(c)
        }
        return sb.reverse().toString()
    }

    /**
     * Parses a user- or statement-supplied amount into cents. Accepts:
     * "1234.56", "1 234,56", "R1,234.56", "-45.00", "(45.00)", "45.00 Cr", "45.00 Dr", "45.00-".
     * Returns null when the text holds no parseable amount.
     */
    fun parse(raw: String): Long? {
        var s = raw.trim()
        if (s.isEmpty()) return null
        var negative = false
        val upper = s.uppercase()
        if (upper.endsWith("DR")) { negative = true; s = s.dropLast(2) }
        else if (upper.endsWith("CR")) { s = s.dropLast(2) }
        s = s.trim()
        if (s.startsWith("(") && s.endsWith(")")) { negative = true; s = s.substring(1, s.length - 1) }
        if (s.endsWith("-")) { negative = !negative; s = s.dropLast(1) }
        s = s.replace("ZAR", "", ignoreCase = true).replace("R", "", ignoreCase = true)
        s = s.replace(" ", "").replace(" ", "").replace("'", "")
        if (s.startsWith("-")) { negative = !negative; s = s.substring(1) }
        if (s.startsWith("+")) s = s.substring(1)
        if (s.isEmpty()) return null

        // Decide which separator is the decimal mark: the last '.' or ',' followed by 1-2 digits.
        val lastDot = s.lastIndexOf('.')
        val lastComma = s.lastIndexOf(',')
        val decIdx = maxOf(lastDot, lastComma)
        val intPart: String
        val fracPart: String
        if (decIdx >= 0 && s.length - decIdx - 1 in 1..2) {
            intPart = s.substring(0, decIdx).replace(".", "").replace(",", "")
            fracPart = s.substring(decIdx + 1)
        } else {
            intPart = s.replace(".", "").replace(",", "")
            fracPart = ""
        }
        if (intPart.isEmpty() && fracPart.isEmpty()) return null
        if (!intPart.all { it.isDigit() } || !fracPart.all { it.isDigit() }) return null
        val whole = if (intPart.isEmpty()) 0L else intPart.toLongOrNull() ?: return null
        val frac = fracPart.padEnd(2, '0').toLong()
        val value = Math.addExact(Math.multiplyExact(whole, 100L), frac)
        return if (negative) -value else value
    }

    /** Integer division rounding half away from zero. */
    fun divRound(numerator: Long, denominator: Long): Long {
        require(denominator != 0L) { "denominator must not be zero" }
        val q = numerator / denominator
        val r = numerator % denominator
        if (r == 0L) return q
        val twiceR = abs(r) * 2
        val sameSign = (numerator < 0) == (denominator < 0)
        return if (twiceR >= abs(denominator)) (if (sameSign) q + 1 else q - 1) else q
    }

    /** value * bp / 10_000 rounded — basis points avoid floats for percentages/APRs. */
    fun applyBasisPoints(minor: Long, basisPoints: Long): Long = divRound(minor * basisPoints, 10_000)

    /** Ratio as basis points (e.g. 0.8 -> 8000). Returns 0 when denominator is 0. */
    fun ratioBp(numerator: Long, denominator: Long): Long =
        if (denominator == 0L) 0L else divRound(numerator * 10_000, denominator)

    fun formatPercentBp(bp: Long): String {
        val sign = if (bp < 0) "-" else ""
        val a = abs(bp)
        return "$sign${a / 100}.${(a % 100) / 10}%"
    }

    /** Machine-readable "-1234.56" for CSV export. */
    fun toPlain(minor: Long): String {
        val a = abs(minor)
        return (if (minor < 0) "-" else "") + "${a / 100}.${(a % 100).toString().padStart(2, '0')}"
    }
}

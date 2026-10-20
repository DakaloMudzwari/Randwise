package com.randwise.app.importer

import com.randwise.app.core.Money
import java.security.MessageDigest
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField
import java.util.Locale

/** A single statement row; [signedAmount] is + for money in, − for money out. */
data class ParsedLine(
    val date: LocalDate,
    val description: String,
    val signedAmount: Long,
    val balance: Long? = null,
)

data class ParsedStatement(
    val lines: List<ParsedLine>,
    val openingBalance: Long?,
    val closingBalance: Long?,
    val warnings: List<String> = emptyList(),
)

data class BalanceCheck(
    /** null when the statement didn't expose enough balances to check. */
    val balanced: Boolean?,
    val expectedClosing: Long?,
    val statedClosing: Long?,
    val message: String,
)

object StatementValidator {
    /** F03: opening balance + Σ lines must equal the closing balance, to the cent. */
    fun validate(s: ParsedStatement): BalanceCheck {
        val open = s.openingBalance
        val close = s.closingBalance
        if (open == null || close == null) {
            return BalanceCheck(null, null, close, "Statement has no opening/closing balance — totals could not be verified.")
        }
        val expected = open + s.lines.sumOf { it.signedAmount }
        return if (expected == close) {
            BalanceCheck(true, expected, close, "Balanced: ${Money.format(open)} + ${s.lines.size} transactions = ${Money.format(close)}.")
        } else {
            BalanceCheck(false, expected, close, "Out of balance by ${Money.format(close - expected)}. Some rows may be missing or misread.")
        }
    }
}

/** Deterministic fingerprints make re-importing a statement idempotent (FDS §9 Deduplication). */
object Fingerprint {
    private val ws = Regex("\\s+")

    fun of(accountId: Long, line: ParsedLine, occurrence: Int): String {
        val desc = line.description.uppercase().replace(ws, " ").trim()
        val raw = "$accountId|${line.date.toEpochDay()}|${line.signedAmount}|$desc|$occurrence"
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    /** Identical rows inside one statement are legitimate (two R20 taxi fares), so number them. */
    fun forBatch(accountId: Long, lines: List<ParsedLine>): List<String> {
        val seen = HashMap<String, Int>()
        return lines.map { line ->
            val key = "${line.date}|${line.signedAmount}|${line.description.uppercase().replace(ws, " ").trim()}"
            val n = seen.getOrDefault(key, 0)
            seen[key] = n + 1
            of(accountId, line, n)
        }
    }
}

object DateParsing {
    private fun fmt(p: String): DateTimeFormatter =
        DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern(p).toFormatter(Locale.ENGLISH)

    private val full = listOf(
        "yyyy-MM-dd", "yyyy/MM/dd", "yyyyMMdd", "dd/MM/yyyy", "d/M/yyyy", "dd-MM-yyyy", "d-M-yyyy",
        "dd.MM.yyyy", "dd MMM yyyy", "d MMM yyyy", "dd-MMM-yyyy", "d-MMM-yyyy", "dd MMMM yyyy", "d MMMM yyyy",
        "dd/MM/yy", "d/M/yy", "dd MMM yy", "d MMM yy", "dd-MMM-yy", "MMM d, yyyy", "MMM dd yyyy",
    ).map(::fmt)

    private fun yearless(p: String): DateTimeFormatter =
        DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern(p)
            .parseDefaulting(ChronoField.YEAR, 2000).toFormatter(Locale.ENGLISH)

    private val partial = listOf("dd MMM", "d MMM", "dd/MM", "d/M", "ddMMM").map(::yearless)

    /** Parses a full date; for year-less dates (common on SA PDF statements) uses [defaultYear]. */
    fun parse(token: String, defaultYear: Int? = null): LocalDate? {
        val t = token.trim().replace(Regex("\\s+"), " ")
        if (t.isEmpty()) return null
        for (f in full) runCatching { return LocalDate.parse(t, f) }
        if (defaultYear != null) {
            for (f in partial) runCatching { return LocalDate.parse(t, f).withYear(defaultYear) }
        }
        return null
    }

    fun isYearless(token: String): Boolean = parse(token) == null && parse(token, 2000) != null
}

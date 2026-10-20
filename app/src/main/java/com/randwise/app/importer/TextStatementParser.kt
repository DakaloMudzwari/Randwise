package com.randwise.app.importer

import com.randwise.app.core.Money
import java.time.LocalDate

/**
 * Parses statement text extracted from a PDF (PDFBox) or from OCR (ML Kit) into transactions.
 * Expected row shape: DATE  DESCRIPTION ...  AMOUNT  [BALANCE]
 */
object TextStatementParser {

    private const val MONTHS = "(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Sept|Oct|Nov|Dec)[a-z]*"
    private val datePrefix = Regex(
        "^\\s*(\\d{4}[-/]\\d{2}[-/]\\d{2}|\\d{1,2}[/.-]\\d{1,2}[/.-]\\d{2,4}|\\d{1,2}[ -]$MONTHS(?:[ -]\\d{2,4})?|\\d{1,2}/\\d{1,2})\\b",
        RegexOption.IGNORE_CASE,
    )
    private const val AMOUNT = "-?R?\\s?\\(?-?\\d{1,3}(?:[ ,\\u00A0]\\d{3})*[.,]\\d{2}\\)?(?:\\s?(?:Cr|Dr|CR|DR|cr|dr))?-?"
    private val trailingAmounts = Regex("(?:\\s+($AMOUNT))(?:\\s+($AMOUNT))?\\s*$")
    private val openingRx = Regex("(OPENING BALANCE|BALANCE BROUGHT FORWARD|BALANCE B/F|PREVIOUS BALANCE)[^\\d-]*($AMOUNT)", RegexOption.IGNORE_CASE)
    private val closingRx = Regex("(CLOSING BALANCE|BALANCE CARRIED FORWARD|BALANCE C/F|NEW BALANCE)[^\\d-]*($AMOUNT)", RegexOption.IGNORE_CASE)
    private val yearRx = Regex("\\b(20\\d{2})\\b")

    fun parse(text: String, fallbackYear: Int = LocalDate.now().year): ParsedStatement {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val statementYear = yearRx.findAll(text).map { it.value.toInt() }.groupingBy { it }.eachCount()
            .maxByOrNull { it.value }?.key ?: fallbackYear
        val usesSuffix = Regex("\\d[.,]\\d{2}\\s?(Cr|Dr)\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)

        var opening: Long? = null
        var closing: Long? = null
        val out = mutableListOf<ParsedLine>()
        var year = statementYear
        var lastMonth = -1

        for (line in lines) {
            openingRx.find(line)?.let { m -> if (opening == null) opening = signed(m.groupValues[2], usesSuffix, isBalance = true) }
            closingRx.find(line)?.let { m -> closing = signed(m.groupValues[2], usesSuffix, isBalance = true) }
            if (openingRx.containsMatchIn(line) || closingRx.containsMatchIn(line)) continue

            val dm = datePrefix.find(line) ?: continue
            val dateToken = dm.groupValues[1]
            val rest = line.substring(dm.range.last + 1)
            val am = trailingAmounts.find(rest) ?: continue
            val a1 = am.groupValues[1]
            val a2 = am.groupValues[2]
            val desc = rest.substring(0, am.range.first).trim().ifEmpty { "Unknown" }

            var date = DateParsing.parse(dateToken)
            if (date == null) {
                date = DateParsing.parse(dateToken, year) ?: continue
                // Year-less statements spanning December -> January.
                if (lastMonth > 0 && date.monthValue < lastMonth && lastMonth - date.monthValue > 6) {
                    year += 1
                    date = date.withYear(year)
                }
            }
            lastMonth = date.monthValue

            val amount = signed(a1, usesSuffix, isBalance = false) ?: continue
            val balance = if (a2.isNotEmpty()) signed(a2, usesSuffix, isBalance = true) else null
            out += ParsedLine(date, desc, amount, balance)
        }

        val warnings = mutableListOf<String>()
        if (out.isEmpty()) warnings += "No transaction rows recognised in the document text."
        val ordered = if (out.size > 1 && out.first().date.isAfter(out.last().date)) out.reversed() else out
        val signedLines = SignInference.fromBalances(ordered)
        if (opening == null) {
            signedLines.firstOrNull()?.let { f -> if (f.balance != null) opening = f.balance - f.signedAmount }
        }
        if (closing == null) closing = signedLines.lastOrNull()?.balance
        return ParsedStatement(signedLines, opening, closing, warnings)
    }

    /**
     * With a Cr/Dr convention, unsigned transaction amounts are debits; unsigned balances are
     * positive. Without it, the printed sign is used as-is.
     */
    private fun signed(token: String, usesSuffix: Boolean, isBalance: Boolean): Long? {
        val value = Money.parse(token) ?: return null
        if (!usesSuffix || isBalance) return value
        val t = token.trim().uppercase()
        return when {
            t.endsWith("CR") -> kotlin.math.abs(value)
            t.endsWith("DR") -> -kotlin.math.abs(value)
            value < 0 -> value
            else -> -value
        }
    }
}

package com.randwise.app.importer

import com.randwise.app.core.Money

/**
 * Parses bank CSV exports (FNB, Standard Bank, Absa, Nedbank, Capitec, TymeBank, Discovery…).
 * Handles preamble rows, quoted fields, single signed amount columns or separate debit/credit
 * columns, and descending date order.
 */
object CsvStatementParser {

    private enum class Col { DATE, DESC, AMOUNT, DEBIT, CREDIT, BALANCE }

    fun parse(text: String): ParsedStatement {
        val rawLines = text.replace("﻿", "").lines().filter { it.isNotBlank() }
        if (rawLines.isEmpty()) return ParsedStatement(emptyList(), null, null, listOf("File is empty."))
        val delimiter = detectDelimiter(rawLines.take(30))
        val rows = rawLines.map { splitRow(it, delimiter) }

        val headerIdx = rows.take(25).indexOfFirst { row -> row.any { it.trim().lowercase().contains("date") } && row.size >= 3 }
        val mapping = if (headerIdx >= 0) mapHeader(rows[headerIdx]) else emptyMap()
        val dataRows = if (headerIdx >= 0) rows.drop(headerIdx + 1) else rows

        var opening: Long? = null
        var closing: Long? = null
        val warnings = mutableListOf<String>()
        val parsed = mutableListOf<ParsedLine>()

        for (row in dataRows) {
            val joined = row.joinToString(" ").uppercase()
            if (joined.contains("OPENING BALANCE") || joined.contains("BALANCE BROUGHT FORWARD") || joined.contains("BALANCE B/F")) {
                opening = row.mapNotNull { Money.parse(it) }.lastOrNull() ?: opening
                continue
            }
            if (joined.contains("CLOSING BALANCE") || joined.contains("BALANCE CARRIED FORWARD")) {
                closing = row.mapNotNull { Money.parse(it) }.lastOrNull() ?: closing
                continue
            }
            val line = if (mapping.containsKey(Col.DATE)) parseMapped(row, mapping) else parseHeuristic(row)
            if (line != null) parsed += line
        }
        if (parsed.isEmpty()) warnings += "No transactions recognised. Check that the file is a bank statement export."

        // Normalise to chronological order (many banks export newest first).
        val ordered = if (parsed.size > 1 && parsed.first().date.isAfter(parsed.last().date)) parsed.reversed() else parsed
        val withSigns = SignInference.fromBalances(ordered)

        if (opening == null) {
            ordered.firstOrNull()?.let { first -> if (first.balance != null) opening = first.balance - withSigns.first().signedAmount }
        }
        if (closing == null) closing = withSigns.lastOrNull()?.balance

        return ParsedStatement(withSigns, opening, closing, warnings)
    }

    private fun mapHeader(header: List<String>): Map<Col, Int> {
        val m = mutableMapOf<Col, Int>()
        header.forEachIndexed { i, raw ->
            val h = raw.trim().lowercase()
            when {
                Col.DATE !in m && h.contains("date") && !h.contains("value") -> m[Col.DATE] = i
                Col.BALANCE !in m && h.contains("balance") -> m[Col.BALANCE] = i
                Col.DEBIT !in m && (h == "debit" || h.contains("debit amount") || h.contains("money out") || h.contains("withdrawal") || h == "debits") -> m[Col.DEBIT] = i
                Col.CREDIT !in m && (h == "credit" || h.contains("credit amount") || h.contains("money in") || h.contains("deposit") || h == "credits") -> m[Col.CREDIT] = i
                Col.AMOUNT !in m && h.contains("amount") -> m[Col.AMOUNT] = i
                Col.DESC !in m && listOf("description", "details", "narrative", "transaction", "merchant", "reference", "payee").any { h.contains(it) } -> m[Col.DESC] = i
            }
        }
        if (Col.DATE in m && Col.DESC !in m) {
            // Pick the first text column not otherwise mapped.
            header.indices.firstOrNull { it !in m.values }?.let { m[Col.DESC] = it }
        }
        return m
    }

    private fun parseMapped(row: List<String>, m: Map<Col, Int>): ParsedLine? {
        fun cell(c: Col) = m[c]?.let { row.getOrNull(it)?.trim() }.orEmpty()
        val date = DateParsing.parse(cell(Col.DATE)) ?: return null
        val amount = when {
            m.containsKey(Col.AMOUNT) && cell(Col.AMOUNT).isNotEmpty() -> Money.parse(cell(Col.AMOUNT))
            else -> {
                val debit = Money.parse(cell(Col.DEBIT))?.let { -kotlin.math.abs(it) } ?: 0L
                val credit = Money.parse(cell(Col.CREDIT))?.let { kotlin.math.abs(it) } ?: 0L
                if (debit == 0L && credit == 0L) null else debit + credit
            }
        } ?: return null
        val desc = cell(Col.DESC).ifEmpty { "Unknown" }
        return ParsedLine(date, desc, amount, Money.parse(cell(Col.BALANCE)))
    }

    private fun parseHeuristic(row: List<String>): ParsedLine? {
        val dateIdx = row.indexOfFirst { DateParsing.parse(it) != null }
        if (dateIdx < 0) return null
        val date = DateParsing.parse(row[dateIdx])!!
        val numeric = row.withIndex().filter { (i, v) -> i != dateIdx && v.any { it.isDigit() } && Money.parse(v) != null && looksLikeMoney(v) }
        if (numeric.isEmpty()) return null
        val amount = Money.parse(numeric.first().value)!!
        val balance = if (numeric.size >= 2) Money.parse(numeric.last().value) else null
        val desc = row.withIndex().filter { (i, _) -> i != dateIdx && numeric.none { it.index == i } }
            .maxByOrNull { it.value.length }?.value?.trim().orEmpty().ifEmpty { "Unknown" }
        return ParsedLine(date, desc, amount, balance)
    }

    private fun looksLikeMoney(v: String) = Regex("[.,]\\d{2}(\\s*(cr|dr))?\\)?-?$", RegexOption.IGNORE_CASE).containsMatchIn(v.trim())

    fun detectDelimiter(sample: List<String>): Char {
        val candidates = listOf(',', ';', '\t', '|')
        return candidates.maxBy { d -> sample.sumOf { line -> splitRow(line, d).size - 1 } }
    }

    /** RFC 4180-style split supporting quoted fields with embedded delimiters and "" escapes. */
    fun splitRow(line: String, delimiter: Char): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> { sb.append('"'); i++ }
                c == '"' -> inQuotes = !inQuotes
                c == delimiter && !inQuotes -> { out += sb.toString(); sb.clear() }
                else -> sb.append(c)
            }
            i++
        }
        out += sb.toString()
        return out
    }
}

object SignInference {
    /**
     * When a running balance is present, the balance movement is the source of truth for the
     * direction of each amount (FNB PDFs, for example, print debits unsigned).
     */
    fun fromBalances(lines: List<ParsedLine>): List<ParsedLine> {
        if (lines.isEmpty()) return lines
        val out = ArrayList<ParsedLine>(lines.size)
        var prevBalance: Long? = null
        for (line in lines) {
            val bal = line.balance
            var amount = line.signedAmount
            if (bal != null && prevBalance != null) {
                val diff = bal - prevBalance
                if (kotlin.math.abs(diff) == kotlin.math.abs(amount)) amount = diff
            }
            out += line.copy(signedAmount = amount)
            if (bal != null) prevBalance = bal
        }
        return out
    }
}

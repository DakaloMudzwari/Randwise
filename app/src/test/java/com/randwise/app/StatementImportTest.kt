package com.randwise.app

import com.randwise.app.data.model.Category
import com.randwise.app.data.model.Bucket
import com.randwise.app.importer.Categorizer
import com.randwise.app.importer.CsvStatementParser
import com.randwise.app.importer.Fingerprint
import com.randwise.app.importer.StatementValidator
import com.randwise.app.importer.TextStatementParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class StatementImportTest {

    private val signedCsv = """
        "Account statement"
        "Date","Description","Amount","Balance"
        2026/10/01,"SALARY ACME",25000.00,26000.00
        2026/10/02,"CHECKERS SANDTON",-850.50,25149.50
        2026/10/02,"CHECKERS SANDTON",-850.50,24299.00
        2026/10/03,"NETFLIX.COM",-199.00,24100.00
    """.trimIndent()

    @Test
    fun `parses signed CSV with preamble and validates balances`() {
        val s = CsvStatementParser.parse(signedCsv)
        assertEquals(4, s.lines.size)
        assertEquals(LocalDate.of(2026, 10, 1), s.lines.first().date)
        assertEquals(2_500_000, s.lines.first().signedAmount)
        assertEquals(100_000, s.openingBalance)
        assertEquals(2_410_000, s.closingBalance)
        assertEquals(true, StatementValidator.validate(s).balanced)
    }

    @Test
    fun `debit credit columns, semicolons, comma decimals, newest first`() {
        val csv = "Date;Description;Debit;Credit;Balance\n03/10/2026;Uber trip;120,00;;880,00\n01/10/2026;Deposit;;1000,00;1000,00"
        val s = CsvStatementParser.parse(csv)
        assertEquals(listOf(100_000L, -12_000L), s.lines.map { it.signedAmount })
        assertEquals(0, s.openingBalance)
        assertEquals(true, StatementValidator.validate(s).balanced)
    }

    @Test
    fun `re-importing the same statement yields identical fingerprints`() {
        val first = Fingerprint.forBatch(1, CsvStatementParser.parse(signedCsv).lines)
        val second = Fingerprint.forBatch(1, CsvStatementParser.parse(signedCsv).lines)
        assertEquals(first, second)
        // Two genuine identical purchases on one day stay distinct.
        assertEquals(4, first.toSet().size)
        // Same rows in another account are different transactions.
        assertTrue(Fingerprint.forBatch(2, CsvStatementParser.parse(signedCsv).lines).none { it in first })
    }

    @Test
    fun `parses FNB style PDF text with Cr suffix and year-less dates`() {
        val text = """
            Statement period 01 Oct 2026 to 31 Oct 2026
            Opening Balance 1 000.00 Cr
            01 Oct Salary Acme 25 000.00Cr 26 000.00Cr
            02 Oct POS Purchase Checkers 850.50 25 149.50Cr
            05 Oct Netflix 199.00 24 950.50Cr
            Closing Balance 24 950.50 Cr
        """.trimIndent()
        val s = TextStatementParser.parse(text)
        assertEquals(listOf(2_500_000L, -85_050L, -19_900L), s.lines.map { it.signedAmount })
        assertEquals(LocalDate.of(2026, 10, 5), s.lines.last().date)
        assertEquals("POS Purchase Checkers", s.lines[1].description)
        assertEquals(true, StatementValidator.validate(s).balanced)
    }

    @Test
    fun `flags out of balance statements`() {
        val s = CsvStatementParser.parse(signedCsv).let { it.copy(lines = it.lines.drop(1)) }
        assertEquals(false, StatementValidator.validate(s).balanced)
    }

    @Test
    fun `categoriser uses whole words`() {
        val cats = Categorizer.DEFAULT_CATEGORIES.mapIndexed { i, c -> Category(id = i + 1L, name = c.name, bucket = c.bucket, colorArgb = c.color) }
        fun cat(desc: String, credit: Boolean = false) = Categorizer.categorize(desc, credit, emptyList(), cats)?.let { id -> cats.first { it.id == id }.name }
        assertEquals("Groceries", cat("POS PURCHASE CHECKERS HYPER 4411"))
        assertEquals("Bank Fees", cat("MONTHLY ACCOUNT FEE"))
        assertNull(cat("COFFEE SHOP"))
        assertNull(cat("TRAINING CENTRE"))
        assertEquals("Salary", cat("ACME SALARY OCT", credit = true))
        assertEquals("Subscriptions", cat("NETFLIX.COM"))
        assertTrue(cats.any { it.bucket == Bucket.INCOME })
    }
}

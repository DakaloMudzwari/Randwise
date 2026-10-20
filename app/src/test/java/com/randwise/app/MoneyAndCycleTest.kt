package com.randwise.app

import com.randwise.app.core.Money
import com.randwise.app.domain.PayCycle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.LocalDate

class MoneyTest {

    @ParameterizedTest
    @CsvSource(
        delimiter = '|',
        value = [
            "1234.56|123456", "1 234,56|123456", "R1,234.56|123456", "-45.00|-4500", "(45.00)|-4500",
            "45.00 Cr|4500", "45.00 Dr|-4500", "45.00-|-4500", "R 12 000|1200000", "0.5|50", "1.234.567,89|123456789",
        ],
    )
    fun `parses bank and user amount formats into cents`(input: String, expected: Long) {
        assertEquals(expected, Money.parse(input))
    }

    @Test
    fun `rejects non-numeric input`() {
        assertNull(Money.parse("abc"))
        assertNull(Money.parse(""))
    }

    @Test
    fun `formats with SA grouping and comma decimals`() {
        val nb = ' ' // non-breaking: amounts never wrap between "R" and the digits
        assertEquals("R${nb}1${nb}234,56", Money.format(123456))
        assertEquals("-R${nb}0,05", Money.format(-5))
        assertEquals("+R${nb}10,00", Money.format(1000, showSign = true))
        assertEquals("R${nb}12${nb}400", Money.formatWhole(1_240_000))
        assertEquals("-1234.56", Money.toPlain(-123456))
    }

    @Test
    fun `integer rounding is half away from zero`() {
        assertEquals(3, Money.divRound(5, 2))
        assertEquals(-3, Money.divRound(-5, 2))
        assertEquals(2, Money.divRound(7, 4))
        assertEquals(3, Money.divRound(10, 3))
        assertEquals(8000, Money.ratioBp(80, 100))
    }

    @Test
    fun `summing many cent values never drifts`() {
        val values = List(10_000) { 10L } // 10 000 × R0.10
        assertEquals(100_000L, values.sum())
    }
}

class PayCycleTest {

    @Test
    fun `weekend payday moves to Friday`() {
        // 25 Oct 2026 is a Sunday -> paid Friday 23 Oct.
        val c = PayCycle.forDate(LocalDate.of(2026, 10, 19), 25, adjustForWeekend = true)
        assertEquals(LocalDate.of(2026, 9, 25), c.start)
        assertEquals(LocalDate.of(2026, 10, 23), c.nextPayday)
        assertEquals(4, c.daysUntilPayday(LocalDate.of(2026, 10, 19)))
    }

    @Test
    fun `cycle rolls over on the adjusted payday`() {
        val c = PayCycle.forDate(LocalDate.of(2026, 10, 24), 25, adjustForWeekend = true)
        assertEquals(LocalDate.of(2026, 10, 23), c.start)
        assertEquals(LocalDate.of(2026, 11, 25), c.nextPayday)
    }

    @Test
    fun `payday 31 clamps to month length`() {
        val c = PayCycle.forDate(LocalDate.of(2027, 2, 10), 31, adjustForWeekend = false)
        assertEquals(LocalDate.of(2027, 1, 31), c.start)
        assertEquals(LocalDate.of(2027, 2, 28), c.nextPayday)
        assertEquals(28, c.totalDays)
    }

    @Test
    fun `elapsed and remaining days add up`() {
        val c = PayCycle.forDate(LocalDate.of(2027, 2, 10), 31, adjustForWeekend = false)
        val today = LocalDate.of(2027, 2, 10)
        assertEquals(c.totalDays, c.elapsedDays(today) + c.remainingDays(today))
    }
}

package com.randwise.app

import com.randwise.app.domain.PayCycle
import com.randwise.app.domain.SaPublicHolidays
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.YearMonth

class HolidayTest {

    @Test
    fun `easter dates`() {
        assertEquals(LocalDate.of(2025, 4, 20), SaPublicHolidays.easterSunday(2025))
        assertEquals(LocalDate.of(2026, 4, 5), SaPublicHolidays.easterSunday(2026))
        assertEquals(LocalDate.of(2027, 3, 28), SaPublicHolidays.easterSunday(2027))
    }

    @Test
    fun `2026 includes Good Friday, Family Day and Sunday-rule Mondays`() {
        val h = SaPublicHolidays.forYear(2026)
        assertTrue(LocalDate.of(2026, 4, 3) in h)  // Good Friday
        assertTrue(LocalDate.of(2026, 4, 6) in h)  // Family Day
        assertTrue(LocalDate.of(2026, 8, 10) in h) // Women's Day 9 Aug is a Sunday -> Monday off
        assertFalse(LocalDate.of(2026, 10, 19) in h)
    }

    @Test
    fun `payday on a public holiday moves to the previous business day`() {
        // 25 Dec 2026 is a Friday (Christmas) -> Thursday 24th.
        assertEquals(LocalDate.of(2026, 12, 24), PayCycle.paydayIn(YearMonth.of(2026, 12), 25, adjustForWeekend = true))
        // 27 Apr 2026 is Freedom Day (Monday); the weekend before is skipped too -> Friday 24th.
        assertEquals(LocalDate.of(2026, 4, 24), PayCycle.paydayIn(YearMonth.of(2026, 4), 27, adjustForWeekend = true))
        // Turning adjustment off keeps the calendar date.
        assertEquals(LocalDate.of(2026, 12, 25), PayCycle.paydayIn(YearMonth.of(2026, 12), 25, adjustForWeekend = false))
    }
}

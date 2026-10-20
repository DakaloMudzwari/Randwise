package com.randwise.app.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.MonthDay

/**
 * South African public holidays (Public Holidays Act 36 of 1994). When a holiday falls on a Sunday,
 * the following Monday is also a public holiday. Employers and banks don't pay on these days, so
 * paydays roll back to the previous business day.
 */
object SaPublicHolidays {

    private val fixed = listOf(
        MonthDay.of(1, 1),   // New Year's Day
        MonthDay.of(3, 21),  // Human Rights Day
        MonthDay.of(4, 27),  // Freedom Day
        MonthDay.of(5, 1),   // Workers' Day
        MonthDay.of(6, 16),  // Youth Day
        MonthDay.of(8, 9),   // National Women's Day
        MonthDay.of(9, 24),  // Heritage Day
        MonthDay.of(12, 16), // Day of Reconciliation
        MonthDay.of(12, 25), // Christmas Day
        MonthDay.of(12, 26), // Day of Goodwill
    )

    private val cache = HashMap<Int, Set<LocalDate>>()

    fun forYear(year: Int): Set<LocalDate> = synchronized(cache) {
        cache.getOrPut(year) {
            val days = fixed.map { it.atYear(year) }.toMutableSet()
            val easter = easterSunday(year)
            days += easter.minusDays(2) // Good Friday
            days += easter.plusDays(1)  // Family Day
            // Sunday rule: the Monday after a Sunday holiday is also a holiday.
            days += days.filter { it.dayOfWeek == DayOfWeek.SUNDAY }.map { it.plusDays(1) }
            days
        }
    }

    fun isHoliday(date: LocalDate): Boolean = date in forYear(date.year)

    fun isBusinessDay(date: LocalDate): Boolean =
        date.dayOfWeek != DayOfWeek.SATURDAY && date.dayOfWeek != DayOfWeek.SUNDAY && !isHoliday(date)

    /** [date] itself if it's a business day, otherwise the closest earlier one. */
    fun previousBusinessDay(date: LocalDate): LocalDate {
        var d = date
        while (!isBusinessDay(d)) d = d.minusDays(1)
        return d
    }

    /** Anonymous Gregorian algorithm (Meeus/Jones/Butcher). */
    fun easterSunday(year: Int): LocalDate {
        val a = year % 19
        val b = year / 100
        val c = year % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31
        val day = ((h + l - 7 * m + 114) % 31) + 1
        return LocalDate.of(year, month, day)
    }
}

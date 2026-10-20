package com.randwise.app.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * A pay cycle runs from one payday (inclusive) to the next payday (exclusive).
 * Budgets, velocity and safe-to-spend are all computed per cycle (F04).
 */
data class PayCycle(
    val start: LocalDate,
    val nextPayday: LocalDate,
    val payday: Int,
    val adjustForWeekend: Boolean,
) {
    /** Last day of this cycle (inclusive). */
    val end: LocalDate get() = nextPayday.minusDays(1)
    val totalDays: Long get() = ChronoUnit.DAYS.between(start, nextPayday)

    /** Days elapsed including today, clamped to [1, totalDays]. */
    fun elapsedDays(today: LocalDate): Long =
        (ChronoUnit.DAYS.between(start, today) + 1).coerceIn(1, totalDays)

    fun remainingDays(today: LocalDate): Long = totalDays - elapsedDays(today)

    fun daysUntilPayday(today: LocalDate): Long =
        ChronoUnit.DAYS.between(today, nextPayday).coerceAtLeast(0)

    operator fun contains(date: LocalDate): Boolean = !date.isBefore(start) && date.isBefore(nextPayday)
    fun containsEpochDay(day: Long): Boolean = day >= start.toEpochDay() && day < nextPayday.toEpochDay()

    fun previous(): PayCycle = forDate(start.minusDays(1), payday, adjustForWeekend)
    fun next(): PayCycle = forDate(nextPayday, payday, adjustForWeekend)

    companion object {
        /** The actual payday date in a month: clamped to month length, moved to Friday if on a weekend. */
        fun paydayIn(month: YearMonth, payday: Int, adjustForWeekend: Boolean): LocalDate {
            val day = payday.coerceIn(1, month.lengthOfMonth())
            var date = month.atDay(day)
            if (adjustForWeekend) {
                date = when (date.dayOfWeek) {
                    DayOfWeek.SATURDAY -> date.minusDays(1)
                    DayOfWeek.SUNDAY -> date.minusDays(2)
                    else -> date
                }
            }
            return date
        }

        fun forDate(today: LocalDate, payday: Int, adjustForWeekend: Boolean): PayCycle {
            val ym = YearMonth.from(today)
            val thisMonth = paydayIn(ym, payday, adjustForWeekend)
            return if (!today.isBefore(thisMonth)) {
                // A weekend-adjusted payday can slide into the previous month only in theory
                // (day 1-2); guard against a zero-length cycle anyway.
                var next = paydayIn(ym.plusMonths(1), payday, adjustForWeekend)
                if (!next.isAfter(today)) next = paydayIn(ym.plusMonths(2), payday, adjustForWeekend)
                PayCycle(thisMonth, next, payday, adjustForWeekend)
            } else {
                val prev = paydayIn(ym.minusMonths(1), payday, adjustForWeekend)
                PayCycle(prev, thisMonth, payday, adjustForWeekend)
            }
        }

        /** Due date of a bill with [dueDay] in the given month (clamped to month length). */
        fun dueDateIn(month: YearMonth, dueDay: Int): LocalDate =
            month.atDay(dueDay.coerceIn(1, month.lengthOfMonth()))
    }
}

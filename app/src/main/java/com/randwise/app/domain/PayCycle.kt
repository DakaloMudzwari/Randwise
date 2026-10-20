package com.randwise.app.domain

import com.randwise.app.data.model.BillFrequency
import com.randwise.app.data.model.PayFrequency
import com.randwise.app.data.model.RecurringBill
import com.randwise.app.data.model.UserProfile
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/** When the user gets paid. Monthly pay uses [dayOfMonth]; weekly/fortnightly pay repeats from [anchor]. */
data class PaySchedule(
    val frequency: PayFrequency = PayFrequency.MONTHLY,
    val dayOfMonth: Int = 25,
    val adjustForWeekend: Boolean = true,
    val anchor: LocalDate? = null,
) {
    companion object {
        fun of(p: UserProfile) = PaySchedule(
            frequency = p.payFrequency,
            dayOfMonth = p.payday,
            adjustForWeekend = p.adjustPaydayForWeekend,
            anchor = p.payAnchorDate?.let(LocalDate::ofEpochDay),
        )
    }
}

/**
 * A pay cycle runs from one payday (inclusive) to the next payday (exclusive).
 * Budgets, velocity and safe-to-spend are all computed per cycle (F04).
 */
data class PayCycle(
    val start: LocalDate,
    val nextPayday: LocalDate,
    val schedule: PaySchedule,
) {
    /** Monthly convenience constructor (kept for callers that only know a day of month). */
    constructor(start: LocalDate, nextPayday: LocalDate, payday: Int, adjustForWeekend: Boolean) :
        this(start, nextPayday, PaySchedule(PayFrequency.MONTHLY, payday, adjustForWeekend))

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

    fun previous(): PayCycle = forDate(start.minusDays(1), schedule)
    fun next(): PayCycle = forDate(nextPayday, schedule)

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

        fun forDate(today: LocalDate, payday: Int, adjustForWeekend: Boolean): PayCycle =
            forDate(today, PaySchedule(PayFrequency.MONTHLY, payday, adjustForWeekend))

        fun forDate(today: LocalDate, schedule: PaySchedule): PayCycle {
            val period = schedule.frequency.days
            if (period != null) {
                // Weekly / fortnightly: fixed-length cycles counted from a known payday (floorMod handles dates before it).
                val anchor = schedule.anchor ?: today
                val offset = Math.floorMod(ChronoUnit.DAYS.between(anchor, today), period)
                val start = today.minusDays(offset)
                return PayCycle(start, start.plusDays(period), schedule)
            }
            val ym = YearMonth.from(today)
            val thisMonth = paydayIn(ym, schedule.dayOfMonth, schedule.adjustForWeekend)
            return if (!today.isBefore(thisMonth)) {
                var next = paydayIn(ym.plusMonths(1), schedule.dayOfMonth, schedule.adjustForWeekend)
                if (!next.isAfter(today)) next = paydayIn(ym.plusMonths(2), schedule.dayOfMonth, schedule.adjustForWeekend)
                PayCycle(thisMonth, next, schedule)
            } else {
                PayCycle(paydayIn(ym.minusMonths(1), schedule.dayOfMonth, schedule.adjustForWeekend), thisMonth, schedule)
            }
        }

        /** Every payday in (from, to]. */
        fun paydaysBetween(from: LocalDate, to: LocalDate, schedule: PaySchedule): List<LocalDate> {
            val out = mutableListOf<LocalDate>()
            var c = forDate(from, schedule)
            while (!c.nextPayday.isAfter(to)) {
                if (c.nextPayday.isAfter(from)) out += c.nextPayday
                c = c.next()
            }
            return out
        }

        /** Due date of a monthly bill with [dueDay] in the given month (clamped to month length). */
        fun dueDateIn(month: YearMonth, dueDay: Int): LocalDate =
            month.atDay(dueDay.coerceIn(1, month.lengthOfMonth()))

        /** Every due date of [bill] in [from, to] (inclusive), for any frequency. */
        fun dueDatesBetween(bill: RecurringBill, from: LocalDate, to: LocalDate): List<LocalDate> {
            if (to.isBefore(from)) return emptyList()
            val anchor = bill.anchorDate?.let(LocalDate::ofEpochDay)
            return when (bill.frequency) {
                BillFrequency.MONTHLY -> monthsSpanning(from, to).map { dueDateIn(it, bill.dueDay) }.filter { !it.isBefore(from) && !it.isAfter(to) }
                BillFrequency.WEEKLY -> {
                    val a = anchor ?: return emptyList()
                    var d = from.plusDays(Math.floorMod(ChronoUnit.DAYS.between(from, a), 7L))
                    buildList { while (!d.isAfter(to)) { add(d); d = d.plusDays(7) } }
                }
                BillFrequency.YEARLY -> {
                    val a = anchor ?: return emptyList()
                    (from.year..to.year).map { y -> YearMonth.of(y, a.monthValue).let { it.atDay(a.dayOfMonth.coerceAtMost(it.lengthOfMonth())) } }
                        .filter { !it.isBefore(from) && !it.isAfter(to) }
                }
            }
        }

        fun monthsSpanning(from: LocalDate, to: LocalDate): List<YearMonth> {
            val out = mutableListOf<YearMonth>()
            var ym = YearMonth.from(from)
            val last = YearMonth.from(to)
            while (!ym.isAfter(last)) { out += ym; ym = ym.plusMonths(1) }
            return out
        }
    }
}

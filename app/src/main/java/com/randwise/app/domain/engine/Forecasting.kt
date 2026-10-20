package com.randwise.app.domain.engine

import com.randwise.app.core.Money
import com.randwise.app.data.model.RecurringBill
import com.randwise.app.data.model.UserProfile
import com.randwise.app.domain.PayCycle
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

data class ScheduledEvent(val date: LocalDate, val signedAmount: Long, val label: String)

data class ForecastPoint(val date: LocalDate, val balance: Long)

data class Forecast(
    val points: List<ForecastPoint>,
    val lowest: ForecastPoint,
    val firstBelowBuffer: LocalDate?,
    val firstNegative: LocalDate?,
    val endBalance: Long,
)

/** Everything the forecaster needs; also reused by the affordability simulator. */
data class ForecastInputs(
    val today: LocalDate,
    val startBalance: Long,
    /** Historical variable spend total over [variableDays] days -> daily velocity without float drift. */
    val variableTotal: Long,
    val variableDays: Long,
    val events: List<ScheduledEvent>,
    val safetyBuffer: Long,
)

/** F07 Cash-Flow Forecast over 7/14/30/90 days. */
object CashFlowForecaster {

    val HORIZONS = listOf(7, 14, 30, 90)

    fun forecast(inputs: ForecastInputs, horizonDays: Int, extraEvents: List<ScheduledEvent> = emptyList()): Forecast {
        val events = (inputs.events + extraEvents).groupBy { it.date }
        val points = ArrayList<ForecastPoint>(horizonDays + 1)
        var eventSum = 0L
        var firstBelow: LocalDate? = null
        var firstNeg: LocalDate? = null
        // Events dated today or earlier (e.g. overdue bills, a purchase made now) land on day 0.
        eventSum += events.filterKeys { !it.isAfter(inputs.today) }.values.flatten().sumOf { it.signedAmount }
        for (i in 0..horizonDays) {
            val date = inputs.today.plusDays(i.toLong())
            if (i > 0) eventSum += events[date].orEmpty().sumOf { it.signedAmount }
            // Cumulative velocity computed from the total each day so rounding never accumulates.
            val variable = if (inputs.variableDays <= 0) 0 else Money.divRound(inputs.variableTotal * i, inputs.variableDays)
            val balance = inputs.startBalance + eventSum - variable
            points += ForecastPoint(date, balance)
            if (firstBelow == null && balance < inputs.safetyBuffer) firstBelow = date
            if (firstNeg == null && balance < 0) firstNeg = date
        }
        val lowest = points.minBy { it.balance }
        return Forecast(points, lowest, firstBelow, firstNeg, points.last().balance)
    }

    /** Paydays and confirmed bills that fall after today within the horizon, plus unpaid overdue bills. */
    fun scheduledEvents(
        today: LocalDate,
        horizonDays: Int,
        profile: UserProfile,
        bills: List<RecurringBill>,
        currentCycleOccurrences: List<BillOccurrence>,
    ): List<ScheduledEvent> {
        val end = today.plusDays(horizonDays.toLong())
        val out = mutableListOf<ScheduledEvent>()
        val months = BillSchedule.monthsSpanning(today, end)
        for (ym in months) {
            val pay = PayCycle.paydayIn(ym, profile.payday, profile.adjustPaydayForWeekend)
            if (pay.isAfter(today) && !pay.isAfter(end)) out += ScheduledEvent(pay, profile.monthlyIncomeMinor, "Salary")
        }
        val paidThisCycle = currentCycleOccurrences.filter { it.paid }.map { it.bill.id to it.dueDate }.toSet()
        for (occ in currentCycleOccurrences) {
            if (!occ.paid && !occ.dueDate.isAfter(today)) {
                out += ScheduledEvent(today.plusDays(1), -occ.bill.expectedAmountMinor, occ.bill.name)
            }
        }
        for (bill in bills.filter { it.isConfirmed }) {
            for (ym in months) {
                val due = PayCycle.dueDateIn(ym, bill.dueDay)
                if (!due.isAfter(today) || due.isAfter(end)) continue
                if ((bill.id to due) in paidThisCycle) continue
                out += ScheduledEvent(due, -bill.expectedAmountMinor, bill.name)
            }
        }
        return out.sortedBy { it.date }
    }
}

enum class Verdict { AFFORDABLE, TIGHT, NOT_AFFORDABLE }

data class AffordabilityResult(
    val verdict: Verdict,
    val amount: Long,
    val recurringMonthly: Boolean,
    val safeToSpendBefore: Long,
    val safeToSpendAfter: Long,
    val lowestWithout: ForecastPoint,
    val lowestWith: ForecastPoint,
    val horizonDays: Int,
    val explanations: List<String>,
)

/** F06: simulate a purchase against future bills and the buffer before payday, and explain the impact. */
object AffordabilityEngine {

    fun check(
        amount: Long,
        recurringMonthly: Boolean,
        inputs: ForecastInputs,
        safeToSpend: Long,
        nextPayday: LocalDate,
        unpaidBillsTotal: Long,
        unpaidBillsCount: Int,
    ): AffordabilityResult {
        require(amount > 0) { "amount must be positive" }
        val today = inputs.today
        val toPayday = ChronoUnit.DAYS.between(today, nextPayday).toInt().coerceAtLeast(1)
        val horizon = if (recurringMonthly) maxOf(90, toPayday) else toPayday
        val purchaseEvents = if (recurringMonthly) {
            (0 until 4).map { m ->
                val ym = YearMonth.from(today).plusMonths(m.toLong())
                ScheduledEvent(PayCycle.dueDateIn(ym, today.dayOfMonth), -amount, "Purchase")
            }.filter { !it.date.isBefore(today) }
        } else {
            listOf(ScheduledEvent(today, -amount, "Purchase"))
        }
        val without = CashFlowForecaster.forecast(inputs, horizon)
        val with = CashFlowForecaster.forecast(inputs, horizon, purchaseEvents)
        val after = safeToSpend - amount

        val verdict = when {
            with.lowest.balance < 0 || after < -inputs.safetyBuffer -> Verdict.NOT_AFFORDABLE
            with.lowest.balance < inputs.safetyBuffer || after < 0 -> Verdict.TIGHT
            else -> Verdict.AFFORDABLE
        }

        val day = java.time.format.DateTimeFormatter.ofPattern("EEE d MMM", java.util.Locale.ENGLISH)
        val why = mutableListOf<String>()
        why += "Safe-to-spend goes from ${Money.format(safeToSpend)} to ${Money.format(after)}."
        if (unpaidBillsCount > 0) {
            why += "$unpaidBillsCount bill${if (unpaidBillsCount == 1) "" else "s"} totalling ${Money.format(unpaidBillsTotal)} still due before payday (${nextPayday.format(day)})."
        }
        why += "Lowest projected balance${if (recurringMonthly) " in the next $horizon days" else " before payday"}: " +
            "${Money.format(with.lowest.balance)} on ${with.lowest.date.format(day)} (without it: ${Money.format(without.lowest.balance)})."
        why += "Your safety buffer is ${Money.format(inputs.safetyBuffer)}."
        if (recurringMonthly) why += "As a monthly commitment this costs ${Money.format(amount * 12)} per year."
        with.firstNegative?.let { why += "Your account is projected to go negative on ${it.format(day)} — expect overdraft or unpaid-debit fees." }
        return AffordabilityResult(verdict, amount, recurringMonthly, safeToSpend, after, without.lowest, with.lowest, horizon, why)
    }
}

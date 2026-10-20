package com.randwise.app.domain.engine

import com.randwise.app.core.Money
import com.randwise.app.data.model.Category
import com.randwise.app.data.model.RecurringBill
import com.randwise.app.data.model.Transaction
import com.randwise.app.data.model.TxType
import com.randwise.app.domain.MerchantText
import com.randwise.app.domain.PayCycle
import java.time.LocalDate
import java.time.YearMonth

/** One expected payment of a confirmed bill inside a pay cycle. */
data class BillOccurrence(val bill: RecurringBill, val dueDate: LocalDate, val paid: Boolean)

object BillSchedule {

    /** Confirmed bills due in [cycle], marking as many as paid as there are matching payments. */
    fun occurrencesInCycle(
        bills: List<RecurringBill>,
        cycle: PayCycle,
        transactions: List<Transaction>,
    ): List<BillOccurrence> {
        val cycleTx = transactions.filter { it.type == TxType.EXPENSE && cycle.containsEpochDay(it.date) }
        val out = mutableListOf<BillOccurrence>()
        for (bill in bills.filter { it.isConfirmed }) {
            val dueDates = monthsSpanning(cycle.start, cycle.end)
                .map { PayCycle.dueDateIn(it, bill.dueDay) }
                .filter { it in cycle }
                .distinct()
                .sorted()
            var payments = cycleTx.count { MerchantText.matches(it.merchant, bill.merchantPattern) }
            for (due in dueDates) {
                out += BillOccurrence(bill, due, paid = payments > 0)
                if (payments > 0) payments--
            }
        }
        return out.sortedBy { it.dueDate }
    }

    fun isBillPayment(tx: Transaction, bills: List<RecurringBill>): Boolean =
        tx.type == TxType.EXPENSE && bills.any { it.isConfirmed && MerchantText.matches(tx.merchant, it.merchantPattern) }

    fun monthsSpanning(from: LocalDate, to: LocalDate): List<YearMonth> {
        val out = mutableListOf<YearMonth>()
        var ym = YearMonth.from(from)
        val last = YearMonth.from(to)
        while (!ym.isAfter(last)) { out += ym; ym = ym.plusMonths(1) }
        return out
    }
}

data class SafeToSpendResult(
    val balance: Long,
    val unpaidBills: List<BillOccurrence>,
    val unpaidBillsTotal: Long,
    val plannedSavingsRemaining: Long,
    val safetyBuffer: Long,
    val safeToSpend: Long,
    val daysUntilPayday: Long,
    /** Safe-to-spend spread evenly over the days left before payday (never negative). */
    val perDay: Long,
)

/** Equation 1 (FDS §4). */
object SafeToSpendEngine {

    fun compute(balance: Long, unpaidBillsBeforePayday: Long, plannedSavings: Long, safetyBuffer: Long): Long =
        balance - unpaidBillsBeforePayday - plannedSavings - safetyBuffer

    fun evaluate(
        balance: Long,
        occurrences: List<BillOccurrence>,
        plannedSavingsRemaining: Long,
        safetyBuffer: Long,
        daysUntilPayday: Long,
    ): SafeToSpendResult {
        val unpaid = occurrences.filter { !it.paid }
        val unpaidTotal = unpaid.sumOf { it.bill.expectedAmountMinor }
        val safe = compute(balance, unpaidTotal, plannedSavingsRemaining, safetyBuffer)
        val perDay = if (safe <= 0) 0 else safe / daysUntilPayday.coerceAtLeast(1)
        return SafeToSpendResult(balance, unpaid, unpaidTotal, plannedSavingsRemaining, safetyBuffer, safe, daysUntilPayday, perDay)
    }
}

/** Equation 2 (FDS §4): projected month-end spend from current velocity. */
object VelocityEngine {

    /**
     * ProjectedSpend = CurrentSpent + (VariableSpent / ElapsedDays) × RemainingDays + KnownUpcomingBills.
     * [variableSpent] is CurrentSpent minus bill payments, so a rent debit on day 1 is not extrapolated.
     */
    fun projectLinear(
        currentSpent: Long,
        elapsedDays: Long,
        remainingDays: Long,
        knownUpcomingBills: Long,
        variableSpent: Long = currentSpent,
    ): Long {
        val elapsed = elapsedDays.coerceAtLeast(1)
        val extrapolated = Money.divRound(variableSpent.coerceAtLeast(0) * remainingDays, elapsed)
        return currentSpent + extrapolated + knownUpcomingBills
    }

    /** Weighted variant: last 7 days carry 60% of the daily rate, the whole cycle 40%. */
    fun projectWeighted(
        currentSpent: Long,
        variableSpent: Long,
        recentSevenDayVariable: Long,
        elapsedDays: Long,
        remainingDays: Long,
        knownUpcomingBills: Long,
    ): Long {
        if (elapsedDays < 7) return projectLinear(currentSpent, elapsedDays, remainingDays, knownUpcomingBills, variableSpent)
        val num = (6 * recentSevenDayVariable.coerceAtLeast(0) * elapsedDays + 4 * variableSpent.coerceAtLeast(0) * 7) * remainingDays
        val den = 70 * elapsedDays
        return currentSpent + Money.divRound(num, den) + knownUpcomingBills
    }
}

enum class BudgetStatus { ON_TRACK, WARNING, PROJECTED_OVER, OVER }

data class BudgetLine(
    val category: Category,
    val budgetId: Long?,
    val limit: Long,
    val spent: Long,
    val committed: Long,
    val remaining: Long,
    val projected: Long,
    /** (spent + committed) / limit in basis points. */
    val usedBp: Long,
    val status: BudgetStatus,
)

/** F04 Smart Budget Engine: spent / committed / remaining per category per pay cycle. */
object BudgetEngine {
    const val WARNING_BP = 8_000L

    fun line(
        category: Category,
        budgetId: Long?,
        limit: Long,
        spent: Long,
        committed: Long,
        projected: Long,
    ): BudgetLine {
        val used = spent + committed
        val usedBp = Money.ratioBp(used, limit)
        val status = when {
            limit <= 0 -> BudgetStatus.ON_TRACK
            used > limit -> BudgetStatus.OVER
            projected > limit -> BudgetStatus.PROJECTED_OVER
            usedBp >= WARNING_BP -> BudgetStatus.WARNING
            else -> BudgetStatus.ON_TRACK
        }
        return BudgetLine(category, budgetId, limit, spent, committed, limit - used, projected, usedBp, status)
    }
}

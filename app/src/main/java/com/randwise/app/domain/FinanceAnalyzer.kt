package com.randwise.app.domain

import com.randwise.app.data.model.Account
import com.randwise.app.data.model.AccountType
import com.randwise.app.data.model.Budget
import com.randwise.app.data.model.Bucket
import com.randwise.app.data.model.Category
import com.randwise.app.data.model.CategoryRule
import com.randwise.app.data.model.Debt
import com.randwise.app.data.model.Goal
import com.randwise.app.data.model.RecurringBill
import com.randwise.app.data.model.Transaction
import com.randwise.app.data.model.TxType
import com.randwise.app.data.model.UserProfile
import com.randwise.app.domain.engine.BehaviorAnalyzer
import com.randwise.app.domain.engine.BehaviorReport
import com.randwise.app.domain.engine.BillOccurrence
import com.randwise.app.domain.engine.BillSchedule
import com.randwise.app.domain.engine.BudgetEngine
import com.randwise.app.domain.engine.BudgetLine
import com.randwise.app.domain.engine.CashFlowForecaster
import com.randwise.app.domain.engine.EmergencyCoverage
import com.randwise.app.domain.engine.EmergencyEngine
import com.randwise.app.domain.engine.Forecast
import com.randwise.app.domain.engine.ForecastInputs
import com.randwise.app.domain.engine.RecurringBillDetector
import com.randwise.app.domain.engine.RecurringCandidate
import com.randwise.app.domain.engine.SafeToSpendEngine
import com.randwise.app.domain.engine.SafeToSpendResult
import com.randwise.app.domain.engine.VelocityEngine
import java.time.LocalDate

/** Everything stored on the device, as one immutable value. */
data class FinanceSnapshot(
    val profile: UserProfile?,
    val accounts: List<Account>,
    val categories: List<Category>,
    val rules: List<CategoryRule>,
    val transactions: List<Transaction>,
    val budgets: List<Budget>,
    val bills: List<RecurringBill>,
    val debts: List<Debt>,
    val goals: List<Goal>,
) {
    companion object {
        val EMPTY = FinanceSnapshot(null, emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
    }
}

data class AccountBalance(val account: Account, val balance: Long)

data class CategorySpend(val category: Category?, val amount: Long)

data class Analysis(
    val today: LocalDate,
    val cycle: PayCycle,
    val accountBalances: List<AccountBalance>,
    val liquidBalance: Long,
    val cycleIncome: Long,
    val cycleSpend: Long,
    val cycleVariableSpend: Long,
    val projectedCycleSpend: Long,
    val categorySpend: List<CategorySpend>,
    val occurrences: List<BillOccurrence>,
    val safeToSpend: SafeToSpendResult,
    val budgetLines: List<BudgetLine>,
    val forecastInputs: ForecastInputs,
    val forecast30: Forecast,
    val behavior: BehaviorReport,
    val recurringCandidates: List<RecurringCandidate>,
    val emergency: EmergencyCoverage,
    /** Cumulative spend per day in this cycle vs the previous cycle (for the trend chart). */
    val cumulativeThisCycle: List<Long>,
    val cumulativePrevCycle: List<Long>,
)

/** Runs every deterministic engine over a snapshot. The AI layer only ever sees this output. */
object FinanceAnalyzer {

    fun analyze(s: FinanceSnapshot, today: LocalDate): Analysis? {
        val profile = s.profile ?: return null
        val cycle = PayCycle.forDate(today, profile.payday, profile.adjustPaydayForWeekend)
        val txs = s.transactions
        val cycleTx = txs.filter { cycle.containsEpochDay(it.date) && it.date <= today.toEpochDay() }
        val catById = s.categories.associateBy { it.id }

        val balances = s.accounts.filter { !it.archived }.map { AccountBalance(it, Ledger.balance(it, txs)) }
        val liquid = Ledger.liquidBalance(s.accounts, txs)

        val occurrences = BillSchedule.occurrencesInCycle(s.bills, cycle, txs)

        // Planned savings still to move this cycle = plan − transfers already into savings accounts.
        val savingsIds = s.accounts.filter { it.type == AccountType.SAVINGS }.map { it.id }.toSet()
        val savedThisCycle = cycleTx.filter { it.type == TxType.TRANSFER_IN && it.accountId in savingsIds }.sumOf { it.amountMinor }
        val plannedRemaining = (profile.plannedSavingsMinor - savedThisCycle).coerceAtLeast(0)

        val sts = SafeToSpendEngine.evaluate(liquid, occurrences, plannedRemaining, profile.safetyBufferMinor, cycle.daysUntilPayday(today))

        val elapsed = cycle.elapsedDays(today)
        val remaining = cycle.remainingDays(today)
        val variableTx = cycleTx.filter { it.type.countsAsSpend && !BillSchedule.isBillPayment(it, s.bills) }
        val recentStart = today.minusDays(6).toEpochDay()

        fun projectFor(all: List<Transaction>, variable: List<Transaction>, upcoming: Long) =
            VelocityEngine.projectWeighted(
                currentSpent = Ledger.spend(all),
                variableSpent = Ledger.spend(variable),
                recentSevenDayVariable = Ledger.spend(variable.filter { it.date >= recentStart }),
                elapsedDays = elapsed,
                remainingDays = remaining,
                knownUpcomingBills = upcoming,
            )

        val cycleSpend = Ledger.spend(cycleTx)
        val projectedTotal = projectFor(cycleTx, variableTx, sts.unpaidBillsTotal)

        val byCat = Ledger.spendByCategory(cycleTx)
        val categorySpend = byCat.filter { it.value > 0 }
            .map { (id, amt) -> CategorySpend(id?.let { catById[it] }, amt) }
            .sortedByDescending { it.amount }

        val cycleBudgets = s.budgets.filter { it.periodStart == cycle.start.toEpochDay() }.associateBy { it.categoryId }
        val budgetLines = s.categories.filter { it.bucket != Bucket.INCOME }.mapNotNull { cat ->
            val b = cycleBudgets[cat.id] ?: return@mapNotNull null
            val catTx = cycleTx.filter { it.categoryId == cat.id }
            val committed = sts.unpaidBills.filter { it.bill.categoryId == cat.id }.sumOf { it.bill.expectedAmountMinor }
            BudgetEngine.line(
                category = cat,
                budgetId = b.id,
                limit = b.limitMinor,
                spent = Ledger.spend(catTx),
                committed = committed,
                projected = projectFor(catTx, variableTx.filter { it.categoryId == cat.id }, committed),
            )
        }

        // Daily velocity from the last 60 days of variable spend (fallback: this cycle so far).
        val histStart = today.minusDays(60).toEpochDay()
        val history = txs.filter { it.date in histStart..today.toEpochDay() && it.type.countsAsSpend && !BillSchedule.isBillPayment(it, s.bills) }
        val earliest = history.minOfOrNull { it.date }
        val (varTotal, varDays) = if (earliest != null && today.toEpochDay() - earliest >= 14) {
            Ledger.spend(history) to (today.toEpochDay() - histStart + 1)
        } else {
            Ledger.spend(variableTx) to elapsed
        }
        val events = CashFlowForecaster.scheduledEvents(today, 90, profile, s.bills, occurrences)
        val inputs = ForecastInputs(today, liquid, varTotal.coerceAtLeast(0), varDays, events, profile.safetyBufferMinor)
        val forecast30 = CashFlowForecaster.forecast(inputs, 30)

        val recurring = RecurringBillDetector.detect(txs, s.bills)
        val behavior = BehaviorAnalyzer.analyze(today, cycle, txs, s.bills, recurring)

        // Emergency coverage: essential spend averaged over the last 3 complete cycles, else confirmed bills.
        val essentialIds = s.categories.filter { it.isEssential || it.bucket == Bucket.FIXED }.map { it.id }.toSet()
        val pastCycles = generateSequence(cycle.previous()) { it.previous() }.take(3).toList()
        val essentialSpends = pastCycles.map { c -> Ledger.spend(txs.filter { c.containsEpochDay(it.date) && it.categoryId in essentialIds }) }
            .filter { it > 0 }
        val monthlyEssential = if (essentialSpends.isNotEmpty()) essentialSpends.sum() / essentialSpends.size
        else s.bills.filter { it.isConfirmed }.sumOf { it.expectedAmountMinor }
        val emergencySaved = s.goals.filter { it.isEmergencyFund }.sumOf { it.savedMinor }
        val emergency = EmergencyEngine.coverage(emergencySaved, monthlyEssential, profile.emergencyTargetMonths)

        val thisCum = cumulative(Ledger.dailySpend(txs, cycle.start.toEpochDay(), elapsed.toInt()))
        val prev = cycle.previous()
        val prevCum = cumulative(Ledger.dailySpend(txs, prev.start.toEpochDay(), prev.totalDays.toInt()))

        return Analysis(
            today = today,
            cycle = cycle,
            accountBalances = balances,
            liquidBalance = liquid,
            cycleIncome = Ledger.income(cycleTx),
            cycleSpend = cycleSpend,
            cycleVariableSpend = Ledger.spend(variableTx),
            projectedCycleSpend = projectedTotal,
            categorySpend = categorySpend,
            occurrences = occurrences,
            safeToSpend = sts,
            budgetLines = budgetLines,
            forecastInputs = inputs,
            forecast30 = forecast30,
            behavior = behavior,
            recurringCandidates = recurring,
            emergency = emergency,
            cumulativeThisCycle = thisCum,
            cumulativePrevCycle = prevCum,
        )
    }

    private fun cumulative(daily: LongArray): List<Long> {
        var run = 0L
        return daily.map { run += it; run }
    }
}

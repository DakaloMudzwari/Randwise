package com.randwise.app.domain.engine

import com.randwise.app.core.Money
import com.randwise.app.data.model.RecurringBill
import com.randwise.app.data.model.Transaction
import com.randwise.app.data.model.TxType
import com.randwise.app.domain.Ledger
import com.randwise.app.domain.MerchantText
import com.randwise.app.domain.PayCycle
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class RecurringCandidate(
    val pattern: String,
    val displayName: String,
    val medianAmount: Long,
    val dueDay: Int,
    val occurrences: Int,
    val firstAmount: Long,
    val lastAmount: Long,
    val categoryId: Long?,
) {
    /** Price change since first seen, basis points. */
    val changeBp: Long get() = Money.ratioBp(lastAmount - firstAmount, firstAmount)
}

/** Recurring Bill Detective (Phase 3): finds monthly debit orders and subscriptions. */
object RecurringBillDetector {

    fun detect(transactions: List<Transaction>, existing: List<RecurringBill>, minOccurrences: Int = 2): List<RecurringCandidate> {
        val known = existing.map { it.merchantPattern.uppercase() }.toSet()
        return transactions.asSequence()
            .filter { it.type == TxType.EXPENSE }
            .groupBy { MerchantText.normalize(it.merchant) }
            .filter { (key, _) -> key.isNotBlank() && key !in known }
            .mapNotNull { (key, list) -> analyse(key, list.sortedBy { it.date }, minOccurrences) }
            .sortedByDescending { it.medianAmount }
    }

    private fun analyse(key: String, txs: List<Transaction>, minOccurrences: Int): RecurringCandidate? {
        // One payment per month only — several coffees a month are not a subscription.
        val perMonth = txs.groupBy { LocalDate.ofEpochDay(it.date).let { d -> d.year * 12 + d.monthValue } }
        if (perMonth.values.any { it.size > 1 } || perMonth.size < minOccurrences) return null
        val intervals = txs.zipWithNext { a, b -> b.date - a.date }
        if (intervals.isEmpty() || intervals.any { it !in 25..38 }) return null
        val amounts = txs.map { it.amountMinor }.sorted()
        val median = amounts[amounts.size / 2]
        // Amounts must stay within 25% of the median (allows price increases to be detected as creep).
        if (amounts.any { kotlin.math.abs(it - median) * 4 > median }) return null
        val days = txs.map { LocalDate.ofEpochDay(it.date).dayOfMonth }.sorted()
        return RecurringCandidate(
            pattern = key,
            displayName = key.split(' ').joinToString(" ") { w -> w.lowercase().replaceFirstChar { it.uppercase() } },
            medianAmount = median,
            dueDay = days[days.size / 2],
            occurrences = txs.size,
            firstAmount = txs.first().amountMinor,
            lastAmount = txs.last().amountMinor,
            categoryId = txs.last().categoryId,
        )
    }
}

enum class Severity { INFO, POSITIVE, WARNING, CRITICAL }

data class Insight(val severity: Severity, val title: String, val body: String)

data class BehaviorReport(
    /** Spend per day in the first 3 days after payday vs the rest of the cycle, basis points. */
    val postPaydaySpikeBp: Long,
    val postPaydaySpike: Boolean,
    val feeCount: Int,
    val feeTotal: Long,
    /** 0 (steady) .. 100 (erratic) from the coefficient of variation of the last 30 days. */
    val volatilityScore: Int,
    val lifestyleInflationBp: Long,
    val subscriptionCreep: List<Pair<String, Long>>,
    val insights: List<Insight>,
)

/** F05 Behavioural Analytics. Statistics use integer math throughout. */
object BehaviorAnalyzer {

    fun analyze(
        today: LocalDate,
        cycle: PayCycle,
        transactions: List<Transaction>,
        bills: List<RecurringBill>,
        recurring: List<RecurringCandidate>,
    ): BehaviorReport {
        val variable = transactions.filter { it.type.countsAsSpend && !BillSchedule.isBillPayment(it, bills) }
        val insights = mutableListOf<Insight>()

        // Post-payday spike, measured on the last complete cycle.
        val prev = cycle.previous()
        val spikeBp = postPaydayRatio(variable, prev)
        val spike = spikeBp >= 20_000
        if (spike) {
            insights += Insight(
                Severity.WARNING, "Payday splurge pattern",
                "In the 3 days after your last payday you spent ${Money.formatPercentBp(spikeBp - 10_000)} more per day than the rest of the cycle.",
            )
        }

        // Fee traps over the last 90 days.
        val since = today.minusDays(90).toEpochDay()
        val fees = transactions.filter { it.type == TxType.EXPENSE && it.date >= since && MerchantText.isFeeTrap(it.merchant) }
        if (fees.isNotEmpty()) {
            insights += Insight(
                Severity.CRITICAL, "Fee traps detected",
                "${fees.size} bank penalty/overdraft fee(s) cost you ${Money.format(fees.sumOf { it.amountMinor })} in 90 days.",
            )
        }

        // 30-day volatility.
        val start = today.minusDays(29).toEpochDay()
        val daily = Ledger.dailySpend(variable, start, 30)
        val volatility = volatilityScore(daily)
        if (volatility >= 65) {
            insights += Insight(Severity.WARNING, "Erratic spending", "Your daily spend swings a lot (volatility $volatility/100). Steadier days make budgets easier to hit.")
        } else if (daily.sum() > 0 && volatility < 35) {
            insights += Insight(Severity.POSITIVE, "Steady spender", "Your daily spending has been consistent over the last 30 days (volatility $volatility/100).")
        }

        // Lifestyle inflation: last complete cycle vs average of the three before it.
        val prevSpend = Ledger.spend(variable.filter { prev.containsEpochDay(it.date) })
        val older = generateSequence(prev.previous()) { it.previous() }.take(3).toList()
        val olderSpends = older.map { c -> Ledger.spend(variable.filter { c.containsEpochDay(it.date) }) }.filter { it > 0 }
        val inflationBp = if (olderSpends.size >= 2) {
            val avg = olderSpends.sum() / olderSpends.size
            Money.ratioBp(prevSpend - avg, avg)
        } else 0L
        if (inflationBp >= 1_000) {
            insights += Insight(
                Severity.WARNING, "Lifestyle creep",
                "Day-to-day spending last cycle was ${Money.formatPercentBp(inflationBp)} higher than your previous 3-cycle average.",
            )
        }

        // Subscription creep: recurring payments whose price has climbed more than 5%.
        val knownBills = bills.map { it.merchantPattern.uppercase() }
        val allRecurring = recurring + RecurringBillDetector.detect(transactions.filter { t ->
            knownBills.any { MerchantText.matches(t.merchant, it) }
        }, emptyList())
        val creep = allRecurring.filter { it.changeBp > 500 }.map { it.displayName to it.changeBp }.distinctBy { it.first }
        creep.firstOrNull()?.let { (name, bp) ->
            insights += Insight(Severity.WARNING, "Subscription price creep", "$name has gone up ${Money.formatPercentBp(bp)} since you started paying it.")
        }
        val unconfirmed = recurring.size
        if (unconfirmed > 0) {
            insights += Insight(Severity.INFO, "Possible debit orders found", "$unconfirmed recurring payment(s) look like bills. Confirm them in Budget so safe-to-spend accounts for them.")
        }

        return BehaviorReport(spikeBp, spike, fees.size, fees.sumOf { it.amountMinor }, volatility, inflationBp, creep, insights)
    }

    fun postPaydayRatio(variable: List<Transaction>, cycle: PayCycle): Long {
        val total = cycle.totalDays.toInt()
        if (total <= 6) return 0
        val daily = Ledger.dailySpend(variable, cycle.start.toEpochDay(), total)
        val first = daily.take(3).sum()
        val rest = daily.drop(3).sum()
        if (rest <= 0 || first <= 0) return 0
        // (first/3) / (rest/(total-3)) in bp
        return Money.divRound(first * (total - 3) * 10_000L, rest * 3)
    }

    fun volatilityScore(daily: LongArray): Int {
        val n = daily.size
        if (n == 0) return 0
        val sum = daily.sum()
        if (sum <= 0) return 0
        val mean = sum / n
        if (mean == 0L) return 100
        var sq = 0L
        for (v in daily) { val d = v - mean; sq += d * d }
        val std = isqrt(sq / n)
        val cvBp = Money.ratioBp(std, mean)
        // CV of 1.5 (15 000 bp) or more maps to 100.
        return (cvBp / 150).coerceIn(0, 100).toInt()
    }

    fun isqrt(v: Long): Long {
        if (v <= 0) return 0
        var x = Math.sqrt(v.toDouble()).toLong()
        while (x * x > v) x--
        while ((x + 1) * (x + 1) <= v) x++
        return x
    }

    fun daysBetween(a: LocalDate, b: LocalDate) = ChronoUnit.DAYS.between(a, b)
}

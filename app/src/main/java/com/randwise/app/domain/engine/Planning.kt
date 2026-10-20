package com.randwise.app.domain.engine

import com.randwise.app.core.Money
import com.randwise.app.data.model.Debt

enum class DebtStrategy { SNOWBALL, AVALANCHE }

data class DebtPayoff(val debtId: Long, val name: String, val payoffMonth: Int, val interestPaid: Long)

data class DebtPlan(
    val strategy: DebtStrategy,
    val feasible: Boolean,
    val months: Int,
    val totalInterest: Long,
    val totalPaid: Long,
    val payoffs: List<DebtPayoff>,
)

/**
 * F08 Debt planner. Each month: accrue interest (APR/12, integer basis points), pay every minimum,
 * then throw everything left of the monthly budget at the target debt. Freed-up minimums roll into
 * the next target automatically because the budget stays constant.
 *  - Snowball: smallest balance first.  - Avalanche: highest APR first.
 */
object DebtPlanner {
    private const val MAX_MONTHS = 600

    fun plan(debts: List<Debt>, monthlyBudget: Long, strategy: DebtStrategy): DebtPlan {
        val active = debts.filter { it.balanceMinor > 0 }
        if (active.isEmpty()) return DebtPlan(strategy, true, 0, 0, 0, emptyList())
        val order = when (strategy) {
            DebtStrategy.SNOWBALL -> active.sortedWith(compareBy<Debt> { it.balanceMinor }.thenByDescending { it.aprBp })
            DebtStrategy.AVALANCHE -> active.sortedWith(compareByDescending<Debt> { it.aprBp }.thenBy { it.balanceMinor })
        }
        val balance = order.associate { it.id to it.balanceMinor }.toMutableMap()
        val interest = order.associate { it.id to 0L }.toMutableMap()
        val payoffMonth = mutableMapOf<Long, Int>()
        var totalPaid = 0L
        var month = 0
        val minimumsTotal = order.sumOf { it.minPaymentMinor }
        val budget = maxOf(monthlyBudget, minimumsTotal)

        while (balance.values.any { it > 0 } && month < MAX_MONTHS) {
            month++
            for (d in order) {
                val b = balance.getValue(d.id)
                if (b <= 0) continue
                val i = Money.divRound(b * d.aprBp, 12 * 10_000L)
                balance[d.id] = b + i
                interest[d.id] = interest.getValue(d.id) + i
            }
            var available = budget
            for (d in order) {
                val b = balance.getValue(d.id)
                if (b <= 0) continue
                val pay = minOf(d.minPaymentMinor, b, available)
                balance[d.id] = b - pay
                available -= pay
                totalPaid += pay
            }
            for (d in order) {
                if (available <= 0) break
                val b = balance.getValue(d.id)
                if (b <= 0) continue
                val pay = minOf(b, available)
                balance[d.id] = b - pay
                available -= pay
                totalPaid += pay
            }
            for (d in order) if (balance.getValue(d.id) <= 0 && d.id !in payoffMonth) payoffMonth[d.id] = month
        }
        val feasible = balance.values.all { it <= 0 }
        val payoffs = order.map { DebtPayoff(it.id, it.name, payoffMonth[it.id] ?: -1, interest.getValue(it.id)) }
            .sortedBy { if (it.payoffMonth < 0) Int.MAX_VALUE else it.payoffMonth }
        return DebtPlan(strategy, feasible, if (feasible) month else -1, interest.values.sum(), totalPaid, payoffs)
    }
}

data class EmergencyCoverage(
    val saved: Long,
    val monthlyEssential: Long,
    /** Months of essential spend covered, ×100 (e.g. 250 = 2.50 months). */
    val monthsX100: Long,
    val targetMonths: Int,
    val targetAmount: Long,
)

object EmergencyEngine {
    fun coverage(saved: Long, monthlyEssential: Long, targetMonths: Int): EmergencyCoverage {
        val monthsX100 = if (monthlyEssential <= 0) 0 else Money.divRound(saved * 100, monthlyEssential)
        return EmergencyCoverage(saved, monthlyEssential, monthsX100, targetMonths, monthlyEssential * targetMonths)
    }
}

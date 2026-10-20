package com.randwise.app.ai

import com.randwise.app.core.Money
import com.randwise.app.domain.Analysis
import com.randwise.app.domain.engine.AffordabilityEngine
import com.randwise.app.domain.engine.BudgetStatus
import com.randwise.app.domain.engine.Verdict

/**
 * Offline, deterministic coach used when AI consent is off, no gateway is configured or the
 * device is offline. Answers are assembled purely from engine facts.
 */
object LocalCoach {

    const val DISCLAIMER = "General guidance only, not financial advice."

    fun answer(question: String, a: Analysis): CoachResponse {
        val q = question.lowercase()
        val sts = a.safeToSpend
        val amount = Regex("r?\\s?(\\d[\\d ,]*(?:[.,]\\d{1,2})?)").findAll(q)
            .mapNotNull { Money.parse(it.groupValues[1]) }.firstOrNull { it >= 100 }

        val text = when {
            ("afford" in q || "buy" in q || "can i" in q) && amount != null -> {
                val r = AffordabilityEngine.check(amount, "month" in q, a.forecastInputs, sts.safeToSpend, a.cycle.nextPayday, sts.unpaidBillsTotal, sts.unpaidBills.size)
                val head = when (r.verdict) {
                    Verdict.AFFORDABLE -> "Yes — ${Money.format(amount)} fits."
                    Verdict.TIGHT -> "Possible, but tight."
                    Verdict.NOT_AFFORDABLE -> "Not right now."
                }
                (listOf(head) + r.explanations).joinToString("\n• ", postfix = "")
            }
            "safe" in q || "spend" in q && "today" in q || "left" in q -> buildString {
                append("You can safely spend ${Money.format(sts.safeToSpend)} until payday (${sts.daysUntilPayday} days), ")
                append("about ${Money.format(sts.perDay)} per day.\n")
                append("That's your balance of ${Money.format(sts.balance)} minus ${Money.format(sts.unpaidBillsTotal)} in unpaid bills, ")
                append("${Money.format(sts.plannedSavingsRemaining)} planned savings and your ${Money.format(sts.safetyBuffer)} buffer.")
            }
            "bill" in q || "debit" in q -> {
                val unpaid = sts.unpaidBills
                if (unpaid.isEmpty()) "All confirmed bills for this cycle are paid."
                else "Still due before payday:\n" + unpaid.joinToString("\n") { "• ${it.bill.name}: ${Money.format(it.bill.expectedAmountMinor)} on ${it.dueDate}" }
            }
            "budget" in q || "over" in q || "categor" in q -> {
                val risky = a.budgetLines.filter { it.status != BudgetStatus.ON_TRACK }
                if (a.budgetLines.isEmpty()) "You haven't set budgets yet. Add limits in the Budget tab and I'll track them."
                else if (risky.isEmpty()) "Every budget is on track this cycle. Nice work."
                else "Budgets to watch:\n" + risky.joinToString("\n") {
                    "• ${it.category.name}: ${Money.format(it.spent)} of ${Money.format(it.limit)} spent, projected ${Money.format(it.projected)} (${it.status.name.lowercase().replace('_', ' ')})"
                }
            }
            "save" in q || "emergency" in q -> {
                val e = a.emergency
                "Your emergency fund covers ${e.monthsX100 / 100}.${(e.monthsX100 % 100).toString().padStart(2, '0')} months of essential spend " +
                    "(target ${e.targetMonths} months = ${Money.format(e.targetAmount)}). " +
                    if (sts.safeToSpend > 0) "Moving even ${Money.format(sts.safeToSpend / 4)} of today's safe-to-spend into savings would help." else "Focus on getting back above your buffer first."
            }
            "forecast" in q || "month end" in q || "payday" in q -> {
                val f = a.forecast30
                "Projected lowest balance in the next 30 days: ${Money.format(f.lowest.balance)} on ${f.lowest.date}." +
                    (f.firstNegative?.let { " Warning: projected to go negative on $it." } ?: "") +
                    " This cycle you're on track to spend ${Money.format(a.projectedCycleSpend)}."
            }
            else -> {
                val top = a.behavior.insights.take(3)
                if (top.isEmpty()) "Your finances look steady. Safe-to-spend is ${Money.format(sts.safeToSpend)} with ${sts.daysUntilPayday} days to payday. " +
                    "Ask me things like \"Can I afford R1500?\", \"Which budgets are at risk?\" or \"What bills are left?\""
                else "Here's what stands out:\n" + top.joinToString("\n") { "• ${it.title}: ${it.body}" }
            }
        }
        return CoachResponse(text, emptyList(), DISCLAIMER)
    }
}

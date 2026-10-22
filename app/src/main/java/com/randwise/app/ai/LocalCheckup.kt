package com.randwise.app.ai

import com.randwise.app.core.Money
import com.randwise.app.domain.engine.BehaviorReport
import com.randwise.app.domain.engine.PlanLine
import com.randwise.app.domain.engine.SpendingProfile

/**
 * On-device money check-up, used when AI interpretation is off or the gateway can't be reached.
 * Every sentence is built from the spending profile, so it is always accurate — just less personal.
 */
object LocalCheckup {

    fun headline(p: SpendingProfile): String {
        val left = p.monthlyIncome - p.typicalSpend
        return if (left < 0) "You're spending more than you earn" else "You keep about ${Money.formatWhole(left)} a month"
    }

    fun summary(p: SpendingProfile): String {
        val top = p.categories.take(3).joinToString(", ") { it.category.name }
        val period = if (p.monthsCovered == 1) "your last month" else "your last ${p.monthsCovered} months"
        return "A typical month costs ${Money.formatWhole(p.typicalSpend)} against ${Money.formatWhole(p.monthlyIncome)} income, based on $period." +
            if (top.isNotEmpty()) " Most of it goes to $top." else ""
    }

    fun insights(p: SpendingProfile, behavior: BehaviorReport, lines: List<PlanLine>): List<CheckupNote> = buildList {
        // A warning, so it leads the insights rather than sitting among the (positive) suggestions.
        if (p.availableForBudget > 0 && lines.sumOf { it.limit } > p.availableForBudget) {
            add(CheckupNote("Even a trimmed budget is more than you earn", "After savings, your essentials and minimum spending still exceed income. Look at your largest fixed costs first."))
        }
        p.categories.firstOrNull()?.let { t ->
            val share = Money.ratioBp(t.typical, p.typicalSpend) / 100
            add(CheckupNote("${t.category.name} is your biggest cost", "${Money.formatWhole(t.typical)} in a typical month — $share% of your spending."))
        }
        if (p.monthsCovered >= 2) {
            p.categories.firstOrNull { t ->
                val recent = t.months.takeLast(p.monthsCovered)
                val first = recent.first()
                val last = recent.last()
                first > 0 && last - first >= 30_000 && last * 4 >= first * 5
            }?.let { t ->
                val recent = t.months.takeLast(p.monthsCovered)
                add(CheckupNote("${t.category.name} is climbing", "From ${Money.formatWhole(recent.first())} to ${Money.formatWhole(recent.last())} a month over ${p.monthsCovered} months."))
            }
        }
        p.merchants.maxByOrNull { it.visits }?.takeIf { it.visits >= 4 }?.let { m ->
            add(CheckupNote("Your most frequent spend: ${m.label}", "${m.visits} payments adding up to ${Money.formatWhole(m.total)} in the last ${p.daysOfData.coerceAtMost(90)} days."))
        }
        if (behavior.feeCount > 0) {
            add(CheckupNote("Bank fees are leaking money", "${behavior.feeCount} penalty or admin fees cost you ${Money.formatWhole(behavior.feeTotal)}. Check debit order dates against payday."))
        }
        if (p.uncategorisedTypical * 10 > p.typicalSpend && p.uncategorisedTypical > 0) {
            add(CheckupNote("Some spending has no category", "${Money.formatWhole(p.uncategorisedTypical)} a month isn't categorised, so it can't be budgeted. Tap transactions in your ledger to sort them."))
        }
    }

    fun suggestions(p: SpendingProfile, lines: List<PlanLine>): List<CheckupNote> = buildList {
        lines.filter { !it.essential && it.typical > it.limit }
            .sortedByDescending { it.typical - it.limit }
            .take(3)
            .forEach { l ->
                add(CheckupNote("Cap ${l.category} at ${Money.formatWhole(l.limit)}", "Your typical month is ${Money.formatWhole(l.typical)} — this frees ${Money.formatWhole(l.typical - l.limit)} a month."))
            }
        if (p.plannedSavingsMonthly == 0L) {
            add(CheckupNote("Pay yourself first", "Set a savings amount in your financial profile so it's protected before you spend — even R200 a month builds a cushion."))
        }
    }

    /** Builds the saved check-up from the profile and an (already guard-railed) plan. */
    fun assemble(
        p: SpendingProfile,
        lines: List<PlanLine>,
        lastTransactionId: Long,
        source: String,
        note: String?,
        headline: String,
        summary: String,
        insights: List<CheckupNote>,
        suggestions: List<CheckupNote>,
    ) = Checkup(
        createdAt = System.currentTimeMillis(),
        source = source,
        note = note,
        lastTransactionId = lastTransactionId,
        daysOfData = p.daysOfData,
        monthsCovered = p.monthsCovered,
        monthlyIncomeMinor = p.monthlyIncome,
        typicalSpendMinor = p.typicalSpend,
        availableMinor = p.availableForBudget,
        headline = headline,
        summary = summary,
        insights = insights,
        suggestions = suggestions,
        categories = p.categories.take(10).map {
            CheckupCategory(it.category.id, it.category.name, it.category.colorArgb, it.essential, it.months, it.typical)
        },
        merchants = p.merchants.take(5).map { CheckupMerchant(it.label, it.total, it.visits) },
        budget = lines.map { CheckupLine(it.categoryId, it.category, it.essential, it.typical, it.limit, it.reason) },
    )
}

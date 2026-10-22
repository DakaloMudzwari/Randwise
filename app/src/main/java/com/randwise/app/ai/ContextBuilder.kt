package com.randwise.app.ai

import com.randwise.app.core.Money
import com.randwise.app.domain.Analysis
import com.randwise.app.domain.FinanceSnapshot
import com.randwise.app.domain.MerchantText
import com.randwise.app.domain.engine.BehaviorReport
import com.randwise.app.domain.engine.PlanLine
import com.randwise.app.domain.engine.SpendingProfile
import java.time.temporal.ChronoUnit

/**
 * Anonymised Context Builder (FDS §5). Converts engine output into facts with no PII:
 * no account names or numbers, no raw statement descriptions, no free-text notes.
 */
object ContextBuilder {

    fun build(snapshot: FinanceSnapshot, a: Analysis): CoachFacts {
        val profile = requireNotNull(snapshot.profile)
        val catNames = snapshot.categories.associate { it.id to it.name }
        val budgetByCat = a.budgetLines.associateBy { it.category.id }
        val categories = a.categorySpend.take(12).map { cs ->
            val line = cs.category?.let { budgetByCat[it.id] }
            CategoryFact(
                category = cs.category?.name ?: "Uncategorised",
                spentMinor = cs.amount,
                limitMinor = line?.limit,
                projectedMinor = line?.projected,
                status = line?.status?.name,
            )
        } + a.budgetLines.filter { l -> a.categorySpend.none { it.category?.id == l.category.id } }
            .map { CategoryFact(it.category.name, 0, it.limit, it.projected, it.status.name) }

        val bills = a.occurrences.map { occ ->
            BillFact(
                label = occ.bill.categoryId?.let { catNames[it] } ?: MerchantText.anonymizedLabel(occ.bill.name),
                amountMinor = occ.bill.expectedAmountMinor,
                dueInDays = ChronoUnit.DAYS.between(a.today, occ.dueDate),
                paid = occ.paid,
            )
        }
        val debts = snapshot.debts.mapIndexed { i, d ->
            DebtFact("Debt ${i + 1}", d.balanceMinor, Money.formatPercentBp(d.aprBp.toLong()), d.minimumFor(d.balanceMinor))
        }
        val f = a.forecast30
        return CoachFacts(
            currency = profile.currency,
            today = a.today.toString(),
            daysUntilPayday = a.safeToSpend.daysUntilPayday,
            liquidBalanceMinor = a.liquidBalance,
            safeToSpendMinor = a.safeToSpend.safeToSpend,
            safeToSpendPerDayMinor = a.safeToSpend.perDay,
            safetyBufferMinor = profile.safetyBufferMinor,
            monthlyIncomeMinor = profile.netPayMinor,
            cycleSpendMinor = a.cycleSpend,
            projectedCycleSpendMinor = a.projectedCycleSpend,
            categories = categories,
            bills = bills,
            forecastLowestMinor = f.lowest.balance,
            forecastLowestInDays = ChronoUnit.DAYS.between(a.today, f.lowest.date),
            forecastNegativeInDays = f.firstNegative?.let { ChronoUnit.DAYS.between(a.today, it) },
            volatilityScore = a.behavior.volatilityScore,
            postPaydaySpikePercent = Money.formatPercentBp(a.behavior.postPaydaySpikeBp),
            feeTrapCount = a.behavior.feeCount,
            feeTrapTotalMinor = a.behavior.feeTotal,
            emergencyMonthsCovered = "${a.emergency.monthsX100 / 100}.${(a.emergency.monthsX100 % 100).toString().padStart(2, '0')}",
            emergencyTargetMonths = a.emergency.targetMonths,
            debts = debts,
            detectedInsights = a.behavior.insights.map { "${it.title}: ${it.body}" },
        )
    }

    /** Check-up facts: three months of category and merchant totals (merchant first word only), plus the baseline plan. */
    fun checkup(p: SpendingProfile, baseline: List<PlanLine>, behavior: BehaviorReport, currency: String): CheckupFacts {
        val planByCat = baseline.associateBy { it.categoryId }
        return CheckupFacts(
            currency = currency,
            today = p.today.toString(),
            monthsCovered = p.monthsCovered,
            daysOfData = p.daysOfData,
            monthlyIncomeMinor = p.monthlyIncome,
            plannedSavingsMinor = p.plannedSavingsMonthly,
            availableForBudgetMinor = p.availableForBudget,
            monthlySpendMinor = p.monthlySpend,
            typicalSpendMinor = p.typicalSpend,
            uncategorisedTypicalMinor = p.uncategorisedTypical,
            categories = p.categories.take(15).map { t ->
                val plan = planByCat[t.category.id]
                CategoryTrendFact(
                    category = t.category.name,
                    essential = t.essential,
                    monthsMinor = t.months,
                    typicalMinor = t.typical,
                    suggestedLimitMinor = plan?.limit,
                    suggestedSavingMinor = plan?.let { (t.typical - it.limit).coerceAtLeast(0) },
                )
            },
            merchants = p.merchants.map { MerchantFact(it.label, it.total, it.visits, it.categoryName) },
            feeCount = behavior.feeCount,
            feeTotalMinor = behavior.feeTotal,
            detectedInsights = behavior.insights.map { "${it.title}: ${it.body}" },
        )
    }

    private val longDigits = Regex("\\b\\d[\\d -]{7,}\\d\\b")
    private val email = Regex("[\\w.+-]+@[\\w-]+\\.[\\w.]+")

    /** Strips anything that looks like an account/ID/card number or e-mail from the user's question. */
    fun sanitizeQuestion(q: String): String =
        q.replace(email, "[email]").replace(longDigits, "[number]").take(800)
}

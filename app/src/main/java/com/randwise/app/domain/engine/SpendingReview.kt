package com.randwise.app.domain.engine

import com.randwise.app.core.Money
import com.randwise.app.data.model.Bucket
import com.randwise.app.data.model.Category
import com.randwise.app.data.model.PayFrequency
import com.randwise.app.data.model.TxType
import com.randwise.app.domain.FinanceSnapshot
import com.randwise.app.domain.Ledger
import com.randwise.app.domain.MerchantText
import java.time.LocalDate

/** One category's net spend in each 30-day month of the review window, oldest first. */
data class CategoryTrend(val category: Category, val months: List<Long>, val typical: Long) {
    val essential: Boolean get() = category.isEssential || category.bucket == Bucket.FIXED || category.bucket == Bucket.DEBT
}

data class MerchantSpend(val label: String, val total: Long, val visits: Int, val categoryName: String?)

/**
 * Three months of behaviour in monthly terms — the input to the money check-up. "Typical" is the
 * median month (the average when there are fewer than three), so one unusual month doesn't skew it.
 */
data class SpendingProfile(
    val today: LocalDate,
    val daysOfData: Int,
    val monthsCovered: Int,
    val monthlySpend: List<Long>,
    val typicalSpend: Long,
    val categories: List<CategoryTrend>,
    val uncategorisedTypical: Long,
    val merchants: List<MerchantSpend>,
    /** Net pay converted to a monthly figure, whatever the pay frequency. */
    val monthlyIncome: Long,
    val plannedSavingsMonthly: Long,
) {
    /** What a monthly budget can share out: income after planned savings. */
    val availableForBudget: Long get() = (monthlyIncome - plannedSavingsMonthly).coerceAtLeast(0)
}

/** A budget line in monthly terms; converted to the pay cycle when the user accepts it. */
data class PlanLine(
    val categoryId: Long,
    val category: String,
    val essential: Boolean,
    val typical: Long,
    val limit: Long,
    val reason: String,
)

/** Something proposed by the AI for one category, before guard-rails are applied. */
data class ProposedLine(val category: String, val limit: Long, val reason: String)

object SpendingReview {
    const val MONTHS = 3
    const val MONTH_DAYS = 30

    /** Fewer days than this and a "typical month" would be a guess. */
    const val MIN_DAYS = 14

    fun profile(s: FinanceSnapshot, today: LocalDate): SpendingProfile? {
        val p = s.profile ?: return null
        val end = today.toEpochDay()
        val start = end - MONTHS * MONTH_DAYS + 1
        val window = s.transactions.filter { it.date in start..end }
        val earliest = window.minOfOrNull { it.date } ?: return null
        val daysOfData = (end - earliest + 1).toInt()
        val covered = ((daysOfData + MONTH_DAYS - 1) / MONTH_DAYS).coerceIn(1, MONTHS)

        // The oldest covered month may be partial (history starts mid-month); scale it to a full month.
        val firstCovered = MONTHS - covered
        val firstDays = (start + (firstCovered + 1) * MONTH_DAYS - earliest).coerceIn(1, MONTH_DAYS.toLong())
        val months = (0 until MONTHS).map { k -> Ledger.inRange(window, start + k * MONTH_DAYS, start + (k + 1) * MONTH_DAYS) }
        fun normalise(values: List<Long>) = values.mapIndexed { k, v ->
            when {
                k < firstCovered -> 0L
                k == firstCovered && firstDays < MONTH_DAYS -> Money.divRound(v * MONTH_DAYS, firstDays)
                else -> v
            }
        }
        fun typical(values: List<Long>) = typicalOf(values.drop(firstCovered))

        val monthlySpend = normalise(months.map { Ledger.spend(it) })
        val perMonthByCat = months.map { Ledger.spendByCategory(it) }
        val catById = s.categories.associateBy { it.id }
        val categories = perMonthByCat.flatMap { it.keys }.toSet().mapNotNull { id ->
            val cat = id?.let { catById[it] } ?: return@mapNotNull null
            if (cat.bucket == Bucket.INCOME) return@mapNotNull null
            val values = normalise(perMonthByCat.map { it[id] ?: 0L })
            CategoryTrend(cat, values, typical(values)).takeIf { it.typical > 0 }
        }.sortedByDescending { it.typical }

        val merchants = window.filter { it.type == TxType.EXPENSE }
            .groupBy { MerchantText.anonymizedLabel(it.merchant) }
            .filterKeys { it.isNotBlank() }
            .map { (label, list) ->
                val topCategory = list.groupingBy { it.categoryId }.eachCount().maxByOrNull { it.value }?.key
                MerchantSpend(label, list.sumOf { it.amountMinor }, list.size, topCategory?.let { catById[it]?.name })
            }
            .sortedByDescending { it.total }
            .take(8)

        return SpendingProfile(
            today = today,
            daysOfData = daysOfData,
            monthsCovered = covered,
            monthlySpend = monthlySpend,
            typicalSpend = typical(monthlySpend),
            categories = categories,
            uncategorisedTypical = typical(normalise(perMonthByCat.map { it[null] ?: 0L })).coerceAtLeast(0),
            merchants = merchants,
            monthlyIncome = monthlyFromCycle(p.netPayMinor, p.payFrequency),
            plannedSavingsMonthly = monthlyFromCycle(p.plannedSavingsMinor, p.payFrequency),
        )
    }

    /** Median of three months; with less history, the plain average. */
    fun typicalOf(values: List<Long>): Long = when {
        values.isEmpty() -> 0
        values.size >= 3 -> values.sorted()[values.size / 2]
        else -> Money.divRound(values.sum(), values.size.toLong())
    }

    fun monthlyFromCycle(amount: Long, f: PayFrequency): Long = when (f) {
        PayFrequency.MONTHLY -> amount
        PayFrequency.FORTNIGHTLY -> Money.divRound(amount * 26, 12)
        PayFrequency.WEEKLY -> Money.divRound(amount * 52, 12)
    }

    fun cycleFromMonthly(amount: Long, f: PayFrequency): Long = when (f) {
        PayFrequency.MONTHLY -> amount
        PayFrequency.FORTNIGHTLY -> Money.divRound(amount * 12, 26)
        PayFrequency.WEEKLY -> Money.divRound(amount * 12, 52)
    }
}

/**
 * Budget recommendation logic. The baseline is deterministic; an AI proposal is only accepted within
 * guard-rails (known categories, 50–120% of the typical month, total within income after savings), so
 * the model can tailor the plan but never produce one the numbers don't support.
 */
object BudgetPlanner {
    /** Limits are rounded to R50 — easier to remember and to stick to. */
    const val STEP = 5_000L

    /** Categories smaller than this aren't worth a budget line. */
    const val MIN_TYPICAL = 5_000L

    private const val TRIM_BP = 9_000L
    private const val FLOOR_BP = 5_000L
    private const val CEILING_BP = 12_000L

    /** Bank fees are leaks to stop (the check-up flags them), not spending to budget for. */
    private val feeCategory = Regex("\\bfees?\\b", RegexOption.IGNORE_CASE)

    fun baseline(p: SpendingProfile): List<PlanLine> {
        val lines = p.categories
            .filter { it.typical >= MIN_TYPICAL && it.category.bucket != Bucket.SAVINGS && !feeCategory.containsMatchIn(it.category.name) }
            .map { t ->
                if (t.essential) {
                    PlanLine(t.category.id, t.category.name, true, t.typical, roundUp(t.typical), "Essential — set at your typical month.")
                } else {
                    PlanLine(t.category.id, t.category.name, false, t.typical, roundUp(Money.applyBasisPoints(t.typical, TRIM_BP)), "About 10% under your typical month.")
                }
            }
        return fit(lines, p.availableForBudget)
    }

    fun accept(baseline: List<PlanLine>, proposal: List<ProposedLine>, available: Long): List<PlanLine> {
        val byName = proposal.associateBy { it.category.trim().lowercase() }
        val lines = baseline.map { b ->
            val p = byName[b.category.lowercase()] ?: return@map b
            b.copy(limit = roundUp(p.limit).coerceIn(floor(b.typical), roundUp(Money.applyBasisPoints(b.typical, CEILING_BP))), reason = p.reason.trim().take(160).ifEmpty { b.reason })
        }
        return fit(lines, available)
    }

    /** Over income? Trim non-essential lines proportionally, never below half their typical month. */
    fun fit(lines: List<PlanLine>, available: Long): List<PlanLine> {
        val total = lines.sumOf { it.limit }
        if (available <= 0 || total <= available) return lines
        val essential = lines.filter { it.essential }.sumOf { it.limit }
        val flexible = total - essential
        if (flexible <= 0) return lines
        val room = (available - essential).coerceAtLeast(0)
        return lines.map { l ->
            if (l.essential) return@map l
            val trimmed = maxOf(floor(l.typical), roundDown(l.limit * room / flexible))
            if (trimmed < l.limit) l.copy(limit = trimmed, reason = "Trimmed so the budget fits your income.") else l
        }
    }

    private fun floor(typical: Long) = roundDown(Money.applyBasisPoints(typical, FLOOR_BP)).coerceAtLeast(STEP)
    private fun roundUp(v: Long) = ((v + STEP - 1) / STEP) * STEP
    private fun roundDown(v: Long) = (v / STEP) * STEP
}

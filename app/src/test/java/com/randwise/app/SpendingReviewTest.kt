package com.randwise.app

import com.randwise.app.data.model.Bucket
import com.randwise.app.data.model.Category
import com.randwise.app.data.model.PayFrequency
import com.randwise.app.data.model.Transaction
import com.randwise.app.data.model.TxType
import com.randwise.app.data.model.UserProfile
import com.randwise.app.domain.FinanceSnapshot
import com.randwise.app.domain.engine.BudgetPlanner
import com.randwise.app.domain.engine.PlanLine
import com.randwise.app.domain.engine.ProposedLine
import com.randwise.app.domain.engine.SpendingReview
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class SpendingReviewTest {
    private val today = LocalDate.of(2026, 10, 20)
    private val start = today.minusDays(89)
    private val rent = Category(id = 1, name = "Rent", bucket = Bucket.FIXED, colorArgb = 0)
    private val groceries = Category(id = 2, name = "Groceries", bucket = Bucket.VARIABLE, colorArgb = 0, isEssential = true)
    private val takeaways = Category(id = 3, name = "Takeaways", bucket = Bucket.VARIABLE, colorArgb = 0)
    private val salary = Category(id = 4, name = "Salary", bucket = Bucket.INCOME, colorArgb = 0)
    private val profile = UserProfile(payday = 25, netPayMinor = 2_000_000, safetyBufferMinor = 100_000, plannedSavingsMinor = 200_000)

    private var seq = 0L
    private fun tx(type: TxType, amount: Long, date: LocalDate, cat: Category?, merchant: String = "SHOP") =
        Transaction(id = ++seq, accountId = 1, amountMinor = amount, type = type, date = date.toEpochDay(), merchant = merchant, categoryId = cat?.id, fingerprint = "f$seq")

    private fun snapshot(txs: List<Transaction>) =
        FinanceSnapshot(profile, emptyList(), listOf(rent, groceries, takeaways, salary), emptyList(), txs, emptyList(), emptyList(), emptyList(), emptyList())

    private fun threeMonths() = (0..2).flatMap { k ->
        val d = start.plusDays(30L * k) // history starts on the window's first day, so no month is partial
        listOf(
            tx(TxType.INCOME, 2_000_000, d, salary),
            tx(TxType.EXPENSE, 800_000, d, rent, "LANDLORD"),
            tx(TxType.EXPENSE, listOf(400_000L, 420_000L, 380_000L)[k], d, groceries, "POS PURCHASE CHECKERS 1234"),
            tx(TxType.EXPENSE, listOf(100_000L, 150_000L, 200_000L)[k], d, takeaways, "MRD FOOD"),
        )
    }

    @Test
    fun `typical month is the median and income categories are left out`() {
        val p = SpendingReview.profile(snapshot(threeMonths()), today)!!
        assertEquals(3, p.monthsCovered)
        assertEquals(90, p.daysOfData)
        assertEquals(listOf("Rent", "Groceries", "Takeaways"), p.categories.map { it.category.name })
        assertEquals(400_000, p.categories[1].typical)
        assertEquals(listOf(100_000L, 150_000L, 200_000L), p.categories[2].months)
        assertEquals(1_370_000, p.typicalSpend)
        assertEquals(1_800_000, p.availableForBudget)
        assertEquals("CHECKERS", p.merchants.first { it.categoryName == "Groceries" }.label)
    }

    @Test
    fun `a partial first month is scaled up to a full month`() {
        val earliest = today.minusDays(39)
        val txs = listOf(tx(TxType.EXPENSE, 100_000, earliest, groceries), tx(TxType.EXPENSE, 300_000, today.minusDays(5), groceries))
        val p = SpendingReview.profile(snapshot(txs), today)!!
        assertEquals(2, p.monthsCovered)
        assertEquals(listOf(0L, 300_000L, 300_000L), p.categories.single().months) // 10 days of R1 000 → R3 000 a month
        assertEquals(300_000, p.categories.single().typical)
    }

    @Test
    fun `no transactions means no profile`() {
        assertNull(SpendingReview.profile(snapshot(emptyList()), today))
    }

    @Test
    fun `baseline keeps essentials and trims wants by ten percent`() {
        val lines = BudgetPlanner.baseline(SpendingReview.profile(snapshot(threeMonths()), today)!!)
        assertEquals(mapOf("Rent" to 800_000L, "Groceries" to 400_000L, "Takeaways" to 135_000L), lines.associate { it.category to it.limit })
    }

    @Test
    fun `AI proposals are clamped to guard-rails and unknown categories ignored`() {
        val baseline = BudgetPlanner.baseline(SpendingReview.profile(snapshot(threeMonths()), today)!!)
        val accepted = BudgetPlanner.accept(
            baseline,
            listOf(
                ProposedLine("takeaways", 50_000, "Cook twice more a week"),
                ProposedLine("Groceries", 1_000_000, ""),
                ProposedLine("Crypto", 500_000, "Invest"),
            ),
            available = 1_800_000,
        ).associateBy { it.category }
        assertEquals(75_000, accepted.getValue("Takeaways").limit) // floor: half the typical month
        assertEquals("Cook twice more a week", accepted.getValue("Takeaways").reason)
        assertEquals(480_000, accepted.getValue("Groceries").limit) // ceiling: 120% of typical
        assertTrue("Crypto" !in accepted)
    }

    @Test
    fun `over income, wants shrink proportionally but not below half their typical month`() {
        val lines = listOf(
            PlanLine(1, "Rent", true, 800_000, 800_000, ""),
            PlanLine(3, "Takeaways", false, 150_000, 135_000, ""),
            PlanLine(5, "Fun", false, 200_000, 180_000, ""),
        )
        val fitted = BudgetPlanner.fit(lines, available = 1_000_000)
        assertEquals(mapOf("Rent" to 800_000L, "Takeaways" to 85_000L, "Fun" to 110_000L), fitted.associate { it.category to it.limit })
        assertEquals("Trimmed so the budget fits your income.", fitted.first { it.category == "Fun" }.reason)
    }

    @Test
    fun `bank fees are never given a budget`() {
        val fees = Category(id = 9, name = "Bank Fees", bucket = Bucket.VARIABLE, colorArgb = 0)
        val txs = threeMonths() + (0..2).map { k -> tx(TxType.EXPENSE, 19_000, start.plusDays(30L * k + 3), fees) }
        val s = snapshot(txs).let { it.copy(categories = it.categories + fees) }
        assertTrue(BudgetPlanner.baseline(SpendingReview.profile(s, today)!!).none { it.category == "Bank Fees" })
    }

    @Test
    fun `pay-cycle amounts convert to and from monthly`() {
        assertEquals(2_600_000, SpendingReview.monthlyFromCycle(1_200_000, PayFrequency.FORTNIGHTLY))
        assertEquals(1_200_000, SpendingReview.cycleFromMonthly(2_600_000, PayFrequency.FORTNIGHTLY))
        assertEquals(520_000, SpendingReview.monthlyFromCycle(120_000, PayFrequency.WEEKLY))
    }
}

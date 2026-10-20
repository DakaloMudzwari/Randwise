package com.randwise.app

import com.randwise.app.data.model.Account
import com.randwise.app.data.model.AccountType
import com.randwise.app.data.model.Debt
import com.randwise.app.data.model.RecurringBill
import com.randwise.app.data.model.Transaction
import com.randwise.app.data.model.TxType
import com.randwise.app.domain.CategoryLearning
import com.randwise.app.domain.Ledger
import com.randwise.app.domain.PayCycle
import com.randwise.app.domain.engine.AffordabilityEngine
import com.randwise.app.domain.engine.BehaviorAnalyzer
import com.randwise.app.domain.engine.BillSchedule
import com.randwise.app.domain.engine.CashFlowForecaster
import com.randwise.app.domain.engine.DebtPlanner
import com.randwise.app.domain.engine.DebtStrategy
import com.randwise.app.domain.engine.ForecastInputs
import com.randwise.app.domain.engine.RecurringBillDetector
import com.randwise.app.domain.engine.SafeToSpendEngine
import com.randwise.app.domain.engine.ScheduledEvent
import com.randwise.app.domain.engine.VelocityEngine
import com.randwise.app.domain.engine.Verdict
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

private var seq = 0L
private fun tx(account: Long, type: TxType, amount: Long, date: LocalDate, merchant: String = "x", cat: Long? = null, group: String? = null) =
    Transaction(id = ++seq, accountId = account, amountMinor = amount, type = type, date = date.toEpochDay(), merchant = merchant, categoryId = cat, fingerprint = "f$seq", transferGroupId = group)

class LedgerTest {
    private val d = LocalDate.of(2026, 10, 5)
    private val cheque = Account(id = 1, name = "Cheque", type = AccountType.CHEQUE, openingBalanceMinor = 100_000)
    private val card = Account(id = 2, name = "Card", type = AccountType.CREDIT_CARD)
    private val savings = Account(id = 3, name = "Savings", type = AccountType.SAVINGS, includeInSafeToSpend = false)
    private val txs = listOf(
        tx(1, TxType.INCOME, 2_000_000, d),
        tx(2, TxType.EXPENSE, 50_000, d, cat = 5),                 // purchase on the card
        tx(1, TxType.CARD_PAYMENT, 50_000, d, group = "g1"),       // paying the card from cheque
        tx(2, TxType.TRANSFER_IN, 50_000, d, group = "g1"),
        tx(1, TxType.TRANSFER_OUT, 100_000, d, group = "g2"),      // move to savings
        tx(3, TxType.TRANSFER_IN, 100_000, d, group = "g2"),
        tx(2, TxType.REFUND, 10_000, d, cat = 5),
    )

    @Test
    fun `transfers and card payments do not alter income or spend`() {
        assertEquals(2_000_000, Ledger.income(txs))
        assertEquals(40_000, Ledger.spend(txs)) // 500 purchase − 100 refund; card payment not double counted
    }

    @Test
    fun `refunds offset category spend`() {
        assertEquals(40_000, Ledger.spendByCategory(txs)[5])
    }

    @Test
    fun `balances follow every leg`() {
        assertEquals(1_950_000, Ledger.balance(cheque, txs))
        assertEquals(10_000, Ledger.balance(card, txs))
        assertEquals(100_000, Ledger.balance(savings, txs))
        // Only cheque is liquid (card excluded, savings opted out).
        assertEquals(1_950_000, Ledger.liquidBalance(listOf(cheque, card, savings), txs))
    }
}

class SafeToSpendAndVelocityTest {

    @Test
    fun `equation 1`() {
        assertEquals(550_000, SafeToSpendEngine.compute(1_000_000, 300_000, 100_000, 50_000))
    }

    @Test
    fun `paid bills are not deducted twice`() {
        val cycle = PayCycle.forDate(LocalDate.of(2026, 10, 19), 25, true)
        val rent = RecurringBill(id = 1, name = "Rent", merchantPattern = "LANDLORD", expectedAmountMinor = 800_000, dueDay = 1, isConfirmed = true)
        val gym = RecurringBill(id = 2, name = "Gym", merchantPattern = "VIRGIN ACTIVE", expectedAmountMinor = 50_000, dueDay = 20, isConfirmed = true)
        val txs = listOf(tx(1, TxType.EXPENSE, 800_000, LocalDate.of(2026, 10, 1), "DEBIT ORDER LANDLORD 12345"))
        val occ = BillSchedule.occurrencesInCycle(listOf(rent, gym), cycle, txs)
        val r = SafeToSpendEngine.evaluate(1_000_000, occ, 0, 50_000, cycle.daysUntilPayday(LocalDate.of(2026, 10, 19)))
        assertEquals(50_000, r.unpaidBillsTotal)
        assertEquals(900_000, r.safeToSpend)
        assertEquals(225_000, r.perDay) // 4 days to payday
    }

    @Test
    fun `equation 2 linear projection`() {
        assertEquals(350_000, VelocityEngine.projectLinear(100_000, 10, 20, 50_000))
        // Rent of 600 inside the 1000 spent is not extrapolated.
        assertEquals(230_000, VelocityEngine.projectLinear(100_000, 10, 20, 50_000, variableSpent = 40_000))
    }
}

class ForecastAndAffordabilityTest {
    private val today = LocalDate.of(2026, 10, 19)
    private val inputs = ForecastInputs(
        today = today, startBalance = 1_000_000, variableTotal = 0, variableDays = 30,
        events = listOf(ScheduledEvent(LocalDate.of(2026, 10, 22), -300_000, "Bill")), safetyBuffer = 50_000,
    )

    @Test
    fun `velocity is spread without rounding drift`() {
        val f = CashFlowForecaster.forecast(inputs.copy(variableTotal = 1_000, variableDays = 3, events = emptyList()), 3)
        assertEquals(listOf(1_000_000L, 999_667L, 999_333L, 999_000L), f.points.map { it.balance })
    }

    @Test
    fun `bills land on their due date`() {
        val f = CashFlowForecaster.forecast(inputs, 7)
        assertEquals(700_000, f.lowest.balance)
        assertEquals(LocalDate.of(2026, 10, 22), f.lowest.date)
    }

    @Test
    fun `affordability verdicts`() {
        val payday = LocalDate.of(2026, 10, 23)
        fun check(amount: Long) = AffordabilityEngine.check(amount, false, inputs, 650_000, payday, 300_000, 1)
        assertEquals(Verdict.AFFORDABLE, check(100_000).verdict)
        assertEquals(550_000, check(100_000).safeToSpendAfter)
        assertEquals(Verdict.TIGHT, check(700_000).verdict)
        assertEquals(Verdict.NOT_AFFORDABLE, check(800_000).verdict)
    }
}

class DebtPlannerTest {
    private val card = Debt(id = 1, name = "Card", balanceMinor = 1_000_000, aprBp = 2_500, minPaymentMinor = 30_000)
    private val loan = Debt(id = 2, name = "Loan", balanceMinor = 300_000, aprBp = 1_000, minPaymentMinor = 15_000)

    @Test
    fun `avalanche never costs more interest than snowball`() {
        val budget = 95_000L
        val snow = DebtPlanner.plan(listOf(card, loan), budget, DebtStrategy.SNOWBALL)
        val aval = DebtPlanner.plan(listOf(card, loan), budget, DebtStrategy.AVALANCHE)
        assertTrue(snow.feasible && aval.feasible)
        assertTrue(aval.totalInterest <= snow.totalInterest)
        assertEquals("Loan", snow.payoffs.first().name)
        // Paid = principal + interest, to the cent.
        assertEquals(1_300_000 + aval.totalInterest, aval.totalPaid)
    }

    @Test
    fun `detects payments that never cover interest`() {
        val bad = Debt(id = 3, name = "Bad", balanceMinor = 1_000_000, aprBp = 3_600, minPaymentMinor = 20_000)
        assertFalse(DebtPlanner.plan(listOf(bad), 20_000, DebtStrategy.AVALANCHE).feasible)
    }
}

class BehaviorTest {

    @Test
    fun `detects monthly subscriptions but not frequent purchases`() {
        val txs = (7..10).map { m -> tx(1, TxType.EXPENSE, 19_900, LocalDate.of(2026, m, 5), "NETFLIX.COM 8836") } +
            (1..12).map { d -> tx(1, TxType.EXPENSE, 4_500, LocalDate.of(2026, 9, d), "VIDA E CAFFE") }
        val found = RecurringBillDetector.detect(txs, emptyList())
        assertEquals(1, found.size)
        assertEquals("NETFLIX COM", found.first().pattern)
        assertEquals(5, found.first().dueDay)
        assertEquals(19_900, found.first().medianAmount)
    }

    @Test
    fun `steady spending scores low volatility, erratic high`() {
        assertEquals(0, BehaviorAnalyzer.volatilityScore(LongArray(30) { 10_000 }))
        val erratic = LongArray(30) { if (it % 10 == 0) 300_000 else 0 }
        assertEquals(100, BehaviorAnalyzer.volatilityScore(erratic))
    }

    @Test
    fun `flags fee traps`() {
        val today = LocalDate.of(2026, 10, 19)
        val cycle = PayCycle.forDate(today, 25, true)
        val txs = listOf(tx(1, TxType.EXPENSE, 15_000, today.minusDays(3), "UNPAID DEBIT ORDER FEE"))
        val report = BehaviorAnalyzer.analyze(today, cycle, txs, emptyList(), emptyList())
        assertEquals(1, report.feeCount)
        assertEquals(15_000, report.feeTotal)
    }
}

class PercentMinimumTest {
    @Test
    fun `percentage minimum with a floor`() {
        val card = Debt(id = 1, name = "Card", balanceMinor = 1_000_000, aprBp = 2_075, minPaymentMinor = 15_000, minPaymentBp = 300)
        assertEquals(30_000, card.minimumFor(1_000_000)) // 3% of R10 000
        assertEquals(15_000, card.minimumFor(200_000))   // 3% = R60 < R150 floor
        assertEquals(8_000, card.minimumFor(8_000))      // never more than what's owed
    }

    @Test
    fun `planner handles shrinking percentage minimums`() {
        val card = Debt(id = 1, name = "Card", balanceMinor = 1_000_000, aprBp = 2_075, minPaymentMinor = 15_000, minPaymentBp = 300)
        val plan = DebtPlanner.plan(listOf(card), 50_000, DebtStrategy.AVALANCHE)
        assertTrue(plan.feasible)
        assertEquals(1_000_000 + plan.totalInterest, plan.totalPaid)
    }
}

class CategoryLearningTest {
    private val d = LocalDate.of(2026, 10, 1)

    @Test
    fun `finds the same merchant across description noise`() {
        val corrected = tx(1, TxType.EXPENSE, 5_000, d, "POS PURCHASE VIDA E CAFFE 4411", cat = null)
        val all = listOf(
            corrected,
            tx(1, TxType.EXPENSE, 4_500, d.plusDays(3), "VIDA E CAFFE 9902", cat = null),
            tx(1, TxType.EXPENSE, 4_800, d.plusDays(5), "CARD PURCHASE VIDA E CAFFE", cat = 9),
            tx(1, TxType.EXPENSE, 4_800, d.plusDays(6), "VIDA E CAFFE", cat = 7), // already correct
            tx(1, TxType.REFUND, 4_800, d.plusDays(6), "VIDA E CAFFE", cat = null), // refunds count as spend too
            tx(1, TxType.INCOME, 4_800, d.plusDays(7), "VIDA E CAFFE", cat = null), // income: different direction
            tx(1, TxType.EXPENSE, 9_000, d.plusDays(8), "CHECKERS", cat = null),
        )
        assertEquals(3, CategoryLearning.similar(all, corrected, 7).size)
        assertEquals("VIDA CAFFE", CategoryLearning.patternFor(corrected))
    }
}

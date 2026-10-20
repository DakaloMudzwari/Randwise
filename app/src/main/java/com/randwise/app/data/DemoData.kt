package com.randwise.app.data

import com.randwise.app.data.model.Account
import com.randwise.app.data.model.AccountType
import com.randwise.app.data.model.Debt
import com.randwise.app.data.model.Goal
import com.randwise.app.data.model.RecurringBill
import com.randwise.app.data.model.Transaction
import com.randwise.app.data.model.TxSource
import com.randwise.app.data.model.TxType
import com.randwise.app.data.model.UserProfile
import com.randwise.app.domain.PayCycle
import java.time.LocalDate
import java.time.YearMonth
import kotlin.random.Random

/**
 * A realistic, deterministic South African household for "Explore with demo data":
 * salary on the 25th, rent, debit orders, groceries, fuel, takeaways, a creeping subscription
 * and the odd bank fee — enough history for every engine to have something to say.
 */
object DemoData {

    data class Seed(
        val profile: UserProfile,
        val accounts: List<Account>,
        val bills: List<RecurringBill>,
        val debts: List<Debt>,
        val goals: List<Goal>,
        val transactions: (accountIds: List<Long>, categories: Map<String, Long>) -> List<Transaction>,
        val budgets: Map<String, Long>,
    )

    fun build(today: LocalDate): Seed {
        val profile = UserProfile(payday = 25, adjustPaydayForWeekend = true, netPayMinor = 2_850_000, safetyBufferMinor = 100_000, plannedSavingsMinor = 300_000, emergencyTargetMonths = 3)
        // A debit order that is still pending, so the demo shows bills being protected before payday.
        val pendingDay = today.plusDays(2).dayOfMonth
        val accounts = listOf(
            Account(name = "Everyday cheque", type = AccountType.CHEQUE, openingBalanceMinor = 640_000),
            Account(name = "Rainy-day savings", type = AccountType.SAVINGS, openingBalanceMinor = 1_250_000, includeInSafeToSpend = false),
            Account(name = "Credit card", type = AccountType.CREDIT_CARD, openingBalanceMinor = -820_000, includeInSafeToSpend = false),
        )
        val txBuilder = { ids: List<Long>, cat: Map<String, Long> ->
            val (cheque, savings, card) = ids
            val rnd = Random(42)
            val out = mutableListOf<Transaction>()
            var n = 0
            fun add(acc: Long, type: TxType, amount: Long, date: LocalDate, merchant: String, category: String?) {
                if (date.isAfter(today)) return
                out += Transaction(
                    accountId = acc, amountMinor = amount, type = type, date = date.toEpochDay(), merchant = merchant,
                    categoryId = category?.let { cat[it] }, fingerprint = "demo:${n++}", source = TxSource.IMPORT,
                )
            }
            val start = today.minusDays(120)
            var month = YearMonth.from(start)
            while (!month.isAfter(YearMonth.from(today))) {
                val pay = PayCycle.paydayIn(month, 25, true)
                if (!pay.isBefore(start)) add(cheque, TxType.INCOME, 2_850_000, pay, "SALARY ACME HOLDINGS", "Salary")
                add(cheque, TxType.EXPENSE, 850_000, month.atDay(1), "DEBIT ORDER LANDLORD PROPERTIES", "Housing")
                add(cheque, TxType.EXPENSE, 128_900, month.atDay(3), "DEBIT ORDER OUTSURANCE", "Insurance")
                add(cheque, TxType.EXPENSE, 44_900, month.atDay(minOf(28, month.lengthOfMonth())), "VODACOM CONTRACT", "Airtime & Data")
                add(card, TxType.EXPENSE, if (month.monthValue >= YearMonth.from(today).monthValue - 1) 19_900 else 17_900, month.atDay(6), "NETFLIX.COM", "Subscriptions")
                add(card, TxType.EXPENSE, 6_999, month.atDay(12), "SPOTIFY P1A2B3", "Subscriptions")
                add(cheque, TxType.EXPENSE, 59_900, month.atDay(26).coerceAtMost(month.atEndOfMonth()), "VIRGIN ACTIVE", "Personal Care")
                // Savings transfer the day after payday.
                val sv = pay.plusDays(1)
                if (!sv.isBefore(start) && !sv.isAfter(today)) {
                    val g = "demo-sv-$month"
                    out += Transaction(accountId = cheque, amountMinor = 200_000, type = TxType.TRANSFER_OUT, date = sv.toEpochDay(), merchant = "Transfer to Rainy-day savings", fingerprint = "demo:${n++}", transferGroupId = g)
                    out += Transaction(accountId = savings, amountMinor = 200_000, type = TxType.TRANSFER_IN, date = sv.toEpochDay(), merchant = "Transfer in", fingerprint = "demo:${n++}", transferGroupId = g)
                    // Card settled from cheque.
                    val c = "demo-cc-$month"
                    out += Transaction(accountId = cheque, amountMinor = 150_000, type = TxType.CARD_PAYMENT, date = sv.toEpochDay(), merchant = "Card payment: Credit card", fingerprint = "demo:${n++}", transferGroupId = c)
                    out += Transaction(accountId = card, amountMinor = 150_000, type = TxType.TRANSFER_IN, date = sv.toEpochDay(), merchant = "Transfer in", fingerprint = "demo:${n++}", transferGroupId = c)
                }
                month = month.plusMonths(1)
            }
            val grocers = listOf("CHECKERS SANDTON CITY", "WOOLWORTHS ROSEBANK", "PICK N PAY MELROSE", "SPAR BRYANSTON")
            val food = listOf("NANDOS ROSEBANK", "UBER EATS", "MR D FOOD", "VIDA E CAFFE", "KFC GRAYSTON")
            val fuel = listOf("ENGEN RIVONIA", "SHELL WILLIAM NICOL", "SASOL SANDTON")
            var d = start
            while (!d.isAfter(today)) {
                val dom = d.dayOfMonth
                // Payday splurge: more takeaways and shopping in the 3 days after the 25th.
                val afterPayday = dom in 25..28
                if (rnd.nextInt(100) < 30) add(cheque, TxType.EXPENSE, 18_000L + rnd.nextLong(65_000), d, grocers[rnd.nextInt(grocers.size)], "Groceries")
                if (rnd.nextInt(100) < (if (afterPayday) 80 else 22)) add(card, TxType.EXPENSE, 6_500L + rnd.nextLong(if (afterPayday) 38_000 else 18_000), d, food[rnd.nextInt(food.size)], "Eating Out")
                if (rnd.nextInt(100) < 12) add(cheque, TxType.EXPENSE, 55_000L + rnd.nextLong(30_000), d, fuel[rnd.nextInt(fuel.size)], "Transport")
                if (rnd.nextInt(100) < 15) add(cheque, TxType.EXPENSE, 4_500L + rnd.nextLong(9_000), d, "UBER TRIP", "Transport")
                if (afterPayday && rnd.nextInt(100) < 40) add(card, TxType.EXPENSE, 35_000L + rnd.nextLong(90_000), d, listOf("TAKEALOT.COM", "MR PRICE", "ZARA SANDTON")[rnd.nextInt(3)], "Shopping")
                if (rnd.nextInt(100) < 6) add(cheque, TxType.EXPENSE, 12_000L + rnd.nextLong(25_000), d, "CLICKS PHARMACY", "Health")
                if (rnd.nextInt(100) < 5) add(cheque, TxType.EXPENSE, 8_000L + rnd.nextLong(40_000), d, "STER KINEKOR", "Entertainment")
                d = d.plusDays(1)
            }
            add(cheque, TxType.EXPENSE, 9_500, today.minusDays(40), "UNPAID DEBIT ORDER FEE", "Bank Fees")
            add(cheque, TxType.EXPENSE, 6_900, today.minusDays(12), "MONTHLY ACCOUNT FEE", "Bank Fees")
            add(card, TxType.REFUND, 49_900, today.minusDays(6), "TAKEALOT.COM REFUND", "Shopping")
            out
        }
        val bills = listOf(
            RecurringBill(name = "Rent", merchantPattern = "LANDLORD PROPERTIES", expectedAmountMinor = 850_000, dueDay = 1, isConfirmed = true),
            RecurringBill(name = "Car & home insurance", merchantPattern = "OUTSURANCE", expectedAmountMinor = 128_900, dueDay = 3, isConfirmed = true),
            RecurringBill(name = "Vodacom", merchantPattern = "VODACOM CONTRACT", expectedAmountMinor = 44_900, dueDay = 28, isConfirmed = true),
            RecurringBill(name = "Gym", merchantPattern = "VIRGIN ACTIVE", expectedAmountMinor = 59_900, dueDay = 26, isConfirmed = true),
            RecurringBill(name = "Medical aid", merchantPattern = "DISCOVERY HEALTH", expectedAmountMinor = 312_000, dueDay = pendingDay, isConfirmed = true),
        )
        val debts = listOf(
            Debt(name = "Credit card", balanceMinor = 820_000, aprBp = 2_075, minPaymentMinor = 15_000, minPaymentBp = 300),
            Debt(name = "Car loan", balanceMinor = 9_450_000, aprBp = 1_250, minPaymentMinor = 365_000),
            Debt(name = "Store account", balanceMinor = 310_000, aprBp = 2_400, minPaymentMinor = 10_000, minPaymentBp = 500),
        )
        val goals = listOf(
            Goal(name = "Emergency fund", targetMinor = 6_000_000, savedMinor = 1_250_000, isEmergencyFund = true),
            Goal(name = "Cape Town December trip", targetMinor = 1_800_000, savedMinor = 720_000),
        )
        val budgets = mapOf("Groceries" to 450_000L, "Eating Out" to 180_000L, "Transport" to 250_000L, "Shopping" to 150_000L, "Entertainment" to 60_000L, "Subscriptions" to 30_000L)
        return Seed(profile, accounts, bills, debts, goals, txBuilder, budgets)
    }

}

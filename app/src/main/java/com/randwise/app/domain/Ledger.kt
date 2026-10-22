package com.randwise.app.domain

import com.randwise.app.data.model.Account
import com.randwise.app.data.model.AccountType
import com.randwise.app.data.model.Transaction
import com.randwise.app.data.model.TxType

/**
 * Ledger arithmetic implementing the F02 accounting rules:
 *  - internal transfers and card payments never touch income or spend totals;
 *  - refunds reduce spend in their category.
 */
object Ledger {

    fun balance(account: Account, transactions: List<Transaction>): Long =
        account.openingBalanceMinor + transactions.filter { it.accountId == account.id }.sumOf { it.signedAmountMinor }

    /**
     * Statement reconciliation: the opening balance that makes [account]'s balance at the end of [endDay] equal the
     * bank's [closingBalance]. Without it, importing history on top of a "current balance" typed at setup counts
     * the same money twice. Transactions after [endDay] (newer manual entries) keep counting on top.
     */
    fun openingToMatch(account: Account, transactions: List<Transaction>, closingBalance: Long, endDay: Long): Long =
        closingBalance - transactions.filter { it.accountId == account.id && it.date <= endDay }.sumOf { it.signedAmountMinor }

    /** Cash the user can actually spend: non-card accounts flagged for safe-to-spend. */
    fun liquidBalance(accounts: List<Account>, transactions: List<Transaction>): Long =
        accounts.filter { !it.archived && it.includeInSafeToSpend && it.type != AccountType.CREDIT_CARD }
            .sumOf { balance(it, transactions) }

    fun income(transactions: List<Transaction>): Long =
        transactions.filter { it.type == TxType.INCOME }.sumOf { it.amountMinor }

    /** Net spend = expenses − refunds. Transfers and card payments are excluded. */
    fun spend(transactions: List<Transaction>): Long =
        transactions.sumOf {
            when (it.type) {
                TxType.EXPENSE -> it.amountMinor
                TxType.REFUND -> -it.amountMinor
                else -> 0L
            }
        }

    fun spendByCategory(transactions: List<Transaction>): Map<Long?, Long> =
        transactions.filter { it.type.countsAsSpend }
            .groupBy { it.categoryId }
            .mapValues { (_, list) -> spend(list) }

    fun inRange(transactions: List<Transaction>, startDay: Long, endDayExclusive: Long): List<Transaction> =
        transactions.filter { it.date >= startDay && it.date < endDayExclusive }

    /** Net spend per day for [days] days starting at [startDay]. */
    fun dailySpend(transactions: List<Transaction>, startDay: Long, days: Int): LongArray {
        val out = LongArray(days)
        for (t in transactions) {
            val idx = (t.date - startDay).toInt()
            if (idx !in 0 until days) continue
            when (t.type) {
                TxType.EXPENSE -> out[idx] += t.amountMinor
                TxType.REFUND -> out[idx] -= t.amountMinor
                else -> Unit
            }
        }
        return out
    }
}

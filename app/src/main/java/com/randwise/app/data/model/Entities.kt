package com.randwise.app.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * Room entities (FDS §6). Every money field is a Long of minor units (cents).
 * Dates are stored as epoch days (LocalDate.toEpochDay()) so they are timezone-safe.
 */

enum class AccountType { CHEQUE, SAVINGS, CREDIT_CARD, CASH }

/** Spending buckets used by the Smart Budget Engine (F04). */
enum class Bucket { FIXED, VARIABLE, DEBT, SAVINGS, INCOME }

enum class TxSource { MANUAL, IMPORT }

/**
 * Transaction types encode the accounting rules of F02:
 *  - TRANSFER_IN / TRANSFER_OUT move money between own accounts and are net-zero for income/expense.
 *  - CARD_PAYMENT settles a credit card; the purchases were already counted on the card account.
 *  - REFUND offsets spend in its category.
 */
enum class TxType(val balanceSign: Int) {
    INCOME(+1),
    EXPENSE(-1),
    REFUND(+1),
    TRANSFER_IN(+1),
    TRANSFER_OUT(-1),
    CARD_PAYMENT(-1);

    val countsAsIncome get() = this == INCOME
    val countsAsSpend get() = this == EXPENSE || this == REFUND
    val isInternal get() = this == TRANSFER_IN || this == TRANSFER_OUT || this == CARD_PAYMENT
}

@Entity(tableName = "user_profile")
data class UserProfile(
    @PrimaryKey val id: Long = 1,
    /** Day of month salary lands (1..31; clamped to month length). */
    val payday: Int,
    /** SA employers usually pay on the preceding Friday if payday falls on a weekend. */
    val adjustPaydayForWeekend: Boolean = true,
    val currency: String = "ZAR",
    val monthlyIncomeMinor: Long,
    val safetyBufferMinor: Long,
    val plannedSavingsMinor: Long = 0,
    val emergencyTargetMonths: Int = 3,
)

@Entity(tableName = "accounts")
data class Account(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Friendly label only — account numbers are never stored (POPIA data minimisation). */
    val name: String,
    val type: AccountType,
    val openingBalanceMinor: Long = 0,
    val includeInSafeToSpend: Boolean = true,
    val archived: Boolean = false,
)

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val bucket: Bucket,
    val colorArgb: Long,
    /** Essential spend counts toward emergency-fund coverage (F08). */
    val isEssential: Boolean = false,
)

/** Remembered mapping "merchant contains pattern" -> category, built from the import mapping UI. */
@Entity(
    tableName = "category_rules",
    indices = [Index(value = ["pattern"], unique = true)],
)
data class CategoryRule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val pattern: String,
    val categoryId: Long,
)

@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["fingerprint"], unique = true),
        Index(value = ["accountId", "date"]),
        Index(value = ["categoryId"]),
    ],
)
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    /** Always a positive magnitude; direction comes from [type]. */
    val amountMinor: Long,
    val type: TxType,
    /** Epoch day. */
    val date: Long,
    val merchant: String,
    val categoryId: Long? = null,
    val note: String = "",
    /** Deterministic hash used for import de-duplication (FDS §9). */
    val fingerprint: String,
    val source: TxSource = TxSource.MANUAL,
    /** Links both legs of an internal transfer. */
    val transferGroupId: String? = null,
) {
    val signedAmountMinor: Long get() = amountMinor * type.balanceSign
}

@Entity(
    tableName = "budgets",
    indices = [Index(value = ["categoryId", "periodStart"], unique = true)],
)
data class Budget(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryId: Long,
    val periodStart: Long,
    val periodEnd: Long,
    val limitMinor: Long,
)

@Entity(tableName = "recurring_bills")
data class RecurringBill(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Normalised merchant text used to match payments. */
    val merchantPattern: String,
    val expectedAmountMinor: Long,
    val dueDay: Int,
    /** Detected bills start unconfirmed until the user accepts them. */
    val isConfirmed: Boolean,
    val categoryId: Long? = null,
)

@Entity(tableName = "debts")
data class Debt(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val balanceMinor: Long,
    /** Annual interest rate in basis points (21.75% = 2175). */
    val aprBp: Int,
    val minPaymentMinor: Long,
)

@Entity(tableName = "goals")
data class Goal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val targetMinor: Long,
    val savedMinor: Long,
    val isEmergencyFund: Boolean = false,
    /** Optional epoch day. */
    val targetDate: Long? = null,
)

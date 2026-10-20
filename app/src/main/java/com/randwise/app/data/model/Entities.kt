package com.randwise.app.data.model

import androidx.room.ColumnInfo
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

/** How often the user is paid. Weekly and fortnightly pay are common for SA hourly and shift workers. */
enum class PayFrequency(val days: Long?) { MONTHLY(null), FORTNIGHTLY(14), WEEKLY(7) }

/** How often a bill repeats. Monthly bills use [RecurringBill.dueDay]; others use [RecurringBill.anchorDate]. */
enum class BillFrequency { WEEKLY, MONTHLY, YEARLY }

@Entity(tableName = "user_profile")
data class UserProfile(
    @PrimaryKey val id: Long = 1,
    /** Monthly pay: day of month salary lands (1..31; clamped to month length). */
    val payday: Int,
    /** SA employers usually pay on the preceding Friday if payday falls on a weekend. */
    val adjustPaydayForWeekend: Boolean = true,
    val currency: String = "ZAR",
    /** Take-home pay received on each payday (per month, fortnight or week depending on [payFrequency]). */
    @ColumnInfo(name = "monthlyIncomeMinor") val netPayMinor: Long,
    val safetyBufferMinor: Long,
    val plannedSavingsMinor: Long = 0,
    val emergencyTargetMonths: Int = 3,
    @ColumnInfo(defaultValue = "MONTHLY") val payFrequency: PayFrequency = PayFrequency.MONTHLY,
    /** Weekly / fortnightly pay: epoch day of any known payday; later paydays repeat from it. */
    val payAnchorDate: Long? = null,
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
    @ColumnInfo(defaultValue = "MONTHLY") val frequency: BillFrequency = BillFrequency.MONTHLY,
    /** Weekly / yearly bills: epoch day of one due date; others repeat from it. */
    val anchorDate: Long? = null,
)

@Entity(tableName = "debts")
data class Debt(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val balanceMinor: Long,
    /** Annual interest rate in basis points (21.75% = 2175). */
    val aprBp: Int,
    /** Fixed minimum (or the floor when [minPaymentBp] is set). */
    val minPaymentMinor: Long,
    /**
     * SA credit cards and store accounts usually require a % of the outstanding balance each month
     * (e.g. 3% = 300), with [minPaymentMinor] as the floor. 0 = fixed instalment (loans).
     */
    @ColumnInfo(defaultValue = "0") val minPaymentBp: Int = 0,
) {
    /** Minimum due on [balance]: the larger of the floor and the percentage, never more than the balance. */
    fun minimumFor(balance: Long): Long {
        val pct = if (minPaymentBp > 0) com.randwise.app.core.Money.applyBasisPoints(balance, minPaymentBp.toLong()) else 0L
        return maxOf(minPaymentMinor, pct).coerceAtMost(balance.coerceAtLeast(0))
    }
}

/** A saved coach conversation turn (kept in the encrypted database; erased with everything else). */
@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** USER, GEMINI or LOCAL. */
    val source: String,
    val text: String,
    /** Newline-separated highlights. */
    val highlights: String = "",
    val note: String? = null,
    val timestamp: Long,
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
    /** When set, progress follows this account's live balance instead of [savedMinor]. */
    val linkedAccountId: Long? = null,
)

/** POPIA: an auditable record of every consent the user grants or withdraws. */
@Entity(tableName = "consent_records")
data class ConsentRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val granted: Boolean,
    /** Version of the privacy notice the user saw. */
    val policyVersion: String,
    /** Epoch millis. */
    val timestamp: Long,
)

package com.randwise.app.data

import androidx.room.withTransaction
import com.randwise.app.core.Money
import com.randwise.app.data.db.RandWiseDatabase
import com.randwise.app.data.model.Account
import com.randwise.app.data.model.AccountType
import com.randwise.app.data.model.Budget
import com.randwise.app.data.model.CheckupEntity
import com.randwise.app.data.model.Category
import com.randwise.app.data.model.CategoryRule
import com.randwise.app.data.model.ChatMessageEntity
import com.randwise.app.data.model.ConsentRecord
import com.randwise.app.data.model.Debt
import com.randwise.app.data.model.Goal
import com.randwise.app.data.model.RecurringBill
import com.randwise.app.data.model.Transaction
import com.randwise.app.data.model.TxSource
import com.randwise.app.data.model.TxType
import com.randwise.app.data.model.UserProfile
import com.randwise.app.domain.CategoryLearning
import com.randwise.app.domain.FinanceSnapshot
import com.randwise.app.domain.Ledger
import com.randwise.app.domain.MerchantText
import com.randwise.app.domain.PayCycle
import com.randwise.app.domain.PaySchedule
import com.randwise.app.domain.engine.RecurringCandidate
import com.randwise.app.importer.Categorizer
import com.randwise.app.importer.Fingerprint
import com.randwise.app.importer.ParsedStatement
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.io.OutputStream
import java.time.LocalDate
import java.util.UUID

/** Rows saved, how far the account was moved to match the statement, and the balance it now matches (if checked). */
data class ImportResult(val inserted: Int, val balanceCorrection: Long, val matchedClosing: Long?)

/** A statement row staged for review before it is committed (F03 duplicate review). */
data class ImportRow(
    val index: Int,
    val date: LocalDate,
    val description: String,
    val amountMinor: Long,
    val type: TxType,
    val categoryId: Long?,
    val fingerprint: String,
    /** Exact fingerprint already stored — committing would be a no-op. */
    val isDuplicate: Boolean,
    /** Same account/date/amount already exists from another source (e.g. a manual entry). */
    val possibleDuplicate: Boolean,
    val include: Boolean,
)

/** Coach turns kept on the device; older ones are trimmed. */
const val CHAT_HISTORY_LIMIT = 200

class FinanceRepository(private val db: RandWiseDatabase) {

    private val profiles = db.profileDao()
    private val accounts = db.accountDao()
    private val categories = db.categoryDao()
    private val txs = db.transactionDao()
    private val budgets = db.budgetDao()
    private val bills = db.billDao()
    private val plans = db.planDao()

    private val consents = db.consentDao()

    val profile: Flow<UserProfile?> = profiles.observe()
    val consentHistory: Flow<List<ConsentRecord>> = consents.observeAll()

    private val chat = db.chatDao()
    private val checkups = db.checkupDao()

    // ---------- Money check-up (latest only, encrypted) ----------

    val checkup: Flow<CheckupEntity?> = checkups.observe()

    suspend fun saveCheckup(json: String, createdAt: Long) = checkups.save(CheckupEntity(json = json, createdAt = createdAt))

    // ---------- Coach chat history (encrypted, bounded, erased with everything else) ----------

    suspend fun chatHistory(): List<ChatMessageEntity> = chat.all()

    suspend fun saveChat(message: ChatMessageEntity) {
        chat.insert(message)
        chat.trim(CHAT_HISTORY_LIMIT)
    }

    suspend fun clearChat() = chat.clear()

    /** POPIA: every grant/withdrawal is logged with the notice version the user saw. */
    suspend fun recordConsent(type: String, granted: Boolean, policyVersion: String) =
        consents.insert(ConsentRecord(type = type, granted = granted, policyVersion = policyVersion, timestamp = System.currentTimeMillis()))

    /** ~13 months of history is enough for every engine (cycles, recurring detection, trends). */
    val snapshot: Flow<FinanceSnapshot> = combine(
        listOf(
            profiles.observe(), accounts.observeAll(), categories.observeAll(), categories.observeRules(),
            txs.observeSince(LocalDate.now().minusDays(400).toEpochDay()), budgets.observeAll(),
            bills.observeAll(), plans.observeDebts(), plans.observeGoals(),
        ),
    ) { a ->
        @Suppress("UNCHECKED_CAST")
        FinanceSnapshot(
            profile = a[0] as UserProfile?,
            accounts = a[1] as List<Account>,
            categories = a[2] as List<Category>,
            rules = a[3] as List<CategoryRule>,
            transactions = a[4] as List<Transaction>,
            budgets = a[5] as List<Budget>,
            bills = a[6] as List<RecurringBill>,
            debts = a[7] as List<Debt>,
            goals = a[8] as List<Goal>,
        )
    }

    // ---------- Profile & onboarding (F01) ----------

    suspend fun completeOnboarding(
        profile: UserProfile,
        accountName: String,
        accountType: AccountType,
        openingBalance: Long,
        fixedObligations: List<Pair<String, Long>>,
        obligationDueDay: Int,
    ) = db.withTransaction {
        profiles.upsert(profile)
        if (categories.getAll().isEmpty()) {
            categories.insertAll(Categorizer.DEFAULT_CATEGORIES.map { Category(name = it.name, bucket = it.bucket, colorArgb = it.color, isEssential = it.essential) })
        }
        if (accounts.getAll().isEmpty()) {
            accounts.insert(Account(name = accountName, type = accountType, openingBalanceMinor = openingBalance))
        }
        val cats = categories.getAll().associateBy { it.name }
        for ((name, amount) in fixedObligations.filter { it.second > 0 }) {
            val cat = when {
                name.contains("rent", true) || name.contains("bond", true) -> cats["Housing"]
                name.contains("insur", true) -> cats["Insurance"]
                name.contains("debt", true) || name.contains("loan", true) -> cats["Debt Repayments"]
                else -> cats["Utilities"]
            }
            bills.upsert(RecurringBill(name = name, merchantPattern = MerchantText.normalize(name), expectedAmountMinor = amount, dueDay = obligationDueDay, isConfirmed = true, categoryId = cat?.id))
        }
        ensureBudgetsForCycle(PayCycle.forDate(LocalDate.now(), PaySchedule.of(profile)))
    }

    suspend fun updateProfile(profile: UserProfile) = profiles.upsert(profile)

    /** Loads the demo household (see [DemoData]) into an empty database. */
    suspend fun seedDemo() = db.withTransaction {
        val today = LocalDate.now()
        val seed = DemoData.build(today)
        if (categories.getAll().isEmpty()) {
            categories.insertAll(Categorizer.DEFAULT_CATEGORIES.map { Category(name = it.name, bucket = it.bucket, colorArgb = it.color, isEssential = it.essential) })
        }
        val cats = categories.getAll().associate { it.name to it.id }
        val ids = seed.accounts.map { accounts.insert(it) }
        txs.insertAll(seed.transactions(ids, cats))
        seed.bills.forEach { b ->
            val catName = when (b.name) { "Rent" -> "Housing"; "Vodacom" -> "Airtime & Data"; "Gym" -> "Personal Care"; "Medical aid" -> "Health"; else -> "Insurance" }
            bills.upsert(b.copy(categoryId = cats[catName]))
        }
        seed.debts.forEach { plans.upsertDebt(it) }
        seed.goals.forEach { plans.upsertGoal(it) }
        val cycle = PayCycle.forDate(today, PaySchedule.of(seed.profile))
        seed.budgets.forEach { (name, limit) -> cats[name]?.let { setBudget(it, cycle, limit) } }
        // Profile last: it is what flips the UI out of onboarding.
        profiles.upsert(seed.profile)
    }

    // ---------- Accounts & ledger (F02) ----------

    suspend fun addAccount(name: String, type: AccountType, opening: Long, includeInSafeToSpend: Boolean) =
        accounts.insert(Account(name = name, type = type, openingBalanceMinor = opening, includeInSafeToSpend = includeInSafeToSpend))

    suspend fun updateAccount(account: Account) = accounts.update(account)

    suspend fun addTransaction(accountId: Long, type: TxType, amount: Long, date: LocalDate, merchant: String, categoryId: Long?, note: String) {
        require(amount > 0) { "Amount must be positive" }
        require(!type.isInternal) { "Use addTransfer for internal movements" }
        txs.insert(
            Transaction(
                accountId = accountId, amountMinor = amount, type = type, date = date.toEpochDay(),
                merchant = merchant.trim().ifEmpty { type.name.lowercase().replaceFirstChar { it.uppercase() } },
                categoryId = categoryId, note = note, fingerprint = "manual:${UUID.randomUUID()}", source = TxSource.MANUAL,
            ),
        )
    }

    /**
     * Internal transfer (net-zero) or credit-card payment. Both legs share a group id; neither leg
     * counts as income or spend, so the purchases on the card are never double-counted.
     */
    suspend fun addTransfer(fromId: Long, toId: Long, amount: Long, date: LocalDate, note: String) = db.withTransaction {
        require(amount > 0 && fromId != toId)
        val to = accounts.getAll().first { it.id == toId }
        val group = UUID.randomUUID().toString()
        val outType = if (to.type == AccountType.CREDIT_CARD) TxType.CARD_PAYMENT else TxType.TRANSFER_OUT
        val label = if (outType == TxType.CARD_PAYMENT) "Card payment: ${to.name}" else "Transfer to ${to.name}"
        txs.insert(Transaction(accountId = fromId, amountMinor = amount, type = outType, date = date.toEpochDay(), merchant = label, note = note, fingerprint = "manual:$group:out", transferGroupId = group))
        txs.insert(Transaction(accountId = toId, amountMinor = amount, type = TxType.TRANSFER_IN, date = date.toEpochDay(), merchant = "Transfer in", note = note, fingerprint = "manual:$group:in", transferGroupId = group))
    }

    suspend fun updateTransaction(t: Transaction) = txs.update(t)

    /**
     * Saves a correction and optionally teaches it: applies the category to the same merchant's other
     * transactions and/or stores a rule used by future imports. Returns how many others changed.
     */
    suspend fun recategorize(t: Transaction, categoryId: Long?, note: String, applyToSimilar: Boolean, rememberRule: Boolean): Int = db.withTransaction {
        txs.update(t.copy(categoryId = categoryId, note = note))
        if (categoryId == null) return@withTransaction 0
        val pattern = CategoryLearning.patternFor(t) ?: return@withTransaction 0
        if (rememberRule) categories.upsertRule(CategoryRule(pattern = pattern, categoryId = categoryId))
        if (!applyToSimilar) return@withTransaction 0
        val similar = CategoryLearning.similar(txs.getAll(), t, categoryId)
        similar.forEach { txs.update(it.copy(categoryId = categoryId)) }
        similar.size
    }

    suspend fun deleteTransaction(t: Transaction) {
        if (t.transferGroupId != null) txs.deleteTransferGroup(t.transferGroupId) else txs.delete(t)
    }

    suspend fun addCategory(name: String, bucket: com.randwise.app.data.model.Bucket, color: Long) =
        categories.insert(Category(name = name, bucket = bucket, colorArgb = color))

    // ---------- Statement import (F03) ----------

    suspend fun stageImport(account: Account, statement: ParsedStatement): List<ImportRow> {
        val lines = statement.lines
        if (lines.isEmpty()) return emptyList()
        val prints = Fingerprint.forBatch(account.id, lines)
        val existing = prints.chunked(500).flatMap { txs.existingFingerprints(it) }.toSet()
        val minDay = lines.minOf { it.date.toEpochDay() } - 1
        val maxDay = lines.maxOf { it.date.toEpochDay() } + 1
        // Anything already stored for this account around these dates — manual entries, or the same
        // transaction imported from a different file format whose description differs (CSV vs PDF).
        val printSet = prints.toSet()
        val nearby = txs.inRange(account.id, minDay, maxDay).filter { it.fingerprint !in printSet }
        val cats = categories.getAll()
        val rules = categories.getRules()
        return lines.mapIndexed { i, line ->
            val credit = line.signedAmount > 0
            val upper = line.description.uppercase()
            val type = when {
                account.type == AccountType.CREDIT_CARD && credit && upper.contains("PAYMENT") -> TxType.TRANSFER_IN
                !credit && (upper.contains("CREDIT CARD PAYMENT") || upper.contains("CC PAYMENT") || upper.contains("CARD REPAYMENT")) -> TxType.CARD_PAYMENT
                upper.contains("OWN ACCOUNT") || upper.contains("INTERNAL TRANSFER") || upper.contains("INTER-ACCOUNT") || upper.contains("TRF TO SAVINGS") ->
                    if (credit) TxType.TRANSFER_IN else TxType.TRANSFER_OUT
                credit && (upper.contains("REFUND") || upper.contains("REVERSAL")) -> TxType.REFUND
                credit -> TxType.INCOME
                else -> TxType.EXPENSE
            }
            val amount = kotlin.math.abs(line.signedAmount)
            val category = if (type.isInternal) null
            else Categorizer.categorize(line.description, isCredit = type == TxType.INCOME, rules = rules, categories = cats)
            val dup = prints[i] in existing
            val possible = !dup && nearby.any { kotlin.math.abs(it.date - line.date.toEpochDay()) <= 1 && it.signedAmountMinor == line.signedAmount && it.type.isInternal == type.isInternal }
            ImportRow(i, line.date, line.description, amount, type, category, prints[i], dup, possible, include = !dup && !possible)
        }
    }

    /** Commits staged rows; returns the number of new rows actually written. */
    /**
     * Saves the chosen rows. When [verifiedClosing] is given (the statement's balances added up), the account is
     * reconciled so its balance on the statement's last day equals the bank's — see [Ledger.openingToMatch].
     * Credit cards are left alone: banks state their balance as the amount owed, with the opposite sign.
     */
    suspend fun commitImport(accountId: Long, rows: List<ImportRow>, rememberRules: Boolean, verifiedClosing: Long? = null): ImportResult = db.withTransaction {
        val chosen = rows.filter { it.include && !it.isDuplicate }
        val ids = txs.insertAll(
            chosen.map {
                Transaction(
                    accountId = accountId, amountMinor = it.amountMinor, type = it.type, date = it.date.toEpochDay(),
                    merchant = it.description, categoryId = it.categoryId, fingerprint = it.fingerprint, source = TxSource.IMPORT,
                )
            },
        )
        if (rememberRules) {
            val rules = categories.getRules()
            val cats = categories.getAll()
            for (row in chosen) {
                val cat = row.categoryId ?: continue
                val auto = Categorizer.categorize(row.description, row.type == TxType.INCOME, rules, cats)
                if (auto != cat) {
                    val pattern = MerchantText.normalize(row.description)
                    if (pattern.length >= 3) categories.upsertRule(CategoryRule(pattern = pattern, categoryId = cat))
                }
            }
        }
        var correction = 0L
        val account = accounts.getAll().firstOrNull { it.id == accountId }
        val endDay = rows.maxOfOrNull { it.date.toEpochDay() }
        if (verifiedClosing != null && account != null && endDay != null && account.type != AccountType.CREDIT_CARD) {
            val opening = Ledger.openingToMatch(account, txs.getAll(), verifiedClosing, endDay)
            correction = opening - account.openingBalanceMinor
            if (correction != 0L) accounts.update(account.copy(openingBalanceMinor = opening))
        }
        ImportResult(ids.count { it != -1L }, correction, verifiedClosing.takeIf { account?.type != AccountType.CREDIT_CARD })
    }

    // ---------- Budgets (F04) ----------

    /** Rolls the previous cycle's limits forward so every cycle has a budget. */
    suspend fun ensureBudgetsForCycle(cycle: PayCycle) {
        val start = cycle.start.toEpochDay()
        if (budgets.forPeriod(start).isNotEmpty()) return
        val previous = budgets.latestBefore(start)
        val end = cycle.end.toEpochDay()
        if (previous.isNotEmpty()) {
            budgets.insertAll(previous.map { Budget(categoryId = it.categoryId, periodStart = start, periodEnd = end, limitMinor = it.limitMinor) })
        }
    }

    suspend fun setBudget(categoryId: Long, cycle: PayCycle, limit: Long) {
        val start = cycle.start.toEpochDay()
        if (limit <= 0) { budgets.delete(categoryId, start); return }
        val existing = budgets.forPeriod(start).firstOrNull { it.categoryId == categoryId }
        budgets.upsert(Budget(id = existing?.id ?: 0, categoryId = categoryId, periodStart = start, periodEnd = cycle.end.toEpochDay(), limitMinor = limit))
    }

    // ---------- Bills, debts, goals ----------

    suspend fun confirmCandidate(c: RecurringCandidate) =
        bills.upsert(RecurringBill(name = c.displayName, merchantPattern = c.pattern, expectedAmountMinor = c.medianAmount, dueDay = c.dueDay, isConfirmed = true, categoryId = c.categoryId))

    suspend fun saveBill(bill: RecurringBill) = bills.upsert(bill)
    suspend fun deleteBill(bill: RecurringBill) = bills.delete(bill)
    suspend fun saveDebt(debt: Debt) = plans.upsertDebt(debt)
    suspend fun deleteDebt(debt: Debt) = plans.deleteDebt(debt)
    suspend fun saveGoal(goal: Goal) = plans.upsertGoal(goal)
    suspend fun deleteGoal(goal: Goal) = plans.deleteGoal(goal)
    suspend fun confirmedBills() = bills.getConfirmed()

    // ---------- POPIA: export & erasure ----------

    suspend fun exportCsv(out: OutputStream) {
        val accs = accounts.getAll().associateBy { it.id }
        val cats = categories.getAll().associateBy { it.id }
        out.bufferedWriter().use { w ->
            w.write("date,account,type,amount,description,category,note\n")
            for (t in txs.getAll()) {
                val amount = Money.toPlain(t.signedAmountMinor)
                val row = listOf(
                    LocalDate.ofEpochDay(t.date).toString(), accs[t.accountId]?.name.orEmpty(), t.type.name, amount,
                    t.merchant, t.categoryId?.let { cats[it]?.name }.orEmpty(), t.note,
                )
                w.write(row.joinToString(",") { "\"" + it.replace("\"", "\"\"") + "\"" })
                w.write("\n")
            }
        }
    }

    suspend fun allTransactions() = txs.getAll()
    suspend fun allAccounts() = accounts.getAll()
    suspend fun allCategories() = categories.getAll()

    /** Right to erasure: removes every financial record from the encrypted store. */
    suspend fun purgeAll() {
        db.clearAllTables()
        db.openHelper.writableDatabase.execSQL("VACUUM")
    }
}

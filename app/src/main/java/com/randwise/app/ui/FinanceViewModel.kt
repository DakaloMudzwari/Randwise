package com.randwise.app.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.randwise.app.AppContainer
import com.randwise.app.RandWiseApplication
import com.randwise.app.data.Privacy
import com.randwise.app.data.Settings
import com.randwise.app.data.model.ConsentRecord
import com.randwise.app.security.PasscodeKind
import com.randwise.app.security.PasscodeRules
import com.randwise.app.security.VerifyResult
import com.randwise.app.data.model.AccountType
import com.randwise.app.data.model.Account
import com.randwise.app.data.model.Bucket
import com.randwise.app.data.model.Debt
import com.randwise.app.data.model.Goal
import com.randwise.app.data.model.RecurringBill
import com.randwise.app.data.model.Transaction
import com.randwise.app.data.model.TxType
import com.randwise.app.data.model.UserProfile
import com.randwise.app.domain.Analysis
import com.randwise.app.domain.FinanceAnalyzer
import com.randwise.app.domain.FinanceSnapshot
import com.randwise.app.domain.engine.AffordabilityEngine
import com.randwise.app.domain.engine.AffordabilityResult
import com.randwise.app.domain.engine.RecurringCandidate
import com.randwise.app.export.ReportExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

data class FinanceUiState(
    val loaded: Boolean = false,
    val snapshot: FinanceSnapshot = FinanceSnapshot.EMPTY,
    val analysis: Analysis? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class FinanceViewModel(private val container: AppContainer) : ViewModel() {

    private val repo get() = container.repository

    val state: StateFlow<FinanceUiState> = repo.snapshot
        .mapLatest { s -> FinanceUiState(true, s, FinanceAnalyzer.analyze(s, LocalDate.now())) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, FinanceUiState())

    val settings: StateFlow<Settings> = container.preferences.settings

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    /** One-off user feedback (snackbars). */
    val messages: SharedFlow<String> = _messages

    init {
        // Make sure a new pay cycle inherits last cycle's budgets as soon as it starts.
        viewModelScope.launch {
            state.map { it.analysis?.cycle }.distinctUntilChanged().collect { cycle ->
                if (cycle != null) repo.ensureBudgetsForCycle(cycle)
            }
        }
    }

    private fun launchSafe(success: String? = null, block: suspend () -> Unit) = viewModelScope.launch {
        runCatching { block() }
            .onSuccess { success?.let { _messages.tryEmit(it) } }
            .onFailure { _messages.tryEmit(it.message ?: "Something went wrong") }
    }

    fun completeOnboarding(
        profile: UserProfile, accountName: String, accountType: AccountType, opening: Long,
        obligations: List<Pair<String, Long>>, obligationDueDay: Int,
    ) = launchSafe { repo.completeOnboarding(profile, accountName, accountType, opening, obligations, obligationDueDay) }

    fun loadDemo() = launchSafe("Demo data loaded — explore away") { repo.seedDemo() }

    fun updateProfile(p: UserProfile) = launchSafe("Profile saved") { repo.updateProfile(p) }

    fun addTransaction(accountId: Long, type: TxType, amount: Long, date: LocalDate, merchant: String, categoryId: Long?, note: String) =
        launchSafe("Transaction added") { repo.addTransaction(accountId, type, amount, date, merchant, categoryId, note) }

    fun addTransfer(fromId: Long, toId: Long, amount: Long, date: LocalDate, note: String) =
        launchSafe("Transfer recorded") { repo.addTransfer(fromId, toId, amount, date, note) }

    fun updateTransaction(t: Transaction) = launchSafe { repo.updateTransaction(t) }
    fun deleteTransaction(t: Transaction) = launchSafe("Transaction deleted") { repo.deleteTransaction(t) }

    fun addAccount(name: String, type: AccountType, opening: Long, include: Boolean) =
        launchSafe("Account added") { repo.addAccount(name, type, opening, include) }

    fun updateAccount(a: Account) = launchSafe { repo.updateAccount(a) }

    fun addCategory(name: String, bucket: Bucket, color: Long) = launchSafe("Category added") { repo.addCategory(name, bucket, color) }

    fun setBudget(categoryId: Long, limit: Long) {
        val cycle = state.value.analysis?.cycle ?: return
        launchSafe { repo.setBudget(categoryId, cycle, limit) }
    }

    fun confirmCandidate(c: RecurringCandidate) = launchSafe("${c.displayName} added to bills") { repo.confirmCandidate(c) }

    /** Dismissed candidates are stored unconfirmed so the detector stops suggesting them. */
    fun dismissCandidate(c: RecurringCandidate) = launchSafe {
        repo.saveBill(RecurringBill(name = c.displayName, merchantPattern = c.pattern, expectedAmountMinor = c.medianAmount, dueDay = c.dueDay, isConfirmed = false))
    }

    fun saveBill(b: RecurringBill) = launchSafe("Bill saved") { repo.saveBill(b) }
    fun deleteBill(b: RecurringBill) = launchSafe { repo.deleteBill(b) }
    fun saveDebt(d: Debt) = launchSafe("Debt saved") { repo.saveDebt(d) }
    fun deleteDebt(d: Debt) = launchSafe { repo.deleteDebt(d) }
    fun saveGoal(g: Goal) = launchSafe("Goal saved") { repo.saveGoal(g) }
    fun deleteGoal(g: Goal) = launchSafe { repo.deleteGoal(g) }

    fun checkAffordability(amount: Long, recurring: Boolean): AffordabilityResult? {
        val a = state.value.analysis ?: return null
        if (amount <= 0) return null
        val s = a.safeToSpend
        return AffordabilityEngine.check(amount, recurring, a.forecastInputs, s.safeToSpend, a.cycle.nextPayday, s.unpaidBillsTotal, s.unpaidBills.size)
    }

    fun updateSettings(context: Context, transform: (Settings) -> Settings) {
        val before = settings.value
        container.preferences.update(transform)
        val after = settings.value
        if (before.billReminders != after.billReminders) {
            (context.applicationContext as RandWiseApplication).scheduleReminders(after.billReminders)
        }
    }

    fun exportCsv(context: Context, uri: Uri) = launchSafe("Exported transactions (CSV)") {
        withContext(Dispatchers.IO) {
            context.contentResolver.openOutputStream(uri)?.use { repo.exportCsv(it) } ?: error("Could not write file")
        }
    }

    fun exportPdf(context: Context, uri: Uri) = launchSafe("Exported report (PDF)") {
        val s = state.value
        val a = s.analysis ?: error("Nothing to export yet")
        withContext(Dispatchers.IO) {
            context.contentResolver.openOutputStream(uri)?.use { ReportExporter.writePdf(it, s.snapshot, a) } ?: error("Could not write file")
        }
    }

    /** POPIA right to erasure: database file, encryption key, password, settings and caches — then restart. */
    fun purgeAll() = viewModelScope.launch {
        container.eraser.eraseEverything()
        container.eraser.restart()
    }

    // ---------- App password ----------

    val passcodeKind: StateFlow<PasscodeKind?> = container.passcode.kind

    /**
     * Sets or changes the app password. When one exists, [current] must match first.
     * Returns a user-facing error, or null on success.
     */
    suspend fun setPasscode(current: String?, new: String, kind: PasscodeKind): String? = withContext(Dispatchers.Default) {
        PasscodeRules.validate(kind, new)?.let { return@withContext it }
        if (container.passcode.isSet) {
            when (val r = container.passcode.verify((current ?: "").toCharArray())) {
                VerifyResult.Success -> Unit
                is VerifyResult.Wrong -> return@withContext "Current ${if (passcodeKind.value == PasscodeKind.PIN) "PIN" else "password"} is incorrect"
                is VerifyResult.LockedOut -> return@withContext "Too many attempts — try again shortly"
            }
        }
        container.passcode.set(new.toCharArray(), kind)
        _messages.tryEmit("App ${if (kind == PasscodeKind.PIN) "PIN" else "password"} saved")
        null
    }

    suspend fun removePasscode(current: String): String? = withContext(Dispatchers.Default) {
        when (container.passcode.verify(current.toCharArray())) {
            VerifyResult.Success -> { container.passcode.clear(); _messages.tryEmit("App password removed"); null }
            is VerifyResult.Wrong -> "That's not right"
            is VerifyResult.LockedOut -> "Too many attempts — try again shortly"
        }
    }

    // ---------- POPIA consent ----------

    val consentHistory: StateFlow<List<ConsentRecord>> = repo.consentHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setStatementConsent(context: Context, granted: Boolean) {
        updateSettings(context) { it.copy(consentStatementUpload = granted) }
        launchSafe { repo.recordConsent(Privacy.Consent.STATEMENTS, granted, Privacy.VERSION) }
    }

    fun setAiConsent(context: Context, granted: Boolean) {
        updateSettings(context) { it.copy(consentAiInterpretation = granted) }
        launchSafe { repo.recordConsent(Privacy.Consent.AI, granted, Privacy.VERSION) }
    }
}

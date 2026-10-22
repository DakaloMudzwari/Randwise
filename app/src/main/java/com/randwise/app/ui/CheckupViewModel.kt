package com.randwise.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.randwise.app.AppContainer
import com.randwise.app.ai.Checkup
import com.randwise.app.ai.CheckupNote
import com.randwise.app.ai.CheckupRequest
import com.randwise.app.ai.ContextBuilder
import com.randwise.app.ai.LocalCheckup
import com.randwise.app.domain.Analysis
import com.randwise.app.domain.FinanceSnapshot
import com.randwise.app.domain.engine.BudgetPlanner
import com.randwise.app.domain.engine.ProposedLine
import com.randwise.app.domain.engine.SpendingReview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * Money check-up: three months of spending, explained, with a budget built from it.
 *
 * Facts and the baseline plan are computed on-device (SpendingReview, BudgetPlanner). With AI
 * interpretation on, Gemini explains them and may tailor the budget — its limits only survive the
 * planner's guard-rails. Without AI, or when the gateway fails, the on-device check-up is used.
 */
class CheckupViewModel(private val container: AppContainer) : ViewModel() {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    val checkup: StateFlow<Checkup?> = container.repository.checkup
        .map { e -> e?.let { runCatching { json.decodeFromString(Checkup.serializer(), it.json) }.getOrNull() } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun run(snapshot: FinanceSnapshot, analysis: Analysis) {
        if (_running.value) return
        _running.value = true
        _error.value = null
        val settings = container.preferences.settings.value
        viewModelScope.launch {
            runCatching {
                val profile = withContext(Dispatchers.Default) { SpendingReview.profile(snapshot, analysis.today) }
                if (profile == null || profile.daysOfData < SpendingReview.MIN_DAYS) {
                    _error.value = "Add at least two weeks of transactions — ideally import 3 months of bank statements — for a check-up."
                    return@runCatching
                }
                val baseline = BudgetPlanner.baseline(profile)
                val lastTx = snapshot.transactions.maxOfOrNull { it.id } ?: 0L
                fun local(note: String?) = LocalCheckup.assemble(
                    profile, baseline, lastTx, "LOCAL", note,
                    LocalCheckup.headline(profile), LocalCheckup.summary(profile),
                    LocalCheckup.insights(profile, analysis.behavior, baseline), LocalCheckup.suggestions(profile, baseline),
                )
                val result = if (settings.consentAiInterpretation && settings.gatewayUrl.isNotBlank()) {
                    runCatching {
                        val facts = ContextBuilder.checkup(profile, baseline, analysis.behavior, snapshot.profile?.currency ?: "ZAR")
                        val reply = container.coachClient.checkup(settings.gatewayUrl, CheckupRequest(facts))
                        val lines = BudgetPlanner.accept(baseline, reply.budget.map { ProposedLine(it.category, it.limitMinor, it.reason) }, profile.availableForBudget)
                        LocalCheckup.assemble(
                            profile, lines, lastTx, "GEMINI", reply.disclaimer.ifBlank { null },
                            reply.headline.trim().take(120).ifEmpty { LocalCheckup.headline(profile) },
                            reply.summary.trim().take(700).ifEmpty { LocalCheckup.summary(profile) },
                            reply.insights.take(6).map { it.clean() }.ifEmpty { LocalCheckup.insights(profile, analysis.behavior, lines) },
                            reply.suggestions.take(5).map { it.clean() }.ifEmpty { LocalCheckup.suggestions(profile, lines) },
                        )
                    }.getOrElse { local("AI is busy or unreachable right now — this check-up was done on your phone. Try again later for the AI version.") }
                } else {
                    local(null)
                }
                container.repository.saveCheckup(json.encodeToString(Checkup.serializer(), result), result.createdAt)
            }.onFailure { _error.value = "The check-up didn't finish. Please try again." }
            _running.value = false
        }
    }

    private fun CheckupNote.clean() = CheckupNote(title.trim().take(80), body.trim().take(400))
}

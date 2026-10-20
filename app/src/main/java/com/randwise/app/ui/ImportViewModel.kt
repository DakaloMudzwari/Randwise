package com.randwise.app.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.randwise.app.AppContainer
import com.randwise.app.data.ImportRow
import com.randwise.app.data.model.Account
import com.randwise.app.data.model.TxType
import com.randwise.app.importer.BalanceCheck
import com.randwise.app.importer.ExtractionMethod
import com.randwise.app.importer.PasswordRequiredException
import com.randwise.app.importer.StatementValidator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface ImportState {
    data object Idle : ImportState
    data class Working(val message: String) : ImportState
    data class NeedsPassword(val uri: Uri, val account: Account, val error: String?) : ImportState
    data class Review(
        val account: Account,
        val fileName: String,
        val method: ExtractionMethod,
        val check: BalanceCheck,
        val warnings: List<String>,
        val rows: List<ImportRow>,
    ) : ImportState {
        val newCount get() = rows.count { it.include && !it.isDuplicate }
        val duplicateCount get() = rows.count { it.isDuplicate }
        val possibleCount get() = rows.count { it.possibleDuplicate }
    }
    data class Done(val inserted: Int, val skipped: Int) : ImportState
    data class Failed(val message: String) : ImportState
}

/** Drives F03: extract -> parse -> validate -> stage with duplicate flags -> user review -> commit. */
class ImportViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow<ImportState>(ImportState.Idle)
    val state: StateFlow<ImportState> = _state.asStateFlow()

    fun start(uri: Uri, account: Account, password: String? = null) {
        _state.value = ImportState.Working("Reading statement…")
        viewModelScope.launch {
            try {
                val result = container.extractor.extract(uri, password)
                if (result.method == ExtractionMethod.PDF_OCR) _state.value = ImportState.Working("Scanned PDF detected — running on-device OCR…")
                val rows = container.repository.stageImport(account, result.statement)
                if (rows.isEmpty()) {
                    _state.value = ImportState.Failed(result.statement.warnings.firstOrNull() ?: "No transactions found in ${result.fileName}.")
                    return@launch
                }
                _state.value = ImportState.Review(account, result.fileName, result.method, StatementValidator.validate(result.statement), result.statement.warnings, rows)
            } catch (e: PasswordRequiredException) {
                _state.value = ImportState.NeedsPassword(uri, account, if (password != null) "Incorrect password" else null)
            } catch (e: Exception) {
                _state.value = ImportState.Failed(e.message ?: "Could not read this file.")
            }
        }
    }

    private fun updateRow(index: Int, f: (ImportRow) -> ImportRow) = _state.update { s ->
        if (s is ImportState.Review) s.copy(rows = s.rows.map { if (it.index == index) f(it) else it }) else s
    }

    fun toggle(index: Int) = updateRow(index) { if (it.isDuplicate) it else it.copy(include = !it.include) }
    fun setCategory(index: Int, categoryId: Long?) = updateRow(index) { it.copy(categoryId = categoryId) }
    fun setType(index: Int, type: TxType) = updateRow(index) { it.copy(type = type, categoryId = if (type.isInternal) null else it.categoryId) }

    fun commit(rememberRules: Boolean) {
        val s = _state.value as? ImportState.Review ?: return
        _state.value = ImportState.Working("Saving…")
        viewModelScope.launch {
            runCatching { container.repository.commitImport(s.account.id, s.rows, rememberRules) }
                .onSuccess { _state.value = ImportState.Done(it, s.rows.size - it) }
                .onFailure { _state.value = ImportState.Failed(it.message ?: "Import failed") }
        }
    }

    fun reset() { _state.value = ImportState.Idle }
}

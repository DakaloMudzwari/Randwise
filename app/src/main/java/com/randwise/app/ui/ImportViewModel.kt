package com.randwise.app.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.randwise.app.AppContainer
import com.randwise.app.data.ImportRow
import com.randwise.app.data.model.Account
import com.randwise.app.data.model.TxType
import com.randwise.app.importer.BalanceCheck
import com.randwise.app.importer.CsvMapping
import com.randwise.app.importer.CsvPreview
import com.randwise.app.importer.CsvStatementParser
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
    /** CSV whose columns couldn't be detected (or the user wants to fix them): pick them by hand. */
    data class NeedsMapping(
        val account: Account,
        val fileName: String,
        val rawCsv: String,
        val preview: CsvPreview,
        val reason: String?,
    ) : ImportState
    data class Review(
        val account: Account,
        val fileName: String,
        val method: ExtractionMethod,
        val check: BalanceCheck,
        val warnings: List<String>,
        val rows: List<ImportRow>,
        val rawCsv: String? = null,
    ) : ImportState {
        val newCount get() = rows.count { it.include && !it.isDuplicate }
        val duplicateCount get() = rows.count { it.isDuplicate }
        val possibleCount get() = rows.count { it.possibleDuplicate }
    }
    data class Done(val inserted: Int, val skipped: Int, val balanceCorrection: Long = 0, val matchedClosing: Long? = null) : ImportState
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
                    // A CSV we couldn't read automatically: let the user point at the columns instead of giving up.
                    if (result.rawCsv != null) {
                        _state.value = ImportState.NeedsMapping(account, result.fileName, result.rawCsv, CsvStatementParser.preview(result.rawCsv), "We couldn't recognise this bank's columns automatically.")
                    } else {
                        _state.value = ImportState.Failed(result.statement.warnings.firstOrNull() ?: "No transactions found in ${result.fileName}.")
                    }
                    return@launch
                }
                _state.value = ImportState.Review(account, result.fileName, result.method, StatementValidator.validate(result.statement), result.statement.warnings, rows, result.rawCsv)
            } catch (e: PasswordRequiredException) {
                _state.value = ImportState.NeedsPassword(uri, account, if (password != null) "Incorrect password" else null)
            } catch (e: java.io.IOException) {
                // PDF/CSV libraries report damaged or empty files with technical messages ("Unable to load the document!").
                _state.value = ImportState.Failed("This file is empty or damaged. Download the statement from your bank again and retry.")
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
            // Only a statement whose balances add up is trusted to correct the account balance.
            val closing = s.check.statedClosing.takeIf { s.check.balanced == true }
            runCatching { container.repository.commitImport(s.account.id, s.rows, rememberRules, closing) }
                .onSuccess { _state.value = ImportState.Done(it.inserted, s.rows.size - it.inserted, it.balanceCorrection, it.matchedClosing) }
                .onFailure { _state.value = ImportState.Failed(it.message ?: "Import failed") }
        }
    }

    /** From the review screen: re-open the column mapping for this CSV. */
    fun remap() {
        val s = _state.value as? ImportState.Review ?: return
        val raw = s.rawCsv ?: return
        _state.value = ImportState.NeedsMapping(s.account, s.fileName, raw, CsvStatementParser.preview(raw), null)
    }

    /** Live count for the mapping screen so the user sees whether their choice works. */
    fun previewCount(raw: String, mapping: CsvMapping): Int = runCatching { CsvStatementParser.parseWith(raw, mapping).lines.size }.getOrDefault(0)

    fun applyMapping(mapping: CsvMapping) {
        val s = _state.value as? ImportState.NeedsMapping ?: return
        _state.value = ImportState.Working("Reading with your columns…")
        viewModelScope.launch {
            val statement = CsvStatementParser.parseWith(s.rawCsv, mapping)
            val rows = container.repository.stageImport(s.account, statement)
            _state.value = if (rows.isEmpty()) s.copy(reason = "No transactions with those columns — try a different date or amount column.")
            else ImportState.Review(s.account, s.fileName, ExtractionMethod.CSV, StatementValidator.validate(statement), statement.warnings, rows, s.rawCsv)
        }
    }

    fun reset() { _state.value = ImportState.Idle }
}

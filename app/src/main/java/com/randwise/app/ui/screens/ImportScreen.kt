package com.randwise.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.randwise.app.core.Money
import com.randwise.app.data.ImportRow
import com.randwise.app.data.model.Bucket
import com.randwise.app.data.model.Category
import com.randwise.app.data.model.TxType
import com.randwise.app.importer.CsvMapping
import com.randwise.app.importer.ExtractionMethod
import com.randwise.app.ui.FinanceViewModel
import com.randwise.app.ui.ImportState
import com.randwise.app.ui.ImportViewModel
import com.randwise.app.ui.components.EmptyState
import com.randwise.app.ui.theme.RW

/** F03 import flow: pick file -> parse -> balance check -> duplicate review -> category mapping -> commit. */
@Composable
fun ImportScreen(vm: FinanceViewModel, importVm: ImportViewModel, accountId: Long, onDone: () -> Unit) {
    val fin by vm.state.collectAsStateWithLifecycle()
    val state by importVm.state.collectAsStateWithLifecycle()
    val account = fin.snapshot.accounts.firstOrNull { it.id == accountId }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && account != null) importVm.start(uri, account) else if (state is ImportState.Idle) onDone()
    }
    val mimeTypes = arrayOf("text/csv", "text/comma-separated-values", "application/csv", "application/pdf", "text/plain", "application/vnd.ms-excel")

    LaunchedEffect(Unit) { if (state is ImportState.Idle || state is ImportState.Done) { importVm.reset(); picker.launch(mimeTypes) } }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton({ importVm.reset(); onDone() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
            Text("Import into ${account?.name ?: "account"}", style = MaterialTheme.typography.titleLarge)
        }
        when (val s = state) {
            is ImportState.Idle -> EmptyState("Choose a statement", "CSV or PDF from your bank's app or internet banking.")
            is ImportState.Working -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                CircularProgressIndicator(color = RW.colors.positive)
                Spacer(Modifier.height(16.dp))
                Text(s.message)
            }
            is ImportState.NeedsPassword -> PasswordPrompt(s.error) { pw -> importVm.start(s.uri, s.account, pw) }
            is ImportState.Failed -> Column(Modifier.padding(24.dp)) {
                EmptyState("Couldn't import", s.message)
                Button({ importVm.reset(); picker.launch(mimeTypes) }, Modifier.fillMaxWidth()) { Text("Choose another file") }
            }
            is ImportState.Done -> Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(Icons.Rounded.CheckCircle, null, tint = RW.colors.positive, modifier = Modifier.size(64.dp))
                Spacer(Modifier.height(12.dp))
                Text("${s.inserted} transactions imported", style = MaterialTheme.typography.titleLarge)
                if (s.skipped > 0) Text("${s.skipped} skipped (duplicates or excluded)", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(24.dp))
                Button({ importVm.reset(); onDone() }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)) { Text("Done") }
                TextButton({ importVm.reset(); picker.launch(mimeTypes) }) { Text("Import another") }
            }
            is ImportState.Review -> ReviewList(s, fin.snapshot.categories, importVm)
            is ImportState.NeedsMapping -> ColumnMapper(s, importVm)
        }
    }
}

@Composable
private fun PasswordPrompt(error: String?, onSubmit: (String) -> Unit) {
    var pw by remember { mutableStateOf("") }
    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("This statement is password protected", style = MaterialTheme.typography.titleMedium)
        Text("Banks usually use your ID number. The password is used once to open the file and is never stored.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            pw, { pw = it }, label = { Text("PDF password") }, singleLine = true, visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), isError = error != null,
            supportingText = error?.let { { Text(it) } }, modifier = Modifier.fillMaxWidth(),
        )
        Button({ onSubmit(pw) }, enabled = pw.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text("Open") }
    }
}

@Composable
private fun ReviewList(s: ImportState.Review, categories: List<Category>, importVm: ImportViewModel) {
    var rememberRules by remember { mutableStateOf(true) }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            item {
                val (icon, color) = when (s.check.balanced) {
                    true -> Icons.Rounded.CheckCircle to RW.colors.positive
                    false -> Icons.Rounded.ErrorOutline to RW.colors.danger
                    null -> Icons.Rounded.HelpOutline to RW.colors.warning
                }
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.UploadFile, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(s.fileName, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                        }
                        Text(
                            "Read via " + when (s.method) {
                                ExtractionMethod.CSV -> "CSV"; ExtractionMethod.PDF_TEXT -> "PDF text"
                                ExtractionMethod.PDF_OCR -> "on-device OCR (check amounts carefully)"; ExtractionMethod.PLAIN_TEXT -> "text"
                            },
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(s.check.message, style = MaterialTheme.typography.bodySmall, color = color)
                        }
                        Text(
                            "${s.rows.size} rows · ${s.newCount} new · ${s.duplicateCount} already imported" + if (s.possibleCount > 0) " · ${s.possibleCount} possible duplicates (unticked)" else "",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        s.warnings.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = RW.colors.warning) }
                        if (s.rawCsv != null) TextButton({ importVm.remap() }, contentPadding = PaddingValues(0.dp)) {
                            Text("Amounts or dates look wrong? Choose the columns yourself", color = RW.colors.positive)
                        }
                    }
                }
            }
            items(s.rows, key = { it.index }) { row ->
                ReviewRow(row, categories, importVm)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
        Surface(tonalElevation = 3.dp) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Remember my category choices", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Switch(rememberRules, { rememberRules = it })
                }
                Button(
                    { importVm.commit(rememberRules) }, enabled = s.newCount > 0, modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                ) { Text("Import ${s.newCount} transactions") }
            }
        }
    }
}

@Composable
private fun ReviewRow(row: ImportRow, categories: List<Category>, importVm: ImportViewModel) {
    var typeMenu by remember { mutableStateOf(false) }
    var catMenu by remember { mutableStateOf(false) }
    val cat = categories.firstOrNull { it.id == row.categoryId }
    val dim = if (row.include) 1f else 0.5f
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(row.include, { importVm.toggle(row.index) }, enabled = !row.isDuplicate)
        Column(Modifier.weight(1f)) {
            Text(row.description, style = MaterialTheme.typography.bodyMedium, maxLines = 1, color = MaterialTheme.colorScheme.onSurface.copy(alpha = dim))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(row.date.toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
                TextButton({ typeMenu = true }, contentPadding = PaddingValues(horizontal = 4.dp)) {
                    Text(row.type.name.lowercase().replace('_', ' '), style = MaterialTheme.typography.labelSmall)
                }
                DropdownMenu(typeMenu, { typeMenu = false }) {
                    TxType.entries.forEach { t ->
                        DropdownMenuItem({ Text(t.name.lowercase().replace('_', ' ')) }, { importVm.setType(row.index, t); typeMenu = false })
                    }
                }
                if (!row.type.isInternal) {
                    TextButton({ catMenu = true }, contentPadding = PaddingValues(horizontal = 4.dp)) {
                        Text(cat?.name ?: "Set category", style = MaterialTheme.typography.labelSmall, color = if (cat == null) RW.colors.warning else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    DropdownMenu(catMenu, { catMenu = false }) {
                        val relevant = if (row.type == TxType.INCOME) categories.filter { it.bucket == Bucket.INCOME } else categories.filter { it.bucket != Bucket.INCOME }
                        relevant.forEach { c -> DropdownMenuItem({ Text(c.name) }, { importVm.setCategory(row.index, c.id); catMenu = false }) }
                    }
                }
            }
            if (row.isDuplicate) Text("Already imported", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else if (row.possibleDuplicate) Text("Possible duplicate — same amount already recorded within a day",style = MaterialTheme.typography.labelSmall, color = RW.colors.warning)
        }
        val signed = if (row.type.balanceSign > 0) row.amountMinor else -row.amountMinor
        Text(
            Money.format(signed, showSign = true),
            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
            color = (if (signed > 0) RW.colors.positive else MaterialTheme.colorScheme.onSurface).copy(alpha = dim),
        )
    }
}

/**
 * Manual CSV column mapping — the fallback when a bank's export layout isn't recognised.
 * Shows a sample of the file and a live count of transactions found with the chosen columns.
 */
@Composable
private fun ColumnMapper(s: ImportState.NeedsMapping, importVm: ImportViewModel) {
    val p = s.preview
    val sug = p.suggested
    var headerRow by remember(s) { mutableStateOf(sug?.headerRow ?: -1) }
    var date by remember(s) { mutableStateOf(sug?.date ?: 0) }
    var desc by remember(s) { mutableStateOf(sug?.description ?: 1.coerceAtMost(p.columnCount - 1)) }
    var split by remember(s) { mutableStateOf(sug != null && sug.amount == null) }
    var amount by remember(s) { mutableStateOf(sug?.amount ?: 2.coerceAtMost(p.columnCount - 1)) }
    var debit by remember(s) { mutableStateOf(sug?.debit ?: 2.coerceAtMost(p.columnCount - 1)) }
    var credit by remember(s) { mutableStateOf(sug?.credit ?: 3.coerceAtMost(p.columnCount - 1)) }
    var balance by remember(s) { mutableStateOf(sug?.balance) }

    val header = p.rows.getOrNull(headerRow)
    fun colName(i: Int) = "Column ${i + 1}" + (header?.getOrNull(i)?.trim()?.takeIf { it.isNotEmpty() }?.let { " · $it" } ?: "")
    val cols = (0 until p.columnCount).toList()
    val mapping = CsvMapping(headerRow, date, desc, if (split) null else amount, if (split) debit else null, if (split) credit else null, balance)
    val found = remember(mapping) { importVm.previewCount(s.rawCsv, mapping) }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Match the columns", style = MaterialTheme.typography.headlineSmall)
            Text(s.reason ?: "Tell RandWise which column holds each value.", color = RW.colors.warning.takeIf { s.reason != null } ?: MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            // Sample of the raw file, monospaced so columns are easy to compare.
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("Your file", style = MaterialTheme.typography.labelMedium)
                    p.rows.take(6).forEachIndexed { i, row ->
                        Text(
                            "${i + 1}: " + row.joinToString(" | ") { it.trim().take(18) },
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                            maxLines = 1, color = if (i == headerRow) RW.colors.positive else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
        item {
            val headerOptions = listOf(-1) + (0 until minOf(10, p.rows.size))
            Dropdown("Header row", headerOptions, headerRow, { if (it < 0) "No header row" else "Row ${it + 1}" }, { headerRow = it })
        }
        item { Dropdown("Date", cols, date, ::colName, { date = it }) }
        item { Dropdown("Description", cols, desc, ::colName, { desc = it }) }
        item {
            com.randwise.app.ui.components.SegmentedControl(listOf("One amount column", "Debit & credit"), if (split) 1 else 0, { split = it == 1 })
        }
        if (split) {
            item { Dropdown("Money out (debit)", cols, debit, ::colName, { debit = it }) }
            item { Dropdown("Money in (credit)", cols, credit, ::colName, { credit = it }) }
        } else {
            item { Dropdown("Amount (+ in, − out)", cols, amount, ::colName, { amount = it }) }
        }
        item {
            val balOptions = listOf<Int?>(null) + cols
            Dropdown("Running balance (optional)", balOptions, balance, { it?.let(::colName) ?: "None" }, { balance = it })
        }
        item {
            Text(
                if (found > 0) "Found $found transactions with these columns" else "No transactions found yet — adjust the columns",
                color = if (found > 0) RW.colors.positive else RW.colors.warning, style = MaterialTheme.typography.titleSmall,
            )
        }
        item {
            Button({ importVm.applyMapping(mapping) }, enabled = found > 0, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)) {
                Text("Use these columns")
            }
        }
    }
}

package com.randwise.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.randwise.app.core.Money
import com.randwise.app.data.model.Bucket
import com.randwise.app.data.model.Transaction
import com.randwise.app.data.model.TxType
import com.randwise.app.ui.FinanceViewModel
import com.randwise.app.ui.Gutter
import com.randwise.app.ui.components.EmptyState
import com.randwise.app.ui.components.Eyebrow
import com.randwise.app.ui.components.GlassCard
import com.randwise.app.ui.components.HeroAmount
import com.randwise.app.ui.components.RWTextField
import com.randwise.app.ui.components.pressable
import com.randwise.app.ui.gutter
import com.randwise.app.ui.theme.MoneyStyle
import com.randwise.app.ui.theme.Palette
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Manual ledger: every transaction, searchable, editable and deletable. accountId 0 = all. */
@Composable
fun LedgerScreen(vm: FinanceViewModel, accountId: Long, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var editing by remember { mutableStateOf<Transaction?>(null) }
    val s = state.snapshot
    val cats = s.categories.associateBy { it.id }
    val account = s.accounts.firstOrNull { it.id == accountId }
    val balance = state.analysis?.accountBalances?.firstOrNull { it.account.id == accountId }?.balance
    val txs = s.transactions.filter { (accountId == 0L || it.accountId == accountId) && (query.isBlank() || it.merchant.contains(query, true)) }
    val grouped = txs.groupBy { it.date }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.gutter().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(Palette.Glass).border(1.dp, Palette.Hairline, CircleShape).pressable(onBack),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Palette.Text) }
            Spacer(Modifier.width(14.dp))
            Column {
                Eyebrow(if (account == null) "Ledger" else account.type.name.replace('_', ' '), Palette.Mint)
                Text(account?.name ?: "All activity", style = MaterialTheme.typography.titleLarge)
            }
        }
        if (balance != null) HeroAmount(balance, Modifier.gutter().padding(bottom = 12.dp), size = 36)
        Row(
            Modifier.gutter().fillMaxWidth().height(50.dp).clip(CircleShape).background(Palette.Glass).border(1.dp, Palette.Hairline, CircleShape).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Search, null, tint = Palette.TextFaint)
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) Text("Search transactions", color = Palette.TextFaint)
                BasicTextField(query, { query = it }, singleLine = true, textStyle = MaterialTheme.typography.bodyLarge.copy(color = Palette.Text), cursorBrush = SolidColor(Palette.Mint), modifier = Modifier.fillMaxWidth())
            }
        }
        if (txs.isEmpty()) EmptyState("Nothing here yet", "Transactions you add or import will appear here.", Modifier.padding(top = 40.dp))
        LazyColumn(contentPadding = PaddingValues(start = Gutter, end = Gutter, top = 16.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.navigationBarsPadding()) {
            grouped.forEach { (day, list) ->
                item(key = "d$day") {
                    val net = list.sumOf { if (it.type.isInternal) 0 else it.signedAmountMinor }
                    Column {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp)) {
                            Eyebrow(LocalDate.ofEpochDay(day).format(HDR), Palette.TextDim)
                            Spacer(Modifier.weight(1f))
                            Text(Money.format(net, showSign = true), style = MaterialTheme.typography.labelMedium.merge(MoneyStyle), color = if (net > 0) Palette.Mint else Palette.TextDim)
                        }
                        GlassCard(padding = 6.dp) { list.forEach { t -> TransactionRow(t, t.categoryId?.let { cats[it] }) { editing = t } } }
                    }
                }
            }
        }
    }

    editing?.let { t ->
        var cat by remember(t.id) { mutableStateOf(t.categoryId) }
        var note by remember(t.id) { mutableStateOf(t.note) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(t.merchant, maxLines = 2) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    HeroAmount(t.signedAmountMinor, size = 30, color = if (t.signedAmountMinor > 0) Palette.Mint else Palette.Text)
                    Text("${LocalDate.ofEpochDay(t.date)} · ${t.type.name.lowercase().replace('_', ' ')}", color = Palette.TextDim)
                    if (!t.type.isInternal) {
                        val relevant = if (t.type == TxType.INCOME) s.categories.filter { it.bucket == Bucket.INCOME } else s.categories.filter { it.bucket != Bucket.INCOME }
                        CategoryDropdown(relevant, cat, { cat = it })
                    }
                    RWTextField(note, { note = it.take(120) }, "Note")
                    if (t.transferGroupId != null) Text("Deleting removes both sides of this transfer.", style = MaterialTheme.typography.bodySmall, color = Palette.TextDim)
                }
            },
            confirmButton = { TextButton({ vm.updateTransaction(t.copy(categoryId = cat, note = note)); editing = null }) { Text("Save") } },
            dismissButton = { TextButton({ vm.deleteTransaction(t); editing = null }) { Text("Delete", color = Palette.Coral) } },
        )
    }
}

private val HDR = DateTimeFormatter.ofPattern("EEE d MMM")

package com.randwise.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.randwise.app.core.Money
import com.randwise.app.data.model.Account
import com.randwise.app.data.model.Bucket
import com.randwise.app.data.model.TxType
import com.randwise.app.ui.FinanceViewModel
import com.randwise.app.ui.components.Eyebrow
import com.randwise.app.ui.components.HeroAmount
import com.randwise.app.ui.components.MoneyField
import com.randwise.app.ui.components.PrimaryButton
import com.randwise.app.ui.components.RWTextField
import com.randwise.app.ui.components.SegmentedControl
import com.randwise.app.ui.theme.Palette
import java.time.LocalDate

private val modes = listOf("Expense", "Income", "Refund", "Transfer")

/** Quick Add: manual ledger entry for F02. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionSheet(vm: FinanceViewModel, onDismiss: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val accounts = state.snapshot.accounts.filter { !it.archived }
    val categories = state.snapshot.categories
    var mode by remember { mutableIntStateOf(0) }
    var account by remember { mutableStateOf(accounts.firstOrNull()) }
    var toAccount by remember { mutableStateOf<Account?>(accounts.getOrNull(1)) }
    var amount by remember { mutableStateOf("") }
    var merchant by remember { mutableStateOf("") }
    var categoryId by remember { mutableStateOf<Long?>(null) }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var note by remember { mutableStateOf("") }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val transfer = mode == 3

    val parsed = Money.parse(amount) ?: 0
    val valid = parsed > 0 && account != null && (!transfer || (toAccount != null && toAccount?.id != account?.id))
    val accent = when (mode) { 1, 2 -> Palette.Mint; 3 -> Palette.Sky; else -> Palette.Text }

    ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = sheet,
        containerColor = Palette.Navy900, shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).imePadding().navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Eyebrow("New ${modes[mode].lowercase()}", Palette.Mint)
                Spacer(Modifier.height(6.dp))
                HeroAmount(if (mode == 0) -parsed else parsed, size = 46, color = if (parsed > 0) accent else Palette.TextFaint)
            }
            SegmentedControl(modes, mode, { mode = it; categoryId = null })
            MoneyField(amount, { amount = it }, "Amount")
            Dropdown(if (transfer) "From account" else "Account", accounts, account, { it.name }, { account = it })
            if (transfer) {
                Dropdown("To account", accounts.filter { it.id != account?.id }, toAccount, { it.name }, { toAccount = it })
                Text(
                    "Moving money between your own accounts — or paying your card — never counts as income or spending.",
                    style = MaterialTheme.typography.bodySmall, color = Palette.TextDim,
                )
            } else {
                RWTextField(merchant, { merchant = it.take(60) }, if (mode == 1) "Source" else "Merchant or description")
                val relevant = if (mode == 1) categories.filter { it.bucket == Bucket.INCOME } else categories.filter { it.bucket != Bucket.INCOME }
                CategoryDropdown(relevant, categoryId, { categoryId = it })
            }
            DateField(date, { date = it })
            RWTextField(note, { note = it.take(120) }, "Note (optional)")
            PrimaryButton(
                "Save ${modes[mode].lowercase()}",
                {
                    val acc = account ?: return@PrimaryButton
                    when (mode) {
                        3 -> vm.addTransfer(acc.id, toAccount!!.id, parsed, date, note)
                        0 -> vm.addTransaction(acc.id, TxType.EXPENSE, parsed, date, merchant, categoryId, note)
                        1 -> vm.addTransaction(acc.id, TxType.INCOME, parsed, date, merchant, categoryId, note)
                        else -> vm.addTransaction(acc.id, TxType.REFUND, parsed, date, merchant, categoryId, note)
                    }
                    onDismiss()
                },
                enabled = valid,
                modifier = Modifier.padding(bottom = 20.dp),
            )
        }
    }
}

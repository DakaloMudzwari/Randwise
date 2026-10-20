package com.randwise.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.randwise.app.core.Money
import com.randwise.app.data.model.AccountType
import com.randwise.app.domain.AccountBalance
import com.randwise.app.ui.FinanceViewModel
import com.randwise.app.ui.Gutter
import com.randwise.app.ui.NavClearance
import com.randwise.app.ui.components.Eyebrow
import com.randwise.app.ui.components.GhostButton
import com.randwise.app.ui.components.GlassCard
import com.randwise.app.ui.components.HeroAmount
import com.randwise.app.ui.components.IconBadge
import com.randwise.app.ui.components.MoneyField
import com.randwise.app.ui.components.RWTextField
import com.randwise.app.ui.components.SectionHeader
import com.randwise.app.ui.components.SegmentedControl
import com.randwise.app.ui.components.enterAnimation
import com.randwise.app.ui.components.pressable
import com.randwise.app.ui.gutter
import com.randwise.app.ui.theme.Palette

@Composable
fun AccountsScreen(vm: FinanceViewModel, onImport: (Long) -> Unit, onLedger: (Long) -> Unit, onAdd: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val a = state.analysis ?: return
    var addAccount by remember { mutableStateOf(false) }
    var importPick by remember { mutableStateOf(false) }
    var consentAsk by remember { mutableStateOf(false) }
    val activeAccounts = state.snapshot.accounts.filter { !it.archived }
    fun startImport() {
        if (activeAccounts.size == 1) onImport(activeAccounts.first().id) else importPick = true
    }
    fun requestImport() = if (settings.consentStatementUpload) startImport() else consentAsk = true

    LazyColumn(contentPadding = PaddingValues(top = 12.dp, bottom = NavClearance), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item {
            Column(Modifier.gutter()) {
                Eyebrow("Net worth across accounts", Palette.Mint)
                Spacer(Modifier.height(6.dp))
                HeroAmount(a.accountBalances.sumOf { it.balance }, size = 40)
                Text("Spendable cash ${Money.formatWhole(a.liquidBalance)}", style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim)
            }
        }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = Gutter), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(a.accountBalances.sortedBy { it.account.type.ordinal }, key = { it.account.id }) { ab -> BankCard(ab, Modifier.enterAnimation(ab.account.id.toInt())) { onLedger(ab.account.id) } }
                item { AddCard { addAccount = true } }
            }
        }
        item {
            Row(Modifier.gutter(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GhostButton("Add or transfer", onAdd, Modifier.weight(1f), icon = Icons.Rounded.SwapHoriz)
                GhostButton("All activity", { onLedger(0) }, Modifier.weight(1f), icon = Icons.Rounded.AccountBalance)
            }
        }
        item { SectionHeader("Import a statement", Modifier.gutter(), eyebrow = "CSV · PDF · scanned PDF") }
        item { DropZone(Modifier.gutter()) { requestImport() } }
        item {
            GlassCard(Modifier.gutter()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Rounded.Lock, Palette.Mint)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Read on your phone, never uploaded", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Works with FNB, Standard Bank, Absa, Nedbank, Capitec, TymeBank, Discovery and more. Re-importing a statement never creates duplicates.",
                            style = MaterialTheme.typography.bodySmall, color = Palette.TextDim,
                        )
                    }
                }
            }
        }
    }

    if (consentAsk) {
        AlertDialog(
            onDismissRequest = { consentAsk = false },
            icon = { Icon(Icons.Rounded.Lock, null, tint = Palette.Mint) },
            title = { Text("Allow statement processing?") },
            text = {
                Text(
                    "RandWise reads the statement you choose to extract dates, descriptions and amounts. Processing happens entirely " +
                        "on this phone and is stored encrypted. You can withdraw consent and erase everything in the Vault.",
                )
            },
            confirmButton = { TextButton({ vm.setStatementConsent(context, true); consentAsk = false; startImport() }) { Text("I agree") } },
            dismissButton = { TextButton({ consentAsk = false }) { Text("Not now") } },
        )
    }
    if (importPick) {
        AlertDialog(
            onDismissRequest = { importPick = false },
            title = { Text("Import into which account?") },
            text = {
                Column {
                    activeAccounts.forEach { acc ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { importPick = false; onImport(acc.id) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconBadge(iconFor(acc.type), Palette.Mint, size = 34.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(acc.name, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = { TextButton({ importPick = false }) { Text("Cancel") } },
        )
    }
    if (addAccount) AddAccountDialog(onDismiss = { addAccount = false }) { name, type, opening, include ->
        vm.addAccount(name, type, opening, include); addAccount = false
    }
}

private fun cardBrush(t: AccountType): Brush = when (t) {
    AccountType.CHEQUE -> Brush.linearGradient(listOf(Color(0xFF0E6E55), Color(0xFF0B3D4F), Color(0xFF0A1F3D)))
    AccountType.SAVINGS -> Brush.linearGradient(listOf(Color(0xFF1C7A8C), Color(0xFF1D4E89), Color(0xFF0E2140)))
    AccountType.CREDIT_CARD -> Brush.linearGradient(listOf(Color(0xFF5B3FA8), Color(0xFF2A2C6B), Color(0xFF0B1A33)))
    AccountType.CASH -> Brush.linearGradient(listOf(Color(0xFF8A6A1C), Color(0xFF4A3D1F), Color(0xFF14213D)))
}

fun iconFor(t: AccountType): ImageVector = when (t) {
    AccountType.CHEQUE -> Icons.Rounded.AccountBalance
    AccountType.SAVINGS -> Icons.Rounded.Savings
    AccountType.CREDIT_CARD -> Icons.Rounded.CreditCard
    AccountType.CASH -> Icons.Rounded.Payments
}

/** Account rendered as a physical card: gradient, light sheen, chip, balance. */
@Composable
private fun BankCard(ab: AccountBalance, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(28.dp)
    Box(
        modifier.width(300.dp).height(186.dp).pressable(onClick).clip(shape).background(cardBrush(ab.account.type))
            .border(1.dp, Color.White.copy(alpha = 0.14f), shape),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Color.White.copy(alpha = 0.07f), size.width * 0.55f, Offset(size.width * 1.02f, -size.height * 0.1f))
            drawCircle(Color.White.copy(alpha = 0.05f), size.width * 0.4f, Offset(size.width * 0.95f, size.height * 1.15f))
            drawRect(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.10f), Color.Transparent), Offset.Zero, Offset(size.width * 0.6f, size.height)))
        }
        Column(Modifier.fillMaxSize().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("RANDWISE", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.weight(1f))
                Icon(iconFor(ab.account.type), null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.height(14.dp))
            // EMV-style chip.
            Box(
                Modifier.size(38.dp, 28.dp).clip(RoundedCornerShape(7.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFFE8D9A8), Color(0xFFB59A55)))),
            )
            Spacer(Modifier.weight(1f))
            HeroAmount(ab.balance, size = 28, color = Color.White)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(ab.account.name, style = MaterialTheme.typography.titleSmall, color = Color.White.copy(alpha = 0.9f), modifier = Modifier.weight(1f), maxLines = 1)
                Text(ab.account.type.name.replace('_', ' ').lowercase(), style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.6f))
            }
        }
    }
}

@Composable
private fun AddCard(onClick: () -> Unit) {
    Column(
        Modifier.width(140.dp).height(186.dp).pressable(onClick).drawBehind {
            drawRoundRect(Palette.TextFaint, style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))), cornerRadius = CornerRadius(28.dp.toPx()))
        },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Rounded.Add, null, tint = Palette.Mint, modifier = Modifier.size(30.dp))
        Spacer(Modifier.height(6.dp))
        Text("Add account", style = MaterialTheme.typography.titleSmall, color = Palette.TextDim)
    }
}

@Composable
private fun DropZone(modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.fillMaxWidth().height(190.dp).pressable(onClick)
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.verticalGradient(listOf(Palette.Emerald.copy(alpha = 0.10f), Color.Transparent)))
            .drawBehind {
                drawRoundRect(Palette.Mint.copy(alpha = 0.6f), style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 12f))), cornerRadius = CornerRadius(28.dp.toPx()))
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(Palette.Mint.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.UploadFile, null, tint = Palette.Mint, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text("Drop in a bank statement", style = MaterialTheme.typography.titleMedium)
        Text("Tap to choose a CSV or PDF", style = MaterialTheme.typography.bodySmall, color = Palette.TextDim, textAlign = TextAlign.Center)
    }
}

@Composable
private fun AddAccountDialog(onDismiss: () -> Unit, onSave: (String, AccountType, Long, Boolean) -> Unit) {
    val types = AccountType.entries
    var name by remember { mutableStateOf("") }
    var type by remember { mutableIntStateOf(1) }
    var opening by remember { mutableStateOf("") }
    var include by remember { mutableStateOf(false) }
    val t = types[type]
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add account") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                RWTextField(name, { name = it.take(40) }, "Nickname", supporting = "Don't enter account numbers")
                SegmentedControl(listOf("Cheque", "Save", "Card", "Cash"), type, { type = it; include = it == 0 || it == 3 })
                MoneyField(opening, { opening = it }, if (t == AccountType.CREDIT_CARD) "Amount owed" else "Current balance")
                if (t != AccountType.CREDIT_CARD) Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Count towards safe-to-spend", Modifier.weight(1f))
                    Switch(include, { include = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                {
                    val amt = Money.parse(opening.ifBlank { "0" }) ?: 0
                    // A card balance owed is a negative asset.
                    onSave(name.trim(), t, if (t == AccountType.CREDIT_CARD) -kotlin.math.abs(amt) else amt, include && t != AccountType.CREDIT_CARD)
                },
                enabled = name.isNotBlank(),
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

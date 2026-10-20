package com.randwise.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.randwise.app.core.Money
import com.randwise.app.data.model.Account
import com.randwise.app.data.model.AccountType
import com.randwise.app.data.model.BillFrequency
import com.randwise.app.data.model.Bucket
import com.randwise.app.domain.FinanceAnalyzer
import com.randwise.app.domain.PayCycle
import com.randwise.app.ui.components.SegmentedControl
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableIntStateOf
import java.time.LocalDate
import com.randwise.app.data.model.Category
import com.randwise.app.data.model.Debt
import com.randwise.app.data.model.Goal
import com.randwise.app.data.model.RecurringBill
import com.randwise.app.domain.MerchantText
import com.randwise.app.domain.engine.BudgetLine
import com.randwise.app.domain.engine.BudgetStatus
import com.randwise.app.domain.engine.DebtPlan
import com.randwise.app.domain.engine.DebtPlanner
import com.randwise.app.domain.engine.DebtStrategy
import com.randwise.app.ui.FinanceViewModel
import com.randwise.app.ui.Gutter
import com.randwise.app.ui.NavClearance
import com.randwise.app.ui.components.ColorDot
import com.randwise.app.ui.components.Eyebrow
import com.randwise.app.ui.components.GlassCard
import com.randwise.app.ui.components.GlowBar
import com.randwise.app.ui.components.IconBadge
import com.randwise.app.ui.components.MoneyField
import com.randwise.app.ui.components.MoneyText
import com.randwise.app.ui.components.Pill
import com.randwise.app.ui.components.RWTextField
import com.randwise.app.ui.components.RingProgress
import com.randwise.app.ui.components.SectionHeader
import com.randwise.app.ui.components.StackedBar
import com.randwise.app.ui.components.BarSegment
import com.randwise.app.ui.components.categoryColor
import com.randwise.app.ui.components.enterAnimation
import com.randwise.app.ui.components.pressable
import com.randwise.app.ui.components.statusColor
import com.randwise.app.ui.theme.Fonts
import com.randwise.app.ui.theme.MoneyStyle
import com.randwise.app.ui.theme.Palette
import com.randwise.app.ui.theme.RWBrush
import java.time.format.DateTimeFormatter

private val D = DateTimeFormatter.ofPattern("d MMM")

@Composable
fun BudgetScreen(vm: FinanceViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val a = state.analysis ?: return
    val s = state.snapshot
    var editBudget by remember { mutableStateOf<Category?>(null) }
    var pickCategory by remember { mutableStateOf(false) }
    var editBill by remember { mutableStateOf<RecurringBill?>(null) }
    var editGoal by remember { mutableStateOf<Goal?>(null) }
    var editDebt by remember { mutableStateOf<Debt?>(null) }

    LazyColumn(contentPadding = PaddingValues(start = Gutter, end = Gutter, top = 12.dp, bottom = NavClearance), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // ---- Cycle header
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Eyebrow("Pay cycle", Palette.Mint)
                    Text("${a.cycle.start.format(D)} – ${a.cycle.end.format(D)}", style = MaterialTheme.typography.headlineMedium)
                    Text("Budget & goals", style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim)
                }
                RingProgress(a.cycle.elapsedDays(a.today).toFloat() / a.cycle.totalDays, Palette.Mint, Modifier.size(78.dp), stroke = 7.dp) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${a.cycle.daysUntilPayday(a.today)}", style = MaterialTheme.typography.headlineSmall)
                        Text("to payday", style = MaterialTheme.typography.labelSmall, color = Palette.TextDim)
                    }
                }
            }
        }

        // ---- Bucket split (F04)
        if (a.budgetLines.isNotEmpty()) item {
            val used = a.budgetLines.sumOf { it.spent + it.committed }
            val limit = a.budgetLines.sumOf { it.limit }
            GlassCard(Modifier.enterAnimation(0)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        Eyebrow("Budgeted spend")
                        Row(verticalAlignment = Alignment.Bottom) {
                            MoneyText(used, style = MaterialTheme.typography.headlineMedium, whole = true)
                            Text("  of ${Money.formatWhole(limit)}", style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim, modifier = Modifier.padding(bottom = 4.dp))
                        }
                    }
                    Pill("${Money.ratioBp(used, limit) / 100}%", if (used > limit) Palette.Coral else Palette.Mint)
                }
                Spacer(Modifier.height(14.dp))
                StackedBar(a.budgetLines.sortedByDescending { it.spent }.map { BarSegment(it.category.name, it.spent + it.committed, categoryColor(it.category.colorArgb)) } +
                    BarSegment("Left", (limit - used).coerceAtLeast(0), Palette.GlassStrong))
            }
        }

        // ---- Budget tiles
        item { SectionHeader("Budgets", eyebrow = "${a.budgetLines.size} categories", action = { AddChip { pickCategory = true } }) }
        item {
            val tiles: List<BudgetLine?> = a.budgetLines + listOf(null)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                tiles.chunked(2).forEachIndexed { r, row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEachIndexed { c, line ->
                            val m = Modifier.weight(1f).enterAnimation(r * 2 + c)
                            if (line == null) NewBudgetTile(m) { pickCategory = true } else BudgetTile(line, m) { editBudget = line.category }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }

        // ---- Bills
        item {
            SectionHeader("Bills & debit orders", eyebrow = "Protected in safe-to-spend", action = {
                AddChip { editBill = RecurringBill(name = "", merchantPattern = "", expectedAmountMinor = 0, dueDay = 1, isConfirmed = true) }
            })
        }
        item {
            GlassCard(padding = 8.dp) {
                val confirmed = s.bills.filter { it.isConfirmed }
                if (confirmed.isEmpty()) Text("No bills yet.", color = Palette.TextDim, modifier = Modifier.padding(12.dp))
                confirmed.forEach { bill ->
                    val occ = a.occurrences.firstOrNull { it.bill.id == bill.id }
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).pressable({ editBill = bill }).padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val nextDue = PayCycle.dueDatesBetween(bill, a.today, a.today.plusDays(400)).firstOrNull()
                        DayBadge(
                            if (bill.frequency == BillFrequency.MONTHLY) bill.dueDay else nextDue?.dayOfMonth ?: bill.dueDay,
                            if (occ?.paid == true) Palette.TextFaint else Palette.Amber,
                            label = when (bill.frequency) { BillFrequency.MONTHLY -> "DAY"; BillFrequency.WEEKLY -> "WEEKLY"; BillFrequency.YEARLY -> nextDue?.month?.name?.take(3) ?: "YEAR" },
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(bill.name, style = MaterialTheme.typography.titleSmall)
                            Text(
                                occ?.let { if (it.paid) "Paid this cycle" else "Due ${it.dueDate.format(D)}" }
                                    ?: (bill.frequency.name.lowercase().replaceFirstChar { it.uppercase() } + (nextDue?.let { " · next ${it.format(D)}" } ?: "")),
                                style = MaterialTheme.typography.bodySmall, color = if (occ?.paid == true) Palette.Mint else Palette.TextDim,
                            )
                        }
                        MoneyText(bill.expectedAmountMinor, style = MaterialTheme.typography.titleSmall.merge(MoneyStyle), whole = true)
                    }
                }
            }
        }
        if (a.recurringCandidates.isNotEmpty()) item {
            GlassCard(brush = Brush.linearGradient(listOf(Palette.Violet.copy(alpha = 0.16f), Palette.Glass))) {
                Eyebrow("Detected by RandWise", Palette.Violet)
                Text("Look like debit orders", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                a.recurringCandidates.take(5).forEach { c ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(c.displayName, style = MaterialTheme.typography.titleSmall)
                            Text("~${Money.formatWhole(c.medianAmount)} · day ${c.dueDay} · seen ${c.occurrences}×", style = MaterialTheme.typography.bodySmall, color = Palette.TextDim)
                        }
                        Pill("Add", Palette.Mint, Modifier.pressable({ vm.confirmCandidate(c) }), filled = true)
                        Spacer(Modifier.width(6.dp))
                        Box(Modifier.size(48.dp).clip(CircleShape).pressable({ vm.dismissCandidate(c) }), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Close, "Dismiss", tint = Palette.TextFaint, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }

        // ---- Goals & emergency (F08)
        item { SectionHeader("Goals", action = { AddChip { editGoal = Goal(name = "", targetMinor = 0, savedMinor = 0) } }) }
        item { EmergencyCard(a.emergency.monthsX100, a.emergency.targetMonths, a.emergency.saved, a.emergency.targetAmount, a.emergency.monthlyEssential) }
        s.goals.filter { !it.isEmergencyFund }.forEach { g ->
            item(key = "goal${g.id}") {
                val saved = FinanceAnalyzer.goalProgress(g, a.accountBalances)
                val linked = g.linkedAccountId?.let { id -> s.accounts.firstOrNull { it.id == id }?.name }
                GlassCard(onClick = { editGoal = g }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RingProgress(if (g.targetMinor > 0) saved.toFloat() / g.targetMinor else 0f, Palette.Teal, Modifier.size(54.dp)) {
                            Text("${Money.ratioBp(saved, g.targetMinor) / 100}%", style = MaterialTheme.typography.labelMedium)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(g.name, style = MaterialTheme.typography.titleSmall)
                            Text(
                                "${Money.formatWhole(saved)} of ${Money.formatWhole(g.targetMinor)}" + (linked?.let { " · tracks $it" } ?: ""),
                                style = MaterialTheme.typography.bodySmall, color = Palette.TextDim,
                            )
                        }
                        Text("${Money.formatWhole((g.targetMinor - saved).coerceAtLeast(0))} to go", style = MaterialTheme.typography.labelMedium, color = Palette.Teal)
                    }
                }
            }
        }

        // ---- Debt planner (F08)
        item { SectionHeader("Debt-free plan", eyebrow = "Snowball vs avalanche", action = { AddChip { editDebt = Debt(name = "", balanceMinor = 0, aprBp = 0, minPaymentMinor = 0) } }) }
        item { DebtPlanner(s.debts, onEdit = { editDebt = it }) }
    }

    // ---- Dialogs
    if (pickCategory) {
        val available = s.categories.filter { c -> c.bucket != Bucket.INCOME && a.budgetLines.none { it.category.id == c.id } }
        AlertDialog(
            onDismissRequest = { pickCategory = false },
            title = { Text("Budget a category") },
            text = {
                androidx.compose.foundation.lazy.LazyColumn {
                    items(available.size) { i ->
                        val c = available[i]
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { pickCategory = false; editBudget = c }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            ColorDot(categoryColor(c.colorArgb), 12); Spacer(Modifier.width(12.dp)); Text(c.name, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = { TextButton({ pickCategory = false }) { Text("Close") } },
        )
    }
    editBudget?.let { cat ->
        val current = a.budgetLines.firstOrNull { it.category.id == cat.id }
        var amount by remember(cat.id) { mutableStateOf(current?.limit?.let { Money.toPlain(it) } ?: "") }
        AlertDialog(
            onDismissRequest = { editBudget = null },
            title = { Text(cat.name) },
            text = { MoneyField(amount, { amount = it }, "Limit this pay cycle", supporting = "Carries into future cycles automatically") },
            confirmButton = { TextButton({ vm.setBudget(cat.id, Money.parse(amount) ?: 0); editBudget = null }) { Text("Save") } },
            dismissButton = {
                if (current != null) TextButton({ vm.setBudget(cat.id, 0); editBudget = null }) { Text("Remove", color = Palette.Coral) }
                else TextButton({ editBudget = null }) { Text("Cancel") }
            },
        )
    }
    editBill?.let { bill -> BillDialog(bill, s.categories, onDismiss = { editBill = null }, onSave = { vm.saveBill(it); editBill = null }, onDelete = { vm.deleteBill(it); editBill = null }) }
    editGoal?.let { goal -> GoalDialog(goal, s.accounts.filter { !it.archived }, onDismiss = { editGoal = null }, onSave = { vm.saveGoal(it); editGoal = null }, onDelete = { vm.deleteGoal(it); editGoal = null }) }
    editDebt?.let { debt -> DebtDialog(debt, onDismiss = { editDebt = null }, onSave = { vm.saveDebt(it); editDebt = null }, onDelete = { vm.deleteDebt(it); editDebt = null }) }
}

@Composable
private fun AddChip(onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).clip(CircleShape).background(Palette.GlassStrong).border(1.dp, Palette.Hairline, CircleShape).pressable(onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Rounded.Add, "Add", tint = Palette.Mint, modifier = Modifier.size(20.dp)) }
}

@Composable
private fun BudgetTile(line: BudgetLine, modifier: Modifier, onClick: () -> Unit) {
    val color = categoryColor(line.category.colorArgb)
    val status = statusColor(line.status)
    GlassCard(modifier, onClick = onClick, padding = 16.dp) {
        Row(verticalAlignment = Alignment.Top) {
            RingProgress(line.usedBp / 10_000f, if (line.status == BudgetStatus.ON_TRACK) color else status, Modifier.size(56.dp), stroke = 6.dp) {
                Text("${(line.usedBp / 100).coerceAtMost(999)}%", style = MaterialTheme.typography.labelMedium.copy(fontFamily = Fonts.Display))
            }
            Spacer(Modifier.weight(1f))
            if (line.status != BudgetStatus.ON_TRACK) Box(Modifier.size(10.dp).clip(CircleShape).background(status))
        }
        Spacer(Modifier.height(14.dp))
        Text(line.category.name, style = MaterialTheme.typography.titleSmall, maxLines = 1)
        MoneyText(line.remaining, style = MaterialTheme.typography.titleLarge, whole = true, color = if (line.remaining < 0) Palette.Coral else Palette.Text)
        Text(
            when (line.status) {
                BudgetStatus.OVER -> "over budget"
                BudgetStatus.PROJECTED_OVER -> "heading for ${Money.formatWhole(line.projected)}"
                else -> "left of ${Money.formatWhole(line.limit)}"
            },
            style = MaterialTheme.typography.bodySmall, color = if (line.status == BudgetStatus.ON_TRACK) Palette.TextDim else status, maxLines = 1,
        )
    }
}

@Composable
private fun NewBudgetTile(modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.heightIn(min = 172.dp).pressable(onClick).drawBehind {
            drawRoundRect(
                Palette.TextFaint, style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))),
                cornerRadius = CornerRadius(28.dp.toPx()),
            )
        },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Rounded.Add, null, tint = Palette.Mint, modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(6.dp))
        Text("New budget", style = MaterialTheme.typography.titleSmall, color = Palette.TextDim)
    }
}

@Composable
private fun DayBadge(day: Int, accent: Color, label: String = "DAY") {
    Column(
        Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(Palette.GlassStrong),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().height(14.dp).background(accent.copy(alpha = 0.85f)), contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.labelSmall.copy(fontSize = androidx.compose.ui.unit.TextUnit(8f, androidx.compose.ui.unit.TextUnitType.Sp)), color = Palette.Ink)
        }
        Text("$day", style = MaterialTheme.typography.titleLarge.copy(fontFamily = Fonts.Display))
    }
}

@Composable
private fun EmergencyCard(monthsX100: Long, target: Int, saved: Long, targetAmount: Long, monthly: Long) {
    GlassCard(brush = Brush.linearGradient(listOf(Palette.Emerald.copy(alpha = 0.22f), Palette.Navy900.copy(alpha = 0.4f)))) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Rounded.Shield, Palette.Mint, size = 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Eyebrow("Emergency cover", Palette.Mint)
                Text("Safety net", style = MaterialTheme.typography.titleMedium)
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text("${monthsX100 / 100}.${(monthsX100 % 100).toString().padStart(2, '0')}", style = MaterialTheme.typography.displaySmall)
            Text("  of $target months covered", style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim, modifier = Modifier.padding(bottom = 8.dp))
        }
        Spacer(Modifier.height(10.dp))
        GlowBar(if (targetAmount > 0) saved.toFloat() / targetAmount else 0f, Palette.Mint, height = 10.dp)
        Spacer(Modifier.height(8.dp))
        Text("${Money.formatWhole(saved)} saved · essentials ≈ ${Money.formatWhole(monthly)}/month · target ${Money.formatWhole(targetAmount)}", style = MaterialTheme.typography.bodySmall, color = Palette.TextDim)
    }
}

@Composable
private fun DebtPlanner(debts: List<Debt>, onEdit: (Debt) -> Unit) {
    var extra by rememberSaveable { mutableStateOf("500") }
    if (debts.isEmpty()) {
        GlassCard { Text("Add your loans and cards to see which payoff strategy saves you the most interest.", color = Palette.TextDim) }
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GlassCard(padding = 8.dp) {
            debts.forEach { d ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).pressable({ onEdit(d) }).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(d.name, style = MaterialTheme.typography.titleSmall)
                        Text("${Money.formatPercentBp(d.aprBp.toLong())} APR · min ${Money.formatWhole(d.minimumFor(d.balanceMinor))}" + (if (d.minPaymentBp > 0) " (${Money.formatPercentBp(d.minPaymentBp.toLong())} of balance)" else ""), style = MaterialTheme.typography.bodySmall, color = Palette.TextDim)
                    }
                    MoneyText(d.balanceMinor, style = MaterialTheme.typography.titleSmall, whole = true)
                }
            }
        }
        MoneyField(extra, { extra = it }, "Extra per month on top of minimums")
        val budget = debts.sumOf { it.minimumFor(it.balanceMinor) } + (Money.parse(extra) ?: 0)
        val snow = remember(debts, budget) { DebtPlanner.plan(debts, budget, DebtStrategy.SNOWBALL) }
        val aval = remember(debts, budget) { DebtPlanner.plan(debts, budget, DebtStrategy.AVALANCHE) }
        val best = when {
            !snow.feasible || !aval.feasible -> null
            aval.totalInterest < snow.totalInterest -> DebtStrategy.AVALANCHE
            snow.totalInterest < aval.totalInterest -> DebtStrategy.SNOWBALL
            else -> null
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StrategyCard(snow, best == DebtStrategy.SNOWBALL, kotlin.math.abs(snow.totalInterest - aval.totalInterest), Modifier.weight(1f))
            StrategyCard(aval, best == DebtStrategy.AVALANCHE, kotlin.math.abs(snow.totalInterest - aval.totalInterest), Modifier.weight(1f))
        }
    }
}

@Composable
private fun StrategyCard(p: DebtPlan, winner: Boolean, saving: Long, modifier: Modifier) {
    val shape = RoundedCornerShape(26.dp)
    Column(
        modifier.clip(shape)
            .background(if (winner) Brush.verticalGradient(listOf(Palette.Emerald.copy(alpha = 0.24f), Palette.Glass)) else RWBrush.card)
            .border(BorderStroke(if (winner) 1.5.dp else 1.dp, if (winner) Palette.Mint else Palette.Hairline), shape)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (p.strategy == DebtStrategy.SNOWBALL) "Snowball" else "Avalanche", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (winner) Icon(Icons.Rounded.EmojiEvents, null, tint = Palette.Mint, modifier = Modifier.size(20.dp))
        }
        Text(if (p.strategy == DebtStrategy.SNOWBALL) "Smallest balance first" else "Highest interest first", style = MaterialTheme.typography.bodySmall, color = Palette.TextDim)
        Spacer(Modifier.height(14.dp))
        if (!p.feasible) {
            Text("Payments don't cover the interest", color = Palette.Coral, style = MaterialTheme.typography.bodySmall)
        } else {
            Text("${p.months / 12}y ${p.months % 12}m", style = MaterialTheme.typography.headlineMedium)
            Text("to debt-free", style = MaterialTheme.typography.bodySmall, color = Palette.TextDim)
            Spacer(Modifier.height(10.dp))
            Text("Interest ${Money.formatWhole(p.totalInterest)}", style = MaterialTheme.typography.titleSmall)
            if (winner && saving > 0) {
                Spacer(Modifier.height(8.dp))
                Pill("Saves ${Money.formatWhole(saving)}", Palette.Mint, filled = true)
            }
            Spacer(Modifier.height(10.dp))
            p.payoffs.forEachIndexed { i, po ->
                Text("${i + 1}. ${po.name}", style = MaterialTheme.typography.bodySmall, color = Palette.TextDim, maxLines = 1)
            }
        }
    }
}

// ---------------------------------------------------------------- Dialogs

@Composable
private fun BillDialog(bill: RecurringBill, categories: List<Category>, onDismiss: () -> Unit, onSave: (RecurringBill) -> Unit, onDelete: (RecurringBill) -> Unit) {
    var name by remember { mutableStateOf(bill.name) }
    var pattern by remember { mutableStateOf(bill.merchantPattern) }
    var amount by remember { mutableStateOf(if (bill.expectedAmountMinor > 0) Money.toPlain(bill.expectedAmountMinor) else "") }
    var day by remember { mutableStateOf(bill.dueDay.toString()) }
    var cat by remember { mutableStateOf(bill.categoryId) }
    val freqs = listOf(BillFrequency.MONTHLY, BillFrequency.WEEKLY, BillFrequency.YEARLY)
    var freq by remember { mutableIntStateOf(freqs.indexOf(bill.frequency).coerceAtLeast(0)) }
    var anchor by remember { mutableStateOf(bill.anchorDate?.let(LocalDate::ofEpochDay) ?: LocalDate.now()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (bill.id == 0L) "New bill" else "Edit bill") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                RWTextField(name, { name = it.take(40) }, "Name")
                RWTextField(pattern, { pattern = it.uppercase().take(40) }, "Statement text to match", supporting = "Blank = use the name")
                MoneyField(amount, { amount = it }, "Expected amount")
                SegmentedControl(listOf("Monthly", "Weekly", "Yearly"), freq, { freq = it })
                if (freqs[freq] == BillFrequency.MONTHLY) RWTextField(day, { day = it.filter(Char::isDigit).take(2) }, "Due day of month", keyboardType = KeyboardType.Number)
                else DateField(anchor, { anchor = it }, label = "Next due date")
                CategoryDropdown(categories.filter { it.bucket != Bucket.INCOME }, cat, { cat = it })
            }
        },
        confirmButton = {
            TextButton(
                {
                    val f = freqs[freq]
                    onSave(
                        bill.copy(
                            name = name.trim(), merchantPattern = pattern.trim().ifEmpty { MerchantText.normalize(name) }, expectedAmountMinor = Money.parse(amount) ?: 0,
                            dueDay = if (f == BillFrequency.MONTHLY) day.toIntOrNull()?.coerceIn(1, 31) ?: 1 else anchor.dayOfMonth,
                            frequency = f, anchorDate = if (f == BillFrequency.MONTHLY) null else anchor.toEpochDay(),
                            categoryId = cat, isConfirmed = true,
                        ),
                    )
                },
                enabled = name.isNotBlank() && (Money.parse(amount) ?: 0) > 0,
            ) { Text("Save") }
        },
        dismissButton = { if (bill.id != 0L) TextButton({ onDelete(bill) }) { Text("Delete", color = Palette.Coral) } else TextButton(onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun GoalDialog(goal: Goal, accounts: List<Account>, onDismiss: () -> Unit, onSave: (Goal) -> Unit, onDelete: (Goal) -> Unit) {
    var name by remember { mutableStateOf(goal.name) }
    var target by remember { mutableStateOf(if (goal.targetMinor > 0) Money.toPlain(goal.targetMinor) else "") }
    var saved by remember { mutableStateOf(Money.toPlain(goal.savedMinor)) }
    var add by remember { mutableStateOf("") }
    var emergency by remember { mutableStateOf(goal.isEmergencyFund) }
    var linked by remember { mutableStateOf(goal.linkedAccountId) }
    val noLink = Account(id = -1, name = "Don't link — I'll update it myself", type = AccountType.SAVINGS)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (goal.id == 0L) "New goal" else goal.name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                RWTextField(name, { name = it.take(40) }, "Name")
                MoneyField(target, { target = it }, "Target")
                val options = listOf(noLink) + accounts
                Dropdown("Track an account's balance", options, options.firstOrNull { it.id == (linked ?: -1L) }, { it.name }, { linked = it.id.takeIf { id -> id != -1L } })
                if (linked == null) {
                    MoneyField(saved, { saved = it }, "Saved so far")
                    if (goal.id != 0L) MoneyField(add, { add = it }, "Add a contribution")
                } else {
                    Text("Progress follows the account's live balance automatically.", style = MaterialTheme.typography.bodySmall, color = Palette.TextDim)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(emergency, { emergency = it })
                    Text("This is my emergency fund")
                }
            }
        },
        confirmButton = {
            TextButton(
                { onSave(goal.copy(name = name.trim(), targetMinor = Money.parse(target) ?: 0, savedMinor = (Money.parse(saved) ?: 0) + (Money.parse(add) ?: 0), isEmergencyFund = emergency, linkedAccountId = linked)) },
                enabled = name.isNotBlank() && (Money.parse(target) ?: 0) > 0,
            ) { Text("Save") }
        },
        dismissButton = { if (goal.id != 0L) TextButton({ onDelete(goal) }) { Text("Delete", color = Palette.Coral) } else TextButton(onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun DebtDialog(debt: Debt, onDismiss: () -> Unit, onSave: (Debt) -> Unit, onDelete: (Debt) -> Unit) {
    var name by remember { mutableStateOf(debt.name) }
    var balance by remember { mutableStateOf(if (debt.balanceMinor > 0) Money.toPlain(debt.balanceMinor) else "") }
    // Basis points share the cents representation: "21.75" -> 2175.
    var apr by remember { mutableStateOf(if (debt.aprBp > 0) Money.toPlain(debt.aprBp.toLong()) else "") }
    var min by remember { mutableStateOf(if (debt.minPaymentMinor > 0) Money.toPlain(debt.minPaymentMinor) else "") }
    var pctMode by remember { mutableIntStateOf(if (debt.minPaymentBp > 0) 1 else 0) }
    var pct by remember { mutableStateOf(if (debt.minPaymentBp > 0) Money.toPlain(debt.minPaymentBp.toLong()) else "3") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (debt.id == 0L) "New debt" else "Edit debt") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                RWTextField(name, { name = it.take(40) }, "Name (e.g. Credit card)")
                MoneyField(balance, { balance = it }, "Outstanding balance")
                RWTextField(apr, { apr = it.filter { c -> c.isDigit() || c == '.' } }, "Interest rate (APR)", keyboardType = KeyboardType.Decimal, suffix = "%")
                SegmentedControl(listOf("Fixed instalment", "% of balance"), pctMode, { pctMode = it })
                if (pctMode == 1) {
                    RWTextField(pct, { pct = it.filter { c -> c.isDigit() || c == '.' } }, "Minimum % of balance", keyboardType = KeyboardType.Decimal, suffix = "%", supporting = "Most SA cards ask 3–5% a month")
                    MoneyField(min, { min = it }, "But never less than", supporting = "The card's minimum floor, e.g. R150")
                } else {
                    MoneyField(min, { min = it }, "Monthly instalment")
                }
            }
        },
        confirmButton = {
            TextButton(
                {
                    onSave(
                        debt.copy(
                            name = name.trim(), balanceMinor = Money.parse(balance) ?: 0, aprBp = (Money.parse(apr) ?: 0).toInt(),
                            minPaymentMinor = Money.parse(min) ?: 0,
                            // Percent and basis points share the cents representation: "3.5" -> 350.
                            minPaymentBp = if (pctMode == 1) (Money.parse(pct) ?: 0).toInt().coerceIn(0, 10_000) else 0,
                        ),
                    )
                },
                enabled = name.isNotBlank() && (Money.parse(balance) ?: 0) > 0,
            ) { Text("Save") }
        },
        dismissButton = { if (debt.id != 0L) TextButton({ onDelete(debt) }) { Text("Delete", color = Palette.Coral) } else TextButton(onDismiss) { Text("Cancel") } },
    )
}

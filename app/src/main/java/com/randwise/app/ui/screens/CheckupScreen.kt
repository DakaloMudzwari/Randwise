package com.randwise.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.randwise.app.ai.Checkup
import com.randwise.app.ai.CheckupLine
import com.randwise.app.ai.CheckupNote
import com.randwise.app.ai.LocalCoach
import com.randwise.app.core.Money
import com.randwise.app.data.model.PayFrequency
import com.randwise.app.domain.engine.SpendingReview
import com.randwise.app.ui.CheckupViewModel
import com.randwise.app.ui.FinanceViewModel
import com.randwise.app.ui.Gutter
import com.randwise.app.ui.NavClearance
import com.randwise.app.ui.components.CoachOrb
import com.randwise.app.ui.components.Eyebrow
import com.randwise.app.ui.components.GhostButton
import com.randwise.app.ui.components.GlassCard
import com.randwise.app.ui.components.GlowBar
import com.randwise.app.ui.components.IconBadge
import com.randwise.app.ui.components.MoneyField
import com.randwise.app.ui.components.MoneyText
import com.randwise.app.ui.components.Pill
import com.randwise.app.ui.components.PrimaryButton
import com.randwise.app.ui.components.SectionHeader
import com.randwise.app.ui.components.Sparkline
import com.randwise.app.ui.components.categoryColor
import com.randwise.app.ui.components.enterAnimation
import com.randwise.app.ui.components.pressable
import com.randwise.app.ui.theme.Palette

/** Fewer days than this and the screen nudges the user to import a full three months. */
private const val FULL_PICTURE_DAYS = 75

@Composable
fun CheckupScreen(vm: FinanceViewModel, cvm: CheckupViewModel, onBack: () -> Unit, onImport: () -> Unit, onBudgetApplied: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val checkup by cvm.checkup.collectAsStateWithLifecycle()
    val running by cvm.running.collectAsStateWithLifecycle()
    val error by cvm.error.collectAsStateWithLifecycle()
    val a = state.analysis ?: return
    val s = state.snapshot
    val run = { cvm.run(s, a) }
    val latestTx = s.transactions.maxOfOrNull { it.id } ?: 0L

    LazyColumn(contentPadding = PaddingValues(start = Gutter, end = Gutter, top = 12.dp, bottom = NavClearance), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(Palette.Glass).border(1.dp, Palette.Hairline, CircleShape).pressable(onBack),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Palette.Text) }
                Spacer(Modifier.width(14.dp))
                Column {
                    Eyebrow("Money check-up", Palette.Mint)
                    Text("Your last 3 months", style = MaterialTheme.typography.titleLarge)
                }
            }
        }

        if (running) item { RunningCard() }
        error?.let { item { Text(it, color = Palette.Amber, style = MaterialTheme.typography.bodyMedium) } }

        val c = checkup
        if (c == null) {
            if (!running) item { IntroCard(onRun = run, onImport = onImport) }
            return@LazyColumn
        }

        item { HeadlineCard(c, Modifier.enterAnimation(0)) }
        if (c.daysOfData < FULL_PICTURE_DAYS || latestTx > c.lastTransactionId) item {
            GlassCard(Modifier.enterAnimation(1)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(if (latestTx > c.lastTransactionId) Icons.Rounded.Refresh else Icons.Rounded.UploadFile, Palette.Sky)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        if (latestTx > c.lastTransactionId) "You have new transactions since this check-up."
                        else "Based on ${c.daysOfData} days. Import 3 months of bank statements for the full picture.",
                        style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim, modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (latestTx > c.lastTransactionId) PrimaryButton("Refresh", run, Modifier.weight(1f), enabled = !running, icon = Icons.Rounded.Refresh)
                    GhostButton("Import statements", onImport, Modifier.weight(1f), icon = Icons.Rounded.UploadFile)
                }
            }
        }

        item { SectionHeader("Where your money goes", eyebrow = "Typical month") }
        item { CategoryBreakdown(c) }

        if (c.merchants.isNotEmpty()) {
            item { SectionHeader("Where you spend most", eyebrow = "Top shops & services") }
            item {
                GlassCard {
                    c.merchants.forEachIndexed { i, m ->
                        if (i > 0) Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(m.label.lowercase().replaceFirstChar { it.titlecase() }, style = MaterialTheme.typography.titleSmall)
                                Text("${m.visits} payment${if (m.visits == 1) "" else "s"}", style = MaterialTheme.typography.bodySmall, color = Palette.TextFaint)
                            }
                            MoneyText(m.totalMinor, style = MaterialTheme.typography.titleSmall, whole = true)
                        }
                    }
                }
            }
        }

        if (c.insights.isNotEmpty()) {
            item { SectionHeader("What we noticed", eyebrow = if (c.source == "GEMINI") "AI analysis" else "Your patterns") }
            items(c.insights) { NoteCard(it, Icons.Rounded.Lightbulb, Palette.Amber) }
        }
        if (c.suggestions.isNotEmpty()) {
            item { SectionHeader("Suggestions", eyebrow = "Small changes, real money") }
            items(c.suggestions) { NoteCard(it, Icons.Rounded.CheckCircle, Palette.Mint) }
        }

        if (c.budget.isNotEmpty()) {
            item { SectionHeader("Your suggested budget", eyebrow = "Built from your spending") }
            item {
                val frequency = s.profile?.payFrequency ?: PayFrequency.MONTHLY
                SuggestedBudget(c, frequency) { picked ->
                    val known = s.categories.map { it.id }.toSet()
                    picked.filter { it.first in known }.forEach { (id, monthly) -> vm.setBudget(id, SpendingReview.cycleFromMonthly(monthly, frequency)) }
                    onBudgetApplied()
                }
            }
        }

        item { GhostButton(if (running) "Checking…" else "Run the check-up again", run, Modifier.fillMaxWidth(), icon = Icons.Rounded.Refresh) }
        item { Text(LocalCoach.DISCLAIMER, style = MaterialTheme.typography.bodySmall, color = Palette.TextFaint) }
    }
}

@Composable
private fun RunningCard() {
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CoachOrb(Modifier.size(48.dp), thinking = true)
            Spacer(Modifier.width(14.dp))
            Column {
                Text("Looking at your last 3 months…", style = MaterialTheme.typography.titleSmall)
                Text("Categories, shops, trends and a budget that fits.", style = MaterialTheme.typography.bodySmall, color = Palette.TextDim)
            }
        }
    }
}

@Composable
private fun IntroCard(onRun: () -> Unit, onImport: () -> Unit) {
    GlassCard {
        CoachOrb(Modifier.size(64.dp))
        Spacer(Modifier.height(14.dp))
        Text("See where your money really goes", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        listOf(
            "Your spending by category and shop, month by month",
            "What's rising, what's leaking, and what to change",
            "A budget built from how you actually spend — you choose what to keep",
        ).forEach {
            Row(Modifier.padding(vertical = 3.dp)) {
                Icon(Icons.Rounded.CheckCircle, null, tint = Palette.Mint, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim)
            }
        }
        Spacer(Modifier.height(16.dp))
        PrimaryButton("Run my check-up", onRun, icon = Icons.Rounded.AutoAwesome)
        Spacer(Modifier.height(8.dp))
        GhostButton("Import 3 months of statements first", onImport, Modifier.fillMaxWidth(), icon = Icons.Rounded.UploadFile)
    }
}

@Composable
private fun HeadlineCard(c: Checkup, modifier: Modifier) {
    GlassCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (c.source == "GEMINI") Pill("AI analysis", Palette.Violet, icon = Icons.Rounded.AutoAwesome)
            else Pill("On your phone", Palette.Mint, icon = Icons.Rounded.PhoneAndroid)
            Spacer(Modifier.weight(1f))
            Text(ago(c.createdAt), style = MaterialTheme.typography.labelMedium, color = Palette.TextFaint)
        }
        Spacer(Modifier.height(12.dp))
        Text(c.headline, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(6.dp))
        Text(c.summary, style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim)
        c.note?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = Palette.TextFaint)
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Stat("Income", c.monthlyIncomeMinor, Palette.Mint, Modifier.weight(1f))
            Stat("Typical spend", c.typicalSpendMinor, Palette.Text, Modifier.weight(1f))
            val left = c.monthlyIncomeMinor - c.typicalSpendMinor
            Stat("Left over", left, if (left < 0) Palette.Coral else Palette.Mint, Modifier.weight(1f))
        }
    }
}

@Composable
private fun Stat(label: String, minor: Long, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Column(modifier) {
        Eyebrow(label)
        MoneyText(minor, style = MaterialTheme.typography.titleMedium, whole = true, color = color)
    }
}

@Composable
private fun CategoryBreakdown(c: Checkup) {
    val max = c.categories.maxOfOrNull { it.typicalMinor }?.coerceAtLeast(1) ?: 1
    GlassCard {
        c.categories.forEachIndexed { i, cat ->
            if (i > 0) Spacer(Modifier.height(14.dp))
            val color = categoryColor(cat.colorArgb)
            val recent = cat.monthsMinor.takeLast(c.monthsCovered)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(cat.name, style = MaterialTheme.typography.titleSmall)
                        if (recent.size >= 2 && recent.first() > 0) {
                            val up = recent.last() > recent.first()
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                if (up) Icons.AutoMirrored.Rounded.TrendingUp else Icons.AutoMirrored.Rounded.TrendingDown,
                                if (up) "Rising" else "Falling",
                                tint = if (up) Palette.Amber else Palette.Mint, modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    GlowBar(cat.typicalMinor.toFloat() / max, color)
                }
                Spacer(Modifier.width(12.dp))
                Column(horizontalAlignment = Alignment.End) {
                    MoneyText(cat.typicalMinor, style = MaterialTheme.typography.titleSmall, whole = true)
                    if (recent.size >= 2) Sparkline(recent, color, Modifier.width(56.dp).height(18.dp))
                }
            }
        }
    }
}

@Composable
private fun NoteCard(note: CheckupNote, icon: androidx.compose.ui.graphics.vector.ImageVector, tint: androidx.compose.ui.graphics.Color) {
    GlassCard {
        Row {
            IconBadge(icon, tint, size = 34.dp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(note.title, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(2.dp))
                Text(note.body, style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim)
            }
        }
    }
}

/** Tick the lines to keep, adjust any amount, then save them as this pay cycle's budgets. */
@Composable
private fun SuggestedBudget(c: Checkup, frequency: PayFrequency, onApply: (List<Pair<Long, Long>>) -> Unit) {
    val keep = remember(c.createdAt) { mutableStateMapOf<Long, Boolean>().apply { c.budget.forEach { put(it.categoryId, true) } } }
    val amounts = remember(c.createdAt) { mutableStateMapOf<Long, String>().apply { c.budget.forEach { put(it.categoryId, Money.toPlain(it.limitMinor)) } } }
    val chosen = c.budget.filter { keep[it.categoryId] == true }.map { it.categoryId to (Money.parse(amounts[it.categoryId].orEmpty()) ?: 0L) }
    val total = chosen.sumOf { it.second }

    GlassCard {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Eyebrow("Budgeted per month")
                MoneyText(total, style = MaterialTheme.typography.headlineSmall, whole = true, color = if (total > c.availableMinor) Palette.Coral else Palette.Text)
            }
            Text("of ${Money.formatWhole(c.availableMinor)} after savings", style = MaterialTheme.typography.bodySmall, color = Palette.TextDim, modifier = Modifier.padding(bottom = 4.dp))
        }
        Spacer(Modifier.height(8.dp))
        GlowBar(if (c.availableMinor > 0) total.toFloat() / c.availableMinor else 0f, if (total > c.availableMinor) Palette.Coral else Palette.Mint)
        if (frequency != PayFrequency.MONTHLY) {
            Spacer(Modifier.height(8.dp))
            Text("You're paid ${frequency.name.lowercase()} — amounts are converted to your pay cycle when saved.", style = MaterialTheme.typography.bodySmall, color = Palette.TextFaint)
        }
        c.budget.forEach { line -> BudgetLineRow(line, keep[line.categoryId] == true, amounts[line.categoryId].orEmpty(), { keep[line.categoryId] = it }, { amounts[line.categoryId] = it }) }
        Spacer(Modifier.height(16.dp))
        PrimaryButton("Use this budget", { onApply(chosen.filter { it.second > 0 }) }, enabled = chosen.any { it.second > 0 }, icon = Icons.Rounded.CheckCircle)
    }
}

@Composable
private fun BudgetLineRow(line: CheckupLine, kept: Boolean, amount: String, onKeep: (Boolean) -> Unit, onAmount: (String) -> Unit) {
    Spacer(Modifier.height(14.dp))
    Row(verticalAlignment = Alignment.Top) {
        Checkbox(kept, onKeep, colors = CheckboxDefaults.colors(checkedColor = Palette.Mint, checkmarkColor = Palette.Ink, uncheckedColor = Palette.TextFaint))
        Column(Modifier.weight(1f).padding(top = 10.dp)) {
            Text(line.category, style = MaterialTheme.typography.titleSmall, color = if (kept) Palette.Text else Palette.TextFaint)
            Text("Typical ${Money.formatWhole(line.typicalMinor)} · ${line.reason}", style = MaterialTheme.typography.bodySmall, color = Palette.TextFaint)
        }
        Spacer(Modifier.width(8.dp))
        MoneyField(amount, onAmount, "Limit", Modifier.width(118.dp))
    }
}

private fun ago(ms: Long): String = when (val days = (System.currentTimeMillis() - ms) / 86_400_000L) {
    0L -> "Today"
    1L -> "Yesterday"
    else -> "$days days ago"
}

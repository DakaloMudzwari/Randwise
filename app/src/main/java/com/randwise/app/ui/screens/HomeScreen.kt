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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.randwise.app.core.Money
import com.randwise.app.data.model.Category
import com.randwise.app.data.model.Transaction
import com.randwise.app.data.model.TxType
import com.randwise.app.domain.Analysis
import com.randwise.app.domain.engine.BillOccurrence
import com.randwise.app.domain.engine.Severity
import com.randwise.app.ui.FinanceViewModel
import com.randwise.app.ui.Gutter
import com.randwise.app.ui.NavClearance
import com.randwise.app.ui.gutter
import com.randwise.app.ui.components.AdBanner
import com.randwise.app.ui.components.AnimatedMoney
import com.randwise.app.ui.components.BarSegment
import com.randwise.app.ui.components.ColorDot
import com.randwise.app.ui.components.EmptyState
import com.randwise.app.ui.components.Eyebrow
import com.randwise.app.ui.components.GlassCard
import com.randwise.app.ui.components.IconBadge
import com.randwise.app.ui.components.MerchantAvatar
import com.randwise.app.ui.components.MoneyText
import com.randwise.app.ui.components.PaydayRunway
import com.randwise.app.ui.components.Pill
import com.randwise.app.ui.components.RingProgress
import com.randwise.app.ui.components.RunwayPin
import com.randwise.app.ui.components.SectionHeader
import com.randwise.app.ui.components.SegmentedGauge
import com.randwise.app.ui.components.Sparkline
import com.randwise.app.ui.components.StackedBar
import com.randwise.app.ui.components.categoryColor
import com.randwise.app.ui.components.enterAnimation
import com.randwise.app.ui.components.pressable
import com.randwise.app.ui.components.severityColor
import com.randwise.app.ui.theme.Palette
import com.randwise.app.ui.theme.RWBrush
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private val DAY_FMT = DateTimeFormatter.ofPattern("d MMM")

@Composable
fun HomeScreen(vm: FinanceViewModel, onAdd: () -> Unit, onOpenLedger: () -> Unit, onImport: () -> Unit, onCoach: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val a = state.analysis ?: return
    val cats = state.snapshot.categories.associateBy { it.id }
    val dueSoon = a.safeToSpend.unpaidBills.count { ChronoUnit.DAYS.between(a.today, it.dueDate) <= 2 }

    LazyColumn(
        contentPadding = PaddingValues(top = 8.dp, bottom = NavClearance),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item { Box(Modifier.gutter()) { TopBar(a.today, dueSoon) } }
        item { Hero(a, Modifier.gutter().enterAnimation(0)) }
        item { Composition(a, Modifier.gutter().enterAnimation(1)) }
        item { QuickActions(onAdd, onImport, onCoach, Modifier.gutter().enterAnimation(2)) }
        item { Runway(a, Modifier.gutter().enterAnimation(3)) }

        val insights = a.behavior.insights.sortedByDescending { it.severity.ordinal }
        if (insights.isNotEmpty()) {
            item { SectionHeader("For you", Modifier.gutter(), eyebrow = "Insights") }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(horizontal = Gutter)) {
                    items(insights) { ins -> InsightCard(ins.severity, ins.title, ins.body) }
                }
            }
        }

        val unpaid = a.safeToSpend.unpaidBills
        item {
            SectionHeader("Due before payday", Modifier.gutter(), eyebrow = "${unpaid.size} bills · ${Money.formatWhole(a.safeToSpend.unpaidBillsTotal)}")
        }
        item {
            if (unpaid.isEmpty()) {
                GlassCard(Modifier.gutter()) { Row(verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(Icons.Rounded.CalendarMonth, Palette.Mint)
                    Spacer(Modifier.width(12.dp))
                    Text("All caught up — every confirmed bill this cycle is paid.", color = Palette.TextDim, style = MaterialTheme.typography.bodyMedium)
                } }
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(horizontal = Gutter)) {
                    items(unpaid) { occ -> BillCard(occ, a.today, categoryColor(occ.bill.categoryId?.let { cats[it]?.colorArgb })) }
                }
            }
        }

        item { Box(Modifier.gutter()) { CycleStats(a) } }

        item {
            SectionHeader("Recent activity", Modifier.gutter(), action = { Pill("See all", Palette.Mint, Modifier.pressable(onOpenLedger)) })
        }
        item {
            GlassCard(Modifier.gutter(), padding = 8.dp) {
                val recent = state.snapshot.transactions.take(6)
                if (recent.isEmpty()) EmptyState("No transactions yet", "Tap Add, or import a bank statement.")
                recent.forEach { t -> TransactionRow(t, t.categoryId?.let { cats[it] }) }
            }
        }

        // Ad slot: last, separated, neutral grey — never mixed with financial content.
        if (settings.showAds) item {
            Spacer(Modifier.height(4.dp))
            AdBanner(Modifier.gutter())
        }
    }
}

@Composable
private fun TopBar(today: LocalDate, dueSoon: Int) {
    val hour = LocalTime.now().hour
    val greeting = when (hour) { in 5..11 -> "Good morning"; in 12..17 -> "Good afternoon"; else -> "Good evening" }
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(RWBrush.emerald), contentAlignment = Alignment.Center) {
            Text("R", style = MaterialTheme.typography.headlineSmall, color = Palette.Ink)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Eyebrow(greeting)
            Text(today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")), style = MaterialTheme.typography.titleMedium)
        }
        Box {
            Box(Modifier.size(44.dp).clip(CircleShape).background(Palette.Glass).border(1.dp, Palette.Hairline, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.NotificationsNone, "Alerts", tint = Palette.Text)
            }
            if (dueSoon > 0) Box(
                Modifier.align(Alignment.TopEnd).offset(2.dp, (-2).dp).size(18.dp).clip(CircleShape).background(Palette.Amber),
                contentAlignment = Alignment.Center,
            ) { Text("$dueSoon", style = MaterialTheme.typography.labelSmall, color = Palette.Ink) }
        }
    }
}

@Composable
private fun Hero(a: Analysis, modifier: Modifier) {
    val sts = a.safeToSpend
    val base = maxOf(a.liquidBalance, a.cycleIncome, 1L)
    val fraction = (sts.safeToSpend.toDouble() / base).toFloat()
    val accent = when {
        sts.safeToSpend <= 0 -> Palette.Coral
        sts.perDay < 5_000 -> Palette.Amber
        else -> Palette.Mint
    }
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        SegmentedGauge(fraction, accent, Modifier.size(300.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Eyebrow("Safe to spend", Palette.TextDim)
                Spacer(Modifier.height(4.dp))
                AnimatedMoney(sts.safeToSpend, MaterialTheme.typography.displayMedium)
                Spacer(Modifier.height(10.dp))
                Pill("${Money.formatWhole(sts.perDay)} a day", accent, icon = Icons.Rounded.TrendingUp)
                Spacer(Modifier.height(8.dp))
                Text(
                    "${sts.daysUntilPayday} days until payday · ${a.cycle.nextPayday.format(DAY_FMT)}",
                    style = MaterialTheme.typography.bodySmall, color = Palette.TextDim, textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Visual version of Equation 1: where today's balance is already spoken for. */
@Composable
private fun Composition(a: Analysis, modifier: Modifier) {
    val s = a.safeToSpend
    val segments = listOf(
        BarSegment("Safe", s.safeToSpend.coerceAtLeast(0), Palette.Mint),
        BarSegment("Bills", s.unpaidBillsTotal, Palette.Amber),
        BarSegment("Savings", s.plannedSavingsRemaining, Palette.Sky),
        BarSegment("Buffer", s.safetyBuffer, Palette.Violet),
    )
    GlassCard(modifier) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Eyebrow("Balance")
                MoneyText(s.balance, style = MaterialTheme.typography.headlineMedium, whole = true)
            }
            Text("how it's allocated", style = MaterialTheme.typography.bodySmall, color = Palette.TextFaint)
        }
        Spacer(Modifier.height(14.dp))
        StackedBar(segments)
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth()) {
            segments.forEach { seg ->
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ColorDot(seg.color, 8)
                        Spacer(Modifier.width(6.dp))
                        Text(seg.label, style = MaterialTheme.typography.labelMedium, color = Palette.TextDim)
                    }
                    MoneyText(seg.amount, style = MaterialTheme.typography.titleSmall, whole = true)
                }
            }
        }
    }
}

@Composable
private fun QuickActions(onAdd: () -> Unit, onImport: () -> Unit, onCoach: () -> Unit, modifier: Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        QuickAction("Add", Icons.Rounded.Add, primary = true, onClick = onAdd)
        QuickAction("Transfer", Icons.Rounded.SwapHoriz, onClick = onAdd)
        QuickAction("Import", Icons.Rounded.UploadFile, onClick = onImport)
        QuickAction("Ask coach", Icons.Rounded.AutoAwesome, onClick = onCoach)
    }
}

@Composable
private fun QuickAction(label: String, icon: ImageVector, primary: Boolean = false, onClick: () -> Unit) {
    Column(Modifier.width(76.dp).pressable(onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(62.dp).clip(RoundedCornerShape(22.dp))
                .background(if (primary) RWBrush.emerald else Brush.linearGradient(listOf(Palette.GlassStrong, Palette.Glass)))
                .border(1.dp, if (primary) Color.Transparent else Palette.Hairline, RoundedCornerShape(22.dp)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, label, tint = if (primary) Palette.Ink else Palette.Text, modifier = Modifier.size(26.dp)) }
        Spacer(Modifier.height(8.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = Palette.TextDim, maxLines = 1)
    }
}

@Composable
private fun Runway(a: Analysis, modifier: Modifier) {
    GlassCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Eyebrow("Payday runway")
                Text("${a.cycle.start.format(DAY_FMT)} → ${a.cycle.nextPayday.format(DAY_FMT)}", style = MaterialTheme.typography.titleMedium)
            }
            Pill("Day ${a.cycle.elapsedDays(a.today)} of ${a.cycle.totalDays}", Palette.Mint)
        }
        Spacer(Modifier.height(4.dp))
        PaydayRunway(
            a.cycle.start, a.cycle.nextPayday, a.today,
            a.occurrences.map { RunwayPin(it.dueDate, it.bill.name, it.bill.expectedAmountMinor, it.paid) },
        )
    }
}

@Composable
private fun InsightCard(severity: Severity, title: String, body: String) {
    val c = severityColor(severity)
    Column(
        Modifier.width(272.dp).height(150.dp).clip(RoundedCornerShape(26.dp))
            .background(Brush.linearGradient(listOf(c.copy(alpha = 0.22f), Palette.Navy900.copy(alpha = 0.6f))))
            .border(1.dp, c.copy(alpha = 0.25f), RoundedCornerShape(26.dp))
            .padding(18.dp),
    ) {
        IconBadge(if (severity == Severity.WARNING || severity == Severity.CRITICAL) Icons.Rounded.WarningAmber else Icons.Rounded.Lightbulb, c, size = 34.dp)
        Spacer(Modifier.height(10.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, color = Palette.Text)
        Spacer(Modifier.height(2.dp))
        Text(body, style = MaterialTheme.typography.bodySmall, color = Palette.TextDim, maxLines = 3)
    }
}

@Composable
private fun BillCard(occ: BillOccurrence, today: LocalDate, color: Color) {
    val days = ChronoUnit.DAYS.between(today, occ.dueDate)
    val urgent = days <= 1
    val ringColor = if (urgent) Palette.Amber else Palette.Mint
    Column(
        Modifier.width(150.dp).clip(RoundedCornerShape(26.dp))
            .background(Brush.linearGradient(listOf(Palette.GlassStrong, Palette.Glass)))
            .border(1.dp, if (urgent) Palette.Amber.copy(alpha = 0.4f) else Palette.Hairline, RoundedCornerShape(26.dp))
            .padding(16.dp),
    ) {
        RingProgress(1f - (days.coerceIn(0, 14) / 14f), ringColor, Modifier.size(52.dp), stroke = 5.dp) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (days < 0) "!" else "$days", style = MaterialTheme.typography.titleMedium.copy(fontFamily = com.randwise.app.ui.theme.Fonts.Display))
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            ColorDot(color, 8)
            Spacer(Modifier.width(6.dp))
            Text(occ.bill.name, style = MaterialTheme.typography.titleSmall, maxLines = 1)
        }
        MoneyText(occ.bill.expectedAmountMinor, style = MaterialTheme.typography.titleLarge, whole = true)
        Text(
            when { days < 0 -> "Overdue"; days == 0L -> "Due today"; days == 1L -> "Tomorrow"; else -> "in $days days" },
            style = MaterialTheme.typography.labelMedium, color = if (urgent) Palette.Amber else Palette.TextDim,
        )
    }
}

@Composable
private fun CycleStats(a: Analysis) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        GlassCard(Modifier.weight(1f)) {
            Eyebrow("Income")
            MoneyText(a.cycleIncome, style = MaterialTheme.typography.headlineSmall, whole = true, color = Palette.Mint)
            Spacer(Modifier.height(6.dp))
            Text("this cycle", style = MaterialTheme.typography.bodySmall, color = Palette.TextFaint)
        }
        GlassCard(Modifier.weight(1f)) {
            Eyebrow("Spent")
            MoneyText(a.cycleSpend, style = MaterialTheme.typography.headlineSmall, whole = true)
            Sparkline(a.cumulativeThisCycle, Palette.Teal, Modifier.fillMaxWidth().height(26.dp).padding(top = 6.dp))
            Text("→ ${Money.formatWhole(a.projectedCycleSpend)} projected", style = MaterialTheme.typography.bodySmall, color = Palette.TextFaint)
        }
    }
}

@Composable
fun TransactionRow(t: Transaction, category: Category?, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .then(if (onClick != null) Modifier.pressable(onClick) else Modifier)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MerchantAvatar(t.merchant, if (t.type.isInternal) Palette.Sky else categoryColor(category?.colorArgb))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(t.merchant, style = MaterialTheme.typography.titleSmall, maxLines = 1)
            Text(
                "${category?.name ?: typeLabel(t)} · ${LocalDate.ofEpochDay(t.date).format(DAY_FMT)}",
                style = MaterialTheme.typography.bodySmall, color = Palette.TextDim, maxLines = 1,
            )
        }
        MoneyText(
            t.signedAmountMinor, signed = true, colorBySign = true,
            style = MaterialTheme.typography.titleSmall.copy(fontFamily = com.randwise.app.ui.theme.Fonts.Display),
        )
    }
}

fun typeLabel(t: Transaction) = when (t.type) {
    TxType.TRANSFER_IN, TxType.TRANSFER_OUT -> "Transfer"
    TxType.CARD_PAYMENT -> "Card payment"
    TxType.REFUND -> "Refund"
    TxType.INCOME -> "Income"
    TxType.EXPENSE -> "Uncategorised"
}

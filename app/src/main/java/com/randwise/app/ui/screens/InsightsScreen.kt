package com.randwise.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.randwise.app.core.Money
import com.randwise.app.domain.Analysis
import com.randwise.app.domain.engine.AffordabilityResult
import com.randwise.app.domain.engine.CashFlowForecaster
import com.randwise.app.domain.engine.Verdict
import com.randwise.app.ui.CoachSource
import com.randwise.app.ui.CoachViewModel
import com.randwise.app.ui.FinanceViewModel
import com.randwise.app.ui.Gutter
import com.randwise.app.ui.NavClearance
import com.randwise.app.ui.components.AdBanner
import com.randwise.app.ui.components.CoachOrb
import com.randwise.app.ui.components.DonutChart
import com.randwise.app.ui.components.DonutSlice
import com.randwise.app.ui.components.EmptyState
import com.randwise.app.ui.components.Eyebrow
import com.randwise.app.ui.components.GlassCard
import com.randwise.app.ui.components.GlowBar
import com.randwise.app.ui.components.HeroAmount
import com.randwise.app.ui.components.IconBadge
import com.randwise.app.ui.components.LineChart
import com.randwise.app.ui.components.LineSeries
import com.randwise.app.ui.components.MoneyText
import com.randwise.app.ui.components.Pill
import com.randwise.app.ui.components.PrimaryButton
import com.randwise.app.ui.components.RingProgress
import com.randwise.app.ui.components.SectionHeader
import com.randwise.app.ui.components.SegmentedControl
import com.randwise.app.ui.components.categoryColor
import com.randwise.app.ui.components.enterAnimation
import com.randwise.app.ui.components.pressable
import com.randwise.app.ui.gutter
import com.randwise.app.ui.theme.Fonts
import com.randwise.app.ui.theme.MoneyStyle
import com.randwise.app.ui.theme.Palette
import com.randwise.app.ui.theme.RW
import com.randwise.app.ui.theme.RWBrush

@Composable
fun InsightsScreen(vm: FinanceViewModel, coach: CoachViewModel) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        SegmentedControl(listOf("Coach", "Trends", "What-if"), tab, { tab = it }, Modifier.gutter().padding(top = 12.dp, bottom = 8.dp))
        Box(Modifier.weight(1f)) {
            when (tab) {
                0 -> CoachTab(vm, coach)
                1 -> TrendsTab(vm)
                else -> WhatIfTab(vm)
            }
        }
    }
}

// ================================================================= Coach

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CoachTab(vm: FinanceViewModel, coach: CoachViewModel) {
    val fin by vm.state.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val state by coach.state.collectAsStateWithLifecycle()
    var input by rememberSaveable { mutableStateOf("") }
    val list = rememberLazyListState()
    val a = fin.analysis ?: return
    LaunchedEffect(state.messages.size, state.sending) {
        val count = state.messages.size + if (state.sending) 1 else 0
        if (count > 0) list.animateScrollToItem(count)
    }
    fun send(q: String) { if (q.isNotBlank()) { coach.ask(q, fin.snapshot, a); input = "" } }

    val imeOpen = WindowInsets.isImeVisible
    Column(Modifier.fillMaxSize().imePadding()) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, contentPadding = PaddingValues(horizontal = Gutter, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Column(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    CoachOrb(Modifier.size(if (state.messages.isEmpty()) 120.dp else 72.dp), thinking = state.sending)
                    Spacer(Modifier.height(16.dp))
                    Text("Your money coach", style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(6.dp))
                    Pill(
                        if (settings.consentAiInterpretation) "Gemini · anonymised totals only" else "On-device · fully private",
                        if (settings.consentAiInterpretation) Palette.Violet else Palette.Mint,
                    )
                }
            }
            if (state.messages.isEmpty()) {
                item {
                    val prompts = listOf(
                        Triple(Icons.Rounded.Speed, "How much can I spend today?", Palette.Mint),
                        Triple(Icons.Rounded.ShoppingBag, "Can I afford R1500?", Palette.Teal),
                        Triple(Icons.Rounded.WarningAmber, "Which budgets are at risk?", Palette.Amber),
                        Triple(Icons.Rounded.CalendarMonth, "What bills are left?", Palette.Sky),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        prompts.chunked(2).forEachIndexed { r, row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                row.forEachIndexed { c, (icon, text, color) ->
                                    GlassCard(Modifier.weight(1f).enterAnimation(r * 2 + c), onClick = { send(text) }, padding = 14.dp) {
                                        IconBadge(icon, color, size = 32.dp)
                                        Spacer(Modifier.height(10.dp))
                                        Text(text, style = MaterialTheme.typography.titleSmall, minLines = 2)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            items(state.messages) { m -> ChatBubble(m.source, m.text, m.highlights, m.note) }
            if (state.sending) item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CoachOrb(Modifier.size(28.dp), thinking = true)
                    Spacer(Modifier.width(10.dp))
                    com.randwise.app.ui.components.TypingDots()
                }
            }
        }
        // Composer: glass pill with an emerald send orb. Sits above the floating nav.
        Row(
            Modifier.gutter().padding(top = 8.dp, bottom = if (imeOpen) 8.dp else NavClearance - 28.dp)
                .navigationBarsPadding()
                .fillMaxWidth().clip(CircleShape).background(Palette.GlassStrong)
                .border(1.dp, Palette.Hairline, CircleShape).padding(start = 20.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) {
                if (input.isEmpty()) Text("Ask about your money…", color = Palette.TextFaint, style = MaterialTheme.typography.bodyLarge)
                BasicTextField(
                    input, { input = it }, textStyle = MaterialTheme.typography.bodyLarge.copy(color = Palette.Text),
                    cursorBrush = SolidColor(Palette.Mint), maxLines = 4, modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.size(46.dp).clip(CircleShape)
                    .background(if (input.isNotBlank() && !state.sending) RWBrush.emerald else Brush.linearGradient(listOf(Palette.Glass, Palette.Glass)))
                    .pressable({ send(input) }, enabled = input.isNotBlank() && !state.sending),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.AutoMirrored.Rounded.Send, "Send", tint = if (input.isNotBlank()) Palette.Ink else Palette.TextFaint, modifier = Modifier.size(20.dp)) }
        }
    }
}

@Composable
private fun ChatBubble(source: CoachSource, text: String, highlights: List<String>, note: String?) {
    val mine = source == CoachSource.USER
    Row(Modifier.fillMaxWidth().enterAnimation(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start, verticalAlignment = Alignment.Bottom) {
        if (!mine) {
            CoachOrb(Modifier.size(28.dp))
            Spacer(Modifier.width(8.dp))
        }
        val shape = RoundedCornerShape(22.dp, 22.dp, if (mine) 6.dp else 22.dp, if (mine) 22.dp else 6.dp)
        Column(
            Modifier.widthIn(max = 300.dp).clip(shape)
                .background(if (mine) RWBrush.emerald else Brush.linearGradient(listOf(Palette.GlassStrong, Palette.Glass)))
                .then(if (mine) Modifier else Modifier.border(1.dp, Palette.Hairline, shape))
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            if (!mine) {
                Eyebrow(if (source == CoachSource.GEMINI) "Gemini coach" else "On-device coach", Palette.Mint)
                Spacer(Modifier.height(4.dp))
            }
            Text(text, style = MaterialTheme.typography.bodyMedium, color = if (mine) Palette.Ink else Palette.Text)
            highlights.forEach {
                Spacer(Modifier.height(6.dp))
                Row { Text("→ ", color = Palette.Mint); Text(it, style = MaterialTheme.typography.bodySmall) }
            }
            if (note != null) {
                Spacer(Modifier.height(8.dp))
                Text(note, style = MaterialTheme.typography.labelSmall, color = Palette.TextFaint)
            }
        }
    }
}

// ================================================================= Trends

@Composable
private fun TrendsTab(vm: FinanceViewModel) {
    val fin by vm.state.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val a = fin.analysis ?: return
    var horizon by rememberSaveable { mutableIntStateOf(2) }
    val days = CashFlowForecaster.HORIZONS[horizon]
    val forecast = remember(a, days) { CashFlowForecaster.forecast(a.forecastInputs, days) }

    LazyColumn(contentPadding = PaddingValues(start = Gutter, end = Gutter, top = 8.dp, bottom = NavClearance), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            GlassCard(Modifier.enterAnimation(0)) {
                Eyebrow("This cycle")
                Text("Where it went", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(16.dp))
                if (a.categorySpend.isEmpty()) EmptyState("No spending yet", "Add or import transactions to see your breakdown.")
                else {
                    val top = a.categorySpend.take(6)
                    val slices = top.map { DonutSlice(it.category?.name ?: "Uncategorised", it.amount, categoryColor(it.category?.colorArgb)) }
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        DonutChart(slices, Modifier.size(210.dp), "Spent", a.cycleSpend)
                    }
                    Spacer(Modifier.height(18.dp))
                    val max = slices.maxOf { it.amount }.coerceAtLeast(1)
                    slices.forEach { s ->
                        Column(Modifier.padding(vertical = 6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(s.label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                Text("${Money.ratioBp(s.amount, a.cycleSpend.coerceAtLeast(1)) / 100}%", style = MaterialTheme.typography.labelMedium, color = Palette.TextFaint)
                                Spacer(Modifier.width(10.dp))
                                MoneyText(s.amount, style = MaterialTheme.typography.titleSmall, whole = true)
                            }
                            Spacer(Modifier.height(6.dp))
                            GlowBar(s.amount.toFloat() / max, s.color, height = 6.dp)
                        }
                    }
                }
            }
        }
        item {
            GlassCard(Modifier.enterAnimation(1)) {
                Eyebrow("Spending pace")
                Row(verticalAlignment = Alignment.Bottom) {
                    MoneyText(a.projectedCycleSpend, style = MaterialTheme.typography.headlineSmall, whole = true, modifier = Modifier.weight(1f))
                    Text("projected this cycle", style = MaterialTheme.typography.bodySmall, color = Palette.TextFaint)
                }
                Spacer(Modifier.height(14.dp))
                LineChart(
                    listOf(
                        LineSeries("This cycle", a.cumulativeThisCycle, Palette.Mint),
                        LineSeries("Last cycle", a.cumulativePrevCycle, Palette.TextFaint, dashed = true),
                    ),
                    xCount = maxOf(a.cycle.totalDays.toInt(), a.cumulativePrevCycle.size),
                )
            }
        }
        item {
            GlassCard(Modifier.enterAnimation(2)) {
                Eyebrow("Cash-flow forecast")
                Row(verticalAlignment = Alignment.Bottom) {
                    MoneyText(forecast.endBalance, style = MaterialTheme.typography.headlineSmall, whole = true, modifier = Modifier.weight(1f))
                    Text("in $days days", style = MaterialTheme.typography.bodySmall, color = Palette.TextFaint)
                }
                Spacer(Modifier.height(12.dp))
                SegmentedControl(CashFlowForecaster.HORIZONS.map { "${it}d" }, horizon, { horizon = it })
                Spacer(Modifier.height(16.dp))
                LineChart(listOf(LineSeries("Projected balance", forecast.points.map { it.balance }, Palette.Teal)), reference = a.forecastInputs.safetyBuffer)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("Low ${Money.formatWhole(forecast.lowest.balance)} · ${forecast.lowest.date.dayOfMonth}/${forecast.lowest.date.monthValue}", if (forecast.lowest.balance < a.forecastInputs.safetyBuffer) Palette.Amber else Palette.Mint)
                    forecast.firstNegative?.let { Pill("Negative ${it.dayOfMonth}/${it.monthValue}", Palette.Coral, icon = Icons.Rounded.Error) }
                }
            }
        }
        item { SectionHeader("Behaviour scorecard", eyebrow = "Last 30–90 days") }
        item {
            val b = a.behavior
            val volColor = when { b.volatilityScore >= 65 -> Palette.Coral; b.volatilityScore >= 35 -> Palette.Amber; else -> Palette.Mint }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GlassCard(Modifier.weight(1f)) {
                        RingProgress(b.volatilityScore / 100f, volColor, Modifier.size(64.dp)) {
                            Text("${b.volatilityScore}", style = MaterialTheme.typography.titleLarge.copy(fontFamily = Fonts.Display))
                        }
                        Spacer(Modifier.height(12.dp))
                        Text("Volatility", style = MaterialTheme.typography.titleSmall)
                        Text(when { b.volatilityScore >= 65 -> "Erratic swings"; b.volatilityScore >= 35 -> "Moderate"; else -> "Steady" }, style = MaterialTheme.typography.bodySmall, color = volColor)
                    }
                    ScoreTile(
                        Modifier.weight(1f), Icons.Rounded.TrendingUp, "Payday splurge",
                        if (b.postPaydaySpikeBp == 0L) "—" else "${b.postPaydaySpikeBp / 100}%",
                        if (b.postPaydaySpike) "vs normal days" else "of a normal day", if (b.postPaydaySpike) Palette.Amber else Palette.Mint,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ScoreTile(
                        Modifier.weight(1f), Icons.Rounded.Payments, "Fee traps",
                        if (b.feeCount == 0) "0" else Money.formatWhole(b.feeTotal), if (b.feeCount == 0) "None — great" else "${b.feeCount} penalty fee(s)",
                        if (b.feeCount == 0) Palette.Mint else Palette.Coral,
                    )
                    ScoreTile(
                        Modifier.weight(1f), Icons.Rounded.Savings, "Lifestyle creep",
                        if (b.lifestyleInflationBp == 0L) "0%" else Money.formatPercentBp(b.lifestyleInflationBp), "vs 3-cycle average",
                        if (b.lifestyleInflationBp >= 1000) Palette.Amber else Palette.Mint,
                    )
                }
                if (b.subscriptionCreep.isNotEmpty()) {
                    GlassCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconBadge(Icons.Rounded.Subscriptions, Palette.Violet)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Subscription creep", style = MaterialTheme.typography.titleSmall)
                                Text(b.subscriptionCreep.joinToString { "${it.first} +${Money.formatPercentBp(it.second)}" }, style = MaterialTheme.typography.bodySmall, color = Palette.TextDim)
                            }
                        }
                    }
                }
            }
        }
        if (settings.showAds) item { AdBanner() }
    }
}

@Composable
private fun ScoreTile(modifier: Modifier, icon: ImageVector, title: String, value: String, caption: String, color: Color) {
    GlassCard(modifier) {
        IconBadge(icon, color, size = 36.dp)
        Spacer(Modifier.height(12.dp))
        Text(value, style = MaterialTheme.typography.headlineSmall.merge(MoneyStyle), color = color, maxLines = 1)
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(caption, style = MaterialTheme.typography.bodySmall, color = Palette.TextDim)
    }
}

// ================================================================= What-if (F06)

@Composable
private fun WhatIfTab(vm: FinanceViewModel) {
    val fin by vm.state.collectAsStateWithLifecycle()
    val a: Analysis = fin.analysis ?: return
    var digits by rememberSaveable { mutableStateOf("") }
    var mode by rememberSaveable { mutableIntStateOf(0) }
    var result by remember { mutableStateOf<AffordabilityResult?>(null) }
    val scroll = rememberScrollState()
    val amount = Money.parse(digits.ifEmpty { "0" }) ?: 0
    LaunchedEffect(result) { if (result != null) scroll.animateScrollTo(scroll.maxValue) }

    fun press(k: String) {
        result = null
        digits = when (k) {
            "⌫" -> digits.dropLast(1)
            "," -> if (digits.contains(',') || digits.isEmpty()) digits else "$digits,"
            else -> if (digits.substringAfter(',', "").length >= 2 || digits.length >= 9) digits else if (digits == "0") k else digits + k
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(scroll).gutter().padding(bottom = NavClearance),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(8.dp))
        Eyebrow("What if I buy…")
        Spacer(Modifier.height(8.dp))
        HeroAmount(amount, size = 52, color = if (amount > 0) Palette.Text else Palette.TextFaint)
        Spacer(Modifier.height(4.dp))
        Text("Safe-to-spend now ${Money.formatWhole(a.safeToSpend.safeToSpend)} · ${a.safeToSpend.daysUntilPayday} days to payday", style = MaterialTheme.typography.bodySmall, color = Palette.TextDim)
        Spacer(Modifier.height(16.dp))
        SegmentedControl(listOf("Once-off", "Every month"), mode, { mode = it; result = null })
        Spacer(Modifier.height(16.dp))
        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", ",", "0", "⌫")
        keys.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { k ->
                    Box(
                        Modifier.weight(1f).height(58.dp).clip(RoundedCornerShape(20.dp)).background(Palette.Glass)
                            .border(1.dp, Palette.Hairline, RoundedCornerShape(20.dp)).pressable({ press(k) }),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (k == "⌫") Icon(Icons.AutoMirrored.Rounded.Backspace, "Delete", tint = Palette.TextDim)
                        else Text(k, style = MaterialTheme.typography.headlineSmall)
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        PrimaryButton("Check the impact", { result = vm.checkAffordability(amount, mode == 1) }, enabled = amount > 0)
        AnimatedVisibility(result != null, enter = fadeIn() + slideInVertically { it / 3 }) {
            result?.let { VerdictCard(it, Modifier.padding(top = 18.dp)) }
        }
    }
}

@Composable
private fun VerdictCard(r: AffordabilityResult, modifier: Modifier) {
    val (color, icon, title) = when (r.verdict) {
        Verdict.AFFORDABLE -> Triple(Palette.Mint, Icons.Rounded.CheckCircle, "Go for it")
        Verdict.TIGHT -> Triple(Palette.Amber, Icons.Rounded.WarningAmber, "Possible, but tight")
        Verdict.NOT_AFFORDABLE -> Triple(Palette.Coral, Icons.Rounded.Cancel, "Not right now")
    }
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
            .background(Brush.verticalGradient(listOf(color.copy(alpha = 0.20f), color.copy(alpha = 0.04f))))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(28.dp))
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, color, size = 48.dp)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(title, style = MaterialTheme.typography.headlineSmall, color = color)
                Text(if (r.recurringMonthly) "As a monthly commitment" else "As a once-off purchase", style = MaterialTheme.typography.bodySmall, color = Palette.TextDim)
            }
        }
        Spacer(Modifier.height(18.dp))
        val scale = maxOf(r.safeToSpendBefore, 1L).toFloat()
        CompareRow("Safe-to-spend before", r.safeToSpendBefore, 1f, Palette.TextDim)
        CompareRow("Safe-to-spend after", r.safeToSpendAfter, r.safeToSpendAfter / scale, color)
        CompareRow("Lowest balance ahead", r.lowestWith.balance, (r.lowestWith.balance / maxOf(r.lowestWithout.balance, 1L).toFloat()), color)
        Spacer(Modifier.height(10.dp))
        r.explanations.forEach { line ->
            Row(Modifier.padding(vertical = 4.dp)) {
                Box(Modifier.padding(top = 7.dp).size(6.dp).clip(CircleShape).background(color))
                Spacer(Modifier.width(10.dp))
                Text(line, style = MaterialTheme.typography.bodyMedium, color = Palette.Text.copy(alpha = 0.9f))
            }
        }
    }
}

@Composable
private fun CompareRow(label: String, value: Long, fraction: Float, color: Color) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Row {
            Text(label, style = MaterialTheme.typography.bodySmall, color = Palette.TextDim, modifier = Modifier.weight(1f))
            MoneyText(value, style = MaterialTheme.typography.titleSmall, whole = true, color = if (value < 0) Palette.Coral else Palette.Text)
        }
        Spacer(Modifier.height(6.dp))
        GlowBar(fraction, color, height = 6.dp)
    }
}

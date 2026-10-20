package com.randwise.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.randwise.app.core.Money
import com.randwise.app.ui.theme.Fonts
import com.randwise.app.ui.theme.Palette
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// ---------------------------------------------------------------- Safe-to-spend gauge

/**
 * Speedometer of 56 rounded ticks across 260°. Filled ticks fade emerald → teal and the
 * leading tick carries a glow. Fill animates from zero on first show.
 */
@Composable
fun SegmentedGauge(
    fraction: Float,
    accent: Color,
    modifier: Modifier = Modifier,
    ticks: Int = 56,
    content: @Composable () -> Unit,
) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(fraction) { anim.animateTo(fraction.coerceIn(0f, 1f), tween(1300, easing = FastOutSlowInEasing)) }
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sweep = 260f
            val start = 140f
            val r = size.minDimension / 2f
            val outer = r * 0.98f
            val inner = r * 0.84f
            val lit = (anim.value * ticks).toInt()
            for (i in 0 until ticks) {
                val angle = Math.toRadians((start + sweep * i / (ticks - 1)).toDouble())
                val c = Offset(size.width / 2, size.height / 2)
                val p1 = Offset(c.x + inner * cos(angle).toFloat(), c.y + inner * sin(angle).toFloat())
                val p2 = Offset(c.x + outer * cos(angle).toFloat(), c.y + outer * sin(angle).toFloat())
                val on = i < lit
                val tColor = if (on) lerp(accent, Palette.Teal, i.toFloat() / ticks) else Color.White.copy(alpha = 0.08f)
                drawLine(tColor, p1, p2, strokeWidth = r * 0.035f, cap = StrokeCap.Round)
                if (on && i == lit - 1) {
                    drawCircle(Brush.radialGradient(listOf(tColor.copy(alpha = 0.55f), Color.Transparent), p2, r * 0.16f), r * 0.16f, p2)
                }
            }
            // Inner hairline ring for depth.
            drawCircle(Color.White.copy(alpha = 0.05f), r * 0.74f, style = Stroke(1.dp.toPx()))
        }
        content()
    }
}

private fun lerp(a: Color, b: Color, t: Float) = Color(
    a.red + (b.red - a.red) * t, a.green + (b.green - a.green) * t, a.blue + (b.blue - a.blue) * t, a.alpha + (b.alpha - a.alpha) * t,
)

// ---------------------------------------------------------------- Payday runway

data class RunwayPin(val date: LocalDate, val label: String, val amount: Long, val paid: Boolean)

/**
 * A timeline from the last payday to the next: elapsed days glow, today pulses, and each bill is
 * pinned on the day it leaves the account.
 */
@Composable
fun PaydayRunway(start: LocalDate, nextPayday: LocalDate, today: LocalDate, pins: List<RunwayPin>, modifier: Modifier = Modifier) {
    val total = ChronoUnit.DAYS.between(start, nextPayday).toFloat().coerceAtLeast(1f)
    val elapsed = ChronoUnit.DAYS.between(start, today).toFloat().coerceIn(0f, total)
    val pulse = rememberInfiniteTransition(label = "runway").animateFloat(0.4f, 1f, infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "p")
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontFamily = Fonts.Body, fontSize = 10.sp, color = Palette.TextDim)
    val moneyStyle = TextStyle(fontFamily = Fonts.Display, fontSize = 11.sp, color = Palette.Text)
    Canvas(modifier.fillMaxWidth().height(96.dp)) {
        val y = size.height * 0.62f
        val pad = 8.dp.toPx()
        val w = size.width - pad * 2
        fun x(d: Float) = pad + w * d / total
        // Track
        drawLine(Color.White.copy(alpha = 0.08f), Offset(pad, y), Offset(pad + w, y), 6.dp.toPx(), StrokeCap.Round)
        drawLine(
            Brush.horizontalGradient(listOf(Palette.Emerald.copy(alpha = 0.4f), Palette.Mint), startX = pad, endX = x(elapsed)),
            Offset(pad, y), Offset(x(elapsed), y), 6.dp.toPx(), StrokeCap.Round,
        )
        // Day ticks
        for (d in 0..total.toInt()) {
            drawCircle(Color.White.copy(alpha = if (d <= elapsed) 0f else 0.12f), 1.4.dp.toPx(), Offset(x(d.toFloat()), y + 12.dp.toPx()))
        }
        // Bill pins: every bill gets a dot; bills within ~12% of the cycle share one label.
        val placed = pins.sortedBy { it.date }.map { ChronoUnit.DAYS.between(start, it.date).toFloat().coerceIn(0f, total) to it }
        placed.forEach { (d, pin) -> drawCircle(if (pin.paid) Palette.TextFaint else Palette.Amber, 3.5.dp.toPx(), Offset(x(d), y)) }
        val clusters = mutableListOf<MutableList<Pair<Float, RunwayPin>>>()
        for (p in placed) {
            val last = clusters.lastOrNull()
            if (last != null && p.first - last.first().first <= total * 0.12f) last += p else clusters += mutableListOf(p)
        }
        clusters.forEachIndexed { i, cluster ->
            val px = x(cluster.map { it.first }.average().toFloat())
            val allPaid = cluster.all { it.second.paid }
            val color = if (allPaid) Palette.TextFaint else Palette.Amber
            val top = if (i % 2 == 0) y - 34.dp.toPx() else y - 56.dp.toPx()
            val amount = cluster.filter { !it.second.paid || allPaid }.sumOf { it.second.amount }
            val text = Money.formatWhole(amount) + if (cluster.size > 1) " · ${cluster.size}" else ""
            drawLine(color.copy(alpha = 0.5f), Offset(px, top + 17.dp.toPx()), Offset(px, y - 6.dp.toPx()), 1.dp.toPx())
            drawLabel(measurer, text, moneyStyle.copy(color = if (allPaid) Palette.TextFaint else Palette.Text), px, top, size.width)
        }
        // Today
        val tx = x(elapsed)
        drawCircle(Palette.Mint.copy(alpha = 0.25f * pulse.value), 12.dp.toPx(), Offset(tx, y))
        drawCircle(Palette.White, 5.dp.toPx(), Offset(tx, y))
        drawLabel(measurer, "Today", labelStyle.copy(color = Palette.Mint), tx, y + 18.dp.toPx(), size.width)
        // Payday flag
        val fx = x(total)
        drawCircle(Palette.Mint, 6.dp.toPx(), Offset(fx, y))
        drawCircle(Palette.Navy950, 2.5.dp.toPx(), Offset(fx, y))
        drawLabel(measurer, "Payday", labelStyle, fx, y + 18.dp.toPx(), size.width)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLabel(m: TextMeasurer, text: String, style: TextStyle, cx: Float, top: Float, maxW: Float) {
    val layout = m.measure(text, style)
    val left = (cx - layout.size.width / 2f).coerceIn(0f, maxW - layout.size.width)
    drawText(layout, topLeft = Offset(left, top))
}

// ---------------------------------------------------------------- Rings

@Composable
fun RingProgress(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
    stroke: Dp = 6.dp,
    track: Color = Color.White.copy(alpha = 0.08f),
    content: @Composable () -> Unit = {},
) {
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(1000, easing = FastOutSlowInEasing), label = "ring")
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val s = stroke.toPx()
            val d = size.minDimension - s
            val tl = Offset((size.width - d) / 2, (size.height - d) / 2)
            drawArc(track, 0f, 360f, false, tl, Size(d, d), style = Stroke(s))
            if (f > 0f) {
                rotate(-90f) {
                    drawArc(
                        Brush.sweepGradient(listOf(color.copy(alpha = 0.5f), color, color)),
                        0f, 360f * f, false, tl, Size(d, d), style = Stroke(s, cap = StrokeCap.Round),
                    )
                }
            }
        }
        content()
    }
}

@Composable
fun ScoreRing(score: Int, color: Color, modifier: Modifier = Modifier.size(64.dp)) {
    RingProgress(score / 100f, color, modifier) {
        Text("$score", style = MaterialTheme.typography.titleMedium.copy(fontFamily = Fonts.Display))
    }
}

// ---------------------------------------------------------------- Donut

data class DonutSlice(val label: String, val amount: Long, val color: Color)

@Composable
fun DonutChart(slices: List<DonutSlice>, modifier: Modifier = Modifier, centerLabel: String, centerValue: Long) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(slices) { anim.snapTo(0f); anim.animateTo(1f, tween(1100, easing = FastOutSlowInEasing)) }
    val total = slices.sumOf { it.amount }.coerceAtLeast(1)
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val s = size.minDimension * 0.13f
            val d = size.minDimension - s
            val tl = Offset((size.width - d) / 2, (size.height - d) / 2)
            drawArc(Color.White.copy(alpha = 0.06f), 0f, 360f, false, tl, Size(d, d), style = Stroke(s))
            var start = -90f
            for (slice in slices) {
                val sweep = 360f * slice.amount / total * anim.value
                drawArc(slice.color, start + 2f, (sweep - 4f).coerceAtLeast(0.5f), false, tl, Size(d, d), style = Stroke(s, cap = StrokeCap.Round))
                start += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Eyebrow(centerLabel)
            Text(Money.formatWhole(centerValue), style = MaterialTheme.typography.headlineSmall)
        }
    }
}

// ---------------------------------------------------------------- Area / line chart

data class LineSeries(val label: String, val values: List<Long>, val color: Color, val dashed: Boolean = false, val fill: Boolean = true)

/** Smooth area chart: cubic curves, gradient fill under the primary series, glowing end point. */
@Composable
fun LineChart(
    series: List<LineSeries>,
    modifier: Modifier = Modifier,
    xCount: Int = series.maxOfOrNull { it.values.size } ?: 0,
    reference: Long? = null,
    referenceColor: Color = Palette.Amber,
    height: Dp = 170.dp,
) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(series.map { it.values.size }) { anim.snapTo(0f); anim.animateTo(1f, tween(1200, easing = FastOutSlowInEasing)) }
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val all = series.flatMap { it.values } + listOfNotNull(reference) + 0L
            val max = all.max().coerceAtLeast(1)
            val min = all.min()
            val range = (max - min).coerceAtLeast(1).toFloat()
            val top = 10.dp.toPx()
            val h = size.height - top
            fun y(v: Long) = top + h - (v - min) / range * h
            val n = (xCount - 1).coerceAtLeast(1)
            for (i in 0..3) {
                val gy = top + h * i / 3f
                drawLine(Color.White.copy(alpha = 0.05f), Offset(0f, gy), Offset(size.width, gy), 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 8f)))
            }
            if (min < 0) drawLine(Palette.Coral.copy(alpha = 0.5f), Offset(0f, y(0)), Offset(size.width, y(0)), 1.5f)
            reference?.let {
                drawLine(referenceColor.copy(alpha = 0.8f), Offset(0f, y(it)), Offset(size.width, y(it)), 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)))
            }
            for (s in series) {
                if (s.values.size < 2) continue
                val count = (s.values.size * anim.value).toInt().coerceIn(2, s.values.size)
                val pts = s.values.take(count).mapIndexed { i, v -> Offset(size.width * i / n, y(v)) }
                val path = Path().apply {
                    moveTo(pts[0].x, pts[0].y)
                    for (i in 1 until pts.size) {
                        val p0 = pts[i - 1]; val p1 = pts[i]
                        val cx = (p0.x + p1.x) / 2
                        cubicTo(cx, p0.y, cx, p1.y, p1.x, p1.y)
                    }
                }
                if (s.fill && !s.dashed) {
                    val area = Path().apply {
                        addPath(path)
                        lineTo(pts.last().x, size.height)
                        lineTo(pts.first().x, size.height)
                        close()
                    }
                    drawPath(area, Brush.verticalGradient(listOf(s.color.copy(alpha = 0.32f), Color.Transparent), startY = top, endY = size.height))
                }
                drawPath(
                    path, s.color,
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, pathEffect = if (s.dashed) PathEffect.dashPathEffect(floatArrayOf(10f, 10f)) else null),
                )
                if (!s.dashed) {
                    val end = pts.last()
                    drawCircle(Brush.radialGradient(listOf(s.color.copy(alpha = 0.5f), Color.Transparent), end, 16.dp.toPx()), 16.dp.toPx(), end)
                    drawCircle(Palette.White, 3.5.dp.toPx(), end)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            series.forEach { s -> Legend(s.label, s.color) }
            if (reference != null) Legend("Safety buffer", referenceColor)
        }
    }
}

@Composable
fun Legend(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(14.dp).height(4.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = Palette.TextDim)
    }
}

/** Tiny trend line for stat tiles. */
@Composable
fun Sparkline(values: List<Long>, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val max = values.max().coerceAtLeast(1)
        val min = values.min()
        val range = (max - min).coerceAtLeast(1).toFloat()
        val pts = values.mapIndexed { i, v -> Offset(size.width * i / (values.size - 1), size.height - (v - min) / range * size.height) }
        val path = Path().apply { moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) } }
        val area = Path().apply { addPath(path); lineTo(size.width, size.height); lineTo(0f, size.height); close() }
        drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.3f), Color.Transparent)))
        drawPath(path, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
    }
}

// ---------------------------------------------------------------- Composition bar

data class BarSegment(val label: String, val amount: Long, val color: Color)

/** Horizontal stacked bar showing how the balance splits into bills, savings, buffer and free cash. */
@Composable
fun StackedBar(segments: List<BarSegment>, modifier: Modifier = Modifier, height: Dp = 12.dp) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(segments) { anim.snapTo(0f); anim.animateTo(1f, tween(1000, easing = FastOutSlowInEasing)) }
    val total = segments.sumOf { it.amount.coerceAtLeast(0) }.coerceAtLeast(1)
    Canvas(modifier.fillMaxWidth().height(height)) {
        var x = 0f
        val gap = 3.dp.toPx()
        val visible = segments.filter { it.amount > 0 }
        visible.forEachIndexed { i, s ->
            val w = (size.width - gap * (visible.size - 1)) * s.amount / total * anim.value
            drawRoundRect(s.color, Offset(x, 0f), Size(w.coerceAtLeast(0f), size.height), CornerRadius(size.height / 2))
            x += w + gap
        }
        if (visible.isEmpty()) drawRoundRect(Color.White.copy(alpha = 0.08f), cornerRadius = CornerRadius(size.height / 2))
    }
}

// ---------------------------------------------------------------- AI orb & typing

/** Living gradient orb that represents the coach; it spins faster while thinking. */
@Composable
fun CoachOrb(modifier: Modifier = Modifier, thinking: Boolean = false) {
    val t = rememberInfiniteTransition(label = "orb")
    val spin by t.animateFloat(0f, 360f, infiniteRepeatable(tween(if (thinking) 1800 else 7000, easing = LinearEasing)), label = "spin")
    val breathe by t.animateFloat(0.92f, 1.04f, infiniteRepeatable(tween(2200), RepeatMode.Reverse), label = "breathe")
    Canvas(modifier) {
        val r = size.minDimension / 2 * breathe
        val c = Offset(size.width / 2, size.height / 2)
        drawCircle(Brush.radialGradient(listOf(Palette.Mint.copy(alpha = 0.35f), Color.Transparent), c, r * 1.5f), r * 1.5f, c)
        rotate(spin, c) {
            drawCircle(Brush.sweepGradient(listOf(Palette.Emerald, Palette.Teal, Palette.Sky, Palette.Violet, Palette.Emerald), c), r, c)
        }
        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.55f), Color.Transparent), Offset(c.x - r * 0.35f, c.y - r * 0.4f), r * 0.8f), r, c)
        val angle = (spin / 180f * PI).toFloat()
        drawCircle(Color.White.copy(alpha = 0.8f), r * 0.08f, Offset(c.x + r * 0.55f * cos(angle), c.y + r * 0.55f * sin(angle)))
    }
}

@Composable
fun TypingDots(modifier: Modifier = Modifier, color: Color = Palette.Mint) {
    val t = rememberInfiniteTransition(label = "dots")
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        repeat(3) { i ->
            val a by t.animateFloat(0.25f, 1f, infiniteRepeatable(tween(600, delayMillis = i * 160), RepeatMode.Reverse), label = "d$i")
            Box(Modifier.size(8.dp).clip(CircleShape).background(color.copy(alpha = a)))
        }
    }
}

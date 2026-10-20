package com.randwise.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.randwise.app.core.Money
import com.randwise.app.domain.engine.BudgetStatus
import com.randwise.app.domain.engine.Severity
import com.randwise.app.ui.theme.Fonts
import com.randwise.app.ui.theme.LocalReduceMotion
import com.randwise.app.ui.theme.MoneyStyle
import com.randwise.app.ui.theme.Palette
import com.randwise.app.ui.theme.RW
import com.randwise.app.ui.theme.RWBrush
import kotlin.math.abs

// ---------------------------------------------------------------- Aurora backdrop

/**
 * Slowly drifting emerald / teal / blue light behind the whole app. Pure radial gradients, so it
 * renders identically on every API level (no RenderEffect blur needed).
 */
@Composable
fun AuroraBackground(modifier: Modifier = Modifier, intensity: Float = 1f) {
    val t = rememberInfiniteTransition(label = "aurora")
    val reduce = LocalReduceMotion.current
    val aState = t.animateFloat(0f, 1f, infiniteRepeatable(tween(14_000, easing = LinearEasing), RepeatMode.Reverse), label = "a")
    val bState = t.animateFloat(0f, 1f, infiniteRepeatable(tween(19_000, easing = LinearEasing), RepeatMode.Reverse), label = "b")
    Canvas(modifier.fillMaxSize().background(Palette.Navy950)) {
        // Read in the draw phase so the drift only redraws, never recomposes.
        val a = if (reduce) 0.5f else aState.value
        val b = if (reduce) 0.5f else bState.value
        val w = size.width
        val h = size.height
        fun blob(center: Offset, radius: Float, color: Color, alpha: Float) = drawCircle(
            Brush.radialGradient(listOf(color.copy(alpha = alpha * intensity), Color.Transparent), center, radius),
            radius, center,
        )
        blob(Offset(w * (0.15f + 0.25f * a), h * (0.02f + 0.06f * b)), w * 0.95f, Palette.Emerald, 0.30f)
        blob(Offset(w * (0.95f - 0.2f * b), h * (0.12f + 0.05f * a)), w * 0.8f, Palette.Teal, 0.20f)
        blob(Offset(w * (0.5f + 0.3f * (a - b)), h * (0.42f + 0.08f * a)), w * 0.9f, Palette.Sky, 0.10f)
        blob(Offset(w * 0.1f, h * 0.95f), w * 0.8f, Palette.Emerald, 0.07f)
        // Vignette pulls focus to the centre.
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Palette.Ink.copy(alpha = 0.55f)), startY = h * 0.55f, endY = h))
    }
}

// ---------------------------------------------------------------- Interaction

/** Springy press-scale feedback for anything tappable. */
fun Modifier.pressable(onClick: () -> Unit, enabled: Boolean = true): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), label = "press")
    this.graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = source, indication = null, enabled = enabled, onClick = onClick)
}

/** Fade + rise entrance, staggered by [index]. */
fun Modifier.enterAnimation(index: Int = 0): Modifier = composed {
    if (LocalReduceMotion.current) return@composed this
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(40L * index.coerceAtMost(10))
        progress.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
    }
    val dy = with(LocalDensity.current) { 18.dp.toPx() }
    this.graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * dy
    }
}

// ---------------------------------------------------------------- Surfaces

val CardShape = RoundedCornerShape(28.dp)

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = CardShape,
    onClick: (() -> Unit)? = null,
    padding: Dp = 18.dp,
    brush: Brush = RWBrush.card,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.pressable(onClick) else Modifier)
            .clip(shape)
            .background(brush)
            .border(BorderStroke(1.dp, RWBrush.glassBorder), shape)
            .padding(padding),
        content = content,
    )
}

/** Kept for screens that want a titled card. */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    action: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    GlassCard(modifier) {
        if (title != null || action != null) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (title != null) Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                action?.invoke(this)
            }
            Spacer(Modifier.height(12.dp))
        }
        content()
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, eyebrow: String? = null, action: (@Composable RowScope.() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            if (eyebrow != null) Eyebrow(eyebrow)
            Text(title, style = MaterialTheme.typography.headlineSmall, color = Palette.Text)
        }
        action?.invoke(this)
    }
}

@Composable
fun Eyebrow(text: String, color: Color = Palette.TextFaint) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.6.sp), color = color)
}

@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier, icon: ImageVector? = null, filled: Boolean = false) {
    Row(
        modifier
            .clip(CircleShape)
            .background(if (filled) color else color.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (filled) Palette.Ink else color, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(text, style = MaterialTheme.typography.labelMedium, color = if (filled) Palette.Ink else color, maxLines = 1)
    }
}

@Composable
fun IconBadge(icon: ImageVector, tint: Color, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    Box(
        modifier.size(size).clip(RoundedCornerShape(size * 0.34f)).background(tint.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = tint, modifier = Modifier.size(size * 0.5f)) }
}

/** Squircle with initials — gives each merchant a recognisable face in lists. */
@Composable
fun MerchantAvatar(name: String, color: Color, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    val initials = name.split(' ', '*', '-', '.').filter { it.isNotBlank() && it.first().isLetter() }
        .take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "R" }
    Box(
        modifier.size(size).clip(RoundedCornerShape(size * 0.36f))
            .background(Brush.linearGradient(listOf(color.copy(alpha = 0.38f), color.copy(alpha = 0.12f)))),
        contentAlignment = Alignment.Center,
    ) {
        Text(initials, style = TextStyle(fontFamily = Fonts.Display, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.34f).sp), color = Palette.Text)
    }
}

@Composable
fun ColorDot(color: Color, size: Int = 10) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(color))
}

// ---------------------------------------------------------------- Money

@Composable
fun MoneyText(
    minor: Long,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    color: Color = Color.Unspecified,
    signed: Boolean = false,
    colorBySign: Boolean = false,
    whole: Boolean = false,
) {
    val c = when {
        !colorBySign -> color
        minor > 0 -> RW.colors.positive
        else -> Palette.Text
    }
    val text = if (whole) Money.formatWhole(minor).let { if (signed && minor > 0) "+$it" else it } else Money.format(minor, showSign = signed)
    Text(text, modifier = modifier, style = style.merge(MoneyStyle), color = c, maxLines = 1)
}

/** Counts up to the value — makes the headline number feel alive without misrepresenting it. */
@Composable
fun AnimatedMoney(minor: Long, style: TextStyle, color: Color = Palette.Text, modifier: Modifier = Modifier) {
    val anim = remember { Animatable(0f) }
    val reduce = LocalReduceMotion.current
    LaunchedEffect(minor) { if (reduce) anim.snapTo(minor.toFloat()) else anim.animateTo(minor.toFloat(), tween(1100, easing = FastOutSlowInEasing)) }
    val shown = if (abs(anim.value - minor) < 100f) minor else anim.value.toLong()
    Text(Money.formatWhole(shown), style = style.merge(MoneyStyle), color = color, modifier = modifier, maxLines = 1)
}

/** Large R-prefixed amount with smaller cents, like a premium banking app. */
@Composable
fun HeroAmount(minor: Long, modifier: Modifier = Modifier, size: Int = 44, color: Color = Palette.Text) {
    val neg = minor < 0
    val a = abs(minor)
    val rands = Money.formatWhole(a - a % 100).removePrefix("R ")
    val cents = (a % 100).toString().padStart(2, '0')
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        Text((if (neg) "−" else "") + "R", style = MoneyStyle.copy(fontSize = (size * 0.55).sp, fontWeight = FontWeight.Medium), color = color.copy(alpha = 0.7f), modifier = Modifier.padding(bottom = (size * 0.12).dp))
        Spacer(Modifier.width(4.dp))
        Text(rands, style = MoneyStyle.copy(fontSize = size.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1.5).sp), color = color)
        Text(",$cents", style = MoneyStyle.copy(fontSize = (size * 0.45).sp, fontWeight = FontWeight.Medium), color = color.copy(alpha = 0.55f), modifier = Modifier.padding(bottom = (size * 0.12).dp))
    }
}

// ---------------------------------------------------------------- Status helpers

fun statusColor(status: BudgetStatus): Color = when (status) {
    BudgetStatus.ON_TRACK -> RW.colors.positive
    BudgetStatus.WARNING, BudgetStatus.PROJECTED_OVER -> RW.colors.warning
    BudgetStatus.OVER -> RW.colors.danger
}

fun severityColor(s: Severity): Color = when (s) {
    Severity.CRITICAL -> RW.colors.danger
    Severity.WARNING -> RW.colors.warning
    Severity.POSITIVE -> RW.colors.positive
    Severity.INFO -> RW.colors.info
}

/** Rounded progress bar with an emerald gradient and a soft glow on the leading edge. */
@Composable
fun GlowBar(fraction: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 8.dp) {
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(900, easing = FastOutSlowInEasing), label = "bar")
    BoxWithConstraints(modifier.fillMaxWidth().height(height).clip(CircleShape).background(Palette.GlassStrong)) {
        Box(
            Modifier.fillMaxHeight().width(maxWidth * f).clip(CircleShape)
                .background(Brush.horizontalGradient(listOf(color.copy(alpha = 0.55f), color))),
        )
    }
}

/** Backwards-compatible name. */
@Composable
fun BudgetProgress(fraction: Float, color: Color, modifier: Modifier = Modifier) = GlowBar(fraction, color, modifier)

@Composable
fun InsightBanner(severity: Severity, title: String, body: String, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    val c = severityColor(severity)
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
            .background(Brush.horizontalGradient(listOf(c.copy(alpha = 0.18f), c.copy(alpha = 0.04f))))
            .border(1.dp, c.copy(alpha = 0.25f), RoundedCornerShape(22.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        if (icon != null) {
            IconBadge(icon, c, size = 36.dp)
            Spacer(Modifier.width(12.dp))
        }
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, color = c)
            Spacer(Modifier.height(2.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = Palette.Text.copy(alpha = 0.88f))
        }
    }
}

// ---------------------------------------------------------------- Inputs

@Composable
fun rwFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Palette.Mint,
    unfocusedBorderColor = Palette.Hairline,
    focusedContainerColor = Palette.Glass,
    unfocusedContainerColor = Palette.Glass,
    focusedLabelColor = Palette.Mint,
    unfocusedLabelColor = Palette.TextDim,
    cursorColor = Palette.Mint,
    focusedPrefixColor = Palette.TextDim,
    unfocusedPrefixColor = Palette.TextDim,
)

val FieldShape = RoundedCornerShape(18.dp)

@Composable
fun RWTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    prefix: String? = null,
    suffix: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    readOnly: Boolean = false,
    /** Masks the input (PINs/passwords). */
    secret: Boolean = false,
    isError: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        readOnly = readOnly,
        isError = isError,
        visualTransformation = if (secret) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        shape = FieldShape,
        colors = rwFieldColors(),
        prefix = prefix?.let { { Text(it) } },
        suffix = suffix?.let { { Text(it) } },
        trailingIcon = trailing,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        supportingText = supporting?.let { { Text(it, color = Palette.TextFaint) } },
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
fun MoneyField(value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, supporting: String? = null) =
    RWTextField(
        value, { s -> onValueChange(s.filter { it.isDigit() || it == '.' || it == ',' || it == ' ' }) }, label, modifier,
        supporting = supporting, keyboardType = KeyboardType.Decimal, prefix = "R ",
    )

/** Glass segmented control with a sliding emerald thumb. */
@Composable
fun SegmentedControl(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    BoxWithConstraints(
        modifier.fillMaxWidth().height(46.dp).clip(CircleShape).background(Palette.Glass)
            .border(1.dp, Palette.Hairline, CircleShape).padding(4.dp),
    ) {
        val segment = maxWidth / options.size
        val x by animateDpAsState(segment * selected, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow), label = "seg")
        Box(Modifier.offset(x = x).width(segment).fillMaxHeight().clip(CircleShape).background(RWBrush.emerald))
        Row(Modifier.fillMaxSize()) {
            options.forEachIndexed { i, label ->
                val color by animateColorAsState(if (i == selected) Palette.Ink else Palette.TextDim, label = "segc")
                Box(Modifier.weight(1f).fillMaxHeight().clip(CircleShape).clickable { onSelect(i) }, contentAlignment = Alignment.Center) {
                    Text(label, style = MaterialTheme.typography.labelLarge, color = color, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null) {
    Row(
        modifier.fillMaxWidth().height(56.dp)
            .pressable(onClick, enabled)
            .clip(CircleShape)
            .background(if (enabled) RWBrush.emerald else Brush.linearGradient(listOf(Palette.GlassStrong, Palette.GlassStrong))),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (enabled) Palette.Ink else Palette.TextFaint, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.titleMedium, color = if (enabled) Palette.Ink else Palette.TextFaint)
    }
}

@Composable
fun GhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Row(
        modifier.height(48.dp).pressable(onClick).clip(CircleShape).background(Palette.Glass)
            .border(1.dp, Palette.Hairline, CircleShape).padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = Palette.Text, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = Palette.Text)
    }
}

@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Column(modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (icon != null) {
            IconBadge(icon, Palette.Mint, size = 56.dp)
            Spacer(Modifier.height(12.dp))
        }
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim, textAlign = TextAlign.Center)
    }
}

fun categoryColor(argb: Long?): Color = argb?.let { Color(it.toInt()) } ?: Color(0xFF94A3B8)

package com.randwise.app.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.randwise.app.security.PasscodeKind
import com.randwise.app.ui.components.AuroraBackground
import com.randwise.app.ui.components.Eyebrow
import com.randwise.app.ui.components.FieldShape
import com.randwise.app.ui.components.PrimaryButton
import com.randwise.app.ui.components.pressable
import com.randwise.app.ui.components.rwFieldColors
import com.randwise.app.ui.theme.Palette
import kotlinx.coroutines.delay

/**
 * Gate shown on open and after 60 s in the background. With an app password the user can always
 * type it; fingerprint/face is offered as a shortcut when enabled and enrolled.
 */
@Composable
fun LockScreen(
    kind: PasscodeKind?,
    biometricAvailable: Boolean,
    error: String?,
    lockedUntil: Long,
    checking: Boolean,
    onSubmit: (CharArray) -> Unit,
    onBiometric: () -> Unit,
    onForgot: () -> Unit,
) {
    var entry by remember { mutableStateOf("") }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var forgot by remember { mutableStateOf(false) }
    val lockedSecs = ((lockedUntil - now) / 1000).coerceAtLeast(0)
    LaunchedEffect(lockedUntil) { while (lockedUntil > System.currentTimeMillis()) { now = System.currentTimeMillis(); delay(500) }; now = System.currentTimeMillis() }
    LaunchedEffect(error) { if (error != null) entry = "" }

    // Clear the field as soon as it's submitted so digits never linger on screen while hashing runs.
    fun submit() {
        if (entry.isNotEmpty() && lockedSecs == 0L && !checking) {
            onSubmit(entry.toCharArray())
            entry = ""
        }
    }

    Box(Modifier.fillMaxSize()) {
        AuroraBackground(intensity = 1.2f)
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(0.6f))
            if (kind == null) SonarFingerprint(onBiometric) else {
                Box(Modifier.size(64.dp).clip(CircleShape).background(Palette.Mint.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Lock, null, tint = Palette.Mint, modifier = Modifier.size(30.dp))
                }
            }
            Spacer(Modifier.height(20.dp))
            Eyebrow("RandWise", Palette.Mint)
            Spacer(Modifier.height(6.dp))
            Text(
                when (kind) { PasscodeKind.PIN -> "Enter your PIN"; PasscodeKind.PASSWORD -> "Enter your password"; null -> "Welcome back" },
                style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            val status = when {
                lockedSecs > 0 -> "Too many attempts. Try again in ${lockedSecs / 60}:${(lockedSecs % 60).toString().padStart(2, '0')}"
                error != null -> error
                else -> "Your finances are encrypted on this phone."
            }
            Text(status, color = if (lockedSecs > 0 || error != null) Palette.Amber else Palette.TextDim, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(28.dp))

            when (kind) {
                PasscodeKind.PIN -> {
                    PinDots(entry.length)
                    Spacer(Modifier.weight(0.4f))
                    PinPad(
                        enabled = lockedSecs == 0L && !checking,
                        showBiometric = biometricAvailable,
                        onDigit = { d -> if (entry.length < 10) entry += d },
                        onDelete = { entry = entry.dropLast(1) },
                        onBiometric = onBiometric,
                    )
                    Spacer(Modifier.height(16.dp))
                    PrimaryButton(if (checking) "Checking…" else "Unlock", ::submit, enabled = entry.length >= 6 && lockedSecs == 0L && !checking)
                }
                PasscodeKind.PASSWORD -> {
                    OutlinedTextField(
                        entry, { entry = it }, label = { Text("Password") }, singleLine = true, shape = FieldShape, colors = rwFieldColors(),
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submit() }),
                        enabled = lockedSecs == 0L, modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(16.dp))
                    PrimaryButton(if (checking) "Checking…" else "Unlock", ::submit, enabled = entry.isNotEmpty() && lockedSecs == 0L && !checking)
                    if (biometricAvailable) {
                        TextButton(onBiometric) { Icon(Icons.Rounded.Fingerprint, null, tint = Palette.Mint); Text("  Use fingerprint or face", color = Palette.Mint) }
                    }
                    Spacer(Modifier.weight(0.4f))
                }
                null -> {
                    Spacer(Modifier.weight(0.4f))
                    PrimaryButton("Unlock", onBiometric)
                }
            }
            if (kind != null) TextButton({ forgot = true }) { Text("Forgot ${if (kind == PasscodeKind.PIN) "PIN" else "password"}?", color = Palette.TextDim) }
            Spacer(Modifier.height(12.dp))
        }
    }

    if (forgot) {
        AlertDialog(
            onDismissRequest = { forgot = false },
            title = { Text("Can't remember it?") },
            text = {
                Text(
                    "For your security RandWise never stores your password and has no way to recover it. " +
                        "You can erase all data on this phone and start again — exports you saved elsewhere are not affected.",
                )
            },
            confirmButton = { TextButton({ forgot = false; onForgot() }) { Text("Erase & start over", color = Palette.Coral) } },
            dismissButton = { TextButton({ forgot = false }) { Text("Keep trying") } },
        )
    }
}

@Composable
private fun PinDots(count: Int) {
    val slots = maxOf(6, count)
    Row(
        Modifier.semantics { contentDescription = "$count digits entered" },
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        repeat(slots) { i ->
            val filled = i < count
            val c by animateColorAsState(if (filled) Palette.Mint else Palette.GlassStrong, label = "dot")
            Box(Modifier.size(14.dp).clip(CircleShape).background(c).border(1.dp, if (filled) Palette.Mint else Palette.Hairline, CircleShape))
        }
    }
}

@Composable
private fun PinPad(enabled: Boolean, showBiometric: Boolean, onDigit: (String) -> Unit, onDelete: () -> Unit, onBiometric: () -> Unit) {
    val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("bio", "0", "del"))
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { k ->
                    val visible = k != "bio" || showBiometric
                    Box(
                        Modifier.weight(1f).heightIn(min = 62.dp).clip(RoundedCornerShape(22.dp))
                            .then(if (visible && k.length == 1) Modifier.background(Palette.Glass).border(1.dp, Palette.Hairline, RoundedCornerShape(22.dp)) else Modifier)
                            .then(
                                if (!visible) Modifier else Modifier.pressable({
                                    when (k) { "del" -> onDelete(); "bio" -> onBiometric(); else -> onDigit(k) }
                                }, enabled = enabled || k == "bio"),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        when (k) {
                            "del" -> Icon(Icons.AutoMirrored.Rounded.Backspace, "Delete digit", tint = Palette.TextDim)
                            "bio" -> if (showBiometric) Icon(Icons.Rounded.Fingerprint, "Use fingerprint or face", tint = Palette.Mint, modifier = Modifier.size(30.dp))
                            else -> Text(k, style = MaterialTheme.typography.headlineMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SonarFingerprint(onClick: () -> Unit) {
    val t = rememberInfiniteTransition(label = "lock")
    val wave by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "wave")
    Box(Modifier.size(180.dp).pressable(onClick), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            for (i in 0 until 3) {
                val p = (wave + i / 3f) % 1f
                drawCircle(Palette.Mint.copy(alpha = (1f - p) * 0.45f), radius = size.minDimension / 2 * (0.4f + 0.6f * p), style = Stroke(2.dp.toPx()))
            }
        }
        Icon(Icons.Rounded.Fingerprint, "Unlock with fingerprint or face", tint = Palette.Mint, modifier = Modifier.size(60.dp))
    }
}


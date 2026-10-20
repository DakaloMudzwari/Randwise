package com.randwise.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.randwise.app.core.Money
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.randwise.app.data.model.AccountType
import com.randwise.app.data.model.PayFrequency
import com.randwise.app.security.PasscodeKind
import kotlinx.coroutines.launch
import java.time.LocalDate
import com.randwise.app.data.model.UserProfile
import com.randwise.app.ui.FinanceViewModel
import com.randwise.app.ui.components.CoachOrb
import com.randwise.app.ui.components.Eyebrow
import com.randwise.app.ui.components.GhostButton
import com.randwise.app.ui.components.GlassCard
import com.randwise.app.ui.components.IconBadge
import com.randwise.app.ui.components.MoneyField
import com.randwise.app.ui.components.PrimaryButton
import com.randwise.app.ui.components.RWTextField
import com.randwise.app.ui.components.SegmentedControl
import com.randwise.app.ui.components.enterAnimation
import com.randwise.app.ui.components.pressable
import com.randwise.app.ui.theme.Palette
import com.randwise.app.ui.theme.RWBrush

/** F01 Financial Profile Setup. */
@Composable
fun OnboardingScreen(vm: FinanceViewModel) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var income by rememberSaveable { mutableStateOf("") }
    var payday by rememberSaveable { mutableStateOf("25") }
    var weekendAdjust by rememberSaveable { mutableStateOf(true) }
    var rent by rememberSaveable { mutableStateOf("") }
    var insurance by rememberSaveable { mutableStateOf("") }
    var debt by rememberSaveable { mutableStateOf("") }
    var obligationDay by rememberSaveable { mutableStateOf("1") }
    var buffer by rememberSaveable { mutableStateOf("500") }
    var savings by rememberSaveable { mutableStateOf("") }
    var emergencyMonths by rememberSaveable { mutableStateOf("3") }
    var accountName by rememberSaveable { mutableStateOf("Everyday account") }
    var accountType by rememberSaveable { mutableIntStateOf(0) }
    var balance by rememberSaveable { mutableStateOf("") }
    var freq by rememberSaveable { mutableIntStateOf(0) }
    var lastPayday by rememberSaveable { mutableLongStateOf(LocalDate.now().toEpochDay()) }
    var lockKind by rememberSaveable { mutableIntStateOf(0) }
    var secret by rememberSaveable { mutableStateOf("") }
    var secretConfirm by rememberSaveable { mutableStateOf("") }
    var secretError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val freqs = listOf(PayFrequency.MONTHLY, PayFrequency.FORTNIGHTLY, PayFrequency.WEEKLY)
    val types = listOf(AccountType.CHEQUE, AccountType.SAVINGS, AccountType.CASH)
    val lastStep = 4
    val canNext = when (step) {
        1 -> (Money.parse(income) ?: 0) > 0 && (freqs[freq] != PayFrequency.MONTHLY || payday.toIntOrNull() in 1..31)
        2 -> obligationDay.toIntOrNull() in 1..31
        3 -> accountName.isNotBlank() && Money.parse(balance.ifBlank { "0" }) != null
        else -> true
    }

    fun finish() {
        val profile = UserProfile(
            payday = payday.toIntOrNull()?.coerceIn(1, 31) ?: 25,
            adjustPaydayForWeekend = weekendAdjust,
            netPayMinor = Money.parse(income) ?: 0,
            safetyBufferMinor = Money.parse(buffer) ?: 0,
            plannedSavingsMinor = Money.parse(savings) ?: 0,
            emergencyTargetMonths = emergencyMonths.toIntOrNull()?.coerceIn(1, 24) ?: 3,
            payFrequency = freqs[freq],
            payAnchorDate = if (freqs[freq] == PayFrequency.MONTHLY) null else lastPayday,
        )
        vm.completeOnboarding(
            profile, accountName.trim(), types[accountType], Money.parse(balance.ifBlank { "0" }) ?: 0,
            listOf("Rent" to (Money.parse(rent) ?: 0), "Insurance" to (Money.parse(insurance) ?: 0), "Debt repayment" to (Money.parse(debt) ?: 0)),
            obligationDay.toIntOrNull()?.coerceIn(1, 31) ?: 1,
        )
    }

    /** Last step: optionally save the app password first, then finish. */
    fun finishWithLock() {
        if (secret.isEmpty()) { finish(); return }
        if (secret != secretConfirm) { secretError = "The two entries don't match"; return }
        scope.launch {
            secretError = vm.setPasscode(null, secret, if (lockKind == 0) PasscodeKind.PIN else PasscodeKind.PASSWORD)
            if (secretError == null) finish()
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
        if (step > 0) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(CircleShape).pressable({ step-- }), contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Palette.Text)
                }
                Spacer(Modifier.weight(1f))
                StepDots(step - 1, lastStep)
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.width(44.dp))
            }
        }

        AnimatedContent(
            targetState = step,
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                (slideInHorizontally { it / 4 * dir } + fadeIn()) togetherWith (slideOutHorizontally { -it / 4 * dir } + fadeOut())
            },
            modifier = Modifier.weight(1f),
            label = "onboarding",
        ) { s ->
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                when (s) {
                    0 -> Welcome()
                    1 -> {
                        StepTitle("Step 1 of $lastStep", "What lands in\nyour account?")
                        Text("How often are you paid?", style = MaterialTheme.typography.titleSmall)
                        SegmentedControl(listOf("Monthly", "2-weekly", "Weekly"), freq, { freq = it })
                        MoneyField(
                            income, { income = it },
                            when (freqs[freq]) { PayFrequency.MONTHLY -> "Monthly take-home pay"; PayFrequency.FORTNIGHTLY -> "Take-home per fortnight"; PayFrequency.WEEKLY -> "Take-home per week" },
                        )
                        if (freqs[freq] == PayFrequency.MONTHLY) {
                            RWTextField(payday, { payday = it.filter(Char::isDigit).take(2) }, "Payday (day of month)", keyboardType = KeyboardType.Number, supporting = "Use 31 for the last day of the month")
                            GlassCard {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text("Weekend or holiday → day before", style = MaterialTheme.typography.titleSmall)
                                        Text("Most SA employers pay on the last business day before a weekend or public holiday", style = MaterialTheme.typography.bodySmall, color = Palette.TextDim)
                                    }
                                    Switch(weekendAdjust, { weekendAdjust = it })
                                }
                            }
                        } else {
                            DateField(LocalDate.ofEpochDay(lastPayday), { lastPayday = it.toEpochDay() }, label = "Your most recent payday")
                            Text("We'll count every ${if (freqs[freq] == PayFrequency.WEEKLY) "7" else "14"} days from this date.", style = MaterialTheme.typography.bodySmall, color = Palette.TextDim)
                        }
                    }
                    2 -> {
                        StepTitle("Step 2 of $lastStep", "What's already\nspoken for?")
                        Text("These become bills that safe-to-spend always protects. Add more later.", color = Palette.TextDim)
                        MoneyField(rent, { rent = it }, "Rent or bond")
                        MoneyField(insurance, { insurance = it }, "Insurance & medical aid")
                        MoneyField(debt, { debt = it }, "Loan & card repayments")
                        RWTextField(obligationDay, { obligationDay = it.filter(Char::isDigit).take(2) }, "Usual debit-order day", keyboardType = KeyboardType.Number)
                    }
                    4 -> {
                        StepTitle("Step 4 of $lastStep", "Protect\nRandWise")
                        Text(
                            "Set your own PIN or password. It's required every time the app opens — even if someone knows your phone's screen lock. " +
                                "You can skip this and add one later in the Vault.",
                            color = Palette.TextDim,
                        )
                        SegmentedControl(listOf("PIN", "Password"), lockKind, { lockKind = it; secret = ""; secretConfirm = ""; secretError = null })
                        val kt = if (lockKind == 0) KeyboardType.NumberPassword else KeyboardType.Password
                        fun clean(s: String) = if (lockKind == 0) s.filter(Char::isDigit).take(10) else s.take(64)
                        RWTextField(secret, { secret = clean(it); secretError = null }, if (lockKind == 0) "PIN (6–10 digits)" else "Password (8+ letters & numbers)", secret = true, keyboardType = kt)
                        RWTextField(secretConfirm, { secretConfirm = clean(it); secretError = null }, "Confirm", secret = true, keyboardType = kt, isError = secretConfirm.isNotEmpty() && secretConfirm != secret)
                        secretError?.let { Text(it, color = Palette.Coral) }
                        Text("RandWise can't recover a forgotten password — only erase the app's data.", style = MaterialTheme.typography.bodySmall, color = Palette.Amber)
                    }
                    else -> {
                        StepTitle("Step 3 of $lastStep", "Your safety net\n& first account")
                        MoneyField(buffer, { buffer = it }, "Safety buffer", supporting = "Never counted as spendable — keeps you clear of fees")
                        MoneyField(savings, { savings = it }, "Savings per pay cycle")
                        RWTextField(emergencyMonths, { emergencyMonths = it.filter(Char::isDigit).take(2) }, "Emergency fund goal (months)", keyboardType = KeyboardType.Number)
                        RWTextField(accountName, { accountName = it.take(40) }, "Account nickname", supporting = "No account numbers needed — ever")
                        SegmentedControl(listOf("Cheque", "Savings", "Cash"), accountType, { accountType = it })
                        MoneyField(balance, { balance = it }, "Current balance")
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (step == 0) {
                PrimaryButton("Set up my profile", { step = 1 }, icon = Icons.AutoMirrored.Rounded.ArrowForward)
                GhostButton("Explore with demo data", { vm.loadDemo() }, Modifier.fillMaxWidth())
            } else {
                PrimaryButton(
                    when { step < lastStep -> "Continue"; secret.isEmpty() -> "Skip & start using RandWise"; else -> "Save & start using RandWise" },
                    { if (step < lastStep) step++ else finishWithLock() },
                    enabled = canNext,
                )
            }
        }
    }
}

@Composable
private fun Welcome() {
    Column(Modifier.fillMaxWidth().padding(top = 40.dp), horizontalAlignment = Alignment.Start) {
        CoachOrb(Modifier.size(96.dp).enterAnimation(0))
        Spacer(Modifier.height(32.dp))
        Eyebrow("RandWise", Palette.Mint)
        Spacer(Modifier.height(8.dp))
        Text("Every rand,\naccounted for.", style = MaterialTheme.typography.displaySmall, modifier = Modifier.enterAnimation(1))
        Spacer(Modifier.height(12.dp))
        Text(
            "Know what's genuinely safe to spend before payday — and get warned before a purchase puts your bills at risk.",
            style = MaterialTheme.typography.bodyLarge, color = Palette.TextDim, modifier = Modifier.enterAnimation(2),
        )
        Spacer(Modifier.height(28.dp))
        Feature(Icons.Rounded.Speed, "Safe-to-spend, live", "Balance minus bills, savings and buffer — to the cent", 3)
        Feature(Icons.Rounded.AutoAwesome, "A coach in your pocket", "Spots payday splurges, fee traps and subscription creep", 4)
        Feature(Icons.Rounded.Lock, "Private by design", "Encrypted on your phone. Never asks for bank passwords.", 5)
    }
}

@Composable
private fun Feature(icon: ImageVector, title: String, body: String, index: Int) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp).enterAnimation(index), verticalAlignment = Alignment.CenterVertically) {
        IconBadge(icon, Palette.Mint, size = 44.dp)
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodySmall, color = Palette.TextDim)
        }
    }
}

@Composable
private fun StepTitle(eyebrow: String, title: String) {
    Column(Modifier.padding(top = 16.dp, bottom = 8.dp)) {
        Eyebrow(eyebrow, Palette.Mint)
        Spacer(Modifier.height(8.dp))
        Text(title, style = MaterialTheme.typography.headlineLarge)
    }
}

@Composable
private fun StepDots(current: Int, count: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(count) { i ->
            val w by animateDpAsState(if (i == current) 28.dp else 8.dp, label = "dot")
            Box(
                Modifier.height(8.dp).width(w).clip(CircleShape)
                    .background(if (i <= current) RWBrush.emerald else androidx.compose.ui.graphics.Brush.linearGradient(listOf(Palette.GlassStrong, Palette.GlassStrong))),
            )
        }
    }
}

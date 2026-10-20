package com.randwise.app.ui.screens

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AdsClick
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Policy
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.ScreenLockPortrait
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.randwise.app.BuildConfig
import com.randwise.app.ads.AdsConsent
import com.randwise.app.core.Money
import com.randwise.app.data.Privacy
import com.randwise.app.data.model.PayFrequency
import com.randwise.app.data.model.UserProfile
import com.randwise.app.security.PasscodeKind
import com.randwise.app.ui.FinanceViewModel
import com.randwise.app.ui.Gutter
import com.randwise.app.ui.NavClearance
import com.randwise.app.ui.components.Eyebrow
import com.randwise.app.ui.components.GhostButton
import com.randwise.app.ui.components.GlassCard
import com.randwise.app.ui.components.HeroAmount
import com.randwise.app.ui.components.IconBadge
import com.randwise.app.ui.components.MoneyField
import com.randwise.app.ui.components.Pill
import com.randwise.app.ui.components.RWTextField
import com.randwise.app.ui.components.SectionHeader
import com.randwise.app.ui.components.SegmentedControl
import com.randwise.app.ui.components.pressable
import com.randwise.app.ui.theme.MoneyStyle
import com.randwise.app.ui.theme.Palette
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun SettingsScreen(vm: FinanceViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val passcodeKind by vm.passcodeKind.collectAsStateWithLifecycle()
    val adPrivacyRequired by AdsConsent.privacyOptionsRequired.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val profile = state.snapshot.profile ?: return
    var editProfile by remember { mutableStateOf(false) }
    var purge by remember { mutableStateOf(false) }
    var passcodeDialog by remember { mutableStateOf<PasscodeAction?>(null) }
    var aiConsentAsk by remember { mutableStateOf(false) }
    var showPolicy by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var showEncryption by remember { mutableStateOf(false) }
    var gateway by remember(settings.gatewayUrl) { mutableStateOf(settings.gatewayUrl) }

    val csv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri -> uri?.let { vm.exportCsv(context, it) } }
    val pdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri -> uri?.let { vm.exportPdf(context, it) } }

    LazyColumn(contentPadding = PaddingValues(start = Gutter, end = Gutter, top = 12.dp, bottom = NavClearance), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Column {
                Eyebrow("Vault", Palette.Mint)
                Text("Settings & privacy", style = MaterialTheme.typography.headlineMedium)
            }
        }

        item {
            GlassCard(brush = Brush.linearGradient(listOf(Palette.Navy700.copy(alpha = 0.9f), Palette.Emerald.copy(alpha = 0.18f)))) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Eyebrow("Take-home per payday")
                        HeroAmount(profile.netPayMinor, size = 32)
                    }
                    GhostButton("Edit", { editProfile = true })
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill(payLabel(profile), Palette.Mint)
                    Pill(profile.currency, Palette.Sky)
                }
                Spacer(Modifier.height(14.dp))
                Row {
                    MiniStat("Buffer", Money.formatWhole(profile.safetyBufferMinor), Modifier.weight(1f))
                    MiniStat("Savings / cycle", Money.formatWhole(profile.plannedSavingsMinor), Modifier.weight(1f))
                    MiniStat("Emergency goal", "${profile.emergencyTargetMonths} mo", Modifier.weight(1f))
                }
            }
        }

        item { SectionHeader("Security") }
        item {
            Group {
                ActionRow(
                    Icons.Rounded.Password, Palette.Mint, "App password",
                    when (passcodeKind) { PasscodeKind.PIN -> "PIN set — required on open"; PasscodeKind.PASSWORD -> "Password set — required on open"; null -> "Not set — add a PIN or password" },
                ) { passcodeDialog = if (passcodeKind == null) PasscodeAction.SET else PasscodeAction.MANAGE }
                ToggleRow(
                    Icons.Rounded.Fingerprint, Palette.Teal, "Fingerprint or face",
                    if (passcodeKind != null) "Quick unlock instead of typing your ${if (passcodeKind == PasscodeKind.PIN) "PIN" else "password"}" else "Unlock with your device lock",
                    settings.biometricLock,
                ) { v -> vm.updateSettings(context) { it.copy(biometricLock = v) } }
                ToggleRow(Icons.Rounded.ScreenLockPortrait, Palette.Sky, "Hide from screenshots", "Blocks screenshots and the recent-apps preview", settings.hideFromScreenshots) { v ->
                    vm.updateSettings(context) { it.copy(hideFromScreenshots = v) }
                }
                ToggleRow(Icons.Rounded.NotificationsActive, Palette.Amber, "Bill reminders", "Heads-up 2 days before a debit · details hidden on lock screen", settings.billReminders) { v ->
                    vm.updateSettings(context) { it.copy(billReminders = v) }
                }
            }
        }

        item { SectionHeader("Privacy & consent", eyebrow = "POPIA · notice v${Privacy.VERSION}") }
        item {
            Group {
                ToggleRow(Icons.Rounded.UploadFile, Palette.Sky, "Statement processing", "Read statements you choose, on this phone only", settings.consentStatementUpload) { v ->
                    vm.setStatementConsent(context, v)
                }
                ToggleRow(Icons.Rounded.AutoAwesome, Palette.Violet, "AI interpretation", "Anonymised totals to Gemini · may be processed outside SA", settings.consentAiInterpretation) { v ->
                    if (v) aiConsentAsk = true else vm.setAiConsent(context, false)
                }
                if (settings.consentAiInterpretation) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        RWTextField(
                            gateway, { gateway = it.trim() }, "AI gateway URL", keyboardType = KeyboardType.Uri,
                            supporting = "Emulator default: ${BuildConfig.DEFAULT_GATEWAY_URL}",
                            trailing = {
                                if (gateway != settings.gatewayUrl) TextButton({ vm.updateSettings(context) { it.copy(gatewayUrl = gateway) } }) { Text("Save") }
                                else Icon(Icons.Rounded.Link, null, tint = Palette.TextFaint)
                            },
                        )
                    }
                }
                ActionRow(Icons.Rounded.Policy, Palette.Mint, "Privacy notice", "What we store, what leaves your phone, your rights") { showPolicy = true }
                ActionRow(Icons.Rounded.History, Palette.TextDim, "Consent history", "Every consent you've given or withdrawn") { showHistory = true }
                if (adPrivacyRequired) ActionRow(Icons.Rounded.AdsClick, Palette.Amber, "Ad privacy choices", "Change how Google may use data for ads") {
                    (context as? Activity)?.let(AdsConsent::showPrivacyOptions)
                }
            }
        }

        item { SectionHeader("Your data") }
        item {
            Group {
                ActionRow(Icons.Rounded.Description, Palette.Mint, "Export transactions", "CSV for Excel or Sheets") { csv.launch("randwise-transactions-${LocalDate.now()}.csv") }
                ActionRow(Icons.Rounded.PictureAsPdf, Palette.Coral, "Export cycle report", "PDF summary of this pay cycle") { pdf.launch("randwise-report-${LocalDate.now()}.pdf") }
                ActionRow(Icons.Rounded.Key, Palette.Sky, "How your data is protected", "Encryption, keys and what we can't see") { showEncryption = true }
            }
        }

        item {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                    .background(Palette.Coral.copy(alpha = 0.08f)).border(1.dp, Palette.Coral.copy(alpha = 0.3f), RoundedCornerShape(24.dp))
                    .pressable({ purge = true }).semantics { role = Role.Button }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconBadge(Icons.Rounded.DeleteForever, Palette.Coral)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Erase all local data", style = MaterialTheme.typography.titleSmall, color = Palette.Coral)
                    Text("Deletes the database, its encryption key and your password", style = MaterialTheme.typography.bodySmall, color = Palette.TextDim)
                }
            }
        }

        item {
            Text(
                "RandWise ${BuildConfig.VERSION_NAME} · Exact integer-cent maths, works offline. Factual information from your own data — not financial advice under the FAIS Act.",
                style = MaterialTheme.typography.bodySmall, color = Palette.TextFaint, modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }

    if (editProfile) ProfileDialog(profile, onDismiss = { editProfile = false }) { vm.updateProfile(it); editProfile = false }
    passcodeDialog?.let { action -> PasscodeDialog(action, passcodeKind, vm, onDismiss = { passcodeDialog = null }, onSwitch = { passcodeDialog = it }) }
    if (showPolicy) PrivacyNoticeDialog { showPolicy = false }
    if (showHistory) ConsentHistoryDialog(vm) { showHistory = false }

    if (aiConsentAsk) {
        AlertDialog(
            onDismissRequest = { aiConsentAsk = false },
            icon = { Icon(Icons.Rounded.Public, null, tint = Palette.Violet) },
            title = { Text("Send anonymised totals offshore?") },
            text = {
                Text(
                    "With AI interpretation on, RandWise sends anonymised totals (safe-to-spend, category totals, bill amounts) to Google's Gemini service, " +
                        "which may process them outside South Africa. No names, account numbers, statement lines or notes are sent. " +
                        "You can switch this off at any time; the on-device coach keeps working.",
                )
            },
            confirmButton = { TextButton({ vm.setAiConsent(context, true); aiConsentAsk = false }) { Text("I consent") } },
            dismissButton = { TextButton({ aiConsentAsk = false }) { Text("Not now") } },
        )
    }

    if (showEncryption) {
        AlertDialog(
            onDismissRequest = { showEncryption = false },
            icon = { Icon(Icons.Rounded.Key, null, tint = Palette.Sky) },
            title = { Text("How your data is protected") },
            text = {
                Text(
                    "• Your data is stored only on this phone, in a SQLCipher database encrypted with AES-256.\n" +
                        "• The database key is random and wrapped by a key that never leaves the Android Keystore.\n" +
                        "• Your app password is never stored — only a salted PBKDF2 hash, so nobody (including us) can recover it.\n" +
                        "• Cloud backup is disabled so copies never leave the device.\n" +
                        "• Erasing data destroys the key, making any leftover files unreadable.",
                )
            },
            confirmButton = { TextButton({ showEncryption = false }) { Text("Got it") } },
        )
    }

    if (purge) {
        var confirm by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { purge = false },
            icon = { Icon(Icons.Rounded.DeleteForever, null, tint = Palette.Coral) },
            title = { Text("Erase everything?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Permanently deletes all accounts, transactions, budgets, bills, goals, settings, your app password and the encryption key. RandWise will restart. Export first if you want a copy.")
                    RWTextField(confirm, { confirm = it }, "Type DELETE to confirm")
                }
            },
            confirmButton = {
                Button(
                    { vm.purgeAll(); purge = false }, enabled = confirm == "DELETE",
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.Coral, contentColor = Palette.Ink),
                ) { Text("Erase all data") }
            },
            dismissButton = { TextButton({ purge = false }) { Text("Cancel") } },
        )
    }
}

private fun payLabel(p: UserProfile): String = when (p.payFrequency) {
    PayFrequency.MONTHLY -> "Paid monthly · day ${p.payday}${if (p.adjustPaydayForWeekend) " · Fri if weekend" else ""}"
    PayFrequency.FORTNIGHTLY -> "Paid fortnightly"
    PayFrequency.WEEKLY -> "Paid weekly"
}

enum class PasscodeAction { SET, MANAGE, CHANGE, REMOVE }

/** Set, change or remove the app password. Changing or removing always requires the current one. */
@Composable
private fun PasscodeDialog(action: PasscodeAction, currentKind: PasscodeKind?, vm: FinanceViewModel, onDismiss: () -> Unit, onSwitch: (PasscodeAction) -> Unit) {
    val scope = rememberCoroutineScope()
    var kindIdx by remember { mutableIntStateOf(if (currentKind == PasscodeKind.PASSWORD) 1 else 0) }
    var current by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val kind = if (kindIdx == 0) PasscodeKind.PIN else PasscodeKind.PASSWORD
    val currentLabel = if (currentKind == PasscodeKind.PIN) "Current PIN" else "Current password"
    fun input(s: String) = if (kind == PasscodeKind.PIN) s.filter(Char::isDigit).take(10) else s.take(64)

    if (action == PasscodeAction.MANAGE) {
        AlertDialog(
            onDismissRequest = onDismiss,
            icon = { Icon(Icons.Rounded.Password, null, tint = Palette.Mint) },
            title = { Text("App ${if (currentKind == PasscodeKind.PIN) "PIN" else "password"}") },
            text = { Text("Your ${if (currentKind == PasscodeKind.PIN) "PIN" else "password"} is required every time RandWise opens.") },
            confirmButton = { TextButton({ onSwitch(PasscodeAction.CHANGE) }) { Text("Change") } },
            dismissButton = { TextButton({ onSwitch(PasscodeAction.REMOVE) }) { Text("Remove", color = Palette.Coral) } },
        )
        return
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        icon = { Icon(Icons.Rounded.Password, null, tint = Palette.Mint) },
        title = {
            Text(when (action) { PasscodeAction.REMOVE -> "Remove app password"; PasscodeAction.CHANGE -> "Change app password"; else -> "Set an app password" })
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (action != PasscodeAction.REMOVE) {
                    SegmentedControl(listOf("PIN", "Password"), kindIdx, { kindIdx = it; new = ""; confirm = ""; error = null })
                    Text(
                        if (kind == PasscodeKind.PIN) "6–10 digits. Avoid birthdays and sequences." else "At least 8 characters with letters and numbers.",
                        style = MaterialTheme.typography.bodySmall, color = Palette.TextDim,
                    )
                }
                if (action == PasscodeAction.CHANGE || action == PasscodeAction.REMOVE) {
                    RWTextField(current, { current = if (currentKind == PasscodeKind.PIN) it.filter(Char::isDigit) else it }, currentLabel, secret = true,
                        keyboardType = if (currentKind == PasscodeKind.PIN) KeyboardType.NumberPassword else KeyboardType.Password)
                }
                if (action != PasscodeAction.REMOVE) {
                    val kt = if (kind == PasscodeKind.PIN) KeyboardType.NumberPassword else KeyboardType.Password
                    RWTextField(new, { new = input(it); error = null }, if (kind == PasscodeKind.PIN) "New PIN" else "New password", secret = true, keyboardType = kt)
                    RWTextField(confirm, { confirm = input(it); error = null }, "Confirm", secret = true, keyboardType = kt, isError = confirm.isNotEmpty() && confirm != new)
                    Text("There's no recovery if you forget it — only erasing the app's data.", style = MaterialTheme.typography.bodySmall, color = Palette.Amber)
                }
                error?.let { Text(it, color = Palette.Coral, style = MaterialTheme.typography.bodyMedium) }
            }
        },
        confirmButton = {
            TextButton(
                {
                    busy = true
                    scope.launch {
                        error = if (action == PasscodeAction.REMOVE) vm.removePasscode(current)
                        else if (new != confirm) "The two entries don't match"
                        else vm.setPasscode(current.ifEmpty { null }, new, kind)
                        busy = false
                        if (error == null) onDismiss()
                    }
                },
                enabled = !busy && (if (action == PasscodeAction.REMOVE) current.isNotEmpty() else new.isNotEmpty() && confirm.isNotEmpty()),
            ) { Text(if (busy) "Checking…" else if (action == PasscodeAction.REMOVE) "Remove" else "Save") }
        },
        dismissButton = { TextButton(onDismiss, enabled = !busy) { Text("Cancel") } },
    )
}

@Composable
private fun PrivacyNoticeDialog(onDismiss: () -> Unit) {
    Dialog(onDismiss, DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Palette.Navy950).statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onDismiss) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Close privacy notice", tint = Palette.Text) }
                Column {
                    Eyebrow("Version ${Privacy.VERSION}", Palette.Mint)
                    Text("Privacy notice", style = MaterialTheme.typography.titleLarge)
                }
            }
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Privacy.sections.forEach { s ->
                    GlassCard {
                        Text(s.title, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        Text(s.body, style = MaterialTheme.typography.bodyMedium, color = Palette.TextDim)
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun ConsentHistoryDialog(vm: FinanceViewModel, onDismiss: () -> Unit) {
    val history by vm.consentHistory.collectAsStateWithLifecycle()
    val fmt = remember { DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm").withZone(ZoneId.systemDefault()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.History, null, tint = Palette.Mint) },
        title = { Text("Consent history") },
        text = {
            if (history.isEmpty()) Text("No consent decisions recorded yet.", color = Palette.TextDim)
            else androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 380.dp)) {
                items(history) { r ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                when (r.type) { Privacy.Consent.STATEMENTS -> "Statement processing"; Privacy.Consent.AI -> "AI interpretation (offshore)"; else -> r.type },
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text("${fmt.format(Instant.ofEpochMilli(r.timestamp))} · notice v${r.policyVersion}", style = MaterialTheme.typography.bodySmall, color = Palette.TextDim)
                        }
                        Pill(if (r.granted) "Granted" else "Withdrawn", if (r.granted) Palette.Mint else Palette.Amber)
                    }
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("Close") } },
    )
}

@Composable
private fun Group(content: @Composable () -> Unit) {
    GlassCard(padding = 6.dp) { content() }
}

@Composable
private fun MiniStat(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Palette.TextDim)
        Text(value, style = MaterialTheme.typography.titleMedium.merge(MoneyStyle))
    }
}

@Composable
private fun ToggleRow(icon: ImageVector, tint: Color, title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).pressable({ onChange(!checked) })
            .semantics(mergeDescendants = true) { role = Role.Switch; stateDescription = if (checked) "On" else "Off" }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon, tint)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Palette.TextDim)
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked, onChange)
    }
}

@Composable
private fun ActionRow(icon: ImageVector, tint: Color, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).pressable(onClick)
            .semantics(mergeDescendants = true) { role = Role.Button }.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon, tint)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Palette.TextDim)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Palette.TextFaint, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun ProfileDialog(p: UserProfile, onDismiss: () -> Unit, onSave: (UserProfile) -> Unit) {
    val freqs = listOf(PayFrequency.MONTHLY, PayFrequency.FORTNIGHTLY, PayFrequency.WEEKLY)
    var freq by remember { mutableIntStateOf(freqs.indexOf(p.payFrequency).coerceAtLeast(0)) }
    var income by remember { mutableStateOf(Money.toPlain(p.netPayMinor)) }
    var payday by remember { mutableStateOf(p.payday.toString()) }
    var anchor by remember { mutableStateOf(p.payAnchorDate?.let(LocalDate::ofEpochDay) ?: LocalDate.now()) }
    var weekend by remember { mutableStateOf(p.adjustPaydayForWeekend) }
    var buffer by remember { mutableStateOf(Money.toPlain(p.safetyBufferMinor)) }
    var savings by remember { mutableStateOf(Money.toPlain(p.plannedSavingsMinor)) }
    var months by remember { mutableStateOf(p.emergencyTargetMonths.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Financial profile") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SegmentedControl(listOf("Monthly", "2-weekly", "Weekly"), freq, { freq = it })
                MoneyField(income, { income = it }, "Take-home per payday")
                if (freqs[freq] == PayFrequency.MONTHLY) {
                    RWTextField(payday, { payday = it.filter(Char::isDigit).take(2) }, "Payday (day of month)", keyboardType = KeyboardType.Number)
                    Row(verticalAlignment = Alignment.CenterVertically) { Text("Friday if weekend", Modifier.weight(1f)); Switch(weekend, { weekend = it }) }
                } else {
                    DateField(anchor, { anchor = it }, label = "Most recent payday")
                }
                MoneyField(buffer, { buffer = it }, "Safety buffer")
                MoneyField(savings, { savings = it }, "Savings per cycle")
                RWTextField(months, { months = it.filter(Char::isDigit).take(2) }, "Emergency goal (months)", keyboardType = KeyboardType.Number)
            }
        },
        confirmButton = {
            TextButton({
                val f = freqs[freq]
                onSave(
                    p.copy(
                        payFrequency = f,
                        payAnchorDate = if (f == PayFrequency.MONTHLY) null else anchor.toEpochDay(),
                        netPayMinor = Money.parse(income) ?: p.netPayMinor,
                        payday = payday.toIntOrNull()?.coerceIn(1, 31) ?: p.payday,
                        adjustPaydayForWeekend = weekend,
                        safetyBufferMinor = Money.parse(buffer) ?: p.safetyBufferMinor,
                        plannedSavingsMinor = Money.parse(savings) ?: p.plannedSavingsMinor,
                        emergencyTargetMonths = months.toIntOrNull()?.coerceIn(1, 24) ?: p.emergencyTargetMonths,
                    ),
                )
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

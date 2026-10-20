package com.randwise.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Policy
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.randwise.app.BuildConfig
import com.randwise.app.core.Money
import com.randwise.app.data.model.UserProfile
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
import com.randwise.app.ui.components.pressable
import com.randwise.app.ui.theme.Palette
import java.time.LocalDate

@Composable
fun SettingsScreen(vm: FinanceViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val profile = state.snapshot.profile ?: return
    var editProfile by remember { mutableStateOf(false) }
    var purge by remember { mutableStateOf(false) }
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
                        Eyebrow("Monthly take-home")
                        HeroAmount(profile.monthlyIncomeMinor, size = 32)
                    }
                    GhostButton("Edit", { editProfile = true })
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("Payday ${profile.payday}${if (profile.adjustPaydayForWeekend) " · Fri if weekend" else ""}", Palette.Mint)
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
                ToggleRow(Icons.Rounded.Fingerprint, Palette.Mint, "Biometric lock", "Fingerprint, face or PIN on open", settings.biometricLock) { v -> vm.updateSettings(context) { it.copy(biometricLock = v) } }
                ToggleRow(Icons.Rounded.NotificationsActive, Palette.Amber, "Bill reminders", "Heads-up 2 days before a debit", settings.billReminders) { v -> vm.updateSettings(context) { it.copy(billReminders = v) } }
            }
        }

        item { SectionHeader("Privacy & consent", eyebrow = "POPIA") }
        item {
            Group {
                ToggleRow(Icons.Rounded.UploadFile, Palette.Sky, "Statement processing", "Read statements you choose, on-device", settings.consentStatementUpload) { v ->
                    vm.updateSettings(context) { it.copy(consentStatementUpload = v) }
                }
                ToggleRow(Icons.Rounded.AutoAwesome, Palette.Violet, "AI interpretation", "Share anonymised totals with the Gemini coach", settings.consentAiInterpretation) { v ->
                    vm.updateSettings(context) { it.copy(consentAiInterpretation = v) }
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
            }
        }

        item { SectionHeader("Your data") }
        item {
            Group {
                ActionRow(Icons.Rounded.Description, Palette.Mint, "Export transactions", "CSV for Excel or Sheets") { csv.launch("randwise-transactions-${LocalDate.now()}.csv") }
                ActionRow(Icons.Rounded.PictureAsPdf, Palette.Coral, "Export cycle report", "PDF summary of this pay cycle") { pdf.launch("randwise-report-${LocalDate.now()}.pdf") }
                ActionRow(Icons.Rounded.Policy, Palette.Sky, "Encryption", "SQLCipher · key held in Android Keystore") { }
            }
        }

        item {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                    .background(Palette.Coral.copy(alpha = 0.08f)).border(1.dp, Palette.Coral.copy(alpha = 0.3f), RoundedCornerShape(24.dp))
                    .pressable({ purge = true }).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconBadge(Icons.Rounded.DeleteForever, Palette.Coral)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Erase all local data", style = MaterialTheme.typography.titleSmall, color = Palette.Coral)
                    Text("Your right to erasure — one tap, permanent", style = MaterialTheme.typography.bodySmall, color = Palette.TextDim)
                }
            }
        }

        item {
            Text(
                "RandWise ${BuildConfig.VERSION_NAME} · Exact integer-cent maths, works offline. Coaching is general guidance, not regulated financial advice.",
                style = MaterialTheme.typography.bodySmall, color = Palette.TextFaint, modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }

    if (editProfile) ProfileDialog(profile, onDismiss = { editProfile = false }) { vm.updateProfile(it); editProfile = false }

    if (purge) {
        var confirm by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { purge = false },
            icon = { Icon(Icons.Rounded.DeleteForever, null, tint = Palette.Coral) },
            title = { Text("Erase everything?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Permanently deletes all accounts, transactions, budgets, bills, goals and settings from this phone. Export first if you want a copy.")
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

@Composable
private fun Group(content: @Composable () -> Unit) {
    GlassCard(padding = 6.dp) { content() }
}

@Composable
private fun MiniStat(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Palette.TextDim)
        Text(value, style = MaterialTheme.typography.titleMedium.merge(com.randwise.app.ui.theme.MoneyStyle))
    }
}

@Composable
private fun ToggleRow(icon: ImageVector, tint: Color, title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).pressable({ onChange(!checked) }).padding(12.dp),
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
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).pressable(onClick).padding(12.dp),
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
    var income by remember { mutableStateOf(Money.toPlain(p.monthlyIncomeMinor)) }
    var payday by remember { mutableStateOf(p.payday.toString()) }
    var weekend by remember { mutableStateOf(p.adjustPaydayForWeekend) }
    var buffer by remember { mutableStateOf(Money.toPlain(p.safetyBufferMinor)) }
    var savings by remember { mutableStateOf(Money.toPlain(p.plannedSavingsMinor)) }
    var months by remember { mutableStateOf(p.emergencyTargetMonths.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Financial profile") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MoneyField(income, { income = it }, "Monthly take-home pay")
                RWTextField(payday, { payday = it.filter(Char::isDigit).take(2) }, "Payday", keyboardType = KeyboardType.Number)
                Row(verticalAlignment = Alignment.CenterVertically) { Text("Friday if weekend", Modifier.weight(1f)); Switch(weekend, { weekend = it }) }
                MoneyField(buffer, { buffer = it }, "Safety buffer")
                MoneyField(savings, { savings = it }, "Savings per cycle")
                RWTextField(months, { months = it.filter(Char::isDigit).take(2) }, "Emergency goal (months)", keyboardType = KeyboardType.Number)
            }
        },
        confirmButton = {
            TextButton({
                onSave(
                    p.copy(
                        monthlyIncomeMinor = Money.parse(income) ?: p.monthlyIncomeMinor,
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

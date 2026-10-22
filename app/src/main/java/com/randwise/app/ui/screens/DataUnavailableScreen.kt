package com.randwise.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.randwise.app.ui.components.AuroraBackground
import com.randwise.app.ui.components.Eyebrow
import com.randwise.app.ui.components.GhostButton
import com.randwise.app.ui.components.PrimaryButton
import com.randwise.app.ui.theme.Palette

/**
 * Shown instead of the app when the encrypted database can't be opened — usually because Android
 * lost the Keystore key that protects it. Without that key the data is unreadable for good, so the
 * only ways forward are to try again or erase and start fresh.
 */
@Composable
fun DataUnavailableScreen(onRetry: () -> Unit, onErase: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        AuroraBackground(intensity = 0.8f)
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(0.6f))
            Box(Modifier.size(64.dp).clip(CircleShape).background(Palette.Amber.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.WarningAmber, null, tint = Palette.Amber, modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.height(20.dp))
            Eyebrow("RandWise", Palette.Amber)
            Spacer(Modifier.height(6.dp))
            Text("We can't open your data", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(
                "Your finances are encrypted with a key that Android keeps for RandWise. That key is no longer available " +
                    "on this phone — this can happen after a system update or restore. Try again first; if it keeps failing, " +
                    "the stored data can't be recovered and you'll need to start fresh.",
                color = Palette.TextDim, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.weight(0.4f))
            PrimaryButton("Try again", onRetry, icon = Icons.Rounded.Refresh)
            Spacer(Modifier.height(12.dp))
            GhostButton("Erase & start fresh", { confirm = true })
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Erase everything?") },
            text = { Text("This deletes the unreadable data and your app password. Exports you saved elsewhere are not affected.") },
            confirmButton = { TextButton({ confirm = false; onErase() }) { Text("Erase & start fresh", color = Palette.Coral) } },
            dismissButton = { TextButton({ confirm = false }) { Text("Cancel") } },
        )
    }
}

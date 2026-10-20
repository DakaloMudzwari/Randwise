package com.randwise.app.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.randwise.app.ui.components.AuroraBackground
import com.randwise.app.ui.components.Eyebrow
import com.randwise.app.ui.components.PrimaryButton
import com.randwise.app.ui.components.pressable
import com.randwise.app.ui.theme.Palette

@Composable
fun LockScreen(error: String?, onUnlock: () -> Unit) {
    val t = rememberInfiniteTransition(label = "lock")
    val wave by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "wave")
    Box(Modifier.fillMaxSize()) {
        AuroraBackground(intensity = 1.2f)
        Column(Modifier.fillMaxSize().padding(32.dp).navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Eyebrow("RandWise", Palette.Mint)
            Spacer(Modifier.height(8.dp))
            Text("Welcome back", style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.height(8.dp))
            Text("Your finances are encrypted on this phone.", color = Palette.TextDim, textAlign = TextAlign.Center)
            Spacer(Modifier.height(56.dp))
            Box(Modifier.size(200.dp).pressable(onUnlock), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    // Three expanding sonar rings.
                    for (i in 0 until 3) {
                        val p = (wave + i / 3f) % 1f
                        drawCircle(Palette.Mint.copy(alpha = (1f - p) * 0.45f), radius = size.minDimension / 2 * (0.4f + 0.6f * p), style = Stroke(2.dp.toPx()))
                    }
                    drawCircle(Palette.Mint.copy(alpha = 0.12f), radius = size.minDimension * 0.22f)
                }
                Icon(Icons.Rounded.Fingerprint, "Unlock", tint = Palette.Mint, modifier = Modifier.size(64.dp))
            }
            Spacer(Modifier.height(40.dp))
            if (error != null) {
                Text(error, color = Palette.TextDim, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
            }
            PrimaryButton("Unlock", onUnlock)
        }
    }
}

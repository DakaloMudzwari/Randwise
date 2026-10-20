package com.randwise.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.randwise.app.ui.theme.Palette
import com.randwise.app.ui.theme.RWBrush

data class NavItem(val route: String, val label: String, val icon: ImageVector)

/**
 * Floating glass pill. The active hub expands into an emerald capsule with its label;
 * the others collapse to icons, so five hubs fit comfortably on small phones.
 */
@Composable
fun FloatingNavBar(items: List<NavItem>, selected: String?, onSelect: (NavItem) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .height(68.dp)
            .shadow(24.dp, CircleShape, ambientColor = Color.Black, spotColor = Color.Black)
            .clip(CircleShape)
            .background(Brush.verticalGradient(listOf(Color(0xF2122546), Color(0xF20A1730))))
            .border(1.dp, RWBrush.glassBorder, CircleShape)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        items.forEach { item ->
            val active = item.route == selected
            val tint by animateColorAsState(if (active) Palette.Ink else Palette.TextDim, label = "navtint")
            Row(
                Modifier
                    .height(48.dp)
                    .clip(CircleShape)
                    .background(if (active) RWBrush.emerald else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)))
                    .pressable({ onSelect(item) })
                    .padding(horizontal = if (active) 16.dp else 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(item.icon, contentDescription = item.label, tint = tint, modifier = Modifier.size(22.dp))
                AnimatedVisibility(
                    active,
                    enter = expandHorizontally(spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow)) + fadeIn(),
                    exit = shrinkHorizontally() + fadeOut(),
                ) {
                    Row {
                        Spacer(Modifier.width(8.dp))
                        Text(item.label, style = MaterialTheme.typography.labelLarge, color = Palette.Ink, maxLines = 1)
                    }
                }
            }
        }
    }
}

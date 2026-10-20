package com.randwise.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.randwise.app.R

/*
 * "Midnight" — RandWise's signature look: deep navy canvas, emerald/teal aurora light, white type.
 * Ads deliberately use a flat neutral grey (AdSurface) that appears nowhere else in the UI,
 * so they can never be mistaken for app content (AdMob policy).
 */
object Palette {
    val Ink = Color(0xFF040B17)
    val Navy950 = Color(0xFF050E1C)
    val Navy900 = Color(0xFF081429)
    val Navy850 = Color(0xFF0B1A33)
    val Navy800 = Color(0xFF0E2140)
    val Navy700 = Color(0xFF16305A)
    val Navy600 = Color(0xFF1F4078)
    val Emerald = Color(0xFF10B981)
    val Mint = Color(0xFF34D399)
    val MintSoft = Color(0xFF6EE7B7)
    val Teal = Color(0xFF2DD4BF)
    val Sky = Color(0xFF60A5FA)
    val Amber = Color(0xFFFBBF24)
    val Coral = Color(0xFFF87171)
    val Violet = Color(0xFFA78BFA)
    val White = Color(0xFFFFFFFF)
    val Text = Color(0xFFF4F7FB)
    val TextDim = Color(0xFF9AABC4)
    val TextFaint = Color(0xFF5E7090)
    val Hairline = Color(0x1FFFFFFF)
    val Glass = Color(0x0FFFFFFF)
    val GlassStrong = Color(0x1AFFFFFF)
    val AdSurface = Color(0xFF2B2E33)
    val AdLabel = Color(0xFFB4B8BE)

    // Kept for code that still refers to the original light tokens.
    val Navy100 = TextDim
    val Emerald400 = Mint
    val Emerald500 = Emerald
}

object RWBrush {
    val emerald = Brush.linearGradient(listOf(Palette.Mint, Palette.Emerald, Color(0xFF059669)))
    val emeraldHorizontal = Brush.horizontalGradient(listOf(Palette.Emerald, Palette.Teal))
    val glassBorder = Brush.linearGradient(listOf(Color(0x33FFFFFF), Color(0x08FFFFFF), Color(0x1AFFFFFF)))
    val card = Brush.linearGradient(listOf(Color(0x14FFFFFF), Color(0x06FFFFFF)))
}

object Fonts {
    val Display = FontFamily(
        Font(R.font.space_grotesk_400, FontWeight.Normal),
        Font(R.font.space_grotesk_500, FontWeight.Medium),
        Font(R.font.space_grotesk_600, FontWeight.SemiBold),
        Font(R.font.space_grotesk_700, FontWeight.Bold),
    )
    val Body = FontFamily(
        Font(R.font.manrope_400, FontWeight.Normal),
        Font(R.font.manrope_500, FontWeight.Medium),
        Font(R.font.manrope_600, FontWeight.SemiBold),
        Font(R.font.manrope_700, FontWeight.Bold),
        Font(R.font.manrope_800, FontWeight.ExtraBold),
    )
}

private fun display(size: Int, weight: FontWeight = FontWeight.SemiBold, tracking: Double = -0.5) =
    TextStyle(fontFamily = Fonts.Display, fontWeight = weight, fontSize = size.sp, letterSpacing = tracking.sp)

private fun body(size: Int, weight: FontWeight = FontWeight.Normal, line: Int = size + 6, tracking: Double = 0.0) =
    TextStyle(fontFamily = Fonts.Body, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp, letterSpacing = tracking.sp)

private val AppTypography = Typography(
    displayLarge = display(56, FontWeight.Bold, -2.0),
    displayMedium = display(44, FontWeight.Bold, -1.5),
    displaySmall = display(36, FontWeight.Bold, -1.0),
    headlineLarge = display(30, FontWeight.Bold, -0.8),
    headlineMedium = display(26, FontWeight.SemiBold, -0.6),
    headlineSmall = display(22, FontWeight.SemiBold, -0.4),
    titleLarge = body(19, FontWeight.Bold, 26, -0.2),
    titleMedium = body(16, FontWeight.Bold, 22),
    titleSmall = body(14, FontWeight.Bold, 20),
    bodyLarge = body(16, FontWeight.Medium, 23),
    bodyMedium = body(14, FontWeight.Medium, 20),
    bodySmall = body(12, FontWeight.Medium, 17),
    labelLarge = body(14, FontWeight.Bold, 20, 0.1),
    labelMedium = body(12, FontWeight.Bold, 16, 0.3),
    labelSmall = body(11, FontWeight.Bold, 14, 0.8),
)

/** Money is set in the display face with tabular figures so columns align. */
val MoneyStyle = TextStyle(fontFamily = Fonts.Display, fontFeatureSettings = "tnum")

private val MidnightScheme = darkColorScheme(
    primary = Palette.Emerald,
    onPrimary = Palette.Ink,
    primaryContainer = Color(0xFF0B3B33),
    onPrimaryContainer = Palette.MintSoft,
    secondary = Palette.Teal,
    onSecondary = Palette.Ink,
    secondaryContainer = Color(0xFF0E3A3C),
    onSecondaryContainer = Palette.MintSoft,
    tertiary = Palette.Amber,
    background = Palette.Navy950,
    onBackground = Palette.Text,
    surface = Palette.Navy900,
    onSurface = Palette.Text,
    surfaceVariant = Palette.Navy800,
    onSurfaceVariant = Palette.TextDim,
    surfaceContainerLowest = Palette.Navy950,
    surfaceContainerLow = Palette.Navy900,
    surfaceContainer = Palette.Navy850,
    surfaceContainerHigh = Palette.Navy800,
    surfaceContainerHighest = Palette.Navy700,
    inverseSurface = Palette.Text,
    inverseOnSurface = Palette.Navy950,
    outline = Color(0x33FFFFFF),
    outlineVariant = Palette.Hairline,
    error = Palette.Coral,
    onError = Palette.Ink,
    scrim = Color(0xCC000000),
)

/**
 * True when the user turned on Android's "Remove animations" (animator duration scale 0).
 * Decorative motion — aurora drift, orb spin, count-ups, entrance slides — then renders static.
 */
val LocalReduceMotion = androidx.compose.runtime.staticCompositionLocalOf { false }

@Composable
fun RandWiseTheme(content: @Composable () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val reduceMotion = androidx.compose.runtime.remember {
        android.provider.Settings.Global.getFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    MaterialTheme(colorScheme = MidnightScheme, typography = AppTypography) {
        // Screens draw straight onto the aurora (no Surface), so set the default text colour here.
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides Palette.Text,
            LocalReduceMotion provides reduceMotion,
            content = content,
        )
    }
}

/** Semantic finance colours. */
object RW {
    val colors = FinanceColors
}

object FinanceColors {
    val positive = Palette.Mint
    val positiveContainer = Color(0x2610B981)
    val warning = Palette.Amber
    val warningContainer = Color(0x26FBBF24)
    val danger = Palette.Coral
    val dangerContainer = Color(0x26F87171)
    val info = Palette.Sky
    val infoContainer = Color(0x2660A5FA)
    val adSurface = Palette.AdSurface
    val adLabel = Palette.AdLabel
}

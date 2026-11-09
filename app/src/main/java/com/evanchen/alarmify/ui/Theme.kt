package com.evanchen.alarmify.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Night sky → sunrise. Warm dawn is for alarms, cool mint for quick alarms. */
object Palette {
    val Night0 = Color(0xFF0A0B1A)
    val Night1 = Color(0xFF13122E)
    val Night2 = Color(0xFF1E1A47)
    val Sheet = Color(0xFF161536)

    val Dawn = Color(0xFFFF8A5B)
    val Gold = Color(0xFFFFC46B)
    val Rose = Color(0xFFFF5F8F)
    val Lavender = Color(0xFFA89BFF)
    val Mint = Color(0xFF6EE7C8)
    val Sky = Color(0xFF66B7FF)
    val Danger = Color(0xFFFF5C6C)

    val Text = Color(0xFFF5F2FF)
    val TextDim = Color(0xFFA9A5CB)
    val TextFaint = Color(0xFF6E6A93)

    val Glass = Color(0x0FFFFFFF)
    val GlassBorder = Color(0x1AFFFFFF)

    val dawnBrush = Brush.linearGradient(listOf(Dawn, Gold))
    val quickBrush = Brush.linearGradient(listOf(Mint, Sky))
}

/** Tabular figures so times don't jiggle as digits change. */
val Numeric = TextStyle(fontFeatureSettings = "tnum")

@Composable
fun AlarmifyTheme(content: @Composable () -> Unit) {
    val colors = darkColorScheme(
        primary = Palette.Dawn,
        onPrimary = Palette.Night0,
        secondary = Palette.Mint,
        background = Palette.Night0,
        surface = Palette.Night1,
        surfaceContainerHigh = Palette.Night2,
        onSurface = Palette.Text,
        onBackground = Palette.Text,
        onSurfaceVariant = Palette.TextDim,
        error = Palette.Danger,
    )
    val base = MaterialTheme.typography
    val typography = base.copy(
        displayLarge = base.displayLarge.copy(fontWeight = FontWeight.Light, fontSize = 72.sp, letterSpacing = (-2).sp),
        headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Bold, fontSize = 34.sp, letterSpacing = (-0.5).sp),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelSmall = base.labelSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 1.4.sp),
    )
    MaterialTheme(colorScheme = colors, typography = typography, content = content)
}

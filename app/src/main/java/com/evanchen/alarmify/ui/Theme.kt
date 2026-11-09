package com.evanchen.alarmify.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.evanchen.alarmify.R

/** Deep water: dark teal, seafoam ink, and coral as the one warm accent. */
object Night {
    val Bg = Color(0xFF06141A)
    val Raised = Color(0xFF0C2029)
    val Fg = Color(0xFFE4F1EE)
    val FgSoft = Color(0xFF8EAAAE)
    val FgFaint = Color(0xFF4C686E)
    val Rule = Color(0xFF15313B)
    val Shade = Color(0xFF0E2830)
    val Accent = Color(0xFFFF7A5C)
}

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun sans(weight: Int) = Font(
    R.font.instrument_sans,
    FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Serif = FontFamily(
    Font(R.font.instrument_serif, FontWeight.Normal),
    Font(R.font.instrument_serif_italic, FontWeight.Normal, FontStyle.Italic),
)

val Sans = FontFamily(sans(400), sans(500), sans(600), sans(700))

/** Big serif clock numerals. */
val Clock = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Normal, color = Night.Fg, fontFeatureSettings = "lnum")

@Composable
fun AlarmifyTheme(content: @Composable () -> Unit) {
    val colors = darkColorScheme(
        primary = Night.Fg,
        onPrimary = Night.Bg,
        secondary = Night.Accent,
        background = Night.Bg,
        surface = Night.Bg,
        onSurface = Night.Fg,
        onBackground = Night.Fg,
        onSurfaceVariant = Night.FgSoft,
        outline = Night.Rule,
        error = Night.Accent,
    )
    val body = TextStyle(fontFamily = Sans, color = Night.Fg)
    val typography = Typography(
        displayLarge = Clock.copy(fontSize = 96.sp, letterSpacing = (-2).sp),
        headlineLarge = TextStyle(fontFamily = Serif, fontSize = 44.sp, letterSpacing = (-0.5).sp, color = Night.Fg),
        titleMedium = body.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
        bodyLarge = body.copy(fontSize = 16.sp),
        bodyMedium = body.copy(fontSize = 14.sp),
        bodySmall = body.copy(fontSize = 13.sp, color = Night.FgSoft),
        labelLarge = body.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
        labelMedium = body.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium),
        labelSmall = body.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp, color = Night.FgFaint),
    )
    MaterialTheme(colorScheme = colors, typography = typography, content = content)
}

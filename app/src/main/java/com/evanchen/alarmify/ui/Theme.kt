package com.evanchen.alarmify.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
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

/** Bedside paper: cream stock, black ink, and the red of an old travel alarm, used sparingly. */
object Paper {
    val Page = Color(0xFFF4EFE4)
    val Sheet = Color(0xFFFAF7F0)
    val Ink = Color(0xFF1B1916)
    val InkSoft = Color(0xFF5E5850)
    val InkFaint = Color(0xFF9C948A)
    val Rule = Color(0xFFDAD2C3)
    val Shade = Color(0xFFEAE3D5)
    val Red = Color(0xFFC8371D)
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
val Clock = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Normal, color = Paper.Ink, fontFeatureSettings = "lnum")

@Composable
fun AlarmifyTheme(content: @Composable () -> Unit) {
    val colors = lightColorScheme(
        primary = Paper.Ink,
        onPrimary = Paper.Page,
        secondary = Paper.Red,
        background = Paper.Page,
        surface = Paper.Page,
        onSurface = Paper.Ink,
        onBackground = Paper.Ink,
        onSurfaceVariant = Paper.InkSoft,
        outline = Paper.Rule,
        error = Paper.Red,
    )
    val body = TextStyle(fontFamily = Sans, color = Paper.Ink)
    val typography = Typography(
        displayLarge = Clock.copy(fontSize = 96.sp, letterSpacing = (-2).sp),
        headlineLarge = TextStyle(fontFamily = Serif, fontSize = 44.sp, letterSpacing = (-0.5).sp, color = Paper.Ink),
        titleMedium = body.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
        bodyLarge = body.copy(fontSize = 16.sp),
        bodyMedium = body.copy(fontSize = 14.sp),
        bodySmall = body.copy(fontSize = 13.sp, color = Paper.InkSoft),
        labelLarge = body.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
        labelMedium = body.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium),
        labelSmall = body.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp, color = Paper.InkFaint),
    )
    MaterialTheme(colorScheme = colors, typography = typography, content = content)
}

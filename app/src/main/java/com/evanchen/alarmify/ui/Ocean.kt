package com.evanchen.alarmify.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

private const val TAU = (2 * PI).toFloat()

/**
 * The water behind a screen: light rays from the surface, slowly falling marine snow, and now and then a small
 * school of fish crossing. Kept faint so it never competes with the content.
 */
@Composable
fun OceanBackdrop(modifier: Modifier = Modifier, school: Boolean = true, depth: () -> Float = { 0f }) {
    // One slow, battery-minded clock for everything (see rememberAmbientTime).
    val clock = rememberAmbientTime()
    val snow = remember { List(34) { Triple(Random.nextFloat(), Random.nextFloat(), Random.nextFloat()) } }

    Canvas(modifier) {
        // How far down the page we've scrolled. The surface light stays at the top of the page, so it slides up
        // and out of view as you go down, and the water gets darker the deeper you are.
        val time = clock.value
        val d = depth().coerceAtLeast(0f)
        drawRect(
            Brush.verticalGradient(
                0f to Color(0xFF0B2530), 1f to Color.Transparent,
                startY = -d, endY = size.height * 0.45f - d,
            ),
        )
        drawRays(time, d)
        drawSnow(time, snow, d)
        val dark = (d / (size.height * 1.5f)).coerceIn(0f, 1f) * 0.45f
        if (dark > 0f) drawRect(Color(0xFF02080B).copy(alpha = dark))
        if (school) drawSchool(time)
    }
}

private fun DrawScope.drawRays(time: Float, scroll: Float) {
    if (scroll > size.height * 0.62f) return
    val beams = listOf(0.18f to 0.9f, 0.46f to 1.3f, 0.74f to 0.7f)
    beams.forEachIndexed { i, (x, w) ->
        val shimmer = 0.5f + 0.5f * sin(time * TAU / listOf(12f, 15f, 20f)[i] + i)
        val drift = sin(time * TAU / listOf(40f, 60f, 120f)[i]) * 24.dp.toPx()
        val top = size.width * x + drift
        val half = 22.dp.toPx() * w
        val depth = size.height * 0.62f
        val top0 = -scroll
        val path = Path().apply {
            moveTo(top - half, top0)
            lineTo(top + half, top0)
            lineTo(top + half * 2.6f + 80.dp.toPx(), top0 + depth)
            lineTo(top - half * 0.6f + 80.dp.toPx(), top0 + depth)
            close()
        }
        drawPath(
            path,
            Brush.verticalGradient(
                listOf(Night.Fg.copy(alpha = 0.035f + 0.03f * shimmer), Color.Transparent),
                startY = top0, endY = top0 + depth,
            ),
        )
    }
}

private fun DrawScope.drawSnow(time: Float, seeds: List<Triple<Float, Float, Float>>, scroll: Float) {
    seeds.forEach { (sx, sy, s) ->
        val period = if (s < 0.5f) 60f else 120f
        val p = ((time / period) + sy) % 1f
        val x = size.width * sx + sin(time * TAU / 12f + sy * 10f) * 8.dp.toPx()
        // Drifts past at a fraction of the scroll speed, so it reads as depth (parallax).
        val y = ((p * size.height - scroll * 0.35f) % size.height + size.height) % size.height
        drawCircle(Night.FgSoft.copy(alpha = 0.10f + 0.22f * s), radius = (0.6f + 1.1f * s) * density, center = Offset(x, y))
    }
}

/** Six little fish crossing left to right about a third of the time. */
private fun DrawScope.drawSchool(time: Float) {
    val period = 40f
    val p = (time % period) / period
    if (p > 0.45f) return
    val q = p / 0.45f
    val lead = Offset(-60.dp.toPx() + q * (size.width + 120.dp.toPx()), size.height * 0.58f + sin(q * TAU) * 18.dp.toPx())
    val formation = listOf(0f to 0f, -16f to -9f, -16f to 10f, -32f to -2f, -30f to 18f, -46f to 7f)
    formation.forEachIndexed { i, (dx, dy) ->
        val sway = sin(time * TAU * 1.5f + i)
        drawFish(
            lead + Offset(dx, dy) * density,
            length = 11.dp.toPx(),
            angle = sin(q * TAU) * 8f,
            color = Night.FgSoft.copy(alpha = 0.22f),
            sway = sway,
            eyeColor = Color.Transparent,
        )
    }
}

/** A hairline that undulates like a calm swell, for between rows. */
@Composable
fun WaveRule(modifier: Modifier = Modifier, color: Color = Night.Rule) {
    Canvas(modifier.fillMaxWidth().height(8.dp)) {
        val amp = 2.dp.toPx()
        val wave = 64.dp.toPx()
        val path = Path()
        var x = 0f
        path.moveTo(0f, size.height / 2)
        while (x <= size.width) {
            path.lineTo(x, size.height / 2 + sin(x / wave * TAU) * amp)
            x += 3f
        }
        drawPath(path, color, style = Stroke(1.dp.toPx()))
    }
}

/** A sheet shape whose top edge is a gentle wave, like looking up at the surface. */
class WaveTopShape(private val amplitude: Float = 7f, private val wavelength: Float = 120f) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val amp = amplitude * density.density
        val wave = wavelength * density.density
        val path = Path().apply {
            moveTo(0f, amp)
            var x = 0f
            while (x <= size.width) {
                lineTo(x, amp + sin(x / wave * TAU) * amp)
                x += 4f
            }
            lineTo(size.width, amp + sin(size.width / wave * TAU) * amp)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        return Outline.Generic(path)
    }
}

/** Plays a little burst of bubbles rising from the element each time [trigger] changes (skips the first value). */
@Composable
fun Modifier.bubbleBurst(trigger: Int): Modifier {
    val progress = remember { Animatable(1f) }
    val seeds = remember(trigger) { List(7) { Triple(Random.nextFloat() * 2 - 1, Random.nextFloat(), Random.nextFloat()) } }
    LaunchedEffect(trigger) {
        if (trigger == 0) return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(1f, tween(900, easing = LinearOutSlowInEasing))
    }
    return drawWithContent {
        drawContent()
        val p = progress.value
        if (p >= 1f) return@drawWithContent
        seeds.forEach { (dx, speed, s) ->
            val rise = p * (40.dp.toPx() + 50.dp.toPx() * speed)
            val c = Offset(
                size.width / 2 + dx * size.width * 0.35f + sin(p * 10f + s * 6f) * 4.dp.toPx(),
                size.height * 0.6f - rise,
            )
            drawCircle(
                Night.Fg.copy(alpha = (1f - p) * 0.8f),
                radius = (2f + 4f * s) * density,
                center = c,
                style = Stroke(1.2.dp.toPx()),
            )
        }
    }
}

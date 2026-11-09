package com.evanchen.alarmify.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evanchen.alarmify.data.Alarm
import java.time.Instant
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The day as a ring, laid out like the sun's path: noon at the top, midnight at the bottom, morning on the left.
 * A live dot marks now, each alarm sits at its time, and an arc sweeps from now to the next alarm.
 * Tap an alarm to edit it; tap anywhere else on the ring to make an alarm at that time.
 */
@Composable
fun DayRing(
    alarms: List<Alarm>,
    now: Long,
    next: Pair<Alarm, Long>?,
    is24: Boolean,
    onAlarmTap: (Alarm) -> Unit,
    onTimeTap: (hour: Int, minute: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val zone = ZoneId.systemDefault()
    val nowT = Instant.ofEpochMilli(now).atZone(zone)
    val nowMin = nowT.hour * 60 + nowT.minute + nowT.second / 60f
    val haptics = LocalHapticFeedback.current
    val measurer = rememberTextMeasurer()

    // The arc from now to the next alarm, in degrees (capped at a full lap), drawn in when it changes.
    val targetSweep = next?.let { ((it.second - now) / 60_000f / 1440f * 360f).coerceIn(0f, 360f) } ?: 0f
    val sweep = remember { Animatable(0f) }
    LaunchedEffect(next?.first?.id, next?.second) {
        sweep.animateTo(targetSweep, tween(900, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(targetSweep) { if (!sweep.isRunning) sweep.snapTo(targetSweep) }

    val glow = rememberInfiniteTransition(label = "glow")
    val breath by glow.animateFloat(0.35f, 0.8f, infiniteRepeatable(tween(1800), RepeatMode.Reverse), label = "breath")
    val sway by glow.animateFloat(-1f, 1f, infiniteRepeatable(tween(420), RepeatMode.Reverse), label = "sway")
    val rise by glow.animateFloat(0f, 1f, infiniteRepeatable(tween(14_000, easing = LinearEasing)), label = "rise")

    val currentAlarms by rememberUpdatedState(alarms)
    val currentOnAlarmTap by rememberUpdatedState(onAlarmTap)
    val currentOnTimeTap by rememberUpdatedState(onTimeTap)

    Box(
        modifier
            .aspectRatio(1f)
            .semantics {
                contentDescription = next?.let {
                    "Next alarm ${formatClockAt(is24, it.second)}, in ${formatDurationLong(it.second - now)}. Tap the ring to add an alarm at that time."
                } ?: "No alarms on. Tap the ring to add an alarm at that time."
            }
            .pointerInput(Unit) {
                detectTapGestures { p ->
                    val c = Offset(size.width / 2f, size.height / 2f)
                    val r = ringRadius(Size(size.width.toFloat(), size.height.toFloat()), density)
                    val d = hypot(p.x - c.x, p.y - c.y)
                    if (abs(d - r) > 44.dp.toPx()) return@detectTapGestures
                    // An alarm marker under the finger?
                    val hit = currentAlarms.minByOrNull { a ->
                        val o = pointAt(c, r, minuteAngle(a.hour * 60f + a.minute))
                        hypot(p.x - o.x, p.y - o.y)
                    }?.takeIf { a ->
                        val o = pointAt(c, r, minuteAngle(a.hour * 60f + a.minute))
                        hypot(p.x - o.x, p.y - o.y) < 28.dp.toPx()
                    }
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (hit != null) {
                        currentOnAlarmTap(hit)
                    } else {
                        // Screen angle → minutes of the day, rounded to the nearest quarter hour.
                        val deg = Math.toDegrees(atan2((p.y - c.y).toDouble(), (p.x - c.x).toDouble())).toFloat()
                        val m = ((((deg - 90f) % 360f + 360f) % 360f) / 360f * 1440f)
                        val q = ((m / 15f).roundToInt() * 15) % 1440
                        currentOnTimeTap(q / 60, q % 60)
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val c = center
            val r = ringRadius(size, density)
            drawDial(c, r, measurer, is24)
            drawBubbles(c, r, rise)

            // Now → next alarm.
            val start = minuteAngle(nowMin)
            if (sweep.value > 0.5f) {
                val frac = sweep.value / 360f
                rotate(start, c) {
                    drawArc(
                        brush = Brush.sweepGradient(
                            0f to Night.Accent.copy(alpha = 0.08f),
                            frac to Night.Accent,
                            center = c,
                        ),
                        startAngle = 0f,
                        sweepAngle = sweep.value,
                        useCenter = false,
                        topLeft = Offset(c.x - r, c.y - r),
                        size = Size(r * 2, r * 2),
                        style = Stroke(6.dp.toPx(), cap = StrokeCap.Round),
                    )
                }
            }

            // Alarms.
            alarms.forEach { a ->
                val o = pointAt(c, r, minuteAngle(a.hour * 60f + a.minute))
                val isNext = a.id == next?.first?.id
                when {
                    isNext -> {
                        drawCircle(Night.Accent.copy(alpha = 0.22f * breath), 16.dp.toPx(), o)
                        drawCircle(Night.Accent, 7.dp.toPx(), o)
                        drawCircle(Night.Bg.copy(alpha = 0.5f), 2.dp.toPx(), o + Offset(-2.5f, -2.5f) * density)
                    }
                    a.enabled -> {
                        // A bubble.
                        drawCircle(Night.Bg, 7.dp.toPx(), o)
                        drawCircle(Night.Fg, 6.dp.toPx(), o, style = Stroke(1.5.dp.toPx()))
                        drawCircle(Night.Fg, 1.5.dp.toPx(), o + Offset(-2.5f, -2.5f) * density)
                    }
                    else -> {
                        drawCircle(Night.Bg, 6.dp.toPx(), o)
                        drawCircle(Night.FgFaint, 4.5.dp.toPx(), o, style = Stroke(1.2.dp.toPx()))
                    }
                }
            }

            // Now: a little fish swimming round the day.
            val n = pointAt(c, r, start)
            drawCircle(Night.Bg, 15.dp.toPx(), n)
            drawFish(n, 34.dp.toPx(), start + 90f, Night.Fg, sway = sway)
        }

        // The countdown in the middle.
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 64.dp)) {
            if (next != null) {
                Text(countdown(next.second - now), style = Clock.copy(fontSize = 68.sp, letterSpacing = (-1).sp))
                Text(
                    listOfNotNull(
                        next.first.label.takeIf { it.isNotBlank() },
                        "${dayPhrase(next.second)} ${formatClockAt(is24, next.second).lowercase()}",
                    ).joinToString(" · ").uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(color = Night.FgSoft),
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
            } else {
                Text("Free", style = Clock.copy(fontSize = 56.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic))
                Text("NO ALARMS ON", style = MaterialTheme.typography.labelSmall.copy(color = Night.FgSoft))
            }
        }
    }
}

/** "7h 23m", "23min" or "42s", with the numbers in serif and the units small. */
private fun countdown(ms: Long): AnnotatedString = buildAnnotatedString {
    val unit = SpanStyle(fontFamily = Sans, fontSize = 20.sp, color = Night.FgSoft, letterSpacing = 0.sp)
    fun part(n: Long, u: String) { append("$n"); withStyle(unit) { append(u) } }
    if (ms < 60_000) { part((ms + 999) / 1000, "s"); return@buildAnnotatedString }
    val m = (ms + 59_999) / 60_000
    if (m >= 60) { part(m / 60, "h "); part(m % 60, "m") } else part(m, "min")
}

private fun ringRadius(size: Size, density: Float) = size.minDimension / 2f - 30f * density

/** Screen angle (degrees, clockwise from +x) for a minute of the day: midnight at the bottom, noon on top. */
private fun minuteAngle(minuteOfDay: Float) = 90f + minuteOfDay / 1440f * 360f

private fun pointAt(c: Offset, r: Float, deg: Float): Offset {
    val rad = deg * PI.toFloat() / 180f
    return Offset(c.x + r * cos(rad), c.y + r * sin(rad))
}

/** A few tiny bubbles drifting up inside the ring. */
private fun DrawScope.drawBubbles(c: Offset, r: Float, rise: Float) {
    val seeds = listOf(0.00f to -0.35f, 0.21f to 0.18f, 0.43f to -0.05f, 0.62f to 0.32f, 0.81f to -0.22f)
    seeds.forEachIndexed { i, (phase, x) ->
        val p = (rise + phase) % 1f
        val y = c.y + r * 0.7f - p * r * 1.4f
        val wobble = sin((p * 6f + i) * PI.toFloat()) * 6.dp.toPx()
        val alpha = (sin(p * PI.toFloat()) * 0.35f).coerceAtLeast(0f)
        drawCircle(
            Night.FgSoft.copy(alpha = alpha),
            radius = (1.5f + i % 3) * density,
            center = Offset(c.x + x * r + wobble, y),
            style = Stroke(1.dp.toPx()),
        )
    }
}

private fun DrawScope.drawDial(c: Offset, r: Float, measurer: TextMeasurer, is24: Boolean) {
    // Quarter-hour ticks, longer on the hour, longest every six hours.
    for (i in 0 until 96) {
        val deg = minuteAngle(i * 15f)
        val (len, color, width) = when {
            i % 24 == 0 -> Triple(12.dp.toPx(), Night.FgSoft, 1.5.dp.toPx())
            i % 4 == 0 -> Triple(7.dp.toPx(), Night.FgFaint, 1.2.dp.toPx())
            else -> Triple(3.dp.toPx(), Night.Rule, 1.dp.toPx())
        }
        val a = pointAt(c, r - 10.dp.toPx(), deg)
        val b = pointAt(c, r - 10.dp.toPx() - len, deg)
        drawLine(color, a, b, width, StrokeCap.Round)
    }
    drawCircle(Night.Rule, r, c, style = Stroke(1.dp.toPx()))

    // Labels just outside the ring.
    val labels = listOf(0 to (if (is24) "00" else "midnight"), 360 to (if (is24) "06" else "6 am"), 720 to (if (is24) "12" else "noon"), 1080 to (if (is24) "18" else "6 pm"))
    labels.forEach { (m, text) ->
        val layout = measurer.measure(text, androidx.compose.ui.text.TextStyle(fontFamily = Sans, fontSize = 10.sp, color = Night.FgFaint, letterSpacing = 1.sp))
        val p = pointAt(c, r + 18.dp.toPx(), minuteAngle(m.toFloat()))
        drawText(layout, topLeft = Offset(p.x - layout.size.width / 2f, p.y - layout.size.height / 2f))
    }
}


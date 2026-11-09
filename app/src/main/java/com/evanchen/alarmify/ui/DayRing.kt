package com.evanchen.alarmify.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evanchen.alarmify.data.Alarm
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

private const val AM = 0
private const val PM = 1
private const val HALF = 720 // minutes on one face

/**
 * Today on a 12-hour dial with two faces, AM (midnight to noon) and PM (noon to midnight), that flip over like a coin.
 * The fish is now, your alarms sit on the face for their half of the day, and an arc runs from now to the next alarm.
 *
 * Tap the dial to make an alarm at that time (or tap an alarm to edit it). Hold and drag to scrub to the exact minute
 * with a live preview in the middle; let go to set it. Swipe sideways, or use the switch below, to flip AM/PM.
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
    val scope = rememberCoroutineScope()
    val clock = rememberAmbientTime()

    // Which face is showing, and the flip between them (0° = AM up, 180° = PM up).
    var face by rememberSaveable { mutableStateOf(if (nowT.hour >= 12) PM else AM) }
    val flip = remember { Animatable(face * 180f) }
    // How high the coin is tossed: 0 resting, 1 at the top of the toss (a little below 0 as it lands).
    val lift = remember { Animatable(0f) }
    /** Flip the dial over like a coin: it lifts, turns over once ([dir] picks which way), and lands with a small bounce. */
    fun showFace(f: Int, dir: Int = if (f == PM) 1 else -1) {
        if (f == face) return
        face = f
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        val target = flip.targetValue + dir * 180f
        scope.launch {
            launch {
                lift.animateTo(1f, tween(240, easing = LinearOutSlowInEasing))
                lift.animateTo(0f, tween(240, easing = FastOutLinearInEasing))
                haptics.performHapticFeedback(HapticFeedbackType.LongPress) // it lands
                lift.animateTo(-0.07f, tween(70))
                lift.animateTo(0f, spring(dampingRatio = 0.35f, stiffness = 700f))
            }
            flip.animateTo(target, tween(480, easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)))
        }
    }
    // The face actually drawn: whichever side is towards you mid-spin.
    val turn = ((flip.value % 360f) + 360f) % 360f
    val shown = if (turn > 90f && turn < 270f) PM else AM

    // The arc to the next alarm grows in when it changes.
    val grow = remember { Animatable(0f) }
    LaunchedEffect(next?.first?.id, next?.second) {
        grow.snapTo(0f)
        grow.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
    }

    // Scrubbing: the minute of the day under your finger while you hold and drag, or null.
    var scrub by remember { mutableStateOf<Int?>(null) }

    val currentAlarms by rememberUpdatedState(alarms)
    val currentOnAlarmTap by rememberUpdatedState(onAlarmTap)
    val currentOnTimeTap by rememberUpdatedState(onTimeTap)
    val currentFace by rememberUpdatedState(face)

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .semantics {
                    contentDescription = buildString {
                        append(if (face == AM) "AM side of the dial. " else "PM side of the dial. ")
                        append(
                            next?.let { "Next alarm ${formatClockAt(is24, it.second)}, in ${formatDurationLong(it.second - now)}. " }
                                ?: "No alarms on. ",
                        )
                        append("Tap the dial to add an alarm at that time; hold and drag to choose the exact time.")
                    }
                    customActions = listOf(
                        CustomAccessibilityAction(if (face == AM) "Show PM side" else "Show AM side") {
                            showFace(1 - face); true
                        },
                    )
                }
                .pointerInput(Unit) {
                    // A tap: edit the alarm under your finger, or make one at that time (to the quarter hour).
                    fun tap(p: Offset, c: Offset, r: Float) {
                        val d = hypot(p.x - c.x, p.y - c.y)
                        if (d < r * 0.4f || d > r + 48.dp.toPx()) return // the middle, or off the dial
                        val onFace = currentAlarms.filter { (it.hour >= 12) == (currentFace == PM) }
                        fun markerAt(a: Alarm) = pointAt(c, r, faceAngle((a.hour % 12) * 60f + a.minute))
                        val hit = onFace.minByOrNull { a -> (markerAt(a) - p).getDistance() }
                            ?.takeIf { a -> (markerAt(a) - p).getDistance() < 28.dp.toPx() }
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (hit != null) {
                            currentOnAlarmTap(hit)
                        } else {
                            val q = (((faceMinute(p, c) / 15f).roundToInt() * 15) % HALF) + currentFace * HALF
                            currentOnTimeTap(q / 60, q % 60)
                        }
                    }
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val c = Offset(size.width / 2f, size.height / 2f)
                        val r = ringRadius(Size(size.width.toFloat(), size.height.toFloat()), density)
                        val slop = viewConfiguration.touchSlop

                        // Before the long-press timeout: a tap, a sideways swipe (flip), or an up/down scroll (leave
                        // it to the list). After it: scrubbing.
                        var swiped = false
                        val ended = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                            var done: Boolean? = null
                            while (done == null) {
                                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                                if (change == null) { done = true; continue }
                                val d = change.position - down.position
                                if (!change.pressed) {
                                    if (d.getDistance() < slop) tap(down.position, c, r)
                                    done = true
                                } else if (d.getDistance() > slop) {
                                    // Sideways: a flip. Up or down: the list is scrolling, so leave it alone.
                                    swiped = abs(d.x) > abs(d.y) * 1.2f
                                    done = !swiped
                                }
                            }
                            done
                        }
                        if (ended == true) return@awaitEachGesture

                        if (swiped) {
                            // Follow the swipe to its end, then flip.
                            var dx = 0f
                            while (true) {
                                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                dx = change.position.x - down.position.x
                                change.consume()
                                if (!change.pressed) break
                            }
                            if (abs(dx) > 40.dp.toPx()) showFace(1 - currentFace, if (dx > 0) 1 else -1)
                            return@awaitEachGesture
                        }

                        // Held: scrub round the face to the exact time.
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        fun minuteAt(p: Offset): Int {
                            val m = faceMinute(p, c)
                            return (currentFace * HALF + ((m / 5f).roundToInt() * 5) % HALF) % 1440
                        }
                        scrub = minuteAt(down.position)
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            change.consume()
                            if (!change.pressed) break
                            val m = minuteAt(change.position)
                            if (m != scrub) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                scrub = m
                            }
                        }
                        scrub?.let { currentOnTimeTap(it / 60, it % 60) }
                        scrub = null
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            // The coin's shadow on the sea floor: it shrinks and fades as the coin rises.
            Canvas(Modifier.fillMaxSize()) {
                val up = lift.value.coerceAtLeast(0f)
                if (up <= 0.01f) return@Canvas
                val r = ringRadius(size, density)
                val w = r * (1.1f - 0.45f * up)
                drawOval(
                    Night.Bg.copy(alpha = 0.55f * up),
                    topLeft = Offset(center.x - w, center.y + r * 1.02f - 7.dp.toPx()),
                    size = Size(w * 2, 14.dp.toPx()),
                )
            }
            // The coin: tossed up and scaled as it comes towards you.
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val up = lift.value
                        scaleX = 1f + 0.13f * up
                        scaleY = 1f + 0.13f * up - if (up < 0f) 0.4f * up else 0f // a squash as it lands
                        translationY = -up.coerceAtLeast(0f) * 30.dp.toPx()
                    },
                contentAlignment = Alignment.Center,
            ) {
            // While it spins it's a solid coin, so side-on you see its rim rather than nothing.
            Canvas(Modifier.fillMaxSize()) {
                val up = lift.value.coerceAtLeast(0f)
                if (up <= 0.01f && abs(turn % 180f) < 0.5f) return@Canvas
                val r = ringRadius(size, density) + 4.dp.toPx()
                val rad = flip.value * PI.toFloat() / 180f
                val face = abs(cos(rad))
                val rim = 7.dp.toPx() * abs(sin(rad))
                val w = r * face + rim
                val solid = (up * 1.6f).coerceIn(0f, 1f)
                drawOval(Night.Raised.copy(alpha = 0.95f * solid), Offset(center.x - w, center.y - r), Size(w * 2, r * 2))
                drawOval(Night.FgFaint.copy(alpha = 0.7f * solid), Offset(center.x - w, center.y - r), Size(w * 2, r * 2), style = Stroke(1.5.dp.toPx()))
            }
            // The flipping face.
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        rotationY = flip.value
                        cameraDistance = 14f * density
                    },
                contentAlignment = Alignment.Center,
            ) {
                // Turn the back face round again so its numbers read the right way.
                Box(Modifier.fillMaxSize().graphicsLayer { rotationY = if (shown == PM) 180f else 0f }) {
                    Canvas(Modifier.fillMaxSize()) {
                        val c = center
                        val r = ringRadius(size, density)
                        val time = clock.value
                        val breath = 0.575f + 0.225f * sin(time * 1.75f)
                        val dim = if (scrub != null) 0.35f else 1f

                        drawFace(c, r, measurer, is24, shown)
                        drawBubbles(c, r, (time / 14f) % 1f)

                        // Now → the next alarm, for the parts of that stretch that fall on this face.
                        if (next != null && grow.value > 0f) {
                            arcSpans(now, next.second, shown, zone).forEach { (from, full) ->
                                val sweep = full * grow.value
                                drawArc(
                                    Night.Accent.copy(alpha = 0.85f * dim),
                                    startAngle = faceAngle(from), sweepAngle = sweep, useCenter = false,
                                    topLeft = Offset(c.x - r, c.y - r), size = Size(r * 2, r * 2),
                                    style = Stroke(6.dp.toPx(), cap = StrokeCap.Round),
                                )
                            }
                        }

                        // Alarms on this half of the day.
                        alarms.filter { (it.hour >= 12) == (shown == PM) }.forEach { a ->
                            val o = pointAt(c, r, faceAngle((a.hour % 12) * 60f + a.minute))
                            val isNext = a.id == next?.first?.id
                            when {
                                isNext -> {
                                    drawCircle(Night.Accent.copy(alpha = 0.22f * breath * dim), 16.dp.toPx(), o)
                                    drawCircle(Night.Accent.copy(alpha = dim), 7.dp.toPx(), o)
                                }
                                a.enabled -> {
                                    drawCircle(Night.Bg, 7.dp.toPx(), o)
                                    drawCircle(Night.Fg.copy(alpha = dim), 6.dp.toPx(), o, style = Stroke(1.5.dp.toPx()))
                                    drawCircle(Night.Fg.copy(alpha = dim), 1.5.dp.toPx(), o + Offset(-2.5f, -2.5f) * density)
                                }
                                else -> {
                                    drawCircle(Night.Bg, 6.dp.toPx(), o)
                                    drawCircle(Night.FgFaint.copy(alpha = dim), 4.5.dp.toPx(), o, style = Stroke(1.2.dp.toPx()))
                                }
                            }
                        }

                        // Now: the fish, if now is on this face.
                        if ((nowT.hour >= 12) == (shown == PM)) {
                            val a = faceAngle(nowMin % HALF)
                            val n = pointAt(c, r, a)
                            drawCircle(Night.Bg, 15.dp.toPx(), n)
                            drawFish(n, 34.dp.toPx(), a + 90f, Night.Fg.copy(alpha = if (scrub != null) 0.5f else 1f))
                        }

                        // Scrubbing: a hand from the middle to a coral handle under your finger.
                        scrub?.let { m ->
                            val a = faceAngle((m % HALF).toFloat())
                            val h = pointAt(c, r, a)
                            drawLine(Night.Accent.copy(alpha = 0.5f), pointAt(c, r * 0.42f, a), pointAt(c, r - 14.dp.toPx(), a), 2.dp.toPx(), StrokeCap.Round)
                            drawCircle(Night.Accent.copy(alpha = 0.25f), 22.dp.toPx(), h)
                            drawCircle(Night.Bg, 13.dp.toPx(), h)
                            drawCircle(Night.Accent, 11.dp.toPx(), h)
                        }
                    }
                }
            }

            }

            // The middle: the countdown, or the time you're scrubbing to.
            AnimatedContent(
                targetState = scrub != null,
                transitionSpec = { (fadeIn(tween(150)) + scaleIn(initialScale = 0.92f)) togetherWith fadeOut(tween(100)) },
                label = "dialCentre",
                modifier = Modifier.padding(horizontal = 64.dp),
            ) { scrubbing ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val s = scrub
                    when {
                        scrubbing && s != null -> {
                            TimeText(s / 60, s % 60, is24, 60.sp)
                            val at = nextOccurrence(s, now, zone)
                            Text(
                                "RINGS IN ${formatDuration(at - now).uppercase()}",
                                style = MaterialTheme.typography.labelSmall.copy(color = Night.Accent),
                            )
                            Text("let go to set", style = MaterialTheme.typography.bodySmall.copy(color = Night.FgFaint, fontFamily = Serif, fontStyle = FontStyle.Italic, fontSize = 15.sp))
                        }
                        next != null -> {
                            Text(countdown(next.second - now), style = Clock.copy(fontSize = 64.sp, letterSpacing = (-1).sp))
                            Text(
                                listOfNotNull(
                                    next.first.label.takeIf { it.isNotBlank() },
                                    "${dayPhrase(next.second)} ${formatClockAt(is24, next.second).lowercase()}",
                                ).joinToString(" · ").uppercase(),
                                style = MaterialTheme.typography.labelSmall.copy(color = Night.FgSoft),
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                            )
                        }
                        else -> {
                            Text("Free", style = Clock.copy(fontSize = 56.sp, fontStyle = FontStyle.Italic))
                            Text("NO ALARMS ON", style = MaterialTheme.typography.labelSmall.copy(color = Night.FgSoft))
                        }
                    }
                }
            }
        }

        FaceSwitch(
            face = face,
            amAlarms = alarms.any { it.enabled && it.hour < 12 },
            pmAlarms = alarms.any { it.enabled && it.hour >= 12 },
            onFace = { showFace(it) },
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** The AM/PM switch under the dial; a small dot shows which side has alarms on. */
@Composable
private fun FaceSwitch(face: Int, amAlarms: Boolean, pmAlarms: Boolean, onFace: (Int) -> Unit, modifier: Modifier) {
    Row(
        modifier.clip(CircleShape).background(Night.Raised).border(1.dp, Night.Rule, CircleShape).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        FaceTab("AM", Icons.Rounded.WbSunny, face == AM, amAlarms) { onFace(AM) }
        FaceTab("PM", Icons.Rounded.DarkMode, face == PM, pmAlarms) { onFace(PM) }
    }
}

@Composable
private fun FaceTab(label: String, icon: ImageVector, selected: Boolean, hasAlarms: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) Night.Fg else Night.Raised, label = "tabBg")
    val fg by animateColorAsState(if (selected) Night.Bg else Night.FgSoft, label = "tabFg")
    Row(
        Modifier.clip(CircleShape).background(bg).pressable(onClick, role = Role.Tab)
            .semantics { this.selected = selected; contentDescription = if (label == "AM") "AM side" else "PM side" }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge.copy(color = fg, fontSize = 14.sp))
        if (hasAlarms) {
            Spacer(Modifier.width(6.dp))
            Box(Modifier.size(6.dp).clip(CircleShape).background(Night.Accent))
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

/** Screen angle (degrees, clockwise from +x) for minutes into a face: 12 at the top, like a clock. */
private fun faceAngle(minuteOfFace: Float) = -90f + minuteOfFace / HALF * 360f

/** Minutes into the face (0 until 720) at a point on screen. */
private fun faceMinute(p: Offset, c: Offset): Float {
    val deg = Math.toDegrees(atan2((p.y - c.y).toDouble(), (p.x - c.x).toDouble())).toFloat()
    return (((deg + 90f) % 360f + 360f) % 360f) / 360f * HALF
}

private fun pointAt(c: Offset, r: Float, deg: Float): Offset {
    val rad = deg * PI.toFloat() / 180f
    return Offset(c.x + r * cos(rad), c.y + r * sin(rad))
}

/** The next time [minuteOfDay] comes round, after [now]. */
private fun nextOccurrence(minuteOfDay: Int, now: Long, zone: ZoneId): Long {
    var at = ZonedDateTime.of(LocalDate.now(zone), LocalTime.of(minuteOfDay / 60, minuteOfDay % 60), zone)
    if (!at.toInstant().isAfter(Instant.ofEpochMilli(now))) at = at.plusDays(1)
    return at.toInstant().toEpochMilli()
}

/**
 * The arc from now ([from]) towards the next alarm ([to]) on [face], as (minute into the face, sweep in degrees).
 * Only drawn when the alarm is in this half of the day or the next: it runs from now round to 12 on this side and
 * carries on from 12 on the other. Anything further off would just be a solid ring, so the countdown says it instead.
 */
private fun arcSpans(from: Long, to: Long, face: Int, zone: ZoneId): List<Pair<Float, Float>> {
    if (to <= from) return emptyList()
    val t = Instant.ofEpochMilli(from).atZone(zone)
    val nowFace = if (t.hour >= 12) PM else AM
    val w0 = t.toLocalDate().atStartOfDay(zone).plusHours(12L * nowFace)
    val ws0 = w0.toInstant().toEpochMilli()
    val we0 = w0.plusHours(12).toInstant().toEpochMilli()
    val we1 = w0.plusHours(24).toInstant().toEpochMilli()
    if (to > we1) return emptyList()
    fun span(start: Long, end: Long, windowStart: Long) = ((start - windowStart) / 60_000f) to ((end - start) / 60_000f / HALF * 360f)
    return buildList {
        if (face == nowFace) add(span(from, minOf(to, we0), ws0))
        if (face != nowFace && to > we0) add(span(we0, to, we0))
    }
}

/** A few tiny bubbles drifting up inside the dial. */
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

/** One clock face: minute ticks, the hours, and the side's name. */
private fun DrawScope.drawFace(c: Offset, r: Float, measurer: TextMeasurer, is24: Boolean, face: Int) {
    // 5-minute ticks, longer on the hour, longest at 12/3/6/9.
    for (i in 0 until 144) {
        val deg = faceAngle(i * 5f)
        val (len, color, width) = when {
            i % 36 == 0 -> Triple(12.dp.toPx(), Night.FgSoft, 1.6.dp.toPx())
            i % 12 == 0 -> Triple(7.dp.toPx(), Night.FgFaint, 1.3.dp.toPx())
            else -> Triple(2.5.dp.toPx(), Night.Rule, 1.dp.toPx())
        }
        val a = pointAt(c, r - 10.dp.toPx(), deg)
        val b = pointAt(c, r - 10.dp.toPx() - len, deg)
        drawLine(color, a, b, width, StrokeCap.Round)
    }
    drawCircle(Night.Rule, r, c, style = Stroke(1.dp.toPx()))

    // The hours, just outside the ring.
    val base = face * 12
    val numberStyle = TextStyle(fontFamily = Serif, fontSize = 15.sp, color = Night.FgSoft)
    for (h in 0 until 12) {
        val shown = if (is24) "%02d".format(base + h) else "${if (h == 0) 12 else h}"
        val major = h % 3 == 0
        val layout = measurer.measure(shown, if (major) numberStyle else numberStyle.copy(fontSize = 11.sp, color = Night.FgFaint))
        val p = pointAt(c, r + 17.dp.toPx(), faceAngle(h * 60f))
        drawText(layout, topLeft = Offset(p.x - layout.size.width / 2f, p.y - layout.size.height / 2f))
    }

    // The side's name, inside the top of the dial.
    val name = measurer.measure(
        if (face == AM) "AM" else "PM",
        TextStyle(fontFamily = Sans, fontSize = 11.sp, color = Night.FgFaint, letterSpacing = 2.sp),
    )
    drawText(name, topLeft = Offset(c.x - name.size.width / 2f, c.y - r + 28.dp.toPx()))
}

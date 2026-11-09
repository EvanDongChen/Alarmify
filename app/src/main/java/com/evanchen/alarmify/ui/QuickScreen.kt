package com.evanchen.alarmify.ui

import android.text.format.DateFormat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.evanchen.alarmify.data.Alarm
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val PRESETS = listOf(5, 10, 15, 20, 30, 45, 60, 90)
private const val MAX_MINUTES = 180f

@Composable
fun QuickScreen(
    alarms: List<Alarm>,
    onStart: (minutes: Int) -> Unit,
    onStartAt: (epochMs: Long) -> Unit,
    onCancel: (Alarm) -> Unit,
) {
    val context = LocalContext.current
    val is24 = remember { DateFormat.is24HourFormat(context) }
    val now by rememberNow()
    val scope = rememberCoroutineScope()
    val dial = remember { Animatable(20f) }
    val minutes = dial.value.roundToInt().coerceAtLeast(1)
    val active = alarms.filter { it.quick && it.nextTrigger(now) != null }.sortedBy { it.nextTrigger(now) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 140.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 20.dp, bottom = 4.dp)) {
                Text("Nap, cook, focus. Set it and go.", color = Palette.TextDim, fontSize = 15.sp)
                Text("Quick alarm", style = MaterialTheme.typography.headlineLarge, color = Palette.Text)
            }
        }

        items(active, key = { it.id }) { alarm ->
            ActiveQuick(alarm, now, is24, Modifier.animateItem()) { onCancel(alarm) }
        }

        item {
            MinuteDial(
                minutes = dial.value,
                onDrag = { scope.launch { dial.snapTo(it) } },
                modifier = Modifier.widthIn(max = 300.dp).fillMaxWidth().padding(vertical = 8.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val big = if (minutes < 60) "$minutes" else "%d:%02d".format(minutes / 60, minutes % 60)
                    Text(big, style = MaterialTheme.typography.displayLarge.merge(Numeric), color = Palette.Text)
                    Text(if (minutes < 60) "minutes" else "hours", color = Palette.TextDim, fontSize = 15.sp)
                    Spacer(Modifier.height(10.dp))
                    Box(Modifier.clip(CircleShape).background(Palette.Mint.copy(alpha = 0.14f)).padding(horizontal = 12.dp, vertical = 5.dp)) {
                        Text(
                            "rings ${formatClockAt(is24, now + minutes * 60_000L)}",
                            color = Palette.Mint, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }

        // One-tap presets: they spin the dial to the value.
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PRESETS.chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { m ->
                            Pill(minutesLabel(m), minutes == m, Modifier.weight(1f), accent = Palette.Mint) {
                                scope.launch {
                                    dial.animateTo(m.toFloat(), spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessLow))
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            GradientButton(
                text = "Start  ·  ${minutesLabel(minutes)}",
                brush = Palette.quickBrush,
                glow = Palette.Mint,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                leading = { Icon(Icons.Rounded.Bolt, null, tint = Palette.Night0) },
            ) { onStart(minutes) }
        }

        item { WakeUpAt(now, is24, onStartAt) }
    }
}

/** A dial you drag around like a kitchen timer: one lap is an hour, up to three laps. */
@Composable
private fun MinuteDial(
    minutes: Float,
    onDrag: (Float) -> Unit,
    modifier: Modifier = Modifier,
    center: @Composable () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    var value by remember { mutableFloatStateOf(minutes) }
    value = minutes
    var lastAngle by remember { mutableFloatStateOf(0f) }

    fun angleOf(p: Offset, size: Size): Float {
        val dx = p.x - size.width / 2f
        val dy = p.y - size.height / 2f
        return Math.toDegrees(atan2(dx.toDouble(), -dy.toDouble())).toFloat() // 0° at 12 o'clock, clockwise
    }

    Box(
        modifier
            .aspectRatio(1f)
            .semantics {
                contentDescription = "Quick alarm length"
                stateDescription = "${value.roundToInt()} minutes"
                setProgress { target -> onDrag(target.coerceIn(1f, MAX_MINUTES)); true }
            }
            .pointerInput(Unit) {
                val sz = Size(size.width.toFloat(), size.height.toFloat())
                detectDragGestures(
                    onDragStart = { lastAngle = angleOf(it, sz) },
                ) { change, _ ->
                    change.consume()
                    val a = angleOf(change.position, sz)
                    var d = a - lastAngle
                    if (d > 180) d -= 360
                    if (d < -180) d += 360
                    lastAngle = a
                    val before = value.roundToInt()
                    val next = (value + d / 6f).coerceIn(1f, MAX_MINUTES) // 6° = 1 minute
                    value = next
                    val after = next.roundToInt()
                    if (after != before && after % 5 == 0) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onDrag(next)
                }
            }
            .pointerInput(Unit) {
                // Tap a spot on the ring to jump within the current hour.
                detectTapGestures { p ->
                    val sz = Size(size.width.toFloat(), size.height.toFloat())
                    val a = (angleOf(p, sz) + 360f) % 360f
                    val lap = (value.roundToInt() / 60) * 60f
                    onDrag((lap + (a / 6f).roundToInt()).coerceIn(1f, MAX_MINUTES))
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val glow = rememberInfiniteTransition(label = "glow")
        val pulse by glow.animateFloat(0.35f, 0.6f, infiniteRepeatable(tween(1800), RepeatMode.Reverse), label = "pulse")

        Canvas(Modifier.fillMaxSize()) {
            val stroke = 22.dp.toPx()
            val r = size.minDimension / 2f - stroke
            val c = Offset(size.width / 2f, size.height / 2f)
            val arcSize = Size(r * 2, r * 2)
            val topLeft = Offset(c.x - r, c.y - r)
            val sweepBrush = Brush.sweepGradient(listOf(Palette.Mint, Palette.Sky, Palette.Lavender, Palette.Mint), c)

            // Track and minute ticks.
            drawCircle(Color(0x12FFFFFF), radius = r, center = c, style = Stroke(stroke))
            for (i in 0 until 60) {
                val major = i % 5 == 0
                val ang = Math.toRadians(i * 6.0 - 90)
                val outer = r - stroke / 2 - 8.dp.toPx()
                val inner = outer - if (major) 10.dp.toPx() else 4.dp.toPx()
                drawLine(
                    if (major) Palette.TextDim.copy(alpha = 0.7f) else Palette.TextFaint.copy(alpha = 0.5f),
                    Offset(c.x + inner * cos(ang).toFloat(), c.y + inner * sin(ang).toFloat()),
                    Offset(c.x + outer * cos(ang).toFloat(), c.y + outer * sin(ang).toFloat()),
                    strokeWidth = if (major) 2.dp.toPx() else 1.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }

            val laps = (minutes / 60f).toInt()
            val partial = minutes - laps * 60f
            rotate(-90f, c) {
                if (laps > 0) {
                    // Completed hours: a full ring, brighter for each lap.
                    drawArc(sweepBrush, 0f, 360f, false, topLeft, arcSize, alpha = 0.35f + 0.2f * laps, style = Stroke(stroke))
                }
                drawArc(sweepBrush, 0f, partial * 6f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }

            // The knob you grab.
            val ang = Math.toRadians(partial * 6.0 - 90)
            val k = Offset(c.x + r * cos(ang).toFloat(), c.y + r * sin(ang).toFloat())
            drawCircle(Palette.Mint.copy(alpha = pulse * 0.5f), radius = stroke * 1.15f, center = k)
            drawCircle(Color.White, radius = stroke * 0.62f, center = k)
        }
        center()
    }
}

@Composable
private fun ActiveQuick(alarm: Alarm, now: Long, is24: Boolean, modifier: Modifier, onCancel: () -> Unit) {
    val at = alarm.nextTrigger(now) ?: return
    val beat = rememberInfiniteTransition(label = "beat")
    val s by beat.animateFloat(0.8f, 1.15f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "s")
    GlassCard(modifier.fillMaxWidth(), tint = Palette.Mint.copy(alpha = 0.10f)) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).scale(s).clip(CircleShape).background(Palette.Mint))
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(formatCountdown(at - now), color = Palette.Text, fontSize = 30.sp, fontWeight = FontWeight.Light, style = Numeric)
                Text(
                    listOfNotNull(
                        "Rings ${formatClockAt(is24, at)}",
                        alarm.label.takeIf { it.isNotBlank() },
                        if ((alarm.snoozedUntil ?: 0) > now) "snoozed" else null,
                    ).joinToString("  ·  "),
                    color = Palette.TextDim, fontSize = 13.sp,
                )
            }
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(Palette.Glass).pressable(onCancel)
                    .semantics { contentDescription = "Cancel quick alarm" },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Close, null, tint = Palette.TextDim) }
        }
    }
}

/** Morning wake-up times one tap away. */
@Composable
private fun WakeUpAt(now: Long, is24: Boolean, onStartAt: (Long) -> Unit) {
    val zone = ZoneId.systemDefault()
    val times = remember(now / 60_000) {
        (0 until 8).map { i ->
            val t = LocalTime.of(5, 30).plusMinutes(30L * i)
            var at = ZonedDateTime.of(LocalDate.now(zone), t, zone)
            if (!at.toInstant().isAfter(java.time.Instant.ofEpochMilli(now))) at = at.plusDays(1)
            at.toInstant().toEpochMilli()
        }
    }
    Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)) {
            Icon(Icons.Rounded.WbTwilight, null, tint = Palette.Gold, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            SectionLabel("Wake up at")
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(times) { at ->
                Column(
                    Modifier
                        .clip(RoundedCornerShape(22.dp))
                        .background(Palette.Glass)
                        .pressable({ onStartAt(at) })
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                ) {
                    Text(formatClockAt(is24, at), color = Palette.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, style = Numeric)
                    Text("in ${formatDuration(at - now)}", color = Palette.Gold, fontSize = 12.sp)
                }
            }
        }
    }
}

